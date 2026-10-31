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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.156, el dueño: «guerras de clanes: no de matarse entre ellos, completando objetivos; diseña bien bonito ese
 * apartado, todo dentro de la app de clanes; objetivos tipo misiones; nada que no se pueda cumplir ni del Nether ni del
 * End (vamos por early game); una barra de progreso vertical, como la del pase pero vertical, con ambos lados con su clan
 * y color, que se llena completando las misiones de la guerra; nada fácil: misiones desafiantes, cuentan como equipo»)
 * <ul>
 *   <li>El líder (o un colíder) declara la guerra a otro clan desde la pestaña GUERRA; el líder o un colíder del otro la
 *   acepta (24 h para hacerlo). Un clan solo está en una guerra a la vez; los aliados no se declaran la guerra.</li>
 *   <li>Dura {@link #DAYS} días. {@link #OBJECTIVES} objetivos de {@link #POOL} (solo Overworld, de equipo y duros), la
 *   meta de cada lado según sus miembros (PadClanProgress.goal). Cuenta lo que hace cada miembro con las reglas de los
 *   oficios (TFJobs.record → PadClanProgress.record → aquí).</li>
 *   <li>El avance de un lado es la media de sus objetivos (cada uno hasta el 100 %). Gana el que llega antes al 100 %, o
 *   el que más lleve al acabar el plazo (empate si van igual).</li>
 *   <li>Premio (nunca monedas amarillas): el ganador, 2 Clan Coins y 150 de prestigio y una moneda verde a cada uno que
 *   aportó; el otro, 30 de prestigio por luchar.</li>
 *   <li>La pantalla con la barra vertical doble es PadClanWarPage (cliente), con los datos de {@link #APP} (app «guerra»).</li>
 * </ul>
 * Datos en &lt;mundo&gt;/tfclient/clan_guerras.json: {guerras: [...], propuestas: [{de, a, t}]}.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadClanWar {
    static final JsonStore STORE_IMPL = new JsonStore("clan_guerras.json");
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.DataApp APP = new App();

    public static final int DAYS = 5, OBJECTIVES = 5;
    static final long WIN_COINS = 2, WIN_PRESTIGE = 150, LOSE_PRESTIGE = 30;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** Los objetivos de guerra (la meta es para un clan de 5; PadClanProgress.goal la escala con los miembros). */
    public static final List<PadClanProgress.Mission> POOL = List.of(
            w("cantera", "Gran cantera", "Rompan piedra.", Items.COBBLESTONE, "romper", "#minecraft:base_stone_overworld", 40000),
            w("lenadores", "Bosque talado", "Talen troncos.", Items.OAK_LOG, "romper", "#minecraft:logs", 8000),
            w("carbon", "Minas de carbón", "Minen carbón.", Items.COAL, "romper", "#forge:ores/coal", 3000),
            w("hierro", "Vetas de hierro", "Minen hierro.", Items.RAW_IRON, "romper", "#forge:ores/iron", 1500),
            w("cazadores", "Cacería nocturna", "Derroten monstruos.", Items.BONE, "matar", "hostil", 2500),
            w("creepers", "Sin un creeper", "Derroten creepers.", Items.GUNPOWDER, "matar", "minecraft:creeper", 500),
            w("cosecha", "La gran cosecha", "Cosechen cultivos maduros.", Items.WHEAT, "cosechar", "#minecraft:crops", 10000),
            w("pesca", "Flota de guerra", "Pesquen lo que sea.", Items.COD, "pescar", "*", 800),
            w("lingotes", "Forja de guerra", "Fundan lingotes.", Items.IRON_INGOT, "fundir", "#forge:ingots", 3000),
            w("constructores", "Murallas", "Coloquen bloques nuevos.", Items.STONE_BRICKS, "colocar", "*", 30000),
            w("granja", "Ganadería", "Críen animales.", Items.WHEAT_SEEDS, "criar", "*", 600),
            w("encantadores", "Arcanos", "Encanten objetos.", Items.ENCHANTED_BOOK, "encantar", "*", 250));

    private static PadClanProgress.Mission w(String id, String name, String desc, net.minecraft.world.item.Item icon, String type, String target,
                                             int amount) {
        return new PadClanProgress.Mission(id, name, desc, new ItemStack(icon), type, target, amount, 0, 0, 0);
    }

    private PadClanWar() {}

    static PadClanProgress.Mission byId(String id) {
        for (PadClanProgress.Mission m : POOL) if (m.id().equals(id)) return m;
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Datos
    // ---------------------------------------------------------------------------------------------------------------

    static JsonArray wars() {
        JsonElement e = STORE_IMPL.root.get("guerras");
        if (e == null || !e.isJsonArray()) {
            JsonArray a = new JsonArray();
            STORE_IMPL.root.add("guerras", a);
            return a;
        }
        return e.getAsJsonArray();
    }

    static JsonArray proposals() {
        JsonElement e = STORE_IMPL.root.get("propuestas");
        if (e == null || !e.isJsonArray()) {
            JsonArray a = new JsonArray();
            STORE_IMPL.root.add("propuestas", a);
            return a;
        }
        return e.getAsJsonArray();
    }

    /** La guerra en marcha de ese clan, o null. */
    public static JsonObject active(String clanId) {
        for (JsonElement e : wars()) {
            JsonObject w = e.getAsJsonObject();
            if (TFJson.str(w, "estado", "").equals("activa") && (clanId.equals(TFJson.str(w, "a", "")) || clanId.equals(TFJson.str(w, "b", "")))) return w;
        }
        return null;
    }

    /** La última guerra de ese clan (en marcha o acabada), o null. */
    static JsonObject last(String clanId) {
        JsonObject best = null;
        for (JsonElement e : wars()) {
            JsonObject w = e.getAsJsonObject();
            if (!(clanId.equals(TFJson.str(w, "a", "")) || clanId.equals(TFJson.str(w, "b", "")))) continue;
            if (best == null || TFJson.num(w, "inicio", 0) > TFJson.num(best, "inicio", 0)) best = w;
        }
        return best;
    }

    static String side(JsonObject w, String clanId) {
        return clanId.equals(TFJson.str(w, "a", "")) ? "A" : "B";
    }

    /** El avance de un lado (0 a 1): la media de sus objetivos. */
    public static double progress(JsonObject w, String side) {
        JsonArray objs = w.has("objetivos") ? w.getAsJsonArray("objetivos") : new JsonArray();
        if (objs.isEmpty()) return 0;
        double sum = 0;
        for (JsonElement e : objs) {
            JsonObject o = e.getAsJsonObject();
            double meta = Math.max(1, TFJson.dec(o, "meta" + side, 1));
            sum += Math.min(1, TFJson.dec(o, "prog" + side, 0) / meta);
        }
        return sum / objs.size();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Declarar, aceptar, acabar
    // ---------------------------------------------------------------------------------------------------------------

    /** Propone la guerra de un clan a otro. Devuelve el error o null. */
    static String declare(String from, String to) {
        if (from.equals(to)) return "No puedes declararte la guerra.";
        JsonObject a = PadClans.clan(from), b = PadClans.clan(to);
        if (a == null || b == null) return "Ese clan ya no existe.";
        if (PadClans.allies(a).contains(to)) return "Sois aliados: rompe la alianza antes.";
        if (active(from) != null) return "Tu clan ya está en guerra.";
        if (active(to) != null) return "Ese clan ya está en guerra.";
        for (JsonElement e : proposals()) {
            JsonObject p = e.getAsJsonObject();
            if (TFJson.str(p, "de", "").equals(from) && TFJson.str(p, "a", "").equals(to)) return "Ya le declaraste la guerra: espera a que acepte.";
        }
        JsonObject p = new JsonObject();
        p.addProperty("de", from);
        p.addProperty("a", to);
        p.addProperty("t", System.currentTimeMillis());
        proposals().add(p);
        STORE_IMPL.changed();
        PadClanProgress.tell(PadServer.server(), b, "¡El clan " + label(a) + " os declara la guerra! El líder o un colíder la acepta en pad › Clanes › GUERRA.",
                ChatFormatting.RED);
        PadClanProgress.tell(PadServer.server(), a, "Declarasteis la guerra a " + label(b) + ". Empieza cuando la acepten (tienen 24 h).", ChatFormatting.GOLD);
        return null;
    }

    static List<JsonObject> incoming(String clanId) {
        List<JsonObject> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (JsonElement e : proposals()) {
            JsonObject p = e.getAsJsonObject();
            if (TFJson.str(p, "a", "").equals(clanId) && now - TFJson.num(p, "t", 0) < DAY_MS && PadClans.clan(TFJson.str(p, "de", "")) != null) out.add(p);
        }
        return out;
    }

    static List<JsonObject> outgoing(String clanId) {
        List<JsonObject> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (JsonElement e : proposals()) {
            JsonObject p = e.getAsJsonObject();
            if (TFJson.str(p, "de", "").equals(clanId) && now - TFJson.num(p, "t", 0) < DAY_MS && PadClans.clan(TFJson.str(p, "a", "")) != null) out.add(p);
        }
        return out;
    }

    private static void dropProposals(String clanId) {
        JsonArray keep = new JsonArray();
        for (JsonElement e : proposals()) {
            JsonObject p = e.getAsJsonObject();
            if (!TFJson.str(p, "de", "").equals(clanId) && !TFJson.str(p, "a", "").equals(clanId)) keep.add(p);
        }
        STORE_IMPL.root.add("propuestas", keep);
        STORE_IMPL.changed();
    }

    /** Acepta la guerra que «from» declaró a «to»: empieza. Devuelve el error o null. */
    static String accept(String to, String from) {
        boolean found = false;
        for (JsonObject p : incoming(to)) if (TFJson.str(p, "de", "").equals(from)) found = true;
        if (!found) return "Esa declaración ya no está.";
        if (active(from) != null || active(to) != null) return "Uno de los dos ya está en guerra.";
        start(from, to, System.currentTimeMillis());
        return null;
    }

    static void reject(String to, String from) {
        JsonArray keep = new JsonArray();
        for (JsonElement e : proposals()) {
            JsonObject p = e.getAsJsonObject();
            if (!(TFJson.str(p, "de", "").equals(from) && TFJson.str(p, "a", "").equals(to))) keep.add(p);
        }
        STORE_IMPL.root.add("propuestas", keep);
        STORE_IMPL.changed();
        JsonObject a = PadClans.clan(from), b = PadClans.clan(to);
        if (a != null && b != null) PadClanProgress.tell(PadServer.server(), a, label(b) + " no aceptó la guerra.", ChatFormatting.GRAY);
    }

    /** Empieza la guerra entre a (el que la declaró) y b: elige los objetivos y sus metas. */
    static JsonObject start(String a, String b, long now) {
        dropProposals(a);
        dropProposals(b);
        JsonObject ca = PadClans.clan(a), cb = PadClans.clan(b);
        JsonObject w = new JsonObject();
        w.addProperty("id", UUID.randomUUID().toString().substring(0, 8));
        w.addProperty("a", a);
        w.addProperty("b", b);
        w.addProperty("inicio", now);
        w.addProperty("fin", now + DAYS * DAY_MS);
        w.addProperty("estado", "activa");
        List<PadClanProgress.Mission> pool = new ArrayList<>(POOL);
        Collections.shuffle(pool, new Random(now ^ (a + b).hashCode()));
        JsonArray objs = new JsonArray();
        int na = PadClans.members(ca).size(), nb = PadClans.members(cb).size();
        for (PadClanProgress.Mission m : pool.subList(0, OBJECTIVES)) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id());
            o.addProperty("metaA", PadClanProgress.goal(m, na));
            o.addProperty("metaB", PadClanProgress.goal(m, nb));
            o.addProperty("progA", 0.0);
            o.addProperty("progB", 0.0);
            objs.add(o);
        }
        w.add("objetivos", objs);
        w.add("aportesA", new JsonObject());
        w.add("aportesB", new JsonObject());
        wars().add(w);
        STORE_IMPL.changed();
        String text = "¡Guerra de clanes! " + label(ca) + " contra " + label(cb) + ": " + DAYS + " días, " + OBJECTIVES
                + " objetivos. Mírala en pad › Clanes › GUERRA.";
        PadClanProgress.tell(PadServer.server(), ca, text, ChatFormatting.RED);
        PadClanProgress.tell(PadServer.server(), cb, text, ChatFormatting.RED);
        return w;
    }

    static String label(JsonObject c) {
        return c == null ? "?" : TFJson.str(c, "nombre", "?") + " [" + TFJson.str(c, "etiqueta", "?") + "]";
    }

    /** Acaba una guerra: el ganador, los premios y los avisos. */
    static void finish(MinecraftServer server, JsonObject w) {
        if (!TFJson.str(w, "estado", "").equals("activa")) return;
        double pa = progress(w, "A"), pb = progress(w, "B");
        String winner = Math.abs(pa - pb) < 1e-9 ? "empate" : pa > pb ? "A" : "B";
        w.addProperty("estado", "acabada");
        w.addProperty("ganador", winner);
        w.addProperty("acabo", System.currentTimeMillis());
        STORE_IMPL.changed();
        JsonObject ca = PadClans.clan(TFJson.str(w, "a", "")), cb = PadClans.clan(TFJson.str(w, "b", ""));
        String score = Math.round(pa * 100) + " % a " + Math.round(pb * 100) + " %";
        if (winner.equals("empate")) {
            for (JsonObject c : new JsonObject[] {ca, cb}) {
                if (c == null) continue;
                PadClanProgress.addPrestige(c, LOSE_PRESTIGE);
                PadClanProgress.tell(server, c, "La guerra acaba en empate (" + score + "). +" + LOSE_PRESTIGE + " de prestigio.", ChatFormatting.GOLD);
            }
            return;
        }
        JsonObject win = winner.equals("A") ? ca : cb, lose = winner.equals("A") ? cb : ca;
        if (win != null) {
            PadClanProgress.addCoins(win, WIN_COINS);
            PadClanProgress.addPrestige(win, WIN_PRESTIGE);
            int greens = 0;
            JsonObject ap = TFJson.obj(w, "aportes" + winner);
            for (String k : ap.keySet()) {
                try {
                    UUID u = UUID.fromString(k);
                    if (TFJson.dec(ap, k, 0) <= 0 || !PadClans.members(win).contains(u)) continue;
                    PadGreen.add(server, u, 1);
                    greens++;
                } catch (IllegalArgumentException ignored) {
                    // se salta
                }
            }
            PadClanProgress.tell(server, win, "¡Ganasteis la guerra contra " + label(lose) + " (" + score + ")! +" + WIN_COINS + " Clan Coins, +" + WIN_PRESTIGE
                    + " de prestigio" + (greens > 0 ? " y una moneda verde para los " + greens + " que aportaron." : "."), ChatFormatting.GREEN);
        }
        if (lose != null) {
            PadClanProgress.addPrestige(lose, LOSE_PRESTIGE);
            PadClanProgress.tell(server, lose, "Perdisteis la guerra contra " + label(win) + " (" + score + "). +" + LOSE_PRESTIGE + " de prestigio por luchar.",
                    ChatFormatting.GRAY);
        }
    }

    /**
     * Lo que hizo un jugador (desde PadClanProgress.record): avanza los objetivos de su lado en la guerra de su clan. Si
     * su lado llega al 100 %, gana.
     */
    static void record(ServerPlayer player, String clanId, String type, Predicate<TFJobsConfig.Target> match, int amount) {
        JsonObject w = active(clanId);
        if (w == null || amount <= 0) return;
        if (System.currentTimeMillis() > TFJson.num(w, "fin", 0)) {
            finish(player.getServer(), w);
            return;
        }
        String side = side(w, clanId);
        boolean moved = false;
        for (JsonElement e : w.getAsJsonArray("objetivos")) {
            JsonObject o = e.getAsJsonObject();
            PadClanProgress.Mission m = byId(TFJson.str(o, "id", ""));
            if (m == null || !m.type().equals(type) || !match.test(new TFJobsConfig.Target(m.target()))) continue;
            double meta = TFJson.dec(o, "meta" + side, m.amount()), now = TFJson.dec(o, "prog" + side, 0);
            if (now >= meta) continue;
            double next = Math.min(meta, now + amount);
            o.addProperty("prog" + side, next);
            JsonObject ap = TFJson.obj(w, "aportes" + side);
            ap.addProperty(player.getUUID().toString(), TFJson.dec(ap, player.getUUID().toString(), 0) + (next - now));
            w.add("aportes" + side, ap);
            moved = true;
        }
        if (!moved) return;
        STORE_IMPL.changed();
        if (progress(w, side) >= 1.0 - 1e-9) finish(player.getServer(), w);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = PadServer.server();
        if (server == null || server.getTickCount() % 1200 != 0) return;
        long now = System.currentTimeMillis();
        for (JsonElement e : wars()) {
            JsonObject w = e.getAsJsonObject();
            if (TFJson.str(w, "estado", "").equals("activa") && now > TFJson.num(w, "fin", 0)) finish(server, w);
        }
    }

    /** Al disolver un clan: su guerra acaba (gana el otro) y sus declaraciones se van. */
    static void clanGone(String clanId) {
        JsonObject w = active(clanId);
        if (w != null) {
            String side = side(w, clanId);
            w.addProperty("estado", "acabada");
            w.addProperty("ganador", side.equals("A") ? "B" : "A");
            w.addProperty("acabo", System.currentTimeMillis());
        }
        dropProposals(clanId);
        STORE_IMPL.changed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La pestaña GUERRA de la app de clanes (declarar, aceptar) y alianzas
    // ---------------------------------------------------------------------------------------------------------------

    static PadView view(PadView.Builder b, ServerPlayer player, String id, JsonObject c) {
        UUID me = player.getUUID();
        boolean officer = PadClanProgress.treasurer(c, me);
        JsonObject w = active(id), lastWar = last(id);
        if (w != null) {
            String side = side(w, id);
            String other = TFJson.str(w, side.equals("A") ? "b" : "a", "");
            JsonObject oc = PadClans.clan(other);
            long left = Math.max(0, TFJson.num(w, "fin", 0) - System.currentTimeMillis());
            double mine = progress(w, side), theirs = progress(w, side.equals("A") ? "B" : "A");
            b.hero(new PadView.Row(new ItemStack(Items.IRON_SWORD), "En guerra contra " + label(oc), 0xC0392B,
                    List.of("Vosotros " + Math.round(mine * 100) + " % · ellos " + Math.round(theirs * 100) + " %",
                            "Quedan " + PadClanViews.until(left) + ". Gana quien llegue antes al 100 %."),
                    (float) mine, "", PadView.Btn.of("VER LA GUERRA", "§guerra", PadView.GOLD), null).image(PadClanProgress.badge(c).image()));
        } else if (lastWar != null && System.currentTimeMillis() - TFJson.num(lastWar, "acabo", 0) < 3 * DAY_MS) {
            String side = side(lastWar, id), win = TFJson.str(lastWar, "ganador", "");
            String other = TFJson.str(lastWar, side.equals("A") ? "b" : "a", "");
            b.hero(new PadView.Row(new ItemStack(Items.WHITE_BANNER), win.equals("empate") ? "Empate contra " + label(PadClans.clan(other))
                    : (win.equals(side) ? "Ganasteis contra " : "Perdisteis contra ") + label(PadClans.clan(other)), win.equals(side) ? 0x1E7C2C : 0x7E8CA8,
                    List.of("La última guerra: " + Math.round(progress(lastWar, side) * 100) + " % a " + Math.round(progress(lastWar, side.equals("A") ? "B" : "A") * 100) + " %."),
                    -1, "", PadView.Btn.of("VER", "§guerra", PadView.BLUE), null).image(PadClanProgress.badge(c).image()));
        }
        b.header(w != null ? "La guerra: objetivos de equipo durante " + DAYS + " días. Premio: " + WIN_COINS + " Clan Coins, " + WIN_PRESTIGE
                + " de prestigio y una verde a cada uno que aporte."
                : officer ? "Declara la guerra a otro clan (no es PvP: gana quien antes cumpla los objetivos). Y las alianzas: suman la mitad de lo suyo a vuestras misiones."
                : "Las guerras y alianzas las deciden el líder y los colíderes.");
        // declaraciones que nos llegan
        for (JsonObject p : incoming(id)) {
            JsonObject from = PadClans.clan(TFJson.str(p, "de", ""));
            b.row(new PadView.Row(new ItemStack(Items.IRON_SWORD), label(from) + " os declara la guerra", 0xC0392B,
                    List.of("Si aceptáis, empieza ya: " + DAYS + " días y " + OBJECTIVES + " objetivos."), -1, "",
                    officer ? PadView.Btn.of("ACEPTAR", "guerra.aceptar:" + TFJson.str(p, "de", ""), PadView.RED) : null,
                    officer ? PadView.Btn.of("NO", "guerra.rechazar:" + TFJson.str(p, "de", ""), PadView.GRAY) : null).image(PadClanProgress.badge(from).image()));
        }
        for (JsonObject p : outgoing(id)) {
            JsonObject to = PadClans.clan(TFJson.str(p, "a", ""));
            b.row(new PadView.Row(new ItemStack(Items.CLOCK), "Esperando a " + label(to), 0x7E8CA8, List.of("Le declarasteis la guerra."), -1, "", null, null)
                    .image(PadClanProgress.badge(to).image()));
        }
        // alianzas
        PadClanAlliances.rows(b, player, id, c, officer);
        // los demás clanes: declarar y proponer alianza
        if (officer) {
            int n = 0;
            for (Map.Entry<String, JsonObject> e : PadClanProgress.byPrestige()) {
                String oid = e.getKey();
                if (oid.equals(id) || n >= 20) continue;
                JsonObject oc = e.getValue();
                boolean ally = PadClans.allies(c).contains(oid);
                boolean busy = active(oid) != null;
                PadView.Btn war = w == null && !ally && !busy ? PadView.Btn.of("GUERRA", "guerra.declarar:" + oid, PadView.RED) : null;
                PadView.Btn al = !ally && PadClans.allies(c).size() < PadClanAlliances.MAX && PadClans.allies(oc).size() < PadClanAlliances.MAX
                        ? PadView.Btn.of("ALIANZA", "alianza.proponer:" + oid, PadView.GREEN) : null;
                b.row(new PadView.Row(new ItemStack(Items.WHITE_BANNER), label(oc), PadClans.rgbOf(oc),
                        List.of("Nivel " + PadClanProgress.level(oc) + " · " + PadClans.members(oc).size() + " miembros · " + PadClanViews.fmt(PadClanProgress.prestige(oc))
                                + " de prestigio" + (ally ? " · aliado" : busy ? " · en guerra" : "")), -1, "", war, al).image(PadClanProgress.badge(oc).image()));
                n++;
            }
        }
        b.empty("No hay más clanes.");
        return b.build();
    }

    /** Las acciones «guerra.…». */
    static String action(ServerPlayer player, String id, JsonObject c, String action) {
        if (!PadClanProgress.treasurer(c, player.getUUID())) return null;
        String verb = action.contains(":") ? action.substring(0, action.indexOf(':')) : action;
        String arg = action.contains(":") ? action.substring(action.indexOf(':') + 1) : "";
        String err = null;
        switch (verb) {
            case "guerra.declarar" -> {
                if (!PadServer.confirm(player, action)) {
                    TFPadNet.notice(player, "Pulsa otra vez GUERRA para declarársela a " + label(PadClans.clan(arg)) + ".");
                    return null;
                }
                err = declare(id, arg);
            }
            case "guerra.aceptar" -> err = accept(id, arg);
            case "guerra.rechazar" -> reject(id, arg);
            default -> {
                return null;
            }
        }
        if (err != null) TFPadNet.notice(player, err);
        return "guerra";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La app «guerra» (datos para la pantalla de la barra vertical doble)
    // ---------------------------------------------------------------------------------------------------------------

    private static final class App implements PadServer.DataApp {
        @Override
        public JsonObject data(ServerPlayer player) {
            String id = PadClans.clanIdOf(player.getUUID());
            JsonObject out = new JsonObject();
            JsonObject w = id == null ? null : active(id);
            if (w == null && id != null) w = last(id);
            out.addProperty("hay", w != null);
            if (w == null) return out;
            final JsonObject war = w;
            out.addProperty("estado", TFJson.str(w, "estado", ""));
            out.addProperty("quedan", Math.max(0, TFJson.num(w, "fin", 0) - System.currentTimeMillis()));
            out.addProperty("ganador", TFJson.str(w, "ganador", ""));
            out.addProperty("tuLado", side(w, id));
            out.addProperty("dias", DAYS);
            for (String s : new String[] {"A", "B"}) {
                JsonObject c = PadClans.clan(TFJson.str(w, s.toLowerCase(java.util.Locale.ROOT), ""));
                JsonObject o = new JsonObject();
                o.addProperty("nombre", c == null ? "(disuelto)" : TFJson.str(c, "nombre", "?"));
                o.addProperty("tag", c == null ? "?" : TFJson.str(c, "etiqueta", "?"));
                o.addProperty("color", c == null ? 0x7E8CA8 : PadClans.rgbOf(c));
                o.addProperty("insignia", c == null ? "" : PadClanProgress.badge(c).image());
                o.addProperty("pct", progress(w, s));
                o.addProperty("miembros", c == null ? 0 : PadClans.members(c).size());
                JsonArray top = new JsonArray();
                JsonObject holder = new JsonObject();
                holder.add("aportes", TFJson.obj(war, "aportes" + s));
                List<Map.Entry<UUID, Double>> list = new ArrayList<>(PadClanProgress.contributors(holder).entrySet());
                for (int i = 0; i < Math.min(3, list.size()); i++) {
                    JsonObject t = new JsonObject();
                    t.addProperty("nombre", PadStats.nameOf(list.get(i).getKey()));
                    t.addProperty("n", Math.round(list.get(i).getValue()));
                    top.add(t);
                }
                o.add("top", top);
                out.add(s.toLowerCase(java.util.Locale.ROOT), o);
            }
            JsonArray objs = new JsonArray();
            for (JsonElement e : w.getAsJsonArray("objetivos")) {
                JsonObject o = e.getAsJsonObject();
                PadClanProgress.Mission m = byId(TFJson.str(o, "id", ""));
                if (m == null) continue;
                JsonObject x = new JsonObject();
                x.addProperty("nombre", m.name());
                x.addProperty("desc", m.desc());
                x.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(m.icon().getItem())));
                for (String k : new String[] {"metaA", "metaB", "progA", "progB"}) x.addProperty(k, Math.round(TFJson.dec(o, k, 0)));
                objs.add(x);
            }
            out.add("objetivos", objs);
            return out;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Prueba en el juego
    // ---------------------------------------------------------------------------------------------------------------

    /** Para la prueba: quita las guerras y declaraciones de esos clanes. */
    public static void testReset(String... clanIds) {
        JsonArray keep = new JsonArray();
        for (JsonElement e : wars()) {
            JsonObject w = e.getAsJsonObject();
            boolean mine = false;
            for (String id : clanIds) mine |= id.equals(TFJson.str(w, "a", "")) || id.equals(TFJson.str(w, "b", ""));
            if (!mine) keep.add(w);
        }
        STORE_IMPL.root.add("guerras", keep);
        for (String id : clanIds) dropProposals(id);
        STORE_IMPL.changed();
    }

    public static String testDeclare(String from, String to) {
        return declare(from, to);
    }

    public static String testAccept(String to, String from) {
        return accept(to, from);
    }

    public static double testProgress(String clanId) {
        JsonObject w = active(clanId);
        if (w == null) w = last(clanId);
        return w == null ? -1 : progress(w, side(w, clanId));
    }

    public static String testState(String clanId) {
        JsonObject w = active(clanId);
        if (w == null) w = last(clanId);
        return w == null ? "ninguna" : TFJson.str(w, "estado", "") + "/" + TFJson.str(w, "ganador", "");
    }

    /** Para la prueba: sube cada objetivo de ese lado a la fracción f de su meta (sin acabarla). */
    public static void testFill(String clanId, double f) {
        JsonObject w = active(clanId);
        if (w == null) return;
        String side = side(w, clanId);
        for (JsonElement e : w.getAsJsonArray("objetivos")) {
            JsonObject o = e.getAsJsonObject();
            o.addProperty("prog" + side, Math.floor(TFJson.dec(o, "meta" + side, 1) * f));
        }
        STORE_IMPL.changed();
    }

    /** Para la prueba: la primera misión de la guerra (tipo y objetivo), para sumarle con PadClanProgress.record. */
    public static String[] testFirstObjective(String clanId) {
        JsonObject w = active(clanId);
        if (w == null) return null;
        PadClanProgress.Mission m = byId(TFJson.str(w.getAsJsonArray("objetivos").get(0).getAsJsonObject(), "id", ""));
        return m == null ? null : new String[] {m.type(), m.target()};
    }

    /** Para la prueba: cumple todos los objetivos de su lado con lo que haría el juego (y gana). */
    public static void testComplete(ServerPlayer player) {
        String id = PadClans.clanIdOf(player.getUUID());
        JsonObject w = id == null ? null : active(id);
        if (w == null) return;
        String side = side(w, id);
        for (JsonElement e : w.getAsJsonArray("objetivos")) {
            JsonObject o = e.getAsJsonObject();
            PadClanProgress.Mission m = byId(TFJson.str(o, "id", ""));
            if (m == null) continue;
            int left = (int) Math.ceil(TFJson.dec(o, "meta" + side, 0) - TFJson.dec(o, "prog" + side, 0));
            Predicate<TFJobsConfig.Target> p = net.tierrasfantasticas.tfclient.jobs.TFMissionCheck.sample(player.getServer(), m.type(), m.target());
            if (p != null && left > 0 && active(id) != null) record(player, id, m.type(), p, left);
        }
    }

    public static void testFinish(String clanId) {
        JsonObject w = active(clanId);
        if (w != null) finish(PadServer.server(), w);
    }
}
