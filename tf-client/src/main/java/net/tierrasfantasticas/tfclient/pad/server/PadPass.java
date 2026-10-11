package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * El TF Pass (1.3.38): el pase de temporada, GRATIS para todos. Las misiones de la app Misiones (diarias y semanales) y
 * las cazas dan XP de pase al completarse; con la XP se suben niveles y cada nivel tiene sus premios (sobre todo
 * monedas verdes, las del Gachapón), que se reclaman desde el pad.
 * <ul>
 *   <li>Config: config/tfclient/pase.json (se crea la primera vez; se recarga con /tf reload): la temporada (número,
 *       nombre, día de inicio, cuántos días dura y si empieza sola la siguiente), cuántos niveles, la XP de cada nivel
 *       (y lo que sube por nivel), la XP de cada tipo de misión y los premios de cada nivel (ver PadPrize).</li>
 *   <li>Al acabar una temporada empieza la siguiente (si «renovar»): la XP y los niveles vuelven a cero; lo no
 *       reclamado se pierde (el pad avisa de cuánto queda).</li>
 *   <li>Datos: &lt;mundo&gt;/tfclient/pase.json: jugadores.&lt;uuid&gt;.{temporada, xp, reclamados: [niveles]}.</li>
 * </ul>
 */
public final class PadPass {
    private static boolean enabled = true, renew = true;
    private static int seasonNumber = 1, seasonDays = 60, levels = 50;
    private static String seasonName = "";
    private static LocalDate seasonStart = LocalDate.now();
    private static long xpPerLevel = 400, xpExtra = 0;
    private static final Map<String, Long> XP = new HashMap<>();
    private static final Map<Integer, List<PadPrize>> REWARDS = new HashMap<>();

    static final JsonStore STORE_IMPL = new JsonStore("pase.json") {
        @Override
        void loaded(MinecraftServer server) {
            loadConfig();
        }
    };
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.DataApp APP = new App();

    private PadPass() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Temporada y niveles
    // ---------------------------------------------------------------------------------------------------------------

    private static long today() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    /** Cuántas temporadas han pasado desde la de la config (0 = la de la config); -1 si aún no ha empezado. */
    private static long cycle() {
        long d = today() - seasonStart.toEpochDay();
        if (d < 0) return -1;
        long k = d / Math.max(1, seasonDays);
        return renew ? k : Math.min(k, 1); // sin renovar: 1 = ya terminó
    }

    /** ¿Hay temporada en curso? */
    static boolean running() {
        long c = cycle();
        return enabled && c >= 0 && (renew || c == 0);
    }

    static int season() {
        return seasonNumber + (int) Math.max(0, cycle());
    }

