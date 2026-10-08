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
 * Kits gratis (las normas de Mojang no dejan vender kits): cada uno con su tiempo de espera (0 = una sola vez), lo
 * que trae y, si quiere, unas monedas. Se configuran en config/tfclient-kits.json; el staff puede guardar uno con lo
 * que lleva en la barra rápida: /tf web kits guardar &lt;id&gt; &lt;horas&gt; [nombre].
 */
public final class PadKits {
    record Kit(String id, String name, String desc, String icon, long cooldownSeconds, long coins, List<ItemStack> items) {
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
        if (k.cooldownSeconds <= 0) return -1;
        long next = last + k.cooldownSeconds * 1000;
        return Math.max(0, (next - System.currentTimeMillis()) / 1000);
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
            PadView.Builder b = PadView.of("kits").header("Kits gratis: reclámalos cuando estén listos.").empty("No hay kits configurados.");
            for (Kit k : KITS) {
                long left = left(player, k);
                String status = left == 0 ? "¡Listo!" : left < 0 ? "Ya lo reclamaste" : "Listo en " + time(left);
                String every = k.cooldownSeconds <= 0 ? "Una sola vez" : "Cada " + time(k.cooldownSeconds);
                List<String> lines = new ArrayList<>();
                lines.add(k.desc.isEmpty() ? every + " · " + k.items.size() + " objetos" : k.desc);
                lines.add(status + (k.desc.isEmpty() ? "" : "  ·  " + every));
                PadView.Btn claim = left == 0 ? PadView.Btn.of("RECLAMAR", "reclamar:" + k.id, PadView.GREEN) : PadView.Btn.off("ESPERA");
                b.row(new PadView.Row(new ItemStack(k.iconItem()), k.name, left == 0 ? 0x1E7C2C : 0x18265C, lines, -1,
                        k.coins > 0 ? "+" + TFEconomy.number(k.coins) : "", PadView.Btn.of("VER", "ver:" + k.id, PadView.BLUE), claim));
            }
            return b.build();
        }

        private PadView preview(ServerPlayer player, Kit k) {
            PadView.Builder b = PadView.of("kits").selected("ver:" + k.id).header(k.name + ": lo que trae.");
            for (ItemStack s : k.items) {
                b.row(new PadView.Row(s.copy(), s.getHoverName().getString(), 0x18265C, List.of("× " + s.getCount()), -1, "", null, null));
            }
            if (k.coins > 0) {
                b.row(new PadView.Row(new ItemStack(Items.GOLD_NUGGET), TFEconomy.format(k.coins), 0xC27A10, List.of("Monedas"), -1, "", null, null));
            }
            b.footer(PadView.Btn.of("VOLVER", "volver", PadView.BLUE));
            long left = left(player, k);
            b.footer(left == 0 ? PadView.Btn.of("RECLAMAR", "reclamar:" + k.id, PadView.GREEN) : PadView.Btn.off("ESPERA"));
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
                    TFPadNet.notice(player, "No se pudieron dar las monedas del kit. Inténtalo en un rato.");
                    return null;
                }
                JsonObject p = STORE_IMPL.player(player.getUUID());
                p.addProperty(k.id, System.currentTimeMillis());
                STORE_IMPL.changed();
                for (ItemStack s : k.items) give(player, s.copy());
                PadStats.add(player, PadStats.KITS, 1);
                player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.MASTER, 0.8F, 0.8F);
                TFPadNet.notice(player, "Reclamaste el kit " + k.name + ".");
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
        return FMLPaths.CONFIGDIR.get().resolve("tfclient-kits.json");
    }

    static void loadConfig() {
        Path file = configFile();
        JsonObject o = TFJson.read(file);
        if (o == null) {
            o = defaults();
            if (!Files.exists(file)) TFJson.write(file, o);
        }
        KITS.clear();
        if (o.has("kits") && o.get("kits").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("kits")) {
                try {
                    JsonObject k = e.getAsJsonObject();
                    List<ItemStack> items = new ArrayList<>();
                    for (JsonElement it : k.getAsJsonArray("objetos")) items.add(stack(it));
                    items.removeIf(ItemStack::isEmpty);
                    String id = TFJson.str(k, "id", "");
                    if (id.isEmpty() || id.length() > 32) id = "kit" + KITS.size();
                    KITS.add(new Kit(id, TFJson.str(k, "nombre", "Kit"), TFJson.str(k, "descripcion", ""),
                            TFJson.str(k, "icono", "minecraft:bundle"), TFJson.num(k, "esperaSegundos", 86400), TFJson.num(k, "monedas", 0), items));
                } catch (Exception ex) {
                    TFClient.LOGGER.warn("TF Pad: un kit no se pudo leer: {}", ex.getMessage());
                }
            }
        }
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
        o.addProperty("_ayuda", "Kits gratis. esperaSegundos: 0 = una sola vez. objetos: \"minecraft:bread 16\". "
                + "Con /tf web kits guardar <id> <horas> [nombre] el staff guarda uno con su barra rápida.");
        JsonArray kits = new JsonArray();
        kits.add(kit("inicial", "Kit inicial", "Lo justo para empezar tu aventura.", "minecraft:stone_pickaxe", 0, 100,
                "minecraft:stone_sword", "minecraft:stone_pickaxe", "minecraft:stone_axe", "minecraft:stone_shovel",
                "minecraft:bread 16", "minecraft:torch 32", "minecraft:oak_log 16", "minecraft:white_bed"));
        kits.add(kit("diario", "Kit diario", "Comida y un poco de todo, cada día.", "minecraft:cooked_beef", 86400, 50,
                "minecraft:cooked_beef 12", "minecraft:apple 4", "minecraft:torch 16", "minecraft:arrow 16"));
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
        k.addProperty("esperaSegundos", seconds);
        k.addProperty("monedas", coins);
        JsonArray a = new JsonArray();
        for (String s : items) a.add(s);
        k.add("objetos", a);
        return k;
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
