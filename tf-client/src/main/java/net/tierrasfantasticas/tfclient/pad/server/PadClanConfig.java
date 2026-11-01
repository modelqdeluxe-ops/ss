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
import com.google.gson.JsonPrimitive;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.158, el dueño: «haz la config de los clanes configurable 100 por 100 con respecto a misiones, miembros y así, en
 * el config y en el pad de admin, que incluya la tienda») config/tfclient/clanes.json: todo lo de los clanes que antes
 * eran números fijos en el código.
 * <ul>
 *   <li><b>nivel</b>: el nivel máximo, la XP (base y cuánto crece por nivel) y las Clan Coins de subir.</li>
 *   <li><b>miembros</b>: las plazas (base, por nivel, tope por nivel y cuántas da cada «+plazas» de la tienda).</li>
 *   <li><b>misiones</b>: cuántas por semana, la verde de cada uno que aporta, lo que suman los aliados, cuánto cambia la
 *   meta con los miembros y la <b>lista</b> de misiones (cada una: nombre, descripción, icono, tipo, objetivo, cantidad
 *   para 5 miembros, Clan Coins, XP, prestigio y si está activa).</li>
 *   <li><b>potenciador</b>: cuánto multiplica.</li>
 *   <li><b>guerra</b>: los días, cuántos objetivos, los premios y la <b>lista</b> de objetivos.</li>
 *   <li><b>banco</b>: la capacidad, las mejoras y lo que cuestan.</li>
 *   <li><b>alianzas</b> y <b>duelos</b>.</li>
 *   <li><b>tienda</b>: lo que se vende en la tienda del clan (precio, cuántas por clan, si está a la venta y, según su
 *   tipo, lo que da: protecciones del clan, plazas, potenciador, un objeto, verdes para todos o comandos).</li>
 * </ul>
 * Lo cambia el pad de administrador (CLANES) o el archivo a mano y /tf reload. Lo que falte o esté mal se queda como
 * viene de fábrica (y el archivo se completa al cargarlo). Lo de fundar un clan sigue en pad.json (clanes.precio).
 */
public final class PadClanConfig {
    public static final String FILE = "clanes.json";
    /** Sube si cambian los valores de fábrica (los que no se hayan tocado se ponen al día solos: ver {@link #load}). */
    static final int VERSION = 1;

    private static JsonObject root = new JsonObject();

    private PadClanConfig() {}

    public static Path file() {
        return TFConfigDir.file(FILE, null);
    }