    /** Fin de la temporada en curso (epoch ms, a medianoche). */
    static long endMillis() {
        long c = Math.max(0, cycle());
        LocalDate end = seasonStart.plusDays((c + 1) * Math.max(1, seasonDays));
        return end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** XP total para llegar al nivel n. */
    static long xpFor(int n) {
        long total = 0;
        for (int i = 1; i <= n; i++) total += xpPerLevel + (i - 1) * xpExtra;
        return total;
    }

    static int levelOf(long xp) {
        int n = 0;
        while (n < levels && xp >= xpFor(n + 1)) n++;
        return n;
    }

    /** Los datos del jugador en esta temporada (si eran de otra, se empieza de cero). */
    private static JsonObject data(ServerPlayer player) {
        JsonObject p = STORE_IMPL.player(player.getUUID());
        if (JsonStore.num(p, "temporada") != season()) {
            for (String k : new ArrayList<>(p.keySet())) p.remove(k);
            p.addProperty("temporada", season());
            p.addProperty("xp", 0);
            p.add("reclamados", new JsonArray());
            STORE_IMPL.changed();
        }
        return p;
    }

    private static boolean claimed(JsonObject p, int level) {
        JsonElement e = p.get("reclamados");
        if (e == null || !e.isJsonArray()) return false;
        for (JsonElement x : e.getAsJsonArray()) if (x.getAsInt() == level) return true;
        return false;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // XP de las misiones
    // ---------------------------------------------------------------------------------------------------------------

    /** La XP de pase que da un tipo de misión (d, s, c), o 0 si el pase no está en marcha. */
    public static long xpOf(String kind) {
        if (!running()) return 0;
        return XP.getOrDefault(kind.equals("d") ? "diaria" : kind.equals("s") ? "semanal" : "caza", 0L);
    }

    /**
     * Lo llama PadMissions cuando un jugador completa una misión (kind: d diaria, s semanal, c caza). Devuelve la XP que
     * le dio (0 si el pase no está en marcha o ese tipo no da XP).
     */
    public static long missionDone(ServerPlayer player, String kind) {
        long xp = xpOf(kind);
        if (xp <= 0) return 0;
        JsonObject p = data(player);
        long before = JsonStore.num(p, "xp");
        int lvBefore = levelOf(before);
        p.addProperty("xp", before + xp);
        STORE_IMPL.changed();
        int lv = levelOf(before + xp);
        if (lv > lvBefore) {
            TFPadNet.notice(player, "TF Pass: ¡nivel " + lv + "! Reclama tu premio en el pad, app TF Pass.");
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.7F, 1.6F);
        }
        String shown = PadServer.get(player, "§vista", "");
        if (shown.startsWith("pase\n") && TFPadNet.hasPad(player)) PadServer.refresh(player, "pase", "");
        return xp;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Reclamar
    // ---------------------------------------------------------------------------------------------------------------

    /** Da los premios de un nivel. Devuelve false si no se puede (no llega, ya lo tiene, error al pagar). */
    private static boolean claim(ServerPlayer player, int level) {
        JsonObject p = data(player);
        if (level < 1 || level > levels || levelOf(JsonStore.num(p, "xp")) < level || claimed(p, level)) return false;
        // primero se marca (así un clic doble no lo da dos veces); si algo falla, se avisa
        p.getAsJsonArray("reclamados").add(level);
        STORE_IMPL.changed();
        boolean ok = true;
        for (PadPrize prize : REWARDS.getOrDefault(level, List.of())) ok &= prize.give(player);
        if (!ok) TFPadNet.notice(player, "Un premio del nivel " + level + " no se pudo dar: avisa al staff.");
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que se manda al pad
    // ---------------------------------------------------------------------------------------------------------------

    static final class App implements PadServer.DataApp {
        @Override
        public JsonObject data(ServerPlayer player) {
            JsonObject p = PadPass.data(player);
            long xp = JsonStore.num(p, "xp");
            int lv = levelOf(xp);
            JsonObject o = new JsonObject();
            o.addProperty("activo", running());
            o.addProperty("temporada", season());
            o.addProperty("nombre", seasonName);
            o.addProperty("fin", endMillis());
            o.addProperty("ahora", System.currentTimeMillis());
            o.addProperty("xp", xp);
            o.addProperty("nivel", lv);
            o.addProperty("max", levels);
            o.addProperty("xpEn", lv >= levels ? 0 : xp - xpFor(lv));
            o.addProperty("xpNivel", lv >= levels ? 0 : xpFor(lv + 1) - xpFor(lv));
            o.addProperty("verdes", PadGreen.balance(player.getUUID()));
            JsonArray src = new JsonArray();
            for (String[] k : new String[][] {{"diaria", "Misión diaria"}, {"semanal", "Misión semanal"}, {"caza", "Caza"}}) {
                long v = XP.getOrDefault(k[0], 0L);
                if (v <= 0) continue;
                JsonObject s = new JsonObject();
                s.addProperty("n", k[1]);
                s.addProperty("xp", v);
                src.add(s);
            }
            o.add("fuentes", src);
            JsonArray ls = new JsonArray();
            for (int i = 1; i <= levels; i++) {
                JsonObject l = new JsonObject();
                l.addProperty("n", i);
                l.addProperty("e", claimed(p, i) ? 2 : i <= lv ? 1 : 0);
                l.addProperty("x", xpFor(i));
                JsonArray pr = new JsonArray();
                for (PadPrize prize : REWARDS.getOrDefault(i, List.of())) pr.add(prize.json());
                l.add("p", pr);
                ls.add(l);
            }
            o.add("niveles", ls);
            return o;
        }

        @Override
        public JsonObject action(ServerPlayer player, String action) {
            if (!running()) {
                TFPadNet.notice(player, "No hay temporada del TF Pass en marcha.");
                return null;
            }
            if (action.equals("todo")) {
                JsonObject p = PadPass.data(player);
                int lv = levelOf(JsonStore.num(p, "xp")), n = 0;
                for (int i = 1; i <= lv; i++) if (!claimed(p, i) && claim(player, i)) n++;
                if (n > 0) {
                    player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.0F);
                    TFPadNet.notice(player, "Reclamaste " + n + (n == 1 ? " nivel" : " niveles") + " del TF Pass.");
                    JsonObject e = new JsonObject();
                    e.addProperty("reclamados", n);
                    return e;
                }
            } else if (action.startsWith("reclamar:")) {
                try {
                    int level = Integer.parseInt(action.substring(9));
                    if (claim(player, level)) {
                        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.2F);
                        JsonObject e = new JsonObject();
                        e.addProperty("reclamado", level);
                        return e;
                    }
                } catch (NumberFormatException ignored) {
                    // acción rara: nada
                }
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Configuración
    // ---------------------------------------------------------------------------------------------------------------

    static Path configFile() {
        return TFConfigDir.file("pase.json", null);
    }

    public static void loadConfig() {
        Path file = configFile();
        JsonObject o = TFJson.read(file);
        if (o == null) {
            o = defaults();
            if (!Files.exists(file)) TFJson.write(file, o);
        }
        enabled = TFJson.bool(o, "activado", true);
        JsonObject s = TFJson.obj(o, "temporada");
        seasonNumber = (int) Math.max(1, TFJson.num(s, "numero", 1));
        seasonName = TFJson.str(s, "nombre", "");
        seasonDays = (int) Math.max(1, TFJson.num(s, "dias", 60));
        renew = TFJson.bool(s, "renovar", true);
        try {
            seasonStart = LocalDate.parse(TFJson.str(s, "inicio", LocalDate.now().toString()));
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: pase.json: «temporada.inicio» no es una fecha (AAAA-MM-DD); se usa hoy");
            seasonStart = LocalDate.now();
        }
        levels = (int) Math.max(1, Math.min(200, TFJson.num(o, "niveles", 50)));
        xpPerLevel = Math.max(1, TFJson.num(o, "xpPorNivel", 400));
        xpExtra = Math.max(0, TFJson.num(o, "xpExtraPorNivel", 0));
        XP.clear();
        JsonObject xp = TFJson.obj(o, "xp");
        for (String k : new String[] {"diaria", "semanal", "caza"}) XP.put(k, Math.max(0, TFJson.num(xp, k, 0)));
        REWARDS.clear();
        if (o.has("premios") && o.get("premios").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("premios")) {
                try {
                    JsonObject l = e.getAsJsonObject();
                    int n = (int) TFJson.num(l, "nivel", 0);
                    if (n < 1 || n > levels) continue;
                    List<PadPrize> list = REWARDS.computeIfAbsent(n, k -> new ArrayList<>());
                    if (l.has("premios") && l.get("premios").isJsonArray()) {
                        for (JsonElement pe : l.getAsJsonArray("premios")) {
                            PadPrize p = PadPrize.read(pe, "pase.json (nivel " + n + ")");
                            if (p != null) list.add(p);
                        }
                    }
                } catch (Exception ex) {
                    TFClient.LOGGER.warn("TF Pad: pase.json: un nivel no se pudo leer: {}", ex.getMessage());
                }
            }
        }
        TFClient.LOGGER.info("TF Pad: TF Pass temporada {} ({} niveles, {} con premio)", season(), levels, REWARDS.size());
    }

    private static JsonObject level(int n, JsonObject... prizes) {
        JsonObject l = new JsonObject();
        l.addProperty("nivel", n);
        JsonArray a = new JsonArray();
        for (JsonObject p : prizes) a.add(p);
        l.add("premios", a);
        return l;
    }

    /**
     * Lo de fábrica: temporada 1 «El Despertar» desde hoy, 60 días (luego empieza sola la siguiente), 50 niveles de
     * 400 XP. Diaria 100 XP, semanal 500, caza 40: haciendo las diarias y las semanales se sube ~1,3 niveles al día (los
     * 50 en ~40 días); con las cazas, en ~25. Premios: 1 moneda verde en los niveles impares, 3 cada 5, 5 cada 10 (10
     * en el 50) y, entre medias, monedas y objetos útiles: 65 monedas verdes por temporada (65 tiradas del Gachapón).
     */
    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.addProperty("activado", true);
        JsonObject s = new JsonObject();
        s.addProperty("numero", 1);
        s.addProperty("nombre", "El Despertar");
        s.addProperty("inicio", LocalDate.now(ZoneId.systemDefault()).toString());
        s.addProperty("dias", 60);
        s.addProperty("renovar", true);
        o.add("temporada", s);
        o.addProperty("niveles", 50);
        o.addProperty("xpPorNivel", 400);
        o.addProperty("xpExtraPorNivel", 0);
        JsonObject xp = new JsonObject();
        xp.addProperty("diaria", 100);
        xp.addProperty("semanal", 500);
        xp.addProperty("caza", 40);
        o.add("xp", xp);
        String[][] items = {{"minecraft:bread", "16"}, {"minecraft:iron_ingot", "8"}, {"minecraft:experience_bottle", "8"},
                {"minecraft:golden_carrot", "8"}, {"minecraft:diamond", "2"}, {"minecraft:ender_pearl", "4"}, {"minecraft:golden_apple", "2"},
                {"minecraft:lapis_lazuli", "32"}};
        String[][] big = {{"minecraft:diamond", "8"}, {"minecraft:enchanted_golden_apple", "1"}, {"minecraft:totem_of_undying", "1"},
                {"minecraft:netherite_scrap", "2"}, {"minecraft:netherite_ingot", "1"}};
        JsonArray ls = new JsonArray();
        for (int n = 1; n <= 50; n++) {
            if (n % 10 == 0) {
                String[] it = big[n / 10 - 1];
                ls.add(level(n, PadPrize.of("verdes", n == 50 ? 10 : 5, null), PadPrize.of("objeto", Long.parseLong(it[1]), it[0])));
            } else if (n % 5 == 0) {
                ls.add(level(n, PadPrize.of("verdes", 3, null), PadPrize.of("monedas", 250L * (n / 5 + 1), null)));
            } else if (n % 2 == 1) {
                ls.add(level(n, PadPrize.of("verdes", 1, null)));
            } else if (n % 4 == 0) {
                ls.add(level(n, PadPrize.of("monedas", 100L + n * 10L, null)));
            } else {
                String[] it = items[(n / 2) % items.length];
                ls.add(level(n, PadPrize.of("objeto", Long.parseLong(it[1]), it[0])));
            }
        }
        o.add("premios", ls);
        return o;
    }
}
