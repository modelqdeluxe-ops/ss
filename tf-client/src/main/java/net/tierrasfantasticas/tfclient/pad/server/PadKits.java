package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Kits gratis (las normas de Mojang no dejan vender kits). Cada uno tiene su tipo: «unico» (de inicio: una sola vez),
 * «semanal» (cada 7 días) u «horas» (cada esperaSegundos), lo que trae y, si quiere, unas monedas. Lo diario está en
 * Recompensas. Se configuran en config/tfclient/kits.json; el staff los crea y cambia desde el pad de administrador
 * (con lo que lleva en el inventario) o con /tf web kits guardar &lt;id&gt; &lt;horas&gt; [nombre].
 */
public final class PadKits {
    /** Sin «diario» desde la 1.3.26: lo diario va en Recompensas (los kits diarios de antes se pasan allí solos). */
    public static final List<String> TYPES = List.of("unico", "semanal", "horas");

    record Kit(String id, String name, String desc, String icon, long cooldownSeconds, long coins, List<ItemStack> items, String type) {
        Item iconItem() {
            Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(icon));
            return i == null || i == Items.AIR ? Items.BUNDLE : i;
        }
    }

    private static final List<Kit> KITS = new ArrayList<>();
    static final JsonStore STORE_IMPL = new JsonStore("kits.json") {
        @Override
        void loaded(MinecraftServer server) {
            loadConfig();
        }
    };
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.App APP = new App();

    private PadKits() {}

    /** Segundos que faltan para poder reclamarlo (0 = ya; -1 = nunca más). */
    static long left(ServerPlayer player, Kit k) {
        JsonObject p = STORE_IMPL.playerIfAny(player.getUUID());
        if (p == null || !p.has(k.id)) return 0;
        long last = p.get(k.id).getAsLong();
        switch (k.type) {
            case "unico" -> {
                return -1;
            }
            case "diario" -> {
                // se renueva a medianoche (hora del servidor)
                java.time.ZoneId zone = java.time.ZoneId.systemDefault();
                java.time.LocalDate day = java.time.Instant.ofEpochMilli(last).atZone(zone).toLocalDate();
                if (!day.equals(java.time.LocalDate.now(zone))) return 0;
                long midnight = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                return Math.max(1, (midnight - System.currentTimeMillis()) / 1000);
            }
            default -> {
                long seconds = k.type.equals("semanal") ? 7 * 86400L : Math.max(1, k.cooldownSeconds);
                return Math.max(0, (last + seconds * 1000 - System.currentTimeMillis()) / 1000);
            }
        }
    }

    /**
     * Lo que trae un kit o una recompensa, debajo de su cabecera: hasta 8 cosas, en tarjetas (el objeto al doble con su
     * cantidad y su nombre); más, en casillas pequeñas para que se vean todas. Las monedas, en su propia tarjeta.
     */
    static void contents(PadView.Builder b, List<ItemStack> items, long coins) {
        int n = items.size() + (coins > 0 ? 1 : 0);
        ItemStack coin = new ItemStack(net.tierrasfantasticas.tfclient.items.TFItems.COIN.get());
        if (n > 8) {
            for (ItemStack s : items) b.cell(s.copy(), "", 0x18265C, "", false);
            if (coins > 0) b.cell(coin, PadShop.price(coins), 0xC27A10, "", false);
            return;
        }
        for (ItemStack s : items) {
            b.card(s.copy(), s.getHoverName().getString(), 0x3496FA, "", "", false);
        }
        if (coins > 0) b.card(coin, TFEconomy.format(coins), 0xF6B628, "", "", false);
    }

    static String typeText(Kit k) {
        return switch (k.type) {
            case "unico" -> "Una sola vez";
            case "diario" -> "Cada día";
            case "semanal" -> "Cada semana";
            default -> "Cada " + time(k.cooldownSeconds);
        };
    }

    static String time(long s) {
        if (s >= 86400) return (s / 86400) + " d " + (s % 86400 / 3600) + " h";
        if (s >= 3600) return (s / 3600) + " h " + (s % 3600 / 60) + " min";
        if (s >= 60) return (s / 60) + " min";
        return s + " s";
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            Kit open = null;
            for (Kit k : KITS) if (tab.equals("ver:" + k.id)) open = k;
            if (open != null) return preview(player, open);
            PadView.Builder b = PadView.of("kits").empty("Sin kits.").cards();
            int ready = 0;
            for (Kit k : KITS) {
                long left = left(player, k);
                if (left == 0) ready++;
                String status = left == 0 ? "Listo" : left < 0 ? "Reclamado" : "en " + time(left);
                b.card(new ItemStack(k.iconItem()), k.name, left == 0 ? 0x40C850 : left < 0 ? 0xAABAD2 : 0xF6B628, status,
                        left == 0 ? PadView.TONE_GREEN : left < 0 ? PadView.TONE_GRAY : 0, -1, "", "tab:ver:" + k.id, left == 0);
            }
            if (!KITS.isEmpty()) b.header(ready == 0 ? "Ninguno listo."
                    : ready + (ready == 1 ? " kit listo" : " kits listos") + " para reclamar. Pulsa uno para verlo.");
            return b.build();
        }

        private PadView preview(ServerPlayer player, Kit k) {
            long left = left(player, k);
            PadView.Builder b = PadView.of("kits").selected("ver:" + k.id);
            List<String> lines = new ArrayList<>();
            if (!k.desc.isEmpty()) lines.add(k.desc);
            lines.add(typeText(k) + " · " + (left == 0 ? "listo para reclamar." : left < 0 ? "ya lo reclamaste." : "vuelve en " + time(left) + "."));
            b.hero(new PadView.Row(new ItemStack(k.iconItem()), k.name, left == 0 ? 0x40C850 : 0xF6B628, lines, -1, "", null, null));
            contents(b, k.items, k.coins);
            b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
            b.footer(left == 0 ? PadView.Btn.of("RECLAMAR", "reclamar:" + k.id, PadView.GREEN)
                    : PadView.Btn.off(left < 0 ? "RECLAMADO" : "EN " + time(left).toUpperCase()));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("ver:")) return action;
            if (action.equals("volver")) return "";
            if (!action.startsWith("reclamar:")) return null;
            String id = action.substring(9);
            for (Kit k : KITS) {
                if (!k.id.equals(id) || left(player, k) != 0) continue;
                // primero las monedas: si la economía no puede pagar, el kit sigue sin reclamar
                if (k.coins > 0 && !TFEconomy.give(player.getServer(), player.getUUID(), player.getGameProfile().getName(), k.coins)) {
                    TFPadNet.notice(player, "Error al dar las monedas del kit.");
                    return null;
                }
                JsonObject p = STORE_IMPL.player(player.getUUID());
                p.addProperty(k.id, System.currentTimeMillis());
                STORE_IMPL.changed();
                for (ItemStack s : k.items) give(player, s.copy());
                PadStats.add(player, PadStats.KITS, 1);
                player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.MASTER, 0.8F, 0.8F);
                TFPadNet.notice(player, "Reclamaste " + k.name + ".");
                if (k.coins > 0) TFPadNet.sendState(player);
                return "";
            }
            return null;
        }
    }

    /** Da el objeto en montones normales; lo que no cabe cae a sus pies. */
    static void give(ServerPlayer player, ItemStack stack) {
        while (stack.getCount() > stack.getMaxStackSize()) giveOne(player, stack.split(stack.getMaxStackSize()));
        giveOne(player, stack);
    }

    private static void giveOne(ServerPlayer player, ItemStack stack) {
        boolean fits = player.getInventory().getSlotWithRemainingSpace(stack) != -1 || player.getInventory().getFreeSlot() != -1;
        if (!fits || !player.getInventory().add(stack) || !stack.isEmpty()) {
            if (!stack.isEmpty()) {
                var drop = player.drop(stack, false);
                if (drop != null) {
                    drop.setNoPickUpDelay();
                    drop.setTarget(player.getUUID());
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Configuración y comando del staff
    // ---------------------------------------------------------------------------------------------------------------

    private static Path configFile() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("kits.json", "tfclient-kits.json");
    }

    static void loadConfig() {
        Path file = configFile();
        JsonObject o = TFJson.read(file);
        if (o == null) {
            o = defaults();
            if (!Files.exists(file)) TFJson.write(file, o);
        }
        KITS.clear();
        // los kits diarios pasan a Recompensas (se quitan de aquí una sola vez, sin perder lo que traían)
        if (o.has("kits") && o.get("kits").isJsonArray()) {
            List<JsonObject> daily = new ArrayList<>();
            JsonArray keep = new JsonArray();
            for (JsonElement e : o.getAsJsonArray("kits")) {
                if (e.isJsonObject() && isDaily(e.getAsJsonObject())) daily.add(e.getAsJsonObject());
                else keep.add(e);
            }
            if (!daily.isEmpty() && PadRewards.adopt(daily)) {
                o.add("kits", keep);
                TFJson.write(file, o);
            }
        }
        if (o.has("kits") && o.get("kits").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("kits")) {
                try {
                    JsonObject k = e.getAsJsonObject();
                    List<ItemStack> items = new ArrayList<>();
                    for (JsonElement it : k.getAsJsonArray("objetos")) items.add(stack(it));
                    items.removeIf(ItemStack::isEmpty);
                    String id = TFJson.str(k, "id", "");
                    if (id.isEmpty() || id.length() > 32) id = "kit" + KITS.size();
                    long seconds = TFJson.num(k, "esperaSegundos", 86400);
                    String type = TFJson.str(k, "tipo", seconds <= 0 ? "unico" : seconds == 604800 ? "semanal" : "horas");
                    if (!TYPES.contains(type)) type = "horas";
                    KITS.add(new Kit(id, TFJson.str(k, "nombre", "Kit"), TFJson.str(k, "descripcion", ""),
                            TFJson.str(k, "icono", "minecraft:bundle"), seconds, TFJson.num(k, "monedas", 0), items, type));
                } catch (Exception ex) {
                    TFClient.LOGGER.warn("TF Pad: un kit no se pudo leer: {}", ex.getMessage());
                }
            }
        }
    }

    private static boolean isDaily(JsonObject k) {
        String type = TFJson.str(k, "tipo", "");
        return type.equals("diario") || type.isEmpty() && TFJson.num(k, "esperaSegundos", -1) == 86400;
    }

    /** "minecraft:bread 16" o {"nbt": "..."} (lo que guarda el comando). */
    private static ItemStack stack(JsonElement e) {
        if (e.isJsonObject() && e.getAsJsonObject().has("nbt")) {
            try {
                return ItemStack.of(TagParser.parseTag(e.getAsJsonObject().get("nbt").getAsString()));
            } catch (Exception ex) {
                return ItemStack.EMPTY;
            }
        }
        String[] parts = e.getAsString().trim().split("\\s+");
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(parts[0]));
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        int n = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        return new ItemStack(item, Math.max(1, Math.min(n, item.getMaxStackSize() * 9)));
    }

    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        o.addProperty("_ayuda", "Kits gratis. tipo: unico (una sola vez), semanal u horas (cada esperaSegundos); lo diario va en recompensas.json. "
                + "objetos: \"minecraft:bread 16\". Se crean y cambian desde el pad de administrador.");
        JsonArray kits = new JsonArray();
        kits.add(kit("inicial", "Kit inicial", "Lo justo para empezar tu aventura.", "minecraft:stone_pickaxe", 0, 100,
                "minecraft:stone_sword", "minecraft:stone_pickaxe", "minecraft:stone_axe", "minecraft:stone_shovel",
                "minecraft:bread 16", "minecraft:torch 32", "minecraft:oak_log 16", "minecraft:white_bed"));
        kits.add(kit("semanal", "Kit semanal", "Una ayuda más grande cada semana.", "minecraft:iron_pickaxe", 604800, 300,
                "minecraft:iron_pickaxe", "minecraft:iron_ingot 8", "minecraft:golden_carrot 16", "minecraft:experience_bottle 8"));
        o.add("kits", kits);
        return o;
    }

    private static JsonObject kit(String id, String name, String desc, String icon, long seconds, long coins, String... items) {
        JsonObject k = new JsonObject();
        k.addProperty("id", id);
        k.addProperty("nombre", name);
        k.addProperty("descripcion", desc);
        k.addProperty("icono", icon);
        k.addProperty("tipo", seconds <= 0 ? "unico" : seconds == 604800 ? "semanal" : "horas");
        k.addProperty("esperaSegundos", seconds);
        k.addProperty("monedas", coins);
        JsonArray a = new JsonArray();
        for (String s : items) a.add(s);
        k.add("objetos", a);
        return k;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Para el pad de administrador
    // ---------------------------------------------------------------------------------------------------------------

    static List<Kit> kits() {
        return List.copyOf(KITS);
    }

    static Kit kit(String id) {
        for (Kit k : KITS) if (k.id.equals(id)) return k;
        return null;
    }

    /** Cambia el kit id en kits.json con f (sobre su objeto JSON) y recarga. Devuelve false si no existe. */
    static boolean edit(String id, java.util.function.Consumer<JsonObject> f) {
        JsonObject o = TFJson.read(configFile());
        if (o == null || !o.has("kits")) return false;
        for (JsonElement e : o.getAsJsonArray("kits")) {
            JsonObject k = e.getAsJsonObject();
            if (!TFJson.str(k, "id", "").equals(id)) continue;
            f.accept(k);
            TFJson.write(configFile(), o);
            loadConfig();
            return true;
        }
        return false;
    }

    static void delete(String id) {
        JsonObject o = TFJson.read(configFile());
        if (o == null || !o.has("kits")) return;
        JsonArray keep = new JsonArray();
        for (JsonElement e : o.getAsJsonArray("kits")) if (!TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) keep.add(e);
        o.add("kits", keep);
        TFJson.write(configFile(), o);
        loadConfig();
    }

    /** Lo que lleva el jugador en el inventario (mochila y barra), como lista de objetos de un kit. */
    static JsonArray inventoryItems(ServerPlayer player) {
        JsonArray items = new JsonArray();
        for (ItemStack s : player.getInventory().items) {
            if (s.isEmpty()) continue;
            JsonObject it = new JsonObject();
            it.addProperty("nbt", s.save(new CompoundTag()).toString());
            items.add(it);
        }
        return items;
    }

    /** Un kit nuevo con lo que lleva en el inventario (o vacío, para llenarlo con el selector). Devuelve su id. */
    static String createFromInventory(ServerPlayer player, String name, String type) {
        JsonArray items = inventoryItems(player);
        JsonObject o = TFJson.read(configFile());
        if (o == null) o = defaults();
        if (!o.has("kits")) o.add("kits", new JsonArray());
        String base = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.isEmpty()) base = "kit";
        if (base.length() > 24) base = base.substring(0, 24);
        String id = base;
        for (int i = 2; kit(id) != null; i++) id = base + "_" + i;
        ItemStack first = new ItemStack(Items.BUNDLE);
        for (ItemStack s : player.getInventory().items) if (!s.isEmpty()) {
            first = s;
            break;
        }
        JsonObject k = new JsonObject();
        k.addProperty("id", id);
        k.addProperty("nombre", name);
        k.addProperty("descripcion", "");
        k.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(first.getItem())));
        k.addProperty("tipo", TYPES.contains(type) ? type : "unico");
        k.addProperty("esperaSegundos", type.equals("semanal") ? 604800 : type.equals("horas") ? 86400 : 0);
        k.addProperty("monedas", 0);
        k.add("objetos", items);
        o.getAsJsonArray("kits").add(k);
        TFJson.write(configFile(), o);
        loadConfig();
        return id;
    }

    /** /tf web kits guardar|quitar|recargar (staff). */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("kits").requires(s -> s.hasPermission(3))
                .then(Commands.literal("recargar").executes(ctx -> {
                    loadConfig();
                    ctx.getSource().sendSuccess(() -> Component.literal("Kits recargados: " + KITS.size()), true);
                    return KITS.size();
                }))
                .then(Commands.literal("quitar").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    JsonObject o = TFJson.read(configFile());
                    if (o == null || !o.has("kits")) return 0;
                    JsonArray keep = new JsonArray();
                    for (JsonElement e : o.getAsJsonArray("kits")) if (!TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) keep.add(e);
                    o.add("kits", keep);
                    TFJson.write(configFile(), o);
                    loadConfig();
                    ctx.getSource().sendSuccess(() -> Component.literal("Kit " + id + " quitado."), true);
                    return 1;
                })))
                .then(Commands.literal("guardar").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("horas", IntegerArgumentType.integer(0, 24 * 365))
                                .executes(ctx -> save(ctx.getSource(), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "horas"), ""))
                                .then(Commands.argument("nombre", StringArgumentType.greedyString())
                                        .executes(ctx -> save(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "horas"), StringArgumentType.getString(ctx, "nombre")))))));
    }

    private static int save(CommandSourceStack source, String id, int hours, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (id.length() > 32) {
            source.sendFailure(Component.literal("El id del kit es muy largo (máx. 32)."));
            return 0;
        }
        JsonArray items = new JsonArray();
        String icon = "minecraft:bundle";
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            if (icon.equals("minecraft:bundle")) icon = String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem()));
            JsonObject it = new JsonObject();
            it.addProperty("nbt", s.save(new CompoundTag()).toString());
            items.add(it);
        }
        if (items.isEmpty()) {
            source.sendFailure(Component.literal("Pon en tu barra rápida lo que debe traer el kit."));
            return 0;
        }
        JsonObject o = TFJson.read(configFile());
        if (o == null) o = defaults();
        JsonArray keep = new JsonArray();
        if (o.has("kits")) for (JsonElement e : o.getAsJsonArray("kits")) if (!TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) keep.add(e);
        JsonObject k = new JsonObject();
        k.addProperty("id", id);
        k.addProperty("nombre", name.isBlank() ? "Kit " + id : name);
        k.addProperty("descripcion", "");
        k.addProperty("icono", icon);
        k.addProperty("tipo", hours == 0 ? "unico" : hours == 168 ? "semanal" : "horas");
        k.addProperty("esperaSegundos", hours * 3600L);
        k.addProperty("monedas", 0);
        k.add("objetos", items);
        keep.add(k);
        o.add("kits", keep);
        TFJson.write(configFile(), o);
        loadConfig();
        int n = items.size();
        source.sendSuccess(() -> Component.literal("Kit " + id + " guardado con " + n + " objetos (" + (hours == 0 ? "una sola vez" : "cada " + hours + " h") + ").")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
