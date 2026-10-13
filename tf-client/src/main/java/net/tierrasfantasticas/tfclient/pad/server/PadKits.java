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
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.server.TFRanks;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Kits para los jugadores. Cada uno tiene su nombre, una descripción, su icono, lo que trae (objetos, monedas y monedas
 * verdes: las monedas se ingresan en el monedero, nunca se dan como objeto), cada cuánto se reclama («unico»: una sola
 * vez; «semanal»: cada 7 días; «horas»: cada esperaSegundos, 86400 = cada día), quién puede reclamarlo («rango»: "" todos,
 * «staff» o el id corto de un rango de la tienda, que vale para ese rango y los de más nivel), un precio opcional en
 * monedas del juego («precio», 0 = gratis) y si lo ven los jugadores («activado»: los kits a medio crear son borradores).
 * Se configuran en config/tfclient/kits.json; el staff los crea y cambia paso a paso desde el pad de administrador (KITS)
 * o con /tf web kits guardar &lt;id&gt; &lt;horas&gt; [nombre].
 */
public final class PadKits {
    /** Sin «diario» desde la 1.3.26 (el calendario diario va en Recompensas); «horas» con 24 h es «cada día». */
    public static final List<String> TYPES = List.of("unico", "semanal", "horas");

    record Kit(String id, String name, String desc, String icon, long cooldownSeconds, long coins, long greens, List<ItemStack> items,
               String type, String rank, long price, boolean on) {
        Item iconItem() {
            Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(icon));
            if (i != null && i != Items.AIR) return i;
            return items.isEmpty() ? Items.BUNDLE : items.get(0).getItem();
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

    // ---------------------------------------------------------------------------------------------------------------
    // Quién puede reclamarlo
    // ---------------------------------------------------------------------------------------------------------------

    /** «Todos los jugadores», «Solo el staff» o «Rango Rey o superior». */
    static String rankText(String rank) {
        if (rank == null || rank.isEmpty()) return "Todos los jugadores";
        if (rank.equals("staff")) return "Solo el staff";
        TFRanks.Rank r = TFRanks.find(rank);
        return "Rango " + (r != null && !r.name().isEmpty() ? r.name() : rank) + " o superior";
    }

    /** ¿Puede reclamarlo este jugador (por su rango)? El staff siempre puede (para probarlo). */
    static boolean allowed(ServerPlayer player, Kit k) {
        if (k.rank.isEmpty() || PadServer.isAdmin(player)) return true;
        if (k.rank.equals("staff")) return false;
        TFRanks.Rank mine = TFRanks.full(TFRanks.of(player.getUUID()));
        if (mine == null) return false;
        TFRanks.Rank need = TFRanks.find(k.rank);
        if (need == null) return mine.key().equalsIgnoreCase(k.rank) || mine.id().equalsIgnoreCase(k.rank);
        return mine.tier() >= need.tier();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que trae
    // ---------------------------------------------------------------------------------------------------------------

    /** El icono de las monedas (de oro) o el de las verdes. */
    static ItemStack coinIcon(boolean green) {
        return new ItemStack(green ? net.tierrasfantasticas.tfclient.items.TFItems.GREEN_COIN.get()
                : net.tierrasfantasticas.tfclient.items.TFItems.COIN.get());
    }

    /** «+25 monedas · +3 monedas verdes» (o "" si no trae ninguna). */
    static String coinsText(long coins, long greens) {
        String out = coins > 0 ? "+" + TFEconomy.number(coins) + (coins == 1 ? " moneda" : " monedas") : "";
        if (greens > 0) out += (out.isEmpty() ? "" : " · ") + "+" + TFEconomy.number(greens) + (greens == 1 ? " moneda verde" : " monedas verdes");
        return out;
    }

    /**
     * Lo que trae un kit o una recompensa, debajo de su cabecera: hasta 8 cosas, en tarjetas (el objeto al doble con su
     * cantidad y su nombre); más, en casillas pequeñas para que se vean todas. Las monedas van primero, cada una en su
     * tarjeta con su icono y «+N» en verde: se ingresan en el monedero, no son un objeto.
     */
    static void contents(PadView.Builder b, List<ItemStack> items, long coins, long greens) {
        int n = items.size() + (coins > 0 ? 1 : 0) + (greens > 0 ? 1 : 0);
        if (n > 8) {
            if (coins > 0) b.cell(coinIcon(false), "+" + PadView.shortNumber(coins), 0xC27A10, "", false);
            if (greens > 0) b.cell(coinIcon(true), "+" + PadView.shortNumber(greens), 0x1E9E46, "", false);
            for (ItemStack s : items) b.cell(s.copy(), "", 0x18265C, "", false);
            return;
        }
        if (coins > 0) {
            b.card(coinIcon(false), coins == 1 ? "Moneda" : "Monedas", 0xF6B628, "+" + PadView.money(coins), PadView.TONE_GREEN, -1, "", "", false);
        }
        if (greens > 0) {
            b.card(coinIcon(true), greens == 1 ? "Moneda verde" : "Monedas verdes", 0x40C850, "+" + PadView.number(greens), PadView.TONE_GREEN,
                    -1, "", "", false);
        }
        for (ItemStack s : items) b.card(s.copy(), s.getHoverName().getString(), 0x3496FA, "", "", false);
    }

    static String typeText(Kit k) {
        return typeText(k.type, k.cooldownSeconds);
    }

    /** «Una sola vez», «Cada semana», «Cada día», «Cada 3 días», «Cada 12 h». */
    static String typeText(String type, long seconds) {
        return switch (type) {
            case "unico" -> "Una sola vez";
            case "diario" -> "Cada día";
            case "semanal" -> "Cada semana";
            default -> {
                long s = Math.max(1, seconds);
                if (s == 86400) yield "Cada día";
                if (s % 86400 == 0) yield "Cada " + (s / 86400) + " días";
                if (s % 3600 == 0) yield "Cada " + (s / 3600) + " h";
                yield "Cada " + time(s);
            }
        };
    }

    static String time(long s) {
        if (s >= 86400) return (s / 86400) + " d " + (s % 86400 / 3600) + " h";
        if (s >= 3600) return (s / 3600) + " h " + (s % 3600 / 60) + " min";
        if (s >= 60) return (s / 60) + " min";
        return s + " s";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La app del jugador
    // ---------------------------------------------------------------------------------------------------------------

    /** Los kits que ven los jugadores (los borradores no). */
    static List<Kit> visible() {
        List<Kit> out = new ArrayList<>();
        for (Kit k : KITS) if (k.on) out.add(k);
        return out;
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            for (Kit k : visible()) if (tab.equals("ver:" + k.id)) return preview(player, k);
            PadView.Builder b = PadView.of("kits").empty("Sin kits.").cards();
            int ready = 0;
            List<Kit> kits = visible();
            for (Kit k : kits) {
                long left = left(player, k);
                boolean can = allowed(player, k);
                if (left == 0 && can) ready++;
                String status = !can ? (k.rank.equals("staff") ? "Staff" : "Con rango") : left == 0 ? (k.price > 0 ? PadView.money(k.price) : "Listo")
                        : left < 0 ? "Reclamado" : "en " + time(left);
                int tone = !can || left < 0 ? PadView.TONE_GRAY : left == 0 ? (k.price > 0 ? PadView.TONE_GOLD : PadView.TONE_GREEN) : 0;
                b.card(new ItemStack(k.iconItem()), k.name, can && left == 0 ? 0x40C850 : left < 0 || !can ? 0xAABAD2 : 0xF6B628, status,
                        tone, -1, "", "tab:ver:" + k.id, can && left == 0);
            }
            if (!kits.isEmpty()) b.header(ready == 0 ? "Ninguno listo."
                    : ready + (ready == 1 ? " kit listo" : " kits listos") + " para reclamar. Pulsa uno para verlo.");
            return b.build();
        }

        private PadView preview(ServerPlayer player, Kit k) {
            long left = left(player, k);
            boolean can = allowed(player, k);
            PadView.Builder b = PadView.of("kits").selected("ver:" + k.id);
            List<String> lines = new ArrayList<>();
            if (!k.desc.isEmpty()) lines.add(k.desc);
            lines.add(typeText(k) + " · " + (!can ? "solo para: " + rankText(k.rank).toLowerCase(Locale.ROOT) + "."
                    : left == 0 ? "listo para reclamar." : left < 0 ? "ya lo reclamaste." : "vuelve en " + time(left) + "."));
            b.hero(new PadView.Row(new ItemStack(k.iconItem()), k.name, can && left == 0 ? 0x40C850 : 0xF6B628, lines, -1,
                    k.price > 0 ? PadView.moneyExact(k.price) : "", null, null));
            contents(b, k.items, k.coins, k.greens);
            b.empty("Este kit no trae nada todavía.");
            b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
            if (!can) b.footer(PadView.Btn.off(k.rank.equals("staff") ? "SOLO STAFF" : "NECESITAS RANGO"));
            else if (left == 0) b.footer(PadView.Btn.of(k.price > 0 ? "PAGAR Y RECLAMAR" : "RECLAMAR", "reclamar:" + k.id, PadView.GREEN));
            else b.footer(PadView.Btn.off(left < 0 ? "RECLAMADO" : "EN " + time(left).toUpperCase(Locale.ROOT)));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("ver:")) return action;
            if (action.equals("volver")) return "";
            if (!action.startsWith("reclamar:")) return null;
            Kit k = kit(action.substring(9));
            if (k == null || !k.on || left(player, k) != 0 || !allowed(player, k)) return null;
            return claim(player, k) ? "" : null;
        }
    }

    /**
     * Reclama el kit: cobra el precio (si tiene), ingresa las monedas en el monedero (las de oro en la economía del
     * servidor, las verdes en su saldo) y da los objetos. Si el dinero falla, no se reclama (y se devuelve el precio).
     */
    static boolean claim(ServerPlayer player, Kit k) {
        String name = player.getGameProfile().getName();
        if (k.price > 0 && !TFEconomy.take(player, k.price)) {
            TFPadNet.notice(player, "Te faltan monedas: este kit cuesta " + TFEconomy.format(k.price) + ".");
            return false;
        }
        if (k.coins > 0 && !TFEconomy.give(player.getServer(), player.getUUID(), name, k.coins)) {
            if (k.price > 0) TFEconomy.give(player.getServer(), player.getUUID(), name, k.price);
            TFPadNet.notice(player, "Error al dar las monedas del kit. Inténtalo en un rato.");
            return false;
        }
        if (k.greens > 0) PadGreen.add(player, k.greens);
        JsonObject p = STORE_IMPL.player(player.getUUID());
        p.addProperty(k.id, System.currentTimeMillis());
        STORE_IMPL.changed();
        for (ItemStack s : k.items) give(player, s.copy());
        PadStats.add(player, PadStats.KITS, 1);
        player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.MASTER, 0.8F, 0.8F);
        String extra = coinsText(k.coins, k.greens);
        TFPadNet.notice(player, "Reclamaste " + k.name + (extra.isEmpty() ? "." : ": " + extra + " en tu monedero."));
        if (k.coins > 0 || k.price > 0) TFPadNet.sendState(player);
        return true;
    }

