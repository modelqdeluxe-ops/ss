/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Recompensas (gratis, como los kits): las pone el staff desde el pad de administrador.
 * <ul>
 *   <li>DIARIA: un calendario de días (7 de partida). Cada día se reclama la del día siguiente de la racha; si un
 *       jugador se salta un día, vuelve al día 1 (o sigue, si el staff lo prefiere). Tras el último, empieza otra vez.</li>
 *   <li>OTRAS: recompensas sueltas con su espera: diaria (cada día natural), semanal, cada X horas o una sola vez.</li>
 * </ul>
 * Config: config/tfclient/recompensas.json. Lo de cada jugador: &lt;mundo&gt;/tfclient/recompensas.json.
 * Los kits «diarios» de antes se pasan aquí solos (ver {@link #adopt}).
 */
public final class PadRewards {
    public static final List<String> TYPES = List.of("diaria", "semanal", "horas", "unica");

    /** Un día del calendario: monedas, monedas verdes (las dos van al monedero) y objetos. */
    record Day(long coins, long greens, List<ItemStack> items) {
        ItemStack icon() {
            if (!items.isEmpty()) return items.get(0).copy();
            return PadKits.coinIcon(coins <= 0 && greens > 0);
        }
    }

    record Reward(String id, String name, String icon, String type, long hours, long coins, long greens, List<ItemStack> items, boolean on) {
        ItemStack iconStack() {
            Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(icon));
            if (i != null && i != Items.AIR) return new ItemStack(i);
            if (!items.isEmpty()) return items.get(0).copy();
            return coins > 0 || greens > 0 ? PadKits.coinIcon(coins <= 0) : new ItemStack(Items.CHEST_MINECART);
        }
    }

    private static final List<Day> DAYS = new ArrayList<>();
    private static final List<Reward> REWARDS = new ArrayList<>();
    private static boolean calendarOn = true, resetOnMiss = true;

    static final JsonStore STORE_IMPL = new JsonStore("recompensas.json") {
        @Override
        void loaded(MinecraftServer server) {
            loaded = true;
            loadConfig();
            if (!CLAIM_COPIES.isEmpty()) copyClaims();
        }
    };
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.App APP = new App();

    private PadRewards() {}

    static long today() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Calendario
    // ---------------------------------------------------------------------------------------------------------------

    /** El índice del día que toca reclamar ahora (o el de hoy, si ya lo reclamó). */
    static int dayIndex(ServerPlayer p) {
        if (DAYS.isEmpty()) return 0;
        JsonObject o = STORE_IMPL.playerIfAny(p.getUUID());
        if (o == null || !o.has("ultimoDia")) return 0;
        long last = JsonStore.num(o, "ultimoDia");
        int streak = (int) JsonStore.num(o, "racha");
        if (last == today()) return Math.floorMod(streak, DAYS.size());
        if (last == today() - 1 || !resetOnMiss) return Math.floorMod(streak + 1, DAYS.size());
        return 0;
    }

    static boolean claimedToday(ServerPlayer p) {
        JsonObject o = STORE_IMPL.playerIfAny(p.getUUID());
        return o != null && o.has("ultimoDia") && JsonStore.num(o, "ultimoDia") == today();
    }

    static long untilTomorrow() {
        ZoneId z = ZoneId.systemDefault();
        long midnight = LocalDate.now(z).plusDays(1).atStartOfDay(z).toInstant().toEpochMilli();
        return Math.max(1, (midnight - System.currentTimeMillis()) / 1000);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Recompensas sueltas
    // ---------------------------------------------------------------------------------------------------------------

    /** Segundos que faltan (0 = ya; -1 = nunca más). */
    static long left(ServerPlayer p, Reward r) {
        JsonObject o = STORE_IMPL.playerIfAny(p.getUUID());
        if (o == null || !o.has("r:" + r.id)) return 0;
        long last = o.get("r:" + r.id).getAsLong();
        switch (r.type) {
            case "unica" -> {
                return -1;
            }
            case "diaria" -> {
                ZoneId z = ZoneId.systemDefault();
                LocalDate day = java.time.Instant.ofEpochMilli(last).atZone(z).toLocalDate();
                return day.equals(LocalDate.now(z)) ? untilTomorrow() : 0;
            }
            default -> {
                long seconds = r.type.equals("semanal") ? 7 * 86400L : Math.max(1, r.hours) * 3600L;
                return Math.max(0, (last + seconds * 1000 - System.currentTimeMillis()) / 1000);
            }
        }
    }

    static String typeText(Reward r) {
        return switch (r.type) {
            case "diaria" -> "Cada día";
            case "semanal" -> "Cada semana";
            case "unica" -> "Una sola vez";
            default -> "Cada " + r.hours + " h";
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La app del jugador
    // ---------------------------------------------------------------------------------------------------------------

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            List<Reward> others = active();
            boolean cal = calendarOn && !DAYS.isEmpty();
            if (tab.startsWith("ver:")) {
                Reward r = reward(tab.substring(4));
                if (r != null && r.on) return detail(player, r);
            }
            if (tab.startsWith("dia:")) { // qué trae un día del calendario
                try {
                    int i = Integer.parseInt(tab.substring(4));
                    if (i >= 0 && i < DAYS.size()) return dayDetail(player, i);
                } catch (NumberFormatException ignored) {
                    // pestaña rara: la entrada
                }
            }
            // DIARIA (el calendario) y REGALOS (lo que te mandan otros jugadores desde Jugadores)
            List<PadPlayers.Gift> gifts = PadPlayers.gifts(player.getUUID());
            boolean showGifts = tab.equals("regalos") || !cal;
            PadView.Builder b = PadView.of("recompensas").empty("No hay recompensas ahora. Vuelve pronto.");
            if (cal) b.tab("diaria", "DIARIA");
            b.tab("regalos", gifts.isEmpty() ? "REGALOS" : "REGALOS (" + gifts.size() + ")").selected(showGifts ? "regalos" : "diaria");
            if (showGifts) {
                b.header(gifts.isEmpty() ? "Aquí llegan los regalos que te mandan otros jugadores."
                        : gifts.size() + (gifts.size() == 1 ? " regalo esperándote." : " regalos esperándote."));
                for (int i = 0; i < gifts.size(); i++) {
                    PadPlayers.Gift g = gifts.get(i);
                    ItemStack icon = g.coins() > 0 ? PadKits.coinIcon(false) : g.item().isEmpty() ? new ItemStack(net.minecraft.world.item.Items.CHEST) : g.item().copy();
                    b.row(new PadView.Row(icon, "De " + g.from(), 0xE0489E, List.of(g.what() + " · " + PadKits.time(Math.max(1, (System.currentTimeMillis() - g.at()) / 1000)) + " atrás."),
                            -1, "", PadView.Btn.of("RECOGER", "regalo:" + i, PadView.GREEN), null));
                }
                if (gifts.size() > 1) b.footer(PadView.Btn.of("RECOGER TODO", "regalo:todo", PadView.GREEN));
                b.empty("Aún no tienes regalos. Cuando alguien te regale algo desde la app Jugadores, llegará aquí.");
                return b.build();
            }
            if (cal) {
                int idx = dayIndex(player);
                boolean done = claimedToday(player);
                b.header(done ? "Ya tienes la de hoy. La siguiente, en " + PadKits.time(untilTomorrow()) + "."
                        : "Día " + (idx + 1) + " de " + DAYS.size() + ": ¡reclámala! Si te saltas un día, "
                        + (resetOnMiss ? "vuelves al día 1." : "sigues donde ibas."));
                b.cards();
                for (int i = 0; i < DAYS.size(); i++) {
                    Day d = DAYS.get(i);
                    boolean today = i == idx;
                    boolean past = i < idx || (today && done);
                    String sub = today ? (done ? "Reclamada" : "¡Hoy!") : past ? "Reclamada" : i == idx + 1 ? "Mañana" : "";
                    String coins = d.coins > 0 ? PadShop.price(d.coins) : d.greens > 0 ? "+" + d.greens + " verdes" : "";
                    b.card(d.icon(), "Día " + (i + 1) + (coins.isEmpty() ? "" : " · " + coins), past ? 0xAABAD2 : today ? 0x40C850 : 0xF6B628,
                            sub, past ? PadView.TONE_GRAY : today ? PadView.TONE_GREEN : 0, -1, "", today && !done ? "dia" : "tab:dia:" + i,
                            today && !done);
                }
                if (!done) b.footer(PadView.Btn.of("RECLAMAR DÍA " + (idx + 1), "dia", PadView.GREEN));
                return b.build();
            }
            int ready = 0;
            b.cards();
            for (Reward r : others) {
                long left = left(player, r);
                if (left == 0) ready++;
                b.card(r.iconStack(), r.name, left == 0 ? 0x40C850 : left < 0 ? 0xAABAD2 : 0xF6B628,
                        left == 0 ? "Lista" : left < 0 ? "Reclamada" : "en " + PadKits.time(left),
                        left == 0 ? PadView.TONE_GREEN : left < 0 ? PadView.TONE_GRAY : 0, -1, "", "tab:ver:" + r.id, left == 0);
            }
            if (!others.isEmpty()) b.header(ready == 0 ? "Ninguna lista ahora." : ready + (ready == 1 ? " lista" : " listas") + " para reclamar. Pulsa una para verla.");
            return b.build();
        }

        private PadView dayDetail(ServerPlayer player, int i) {
            Day d = DAYS.get(i);
            PadView.Builder b = PadView.of("recompensas").selected("dia:" + i);
            b.hero(new PadView.Row(d.icon(), "Día " + (i + 1) + " del calendario", 0xF6B628, List.of("Lo que trae ese día de la racha."), -1,
                    "", null, null));
            PadKits.contents(b, d.items, d.coins, d.greens);
            b.empty("Este día no trae nada.");
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            return b.build();
        }

        private PadView detail(ServerPlayer player, Reward r) {
            long left = left(player, r);
            PadView.Builder b = PadView.of("recompensas").selected("ver:" + r.id);
            b.hero(new PadView.Row(r.iconStack(), r.name, left == 0 ? 0x40C850 : 0xF6B628, List.of(typeText(r) + " · "
                    + (left == 0 ? "lista para reclamar." : left < 0 ? "ya la reclamaste." : "vuelve en " + PadKits.time(left) + ".")), -1, "", null, null));
            PadKits.contents(b, r.items, r.coins, r.greens);
            b.empty("No trae nada.");
            b.footer(PadView.Btn.of("ATRÁS", "tab:otras", PadView.BLUE));
            b.footer(left == 0 ? PadView.Btn.of("RECLAMAR", "reclamar:" + r.id, PadView.GREEN)
                    : PadView.Btn.off(left < 0 ? "RECLAMADA" : "EN " + PadKits.time(left).toUpperCase()));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("tab:")) return action.substring(4);
            if (action.startsWith("regalo:")) {
                String w = action.substring(7);
                int i = -1;
                if (!w.equals("todo")) {
                    try {
                        i = Integer.parseInt(w);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                int n = PadPlayers.claimGifts(player, i);
                if (n > 0) TFPadNet.notice(player, n == 1 ? "Regalo recogido." : n + " regalos recogidos.");
                return "regalos";
            }
            if (action.equals("dia")) {
                if (!calendarOn || DAYS.isEmpty() || claimedToday(player)) return null;
                int idx = dayIndex(player);
                Day d = DAYS.get(idx);
                if (!pay(player, d.coins, d.greens)) return null;
                JsonObject o = STORE_IMPL.player(player.getUUID());
                o.addProperty("racha", idx);
                o.addProperty("ultimoDia", today());
                o.addProperty("total", JsonStore.num(o, "total") + 1);
                STORE_IMPL.changed();
                for (ItemStack s : d.items) PadKits.give(player, s.copy());
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.3F);
                String extra = PadKits.coinsText(d.coins, d.greens);
                TFPadNet.notice(player, "¡Día " + (idx + 1) + " reclamado!" + (extra.isEmpty() ? "" : " " + extra + " en tu monedero.")
                        + " Vuelve mañana a por el día " + (idx + 2 > DAYS.size() ? 1 : idx + 2) + ".");
                return null;
            }
            if (action.startsWith("reclamar:")) {
                Reward r = reward(action.substring(9));
                if (r == null || !r.on || left(player, r) != 0) return null;
                if (!pay(player, r.coins, r.greens)) return null;
                STORE_IMPL.player(player.getUUID()).addProperty("r:" + r.id, System.currentTimeMillis());
                STORE_IMPL.changed();
                for (ItemStack s : r.items) PadKits.give(player, s.copy());
                player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.MASTER, 0.8F, 0.9F);
                String extra = PadKits.coinsText(r.coins, r.greens);
                TFPadNet.notice(player, "Reclamaste " + r.name + (extra.isEmpty() ? "." : ": " + extra + " en tu monedero."));
                return "otras";
            }
            return null;
        }
    }

    /**
     * Primero las monedas, directas al monedero (las de oro en la economía del servidor, las verdes en su saldo): si la
     * economía no puede pagar, no se reclama.
     */
    private static boolean pay(ServerPlayer player, long coins, long greens) {
        if (coins > 0) {
            if (!TFEconomy.give(player.getServer(), player.getUUID(), player.getGameProfile().getName(), coins)) {
                TFPadNet.notice(player, "Error al dar las monedas. Inténtalo en un rato.");
                return false;
            }
            TFPadNet.sendState(player);
        }
        if (greens > 0) PadGreen.add(player, greens);
        return true;
    }

    static List<Reward> active() {
        List<Reward> out = new ArrayList<>();
        for (Reward r : REWARDS) if (r.on) out.add(r);
        return out;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Configuración (config/tfclient/recompensas.json)
    // ---------------------------------------------------------------------------------------------------------------

    static Path configFile() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("recompensas.json", "tfclient-recompensas.json");
    }

    static JsonObject readConfig() {
        JsonObject o = TFJson.read(configFile());
        return o == null ? defaults() : o;
    }

    static void writeConfig(JsonObject o) {
        TFJson.write(configFile(), o);
        loadConfig();
    }

    static void loadConfig() {
        Path file = configFile();
        JsonObject o = TFJson.read(file);
        if (o == null) {
            o = defaults();
            if (!Files.exists(file)) TFJson.write(file, o);
        }
        DAYS.clear();
        REWARDS.clear();
        // las monedas puestas como objeto (antes se daban como objeto): pasan a «monedas» y «verdes», una sola vez
        boolean moved = false;
        if (o.has("calendario") && o.get("calendario").isJsonObject() && o.getAsJsonObject("calendario").has("dias")
                && o.getAsJsonObject("calendario").get("dias").isJsonArray()) {
            for (JsonElement e : o.getAsJsonObject("calendario").getAsJsonArray("dias")) if (e.isJsonObject()) moved |= PadKits.moveCoins(e.getAsJsonObject());
        }
        if (o.has("recompensas") && o.get("recompensas").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("recompensas")) if (e.isJsonObject()) moved |= PadKits.moveCoins(e.getAsJsonObject());
        }
        if (moved) {
            TFJson.write(file, o);
            TFClient.LOGGER.info("TF Pad: las monedas que había como objeto en las recompensas pasan al monedero");
        }
        JsonObject cal = o.has("calendario") && o.get("calendario").isJsonObject() ? o.getAsJsonObject("calendario") : new JsonObject();
        calendarOn = TFJson.bool(cal, "activado", true);
        resetOnMiss = TFJson.bool(cal, "reiniciarSiFalta", true);
        if (cal.has("dias") && cal.get("dias").isJsonArray()) {
            for (JsonElement e : cal.getAsJsonArray("dias")) {
                if (!e.isJsonObject()) continue;
                JsonObject d = e.getAsJsonObject();
                DAYS.add(new Day(Math.max(0, TFJson.num(d, "monedas", 0)), Math.max(0, TFJson.num(d, "verdes", 0)),
                        PadItemPicker.stacks(d.has("objetos") && d.get("objetos").isJsonArray() ? d.getAsJsonArray("objetos") : null)));
            }
        }
        if (o.has("recompensas") && o.get("recompensas").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("recompensas")) {
                try {
                    JsonObject r = e.getAsJsonObject();
                    String id = TFJson.str(r, "id", "");
                    if (id.isEmpty() || id.length() > 32) id = "r" + REWARDS.size();
                    String type = TFJson.str(r, "tipo", "diaria");
                    if (!TYPES.contains(type)) type = "diaria";
                    REWARDS.add(new Reward(id, TFJson.str(r, "nombre", "Recompensa"), TFJson.str(r, "icono", ""), type,
                            Math.max(1, Math.min(24L * 3650, TFJson.num(r, "horas", 24))), Math.max(0, TFJson.num(r, "monedas", 0)),
                            Math.max(0, TFJson.num(r, "verdes", 0)),
                            PadItemPicker.stacks(r.has("objetos") && r.get("objetos").isJsonArray() ? r.getAsJsonArray("objetos") : null),
                            TFJson.bool(r, "activada", true)));
                } catch (Exception ex) {
                    TFClient.LOGGER.warn("TF Pad: una recompensa no se pudo leer: {}", ex.getMessage());
                }
            }
        }
    }

    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        o.addProperty("_ayuda", "Recompensas gratis. calendario.dias: lo de cada día de la racha (objetos y monedas). "
                + "recompensas: sueltas, tipo diaria | semanal | horas (cada «horas») | unica. Todo se cambia desde el pad de administrador.");
        JsonObject cal = new JsonObject();
        cal.addProperty("activado", true);
        cal.addProperty("reiniciarSiFalta", true);
        JsonArray days = new JsonArray();
        days.add(day(5, "minecraft:bread 8"));
        days.add(day(5, "minecraft:torch 16"));
        days.add(day(10, "minecraft:iron_ingot 4"));
        days.add(day(10, "minecraft:cooked_beef 12"));
        days.add(day(15, "minecraft:golden_carrot 8"));
        days.add(day(20, "minecraft:experience_bottle 8"));
        days.add(day(40, "minecraft:diamond 1"));
        cal.add("dias", days);
        o.add("calendario", cal);
        o.add("recompensas", new JsonArray());
        return o;
    }

    private static JsonObject day(long coins, String... items) {
        JsonObject d = new JsonObject();
        d.addProperty("monedas", coins);
        JsonArray a = new JsonArray();
        for (String s : items) a.add(s);
        d.add("objetos", a);
        return d;
    }

    /**
     * Los kits «diarios» de antes pasan a ser recompensas diarias (el dueño quitó el kit diario de Kits). Quien ya lo
     * reclamó hoy lo sigue teniendo reclamado. Devuelve false si recompensas.json existe pero no se puede leer (no se
     * toca nada: así no se pierde ni el archivo ni el kit).
     */
    static boolean adopt(List<JsonObject> dailyKits) {
        if (dailyKits.isEmpty()) return true;
        if (TFJson.read(configFile()) == null && Files.exists(configFile())) {
            TFClient.LOGGER.error("TF Pad: recompensas.json no se puede leer; los kits diarios se quedan en kits.json");
            return false;
        }
        JsonObject o = readConfig();
        if (!o.has("recompensas") || !o.get("recompensas").isJsonArray()) o.add("recompensas", new JsonArray());
        JsonArray list = o.getAsJsonArray("recompensas");
        java.util.Set<String> used = new java.util.HashSet<>();
        for (JsonElement e : list) if (e.isJsonObject()) used.add(TFJson.str(e.getAsJsonObject(), "id", ""));
        for (JsonObject k : dailyKits) {
            JsonObject r = new JsonObject();
            String kitId = TFJson.str(k, "id", "diario");
            String id = kitId.length() > 28 ? kitId.substring(0, 28) : kitId;
            for (int n = 2; used.contains(id); n++) id = (kitId.length() > 26 ? kitId.substring(0, 26) : kitId) + "_" + n;
            used.add(id);
            CLAIM_COPIES.put(kitId, id);
            r.addProperty("id", id);
            r.addProperty("nombre", TFJson.str(k, "nombre", "Recompensa diaria"));
            r.addProperty("icono", TFJson.str(k, "icono", ""));
            r.addProperty("tipo", "diaria");
            r.addProperty("horas", 24);
            r.addProperty("monedas", TFJson.num(k, "monedas", 0));
            r.addProperty("verdes", TFJson.num(k, "verdes", 0));
            r.addProperty("activada", true);
            r.add("objetos", k.has("objetos") && k.get("objetos").isJsonArray() ? k.getAsJsonArray("objetos") : new JsonArray());
            list.add(r);
        }
        writeConfig(o);
        TFClient.LOGGER.info("TF Pad: {} kit(s) diario(s) pasaron a Recompensas", dailyKits.size());
        if (loaded) copyClaims();
        return true;
    }

    /** Kit diario → recompensa nueva, para pasar lo que cada jugador ya había reclamado. */
    private static final java.util.Map<String, String> CLAIM_COPIES = new java.util.HashMap<>();
    private static boolean loaded;

    private static void copyClaims() {
        JsonElement all = PadKits.STORE_IMPL.root.get("jugadores");
        if (all != null && all.isJsonObject()) {
            for (var e : all.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject kp = e.getValue().getAsJsonObject();
                for (var c : CLAIM_COPIES.entrySet()) {
                    if (!kp.has(c.getKey())) continue;
                    try {
                        JsonObject rp = STORE_IMPL.player(java.util.UUID.fromString(e.getKey()));
                        if (!rp.has("r:" + c.getValue())) rp.add("r:" + c.getValue(), kp.get(c.getKey()));
                    } catch (IllegalArgumentException ignored) {
                        // clave rara
                    }
                }
            }
            STORE_IMPL.changed();
        }
        CLAIM_COPIES.clear();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Para el pad de administrador
    // ---------------------------------------------------------------------------------------------------------------

    static List<Day> days() {
        return List.copyOf(DAYS);
    }

    static List<Reward> rewards() {
        return List.copyOf(REWARDS);
    }

    static Reward reward(String id) {
        for (Reward r : REWARDS) if (r.id.equals(id)) return r;
        return null;
    }

    static boolean calendarOn() {
        return calendarOn;
    }

    static boolean resetOnMiss() {
        return resetOnMiss;
    }
}
