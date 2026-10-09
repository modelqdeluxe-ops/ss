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

    record Day(long coins, List<ItemStack> items) {
        ItemStack icon() {
            return items.isEmpty() ? new ItemStack(net.tierrasfantasticas.tfclient.items.TFItems.COIN.get()) : items.get(0).copy();
        }
    }

    record Reward(String id, String name, String icon, String type, long hours, long coins, List<ItemStack> items, boolean on) {
        ItemStack iconStack() {
            Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(icon));
            if (i != null && i != Items.AIR) return new ItemStack(i);
            return items.isEmpty() ? new ItemStack(Items.CHEST_MINECART) : items.get(0).copy();
        }
    }

    private static final List<Day> DAYS = new ArrayList<>();
    private static final List<Reward> REWARDS = new ArrayList<>();
    private static boolean calendarOn = true, resetOnMiss = true;

    static final JsonStore STORE_IMPL = new JsonStore("recompensas.json") {
        @Override
        void loaded(MinecraftServer server) {
            loadConfig();
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
            boolean showOthers = tab.equals("otras") ? !others.isEmpty() : !cal && !others.isEmpty();
            PadView.Builder b = PadView.of("recompensas").empty("No hay recompensas ahora. Vuelve pronto.");
            if (cal && !others.isEmpty()) {
                b.tab("diaria", "DIARIA").tab("otras", "OTRAS (" + others.size() + ")").selected(showOthers ? "otras" : "diaria");
            }
            if (!showOthers && cal) {
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
                    String coins = d.coins > 0 ? PadShop.price(d.coins) : "";
                    b.card(d.icon(), "Día " + (i + 1) + (coins.isEmpty() ? "" : " · " + coins), past ? 0xAABAD2 : today ? 0x40C850 : 0xF6B628,
                            sub, today && !done ? "dia" : "tab:dia:" + i, today && !done);
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
                        left == 0 ? "Lista" : left < 0 ? "Reclamada" : "en " + PadKits.time(left), "tab:ver:" + r.id, left == 0);
            }
            if (!others.isEmpty()) b.header(ready == 0 ? "Ninguna lista ahora." : ready + (ready == 1 ? " lista" : " listas") + " para reclamar. Pulsa una para verla.");
            return b.build();
        }

        private PadView dayDetail(ServerPlayer player, int i) {
            Day d = DAYS.get(i);
            PadView.Builder b = PadView.of("recompensas").selected("dia:" + i).header("Día " + (i + 1) + " del calendario: lo que trae.");
            for (ItemStack s : d.items) b.cell(s.copy(), "", 0x18265C, "", false);
            if (d.coins > 0) b.cell(new ItemStack(net.tierrasfantasticas.tfclient.items.TFItems.COIN.get()), PadShop.price(d.coins), 0xC27A10, "", false);
            b.empty("Este día no trae nada.");
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            return b.build();
        }

        private PadView detail(ServerPlayer player, Reward r) {
            long left = left(player, r);
            PadView.Builder b = PadView.of("recompensas").selected("ver:" + r.id).header(r.name + " · " + typeText(r));
            for (ItemStack s : r.items) b.cell(s.copy(), "", 0x18265C, "", false);
            if (r.coins > 0) b.cell(new ItemStack(net.tierrasfantasticas.tfclient.items.TFItems.COIN.get()), PadShop.price(r.coins), 0xC27A10, "", false);
            b.footer(PadView.Btn.of("ATRÁS", "tab:otras", PadView.BLUE));
            b.footer(left == 0 ? PadView.Btn.of("RECLAMAR", "reclamar:" + r.id, PadView.GREEN)
                    : PadView.Btn.off(left < 0 ? "RECLAMADA" : "EN " + PadKits.time(left).toUpperCase()));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("tab:")) return action.substring(4);
            if (action.equals("dia")) {
                if (!calendarOn || DAYS.isEmpty() || claimedToday(player)) return null;
                int idx = dayIndex(player);
                Day d = DAYS.get(idx);
                if (!pay(player, d.coins)) return null;
                JsonObject o = STORE_IMPL.player(player.getUUID());
                o.addProperty("racha", idx);
                o.addProperty("ultimoDia", today());
                o.addProperty("total", JsonStore.num(o, "total") + 1);
                STORE_IMPL.changed();
                for (ItemStack s : d.items) PadKits.give(player, s.copy());
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.3F);
                TFPadNet.notice(player, "¡Día " + (idx + 1) + " reclamado! Vuelve mañana a por el día " + (idx + 2 > DAYS.size() ? 1 : idx + 2) + ".");
                return null;
            }
            if (action.startsWith("reclamar:")) {
                Reward r = reward(action.substring(9));
                if (r == null || !r.on || left(player, r) != 0) return null;
                if (!pay(player, r.coins)) return null;
                STORE_IMPL.player(player.getUUID()).addProperty("r:" + r.id, System.currentTimeMillis());
                STORE_IMPL.changed();
                for (ItemStack s : r.items) PadKits.give(player, s.copy());
                player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.MASTER, 0.8F, 0.9F);
                TFPadNet.notice(player, "Reclamaste " + r.name + ".");
                return "otras";
            }
            return null;
        }
    }

    /** Primero las monedas: si la economía no puede pagar, no se reclama. */
    private static boolean pay(ServerPlayer player, long coins) {
        if (coins <= 0) return true;
        if (TFEconomy.give(player.getServer(), player.getUUID(), player.getGameProfile().getName(), coins)) {
            TFPadNet.sendState(player);
            return true;
        }
        TFPadNet.notice(player, "Error al dar las monedas. Inténtalo en un rato.");
        return false;
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
        JsonObject cal = o.has("calendario") && o.get("calendario").isJsonObject() ? o.getAsJsonObject("calendario") : new JsonObject();
        calendarOn = TFJson.bool(cal, "activado", true);
        resetOnMiss = TFJson.bool(cal, "reiniciarSiFalta", true);
        if (cal.has("dias") && cal.get("dias").isJsonArray()) {
            for (JsonElement e : cal.getAsJsonArray("dias")) {
                if (!e.isJsonObject()) continue;
                JsonObject d = e.getAsJsonObject();
                DAYS.add(new Day(Math.max(0, TFJson.num(d, "monedas", 0)),
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
                            Math.max(1, TFJson.num(r, "horas", 24)), Math.max(0, TFJson.num(r, "monedas", 0)),
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
        days.add(day(25, "minecraft:bread 8"));
        days.add(day(25, "minecraft:torch 16"));
        days.add(day(50, "minecraft:iron_ingot 4"));
        days.add(day(50, "minecraft:cooked_beef 12"));
        days.add(day(75, "minecraft:golden_carrot 8"));
        days.add(day(100, "minecraft:experience_bottle 8"));
        days.add(day(200, "minecraft:diamond 2"));
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

    /** Los kits «diarios» de antes pasan a ser recompensas diarias (el dueño quitó el kit diario de Kits). */
    static void adopt(List<JsonObject> dailyKits) {
        if (dailyKits.isEmpty()) return;
        JsonObject o = readConfig();
        if (!o.has("recompensas") || !o.get("recompensas").isJsonArray()) o.add("recompensas", new JsonArray());
        JsonArray list = o.getAsJsonArray("recompensas");
        for (JsonObject k : dailyKits) {
            JsonObject r = new JsonObject();
            String id = TFJson.str(k, "id", "diario");
            r.addProperty("id", id.length() > 32 ? id.substring(0, 32) : id);
            r.addProperty("nombre", TFJson.str(k, "nombre", "Recompensa diaria"));
            r.addProperty("icono", TFJson.str(k, "icono", ""));
            r.addProperty("tipo", "diaria");
            r.addProperty("horas", 24);
            r.addProperty("monedas", TFJson.num(k, "monedas", 0));
            r.addProperty("activada", true);
            r.add("objetos", k.has("objetos") && k.get("objetos").isJsonArray() ? k.getAsJsonArray("objetos") : new JsonArray());
            list.add(r);
        }
        writeConfig(o);
        TFClient.LOGGER.info("TF Pad: {} kit(s) diario(s) pasaron a Recompensas", dailyKits.size());
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
