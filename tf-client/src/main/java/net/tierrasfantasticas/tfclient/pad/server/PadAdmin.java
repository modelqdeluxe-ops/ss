package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.market.TFMarket;
import net.tierrasfantasticas.tfclient.pad.PadCommunityServer;
import net.tierrasfantasticas.tfclient.pad.PadConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * El pad de administrador (solo staff, nivel 3): configura el pad de todos sin tocar archivos. Cada app guarda en su
 * config de config/tfclient/ y la recarga al momento. 1.3.66: todas se manejan igual que Kits, con las piezas de
 * {@link PadAdminUi}: interruptores que dicen qué pasa en cada caso, valores con su unidad («30 s», «800 bloques») y
 * CAMBIAR, que abre el campo de abajo con lo que se puede escribir (pulsarlo otra vez o CANCELAR lo cierra), fichas
 * grandes arriba y el selector de objetos ({@link PadItemPicker}) en vez de «lo que tengas en la mano». Cada vista dice
 * su pestaña (selected), así una acción nunca te devuelve a otra pantalla.
 * <ul>
 *   <li>APPS: las apps de los jugadores en tarjetas, con su icono de la portada; pulsar una la muestra u oculta.</li>
 *   <li>AJUSTES: los de pad.json por pestañas (VIAJES, EXPLORAR, JUGADORES, ECONOMÍA) y SISTEMA (recargar configs).</li>
 *   <li>TIENDA: la portada (abierta, VENDER TODO y las categorías); cada categoría con sus objetos en tarjetas (en oro
 *       lo que paga el jugador, en verde lo que le paga la tienda), AÑADIR OBJETO (al elegirlo se abre su ficha con el
 *       precio listo para escribir), su nombre, su icono y BORRAR; la ficha de un objeto con todos sus valores.</li>
 *   <li>VIAJES: PUNTOS (se ponen donde estás; su ficha: nombre, icono, MOVER AQUÍ, IR y BORRAR) y WARPS (cuáles se ven).</li>
 *   <li>GTS: lo que está a la venta, con RETIRAR. COMUNIDAD: fotos denunciadas y recientes. OFICIOS: sus ajustes.</li>
 * </ul>
 */
public final class PadAdmin {
    public static final PadServer.App APPS = new Apps();
    public static final PadServer.App SETTINGS = new Settings();
    public static final PadServer.App SHOP = new Shop();
    public static final PadServer.App KITS = PadAdminKits.APP; // el asistente de kits (PadAdminKits)
    public static final PadServer.App TRAVEL = new Travel();
    public static final PadServer.App MARKET = new Market();
    public static final PadServer.App COMMUNITY = new Community();
    public static final PadServer.App JOBS = new Jobs();

    private static final int TEXT = PadAdminUi.TEXT, MUTED = PadAdminUi.MUTED;

    private PadAdmin() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Ayudantes
    // ---------------------------------------------------------------------------------------------------------------

    private static ItemStack item(String id, Item fallback) {
        Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        return new ItemStack(i == null || i == Items.AIR ? fallback : i);
    }