    /**
     * Da el objeto en montones normales; lo que no cabe cae a sus pies. Una moneda (de oro o verde) no se da: se ingresa
     * en el monedero ({@link net.tierrasfantasticas.tfclient.economy.TFCoins}).
     */
    static void give(ServerPlayer player, ItemStack stack) {
        if (net.tierrasfantasticas.tfclient.economy.TFCoins.credit(player, stack)) return;
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

    static Path configFile() {
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
        // las monedas puestas como objeto (antes se daban como objeto): pasan a «monedas» y «verdes», una sola vez
        if (o.has("kits") && o.get("kits").isJsonArray()) {
            boolean moved = false;
            for (JsonElement e : o.getAsJsonArray("kits")) {
                if (e.isJsonObject()) moved |= moveCoins(e.getAsJsonObject());
            }
            if (moved) {
                TFJson.write(file, o);
                TFClient.LOGGER.info("TF Pad: las monedas que había como objeto en los kits pasan a monedas del kit (al monedero)");
            }
        }
        if (o.has("kits") && o.get("kits").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("kits")) {
                try {
                    JsonObject k = e.getAsJsonObject();
                    List<ItemStack> items = PadItemPicker.stacks(k.has("objetos") && k.get("objetos").isJsonArray() ? k.getAsJsonArray("objetos") : null);
                    String id = TFJson.str(k, "id", "");
                    if (id.isEmpty() || id.length() > 32) id = "kit" + KITS.size();
                    long seconds = TFJson.num(k, "esperaSegundos", 86400);
                    String type = TFJson.str(k, "tipo", seconds <= 0 ? "unico" : seconds == 604800 ? "semanal" : "horas");
                    if (!TYPES.contains(type)) type = "horas";
                    KITS.add(new Kit(id, TFJson.str(k, "nombre", "Kit"), TFJson.str(k, "descripcion", ""),
                            TFJson.str(k, "icono", ""), seconds, Math.max(0, TFJson.num(k, "monedas", 0)), Math.max(0, TFJson.num(k, "verdes", 0)),
                            items, type, TFJson.str(k, "rango", ""), Math.max(0, TFJson.num(k, "precio", 0)), TFJson.bool(k, "activado", true)));
                } catch (Exception ex) {
                    TFClient.LOGGER.warn("TF Pad: un kit no se pudo leer: {}", ex.getMessage());
                }
            }
        }
    }

