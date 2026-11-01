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
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.jobs.TFMissionCheck;
import net.tierrasfantasticas.tfclient.pad.PadConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.158) CLANES en el pad de administrador: todo config/tfclient/clanes.json ({@link PadClanConfig}) sin tocar el
 * archivo. El dueño: «haz la config de los clanes configurable 100 por 100 con respecto a misiones, miembros y así, en el
 * config y en el pad de admin, que incluya la tienda».
 * <ul>
 *   <li>GENERAL: lo que cuesta fundar (pad.json), las plazas, los niveles, el banco, las alianzas y los duelos.</li>
 *   <li>MISIONES: cuántas por semana, las verdes, lo de los aliados, la meta según los miembros, el potenciador y la lista
 *   (encender o apagar cada una; pulsarla para cambiar nombre, tipo, objetivo, cantidad, premio e icono; añadir).</li>
 *   <li>GUERRA: días, objetivos, premios y la lista de objetivos (igual que las misiones, sin premio propio).</li>
 *   <li>TIENDA: lo que se vende en la tienda del clan (a la venta o no, precio, cuántas por clan, tipo y lo que da);
 *   AÑADIR LO QUE LLEVAS EN LA MANO la vende como objeto.</li>
 * </ul>
 * Se guarda al momento y vale al momento (como /tf reload).
 */
public final class PadAdminClans {
    public static final PadServer.App APP = new App();
    private static final String APP_ID = "a_clanes";
    private static final int TEXT = 0x18265C, MUTED = 0x7E8CA8;
    private static final int INT = 0, DEC = 1, PCT = 2;

    /** Los tipos de misión (los de los oficios). */
    static final List<String> TYPES = List.of("romper", "colocar", "matar", "pescar", "cosechar", "criar", "fabricar", "fundir", "encantar");
    /** Los tipos de oferta de la tienda del clan. */
    static final List<String> OFFER_TYPES = List.of("proteccion", "plazas", "potenciador", "escudo", "estandarte", "objeto", "verdes", "comando");

    private PadAdminClans() {}

    /** Un número de clanes.json: su sitio (sección.clave), cómo se llama, para qué sirve, su icono, límites y clase. */
    private record Num(String id, String title, String help, Item icon, double min, double max, int kind) {
        /** La sección (puede ser anidada: «guerra.pvp»). */
        String section() {
            return id.substring(0, id.lastIndexOf('.'));
        }

        String key() {
            return id.substring(id.lastIndexOf('.') + 1);
        }
    }

    /** El objeto de una sección (anidada con puntos) dentro de r; lo crea si falta (r cambia). */
    private static JsonObject sect(JsonObject r, String path) {
        JsonObject cur = r;
        for (String part : path.split("\\.")) {
            if (!cur.has(part) || !cur.get(part).isJsonObject()) cur.add(part, new JsonObject());
            cur = cur.getAsJsonObject(part);
        }
        return cur;
    }

    private static final String PRICE = "clanes.precio";

    private static final List<Num> GENERAL = List.of(
            new Num("miembros.base", "Plazas al empezar", "Los miembros que caben en un clan de nivel 1.", Items.PLAYER_HEAD, 1, 1000, INT),
            new Num("miembros.porNivel", "Plazas por nivel", "Las que se suman en cada nivel.", Items.PLAYER_HEAD, 0, 100, INT),
            new Num("miembros.maximoPorNivel", "Tope de plazas por nivel", "Lo más que dan los niveles (sin las compradas).", Items.PLAYER_HEAD, 1, 1000, INT),
            new Num("miembros.porCompra", "Plazas de cada compra", "Las que da cada «+plazas» de la tienda del clan.", Items.PLAYER_HEAD, 0, 100, INT),
            new Num("nivel.maximo", "Nivel máximo", "Hasta dónde sube un clan.", Items.EXPERIENCE_BOTTLE, 1, 200, INT),
            new Num("nivel.xpBase", "XP del nivel 1 al 2", "La XP que pide el primer nivel.", Items.EXPERIENCE_BOTTLE, 1, 100_000_000, INT),
            new Num("nivel.xpCrece", "Cuánto crece la XP", "Cada nivel pide esto por la XP del anterior (1,35 = un 35 % más).", Items.EXPERIENCE_BOTTLE, 1, 5, DEC),
            new Num("nivel.monedas", "Clan Coins por nivel", "Las que gana el clan al subir de nivel.", Items.PRISMARINE_CRYSTALS, 0, 100, INT),
            new Num("nivel.monedasEspeciales", "Clan Coins de los niveles especiales", "Las de los niveles 5, 10, 15… (ver abajo).", Items.PRISMARINE_CRYSTALS, 0, 100, INT),
            new Num("nivel.especialCada", "Cada cuántos niveles, los especiales", "0 = ninguno.", Items.PRISMARINE_CRYSTALS, 0, 200, INT),
            new Num("banco.inicial", "Banco: capacidad al empezar", "Los objetos que caben sin mejoras.", Items.CHEST, 1, 100_000_000, INT),
            new Num("banco.porMejora", "Banco: capacidad por mejora", "Lo que suma cada mejora.", Items.CHEST, 0, 100_000_000, INT),
            new Num("banco.mejoras", "Banco: mejoras como mucho", "Cuántas veces se puede mejorar.", Items.CHEST, 0, 1000, INT),
            new Num("banco.primeraMejora", "Banco: precio de la 1.ª mejora", "En Clan Coins.", Items.CHEST, 0, 100_000, INT),
            new Num("banco.subePorMejora", "Banco: lo que sube cada mejora", "La mejora n cuesta la 1.ª + esto × (n − 1).", Items.CHEST, 0, 100_000, INT),
            new Num("alianzas.maximo", "Aliados por clan", "Cuántas alianzas puede tener un clan.", Items.WHITE_BANNER, 0, 20, INT),
            new Num("duelos.prestigio", "Duelos: prestigio al ganar", "Para el clan del ganador.", Items.IRON_SWORD, 0, 100_000, INT),
            new Num("duelos.horasPorPareja", "Duelos: horas entre la misma pareja", "Antes, el duelo no da prestigio (contra el farmeo).", Items.CLOCK, 0, 720, INT),
            new Num("duelos.distancia", "Duelos: distancia para retar", "En bloques.", Items.COMPASS, 2, 512, INT),
            new Num("duelos.huida", "Duelos: distancia de huida", "Más lejos, pierde el que huye.", Items.COMPASS, 2, 1024, INT),
            new Num("duelos.segundos", "Duelos: lo que dura", "En segundos.", Items.CLOCK, 10, 3600, INT),
            new Num("duelos.segundosParaAceptar", "Duelos: tiempo para aceptar", "En segundos.", Items.CLOCK, 10, 3600, INT));

