package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
 * config de config/tfclient/ y la recarga al momento.
 * <ul>
 *   <li>APPS: qué apps salen en el pad de los jugadores.</li>
 *   <li>AJUSTES: los de pad.json (esperas de viajes, explorar, hogares sin rango, envíos, regalos, GTS) y RECARGAR.</li>
 *   <li>TIENDA: categorías y objetos (desde tu mano): precio de compra y de venta por lote, cantidad, límites diarios.
 *       Lo que tiene precio de venta es la lista de lo que la tienda compra.</li>
 *   <li>KITS: se crean con lo que llevas en el inventario; tipo (una vez, diario, semanal, cada X horas), monedas e
 *       icono.</li>
 *   <li>VIAJES: puntos (se ponen donde estás) y qué warps de EssentialsX se ven.</li>
 *   <li>GTS: lo que está a la venta, con RETIRAR. COMUNIDAD: fotos denunciadas y recientes. OFICIOS: ajustes.</li>
 * </ul>
 * Para escribir un valor: EDITAR en su fila y el campo de abajo (sesión «&lt;app&gt;.edit»).
 */
public final class PadAdmin {
    public static final PadServer.App APPS = new Apps();
    public static final PadServer.App SETTINGS = new Settings();
    public static final PadServer.App SHOP = new Shop();
    public static final PadServer.App KITS = new Kits();
    public static final PadServer.App TRAVEL = new Travel();
    public static final PadServer.App MARKET = new Market();
    public static final PadServer.App COMMUNITY = new Community();
    public static final PadServer.App JOBS = new Jobs();

    private static final int TEXT = 0x18265C, MUTED = 0x4A6694, GOLD = 0xC27A10;

    private PadAdmin() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Ayudantes
    // ---------------------------------------------------------------------------------------------------------------

    private static PadView.Btn toggle(boolean on, String action) {
        return PadView.Btn.of(on ? "SÍ" : "NO", action, on ? PadView.GREEN : PadView.GRAY);
    }

    private static PadView.Btn editBtn(String field) {
        return PadView.Btn.of("EDITAR", "editar:" + field, PadView.BLUE);
    }

    private static PadView.Row setting(ItemStack icon, String title, String value, PadView.Btn btn, boolean editing) {
        return new PadView.Row(icon, title, TEXT, List.of(value), -1, "", btn, null).selected(editing);
    }

    private static String editing(ServerPlayer p, String app) {
        return PadServer.get(p, app + ".edit", "");
    }

    private static void startEdit(ServerPlayer p, String app, String field) {
        PadServer.put(p, app + ".edit", field);
    }

    private static void stopEdit(ServerPlayer p, String app) {
        PadServer.put(p, app + ".edit", null);
    }

    /** Un número escrito (acepta 1500, 1.500, 5k, 2m). -1 si no vale. */
    private static long number(String text) {
        String t = text.trim();
        if (t.equals("0")) return 0;
        return TFMarket.parsePrice(t);
    }

    private static ItemStack item(String id, Item fallback) {
        Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        return new ItemStack(i == null || i == Items.AIR ? fallback : i);
    }