    private static String idOf(ItemStack s) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem()));
    }

    /** Para refrescar el pad de todos cuando cambia algo que ven (apps, monedas…). */
    private static void broadcastState() {
        if (PadServer.server() == null) return;
        for (ServerPlayer p : PadServer.server().getPlayerList().getPlayers()) TFPadNet.sendState(p);
    }

    /** Segundos para leer: «45 s», «5 min», «1 h 30 min». */
    static String duration(long s) {
        if (s < 60) return s + " s";
        long h = s / 3600, m = s % 3600 / 60, sec = s % 60;
        List<String> parts = new ArrayList<>();
        if (h > 0) parts.add(h + " h");
        if (m > 0) parts.add(m + " min");
        if (sec > 0) parts.add(sec + " s");
        return String.join(" ", parts);
    }

    /** «1 objeto», «3 objetos». */
    private static String count(long n, String one, String many) {
        return PadView.number(n) + " " + (n == 1 ? one : many);
    }

    /** La pestaña de las que hay (por su clave) o la primera. */
    private static String tabOf(String tab, String... keys) {
        for (String k : keys) if (k.equals(tab)) return k;
        return keys[0];
    }

    // ---------------------------------------------------------------------------------------------------------------
    // APPS
    // ---------------------------------------------------------------------------------------------------------------

    /** Una app de los jugadores: su id, su nombre, el objeto (por si el cliente no tiene su icono) y su color. */
    private record AppInfo(String id, String name, Item icon, int color) {}

    private static final List<AppInfo> PLAYER_APPS = List.of(
            new AppInfo("musica", "Música", Items.MUSIC_DISC_CAT, 0x3496FA), new AppInfo("oficios", "Oficios", Items.IRON_PICKAXE, 0xE0892E),
            new AppInfo("misiones", "Misiones", Items.WRITABLE_BOOK, 0x22A884), new AppInfo("pase", "TF Pass", Items.NETHER_STAR, 0x2EAF5C),
            new AppInfo("gachapon", "Gachapón", Items.EMERALD, 0xE0489E), new AppInfo("cazas", "Cazas", Items.CROSSBOW, 0xC8553D),
            new AppInfo("recompensas", "Recompensas", Items.CHEST_MINECART, 0xE2466A), new AppInfo("tienda", "Tienda", Items.CHEST, 0xE6A019),
            new AppInfo("gts", "GTS", Items.EMERALD, 0x8B5CF0), new AppInfo("monedero", "Monedero", Items.GOLD_INGOT, 0xD49A12),
            new AppInfo("viajes", "Viajes", Items.FILLED_MAP, 0x1AA3C8), new AppInfo("hogares", "Hogares", Items.OAK_DOOR, 0xDB6440),
            new AppInfo("kits", "Kits", Items.BUNDLE, 0xB4783A), new AppInfo("protecciones", "Protección", Items.SHIELD, 0x3B7DDB),
            new AppInfo("clanes", "Clanes", Items.WHITE_BANNER, 0xD43A50), new AppInfo("jugadores", "Jugadores", Items.PLAYER_HEAD, 0x2E9BE6),
            new AppInfo("comunidad", "Comunidad", Items.PAINTING, 0x3B9BE0), new AppInfo("camara", "Cámara", Items.SPYGLASS, 0x3B9BE0),
            new AppInfo("ranking", "Ranking", Items.GOLD_BLOCK, 0xE3A52A), new AppInfo("armario", "Armario", Items.LEATHER_CHESTPLATE, 0x9E5AE0),
            new AppInfo("efectos", "Efectos", Items.BLAZE_POWDER, 0xE0489E), new AppInfo("rango", "Mi rango", Items.NETHER_STAR, 0xD9A21E),
            new AppInfo("web", "Web", Items.COMPASS, 0x3496FA), new AppInfo("guia", "Guía", Items.BOOK, 0x1E9E8C));

    private static final int OFF_COLOR = 0x8C96AA;

    private static final class Apps implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            int on = 0;
            for (AppInfo a : PLAYER_APPS) if (PadConfig.appOn(a.id)) on++;
            PadView.Builder b = PadView.of("a_apps").header(on + " de " + PLAYER_APPS.size() + " visibles. Pulsa una app para mostrarla u "
                    + "ocultarla en el pad de todos los jugadores (al momento).");
            for (AppInfo a : PLAYER_APPS) {
                boolean v = PadConfig.appOn(a.id);
                b.cell(new PadView.Cell(new ItemStack(a.icon), a.name, v ? a.color : OFF_COLOR, "app:" + a.id, false, v ? "VISIBLE" : "OCULTA",
                        -1, "", v ? PadView.TONE_GREEN : PadView.TONE_GRAY).image(PadView.padImage("icon_" + a.id)));
            }
            if (on < PLAYER_APPS.size()) b.footer(PadView.Btn.of("MOSTRAR TODAS", "todas", PadView.GREEN));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("app:")) {
                String id = action.substring(4);
                PadConfig.toggleApp(id);
                broadcastState();
                String name = id;
                for (AppInfo a : PLAYER_APPS) if (a.id.equals(id)) name = a.name;
                TFPadNet.notice(player, PadConfig.appOn(id) ? name + ": visible. Ya sale en el pad de los jugadores."
                        : name + ": oculta. Ya no sale en el pad de nadie (ni se puede abrir).");
            } else if (action.equals("todas")) {
                for (String id : PadConfig.disabledApps()) PadConfig.toggleApp(id);
                broadcastState();
                TFPadNet.notice(player, "Todas las apps visibles.");
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // AJUSTES (config/tfclient/pad.json)
    // ---------------------------------------------------------------------------------------------------------------

    private static final String SETTINGS_ID = "a_ajustes";

    /** Las pestañas de AJUSTES: clave, nombre, el grupo de PadConfig.Setting que enseña y su frase. */
    private static final String[][] SETTING_TABS = {
            {"", "VIAJES", "viajes", "Las esperas y los warps de la app Viajes."},
            {"explorar", "EXPLORAR", "explorar", "Explorar: lleva a un sitio seguro al azar del mundo."},
            {"jugadores", "JUGADORES", "jugadores", "Hogares sin rango y regalos entre jugadores."},
            {"economia", "ECONOMÍA", "economia", "Envíos del Monedero y el GTS (el mercado)."},
            {"sistema", "SISTEMA", "", "Todo se guarda al momento en config/tfclient/."}};

    /** El valor como se lee: «30 s», «Sin espera», «800 bloques», «¤1.000.000», «7 días». */
    private static String shown(PadConfig.Setting s, long v) {
        return switch (s.unit()) {
            case "s" -> v == 0 ? (s.key().endsWith("cuentaAtras") ? "Al momento" : "Sin espera") : duration(v);
            case "monedas" -> PadView.moneyExact(v);
            case "hogares" -> count(v, "hogar", "hogares");
            case "días" -> count(v, "día", "días");
            case "bloques" -> count(v, "bloque", "bloques");
            default -> PadView.number(v) + (s.unit().isEmpty() ? "" : " " + s.unit());
        };
    }

    /** Lo que pide el campo de abajo: «SEGUNDOS (0 A 3.600)». */
    private static String hint(PadConfig.Setting s) {
        String what = switch (s.unit()) {
            case "s" -> "SEGUNDOS";
            case "monedas" -> "MONEDAS";
            case "bloques" -> "BLOQUES";
            case "días" -> "DÍAS";
            case "hogares" -> "HOGARES";
            default -> "NÚMERO";
        };
        return what + " (" + PadView.number(s.min()) + " A " + PadView.number(s.max()) + ")";
    }

    private static ItemStack settingIcon(String key) {
        Item i = switch (key) {
            case "viajes.espera", "explorar.espera", "gts.dias" -> Items.CLOCK;
            case "viajes.cuentaAtras" -> Items.REPEATER;
            case "viajes.warps" -> Items.ENDER_PEARL;
            case "explorar.activado" -> Items.COMPASS;
            case "explorar.min" -> Items.MAP;
            case "explorar.max" -> Items.FILLED_MAP;
            case "hogares.sinRango" -> Items.OAK_DOOR;
            case "regalos.activado", "regalos.porDia" -> Items.CHEST;
            case "gts.maxPublicaciones" -> Items.EMERALD;
            default -> null;
        };
        return i == null ? PadKits.coinIcon(false) : new ItemStack(i);
    }

    /** Lo que pasa con un interruptor encendido y apagado. */
    private static String[] onOff(String key) {
        return switch (key) {
            case "viajes.warps" -> new String[] {"Salen en Viajes (menos los que ocultes en VIAJES › WARPS).",
                    "No salen: en Viajes solo están el spawn y los puntos."};
            case "explorar.activado" -> new String[] {"El botón sale en Viajes.", "No sale: nadie puede explorar desde el pad."};
            case "regalos.activado" -> new String[] {"Se pueden regalar objetos y monedas.", "Apagados: nadie puede regalar."};
            case "monedero.enviar" -> new String[] {"Se pueden mandar monedas desde el Monedero.", "Apagado: nadie puede mandar monedas."};
            default -> new String[] {"Encendido.", "Apagado."};
        };
    }

    private static String[] settingsTab(String tab) {
        for (String[] t : SETTING_TABS) if (t[0].equals(tab)) return t;
        return SETTING_TABS[0];
    }

    private static final class Settings implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            String[] t = settingsTab(tab);
            PadView.Builder b = PadView.of(SETTINGS_ID).selected(t[0]);
            for (String[] x : SETTING_TABS) b.tab(x[0], x[1]);
            b.header(t[3]);
            if (t[0].equals("sistema")) {
                b.row(PadAdminUi.button(new ItemStack(Items.COMMAND_BLOCK), "Recargar configs",
                        "Vuelve a leer todos los archivos de config/tfclient/, por si cambiaste alguno a mano (como /tf reload).", "",
                        PadView.Btn.of("RECARGAR", "recargar", PadView.GOLD)));
                b.row(PadAdminUi.note("Lo que cambias en el pad de administrador se guarda al momento y los jugadores lo ven sin reiniciar. "
                        + "pad.json guarda estos ajustes, las apps ocultas y los warps ocultos."));
                return b.build();
            }
            String f = PadAdminUi.field(player, SETTINGS_ID, t[0]);
            for (PadConfig.Setting s : PadConfig.SETTINGS) {
                if (!s.group().equals(t[2])) continue;
                long v = PadConfig.get(s.key());
                if (s.bool()) {
                    String[] oo = onOff(s.key());
                    b.row(PadAdminUi.toggle(settingIcon(s.key()), s.label(), s.help(), v != 0, oo[0], oo[1], "si:" + s.key()));
                } else {
                    b.row(PadAdminUi.value(settingIcon(s.key()), s.label(), s.help(), shown(s, v), "f:" + s.key(), f.equals(s.key())));
                }
            }
            PadConfig.Setting s = f.isEmpty() ? null : PadConfig.setting(f);
            if (s != null) {
                b.input("v", hint(s), 14, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] t = settingsTab(tab);
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "si" -> {
                    PadConfig.Setting s = PadConfig.setting(arg);
                    if (s == null) return null;
                    PadConfig.set(arg, PadConfig.on(arg) ? 0 : 1);
                    broadcastState();
                    TFPadNet.notice(player, s.label() + ": " + (PadConfig.on(arg) ? "SÍ" : "NO") + ".");
                }
                case "f" -> PadAdminUi.editField(player, SETTINGS_ID, t[0], arg.equals(PadAdminUi.field(player, SETTINGS_ID, t[0])) ? "" : arg);
                case "v" -> {
                    PadConfig.Setting s = PadConfig.setting(PadAdminUi.field(player, SETTINGS_ID, t[0]));
                    if (s == null) return null;
                    long v = PadAdminUi.number(player, text, s.min(), s.max());
                    if (v < 0) return null;
                    PadConfig.set(s.key(), v);
                    PadAdminUi.stopEditing(player, SETTINGS_ID);
                    TFPadNet.notice(player, "Guardado: " + s.label() + " = " + shown(s, v) + ".");
                }
                case "recargar" -> reloadAll(player);
                default -> {
                }
            }
            return null;
        }
    }

    /** Vuelve a leer todas las configs de config/tfclient/ (lo mismo que /tf reload: {@link TFReload}). */
    static void reloadAll(ServerPlayer player) {
        TFReload.Result r = TFReload.all();
        TFPadNet.notice(player, r.problems().isEmpty() ? "Configs recargadas."
                : r.problems().size() == 1 ? r.problems().get(0) : "Configs recargadas con " + r.problems().size() + " avisos: " + r.problems().get(0));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // TIENDA (config/tfclient/tienda.json)
    // ---------------------------------------------------------------------------------------------------------------

    private static final String SHOP_ID = "a_tienda";
    private static final int SHOP_GOLD = 0xE6A019, SHOP_GREEN = 0x40C850;

    private static JsonObject shopRoot() {
        JsonObject root = TFJson.read(TFShopConfig.file());
        if (root == null) root = new JsonObject();
        if (!root.has("categorias") || !root.get("categorias").isJsonArray()) root.add("categorias", new JsonArray());
        return root;
    }

    /** Cambia tienda.json con f y recarga la tienda. */
    private static void editShop(Consumer<JsonObject> f) {
        JsonObject root = shopRoot();
        f.accept(root);
        TFJson.write(TFShopConfig.file(), root);
        TFShopConfig.load();
    }

    private static JsonObject category(JsonObject root, String id) {
        for (JsonElement e : root.getAsJsonArray("categorias")) {
            if (!e.isJsonObject()) continue;
            JsonObject c = e.getAsJsonObject();
            if (TFJson.str(c, "id", "").equals(id)) return c;
        }
        return null;
    }

    private static JsonArray objects(JsonObject c) {
        if (!c.has("objetos") || !c.get("objetos").isJsonArray()) c.add("objetos", new JsonArray());
        return c.getAsJsonArray("objetos");
    }

    private static JsonObject entry(JsonObject root, String cat, int index) {
        JsonObject c = category(root, cat);
        if (c == null) return null;
        JsonArray a = objects(c);
        return index >= 0 && index < a.size() && a.get(index).isJsonObject() ? a.get(index).getAsJsonObject() : null;
    }

    /** El objeto de la tienda tal cual (con su NBT y su cantidad de verdad: 128, 1.024…). */
    private static ItemStack entryStack(JsonObject o) {
        ItemStack s = item(TFJson.str(o, "objeto", ""), Items.BARRIER);
        String nbt = TFJson.str(o, "nbt", "");
        if (!nbt.isBlank()) {
            try {
                s.setTag(net.minecraft.nbt.TagParser.parseTag(nbt));
            } catch (Exception ignored) {
                // sin NBT
            }
        }
        // el pad dibuja la cantidad aunque pase de lo que se apila
        s.setCount((int) Math.max(1, Math.min(6400, TFJson.num(o, "cantidad", 1))));
        return s;
    }

    private static String entryName(JsonObject o, ItemStack s) {
        String name = TFJson.str(o, "nombre", "");
        return name.isBlank() ? s.getHoverName().getString() : name;
    }

    /** Pone en o el objeto s (id, NBT y cantidad: las unidades por compra). */
    private static void setObject(JsonObject o, ItemStack s) {
        o.addProperty("objeto", idOf(s));
        if (s.hasTag() && !s.getTag().isEmpty()) o.addProperty("nbt", s.getTag().toString());
        else o.remove("nbt");
        o.addProperty("cantidad", s.getCount());
    }

    /** Los campos de la ficha de un objeto: clave en tienda.json, título, explicación, lo que pide el campo y su máximo. */
    private record ShopField(String key, String label, String help, String hint, long max) {}

    private static final List<ShopField> SHOP_FIELDS = List.of(
            new ShopField("comprar", "Precio de compra", "Lo que paga el jugador. 0 = no se puede comprar.",
                    "LO QUE PAGA EL JUGADOR (0 = NO SE COMPRA)", 1_000_000_000_000L),
            new ShopField("vender", "Precio de venta", "Lo que le paga la tienda. 0 = no lo compra.",
                    "LO QUE PAGA LA TIENDA (0 = NO LO COMPRA)", 1_000_000_000_000L),
            new ShopField("cantidad", "Unidades por compra", "Se compra y se vende de tantas en tantas.",
                    "UNIDADES POR COMPRA (1 A 6.400)", 6400),
            new ShopField("limiteCompraDiario", "Límite de compra al día", "Por jugador. 0 = sin límite.",
                    "UNIDADES AL DÍA (0 = SIN LÍMITE)", 1_000_000_000L),
            new ShopField("limiteVentaDiario", "Límite de venta al día", "Por jugador. 0 = sin límite.",
                    "UNIDADES AL DÍA (0 = SIN LÍMITE)", 1_000_000_000L),
            new ShopField("nombre", "Nombre en la tienda", "El que ven los jugadores. Vacío = el del objeto.", "NOMBRE (VACÍO = EL DEL OBJETO)", 0));

    private static ShopField shopField(String key) {
        for (ShopField f : SHOP_FIELDS) if (f.key.equals(key)) return f;
        return null;
    }

    private static final class Shop implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, SHOP_ID, rawTab);
            JsonObject root = shopRoot();
            String[] t = tab.split(":");
            JsonObject c = t.length > 1 ? category(root, t[1]) : null;
            if (c != null) {
                String cat = t[1];
                switch (t[0]) {
                    case "c" -> {
                        return categoryView(player, c, cat, tab);
                    }
                    case "cp" -> {
                        PadView.Builder b = PadItemPicker.builder(player, SHOP_ID, tab, "Añadir a " + TFJson.str(c, "nombre", "?")
                                + ": pulsa un objeto y luego ponle precio. El de tu inventario va tal cual (cantidad y encantamientos).", true, "");
                        b.footer(PadView.Btn.of("ATRÁS", "tab:c:" + cat, PadView.BLUE));
                        return b.build();
                    }
                    case "ci" -> {
                        PadView.Builder b = PadItemPicker.builder(player, SHOP_ID, tab, "Icono de " + TFJson.str(c, "nombre", "?")
                                + ": pulsa el objeto que la representará en la tienda.", false, TFJson.str(c, "icono", ""));
                        b.footer(PadView.Btn.of("ATRÁS", "tab:c:" + cat, PadView.BLUE));
                        return b.build();
                    }
                    case "i", "io" -> {
                        int idx = t.length > 2 ? PadAdminUi.parse(t[2]) : -1;
                        JsonObject o = entry(root, cat, idx);
                        if (o == null) return categoryView(player, c, cat, "c:" + cat);
                        if (t[0].equals("i")) return itemView(player, c, cat, idx, o, tab);
                        PadView.Builder b = PadItemPicker.builder(player, SHOP_ID, tab, "Cambiar " + entryName(o, entryStack(o))
                                + " por otro objeto: pulsa el nuevo (se quedan los precios y los límites).", true, TFJson.str(o, "objeto", ""));
                        b.footer(PadView.Btn.of("ATRÁS", "tab:i:" + cat + ":" + idx, PadView.BLUE));
                        return b.build();
                    }
                    default -> {
                    }
                }
            }
            return main(root);
        }

        private PadView main(JsonObject root) {
            JsonArray cats = root.getAsJsonArray("categorias");
            PadView.Builder b = PadView.of(SHOP_ID).selected("").header(cats.isEmpty()
                    ? "Empieza por una categoría: escribe su nombre abajo y pulsa CREAR."
                    : "Pulsa una categoría para ver sus objetos y precios. Para una nueva, escribe su nombre abajo y pulsa CREAR.");
            b.row(PadAdminUi.section("Categorías (" + cats.size() + ")"));
            for (JsonElement e : cats) {
                if (!e.isJsonObject()) continue;
                JsonObject c = e.getAsJsonObject();
                JsonArray objs = objects(c);
                int sells = 0;
                for (JsonElement o : objs) if (o.isJsonObject() && TFJson.num(o.getAsJsonObject(), "vender", 0) > 0) sells++;
                String id = TFJson.str(c, "id", "");
                b.row(new PadView.Row(item(TFJson.str(c, "icono", "minecraft:chest"), Items.CHEST), TFJson.str(c, "nombre", "?"), TEXT,
                        List.of(objs.isEmpty() ? "Vacía: entra y pulsa AÑADIR OBJETO." : count(objs.size(), "objeto", "objetos")
                                + " · la tienda compra " + sells), -1, "", PadView.Btn.of("EDITAR", "tab:c:" + id, PadView.BLUE), null)
                        .clickable("tab:c:" + id));
            }
            if (cats.isEmpty()) b.row(PadAdminUi.note("Aún no hay categorías (por ejemplo: Bloques, Minerales, Comida, Granjas)."));
            b.row(PadAdminUi.section("La tienda"));
            boolean open = TFJson.bool(root, "activada", true), sellAll = TFJson.bool(root, "botonVenderTodo", true);
            b.row(PadAdminUi.toggle(new ItemStack(Items.CHEST), "Tienda abierta", "", open, "Todos pueden comprar y vender.",
                    "Cerrada: solo el staff puede usarla.", "abierta"));
            b.row(PadAdminUi.toggle(new ItemStack(Items.HOPPER), "Botón VENDER TODO", "", sellAll,
                    "Sale en la tienda: vende de una vez todo lo que la tienda compra.", "No sale: se vende objeto a objeto.", "vendertodo"));
            b.input("nuevacat", "NOMBRE DE UNA CATEGORÍA NUEVA", 32, "CREAR");
            return b.build();
        }

        /** Una categoría: su ficha arriba, sus objetos en tarjetas y debajo su nombre, su icono y BORRAR. */
        private PadView categoryView(ServerPlayer player, JsonObject c, String cat, String tab) {
            String f = PadAdminUi.field(player, SHOP_ID, tab);
            JsonArray objs = objects(c);
            int buys = 0, sells = 0;
            for (JsonElement e : objs) {
                if (!e.isJsonObject()) continue;
                if (TFJson.num(e.getAsJsonObject(), "comprar", 0) > 0) buys++;
                if (TFJson.num(e.getAsJsonObject(), "vender", 0) > 0) sells++;
            }
            String name = TFJson.str(c, "nombre", "?");
            ItemStack icon = item(TFJson.str(c, "icono", "minecraft:chest"), Items.CHEST);
            PadView.Builder b = PadView.of(SHOP_ID).selected(tab);
            b.hero(new PadView.Row(icon, name, SHOP_GOLD, List.of(objs.isEmpty() ? "Vacía: pulsa AÑADIR OBJETO (abajo)."
                    : count(objs.size(), "objeto", "objetos") + " · se compran " + buys + " · la tienda compra " + sells,
                    "Oro: lo que paga el jugador. Verde: lo que le paga la tienda."), -1, "", null, null));
            for (int i = 0; i < objs.size(); i++) {
                if (!objs.get(i).isJsonObject()) continue;
                JsonObject o = objs.get(i).getAsJsonObject();
                ItemStack s = entryStack(o);
                long buy = TFJson.num(o, "comprar", 0), sell = TFJson.num(o, "vender", 0);
                String sub = buy > 0 ? PadView.money(buy) : sell > 0 ? "SOLO VENTA" : "SIN PRECIO";
                int tone = buy > 0 ? PadView.TONE_GOLD : sell > 0 ? PadView.TONE_BLUE : PadView.TONE_GRAY;
                b.cell(new PadView.Cell(s, entryName(o, s), sell > 0 ? SHOP_GREEN : buy > 0 ? 0xF6B628 : OFF_COLOR, "tab:i:" + cat + ":" + i,
                        false, sub, -1, sell > 0 ? "+" + PadView.money(sell) : "", tone));
            }
            b.row(PadAdminUi.section("La categoría"));
            b.row(PadAdminUi.value(new ItemStack(Items.NAME_TAG), "Nombre", "El que ven los jugadores en la tienda.", name, "f:nombre",
                    f.equals("nombre")));
            b.row(PadAdminUi.button(icon.copy(), "Icono", icon.getHoverName().getString() + ". El objeto que la representa.", "",
                    PadView.Btn.of("CAMBIAR", "tab:ci:" + cat, PadView.BLUE)));
            boolean sure = PadServer.confirming(player, SHOP_ID + ".borrar:" + cat);
            b.row(PadAdminUi.button(new ItemStack(Items.BARRIER), "Borrar la categoría",
                    objs.isEmpty() ? "Está vacía." : "Se borran también sus " + count(objs.size(), "objeto", "objetos") + ".", "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrarcat", PadView.RED)));
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            if (f.equals("nombre")) {
                b.input("v", "NOMBRE DE LA CATEGORÍA", 32, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            } else {
                b.footer(PadView.Btn.of("AÑADIR OBJETO", "tab:cp:" + cat, PadView.GREEN));
            }
            return b.build();
        }

        /** La ficha de un objeto: cómo lo ve el jugador arriba y cada valor con CAMBIAR. */
        private PadView itemView(ServerPlayer player, JsonObject c, String cat, int idx, JsonObject o, String tab) {
            String f = PadAdminUi.field(player, SHOP_ID, tab);
            ItemStack s = entryStack(o);
            long buy = TFJson.num(o, "comprar", 0), sell = TFJson.num(o, "vender", 0), qty = Math.max(1, TFJson.num(o, "cantidad", 1));
            String per = qty == 1 ? "" : " (por " + PadView.number(qty) + ")";
            List<String> lines = new ArrayList<>();
            lines.add(buy > 0 ? "El jugador lo compra por " + TFEconomy.format(buy) + per + "." : "No se puede comprar.");
            lines.add(sell > 0 ? "La tienda se lo compra por " + TFEconomy.format(sell) + per + "." : "La tienda no lo compra.");
            PadView.Builder b = PadView.of(SHOP_ID).selected(tab);
            b.hero(new PadView.Row(s.copy(), entryName(o, s), buy == 0 && sell == 0 ? OFF_COLOR : SHOP_GOLD, lines, -1,
                    buy == 0 && sell == 0 ? "SIN PRECIO" : "", null, null));
            for (ShopField fd : SHOP_FIELDS) {
                long v = TFJson.num(o, fd.key, fd.key.equals("cantidad") ? 1 : 0);
                String value;
                ItemStack icon;
                PadView.Btn second = null;
                switch (fd.key) {
                    case "comprar", "vender" -> {
                        value = v > 0 ? PadView.moneyExact(v) : fd.key.equals("comprar") ? "No se compra" : "No lo compra";
                        icon = fd.key.equals("comprar") ? PadKits.coinIcon(false) : new ItemStack(Items.HOPPER);
                        if (v > 0) second = PadView.Btn.of("QUITAR", "cero:" + fd.key, PadView.GRAY);
                    }
                    case "cantidad" -> {
                        value = "×" + PadView.number(Math.max(1, v));
                        icon = s.copy();
                    }
                    case "nombre" -> {
                        String n = TFJson.str(o, "nombre", "");
                        value = n.isBlank() ? "El del objeto" : n;
                        icon = new ItemStack(Items.NAME_TAG);
                        if (!n.isBlank()) second = PadView.Btn.of("QUITAR", "cero:nombre", PadView.GRAY);
                    }
                    default -> {
                        value = v > 0 ? PadView.number(v) + " al día" : "Sin límite";
                        icon = new ItemStack(Items.CLOCK);
                        if (v > 0) second = PadView.Btn.of("QUITAR", "cero:" + fd.key, PadView.GRAY);
                    }
                }
                b.row(PadAdminUi.value(icon, fd.label, fd.help, value, "f:" + fd.key, f.equals(fd.key), second));
            }
            b.row(PadAdminUi.button(s.copy(), "Objeto", s.getHoverName().getString() + ". Cámbialo por otro sin perder los precios.", "",
                    PadView.Btn.of("CAMBIAR", "tab:io:" + cat + ":" + idx, PadView.BLUE)));
            b.footer(PadView.Btn.of("ATRÁS", "tab:c:" + cat, PadView.BLUE));
            ShopField fd = shopField(f);
            if (fd != null) {
                b.input("v", fd.hint, fd.key.equals("nombre") ? 40 : 16, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            } else {
                boolean sure = PadServer.confirming(player, SHOP_ID + ".quitar:" + cat + ":" + idx);
                b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "QUITAR DE LA TIENDA", "quitar", PadView.RED));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, SHOP_ID, rawTab);
            String[] t = tab.split(":");
            String cat = t.length > 1 ? t[1] : "";
            int idx = t.length > 2 ? PadAdminUi.parse(t[2]) : -1;
            // los selectores: añadir un objeto, el icono de la categoría y cambiar el objeto de una ficha
            if (t[0].equals("cp") || t[0].equals("ci") || t[0].equals("io")) {
                ItemStack chosen = PadItemPicker.action(player, SHOP_ID, action, text);
                if (chosen != null) {
                    if (chosen.isEmpty()) return null;
                    String what = chosen.getCount() + " × " + chosen.getHoverName().getString();
                    switch (t[0]) {
                        case "cp" -> {
                            int[] index = {-1};
                            editShop(r -> {
                                JsonObject c = category(r, cat);
                                if (c == null) return;
                                JsonObject o = new JsonObject();
                                setObject(o, chosen);
                                o.addProperty("comprar", 0);
                                o.addProperty("vender", 0);
                                objects(c).add(o);
                                index[0] = objects(c).size() - 1;
                            });
                            if (index[0] < 0) return "";
                            String next = "i:" + cat + ":" + index[0];
                            PadAdminUi.editField(player, SHOP_ID, next, "comprar");
                            TFPadNet.notice(player, "Añadido: " + what + ". Escribe abajo lo que paga el jugador (0 si solo se vende).");
                            return next;
                        }
                        case "ci" -> {
                            editShop(r -> {
                                JsonObject c = category(r, cat);
                                if (c != null) c.addProperty("icono", idOf(chosen));
                            });
                            TFPadNet.notice(player, "Icono: " + chosen.getHoverName().getString() + ".");
                            return "c:" + cat;
                        }
                        default -> {
                            editShop(r -> {
                                JsonObject o = entry(r, cat, idx);
                                if (o != null) setObject(o, chosen);
                            });
                            TFPadNet.notice(player, "Ahora es " + what + ".");
                            return "i:" + cat + ":" + idx;
                        }
                    }
                }
            }
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "abierta" -> editShop(r -> r.addProperty("activada", !TFJson.bool(r, "activada", true)));
                case "vendertodo" -> editShop(r -> r.addProperty("botonVenderTodo", !TFJson.bool(r, "botonVenderTodo", true)));
                case "nuevacat" -> {
                    String name = text.trim();
                    if (name.isEmpty()) return null;
                    String base = PadAdminUi.slug(name, "categoria");
                    String[] made = {null};
                    editShop(r -> {
                        String cid = base;
                        for (int i = 2; category(r, cid) != null; i++) cid = base + "_" + i;
                        JsonObject c = new JsonObject();
                        c.addProperty("id", cid);
                        c.addProperty("nombre", name);
                        c.addProperty("icono", "minecraft:chest");
                        c.add("objetos", new JsonArray());
                        r.getAsJsonArray("categorias").add(c);
                        made[0] = cid;
                    });
                    TFPadNet.notice(player, "Categoría «" + name + "» creada. Añádele objetos.");
                    return "c:" + made[0];
                }
                case "f" -> PadAdminUi.editField(player, SHOP_ID, tab, arg.equals(PadAdminUi.field(player, SHOP_ID, tab)) ? "" : arg);
                case "v" -> {
                    String f = PadAdminUi.field(player, SHOP_ID, tab);
                    if (t[0].equals("c") && f.equals("nombre")) {
                        if (text.isBlank()) return null;
                        editShop(r -> {
                            JsonObject c = category(r, cat);
                            if (c != null) c.addProperty("nombre", text.trim());
                        });
                        PadAdminUi.stopEditing(player, SHOP_ID);
                        TFPadNet.notice(player, "Guardado: ahora se llama «" + text.trim() + "».");
                        return null;
                    }
                    ShopField fd = shopField(f);
                    if (!t[0].equals("i") || fd == null) return null;
                    if (fd.key.equals("nombre")) {
                        editShop(r -> {
                            JsonObject o = entry(r, cat, idx);
                            if (o == null) return;
                            if (text.isBlank()) o.remove("nombre");
                            else o.addProperty("nombre", text.trim());
                        });
                        PadAdminUi.stopEditing(player, SHOP_ID);
                        TFPadNet.notice(player, text.isBlank() ? "Guardado: se llama como el objeto." : "Guardado: «" + text.trim() + "».");
                        return null;
                    }
                    long v = PadAdminUi.number(player, text, fd.key.equals("cantidad") ? 1 : 0, fd.max);
                    if (v < 0) return null;
                    editShop(r -> {
                        JsonObject o = entry(r, cat, idx);
                        if (o != null) o.addProperty(fd.key, v);
                    });
                    // tras el precio de compra recién añadido, el de venta: el alta de un objeto queda en dos pasos
                    boolean fresh = fd.key.equals("comprar") && TFJson.num(entry(shopRoot(), cat, idx), "vender", 0) == 0;
                    if (fresh) PadAdminUi.editField(player, SHOP_ID, tab, "vender");
                    else PadAdminUi.stopEditing(player, SHOP_ID);
                    String said = switch (fd.key) {
                        case "comprar" -> v > 0 ? "Precio de compra: " + TFEconomy.format(v) + "." : "No se puede comprar.";
                        case "vender" -> v > 0 ? "La tienda lo compra por " + TFEconomy.format(v) + "." : "La tienda no lo compra.";
                        case "cantidad" -> "De " + PadView.number(v) + " en " + PadView.number(v) + ".";
                        default -> v > 0 ? "Límite: " + PadView.number(v) + " al día." : "Sin límite.";
                    };
                    TFPadNet.notice(player, "Guardado. " + said + (fresh ? " Ahora lo que paga la tienda (0 si no lo compra)." : ""));
                }
                case "cero" -> {
                    if (!t[0].equals("i") || shopField(arg) == null) return null;
                    editShop(r -> {
                        JsonObject o = entry(r, cat, idx);
                        if (o == null) return;
                        if (arg.equals("nombre")) o.remove("nombre");
                        else o.addProperty(arg, 0);
                    });
                    PadAdminUi.stopEditing(player, SHOP_ID);
                }
                case "borrarcat" -> {
                    if (!t[0].equals("c") || !PadServer.confirm(player, SHOP_ID + ".borrar:" + cat)) return null;
                    String[] gone = {cat};
                    editShop(r -> {
                        JsonArray keep = new JsonArray();
                        for (JsonElement e : r.getAsJsonArray("categorias")) {
                            if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(cat)) gone[0] = TFJson.str(e.getAsJsonObject(), "nombre", cat);
                            else keep.add(e);
                        }
                        r.add("categorias", keep);
                    });
                    TFPadNet.notice(player, "Categoría «" + gone[0] + "» borrada.");
                    return "";
                }
                case "quitar" -> {
                    if (!t[0].equals("i") || !PadServer.confirm(player, SHOP_ID + ".quitar:" + cat + ":" + idx)) return null;
                    editShop(r -> {
                        JsonObject c = category(r, cat);
                        if (c == null) return;
                        JsonArray arr = objects(c);
                        if (idx >= 0 && idx < arr.size()) arr.remove(idx);
                    });
                    TFPadNet.notice(player, "Quitado de la tienda.");
                    return "c:" + cat;
                }
                default -> {
                }
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // VIAJES
    // ---------------------------------------------------------------------------------------------------------------

    private static final String TRAVEL_ID = "a_viajes";

    private static String where(PadTravel.Point p) {
        return PadTravel.dimName(p.dim()) + " · " + (int) Math.floor(p.x()) + ", " + (int) Math.floor(p.y()) + ", " + (int) Math.floor(p.z());
    }

    private static final class Travel implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, TRAVEL_ID, rawTab);
            if (tab.startsWith("p:") || tab.startsWith("pi:")) {
                String id = tab.substring(tab.indexOf(':') + 1);
                PadTravel.Point p = PadTravel.point(id);
                if (p != null) {
                    if (tab.startsWith("p:")) return point(player, p, tab);
                    PadView.Builder b = PadItemPicker.builder(player, TRAVEL_ID, tab, "Icono de " + p.name()
                            + ": pulsa el objeto que se verá en la app Viajes.", false, p.icon());
                    b.footer(PadView.Btn.of("ATRÁS", "tab:p:" + id, PadView.BLUE));
                    return b.build();
                }
                tab = "";
            }
            boolean warpsTab = tab.equals("warps");
            PadView.Builder b = PadView.of(TRAVEL_ID).tab("", "PUNTOS").tab("warps", "WARPS").selected(warpsTab ? "warps" : "");
            if (warpsTab) {
                List<EssentialsWarps.Warp> warps = EssentialsWarps.list(player.getServer());
                boolean on = PadConfig.on("viajes.warps");
                b.header("Los /warp de EssentialsX que salen en Viajes (/setwarp).");
                b.row(PadAdminUi.toggle(new ItemStack(Items.ENDER_PEARL), "Warps en Viajes", "", on,
                        "Salen en Viajes los que estén en SÍ.", "Apagados: no sale ninguno.", "warps"));
                if (!warps.isEmpty()) b.row(PadAdminUi.section("Warps del servidor (" + warps.size() + ")"));
                for (EssentialsWarps.Warp w : warps) {
                    boolean shown = !PadConfig.hiddenWarps().contains(w.id());
                    b.row(PadAdminUi.toggle(new ItemStack(Items.ENDER_EYE), w.name(), "/warp " + w.id(), shown,
                            on ? "Se ve en Viajes." : "Se verá si enciendes los warps.", "Oculto: no sale en Viajes.", "warp:" + w.id()));
                }
                if (warps.isEmpty()) b.row(PadAdminUi.note("No hay warps de EssentialsX (o no está instalado). Crea uno con /setwarp <nombre>."));
                return b.build();
            }
            List<PadTravel.Point> points = PadTravel.points();
            b.header(points.isEmpty() ? "Ve al sitio, escribe su nombre abajo y pulsa AÑADIR AQUÍ."
                    : "Pulsa uno para cambiarlo. Nuevo: ve al sitio y AÑADIR AQUÍ.");
            for (PadTravel.Point p : points) {
                b.row(new PadView.Row(item(p.icon(), Items.FILLED_MAP), p.name(), TEXT, List.of(where(p)), -1, "",
                        PadView.Btn.of("EDITAR", "tab:p:" + p.id(), PadView.BLUE), PadView.Btn.of("IR", "ir:" + p.id(), PadView.GRAY))
                        .clickable("tab:p:" + p.id()));
            }
            b.empty("Aún no hay puntos. Ve al sitio, escribe abajo su nombre y pulsa AÑADIR AQUÍ: saldrá en la app Viajes de todos.");
            b.input("nuevo", "NOMBRE DEL PUNTO (SE PONE DONDE ESTÁS)", 32, "AÑADIR AQUÍ");
            return b.build();
        }

        /** La ficha de un punto: dónde está y cada cosa con su botón. */
        private PadView point(ServerPlayer player, PadTravel.Point p, String tab) {
            String f = PadAdminUi.field(player, TRAVEL_ID, tab);
            ItemStack icon = item(p.icon(), Items.FILLED_MAP);
            PadView.Builder b = PadView.of(TRAVEL_ID).selected(tab);
            b.hero(new PadView.Row(icon, p.name(), 0x1AA3C8, List.of(where(p), "Sale en la app Viajes de todos los jugadores."), -1, "",
                    PadView.Btn.of("IR", "ir:" + p.id(), PadView.BLUE), null));
            b.row(PadAdminUi.value(new ItemStack(Items.NAME_TAG), "Nombre", "El que ven los jugadores.", p.name(), "f:nombre", f.equals("nombre")));
            b.row(PadAdminUi.button(icon.copy(), "Icono", icon.getHoverName().getString() + ".", "",
                    PadView.Btn.of("CAMBIAR", "tab:pi:" + p.id(), PadView.BLUE)));
            b.row(PadAdminUi.button(new ItemStack(Items.COMPASS), "Posición", "Lo mueve a donde estás ahora, mirando hacia donde miras.", "",
                    PadView.Btn.of("MOVER AQUÍ", "mover", PadView.GOLD)));
            boolean sure = PadServer.confirming(player, TRAVEL_ID + ".borrar:" + p.id());
            b.row(PadAdminUi.button(new ItemStack(Items.BARRIER), "Borrar el punto", "Deja de salir en Viajes.", "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar", PadView.RED)));
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            if (f.equals("nombre")) {
                b.input("v", "NOMBRE DEL PUNTO", 32, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, TRAVEL_ID, rawTab);
            String pid = tab.startsWith("p:") || tab.startsWith("pi:") ? tab.substring(tab.indexOf(':') + 1) : "";
            if (tab.startsWith("pi:")) {
                ItemStack chosen = PadItemPicker.action(player, TRAVEL_ID, action, text);
                if (chosen != null) {
                    if (chosen.isEmpty()) return null;
                    PadTravel.editPoint(pid, o -> o.addProperty("icono", idOf(chosen)));
                    TFPadNet.notice(player, "Icono: " + chosen.getHoverName().getString() + ".");
                    return "p:" + pid;
                }
            }
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "nuevo" -> {
                    if (text.isBlank()) return null;
                    String base = PadAdminUi.slug(text.trim(), "punto");
                    String id = base;
                    for (int i = 2; PadTravel.point(id) != null; i++) id = base + "_" + i;
                    PadTravel.putPoint(player, id, text.trim());
                    TFPadNet.notice(player, "Punto «" + text.trim() + "» puesto aquí. Ya sale en Viajes.");
                    return "p:" + id;
                }
                case "f" -> PadAdminUi.editField(player, TRAVEL_ID, tab, arg.equals(PadAdminUi.field(player, TRAVEL_ID, tab)) ? "" : arg);
                case "v" -> {
                    if (pid.isEmpty() || text.isBlank()) return null;
                    PadTravel.editPoint(pid, o -> o.addProperty("nombre", text.trim()));
                    PadAdminUi.stopEditing(player, TRAVEL_ID);
                    TFPadNet.notice(player, "Guardado: «" + text.trim() + "».");
                }
                case "mover" -> {
                    PadTravel.Point p = PadTravel.point(pid);
                    if (p == null) return null;
                    PadTravel.putPoint(player, pid, p.name());
                    TFPadNet.notice(player, "«" + p.name() + "» movido aquí.");
                }
                case "borrar" -> {
                    if (!PadServer.confirm(player, TRAVEL_ID + ".borrar:" + pid)) return null;
                    PadTravel.Point p = PadTravel.point(pid);
                    PadTravel.removePoint(pid);
                    PadAdminUi.stopEditing(player, TRAVEL_ID);
                    TFPadNet.notice(player, "Punto «" + (p == null ? pid : p.name()) + "» borrado.");
                    return "";
                }
                case "ir" -> {
                    PadTravel.Point p = PadTravel.point(arg.isEmpty() ? pid : arg);
                    if (p == null) return null;
                    ServerLevel level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(p.dim())));
                    if (level == null) {
                        TFPadNet.notice(player, "Ese mundo no está cargado.");
                        return null;
                    }
                    TFPadNet.close(player);
                    player.teleportTo(level, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
                }
                case "warps" -> {
                    PadConfig.set("viajes.warps", PadConfig.on("viajes.warps") ? 0 : 1);
                    broadcastState();
                }
                case "warp" -> PadConfig.toggleWarp(arg);
                default -> {
                }
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // GTS
    // ---------------------------------------------------------------------------------------------------------------

    private static final class Market implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            List<TFMarket.Listing> all = TFMarket.listings();
            PadView.Builder b = PadView.of("a_gts").header(all.isEmpty() ? "El mercado entre jugadores está vacío."
                    : count(all.size(), "cosa", "cosas") + " a la venta. RETIRAR devuelve el objeto a su dueño (lo recoge en GTS › Recoger).");
            for (TFMarket.Listing l : all) {
                long left = l.expiresAt() - System.currentTimeMillis();
                long days = left / 86_400_000L, hours = left / 3_600_000L;
                String rest = left <= 0 ? "caduca ya" : days >= 1 ? "quedan " + count(days, "día", "días") : "quedan " + Math.max(1, hours) + " h";
                boolean sure = PadServer.confirming(player, "a_gts.retirar:" + l.id());
                b.row(new PadView.Row(l.item(), TFMarket.describe(l.item()), TEXT, List.of("De " + l.sellerName() + " · " + rest), -1,
                        PadView.moneyExact(l.price()), PadView.Btn.of(sure ? "¿SEGURO?" : "RETIRAR", "retirar:" + l.id(), PadView.RED), null));
            }
            b.empty("No hay nada a la venta. Lo que publiquen los jugadores sale aquí.");
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("retirar:")) {
                String id = action.substring(8);
                if (!PadServer.confirm(player, "a_gts.retirar:" + id)) return null;
                TFPadNet.notice(player, TFMarket.adminRemove(id) ? "Retirado: vuelve a su dueño." : "Ya no estaba a la venta.");
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // COMUNIDAD
    // ---------------------------------------------------------------------------------------------------------------

    private static final class Community implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            boolean reported = !tab.equals("todas");
            List<PadCommunityServer.PostInfo> flagged = PadCommunityServer.adminPosts(true, 60);
            List<PadCommunityServer.PostInfo> posts = reported ? flagged : PadCommunityServer.adminPosts(false, 60);
            PadView.Builder b = PadView.of("a_comunidad").tab("", flagged.isEmpty() ? "DENUNCIADAS" : "DENUNCIADAS (" + flagged.size() + ")")
                    .tab("todas", "RECIENTES").selected(reported ? "" : "todas");
            b.header(reported ? "Fotos denunciadas: ESTÁ BIEN las deja; BORRAR las quita."
                    : "Las últimas fotos publicadas. BORRAR la quita para todos.");
            for (PadCommunityServer.PostInfo p : posts) {
                boolean sure = PadServer.confirming(player, "a_comunidad.borrar:" + p.id());
                List<String> lines = new ArrayList<>();
                if (!p.caption().isEmpty()) lines.add("«" + p.caption() + "»");
                lines.add(count(p.likes(), "like", "likes") + " · " + count(p.reports(), "denuncia", "denuncias"));
                b.row(new PadView.Row(new ItemStack(Items.PAINTING), p.name(), p.reports() > 0 ? 0xC8323C : TEXT, lines, -1, "",
                        PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar:" + p.id(), PadView.RED),
                        p.reports() > 0 ? PadView.Btn.of("ESTÁ BIEN", "ok:" + p.id(), PadView.GREEN) : null));
            }
            b.empty(reported ? "No hay fotos denunciadas. Todo en orden." : "Aún no hay fotos.");
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            if (a.length < 2) return null;
            if (a[0].equals("borrar")) {
                if (!PadServer.confirm(player, "a_comunidad.borrar:" + a[1])) return null;
                PadCommunityServer.adminDelete(a[1]);
                TFPadNet.notice(player, "Foto borrada.");
            } else if (a[0].equals("ok")) {
                PadCommunityServer.adminClearReports(a[1]);
                TFPadNet.notice(player, "Denuncias quitadas: la foto se queda.");
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // OFICIOS (ajustes generales de config/tfclient/oficios.json)
    // ---------------------------------------------------------------------------------------------------------------

    private static final String JOBS_ID = "a_oficios";

    /** Un ajuste de oficios.json: clave, título, explicación, si es sí/no (con lo que pasa en cada caso), límites y unidad. */
    private record JobField(String key, String label, String help, boolean bool, String onText, String offText, long min, long max,
                            String unit, Item icon) {}

    private static final List<JobField> JOB_FIELDS = List.of(
            new JobField("activado", "Oficios", "", true, "Encendidos: se gana dinero trabajando.", "Apagados: nadie gana con ellos.", 0, 1, "",
                    Items.IRON_PICKAXE),
            new JobField("maxOficios", "Oficios a la vez", "Cuántos puede tener cada jugador.", false, "", "", 1, 10, "", Items.BOOK),
            new JobField("esperaCambioMinutos", "Espera para cambiar", "Tras unirse a uno, para poder dejarlo o cambiarlo.", false, "", "", 0, 1440,
                    "min", Items.CLOCK),
            new JobField("pagoCadaSegundos", "Pago cada", "Cada cuánto se cobra lo ganado.", false, "", "", 1, 600, "s", Items.GOLD_NUGGET),
            new JobField("bloquesColocadosNoCuentan", "Bloques colocados no cuentan", "", true, "Romper lo que uno mismo puso no paga (evita trampas).",
                    "Ojo: se puede poner y romper para ganar.", 0, 1, "", Items.STONE),
            new JobField("generadoresCuentan", "Mobs de generador cuentan", "", true, "Pagan aunque salgan de un generador (granjas).",
                    "Los de generador no pagan (recomendado).", 0, 1, "", Items.SPAWNER));

    private static JobField jobField(String key) {
        for (JobField f : JOB_FIELDS) if (f.key.equals(key)) return f;
        return null;
    }

    private static boolean jobOn(JsonObject root, String key) {
        return TFJson.bool(root, key, !key.equals("generadoresCuentan"));
    }

    private static String jobValue(JobField f, long v) {
        return switch (f.unit) {
            case "min" -> v == 0 ? "Sin espera" : duration(v * 60);
            case "s" -> duration(v);
            default -> PadView.number(v);
        };
    }

    /** Cambia oficios.json con f y lo recarga (si no se puede leer, no se toca: se perderían los oficios). */
    private static void editJobs(ServerPlayer player, Consumer<JsonObject> f) {
        JsonObject root = TFJson.read(TFJobsConfig.file());
        if (root == null) {
            TFPadNet.notice(player, "No se pudo leer config/tfclient/oficios.json: arréglalo y pulsa RECARGAR OFICIOS.");
            return;
        }
        f.accept(root);
        TFJson.write(TFJobsConfig.file(), root);
        TFJobsConfig.load();
    }

    private static final class Jobs implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            JsonObject root = TFJson.read(TFJobsConfig.file());
            if (root == null) root = new JsonObject();
            String f = PadAdminUi.field(player, JOBS_ID, "");
            PadView.Builder b = PadView.of(JOBS_ID).header("Ajustes de los oficios. Lo que paga cada uno, sus misiones y niveles: en "
                    + "config/tfclient/oficios.json (y RECARGAR).");
            b.row(PadAdminUi.section("General"));
            for (JobField fd : JOB_FIELDS) {
                if (fd.bool) {
                    b.row(PadAdminUi.toggle(new ItemStack(fd.icon), fd.label, fd.help, jobOn(root, fd.key), fd.onText, fd.offText, "si:" + fd.key));
                } else {
                    b.row(PadAdminUi.value(new ItemStack(fd.icon), fd.label, fd.help, jobValue(fd, TFJson.num(root, fd.key, 0)), "f:" + fd.key,
                            f.equals(fd.key)));
                }
            }
            b.row(PadAdminUi.section("Oficios (" + TFJobsConfig.jobs.size() + ")"));
            for (TFJobsConfig.Job job : TFJobsConfig.jobs.values()) {
                b.row(new PadView.Row(new ItemStack(net.tierrasfantasticas.tfclient.jobs.TFJobsMenu.icon(job)), job.name(), job.color(),
                        List.of(count(job.actions().size(), "forma de ganar", "formas de ganar") + " · "
                                + count(job.missions().size(), "misión", "misiones")), -1, "", null, null));
            }
            JobField fd = jobField(f);
            if (fd != null && !fd.bool) {
                String what = fd.unit.equals("min") ? "MINUTOS" : fd.unit.equals("s") ? "SEGUNDOS" : "NÚMERO";
                b.input("v", what + " (" + fd.min + " A " + PadView.number(fd.max) + ")", 10, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            } else {
                b.footer(PadView.Btn.of("RECARGAR OFICIOS", "recargar", PadView.GOLD));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "si" -> {
                    JobField fd = jobField(arg);
                    if (fd == null || !fd.bool) return null;
                    editJobs(player, r -> r.addProperty(arg, !jobOn(r, arg)));
                }
                case "f" -> PadAdminUi.editField(player, JOBS_ID, "", arg.equals(PadAdminUi.field(player, JOBS_ID, "")) ? "" : arg);
                case "v" -> {
                    JobField fd = jobField(PadAdminUi.field(player, JOBS_ID, ""));
                    if (fd == null || fd.bool) return null;
                    long v = PadAdminUi.number(player, text, fd.min, fd.max);
                    if (v < 0) return null;
                    editJobs(player, r -> r.addProperty(fd.key, v));
                    PadAdminUi.stopEditing(player, JOBS_ID);
                    TFPadNet.notice(player, "Guardado: " + fd.label + " = " + jobValue(fd, v) + ".");
                }
                case "recargar" -> {
                    List<String> w = TFJobsConfig.load();
                    TFPadNet.notice(player, "Oficios recargados" + (w.isEmpty() ? "." : " (" + w.size() + " avisos en la consola)."));
                }
                default -> {
                }
            }
            return null;
        }
    }
}
