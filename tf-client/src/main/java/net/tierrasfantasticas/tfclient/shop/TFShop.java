package net.tierrasfantasticas.tfclient.shop;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.items.TFItem;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig.Entry;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * La tienda del servidor: se abre desde el pad (app Tienda) y se compra y se vende con las monedas del servidor
 * ({@link TFEconomy}). Todo se configura en config/tfclient/tienda.json ({@link TFShopConfig}; /tf reload la vuelve a leer).
 * Solo se venden objetos «limpios» (sin nombre, encantamientos ni daño; o con el NBT exacto que pide la tienda) y nunca
 * los objetos de los sets. Los límites diarios por jugador se guardan en &lt;mundo&gt;/tfclient/tienda.json y las compras y
 * ventas, si registro = true, en &lt;mundo&gt;/tfclient/tienda-registro.log.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFShop {
    private static MinecraftServer server;
    private static String day = "";
    /** jugador → «c:clave» / «v:clave» → unidades compradas o vendidas hoy. */
    private static final Map<UUID, Map<String, Long>> today = new HashMap<>();
    private static boolean dirty;

    private TFShop() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        TFShopConfig.load();
        loadDaily();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        saveDaily();
        server = null;
        today.clear();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Comprar y vender
    // ---------------------------------------------------------------------------------------------------------------

    /** Compra lots lotes. Devuelve true si se hizo. */
    public static boolean buy(ServerPlayer player, Entry e, int lots) {
        if (!e.buyable() || lots <= 0) return false;
        if (!player.hasPermissions(e.permission())) {
            say(player, TFShopConfig.message("sinPermiso"), Map.of());
            return false;
        }
        long units = (long) lots * e.amount();
        long left = left(player.getUUID(), "c:" + e.key(), e.buyDaily());
        if (left <= 0 || units > left) {
            say(player, TFShopConfig.message("limiteCompra"), Map.of("objeto", name(e), "limite", Long.toString(e.buyDaily())));
            return false;
        }
        long price = Math.multiplyExact(e.buy(), (long) lots);
        if (!TFEconomy.take(player, price)) {
            say(player, TFShopConfig.message("sinDinero"), Map.of("precio", TFEconomy.format(price)));
            sound(player, false);
            return false;
        }
        if (e.giveItem()) {
            int max = e.item().getMaxStackSize();
            long remaining = units;
            while (remaining > 0) {
                int n = (int) Math.min(remaining, max);
                TFCoinShop.give(player, e.stack(n));
                remaining -= n;
            }
        }
        for (String command : e.commands()) {
            String c = command.replace("{player}", player.getGameProfile().getName()).replace("{uuid}", player.getUUID().toString())
                    .replace("{cantidad}", Long.toString(units)).replace("{lotes}", Integer.toString(lots));
            List<String> errors = TFBridge.run(player.server, c.startsWith("/") ? c.substring(1) : c);
            if (!errors.isEmpty()) TFClient.LOGGER.warn("TF Tienda: «{}» falló: {}", c, errors.get(0));
        }
        count(player.getUUID(), "c:" + e.key(), units);
        say(player, TFShopConfig.message("compra"), Map.of("cantidad", Long.toString(units), "objeto", name(e), "precio", TFEconomy.format(price)));
        sound(player, true);
        log(player, "COMPRA", e, units, price);
        return true;
    }

    /** Vende hasta lots lotes (o todos los que tenga si lots &lt;= 0). Devuelve true si vendió algo. */
    public static boolean sell(ServerPlayer player, Entry e, int lots, boolean quiet) {
        if (!e.sellable()) return false;
        if (!player.hasPermissions(e.permission())) {
            if (!quiet) say(player, TFShopConfig.message("sinPermiso"), Map.of());
            return false;
        }
        int have = count(player.getInventory(), e);
        long canLots = have / e.amount();
        if (canLots <= 0) {
            if (!quiet) say(player, TFShopConfig.message("sinObjetos"), Map.of("objeto", name(e)));
            return false;
        }
        long wantLots = lots <= 0 ? canLots : Math.min(lots, canLots);
        long left = left(player.getUUID(), "v:" + e.key(), e.sellDaily());
        wantLots = Math.min(wantLots, left / e.amount());
        if (wantLots <= 0) {
            if (!quiet) say(player, TFShopConfig.message("limiteVenta"), Map.of("objeto", name(e), "limite", Long.toString(e.sellDaily())));
            return false;
        }
        long units = wantLots * e.amount();
        remove(player.getInventory(), e, (int) units);
        long price = Math.multiplyExact(e.sell(), wantLots);
        TFEconomy.give(player.server, player.getUUID(), player.getGameProfile().getName(), price);
        count(player.getUUID(), "v:" + e.key(), units);
        player.containerMenu.broadcastChanges();
        if (!quiet) {
            say(player, TFShopConfig.message("venta"), Map.of("cantidad", Long.toString(units), "objeto", name(e), "precio", TFEconomy.format(price)));
            sound(player, true);
        }
        log(player, "VENTA", e, units, price);
        return true;
    }

    /** Vende todo lo vendible del inventario. */
    public static void sellAll(ServerPlayer player) {
        long before = TFEconomy.balance(player.server, player.getUUID()).orElse(-1);
        int lines = 0;
        long total = 0;
        for (TFShopConfig.Category c : TFShopConfig.categories.values()) {
            if (!player.hasPermissions(c.permission())) continue;
            for (Entry e : c.entries()) {
                if (!e.sellable()) continue;
                int have = count(player.getInventory(), e);
                if (have < e.amount()) continue;
                long lots = Math.min(have / e.amount(), left(player.getUUID(), "v:" + e.key(), e.sellDaily()) / e.amount());
                if (lots > 0 && sell(player, e, (int) lots, true)) {
                    lines++;
                    total += e.sell() * lots;
                }
            }
        }
        if (lines == 0) {
            say(player, TFShopConfig.message("nadaQueVender"), Map.of());
            sound(player, false);
            return;
        }
        say(player, TFShopConfig.message("ventaTodo"), Map.of("lineas", Integer.toString(lines), "precio", TFEconomy.format(total)));
        sound(player, true);
    }

    /** El objeto de la tienda que se puede vender con este montón del inventario (o null). */
    public static Entry sellableFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (TFShopConfig.Category c : TFShopConfig.categories.values()) {
            for (Entry e : c.entries()) {
                if (e.sellable() && matches(stack, e)) return e;
            }
        }
        return null;
    }

    /** Unidades vendibles de este objeto en el inventario (la mochila, la barra y la mano izquierda). */
    public static int count(Inventory inv, Entry e) {
        int n = 0;
        for (int i = 0; i < inv.items.size(); i++) if (matches(inv.items.get(i), e)) n += inv.items.get(i).getCount();
        for (ItemStack s : inv.offhand) if (matches(s, e)) n += s.getCount();
        return n;
    }

    private static void remove(Inventory inv, Entry e, int units) {
        for (int i = 0; i < inv.items.size() && units > 0; i++) units -= take(inv.items.get(i), e, units);
        for (ItemStack s : inv.offhand) if (units > 0) units -= take(s, e, units);
        inv.setChanged();
    }

    private static int take(ItemStack s, Entry e, int units) {
        if (!matches(s, e)) return 0;
        int n = Math.min(units, s.getCount());
        s.shrink(n);
        return n;
    }

    /** Mismo objeto y «limpio»: sin daño, sin NBT (o con el NBT exacto de la tienda) y nunca un objeto de los sets. */
    private static boolean matches(ItemStack s, Entry e) {
        if (s.isEmpty() || s.getItem() != e.item() || s.getItem() instanceof TFItem || s.isDamaged()) return false;
        if (e.nbt() != null) return e.nbt().equals(s.getTag());
        return !s.hasTag() || s.getTag().isEmpty();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Límites diarios
    // ---------------------------------------------------------------------------------------------------------------

    /** Unidades que aún se pueden comprar/vender hoy (Long.MAX_VALUE si no hay límite). */
    public static long left(UUID uuid, String key, long daily) {
        if (daily <= 0) return Long.MAX_VALUE;
        rollDay();
        return Math.max(0, daily - today.getOrDefault(uuid, Map.of()).getOrDefault(key, 0L));
    }

    private static void count(UUID uuid, String key, long units) {
        rollDay();
        today.computeIfAbsent(uuid, k -> new HashMap<>()).merge(key, units, Long::sum);
        dirty = true;
        saveDaily();
    }

    private static void rollDay() {
        String now = LocalDate.now().toString();
        if (!now.equals(day)) {
            day = now;
            today.clear();
            dirty = true;
        }
    }

    private static Path dailyFile() {
        return TFJson.worldFile(server, "tienda.json");
    }

    private static void loadDaily() {
        today.clear();
        day = LocalDate.now().toString();
        if (server == null) return;
        JsonObject json = TFJson.read(dailyFile());
        if (json == null || !day.equals(TFJson.str(json, "dia", ""))) return;
        JsonObject players = TFJson.obj(json, "jugadores");
        for (String id : players.keySet()) {
            try {
                UUID uuid = UUID.fromString(id);
                JsonObject counts = players.getAsJsonObject(id);
                Map<String, Long> map = new HashMap<>();
                for (String key : counts.keySet()) map.put(key, counts.get(key).getAsLong());
                today.put(uuid, map);
            } catch (RuntimeException ignored) {
                // entrada no válida
            }
        }
    }

    private static void saveDaily() {
        if (!dirty || server == null) return;
        dirty = false;
        JsonObject json = new JsonObject();
        json.addProperty("dia", day);
        JsonObject players = new JsonObject();
        today.forEach((uuid, counts) -> {
            JsonObject o = new JsonObject();
            counts.forEach(o::addProperty);
            players.add(uuid.toString(), o);
        });
        json.add("jugadores", players);
        TFJson.write(dailyFile(), json);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Textos, sonidos y registro
    // ---------------------------------------------------------------------------------------------------------------

    public static String name(Entry e) {
        return e.name().isBlank() ? e.stack(1).getHoverName().getString() : e.name();
    }

    /** Mientras es true, los mensajes salen como aviso dentro del pad en vez de en el chat (la app Tienda). */
    public static boolean toPad;

    /** Envía un mensaje de la configuración: &amp;a… son los colores de Minecraft; {clave} se sustituye. */
    public static void say(ServerPlayer player, String template, Map<String, String> values) {
        String text = template;
        for (Map.Entry<String, String> v : values.entrySet()) text = text.replace("{" + v.getKey() + "}", v.getValue());
        if (toPad) {
            String plain = ChatFormatting.stripFormatting(colors(text));
            if (plain != null && !plain.isBlank()) net.tierrasfantasticas.tfclient.pad.TFPadNet.notice(player, plain.trim());
            return;
        }
        player.sendSystemMessage(Component.literal(colors(text)));
    }

    public static String colors(String text) {
        return text.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    private static void sound(ServerPlayer player, boolean ok) {
        if (!TFShopConfig.sounds) return;
        if (ok) player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.6F, 1.4F);
        else player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.MASTER, 0.6F, 1.0F);
    }

    private static void log(ServerPlayer player, String what, Entry e, long units, long price) {
        if (!TFShopConfig.log || server == null) return;
        String line = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " " + what + " "
                + player.getGameProfile().getName() + " (" + player.getUUID() + ") " + units + "× " + e.itemId() + " [" + e.key() + "] "
                + price + System.lineSeparator();
        try {
            Path file = TFJson.worldFile(server, "tienda-registro.log");
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            TFClient.LOGGER.warn("TF Tienda: no se pudo escribir el registro: {}", ex.getMessage());
        }
    }

    /** Categorías que ve este jugador (las que tienen nivel de permiso alto solo las ve el staff). */
    public static List<TFShopConfig.Category> visible(ServerPlayer player) {
        List<TFShopConfig.Category> out = new ArrayList<>();
        for (TFShopConfig.Category c : TFShopConfig.categories.values()) if (player.hasPermissions(c.permission())) out.add(c);
        return out;
    }
}
