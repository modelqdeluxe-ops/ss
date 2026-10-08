package net.tierrasfantasticas.tfclient.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.items.TFBinding;
import net.tierrasfantasticas.tfclient.items.TFItem;
import net.tierrasfantasticas.tfclient.items.TFRevocations;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * El GTS: el mercado entre jugadores del TF Pad. Un jugador publica lo que tiene en la mano a un precio (lo escribe en
 * el chat) y el objeto queda guardado hasta que otro lo compra, él lo retira o pasan {@link #DAYS} días; entonces vuelve
 * a «Recoger». Al venderse, el vendedor cobra al momento (si la economía no deja pagarle desconectado, cobra al entrar).
 * <p>
 * No se publica lo que va ligado a su dueño (piezas de sets), lo comprado en la web (lleva TFOrder: un reembolso tiene
 * que poder quitarlo) ni contenedores con cosas dentro. Todo vive en &lt;mundo&gt;/tfclient/gts.json.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFMarket {
    public static final int MAX_LISTINGS = 10;
    public static final long DAYS = 7;
    public static final long MAX_PRICE = 1_000_000_000L;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long PROMPT_MS = 60_000;

    public record Listing(String id, UUID seller, String sellerName, ItemStack item, long price, long created) {
        public long expiresAt() {
            return created + DAYS * DAY_MS;
        }
    }

    public record Prompt(ItemStack snapshot, int slot, long expires) {}

    private static final List<Listing> listings = new ArrayList<>();
    private static final Map<UUID, List<ItemStack>> returns = new HashMap<>();
    private static final Map<UUID, Long> owed = new HashMap<>();
    private static final Map<UUID, List<String>> news = new HashMap<>();
    /** Se mira desde el hilo de red (el chat llega por ahí). */
    private static final Map<UUID, Prompt> prompts = new java.util.concurrent.ConcurrentHashMap<>();
    private static MinecraftServer server;
    private static boolean dirty;
    private static int ticks;

    private TFMarket() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------------------------------------------------

    /** Lo publicado, lo más nuevo primero. */
    public static List<Listing> listings() {
        return List.copyOf(listings);
    }

    public static List<Listing> listingsOf(UUID uuid) {
        return listings.stream().filter(l -> l.seller.equals(uuid)).toList();
    }

    public static List<ItemStack> returnsOf(UUID uuid) {
        return List.copyOf(returns.getOrDefault(uuid, List.of()));
    }

    public static Listing find(String id) {
        for (Listing l : listings) if (l.id.equals(id)) return l;
        return null;
    }

    /** Por qué no se puede publicar este objeto (null si se puede). */
    public static String whyNot(ItemStack stack) {
        if (stack.isEmpty()) return "No tienes nada en la mano.";
        if (stack.getItem() instanceof TFItem && (TFBinding.bindable(stack) || TFBinding.owner(stack) != null)) {
            return "Las piezas de los sets van ligadas a su dueño y no se pueden vender.";
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TFRevocations.ORDER, Tag.TAG_STRING)) {
            return "Lo comprado en la web no se puede vender en el GTS.";
        }
        if (tag != null && (!tag.getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND).isEmpty()
                || !tag.getList("Items", Tag.TAG_COMPOUND).isEmpty())) {
            return "Vacía el contenedor antes de venderlo.";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Publicar: el precio se escribe en el chat
    // ---------------------------------------------------------------------------------------------------------------

    /** Pide el precio por el chat; si no se puede vender, devuelve por qué (y no cierra la ventana). */
    public static String requestPrice(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        String why = whyNot(hand);
        if (why != null) return why;
        if (listingsOf(player.getUUID()).size() >= MAX_LISTINGS) {
            return "Ya tienes " + MAX_LISTINGS + " cosas a la venta. Retira alguna o espera a que se venda.";
        }
        net.tierrasfantasticas.tfclient.claims.gui.ClaimMenuHandler.clearPrompt(player.getUUID());
        prompts.put(player.getUUID(), new Prompt(hand.copy(), player.getInventory().selected, System.currentTimeMillis() + PROMPT_MS));
        player.closeContainer();
        tell(player, "Escribe en el chat el precio para " + describe(hand) + " (o «cancelar»):", ChatFormatting.YELLOW);
        player.sendSystemMessage(Component.literal("    Solo el número, por ejemplo 250. Tu mensaje no lo ve nadie.").withStyle(ChatFormatting.DARK_GRAY));
        player.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.MASTER, 0.6F, 1.2F);
        return null;
    }

    public static boolean hasPrompt(UUID uuid) {
        Prompt p = prompts.get(uuid);
        if (p == null) return false;
        if (System.currentTimeMillis() > p.expires) {
            prompts.remove(uuid);
            return false;
        }
        return true;
    }

    /** Saca la pregunta pendiente (desde el hilo de red, en el mismo momento en que llega el mensaje). */
    public static Prompt popPrompt(UUID uuid) {
        Prompt p = prompts.remove(uuid);
        return p == null || System.currentTimeMillis() > p.expires ? null : p;
    }

    /** La respuesta del chat a la pregunta p (en el hilo del servidor). */
    public static void answer(ServerPlayer player, Prompt p, String text) {
        if (p == null || player.hasDisconnected()) return;
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("cancelar") || t.equalsIgnoreCase("cancel") || t.startsWith("/")) {
            tell(player, "Venta cancelada.", ChatFormatting.GRAY);
            return;
        }
        long price = parsePrice(t);
        if (price <= 0 || price > MAX_PRICE) {
            tell(player, "No entendí ese precio. Escribe solo el número (por ejemplo 1500 o 5k). Vuelve a probar desde el GTS.", ChatFormatting.RED);
            return;
        }
        ItemStack hand = player.getMainHandItem();
        if (player.getInventory().selected != p.slot || !ItemStack.matches(hand, p.snapshot)) {
            tell(player, "El objeto ya no está en tu mano. Vuelve a probar desde el GTS.", ChatFormatting.RED);
            return;
        }
        String why = whyNot(hand);
        if (why != null) {
            tell(player, why, ChatFormatting.RED);
            return;
        }
        if (listingsOf(player.getUUID()).size() >= MAX_LISTINGS) {
            tell(player, "Ya tienes " + MAX_LISTINGS + " cosas a la venta.", ChatFormatting.RED);
            return;
        }
        ItemStack item = hand.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Listing l = new Listing(UUID.randomUUID().toString().substring(0, 8), player.getUUID(), player.getGameProfile().getName(),
                item, price, System.currentTimeMillis());
        listings.add(0, l);
        save();
        log("publica", player.getGameProfile().getName(), l);
        tell(player, "Publicado: " + describe(item) + " por " + TFEconomy.format(price) + ". Estará " + DAYS + " días en el GTS.", ChatFormatting.GREEN);
        player.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.MASTER, 0.5F, 1.0F);
        TFMarketMenu.openMine(player);
    }

    private static final java.util.regex.Pattern PRICE = java.util.regex.Pattern.compile(
            "^(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d+)\\s*([km])?\\s*(?:monedas?|coins?)?$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * «1500», «1.500», «1,500», «1 500», «250 monedas», «5k» (5.000), «2m» (2.000.000) → el número. Cualquier otra
     * cosa («1.5k», «12,50», «cinco») devuelve -1: mejor preguntar otra vez que vender por un precio que no quería.
     */
    static long parsePrice(String text) {
        java.util.regex.Matcher m = PRICE.matcher(text.trim());
        if (!m.matches()) return -1;
        String digits = m.group(1).replaceAll("[.,\\s]", "");
        if (digits.length() > 12) return -1;
        long value = Long.parseLong(digits);
        String suffix = m.group(2) == null ? "" : m.group(2).toLowerCase(java.util.Locale.ROOT);
        if (suffix.equals("k")) value *= 1_000L;
        if (suffix.equals("m")) value *= 1_000_000L;
        return value;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Comprar, retirar, recoger
    // ---------------------------------------------------------------------------------------------------------------

    /** Compra; devuelve true si se hizo. */
    public static boolean buy(ServerPlayer buyer, String id) {
        Listing l = find(id);
        if (l == null) {
            tell(buyer, "Eso ya no está a la venta.", ChatFormatting.RED);
            return false;
        }
        if (l.seller.equals(buyer.getUUID())) return withdraw(buyer, id);
        if (!TFEconomy.take(buyer, l.price)) {
            tell(buyer, "No tienes suficientes monedas (cuesta " + TFEconomy.format(l.price) + ").", ChatFormatting.RED);
            return false;
        }
        listings.remove(l);
        save(); // antes de dar el objeto: si el servidor cae justo aquí, mejor perder la venta que duplicar el objeto
        give(buyer, l.item.copy());
        String buyerName = buyer.getGameProfile().getName();
        if (!TFEconomy.give(buyer.getServer(), l.seller, l.sellerName, l.price)) owed.merge(l.seller, l.price, Long::sum);
        log("compra " + buyerName, l.sellerName, l);
        tell(buyer, "Compraste " + describe(l.item) + " a " + l.sellerName + " por " + TFEconomy.format(l.price) + ".", ChatFormatting.GREEN);
        buyer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5F, 1.6F);
        String msg = buyerName + " compró tu " + describe(l.item) + " por " + TFEconomy.format(l.price) + ".";
        ServerPlayer seller = buyer.getServer().getPlayerList().getPlayer(l.seller);
        if (seller != null) {
            tell(seller, msg, ChatFormatting.GREEN);
            seller.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.6F, 1.0F);
        } else {
            news.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(msg);
        }
        save();
        return true;
    }

    /** Retira una publicación propia y devuelve el objeto. */
    public static boolean withdraw(ServerPlayer player, String id) {
        Listing l = find(id);
        if (l == null || !l.seller.equals(player.getUUID())) return false;
        listings.remove(l);
        save();
        give(player, l.item.copy());
        log("retira", player.getGameProfile().getName(), l);
        tell(player, "Retiraste " + describe(l.item) + " del GTS.", ChatFormatting.GRAY);
        return true;
    }

    /** Recoge lo devuelto número index. */
    public static void collect(ServerPlayer player, int index) {
        List<ItemStack> list = returns.get(player.getUUID());
        if (list == null || index < 0 || index >= list.size()) return;
        ItemStack item = list.remove(index);
        if (list.isEmpty()) returns.remove(player.getUUID());
        save();
        give(player, item);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Caducidad, pagos pendientes y avisos
    // ---------------------------------------------------------------------------------------------------------------

    private static void expire() {
        long now = System.currentTimeMillis();
        for (Listing l : List.copyOf(listings)) {
            if (now < l.expiresAt()) continue;
            listings.remove(l);
            returns.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.item);
            dirty = true;
            log("caduca", l.sellerName, l);
            ServerPlayer p = server.getPlayerList().getPlayer(l.seller);
            if (p != null) tell(p, "Tu " + describe(l.item) + " no se vendió en " + DAYS + " días: recógelo en el GTS.", ChatFormatting.YELLOW);
        }
    }

    private static void greet(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Long coins = owed.remove(uuid);
        if (coins != null) {
            if (TFEconomy.give(server, uuid, player.getGameProfile().getName(), coins)) {
                dirty = true;
                tell(player, "Cobraste " + TFEconomy.format(coins) + " de tus ventas en el GTS.", ChatFormatting.GREEN);
            } else {
                owed.put(uuid, coins);
            }
        }
        List<String> sold = news.remove(uuid);
        if (sold != null) {
            dirty = true;
            for (String line : sold) tell(player, line, ChatFormatting.GREEN);
        }
        int waiting = returns.getOrDefault(uuid, List.of()).size();
        if (waiting > 0) tell(player, "Tienes " + waiting + " cosa(s) para recoger en el GTS (abre el pad).", ChatFormatting.YELLOW);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Eventos
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        load();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        save();
        listings.clear();
        returns.clear();
        owed.clear();
        news.clear();
        prompts.clear();
        server = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        if (++ticks % 1200 == 0) expire();
        if (dirty && ticks % 100 == 0) save();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (server != null && event.getEntity() instanceof ServerPlayer player) greet(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        prompts.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Guardar
    // ---------------------------------------------------------------------------------------------------------------

    /** Lo que no se pudo leer (por ejemplo, un objeto de un mod que ya no está): se guarda tal cual, nunca se borra. */
    private static JsonArray unreadListings = new JsonArray();
    private static JsonObject unreadReturns = new JsonObject();

    private static void load() {
        listings.clear();
        returns.clear();
        owed.clear();
        news.clear();
        unreadListings = new JsonArray();
        unreadReturns = new JsonObject();
        java.nio.file.Path file = TFJson.worldFile(server, "gts.json");
        JsonObject root = TFJson.read(file);
        if (root == null) {
            if (Files.exists(file)) setAside(file);
            return;
        }
        try {
            if (root.has("publicaciones") && root.get("publicaciones").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("publicaciones")) {
                    try {
                        JsonObject o = e.getAsJsonObject();
                        ItemStack item = item(TFJson.str(o, "objeto", ""));
                        if (item.isEmpty()) throw new IllegalStateException("objeto ilegible");
                        listings.add(new Listing(TFJson.str(o, "id", ""), UUID.fromString(TFJson.str(o, "vendedor", "")),
                                TFJson.str(o, "nombre", "?"), item, TFJson.num(o, "precio", 0), TFJson.num(o, "fecha", 0)));
                    } catch (Exception ex) {
                        unreadListings.add(e);
                        TFClient.LOGGER.warn("GTS: una publicación no se pudo leer ({}); se guarda tal cual", ex.getMessage());
                    }
                }
            }
            JsonObject back = TFJson.obj(root, "recoger");
            for (String key : back.keySet()) {
                List<ItemStack> items = new ArrayList<>();
                JsonArray keep = new JsonArray();
                for (JsonElement e : back.getAsJsonArray(key)) {
                    ItemStack item = e.isJsonPrimitive() ? item(e.getAsString()) : ItemStack.EMPTY;
                    if (item.isEmpty()) keep.add(e);
                    else items.add(item);
                }
                UUID uuid = UUID.fromString(key);
                if (!items.isEmpty()) returns.put(uuid, items);
                if (!keep.isEmpty()) unreadReturns.add(key, keep);
            }
            JsonObject pay = TFJson.obj(root, "pendiente");
            for (String key : pay.keySet()) owed.put(UUID.fromString(key), pay.get(key).getAsLong());
            JsonObject n = TFJson.obj(root, "avisos");
            for (String key : n.keySet()) {
                List<String> lines = new ArrayList<>();
                for (JsonElement e : n.getAsJsonArray(key)) lines.add(e.getAsString());
                news.put(UUID.fromString(key), lines);
            }
        } catch (Exception e) {
            // Algo no cuadra en el archivo: se aparta entero (con todo lo de los jugadores) antes de que se pise
            TFClient.LOGGER.error("GTS: gts.json tiene algo que no se entiende", e);
            setAside(file);
        }
    }

    private static void setAside(java.nio.file.Path file) {
        try {
            java.nio.file.Path copy = file.resolveSibling("gts-roto-" + System.currentTimeMillis() + ".json");
            Files.copy(file, copy);
            TFClient.LOGGER.error("GTS: copia de seguridad en {}", copy.getFileName());
        } catch (IOException e) {
            TFClient.LOGGER.error("GTS: gts.json no se pudo copiar", e);
        }
    }

    private static void save() {
        if (server == null) return;
        dirty = false;
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();
        for (Listing l : listings) {
            JsonObject o = new JsonObject();
            o.addProperty("id", l.id);
            o.addProperty("vendedor", l.seller.toString());
            o.addProperty("nombre", l.sellerName);
            o.addProperty("objeto", l.item.save(new CompoundTag()).toString());
            o.addProperty("precio", l.price);
            o.addProperty("fecha", l.created);
            list.add(o);
        }
        for (JsonElement raw : unreadListings) list.add(raw);
        root.add("publicaciones", list);
        JsonObject back = new JsonObject();
        returns.forEach((uuid, items) -> {
            JsonArray a = new JsonArray();
            for (ItemStack item : items) a.add(item.save(new CompoundTag()).toString());
            back.add(uuid.toString(), a);
        });
        for (String key : unreadReturns.keySet()) {
            JsonArray a = back.has(key) ? back.getAsJsonArray(key) : new JsonArray();
            for (JsonElement raw : unreadReturns.getAsJsonArray(key)) a.add(raw);
            back.add(key, a);
        }
        root.add("recoger", back);
        JsonObject pay = new JsonObject();
        owed.forEach((uuid, coins) -> pay.addProperty(uuid.toString(), coins));
        root.add("pendiente", pay);
        JsonObject n = new JsonObject();
        news.forEach((uuid, lines) -> {
            JsonArray a = new JsonArray();
            lines.forEach(a::add);
            n.add(uuid.toString(), a);
        });
        root.add("avisos", n);
        TFJson.write(TFJson.worldFile(server, "gts.json"), root);
    }

    private static ItemStack item(String snbt) {
        try {
            return ItemStack.of(TagParser.parseTag(snbt));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static void log(String what, String who, Listing l) {
        try {
            String line = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " " + what + " | " + who + " | "
                    + l.item.getCount() + "x " + l.item.getItem() + " | " + l.price + " | " + l.id + System.lineSeparator();
            java.nio.file.Path file = TFJson.worldFile(server, "gts-registro.log");
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            TFClient.LOGGER.warn("GTS: no se pudo escribir el registro", e);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** Da el objeto; lo que no cabe cae a sus pies (también en creativo, donde Inventory.add lo borraría). */
    static void give(ServerPlayer player, ItemStack template) {
        int left = template.getCount();
        while (left > 0) {
            ItemStack stack = template.copy();
            int n = Math.min(left, stack.getMaxStackSize());
            stack.setCount(n);
            left -= n;
            boolean fits = player.getInventory().getSlotWithRemainingSpace(stack) != -1 || player.getInventory().getFreeSlot() != -1;
            if (!fits || !player.getInventory().add(stack) || !stack.isEmpty()) {
                if (!stack.isEmpty()) {
                    net.minecraft.world.entity.item.ItemEntity drop = player.drop(stack, false);
                    if (drop != null) {
                        drop.setNoPickUpDelay();
                        drop.setTarget(player.getUUID());
                    }
                }
            }
        }
        player.containerMenu.broadcastChanges();
    }

    public static String describe(ItemStack stack) {
        return stack.getCount() + "× " + stack.getHoverName().getString();
    }

    static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[GTS] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(color)));
    }
}