    private static final List<Num> MISSIONS = List.of(
            new Num("misiones.porSemana", "Misiones por semana", "Las que le salen a cada clan el lunes.", Items.WRITABLE_BOOK, 0, 20, INT),
            new Num("misiones.verdes", "Verdes por misión", "Las que gana cada miembro que aportó al cumplirse.", Items.EMERALD, 0, 100, INT),
            new Num("misiones.aliados", "Lo que suman los aliados", "De lo que hace un clan aliado.", Items.WHITE_BANNER, 0, 100, PCT),
            new Num("misiones.metaMin", "Meta de un clan pequeño", "La cantidad de cada misión es para 5 miembros; con menos, por esto como poco.", Items.TARGET, 0.1, 10, DEC),
            new Num("misiones.metaMax", "Meta de un clan grande", "Con muchos miembros, por esto como mucho.", Items.TARGET, 0.1, 20, DEC),
            new Num("potenciador.multiplicador", "Potenciador de la tienda", "Lo que multiplica lo que se hace mientras dura.", Items.EXPERIENCE_BOTTLE, 1, 10, DEC));

    private static final List<Num> WAR = List.of(
            new Num("guerra.dias", "Días que dura", "Gana quien llegue antes al 100 % o el que más lleve al final.", Items.CLOCK, 1, 60, INT),
            new Num("guerra.pvp.cantidad", "PvP: derrotas para el 100 %", "El objetivo PvP (para un clan de 5): derrotar a los del clan rival.", Items.IRON_SWORD, 1, 1000, INT),
            new Num("guerra.pvp.minutos", "PvP: minutos entre la misma pareja", "Matar al mismo otra vez no cuenta hasta entonces (contra el farmeo).", Items.CLOCK, 0, 1440, INT),
            new Num("guerra.objetivos", "Objetivos", "Cuántos de la lista salen en cada guerra.", Items.TARGET, 1, 20, INT),
            new Num("guerra.monedas", "Clan Coins del ganador", "", Items.PRISMARINE_CRYSTALS, 0, 100, INT),
            new Num("guerra.prestigio", "Prestigio del ganador", "", Items.GOLDEN_HELMET, 0, 100_000, INT),
            new Num("guerra.prestigioPerdedor", "Prestigio del perdedor", "Por luchar (y el de cada clan si hay empate).", Items.IRON_HELMET, 0, 100_000, INT),
            new Num("guerra.verdes", "Verdes de cada uno que aportó", "Solo del clan ganador.", Items.EMERALD, 0, 100, INT),
            new Num("guerra.horasParaAceptar", "Horas para aceptar", "Lo que tiene el otro clan para aceptar la guerra.", Items.CLOCK, 1, 720, INT));

    private static Num num(String id) {
        for (List<Num> l : List.of(GENERAL, MISSIONS, WAR)) for (Num n : l) if (n.id.equals(id)) return n;
        return null;
    }