    /** Pasa las monedas que haya en «objetos» a «monedas» y «verdes» (de un kit, un día o una recompensa). */
    static boolean moveCoins(JsonObject o) {
        if (!o.has("objetos") || !o.get("objetos").isJsonArray()) return false;
        long[] c = PadItemPicker.takeCoins(o.getAsJsonArray("objetos"));
        if (c[0] <= 0 && c[1] <= 0) return false;
        if (c[0] > 0) o.addProperty("monedas", Math.max(0, TFJson.num(o, "monedas", 0)) + c[0]);
        if (c[1] > 0) o.addProperty("verdes", Math.max(0, TFJson.num(o, "verdes", 0)) + c[1]);
        return true;
    }

    private static boolean isDaily(JsonObject k) {
        String type = TFJson.str(k, "tipo", "");
        return type.equals("diario") || type.isEmpty() && TFJson.num(k, "esperaSegundos", -1) == 86400;
    }

    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        o.addProperty("_ayuda", "Kits. tipo: unico (una sola vez), semanal u horas (cada esperaSegundos; 86400 = cada día). "
                + "monedas y verdes: se ingresan en el monedero. rango: \"\" (todos), \"staff\" o el de un rango (y los de más nivel). "
                + "precio: monedas que cuesta reclamarlo (0 = gratis). activado: false = borrador. objetos: \"minecraft:bread 16\". "
                + "Se crean y cambian desde el pad de administrador.");
        JsonArray kits = new JsonArray();
        kits.add(kit("inicial", "Kit inicial", "Lo justo para empezar tu aventura.", "minecraft:stone_pickaxe", 0, 25,
                "minecraft:stone_sword", "minecraft:stone_pickaxe", "minecraft:stone_axe", "minecraft:stone_shovel",
                "minecraft:bread 16", "minecraft:torch 32", "minecraft:oak_log 16", "minecraft:white_bed"));
        kits.add(kit("semanal", "Kit semanal", "Una ayuda más grande cada semana.", "minecraft:iron_pickaxe", 604800, 60,
                "minecraft:iron_pickaxe", "minecraft:iron_ingot 8", "minecraft:golden_carrot 16", "minecraft:experience_bottle 8"));
        o.add("kits", kits);
        return o;
    }

    private static JsonObject kit(String id, String name, String desc, String icon, long seconds, long coins, String... items) {
        JsonObject k = blank(id, name);
        k.addProperty("descripcion", desc);
        k.addProperty("icono", icon);
        k.addProperty("tipo", seconds <= 0 ? "unico" : seconds == 604800 ? "semanal" : "horas");
        k.addProperty("esperaSegundos", seconds);
        k.addProperty("monedas", coins);
        k.addProperty("activado", true);
        JsonArray a = new JsonArray();
        for (String s : items) a.add(s);
        k.add("objetos", a);
        return k;
    }

    /** Un kit vacío (borrador): una sola vez, gratis, para todos. */
    private static JsonObject blank(String id, String name) {
        JsonObject k = new JsonObject();
        k.addProperty("id", id);
        k.addProperty("nombre", name);
        k.addProperty("descripcion", "");
        k.addProperty("icono", "");
        k.addProperty("tipo", "unico");
        k.addProperty("esperaSegundos", 0);
        k.addProperty("monedas", 0);
        k.addProperty("verdes", 0);
        k.addProperty("rango", "");
        k.addProperty("precio", 0);
        k.addProperty("activado", false);
        k.add("objetos", new JsonArray());
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
        if (o == null || !o.has("kits") || !o.get("kits").isJsonArray()) return false;
        for (JsonElement e : o.getAsJsonArray("kits")) {
            if (!e.isJsonObject()) continue;
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
        for (JsonElement e : o.getAsJsonArray("kits")) if (!(e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id))) keep.add(e);
        o.add("kits", keep);
        TFJson.write(configFile(), o);
        loadConfig();
    }

    private static String freeId(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.isEmpty()) base = "kit";
        if (base.length() > 24) base = base.substring(0, 24);
        String id = base;
        for (int i = 2; kit(id) != null; i++) id = base + "_" + i;
        return id;
    }

    /** Un kit nuevo, vacío y sin publicar (borrador), con ese nombre. Devuelve su id. */
    static String create(String name) {
        JsonObject o = TFJson.read(configFile());
        if (o == null) o = defaults();
        if (!o.has("kits") || !o.get("kits").isJsonArray()) o.add("kits", new JsonArray());
        String id = freeId(name);
        o.getAsJsonArray("kits").add(blank(id, name));
        TFJson.write(configFile(), o);
        loadConfig();
        return id;
    }

    /** Una copia del kit (sin publicar), justo detrás del original. Devuelve su id o null. */
    static String duplicate(String id) {
        JsonObject o = TFJson.read(configFile());
        if (o == null || !o.has("kits") || !o.get("kits").isJsonArray()) return null;
        JsonArray kits = o.getAsJsonArray("kits");
        for (int i = 0; i < kits.size(); i++) {
            if (!kits.get(i).isJsonObject() || !TFJson.str(kits.get(i).getAsJsonObject(), "id", "").equals(id)) continue;
            JsonObject copy = kits.get(i).getAsJsonObject().deepCopy();
            String name = TFJson.str(copy, "nombre", "Kit") + " (copia)";
            String nid = freeId(name);
            copy.addProperty("id", nid);
            copy.addProperty("nombre", name);
            copy.addProperty("activado", false);
            JsonArray out = new JsonArray();
            for (int j = 0; j < kits.size(); j++) {
                out.add(kits.get(j));
                if (j == i) out.add(copy);
            }
            o.add("kits", out);
            TFJson.write(configFile(), o);
            loadConfig();
            return nid;
        }
        return null;
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
                    delete(id);
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

    /** /tf web kits guardar: lo de la barra rápida (sin el pad de administrador; las monedas, al monedero del kit). */
    private static int save(CommandSourceStack source, String id, int hours, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (id.length() > 32) {
            source.sendFailure(Component.literal("El id del kit es muy largo (máx. 32)."));
            return 0;
        }
        JsonArray items = new JsonArray();
        String icon = "";
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (PadItemPicker.hidden(s)) continue;
            if (icon.isEmpty() && !net.tierrasfantasticas.tfclient.economy.TFCoins.isCoin(s)) icon = String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem()));
            items.add(PadItemPicker.json(s));
        }
        if (items.isEmpty()) {
            source.sendFailure(Component.literal("Pon en tu barra rápida lo que debe traer el kit."));
            return 0;
        }
        JsonObject o = TFJson.read(configFile());
        if (o == null) o = defaults();
        JsonArray keep = new JsonArray();
        if (o.has("kits")) for (JsonElement e : o.getAsJsonArray("kits")) if (!(e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id))) keep.add(e);
        JsonObject k = blank(id, name.isBlank() ? "Kit " + id : name);
        k.addProperty("icono", icon);
        k.addProperty("tipo", hours == 0 ? "unico" : hours == 168 ? "semanal" : "horas");
        k.addProperty("esperaSegundos", hours * 3600L);
        k.addProperty("activado", true);
        k.add("objetos", items);
        moveCoins(k);
        keep.add(k);
        o.add("kits", keep);
        TFJson.write(configFile(), o);
        loadConfig();
        int n = k.getAsJsonArray("objetos").size();
        source.sendSuccess(() -> Component.literal("Kit " + id + " guardado con " + n + " objetos (" + (hours == 0 ? "una sola vez" : "cada " + hours + " h") + ").")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