    /** El JSON tal cual (para el pad de administrador). */
    static JsonObject root() {
        return root;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo de fábrica
    // ---------------------------------------------------------------------------------------------------------------

    /** Una misión (o un objetivo de guerra, sin premio propio) en JSON. */
    static JsonObject mission(PadClanProgress.Mission m, boolean rewards) {
        JsonObject o = new JsonObject();
        o.addProperty("id", m.id());
        o.addProperty("nombre", m.name());
        o.addProperty("descripcion", m.desc());
        o.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(m.icon().getItem())));
        o.addProperty("tipo", m.type());
        o.addProperty("objetivo", m.target());
        o.addProperty("cantidad", m.amount());
        if (rewards) {
            o.addProperty("monedas", m.coins());
            o.addProperty("xp", m.xp());
            o.addProperty("prestigio", m.prestige());
        }
        o.addProperty("activa", true);
        return o;
    }

    private static JsonObject offer(String id, String name, String desc, String icon, long price, int max, String type) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nombre", name);
        o.addProperty("descripcion", desc);
        o.addProperty("icono", icon);
        o.addProperty("precio", price);
        o.addProperty("maximo", max);
        o.addProperty("tipo", type);
        o.addProperty("activa", true);
        return o;
    }

    private static JsonObject DEF;

    /** Lo de fábrica (se hace una vez: necesita los objetos del juego ya registrados). */
    static JsonObject defaults() {
        if (DEF == null) DEF = factory();
        return DEF;
    }

    private static JsonObject factory() {
        JsonObject r = new JsonObject();
        JsonArray help = new JsonArray();
        for (String h : new String[] {
                "Los clanes (1.3.158). Cambia los números y /tf reload (o el pad de administrador › CLANES). Lo que borres vuelve con lo de fábrica.",
                "misiones.lista y guerra.lista: tipo = romper, colocar, matar, pescar, cosechar, criar, fabricar, fundir o encantar (como los oficios);",
                "  objetivo = un id (minecraft:zombie), una etiqueta (#minecraft:logs), 'hostil' o '*'; cantidad = la meta para un clan de 5 miembros",
                "  (se escala con los miembros: de metaMin a metaMax veces). Nada del Nether ni del End: el servidor va por el inicio del juego.",
                "tienda: tipo = proteccion (piedra: claimstone_clan_50x50 o claimstone_clan_120x120), plazas (las de miembros.porCompra),",
                "  potenciador (horas), objeto ('objeto': lo pone el pad de administrador › CLANES › TIENDA › Vender lo que llevas en la mano,",
                "  o \"minecraft:diamond 3\"), verdes (cantidad para cada miembro) o comando (comandos: lista, desde la consola; {jugador},",
                "  {clan} y {etiqueta}). maximo = cuántas por clan (0 = sin tope); precio en Clan Coins; activa = a la venta.",
                "  En nombre y descripcion valen {cantidad}, {horas} y {x} (lo que multiplica el potenciador)."}) {
            help.add(new JsonPrimitive(h));
        }
        r.add("_ayuda", help);
        r.addProperty("version", VERSION);
        JsonObject nivel = new JsonObject();
        nivel.addProperty("maximo", 20);
        nivel.addProperty("xpBase", 1000);
        nivel.addProperty("xpCrece", 1.35);
        nivel.addProperty("monedas", 1);
        nivel.addProperty("monedasEspeciales", 2);
        nivel.addProperty("especialCada", 5);
        r.add("nivel", nivel);
        JsonObject miembros = new JsonObject();
        miembros.addProperty("base", 20);
        miembros.addProperty("porNivel", 2);
        miembros.addProperty("maximoPorNivel", 40);
        miembros.addProperty("porCompra", 2);
        r.add("miembros", miembros);
        JsonObject mis = new JsonObject();
        mis.addProperty("porSemana", 4);
        mis.addProperty("verdes", 1);
        mis.addProperty("aliados", 0.5);
        mis.addProperty("metaMin", 0.7);
        mis.addProperty("metaMax", 2.5);
        JsonArray ml = new JsonArray();
        for (PadClanProgress.Mission m : PadClanProgress.FACTORY) ml.add(mission(m, true));
        mis.add("lista", ml);
        r.add("misiones", mis);
        JsonObject pot = new JsonObject();
        pot.addProperty("multiplicador", 1.5);
        r.add("potenciador", pot);
        JsonObject war = new JsonObject();
        war.addProperty("dias", 5);
        war.addProperty("objetivos", 5);
        war.addProperty("monedas", 2);
        war.addProperty("prestigio", 150);
        war.addProperty("prestigioPerdedor", 30);
        war.addProperty("verdes", 1);
        war.addProperty("horasParaAceptar", 24);
        JsonArray wl = new JsonArray();
        for (PadClanProgress.Mission m : PadClanWar.FACTORY) wl.add(mission(m, false));
        war.add("lista", wl);
        r.add("guerra", war);
        JsonObject bank = new JsonObject();
        bank.addProperty("inicial", 5000);
        bank.addProperty("porMejora", 5000);
        bank.addProperty("mejoras", 19);
        bank.addProperty("primeraMejora", 2);
        bank.addProperty("subePorMejora", 1);
        r.add("banco", bank);
        JsonObject al = new JsonObject();
        al.addProperty("maximo", 2);
        r.add("alianzas", al);
        JsonObject du = new JsonObject();
        du.addProperty("prestigio", 25);
        du.addProperty("horasPorPareja", 24);
        du.addProperty("distancia", 48);
        du.addProperty("huida", 64);
        du.addProperty("segundos", 180);
        du.addProperty("segundosParaAceptar", 60);
        r.add("duelos", du);
        // (1.3.158, el dueño: «sube los precios») el doble que en la 1.3.156
        JsonArray shop = new JsonArray();
        JsonObject p50 = offer("proteccion_clan_50x50", "Protección del clan 50x50",
                "El territorio del clan: todos los del clan construyen, sin PvP entre vosotros y con el aviso «Territorio de [TAG]». La pone el líder o un colíder.",
                "tfclient:claimstone_clan_50x50", 30, 2, "proteccion");
        p50.addProperty("piedra", "claimstone_clan_50x50");
        shop.add(p50);
        JsonObject p120 = offer("proteccion_clan_120x120", "Protección del clan 120x120",
                "Un territorio grande del clan (120x120), con todo lo de la de 50x50. La pone el líder o un colíder.",
                "tfclient:claimstone_clan_120x120", 80, 2, "proteccion");
        p120.addProperty("piedra", "claimstone_clan_120x120");
        shop.add(p120);
        shop.add(offer("plazas", "+{cantidad} plazas en el clan", "{cantidad} miembros más, además de los que da el nivel (las de cada compra: miembros.porCompra).",
                "minecraft:player_head", 20, 5, "plazas"));
        JsonObject boost = offer("potenciador", "Potenciador de misiones ({horas} h)", "Durante {horas} horas, todo lo que hagáis cuenta {x} en las misiones del clan.",
                "minecraft:experience_bottle", 6, 0, "potenciador");
        boost.addProperty("horas", 24);
        shop.add(boost);
        r.add("tienda", shop);
        return r;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Cargar
    // ---------------------------------------------------------------------------------------------------------------

    /** Lee clanes.json (lo que falte, de fábrica), lo completa en el archivo y lo reparte a los módulos de clanes. */
    public static void load() {
        JsonObject def = defaults();
        JsonObject cur = TFJson.read(file());
        if (cur == null) cur = new JsonObject();
        JsonObject merged = merge(def, cur);
        merged.add("_ayuda", def.get("_ayuda"));
        merged.addProperty("version", VERSION);
        root = merged;
        TFJson.write(file(), merged);
        apply();
    }

    /** Guarda el JSON (desde el pad de administrador) y lo vuelve a aplicar. */
    static void save(JsonObject r) {
        root = merge(defaults(), r);
        TFJson.write(file(), root);
        apply();
    }

    /** def con lo que haya en cur encima (los objetos se mezclan; las listas y los valores de cur mandan si son del mismo tipo). */
    private static JsonObject merge(JsonObject def, JsonObject cur) {
        JsonObject out = new JsonObject();
        for (String k : def.keySet()) {
            JsonElement d = def.get(k), c = cur.get(k);
            if (c == null) out.add(k, d.deepCopy());
            else if (d.isJsonObject() && c.isJsonObject()) out.add(k, merge(d.getAsJsonObject(), c.getAsJsonObject()));
            else if (d.isJsonArray() == c.isJsonArray() && d.isJsonPrimitive() == c.isJsonPrimitive()) out.add(k, c.deepCopy());
            else out.add(k, d.deepCopy());
        }
        for (String k : cur.keySet()) if (!out.has(k)) out.add(k, cur.get(k).deepCopy());
        return out;
    }

    static long num(String section, String key) {
        return TFJson.num(TFJson.obj(root, section), key, TFJson.num(TFJson.obj(defaults(), section), key, 0));
    }

    static double dec(String section, String key) {
        return TFJson.dec(TFJson.obj(root, section), key, TFJson.dec(TFJson.obj(defaults(), section), key, 0));
    }

    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id);
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return it == null || it == Items.AIR ? Items.BOOK : it;
    }

    /** La «lista» de una sección. */
    static JsonArray list(JsonObject r, String section) {
        JsonObject o = TFJson.obj(r, section);
        return o.has("lista") && o.get("lista").isJsonArray() ? o.getAsJsonArray("lista") : new JsonArray();
    }

    /** Las misiones de una lista (onlyActive: sin las desactivadas). Las de id repetido o vacío se saltan. */
    static List<PadClanProgress.Mission> missions(JsonArray list, boolean withRewards, boolean onlyActive) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<PadClanProgress.Mission> out = new ArrayList<>();
        if (list == null) return out;
        for (JsonElement e : list) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (onlyActive && !TFJson.bool(o, "activa", true)) continue;
            String id = TFJson.str(o, "id", "").trim();
            if (id.isEmpty() || !seen.add(id)) continue;
            out.add(new PadClanProgress.Mission(id, TFJson.str(o, "nombre", id), TFJson.str(o, "descripcion", ""),
                    new ItemStack(item(TFJson.str(o, "icono", "minecraft:book"))), TFJson.str(o, "tipo", "romper"), TFJson.str(o, "objetivo", "*"),
                    (int) Math.max(1, TFJson.num(o, "cantidad", 100)), withRewards ? Math.max(0, TFJson.num(o, "monedas", 1)) : 0,
                    withRewards ? Math.max(0, TFJson.num(o, "xp", 300)) : 0, withRewards ? Math.max(0, TFJson.num(o, "prestigio", 20)) : 0));
        }
        return out;
    }

    /** Pasa los valores a cada módulo (que los lee de sus campos de siempre). */
    private static void apply() {
        try {
            PadClanProgress.MAX_LEVEL = (int) Math.max(1, Math.min(200, num("nivel", "maximo")));
            PadClanProgress.MISSIONS_PER_WEEK = (int) Math.max(0, Math.min(20, num("misiones", "porSemana")));
            PadClanProgress.ALLY_SHARE = Math.max(0, Math.min(1, dec("misiones", "aliados")));
            PadClanProgress.BOOST = Math.max(1, Math.min(10, dec("potenciador", "multiplicador")));
            List<PadClanProgress.Mission> all = missions(list(root, "misiones"), true, false), on = missions(list(root, "misiones"), true, true);
            PadClanProgress.KNOWN = all.isEmpty() ? PadClanProgress.FACTORY : all;
            // apagarlas todas = no salen misiones (para quitarlas también vale misiones.porSemana = 0)
            PadClanProgress.POOL = all.isEmpty() ? PadClanProgress.FACTORY : on;
            PadClanWar.DAYS = (int) Math.max(1, Math.min(60, num("guerra", "dias")));
            List<PadClanProgress.Mission> wall = missions(list(root, "guerra"), false, false), won = missions(list(root, "guerra"), false, true);
            PadClanWar.KNOWN = wall.isEmpty() ? PadClanWar.FACTORY : wall;
            PadClanWar.POOL = won.isEmpty() ? PadClanWar.FACTORY : won;
            PadClanWar.OBJECTIVES = (int) Math.max(1, Math.min(PadClanWar.POOL.size(), num("guerra", "objetivos")));
            PadClanWar.WIN_COINS = Math.max(0, num("guerra", "monedas"));
            PadClanWar.WIN_PRESTIGE = Math.max(0, num("guerra", "prestigio"));
            PadClanWar.LOSE_PRESTIGE = Math.max(0, num("guerra", "prestigioPerdedor"));
            PadClanWar.ACCEPT_MS = Math.max(1, num("guerra", "horasParaAceptar")) * 3_600_000L;
            PadClanBank.BASE = Math.max(1, num("banco", "inicial"));
            PadClanBank.STEP = Math.max(0, num("banco", "porMejora"));
            PadClanBank.MAX_UPGRADES = (int) Math.max(0, Math.min(1000, num("banco", "mejoras")));
            PadClanAlliances.MAX = (int) Math.max(0, Math.min(20, num("alianzas", "maximo")));
            PadClanDuels.PRESTIGE = Math.max(0, num("duelos", "prestigio"));
            PadClanDuels.PAIR_MS = Math.max(0, num("duelos", "horasPorPareja")) * 3_600_000L;
            PadClanDuels.START_DIST = Math.max(2, num("duelos", "distancia"));
            PadClanDuels.FLEE_DIST = Math.max(PadClanDuels.START_DIST, num("duelos", "huida"));
            PadClanDuels.MAX_MS = Math.max(10, num("duelos", "segundos")) * 1000L;
            PadClanDuels.CHALLENGE_MS = Math.max(10, num("duelos", "segundosParaAceptar")) * 1000L;
            PadClanShop.load(root.has("tienda") && root.get("tienda").isJsonArray() ? root.getAsJsonArray("tienda") : new JsonArray());
        } catch (RuntimeException e) {
            TFClient.LOGGER.error("TF Clanes: clanes.json no se pudo aplicar entero", e);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que se calcula con la config
    // ---------------------------------------------------------------------------------------------------------------

    /** XP para pasar del nivel dado al siguiente: xpBase · xpCrece^(nivel−1), redondeada a 50. */
    static long xpFor(int level) {
        return Math.round(Math.max(1, num("nivel", "xpBase")) * Math.pow(Math.max(1, dec("nivel", "xpCrece")), Math.max(0, level - 1)) / 50.0) * 50;
    }

    /** Las plazas de un clan de ese nivel con esas compras de «+plazas». */
    static int seats(int level, long bought) {
        long byLevel = Math.min(num("miembros", "maximoPorNivel"), num("miembros", "base") + num("miembros", "porNivel") * (level - 1));
        return (int) Math.max(1, byLevel + num("miembros", "porCompra") * Math.max(0, bought));
    }

    /** Las Clan Coins de subir a ese nivel. */
    static long levelReward(int level) {
        long every = num("nivel", "especialCada");
        return every > 0 && level % every == 0 ? num("nivel", "monedasEspeciales") : num("nivel", "monedas");
    }

    /** «x1,5» (lo que multiplica el potenciador). */
    public static String boostText() {
        return "x" + decimal(PadClanProgress.BOOST);
    }

    /** «la mitad» o «el 30 %» (lo que suman los aliados). */
    public static String allyText() {
        double s = PadClanProgress.ALLY_SHARE;
        return Math.abs(s - 0.5) < 1e-9 ? "la mitad" : "el " + Math.round(s * 100) + " %";
    }

    static String decimal(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0).replace('.', ',');
    }

    static long missionGreens() {
        return Math.max(0, num("misiones", "verdes"));
    }

    static long warGreens() {
        return Math.max(0, num("guerra", "verdes"));
    }

    /** Cuánto se multiplica la meta de una misión para un clan de n miembros (1 con 5). */
    static double goalFactor(int members) {
        return Math.max(dec("misiones", "metaMin"), Math.min(dec("misiones", "metaMax"), 0.5 + 0.1 * members));
    }

    /**
     * (1.3.158) Para la prueba en el juego: cambia clanes.json como el pad de administrador (plazas al empezar, precio de
     * la protección 50x50, una misión apagada) y mira que vale al momento; luego lo deja como estaba.
     */
    public static String testConfig(String clanId) {
        JsonObject before = root.deepCopy();
        JsonObject c = PadClans.clan(clanId);
        int seats0 = PadClanProgress.maxMembers(c);
        PadClanShop.Offer o0 = PadClanShop.offer("proteccion_clan_50x50");
        long price0 = o0 == null ? -1 : o0.price();
        int pool0 = PadClanProgress.POOL.size();
        JsonObject r = root.deepCopy();
        JsonObject m = TFJson.obj(r, "miembros");
        m.addProperty("base", TFJson.num(m, "base", 20) + 10);
        for (JsonElement e : r.getAsJsonArray("tienda")) {
            if (TFJson.str(e.getAsJsonObject(), "id", "").equals("proteccion_clan_50x50")) e.getAsJsonObject().addProperty("precio", 45);
        }
        list(r, "misiones").get(0).getAsJsonObject().addProperty("activa", false);
        save(r);
        int seats1 = PadClanProgress.maxMembers(c);
        PadClanShop.Offer o1 = PadClanShop.offer("proteccion_clan_50x50");
        long price1 = o1 == null ? -1 : o1.price();
        int pool1 = PadClanProgress.POOL.size();
        JsonObject disk = TFJson.read(file());
        boolean saved = disk != null && TFJson.num(TFJson.obj(disk, "miembros"), "base", 0) == TFJson.num(m, "base", 0);
        save(before);
        int seats2 = PadClanProgress.maxMembers(c);
        boolean ok = seats1 == seats0 + 10 && price0 == 30 && price1 == 45 && pool1 == pool0 - 1 && saved
                && seats2 == seats0 && PadClanShop.offer("proteccion_clan_50x50").price() == 30 && PadClanProgress.POOL.size() == pool0;
        return "plazas " + seats0 + " → " + seats1 + " (+10 al empezar), protección 50x50 " + price0 + " → " + price1 + " Clan Coins, misiones que salen "
                + pool0 + " → " + pool1 + ", guardado en el archivo " + saved + "; al deshacerlo " + seats2 + " plazas" + (ok ? " (bien)" : " (MAL)");
    }

    /** Lo que cuesta la mejora número next del banco. */
    static long bankUpgradeCost(int next) {
        return Math.max(0, num("banco", "primeraMejora") + num("banco", "subePorMejora") * (next - 1L));
    }
}