    private static String shown(Num n, JsonObject r) {
        JsonObject s = sect(r.deepCopy(), n.section());
        return switch (n.kind) {
            case DEC -> "x" + PadClanConfig.decimal(TFJson.dec(s, n.key(), 1));
            case PCT -> Math.round(TFJson.dec(s, n.key(), 0) * 100) + " %";
            default -> PadView.number(TFJson.num(s, n.key(), 0));
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Listas
    // ---------------------------------------------------------------------------------------------------------------

    /** La lista de una pestaña: «misiones», «guerra» o «tienda». */
    private static JsonArray list(JsonObject r, String which) {
        if (which.equals("tienda")) {
            if (!r.has("tienda") || !r.get("tienda").isJsonArray()) r.add("tienda", new JsonArray());
            return r.getAsJsonArray("tienda");
        }
        JsonObject s = TFJson.obj(r, which);
        if (!s.has("lista") || !s.get("lista").isJsonArray()) s.add("lista", new JsonArray());
        r.add(which, s);
        return s.getAsJsonArray("lista");
    }

    private static JsonObject find(JsonArray a, String id) {
        for (JsonElement e : a) if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) return e.getAsJsonObject();
        return null;
    }

    private static String which(char c) {
        return c == 'm' ? "misiones" : c == 'w' ? "guerra" : "tienda";
    }

    private static ItemStack icon(JsonObject o) {
        return new ItemStack(PadClanConfig.item(TFJson.str(o, "icono", "minecraft:book")));
    }

    /** ¿Cuenta ese objetivo para ese tipo? (algo de verdad del juego que lo cumple, como la prueba de los clanes). */
    private static boolean counts(String type, String target) {
        try {
            Predicate<TFJobsConfig.Target> p = TFMissionCheck.sample(PadServer.server(), type, target);
            return p != null && p.test(new TFJobsConfig.Target(target));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String typeName(String t) {
        return switch (t) {
            case "proteccion" -> "Protección del clan";
            case "plazas" -> "Plazas";
            case "potenciador" -> "Potenciador";
            case "escudo" -> "Escudo de guerra";
            case "objeto" -> "Objeto";
            case "verdes" -> "Verdes para todos";
            case "estandarte" -> "Estandarte del clan";
            case "comando" -> "Comandos";
            default -> t;
        };
    }

    private static String newId(JsonArray a, String name, String prefix) {
        String base = prefix + PadAdminUi.slug(name, "nueva");
        String id = base;
        for (int i = 2; find(a, id) != null; i++) id = base + "_" + i;
        return id;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vistas
    // ---------------------------------------------------------------------------------------------------------------

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            JsonObject r = PadClanConfig.root();
            if (tab.startsWith("pi:") && tab.length() > 5) {
                String what = tab.substring(3);
                JsonObject o = find(list(r.deepCopy(), which(what.charAt(0))), what.substring(2));
                if (o != null) {
                    return PadItemPicker.builder(player, APP_ID, tab, "Icono de «" + TFJson.str(o, "nombre", "") + "»: pulsa el objeto que saldrá en su fila.",
                            false, TFJson.str(o, "icono", "")).footer(PadView.Btn.of("ATRÁS", "tab:" + what, PadView.BLUE)).build();
                }
            }
            if (tab.length() > 2 && tab.charAt(1) == ':' && "mwt".indexOf(tab.charAt(0)) >= 0) {
                JsonObject o = find(list(r.deepCopy(), which(tab.charAt(0))), tab.substring(2));
                if (o != null) return tab.charAt(0) == 't' ? offer(player, tab, o) : mission(player, tab, o);
                tab = which(tab.charAt(0));
            }
            if (!List.of("general", "misiones", "guerra", "tienda").contains(tab)) tab = "general";
            PadView.Builder b = PadView.of(APP_ID).selected(tab).compact()
                    .tab("general", "GENERAL").tab("misiones", "MISIONES").tab("guerra", "GUERRA").tab("tienda", "TIENDA");
            String f = PadAdminUi.field(player, APP_ID, tab);
            switch (tab) {
                case "misiones" -> {
                    b.header("Pulsa una misión para cambiarla; SÍ/NO: si sale o no.");
                    for (Num n : MISSIONS) b.row(PadAdminUi.value(new ItemStack(n.icon), n.title, n.help, shown(n, r), "f:" + n.id, f.equals(n.id)));
                    missionRows(b, r, 'm');
                    if (f.isEmpty()) b.input("nueva", "NOMBRE DE UNA MISIÓN NUEVA", 40, "AÑADIR");
                }
                case "guerra" -> {
                    b.header("Pulsa un objetivo para cambiarlo; SÍ/NO: si sale o no.");
                    boolean pvp = TFJson.bool(sect(r.deepCopy(), "guerra.pvp"), "activa", true);
                    b.row(PadAdminUi.toggle(new ItemStack(Items.IRON_SWORD), "Objetivo PvP en cada guerra", "Derrotar a los del clan rival; los demás objetivos no son de matar.",
                            pvp, "Sí: uno de los objetivos es PvP (opcional: da puntos como los demás).", "No: solo objetivos de trabajo.", "pvp"));
                    for (Num n : WAR) b.row(PadAdminUi.value(new ItemStack(n.icon), n.title, n.help, shown(n, r), "f:" + n.id, f.equals(n.id)));
                    missionRows(b, r, 'w');
                    if (f.isEmpty()) b.input("nueva", "NOMBRE DE UN OBJETIVO NUEVO", 40, "AÑADIR");
                }
                case "tienda" -> {
                    b.header("Pulsa una oferta para cambiarla; SÍ/NO: a la venta o no.");
                    JsonArray a = list(r.deepCopy(), "tienda");
                    int on = 0;
                    for (JsonElement e : a) if (e.isJsonObject() && TFJson.bool(e.getAsJsonObject(), "activa", true)) on++;
                    b.row(PadAdminUi.section("A la venta: " + on + " de " + a.size()));
                    for (JsonElement e : a) {
                        if (!e.isJsonObject()) continue;
                        JsonObject o = e.getAsJsonObject();
                        String id = TFJson.str(o, "id", ""), type = TFJson.str(o, "tipo", "objeto");
                        boolean active = TFJson.bool(o, "activa", true);
                        long max = TFJson.num(o, "maximo", 0);
                        b.row(new PadView.Row(PadClanShop.icon(type, o), PadClanShop.fill(TFJson.str(o, "nombre", id), type, o), active ? TEXT : MUTED,
                                List.of(PadClanShop.fill(TFJson.str(o, "descripcion", ""), type, o),
                                        typeName(type) + " · " + (max > 0 ? "hasta " + max + " por clan" : "sin tope")),
                                -1, PadView.clanCoins(TFJson.num(o, "precio", 0)),
                                PadView.Btn.of(active ? "SÍ" : "NO", "act:t:" + id, active ? PadView.GREEN : PadView.RED),
                                PadView.Btn.of("EDITAR", "tab:t:" + id, PadView.BLUE)).clickable("tab:t:" + id));
                    }
                    b.row(PadAdminUi.button(player.getMainHandItem().isEmpty() ? new ItemStack(Items.CHEST) : player.getMainHandItem().copy(),
                            "Vender lo que llevas en la mano", "Lo que tienes en la mano (con sus encantamientos y su nombre) pasa a la tienda del clan.",
                            "", PadView.Btn.of("AÑADIR", "mano", PadView.GOLD)));
                    b.input("nueva", "NOMBRE DE UNA OFERTA NUEVA", 40, "AÑADIR");
                }
                default -> {
                    b.header("Todo lo de los clanes (clanes.json). Pulsa uno para cambiarlo.");
                    PadConfig.Setting s = PadConfig.setting(PRICE);
                    if (s != null) {
                        b.row(PadAdminUi.value(new ItemStack(Items.GOLD_INGOT), s.label(), s.help(), PadView.moneyExact(PadConfig.get(PRICE)), "f:" + PRICE,
                                f.equals(PRICE)));
                    }
                    for (Num n : GENERAL) b.row(PadAdminUi.value(new ItemStack(n.icon), n.title, n.help, shown(n, r), "f:" + n.id, f.equals(n.id)));
                }
            }
            if (!f.isEmpty()) {
                Num n = num(f);
                b.input("v", n == null ? "MONEDAS (0 = GRATIS)" : n.kind == DEC ? "UN NÚMERO (1,5)" : n.kind == PCT ? "TANTO POR CIENTO (0 A 100)" : "UN NÚMERO", 14,
                        "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            return b.build();
        }

        private void missionRows(PadView.Builder b, JsonObject r, char kind) {
            JsonArray a = list(r.deepCopy(), which(kind));
            int on = 0;
            for (JsonElement e : a) if (e.isJsonObject() && TFJson.bool(e.getAsJsonObject(), "activa", true)) on++;
            b.row(PadAdminUi.section((kind == 'm' ? "Las misiones" : "Los objetivos") + " (" + on + " de " + a.size() + " salen)"));
            for (JsonElement e : a) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String id = TFJson.str(o, "id", "");
                boolean active = TFJson.bool(o, "activa", true);
                List<String> lines = new ArrayList<>();
                lines.add(TFJson.str(o, "tipo", "?") + " " + TFJson.str(o, "objetivo", "?") + ": " + PadView.number(TFJson.num(o, "cantidad", 0)) + " (para 5)");
                if (kind == 'm') {
                    lines.add("+" + TFJson.num(o, "monedas", 0) + " Clan Coins · +" + TFJson.num(o, "xp", 0) + " XP · +" + TFJson.num(o, "prestigio", 0) + " de prestigio");
                }
                b.row(new PadView.Row(icon(o), TFJson.str(o, "nombre", id), active ? TEXT : MUTED, lines, -1, "",
                        PadView.Btn.of(active ? "SÍ" : "NO", "act:" + kind + ":" + id, active ? PadView.GREEN : PadView.RED),
                        PadView.Btn.of("EDITAR", "tab:" + kind + ":" + id, PadView.BLUE)).clickable("tab:" + kind + ":" + id));
            }
        }

        /** Una misión o un objetivo de guerra. */
        private PadView mission(ServerPlayer player, String tab, JsonObject o) {
            boolean m = tab.charAt(0) == 'm';
            String id = tab.substring(2);
            String f = PadAdminUi.field(player, APP_ID, tab);
            boolean active = TFJson.bool(o, "activa", true);
            String type = TFJson.str(o, "tipo", "romper"), target = TFJson.str(o, "objetivo", "*");
            PadView.Builder b = PadView.of(APP_ID).selected(tab).compact();
            b.hero(new PadView.Row(icon(o), TFJson.str(o, "nombre", id), active ? 0x1E7C2C : MUTED,
                    List.of(TFJson.str(o, "descripcion", ""), (active ? "Sale" : "No sale") + (m ? " en las misiones de la semana." : " en las guerras nuevas.")),
                    -1, m ? PadView.clanCoins(TFJson.num(o, "monedas", 0)) : "", null, null));
            b.row(PadAdminUi.toggle(new ItemStack(Items.LEVER), m ? "Sale en las misiones" : "Sale en las guerras", "", active,
                    "Sí: puede tocarle a un clan.", "No: no sale (las que ya tenía un clan siguen).", "act:" + tab));
            b.row(PadAdminUi.value(new ItemStack(Items.NAME_TAG), "Nombre", "El que ven los del clan.", TFJson.str(o, "nombre", ""), "f:nombre",
                    f.equals("nombre")));
            b.row(PadAdminUi.value(new ItemStack(Items.WRITABLE_BOOK), "Descripción", "", "", "f:descripcion", f.equals("descripcion")));
            b.row(PadAdminUi.cycle(new ItemStack(Items.IRON_PICKAXE), "Tipo", "Lo que cuenta (como en los oficios).", type, "tipo:-1", "tipo:1"));
            b.row(PadAdminUi.value(new ItemStack(Items.TARGET), "Objetivo", "Un id (minecraft:zombie), una #etiqueta, hostil o * (todo). "
                    + (counts(type, target) ? "Cuenta." : "¡No cuenta para «" + type + "»!"), target, "f:objetivo", f.equals("objetivo")));
            b.row(PadAdminUi.value(new ItemStack(Items.ARROW), "Cantidad", "Para un clan de 5 (cambia con los miembros).",
                    PadView.number(TFJson.num(o, "cantidad", 1)), "f:cantidad", f.equals("cantidad")));
            if (m) {
                b.row(PadAdminUi.value(new ItemStack(Items.PRISMARINE_CRYSTALS), "Clan Coins", "Para el clan al cumplirla.",
                        PadView.number(TFJson.num(o, "monedas", 0)), "f:monedas", f.equals("monedas")));
                b.row(PadAdminUi.value(new ItemStack(Items.EXPERIENCE_BOTTLE), "XP del clan", "", PadView.number(TFJson.num(o, "xp", 0)), "f:xp",
                        f.equals("xp")));
                b.row(PadAdminUi.value(new ItemStack(Items.GOLDEN_HELMET), "Prestigio", "", PadView.number(TFJson.num(o, "prestigio", 0)), "f:prestigio",
                        f.equals("prestigio")));
            }
            b.row(PadAdminUi.button(icon(o), "Icono", "El objeto que sale en su fila.", "", PadView.Btn.of("CAMBIAR", "tab:pi:" + tab, PadView.BLUE)));
            boolean sure = PadServer.confirming(player, APP_ID + ".borrar:" + tab);
            b.row(PadAdminUi.button(new ItemStack(Items.BARRIER), "Borrar", "Mejor apágala con SÍ/NO si solo quieres que no salga.", "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar", PadView.RED)));
            b.footer(PadView.Btn.of("ATRÁS", "tab:" + which(tab.charAt(0)), PadView.BLUE));
            if (!f.isEmpty()) {
                b.input("v", switch (f) {
                    case "nombre" -> "NOMBRE";
                    case "descripcion" -> "DESCRIPCIÓN";
                    case "objetivo" -> "MINECRAFT:ZOMBIE, #MINECRAFT:LOGS, HOSTIL O *";
                    default -> "UN NÚMERO";
                }, 96, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            return b.build();
        }

        /** Una oferta de la tienda del clan. */
        private PadView offer(ServerPlayer player, String tab, JsonObject o) {
            String id = tab.substring(2);
            String f = PadAdminUi.field(player, APP_ID, tab);
            boolean active = TFJson.bool(o, "activa", true);
            String type = TFJson.str(o, "tipo", "objeto");
            long max = TFJson.num(o, "maximo", 0);
            PadView.Builder b = PadView.of(APP_ID).selected(tab).compact();
            b.hero(new PadView.Row(PadClanShop.icon(type, o), PadClanShop.fill(TFJson.str(o, "nombre", id), type, o), active ? 0x1E7C2C : MUTED,
                    List.of(PadClanShop.fill(TFJson.str(o, "descripcion", ""), type, o), "Así sale en la tienda del clan."), -1,
                    PadView.clanCoins(TFJson.num(o, "precio", 0)), null, null));
            b.row(PadAdminUi.toggle(new ItemStack(Items.LEVER), "A la venta", "", active, "Sí: sale en la tienda del clan.", "No: no sale.", "act:" + tab));
            b.row(PadAdminUi.value(new ItemStack(Items.PRISMARINE_CRYSTALS), "Precio", "En Fantastic Clan Coins (las del clan).",
                    PadView.number(TFJson.num(o, "precio", 0)), "f:precio", f.equals("precio")));
            b.row(PadAdminUi.value(new ItemStack(Items.HOPPER), "Cuántas por clan", "0 = sin tope.", max > 0 ? String.valueOf(max) : "Sin tope", "f:maximo",
                    f.equals("maximo")));
            b.row(PadAdminUi.value(new ItemStack(Items.NAME_TAG), "Nombre", "{cantidad}, {horas} y {x} se cambian por sus números.",
                    TFJson.str(o, "nombre", ""), "f:nombre", f.equals("nombre")));
            b.row(PadAdminUi.value(new ItemStack(Items.WRITABLE_BOOK), "Descripción", "", "", "f:descripcion", f.equals("descripcion")));
            b.row(PadAdminUi.cycle(new ItemStack(Items.COMPARATOR), "Tipo", "Lo que da al comprarla.", typeName(type), "otipo:-1", "otipo:1"));
            switch (type) {
                case "proteccion" -> b.row(PadAdminUi.cycle(PadClanShop.icon(type, o), "Piedra", "La protección que da (las «clan» son del clan entero).",
                        TFJson.str(o, "piedra", "?"), "piedra:-1", "piedra:1"));
                case "plazas" -> b.row(PadAdminUi.note("Cada compra suma las plazas de GENERAL › Plazas de cada compra ("
                        + PadClanConfig.num("miembros", "porCompra") + ")."));
                case "potenciador" -> b.row(PadAdminUi.value(new ItemStack(Items.CLOCK), "Horas", "Lo que dura (se suman si se compra otra vez).",
                        String.valueOf(PadClanShop.hours(o)), "f:horas", f.equals("horas")));
                case "escudo" -> b.row(PadAdminUi.value(new ItemStack(Items.CLOCK), "Horas", "Lo que nadie les puede declarar la guerra (se suman si compran otro).",
                        String.valueOf(PadClanShop.hours(o)), "f:horas", f.equals("horas")));
                case "verdes" -> b.row(PadAdminUi.value(new ItemStack(Items.EMERALD), "Verdes para cada miembro", "",
                        String.valueOf(Math.max(1, TFJson.num(o, "cantidad", 1))), "f:cantidad", f.equals("cantidad")));
                case "estandarte" -> b.row(PadAdminUi.value(new ItemStack(net.tierrasfantasticas.tfclient.clans.ClanBlocks.BANNER_ITEM.get()),
                        "Estandartes por compra", "Llevan la insignia y la etiqueta del clan (las de ahora).",
                        String.valueOf(Math.max(1, TFJson.num(o, "cantidad", 1))), "f:cantidad", f.equals("cantidad")));
                case "objeto" -> {
                    ItemStack s = PadClanShop.stored(o);
                    b.row(PadAdminUi.button(s, "El objeto", s.getCount() + " × " + net.tierrasfantasticas.tfclient.util.TFNames.es(s) + ". Cámbialo por lo que llevas en la mano.",
                            "", PadView.Btn.of("PONER EL DE LA MANO", "mano:" + id, PadView.GOLD)));
                }
                case "comando" -> {
                    JsonArray cmds = o.has("comandos") && o.get("comandos").isJsonArray() ? o.getAsJsonArray("comandos") : new JsonArray();
                    b.row(PadAdminUi.note("Se ejecutan desde la consola al comprarla. {jugador} = quien compra, {clan} = el id del clan, {etiqueta} = su etiqueta."));
                    for (int i = 0; i < cmds.size(); i++) {
                        b.row(PadAdminUi.button(new ItemStack(Items.COMMAND_BLOCK), "/" + cmds.get(i).getAsString().replaceFirst("^/", ""), "", "",
                                PadView.Btn.of("QUITAR", "cmdq:" + i, PadView.RED)));
                    }
                    b.row(PadAdminUi.value(new ItemStack(Items.COMMAND_BLOCK), "Añadir un comando", "", "", "f:comando", f.equals("comando")));
                }
                default -> {
                }
            }
            if (!type.equals("proteccion") && !type.equals("objeto") && !type.equals("estandarte")) {
                b.row(PadAdminUi.button(icon(o), "Icono", "El objeto que sale en su fila.", "", PadView.Btn.of("CAMBIAR", "tab:pi:" + tab, PadView.BLUE)));
            }
            boolean sure = PadServer.confirming(player, APP_ID + ".borrar:" + tab);
            b.row(PadAdminUi.button(new ItemStack(Items.BARRIER), "Borrar la oferta", "Lo que ya compraron los clanes se queda.", "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar", PadView.RED)));
            b.footer(PadView.Btn.of("ATRÁS", "tab:tienda", PadView.BLUE));
            if (!f.isEmpty()) {
                b.input("v", switch (f) {
                    case "nombre" -> "NOMBRE";
                    case "descripcion" -> "DESCRIPCIÓN";
                    case "comando" -> "COMANDO (SIN /), POR EJEMPLO: GIVE {JUGADOR} DIAMOND 1";
                    default -> "UN NÚMERO";
                }, 200, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            return b.build();
        }

        // -----------------------------------------------------------------------------------------------------------
        // Acciones
        // -----------------------------------------------------------------------------------------------------------

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            JsonObject r = PadClanConfig.root().deepCopy();
            if (tab.startsWith("pi:") && tab.length() > 5) {
                ItemStack chosen = PadItemPicker.action(player, APP_ID, action, text);
                if (chosen != null) {
                    String what = tab.substring(3);
                    JsonObject o = find(list(r, which(what.charAt(0))), what.substring(2));
                    if (chosen.isEmpty() || o == null) return null;
                    o.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(chosen.getItem())));
                    PadClanConfig.save(r);
                    TFPadNet.notice(player, "Icono: " + net.tierrasfantasticas.tfclient.util.TFNames.es(chosen) + ".");
                    return what;
                }
                return null;
            }
            boolean detail = tab.length() > 2 && tab.charAt(1) == ':' && "mwt".indexOf(tab.charAt(0)) >= 0;
            JsonArray a = detail ? list(r, which(tab.charAt(0))) : List.of("misiones", "guerra", "tienda").contains(tab) ? list(r, tab) : null;
            JsonObject o = detail ? find(a, tab.substring(2)) : null;
            String[] parts = action.split(":", 2);
            String arg = parts.length > 1 ? parts[1] : "";
            switch (parts[0]) {
                case "f" -> PadAdminUi.editField(player, APP_ID, tab, arg.equals(PadAdminUi.field(player, APP_ID, tab)) ? "" : arg);
                case "pvp" -> {
                    JsonObject p = sect(r, "guerra.pvp");
                    boolean on = !TFJson.bool(p, "activa", true);
                    p.addProperty("activa", on);
                    PadClanConfig.save(r);
                    TFPadNet.notice(player, on ? "Las guerras nuevas tendrán un objetivo PvP." : "Las guerras nuevas no tendrán PvP.");
                }
                case "act" -> {
                    // act:m:id (en la lista) o act:m:id desde su ficha
                    if (arg.length() < 3) return null;
                    JsonObject x = find(list(r, which(arg.charAt(0))), arg.substring(2));
                    if (x == null) return null;
                    boolean on = !TFJson.bool(x, "activa", true);
                    x.addProperty("activa", on);
                    PadClanConfig.save(r);
                    TFPadNet.notice(player, "«" + TFJson.str(x, "nombre", "") + "»: " + (on ? (arg.charAt(0) == 't' ? "a la venta." : "sale.")
                            : (arg.charAt(0) == 't' ? "ya no se vende." : "ya no sale.")));
                }
                case "v" -> {
                    return value(player, tab, r, o, text);
                }
                case "nueva" -> {
                    String name = text.trim();
                    if (a == null || detail || name.isEmpty()) return null;
                    JsonObject n = new JsonObject();
                    if (tab.equals("tienda")) {
                        n.addProperty("id", newId(a, name, "o_"));
                        n.addProperty("nombre", name);
                        n.addProperty("descripcion", "");
                        n.addProperty("icono", "minecraft:chest");
                        n.addProperty("precio", 10);
                        n.addProperty("maximo", 0);
                        n.addProperty("tipo", "verdes");
                        n.addProperty("cantidad", 1);
                        n.addProperty("activa", false);
                    } else {
                        n.addProperty("id", newId(a, name, tab.equals("misiones") ? "m_" : "w_"));
                        n.addProperty("nombre", name);
                        n.addProperty("descripcion", "");
                        n.addProperty("icono", "minecraft:oak_log");
                        n.addProperty("tipo", "romper");
                        n.addProperty("objetivo", "#minecraft:logs");
                        n.addProperty("cantidad", 2000);
                        if (tab.equals("misiones")) {
                            n.addProperty("monedas", 1);
                            n.addProperty("xp", 350);
                            n.addProperty("prestigio", 25);
                        }
                        n.addProperty("activa", false);
                    }
                    a.add(n);
                    PadClanConfig.save(r);
                    TFPadNet.notice(player, "Añadida (apagada): cámbiala y enciéndela con SÍ/NO.");
                    return (tab.equals("guerra") ? "w" : tab.substring(0, 1)) + ":" + TFJson.str(n, "id", "");
                }
                case "mano" -> {
                    ItemStack held = player.getMainHandItem();
                    if (held.isEmpty()) {
                        TFPadNet.notice(player, "No llevas nada en la mano.");
                        return null;
                    }
                    JsonObject target = o;
                    if (target == null) {
                        if (!tab.equals("tienda")) return null;
                        target = new JsonObject();
                        target.addProperty("id", newId(a, held.getHoverName().getString(), "o_"));
                        target.addProperty("nombre", net.tierrasfantasticas.tfclient.util.TFNames.es(held));
                        target.addProperty("descripcion", "Para quien la compra (el líder o un colíder).");
                        target.addProperty("precio", 10);
                        target.addProperty("maximo", 0);
                        target.addProperty("tipo", "objeto");
                        target.addProperty("activa", false);
                        a.add(target);
                    }
                    target.add("objeto", PadItemPicker.json(held));
                    target.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(held.getItem())));
                    target.addProperty("tipo", "objeto");
                    PadClanConfig.save(r);
                    TFPadNet.notice(player, held.getCount() + " × " + net.tierrasfantasticas.tfclient.util.TFNames.es(held) + " en la tienda del clan" + (o == null ? " (apagada: ponle precio y enciéndela)." : "."));
                    return "t:" + TFJson.str(target, "id", "");
                }
                case "tipo", "otipo", "piedra" -> {
                    if (o == null) return null;
                    int dir = arg.startsWith("-") ? -1 : 1;
                    if (parts[0].equals("piedra")) {
                        List<String> ids = new ArrayList<>();
                        for (ClaimTier t : ClaimTier.VALUES) ids.add(t.id);
                        o.addProperty("piedra", next(ids, TFJson.str(o, "piedra", ""), dir));
                    } else {
                        List<String> opts = parts[0].equals("tipo") ? TYPES : OFFER_TYPES;
                        String now = next(opts, TFJson.str(o, "tipo", opts.get(0)), dir);
                        o.addProperty("tipo", now);
                        if (now.equals("proteccion") && !o.has("piedra")) o.addProperty("piedra", "claimstone_clan_50x50");
                        if ((now.equals("potenciador") || now.equals("escudo")) && !o.has("horas")) o.addProperty("horas", 24);
                    }
                    PadClanConfig.save(r);
                }
                case "cmdq" -> {
                    if (o == null || !o.has("comandos") || !o.get("comandos").isJsonArray()) return null;
                    int i = PadAdminUi.parse(arg);
                    JsonArray cmds = o.getAsJsonArray("comandos");
                    if (i < 0 || i >= cmds.size()) return null;
                    cmds.remove(i);
                    PadClanConfig.save(r);
                }
                case "borrar" -> {
                    if (o == null || !PadServer.confirm(player, APP_ID + ".borrar:" + tab)) return null;
                    for (int i = 0; i < a.size(); i++) {
                        if (a.get(i) == o) {
                            a.remove(i);
                            break;
                        }
                    }
                    PadClanConfig.save(r);
                    TFPadNet.notice(player, "Borrada.");
                    return which(tab.charAt(0));
                }
                default -> {
                }
            }
            return null;
        }

        private static String next(List<String> opts, String now, int dir) {
            int i = opts.indexOf(now);
            return opts.get(Math.floorMod((i < 0 ? 0 : i) + dir, opts.size()));
        }

        /** Lo escrito en el campo de abajo. */
        private String value(ServerPlayer player, String tab, JsonObject r, JsonObject o, String text) {
            String f = PadAdminUi.field(player, APP_ID, tab);
            String v = text.trim();
            if (f.isEmpty() || v.isEmpty()) return null;
            if (f.equals(PRICE)) {
                PadConfig.Setting s = PadConfig.setting(PRICE);
                long n = PadAdminUi.number(player, v, s.min(), s.max());
                if (n < 0) return null;
                PadConfig.set(PRICE, n);
                PadAdminUi.stopEditing(player, APP_ID);
                TFPadNet.notice(player, "Fundar un clan: " + PadView.moneyExact(n) + ".");
                return null;
            }
            Num num = num(f);
            if (num != null) {
                JsonObject s = sect(r, num.section());
                if (num.kind == INT) {
                    long n = PadAdminUi.number(player, v, (long) num.min, (long) num.max);
                    if (n < 0) return null;
                    s.addProperty(num.key(), n);
                } else {
                    double d;
                    try {
                        d = Double.parseDouble(v.replace("%", "").replace("x", "").replace(',', '.').trim());
                    } catch (NumberFormatException e) {
                        TFPadNet.notice(player, "Escribe un número (por ejemplo " + (num.kind == PCT ? "50" : "1,5") + ").");
                        return null;
                    }
                    if (num.kind == PCT) d /= 100.0;
                    double lo = num.kind == PCT ? num.min / 100.0 : num.min, hi = num.kind == PCT ? num.max / 100.0 : num.max;
                    if (d < lo || d > hi || Double.isNaN(d)) {
                        TFPadNet.notice(player, "Tiene que estar entre " + (num.kind == PCT ? (long) num.min + " y " + (long) num.max + " %"
                                : PadClanConfig.decimal(num.min) + " y " + PadClanConfig.decimal(num.max)) + ".");
                        return null;
                    }
                    s.addProperty(num.key(), Math.round(d * 1000) / 1000.0);
                }
                PadClanConfig.save(r);
                PadAdminUi.stopEditing(player, APP_ID);
                TFPadNet.notice(player, "Guardado: " + num.title + " = " + shown(num, r) + ".");
                return null;
            }
            if (o == null) return null;
            switch (f) {
                case "nombre", "descripcion" -> o.addProperty(f, v);
                case "objetivo" -> {
                    String t = v.toLowerCase(Locale.ROOT);
                    String type = TFJson.str(o, "tipo", "romper");
                    if (!counts(type, t)) {
                        TFPadNet.notice(player, "«" + v + "» no cuenta para «" + type + "» (no existe o nada del juego lo cumple).");
                        return null;
                    }
                    o.addProperty("objetivo", t);
                }
                case "comando" -> {
                    JsonArray cmds = o.has("comandos") && o.get("comandos").isJsonArray() ? o.getAsJsonArray("comandos") : new JsonArray();
                    cmds.add(v.startsWith("/") ? v.substring(1) : v);
                    o.add("comandos", cmds);
                }
                default -> {
                    long[] range = switch (f) {
                        case "cantidad" -> new long[] {1, 100_000_000};
                        case "monedas", "maximo" -> new long[] {0, 1000};
                        case "horas" -> new long[] {1, 24 * 60};
                        default -> new long[] {0, 100_000_000};
                    };
                    long n = PadAdminUi.number(player, v, range[0], range[1]);
                    if (n < 0) return null;
                    o.addProperty(f, n);
                }
            }
            PadClanConfig.save(r);
            PadAdminUi.stopEditing(player, APP_ID);
            TFPadNet.notice(player, "Guardado.");
            return null;
        }
    }
}