    private static String idOf(ItemStack s) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem()));
    }

    /** Lo que el admin tiene en la mano (o avisa si no tiene nada). */
    private static ItemStack hand(ServerPlayer p) {
        ItemStack h = p.getMainHandItem();
        if (h.isEmpty()) TFPadNet.notice(p, "Ponte en la mano el objeto que quieres usar.");
        return h;
    }

    /** Para refrescar el pad de todos cuando cambia algo que ven (apps, monedas…). */
    private static void broadcastState() {
        if (PadServer.server() == null) return;
        for (ServerPlayer p : PadServer.server().getPlayerList().getPlayers()) TFPadNet.sendState(p);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // APPS
    // ---------------------------------------------------------------------------------------------------------------

    private record AppInfo(String id, String name, Item icon) {}

    private static final List<AppInfo> PLAYER_APPS = List.of(
            new AppInfo("musica", "Música", Items.MUSIC_DISC_CAT), new AppInfo("oficios", "Oficios", Items.IRON_PICKAXE), new AppInfo("misiones", "Misiones", Items.WRITABLE_BOOK),
            new AppInfo("cazas", "Cazas", Items.CROSSBOW), new AppInfo("recompensas", "Recompensas", Items.CHEST_MINECART),
            new AppInfo("tienda", "Tienda", Items.CHEST),
            new AppInfo("gts", "GTS", Items.EMERALD), new AppInfo("monedero", "Monedero", Items.GOLD_INGOT),
            new AppInfo("viajes", "Viajes", Items.FILLED_MAP), new AppInfo("hogares", "Hogares", Items.OAK_DOOR),
            new AppInfo("kits", "Kits", Items.BUNDLE), new AppInfo("protecciones", "Protección", Items.SHIELD),
            new AppInfo("clanes", "Clanes", Items.WHITE_BANNER), new AppInfo("jugadores", "Jugadores", Items.PLAYER_HEAD),
            new AppInfo("comunidad", "Comunidad", Items.PAINTING), new AppInfo("camara", "Cámara", Items.SPYGLASS),
            new AppInfo("ranking", "Ranking", Items.GOLD_BLOCK), new AppInfo("armario", "Armario", Items.LEATHER_CHESTPLATE),
            new AppInfo("efectos", "Efectos", Items.BLAZE_POWDER), new AppInfo("rango", "Mi rango", Items.NETHER_STAR),
            new AppInfo("web", "Web", Items.COMPASS));

    private static final class Apps implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            PadView.Builder b = PadView.of("a_apps").header("Las que apagues no salen en el pad de los jugadores.");
            for (AppInfo a : PLAYER_APPS) {
                boolean on = PadConfig.appOn(a.id);
                b.row(new PadView.Row(new ItemStack(a.icon), a.name, on ? TEXT : MUTED, List.of(on ? "Activa" : "Apagada"), -1, "",
                        toggle(on, "app:" + a.id), null));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("app:")) {
                PadConfig.toggleApp(action.substring(4));
                broadcastState();
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // AJUSTES
    // ---------------------------------------------------------------------------------------------------------------

    private static final class Settings implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            String edit = editing(player, "a_ajustes");
            PadView.Builder b = PadView.of("a_ajustes").header("Se guardan en config/tfclient/pad.json al momento.");
            for (PadConfig.Setting s : PadConfig.SETTINGS) {
                long v = PadConfig.get(s.key());
                if (s.bool()) {
                    b.row(setting(new ItemStack(v != 0 ? Items.LIME_DYE : Items.GRAY_DYE), s.label(), s.help(), toggle(v != 0, "si:" + s.key()), false));
                } else {
                    b.row(new PadView.Row(new ItemStack(Items.COMPARATOR), s.label(), TEXT, List.of(s.help()), -1, String.valueOf(v),
                            editBtn(s.key()), null).selected(edit.equals(s.key())));
                }
            }
            if (!edit.isEmpty()) {
                PadConfig.Setting s = PadConfig.setting(edit);
                if (s != null) b.input("valor", "NUEVO VALOR (" + s.min() + " A " + s.max() + ")", 14, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "cancelar", PadView.GRAY));
            } else {
                b.footer(PadView.Btn.of("RECARGAR CONFIGS", "recargar", PadView.GOLD));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("si:")) {
                String key = action.substring(3);
                PadConfig.set(key, PadConfig.on(key) ? 0 : 1);
                broadcastState();
            } else if (action.startsWith("editar:")) {
                startEdit(player, "a_ajustes", action.substring(7));
            } else if (action.equals("cancelar")) {
                stopEdit(player, "a_ajustes");
            } else if (action.equals("valor")) {
                String key = editing(player, "a_ajustes");
                long v = number(text);
                if (v < 0) {
                    TFPadNet.notice(player, "Escribe un número.");
                    return null;
                }
                PadConfig.set(key, v);
                stopEdit(player, "a_ajustes");
                TFPadNet.notice(player, "Guardado.");
            } else if (action.equals("recargar")) {
                reloadAll(player);
            }
            return null;
        }
    }

    /** Vuelve a leer todas las configs de config/tfclient/ (pad, tienda, kits, oficios, misiones y ayuda). */
    static void reloadAll(ServerPlayer player) {
        PadConfig.load();
        List<String> shop = TFShopConfig.load();
        PadKits.loadConfig();
        PadRewards.loadConfig();
        List<String> jobs = TFJobsConfig.load();
        PadMissions.loadConfig();
        PadHelp.loadConfig();
        broadcastState();
        int warnings = shop.size() + jobs.size();
        TFPadNet.notice(player, "Configs recargadas" + (warnings > 0 ? " (" + warnings + " avisos en la consola)." : "."));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // TIENDA (config/tfclient/tienda.json)
    // ---------------------------------------------------------------------------------------------------------------

    /** Cambia tienda.json con f y recarga la tienda. */
    private static void editShop(Consumer<JsonObject> f) {
        JsonObject root = TFJson.read(TFShopConfig.file());
        if (root == null) root = new JsonObject();
        if (!root.has("categorias") || !root.get("categorias").isJsonArray()) root.add("categorias", new JsonArray());
        f.accept(root);
        TFJson.write(TFShopConfig.file(), root);
        TFShopConfig.load();
    }

    private static JsonObject category(JsonObject root, String id) {
        for (JsonElement e : root.getAsJsonArray("categorias")) {
            JsonObject c = e.getAsJsonObject();
            if (TFJson.str(c, "id", "").equals(id)) return c;
        }
        return null;
    }

    private static JsonObject entry(JsonObject root, String cat, int index) {
        JsonObject c = category(root, cat);
        if (c == null || !c.has("objetos")) return null;
        JsonArray a = c.getAsJsonArray("objetos");
        return index >= 0 && index < a.size() ? a.get(index).getAsJsonObject() : null;
    }

    private record ShopField(String key, String label, String help) {}

    private static final List<ShopField> SHOP_FIELDS = List.of(
            new ShopField("comprar", "Precio de compra", "Lo que paga el jugador por un lote. 0 = no se compra."),
            new ShopField("vender", "Precio de venta", "Lo que la tienda paga por un lote. 0 = no se vende."),
            new ShopField("cantidad", "Cantidad por lote", "Unidades en cada lote."),
            new ShopField("limiteCompraDiario", "Límite de compra al día", "Unidades por jugador. 0 = sin límite."),
            new ShopField("limiteVentaDiario", "Límite de venta al día", "Unidades por jugador. 0 = sin límite."),
            new ShopField("nombre", "Nombre", "Vacío = el del objeto."));

    private static final class Shop implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            JsonObject root = TFJson.read(TFShopConfig.file());
            if (root == null) root = new JsonObject();
            if (!root.has("categorias")) root.add("categorias", new JsonArray());
            String[] t = tab.split(":");
            if (t[0].equals("c") && t.length > 1 && category(root, t[1]) != null) return categoryView(player, root, t[1]);
            if (t[0].equals("i") && t.length > 2) {
                int idx = parse(t[2]);
                JsonObject e = entry(root, t[1], idx);
                if (e != null) return itemView(player, root, t[1], idx, e);
            }
            return main(player, root);
        }

        private PadView main(ServerPlayer player, JsonObject root) {
            PadView.Builder b = PadView.of("a_tienda").header("Pulsa una categoría para cambiar sus objetos y precios.");
            b.row(setting(new ItemStack(Items.CHEST), "Tienda abierta", "Si la cierras, solo el staff puede usarla.",
                    toggle(TFJson.bool(root, "activada", true), "abierta"), false));
            b.row(setting(new ItemStack(Items.HOPPER), "Botón VENDER TODO", "Vende de una vez todo lo que la tienda compra.",
                    toggle(TFJson.bool(root, "botonVenderTodo", true), "vendertodo"), false));
            for (JsonElement e : root.getAsJsonArray("categorias")) {
                JsonObject c = e.getAsJsonObject();
                int n = c.has("objetos") ? c.getAsJsonArray("objetos").size() : 0;
                int sells = 0;
                if (c.has("objetos")) for (JsonElement o : c.getAsJsonArray("objetos")) if (TFJson.num(o.getAsJsonObject(), "vender", 0) > 0) sells++;
                b.row(new PadView.Row(item(TFJson.str(c, "icono", "minecraft:chest"), Items.CHEST), TFJson.str(c, "nombre", "?"), TEXT,
                        List.of(n + " objetos · la tienda compra " + sells), -1, "", null, null).clickable("tab:c:" + TFJson.str(c, "id", "")));
            }
            b.input("nuevacat", "NOMBRE DE UNA CATEGORÍA NUEVA", 32, "CREAR");
            return b.build();
        }

        private PadView categoryView(ServerPlayer player, JsonObject root, String cat) {
            JsonObject c = category(root, cat);
            String edit = editing(player, "a_tienda");
            PadView.Builder b = PadView.of("a_tienda").header(TFJson.str(c, "nombre", "?") + " · pulsa un objeto para sus precios.");
            JsonArray objs = c.has("objetos") ? c.getAsJsonArray("objetos") : new JsonArray();
            for (int i = 0; i < objs.size(); i++) {
                JsonObject o = objs.get(i).getAsJsonObject();
                ItemStack s = entryStack(o);
                long buy = TFJson.num(o, "comprar", 0), sell = TFJson.num(o, "vender", 0);
                String sub = (buy > 0 ? "C " + PadShop.price(buy) : "") + (buy > 0 && sell > 0 ? " · " : "") + (sell > 0 ? "V " + PadShop.price(sell) : "");
                String name = TFJson.str(o, "nombre", "");
                b.card(s, name.isBlank() ? s.getHoverName().getString() : name, sell > 0 ? 0x40C850 : 0xF6B628, sub.isEmpty() ? "sin precio" : sub,
                        "tab:i:" + cat + ":" + i, false);
            }
            b.empty("Sin objetos. Ponte uno en la mano y pulsa AÑADIR LO DE MI MANO.");
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            b.footer(PadView.Btn.of("AÑADIR LO DE MI MANO", "anadir:" + cat, PadView.GREEN));
            b.footer(PadView.Btn.of("ICONO", "icono:" + cat, PadView.GRAY));
            boolean sure = PadServer.confirming(player, "a_tienda.borrar:" + cat);
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrarcat:" + cat, PadView.RED));
            if (edit.equals("cat:" + cat)) b.input("nombrecat:" + cat, "NUEVO NOMBRE", 32, "GUARDAR");
            return b.build();
        }

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
            s.setCount((int) Math.max(1, Math.min(64, TFJson.num(o, "cantidad", 1))));
            return s;
        }

        private PadView itemView(ServerPlayer player, JsonObject root, String cat, int idx, JsonObject o) {
            String edit = editing(player, "a_tienda");
            ItemStack s = entryStack(o);
            PadView.Builder b = PadView.of("a_tienda").header(s.getHoverName().getString() + " · " + TFJson.str(category(root, cat), "nombre", ""));
            for (ShopField f : SHOP_FIELDS) {
                String value = f.key.equals("nombre") ? TFJson.str(o, "nombre", "") : String.valueOf(TFJson.num(o, f.key, f.key.equals("cantidad") ? 1 : 0));
                if (!f.key.equals("nombre") && (f.key.equals("comprar") || f.key.equals("vender"))) {
                    long v = TFJson.num(o, f.key, 0);
                    value = v > 0 ? PadView.moneyExact(v) : "No";
                }
                b.row(new PadView.Row(f.key.equals("vender") ? new ItemStack(Items.HOPPER) : f.key.equals("comprar") ? new ItemStack(Items.GOLD_NUGGET) : s.copy(),
                        f.label, TEXT, List.of(f.help), -1, value.isEmpty() ? "—" : value, editBtn(f.key), null).selected(edit.equals(f.key)));
            }
            b.footer(PadView.Btn.of("ATRÁS", "tab:c:" + cat, PadView.BLUE));
            b.footer(PadView.Btn.of("CAMBIAR POR MI MANO", "mano:" + cat + ":" + idx, PadView.GRAY));
            boolean sure = PadServer.confirming(player, "a_tienda.quitar:" + cat + ":" + idx);
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "QUITAR", "quitar:" + cat + ":" + idx, PadView.RED));
            if (!edit.isEmpty()) {
                for (ShopField f : SHOP_FIELDS) {
                    if (f.key.equals(edit)) b.input("campo:" + cat + ":" + idx, f.label.toUpperCase(Locale.ROOT), 32, "GUARDAR");
                }
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":");
            switch (a[0]) {
                case "abierta" -> editShop(r -> r.addProperty("activada", !TFJson.bool(r, "activada", true)));
                case "vendertodo" -> editShop(r -> r.addProperty("botonVenderTodo", !TFJson.bool(r, "botonVenderTodo", true)));
                case "nuevacat" -> {
                    String name = text.trim();
                    if (name.isEmpty()) return null;
                    String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
                    if (base.isEmpty()) base = "categoria";
                    String id = base.length() > 24 ? base.substring(0, 24) : base;
                    String[] made = {null};
                    editShop(r -> {
                        String cid = id;
                        for (int i = 2; category(r, cid) != null; i++) cid = id + "_" + i;
                        JsonObject c = new JsonObject();
                        c.addProperty("id", cid);
                        c.addProperty("nombre", name);
                        ItemStack h = player.getMainHandItem();
                        c.addProperty("icono", h.isEmpty() ? "minecraft:chest" : idOf(h));
                        c.add("objetos", new JsonArray());
                        r.getAsJsonArray("categorias").add(c);
                        made[0] = cid;
                    });
                    return "c:" + made[0];
                }
                case "anadir" -> {
                    ItemStack h = hand(player);
                    if (h.isEmpty() || a.length < 2) return null;
                    int[] index = {-1};
                    editShop(r -> {
                        JsonObject c = category(r, a[1]);
                        if (c == null) return;
                        if (!c.has("objetos")) c.add("objetos", new JsonArray());
                        JsonObject o = new JsonObject();
                        o.addProperty("objeto", idOf(h));
                        if (h.hasTag() && !h.getTag().isEmpty()) o.addProperty("nbt", h.getTag().toString());
                        o.addProperty("cantidad", h.getCount());
                        o.addProperty("comprar", 0);
                        o.addProperty("vender", 0);
                        c.getAsJsonArray("objetos").add(o);
                        index[0] = c.getAsJsonArray("objetos").size() - 1;
                    });
                    TFPadNet.notice(player, "Añadido. Ponle precio de compra y/o de venta.");
                    return index[0] >= 0 ? "i:" + a[1] + ":" + index[0] : null;
                }
                case "icono" -> {
                    ItemStack h = hand(player);
                    if (h.isEmpty() || a.length < 2) return null;
                    editShop(r -> {
                        JsonObject c = category(r, a[1]);
                        if (c != null) c.addProperty("icono", idOf(h));
                    });
                }
                case "borrarcat" -> {
                    if (a.length < 2 || !PadServer.confirm(player, "a_tienda.borrar:" + a[1])) return null;
                    editShop(r -> {
                        JsonArray keep = new JsonArray();
                        for (JsonElement e : r.getAsJsonArray("categorias")) if (!TFJson.str(e.getAsJsonObject(), "id", "").equals(a[1])) keep.add(e);
                        r.add("categorias", keep);
                    });
                    return "";
                }
                case "nombrecat" -> {
                    if (a.length < 2 || text.isBlank()) return null;
                    editShop(r -> {
                        JsonObject c = category(r, a[1]);
                        if (c != null) c.addProperty("nombre", text.trim());
                    });
                    stopEdit(player, "a_tienda");
                }
                case "editar" -> startEdit(player, "a_tienda", a.length > 1 ? a[1] : "");
                case "campo" -> {
                    if (a.length < 3) return null;
                    String field = editing(player, "a_tienda");
                    int idx = parse(a[2]);
                    if (field.equals("nombre")) {
                        editShop(r -> {
                            JsonObject o = entry(r, a[1], idx);
                            if (o != null) o.addProperty("nombre", text.trim());
                        });
                    } else {
                        long v = number(text);
                        if (v < 0) {
                            TFPadNet.notice(player, "Escribe un número (0 para quitarlo).");
                            return null;
                        }
                        long value = field.equals("cantidad") ? Math.max(1, Math.min(6400, v)) : v;
                        editShop(r -> {
                            JsonObject o = entry(r, a[1], idx);
                            if (o != null) o.addProperty(field, value);
                        });
                    }
                    stopEdit(player, "a_tienda");
                    TFPadNet.notice(player, "Guardado.");
                }
                case "mano" -> {
                    ItemStack h = hand(player);
                    if (h.isEmpty() || a.length < 3) return null;
                    int idx = parse(a[2]);
                    editShop(r -> {
                        JsonObject o = entry(r, a[1], idx);
                        if (o == null) return;
                        o.addProperty("objeto", idOf(h));
                        if (h.hasTag() && !h.getTag().isEmpty()) o.addProperty("nbt", h.getTag().toString());
                        else o.remove("nbt");
                        o.addProperty("cantidad", h.getCount());
                    });
                }
                case "quitar" -> {
                    if (a.length < 3 || !PadServer.confirm(player, "a_tienda.quitar:" + a[1] + ":" + a[2])) return null;
                    int idx = parse(a[2]);
                    editShop(r -> {
                        JsonObject c = category(r, a[1]);
                        if (c == null || !c.has("objetos")) return;
                        JsonArray arr = c.getAsJsonArray("objetos");
                        if (idx >= 0 && idx < arr.size()) arr.remove(idx);
                    });
                    return "c:" + a[1];
                }
                default -> {
                }
            }
            return null;
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** Quita el objeto n.º index de la lista «objetos» (los que se leen; los ilegibles no cuentan). */
    static void removeItem(JsonObject o, int index) {
        if (!o.has("objetos") || !o.get("objetos").isJsonArray()) return;
        JsonArray keep = new JsonArray();
        int i = 0;
        for (JsonElement e : o.getAsJsonArray("objetos")) {
            boolean valid = !PadItemPicker.stack(e).isEmpty();
            if (valid && i++ == index) continue;
            keep.add(e);
        }
        o.add("objetos", keep);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // KITS (config/tfclient/kits.json)
    // ---------------------------------------------------------------------------------------------------------------

    private static final class Kits implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            tab = PadItemPicker.tab(player, "a_kits", tab);
            if (tab.startsWith("p:")) { // elegir objetos para el kit
                PadKits.Kit k = PadKits.kit(tab.substring(2));
                if (k != null) return PadItemPicker.view(player, "a_kits", tab, k.name(), "tab:k:" + k.id());
            }
            if (tab.startsWith("k:")) {
                PadKits.Kit k = PadKits.kit(tab.substring(2));
                if (k != null) return kitView(player, k);
            }
            PadView.Builder b = PadView.of("a_kits").header("Pulsa un kit para cambiarlo. Uno nuevo se crea con lo que llevas en el inventario (o vacío).");
            for (PadKits.Kit k : PadKits.kits()) {
                b.card(new ItemStack(k.iconItem()), k.name(), 0x40C850, PadKits.typeText(k), "tab:k:" + k.id(), false);
            }
            b.input("nuevo", "NOMBRE DEL KIT NUEVO", 32, "CREAR");
            return b.build();
        }

        private PadView kitView(ServerPlayer player, PadKits.Kit k) {
            String edit = editing(player, "a_kits");
            PadView.Builder b = PadView.of("a_kits").header(k.name() + " · " + PadKits.typeText(k) + " · " + k.items().size() + " objetos");
            b.row(new PadView.Row(new ItemStack(Items.NAME_TAG), "Nombre", TEXT, List.of(k.name()), -1, "", editBtn("nombre"), null).selected(edit.equals("nombre")));
            b.row(new PadView.Row(new ItemStack(Items.CLOCK), "Tipo", TEXT, List.of(PadKits.typeText(k)), -1, "",
                    PadView.Btn.of("CAMBIAR", "tipo:" + k.id(), PadView.BLUE), null));
            if (k.type().equals("horas")) {
                b.row(new PadView.Row(new ItemStack(Items.CLOCK), "Horas de espera", TEXT, List.of("Entre una vez y la siguiente."), -1,
                        String.valueOf(k.cooldownSeconds() / 3600), editBtn("horas"), null).selected(edit.equals("horas")));
            }
            b.row(new PadView.Row(new ItemStack(Items.GOLD_NUGGET), "Monedas", TEXT, List.of("Además de los objetos."), -1,
                    k.coins() > 0 ? PadView.moneyExact(k.coins()) : "—", editBtn("monedas"), null).selected(edit.equals("monedas")));
            b.row(new PadView.Row(new ItemStack(k.iconItem()), "Icono", TEXT, List.of("El objeto que sale en su tarjeta."), -1, "",
                    PadView.Btn.of("MI MANO", "icono:" + k.id(), PadView.GRAY), null));
            boolean sureEmpty = PadServer.confirming(player, "a_kits.vaciar:" + k.id());
            b.row(new PadView.Row(new ItemStack(Items.BUNDLE), "Objetos", TEXT,
                    List.of("AÑADIR: de tu inventario o de todos los objetos del juego (también mods). Pulsa uno de abajo para quitarlo."), -1,
                    String.valueOf(k.items().size()), PadView.Btn.of("AÑADIR", "tab:p:" + k.id(), PadView.GREEN),
                    PadView.Btn.of(sureEmpty ? "¿SEGURO?" : "VACIAR", "vaciar:" + k.id(), PadView.RED)));
            for (int i = 0; i < k.items().size(); i++) b.cell(k.items().get(i).copy(), "", TEXT, "quitarobj:" + k.id() + ":" + i, false);
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            boolean sure = PadServer.confirming(player, "a_kits.borrar:" + k.id());
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar:" + k.id(), PadView.RED));
            if (!edit.isEmpty()) b.input("campo:" + k.id(), edit.equals("nombre") ? "NOMBRE" : edit.equals("horas") ? "HORAS" : "MONEDAS", 32, "GUARDAR");
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            tab = PadItemPicker.tab(player, "a_kits", tab);
            // el selector de objetos: lo elegido se añade al kit
            if (tab.startsWith("p:")) {
                ItemStack chosen = PadItemPicker.action(player, "a_kits", action, text);
                if (chosen != null) {
                    if (!chosen.isEmpty()) {
                        String kid = tab.substring(2);
                        PadKits.edit(kid, o -> {
                            if (!o.has("objetos") || !o.get("objetos").isJsonArray()) o.add("objetos", new JsonArray());
                            o.getAsJsonArray("objetos").add(PadItemPicker.json(chosen));
                        });
                        TFPadNet.notice(player, "Añadido: " + chosen.getCount() + " × " + chosen.getHoverName().getString() + ".");
                    }
                    return null;
                }
            }
            String[] a = action.split(":", 2);
            String id = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "nuevo" -> {
                    if (text.isBlank()) return null;
                    String made = PadKits.createFromInventory(player, text.trim(), "unico");
                    TFPadNet.notice(player, "Kit creado (una sola vez). Añade objetos o cambia el tipo.");
                    return "k:" + made;
                }
                case "vaciar" -> {
                    if (!PadServer.confirm(player, "a_kits.vaciar:" + id)) return null;
                    PadKits.edit(id, o -> o.add("objetos", new JsonArray()));
                }
                case "quitarobj" -> {
                    int colon = id.lastIndexOf(':');
                    if (colon < 0) return null;
                    String kid = id.substring(0, colon);
                    int index;
                    try {
                        index = Integer.parseInt(id.substring(colon + 1));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    PadKits.edit(kid, o -> removeItem(o, index));
                }
                case "tipo" -> {
                    PadKits.Kit k = PadKits.kit(id);
                    if (k == null) return null;
                    String next = PadKits.TYPES.get((PadKits.TYPES.indexOf(k.type()) + 1) % PadKits.TYPES.size());
                    PadKits.edit(id, o -> {
                        o.addProperty("tipo", next);
                        if (next.equals("horas") && TFJson.num(o, "esperaSegundos", 0) <= 0) o.addProperty("esperaSegundos", 86400);
                    });
                }
                case "icono" -> {
                    ItemStack h = hand(player);
                    if (!h.isEmpty()) PadKits.edit(id, o -> o.addProperty("icono", idOf(h)));
                }
                case "objetos" -> {
                    JsonArray items = PadKits.inventoryItems(player);
                    if (items.isEmpty()) {
                        TFPadNet.notice(player, "Lleva en el inventario lo que debe traer el kit.");
                        return null;
                    }
                    PadKits.edit(id, o -> o.add("objetos", items));
                    TFPadNet.notice(player, "Objetos del kit cambiados (" + items.size() + ").");
                }
                case "borrar" -> {
                    if (!PadServer.confirm(player, "a_kits.borrar:" + id)) return null;
                    PadKits.delete(id);
                    return "";
                }
                case "editar" -> startEdit(player, "a_kits", id);
                case "campo" -> {
                    String field = editing(player, "a_kits");
                    if (field.equals("nombre")) {
                        if (!text.isBlank()) PadKits.edit(id, o -> o.addProperty("nombre", text.trim()));
                    } else {
                        long v = number(text);
                        if (v < 0) {
                            TFPadNet.notice(player, "Escribe un número.");
                            return null;
                        }
                        if (field.equals("horas")) PadKits.edit(id, o -> o.addProperty("esperaSegundos", Math.max(1, v) * 3600));
                        else PadKits.edit(id, o -> o.addProperty("monedas", v));
                    }
                    stopEdit(player, "a_kits");
                    TFPadNet.notice(player, "Guardado.");
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

    private static final class Travel implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            String edit = editing(player, "a_viajes");
            PadView.Builder b = PadView.of("a_viajes").header("Los puntos se ponen donde estás. Los warps de EssentialsX salen solos.");
            for (PadTravel.Point p : PadTravel.points()) {
                boolean sure = PadServer.confirming(player, "a_viajes.borrar:" + p.id());
                b.row(new PadView.Row(item(p.icon(), Items.FILLED_MAP), p.name(), TEXT,
                        List.of(PadTravel.dimName(p.dim()) + " · " + (int) p.x() + ", " + (int) p.y() + ", " + (int) p.z()), -1, "",
                        PadView.Btn.of("IR", "ir:" + p.id(), PadView.BLUE), PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar:" + p.id(), PadView.RED))
                        .clickable("sel:" + p.id()).selected(edit.equals("p:" + p.id())));
            }
            if (edit.startsWith("p:")) {
                String id = edit.substring(2);
                b.footer(PadView.Btn.of("MOVER AQUÍ", "mover:" + id, PadView.BLUE));
                b.footer(PadView.Btn.of("ICONO = MI MANO", "icono:" + id, PadView.GRAY));
                b.footer(PadView.Btn.of("LISTO", "cancelar", PadView.GREEN));
                b.input("nombre:" + id, "NUEVO NOMBRE", 32, "RENOMBRAR");
            } else {
                b.input("nuevo", "NOMBRE DEL PUNTO (SE PONE DONDE ESTÁS)", 32, "AÑADIR AQUÍ");
            }
            List<EssentialsWarps.Warp> warps = EssentialsWarps.list(player.getServer());
            if (!warps.isEmpty()) {
                b.text("Warps de EssentialsX" + (PadConfig.on("viajes.warps") ? "" : " (apagados en Ajustes)"), GOLD, List.of());
                for (EssentialsWarps.Warp w : warps) {
                    boolean shown = !PadConfig.hiddenWarps().contains(w.id());
                    b.row(new PadView.Row(new ItemStack(Items.ENDER_PEARL), w.name(), shown ? TEXT : MUTED, List.of("/warp " + w.id()), -1, "",
                            PadView.Btn.of(shown ? "SE VE" : "OCULTO", "warp:" + w.id(), shown ? PadView.GREEN : PadView.GRAY), null));
                }
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            String id = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "nuevo" -> {
                    if (text.isBlank()) return null;
                    String base = text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
                    if (base.isEmpty()) base = "punto";
                    String pid = base;
                    for (int i = 2; PadTravel.point(pid) != null; i++) pid = base + "_" + i;
                    PadTravel.putPoint(player, pid, text.trim());
                    TFPadNet.notice(player, "Punto puesto aquí.");
                }
                case "sel" -> startEdit(player, "a_viajes", "p:" + id);
                case "cancelar" -> stopEdit(player, "a_viajes");
                case "mover" -> {
                    PadTravel.Point p = PadTravel.point(id);
                    if (p != null) PadTravel.putPoint(player, id, p.name());
                    TFPadNet.notice(player, "Punto movido aquí.");
                }
                case "icono" -> {
                    ItemStack h = hand(player);
                    if (!h.isEmpty()) PadTravel.editPoint(id, o -> o.addProperty("icono", idOf(h)));
                }
                case "nombre" -> {
                    if (!text.isBlank()) PadTravel.editPoint(id, o -> o.addProperty("nombre", text.trim()));
                }
                case "borrar" -> {
                    if (!PadServer.confirm(player, "a_viajes.borrar:" + id)) return null;
                    PadTravel.removePoint(id);
                    stopEdit(player, "a_viajes");
                }
                case "ir" -> {
                    PadTravel.Point p = PadTravel.point(id);
                    if (p == null) return null;
                    ServerLevel level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(p.dim())));
                    if (level == null) return null;
                    TFPadNet.close(player);
                    player.teleportTo(level, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
                }
                case "warp" -> PadConfig.toggleWarp(id);
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
            PadView.Builder b = PadView.of("a_gts").header(all.size() + " cosas a la venta. RETIRAR devuelve el objeto a su dueño (a Recoger).");
            for (TFMarket.Listing l : all) {
                long days = Math.max(0, (l.expiresAt() - System.currentTimeMillis()) / 86_400_000L);
                boolean sure = PadServer.confirming(player, "a_gts.retirar:" + l.id());
                b.row(new PadView.Row(l.item(), TFMarket.describe(l.item()), TEXT, List.of("De " + l.sellerName() + " · quedan " + days + " días"), -1,
                        PadView.moneyExact(l.price()), PadView.Btn.of(sure ? "¿SEGURO?" : "RETIRAR", "retirar:" + l.id(), PadView.RED), null));
            }
            b.empty("No hay nada a la venta.");
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
            PadView.Builder b = PadView.of("a_comunidad").tab("", "DENUNCIADAS").tab("todas", "RECIENTES").selected(reported ? "" : "todas");
            for (PadCommunityServer.PostInfo p : PadCommunityServer.adminPosts(reported, 60)) {
                boolean sure = PadServer.confirming(player, "a_comunidad.borrar:" + p.id());
                List<String> lines = new ArrayList<>();
                if (!p.caption().isEmpty()) lines.add("«" + p.caption() + "»");
                lines.add(p.likes() + " likes · " + p.reports() + " denuncias");
                b.row(new PadView.Row(new ItemStack(Items.PAINTING), p.name(), p.reports() > 0 ? 0xC8323C : TEXT, lines, -1, "",
                        PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar:" + p.id(), PadView.RED),
                        p.reports() > 0 ? PadView.Btn.of("ESTÁ BIEN", "ok:" + p.id(), PadView.GREEN) : null));
            }
            b.empty(reported ? "No hay fotos denunciadas." : "No hay fotos.");
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
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // OFICIOS (ajustes generales de config/tfclient/oficios.json)
    // ---------------------------------------------------------------------------------------------------------------

    private record JobField(String key, String label, String help, boolean bool, long min, long max) {}

    private static final List<JobField> JOB_FIELDS = List.of(
            new JobField("activado", "Oficios", "Si se apagan, nadie gana con ellos.", true, 0, 1),
            new JobField("maxOficios", "Oficios a la vez", "Cuántos puede tener cada jugador.", false, 1, 10),
            new JobField("esperaCambioMinutos", "Espera para cambiar (min)", "Tras unirse a uno.", false, 0, 1440),
            new JobField("pagoCadaSegundos", "Pago cada (s)", "Cada cuánto se cobra lo ganado.", false, 1, 600),
            new JobField("avisoActionBar", "Aviso encima de la barra", "+xp y +monedas al trabajar.", true, 0, 1),
            new JobField("bloquesColocadosNoCuentan", "Bloques colocados no cuentan", "Evita poner y romper para ganar.", true, 0, 1),
            new JobField("generadoresCuentan", "Mobs de generador cuentan", "Mejor que no (granjas).", true, 0, 1));

    private static void editJobs(Consumer<JsonObject> f) {
        JsonObject root = TFJson.read(TFJobsConfig.file());
        if (root == null) return;
        f.accept(root);
        TFJson.write(TFJobsConfig.file(), root);
        TFJobsConfig.load();
    }

    private static final class Jobs implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            JsonObject root = TFJson.read(TFJobsConfig.file());
            if (root == null) root = new JsonObject();
            String edit = editing(player, "a_oficios");
            PadView.Builder b = PadView.of("a_oficios").header("Ajustes de los oficios. Pagos, misiones y niveles: en config/tfclient/oficios.json.");
            for (JobField f : JOB_FIELDS) {
                if (f.bool) {
                    boolean on = TFJson.bool(root, f.key, !f.key.equals("generadoresCuentan"));
                    b.row(setting(new ItemStack(on ? Items.LIME_DYE : Items.GRAY_DYE), f.label, f.help, toggle(on, "si:" + f.key), false));
                } else {
                    b.row(new PadView.Row(new ItemStack(Items.COMPARATOR), f.label, TEXT, List.of(f.help), -1, String.valueOf(TFJson.num(root, f.key, 0)),
                            editBtn(f.key), null).selected(edit.equals(f.key)));
                }
            }
            b.text("Oficios", GOLD, List.of());
            for (TFJobsConfig.Job job : TFJobsConfig.jobs.values()) {
                b.row(new PadView.Row(new ItemStack(net.tierrasfantasticas.tfclient.jobs.TFJobsMenu.icon(job)), job.name(), job.color(),
                        List.of(job.actions().size() + " formas de ganar · " + job.missions().size() + " misiones"), -1, "", null, null));
            }
            if (!edit.isEmpty()) b.input("valor", "NUEVO VALOR", 10, "GUARDAR");
            b.footer(PadView.Btn.of("RECARGAR OFICIOS", "recargar", PadView.GOLD));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("si:")) {
                String key = action.substring(3);
                editJobs(r -> r.addProperty(key, !TFJson.bool(r, key, !key.equals("generadoresCuentan"))));
            } else if (action.startsWith("editar:")) {
                startEdit(player, "a_oficios", action.substring(7));
            } else if (action.equals("valor")) {
                String key = editing(player, "a_oficios");
                long v = number(text);
                for (JobField f : JOB_FIELDS) {
                    if (!f.key.equals(key)) continue;
                    if (v < f.min || v > f.max) {
                        TFPadNet.notice(player, "Entre " + f.min + " y " + f.max + ".");
                        return null;
                    }
                    editJobs(r -> r.addProperty(key, v));
                }
                stopEdit(player, "a_oficios");
                TFPadNet.notice(player, "Guardado.");
            } else if (action.equals("recargar")) {
                List<String> w = TFJobsConfig.load();
                TFPadNet.notice(player, "Oficios recargados" + (w.isEmpty() ? "." : " (" + w.size() + " avisos en la consola)."));
            }
            return null;
        }
    }

    static String unused(CompoundTag t) {
        return t.toString();
    }
}
