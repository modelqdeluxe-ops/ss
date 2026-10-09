package net.tierrasfantasticas.tfclient.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Tienda de monedas de la web: objetos del servidor que se compran con monedas ({@link TFEconomy}). El staff la llena
 * desde el juego y la web (pestaña «Tienda de monedas») se actualiza sola a través del puente; los jugadores compran
 * en la web y el puente manda «comprar», que cobra las monedas y da el objeto.
 * <pre>
 * /tf web tienda add &lt;precio&gt; [nombre]       pone a la venta lo que tienes en la mano (staff, nivel 3)
 * /tf web tienda precio &lt;id&gt; &lt;precio&gt;        cambia el precio
 * /tf web tienda quitar &lt;id&gt;                 lo quita de la venta
 * /tf web tienda lista | vaciar             lista los objetos / quita todos
 * /tf web tienda comprar &lt;jugador&gt; &lt;id&gt;     lo usa la web: cobra y entrega
 * </pre>
 * Se guarda en config/tfclient/tienda-monedas.json (con el objeto exacto: encantamientos, nombre, NBT).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFCoinShop {
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static boolean loaded;

    /** Un objeto a la venta. stack lleva la cantidad que se entrega. */
    public record Entry(String id, String name, long price, ItemStack stack) {
        String item() {
            return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }
    }

    private static final SuggestionProvider<CommandSourceStack> IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(ENTRIES.keySet(), builder);

    private TFCoinShop() {}

    private static Path file() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("tienda-monedas.json", "tfclient-tienda.json");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load();
    }

    /** Vuelve a leer config/tfclient/tienda-monedas.json (/tf reload). */
    public static void reload() {
        load();
    }

    private static void load() {
        ENTRIES.clear();
        loaded = true;
        JsonObject json = TFJson.read(file());
        if (json == null || !json.has("objetos")) return;
        for (JsonElement el : json.getAsJsonArray("objetos")) {
            try {
                JsonObject o = el.getAsJsonObject();
                CompoundTag tag = TagParser.parseTag(o.get("objeto").getAsString());
                ItemStack stack = ItemStack.of(tag);
                if (stack.isEmpty()) {
                    TFClient.LOGGER.warn("TF Tienda: «{}» ya no existe en el servidor, se ignora", o.get("id").getAsString());
                    continue;
                }
                Entry e = new Entry(o.get("id").getAsString(), o.get("nombre").getAsString(), o.get("precio").getAsLong(), stack);
                ENTRIES.put(e.id(), e);
            } catch (Exception ex) {
                TFClient.LOGGER.warn("TF Tienda: objeto no válido en {}: {}", file(), ex.getMessage());
            }
        }
        TFClient.LOGGER.info("TF Tienda: {} objetos en la tienda de monedas", ENTRIES.size());
    }

    private static void save() {
        JsonArray list = new JsonArray();
        for (Entry e : ENTRIES.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("nombre", e.name());
            o.addProperty("precio", e.price());
            o.addProperty("objeto", e.stack().save(new CompoundTag()).toString());
            list.add(o);
        }
        JsonObject json = new JsonObject();
        json.addProperty("_ayuda", "Tienda de monedas. Se edita desde el juego con /tf web tienda add|precio|quitar|vaciar.");
        json.add("objetos", list);
        TFJson.write(file(), json);
    }

    // --- Sincronizar con la web ---

    private static JsonObject addOp(Entry e) {
        JsonObject op = new JsonObject();
        op.addProperty("op", "add");
        op.addProperty("id", e.id());
        op.addProperty("item", e.item());
        op.addProperty("name", e.name());
        op.addProperty("count", e.stack().getCount());
        op.addProperty("price", e.price());
        return op;
    }

    /** Al conectar con la web se le manda la tienda entera (el servidor manda). */
    public static List<JsonObject> snapshot() {
        if (!loaded) load();
        List<JsonObject> ops = new ArrayList<>();
        JsonObject clear = new JsonObject();
        clear.addProperty("op", "clear");
        ops.add(clear);
        for (Entry e : ENTRIES.values()) ops.add(addOp(e));
        return ops;
    }

    // --- Comandos ---

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("tienda")
                .then(Commands.literal("add").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("precio", LongArgumentType.longArg(1, 1_000_000_000L))
                                .executes(ctx -> add(ctx, null))
                                .then(Commands.argument("nombre", StringArgumentType.greedyString())
                                        .executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "nombre"))))))
                .then(Commands.literal("precio").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                                .then(Commands.argument("precio", LongArgumentType.longArg(1, 1_000_000_000L)).executes(TFCoinShop::price))))
                .then(Commands.literal("quitar").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS).executes(TFCoinShop::remove)))
                .then(Commands.literal("lista").requires(s -> s.hasPermission(3)).executes(TFCoinShop::list))
                .then(Commands.literal("vaciar").requires(s -> s.hasPermission(3)).executes(TFCoinShop::clear))
                .then(Commands.literal("comprar").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS).executes(TFCoinShop::buy))));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Ten en la mano el objeto que quieres vender (con la cantidad que se entregará)."));
            return 0;
        }
        if (!loaded) load();
        long price = LongArgumentType.getLong(ctx, "precio");
        String label = name != null && !name.isBlank() ? name.trim() : held.getHoverName().getString();
        if (label.length() > 64) label = label.substring(0, 64);
        String id = uniqueId(label);
        Entry entry = new Entry(id, label, price, held.copy());
        ENTRIES.put(id, entry);
        save();
        TFBridge.queueShop(addOp(entry));
        ctx.getSource().sendSuccess(() -> Component.literal("A la venta: " + held.getCount() + "× " + entry.name() + " por "
                + TFEconomy.format(price) + " (id: " + id + "). La web se actualiza en unos segundos.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int price(CommandContext<CommandSourceStack> ctx) {
        Entry e = ENTRIES.get(StringArgumentType.getString(ctx, "id"));
        if (e == null) return unknown(ctx);
        Entry updated = new Entry(e.id(), e.name(), LongArgumentType.getLong(ctx, "precio"), e.stack());
        ENTRIES.put(e.id(), updated);
        save();
        TFBridge.queueShop(addOp(updated));
        ctx.getSource().sendSuccess(() -> Component.literal(e.name() + " ahora cuesta " + TFEconomy.format(updated.price()))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        Entry e = ENTRIES.remove(StringArgumentType.getString(ctx, "id"));
        if (e == null) return unknown(ctx);
        save();
        JsonObject op = new JsonObject();
        op.addProperty("op", "remove");
        op.addProperty("id", e.id());
        TFBridge.queueShop(op);
        ctx.getSource().sendSuccess(() -> Component.literal("Quitado de la tienda: " + e.name()).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        if (ENTRIES.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("La tienda de monedas está vacía. Añade con /tf web tienda add <precio>.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Tienda de monedas (" + ENTRIES.size() + "):").withStyle(ChatFormatting.GOLD), false);
        for (Entry e : ENTRIES.values()) {
            Component line = Component.literal(" • " + e.id()).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" — " + e.stack().getCount() + "× " + e.name() + " · " + TFEconomy.format(e.price()))
                            .withStyle(ChatFormatting.GRAY));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return ENTRIES.size();
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int n = ENTRIES.size();
        ENTRIES.clear();
        save();
        JsonObject op = new JsonObject();
        op.addProperty("op", "clear");
        TFBridge.queueShop(op);
        ctx.getSource().sendSuccess(() -> Component.literal("Tienda vaciada (" + n + " objetos).").withStyle(ChatFormatting.YELLOW), true);
        return n;
    }

    private static int unknown(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("No hay ningún objeto con ese id. Míralos con /tf web tienda lista."));
        return 0;
    }

    private static String uniqueId(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "objeto";
        if (base.length() > 32) base = base.substring(0, 32).replaceAll("-+$", "");
        String id = base;
        for (int i = 2; ENTRIES.containsKey(id); i++) id = base + "-" + i;
        return id;
    }

    // --- Compra desde la web ---

    /** /tf web tienda comprar: cobra las monedas y da el objeto. Si falla, el motivo le llega a la web tal cual. */
    private static int buy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
        if (!loaded) load();
        Entry e = ENTRIES.get(StringArgumentType.getString(ctx, "id"));
        if (e == null) {
            ctx.getSource().sendFailure(Component.literal("Ese objeto ya no está a la venta. No se ha cobrado nada."));
            return 0;
        }
        if (!TFEconomy.take(player, e.price())) {
            OptionalLong have = TFEconomy.balance(player.getServer(), player.getUUID());
            ctx.getSource().sendFailure(Component.literal("No tenías bastantes " + TFServerConfig.currency() + " (cuesta "
                    + TFEconomy.format(e.price()) + (have.isPresent() ? ", tienes " + TFEconomy.format(have.getAsLong()) : "")
                    + "). No se ha cobrado nada."));
            player.sendSystemMessage(Component.literal("La compra de la web no se pudo hacer: " + e.name() + " cuesta "
                    + TFEconomy.format(e.price()) + ".").withStyle(ChatFormatting.RED));
            return 0;
        }
        give(player, e.stack());
        player.sendSystemMessage(Component.literal("✔ Compraste " + e.stack().getCount() + "× " + e.name() + " por "
                + TFEconomy.format(e.price()) + ".").withStyle(ChatFormatting.GREEN));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5F, 1.6F);
        return 1;
    }

    /** Da el objeto (en montones normales) y tira al suelo lo que no quepa. */
    public static void give(ServerPlayer player, ItemStack template) {
        int left = template.getCount();
        while (left > 0) {
            ItemStack stack = template.copy();
            int n = Math.min(left, stack.getMaxStackSize());
            stack.setCount(n);
            left -= n;
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                ItemEntity drop = player.drop(stack, false);
                if (drop != null) {
                    drop.setNoPickUpDelay();
                    drop.setTarget(player.getUUID());
                }
            }
        }
        player.containerMenu.broadcastChanges();
    }

}
