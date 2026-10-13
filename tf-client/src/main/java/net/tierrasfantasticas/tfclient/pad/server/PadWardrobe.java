package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.skins.SkinServer;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFWardrobe;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.skills.SkillServer;
import net.tierrasfantasticas.tfclient.util.TFJson;
import net.tierrasfantasticas.tfclient.vfx.VfxCatalog;
import net.tierrasfantasticas.tfclient.vfx.VfxServer;

/**
 * Armario y Efectos dentro del pad. Armario: lo que tienes de tus compras y de tu rango (lo dice la web por el puente)
 * por hueco (CABEZA, PECHO, PIERNAS, PIES, ESPALDA) con PONER / QUITAR; solo cambia cómo te ven, no la armadura de
 * verdad. Efectos: los efectos de kill (gratis) con EQUIPAR / QUITAR y, en SKILLS, las clases de skills que has
 * comprado en la web con ACTIVAR / DESACTIVAR (una activa a la vez). Todo se aplica al momento en el servidor y se
 * guarda en la web en la siguiente consulta del puente.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadWardrobe {
    public static final PadServer.App WARDROBE = new Wardrobe();
    public static final PadServer.App EFFECTS = new Effects();

    private static final int TEXT = 0x18265C;
    /** El color de las tarjetas del Armario (el de la app). */
    private static final int WARDROBE_COLOR = 0x9E5AE0;
    private static final String[] LABELS = {"CABEZA", "PECHO", "PIERNAS", "PIES", "ESPALDA"};

    // ---------------------------------------------------------------------------------------------------------------
    // Imágenes de los efectos (tools/pad/fx_images.py): textures/gui/pad/fx/kill_<id>.png, class_<id>.png y meta.json
    // ---------------------------------------------------------------------------------------------------------------

    /** Lo que dice la web de un efecto de kill o de una clase: categoría (o grupo), color, descripción y orden. */
    record FxMeta(String cat, int color, String desc, int order, String role) {}

    private static Map<String, FxMeta> killMeta, classMeta;

    private static synchronized void loadMeta() {
        if (killMeta != null) return;
        killMeta = new HashMap<>();
        classMeta = new HashMap<>();
        try (java.io.InputStream in = PadWardrobe.class.getResourceAsStream("/assets/tfclient/textures/gui/pad/fx/meta.json")) {
            if (in == null) return;
            JsonObject o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("kills").entrySet()) {
                JsonObject k = e.getValue().getAsJsonObject();
                killMeta.put(e.getKey(), new FxMeta(TFJson.str(k, "cat", ""), k.has("color") ? k.get("color").getAsInt() : 0xE0489E,
                        TFJson.str(k, "desc", ""), k.has("order") ? k.get("order").getAsInt() : 99, ""));
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("classes").entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                classMeta.put(e.getKey(), new FxMeta(TFJson.str(c, "group", ""), c.has("color") ? c.get("color").getAsInt() : 0xE0489E,
                        TFJson.str(c, "desc", ""), 0, TFJson.str(c, "role", "")));
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo leer fx/meta.json", e);
        }
    }

    static FxMeta killMeta(String id) {
        loadMeta();
        return killMeta.getOrDefault(id, new FxMeta("", 0xE0489E, "", 99, ""));
    }

    static FxMeta classMeta(String id) {
        loadMeta();
        return classMeta.getOrDefault(id, new FxMeta("", 0xE0489E, "", 0, ""));
    }

    /**
     * Un color que se lee como texto sobre blanco: los muy claros (amarillos, celestes) se oscurecen hacia el azul
     * noche del pad.
     */
    static int readable(int rgb) {
        double lum = (0.299 * (rgb >> 16 & 255) + 0.587 * (rgb >> 8 & 255) + 0.114 * (rgb & 255)) / 255;
        if (lum <= 0.55) return rgb;
        float k = (float) Math.min(0.75, (lum - 0.55) / 0.45 + 0.35);
        int r = Math.round((rgb >> 16 & 255) + (0x18 - (rgb >> 16 & 255)) * k);
        int g = Math.round((rgb >> 8 & 255) + (0x26 - (rgb >> 8 & 255)) * k);
        int b = Math.round((rgb & 255) + (0x5C - (rgb & 255)) * k);
        return r << 16 | g << 8 | b;
    }

    record Piece(String set, String id, String name, String slot, String from) {
        String key() {
            return set + "/" + id;
        }
    }

    /** Lo que tiene cada uno según la web (llega por el puente al abrir el Armario). */
    private static final Map<UUID, List<Piece>> OWNED = new HashMap<>();

    private PadWardrobe() {}

    /** padData de la web: [{uuid, owned: [{set, id, name, slot, from}]}]. */
    public static void receive(MinecraftServer srv, JsonArray list) {
        for (JsonElement el : list) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            UUID uuid;
            try {
                uuid = UUID.fromString(TFJson.str(o, "uuid", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            List<Piece> pieces = new ArrayList<>();
            if (o.has("owned") && o.get("owned").isJsonArray()) {
                for (JsonElement p : o.getAsJsonArray("owned")) {
                    if (!p.isJsonObject()) continue;
                    JsonObject q = p.getAsJsonObject();
                    pieces.add(new Piece(TFJson.str(q, "set", ""), TFJson.str(q, "id", ""), TFJson.str(q, "name", ""),
                            TFJson.str(q, "slot", ""), TFJson.str(q, "from", "")));
                }
            }
            boolean first = !OWNED.containsKey(uuid);
            List<Piece> old = OWNED.put(uuid, pieces);
            ServerPlayer player = srv.getPlayerList().getPlayer(uuid);
            if (player != null && (first || !pieces.equals(old))) {
                PadServer.refresh(player, "armario", PadServer.get(player, "armario.tab", ""));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OWNED.remove(event.getEntity().getUUID());
    }

    private static ItemStack stack(String set, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TFClient.MOD_ID, set + "_" + id));
        return item == null || item == Items.AIR ? new ItemStack(Items.LEATHER_CHESTPLATE) : new ItemStack(item);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static final class Wardrobe implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            if (tab.isEmpty() || tab.startsWith("skins")) return skinsView(player, tab.isEmpty() ? "skins" : tab);
            int slot = 0;
            for (int i = 0; i < TFWardrobe.SLOTS.length; i++) if (TFWardrobe.SLOTS[i].equals(tab)) slot = i;
            String key = TFWardrobe.SLOTS[slot];
            PadServer.put(player, "armario.tab", key);
            PadView.Builder b = tabs(player, key);
            String[] worn = TFWardrobe.worn(player.getUUID());
            TFBridge.padWant(player.getUUID());
            List<Piece> owned = OWNED.get(player.getUUID());
            if (owned == null) {
                // sin la web conectada no hay lista de piezas: el Armario sigue (las skins no dependen de ella)
                if (TFBridge.connected()) return b.empty("Cargando…").build();
                owned = List.of();
            }
            // 1.4: cada pieza es una tarjeta (pulsarla la pone; la puesta, marcada, se quita con QUITAR abajo)
            int n = 0;
            String wornName = "";
            for (Piece p : owned) {
                if (!p.slot.equals(key)) continue;
                n++;
                boolean on = worn[slot].equals(p.key());
                String name = p.name.isEmpty() ? p.key() : p.name;
                if (on) wornName = name;
                b.card(stack(p.set, p.id), name, WARDROBE_COLOR, on ? "PUESTO" : p.from.isEmpty() ? "PONER" : p.from,
                        on ? PadView.TONE_GREEN : PadView.TONE_BLUE, -1, "", on ? "" : "poner:" + slot + ":" + p.key(), on);
            }
            b.header(wornName.isEmpty() ? "Pulsa una pieza para ponértela." : "Llevas " + wornName + ".");
            if (!wornName.isEmpty()) b.footer(PadView.Btn.of("QUITAR", "quitar:" + slot, PadView.RED));
            if (n == 0) b.empty("No tienes nada para " + LABELS[slot].toLowerCase() + ". Los sets de los rangos y de las crates de la tienda web aparecen aquí.");
            return b.build();
        }

        /** Las pestañas: SKINS primero y luego las piezas (con un punto si llevas algo en ella). */
        private static PadView.Builder tabs(ServerPlayer player, String selected) {
            PadView.Builder b = PadView.of("armario").tab("skins", "SKINS");
            String[] worn = TFWardrobe.worn(player.getUUID());
            for (int i = 0; i < LABELS.length; i++) b.tab(TFWardrobe.SLOTS[i], worn[i].isEmpty() ? LABELS[i] : LABELS[i] + " ·");
            return b.selected(selected.startsWith("skins") ? "skins" : selected);
        }

        /** SKINS: tu colección («skins»), una skin («skins:ver:id») o añadir una («skins:nueva»). */
        private static PadView skinsView(ServerPlayer player, String tab) {
            PadServer.put(player, "armario.tab", tab);
            PadView.Builder b = tabs(player, "skins");
            java.util.UUID uuid = player.getUUID();
            if (!SkinServer.hasClient(player)) {
                return b.empty("Para usar skins necesitas TF Client actualizado.").build();
            }
            List<SkinServer.Skin> mine = SkinServer.skins(uuid);
            if (tab.equals("skins:nueva")) return newSkinView(player, b, mine.size());
            if (tab.startsWith("skins:ver:")) {
                SkinServer.Skin sk = SkinServer.skin(uuid, tab.substring(10));
                if (sk != null) return skinView(player, b, sk);
            }
            String worn = SkinServer.worn(uuid);
            SkinServer.sendImages(player, mine.stream().map(SkinServer.Skin::hash).toList());
            SkinServer.Skin wearing = worn.isEmpty() ? null : SkinServer.skin(uuid, worn);
            // arriba, el escaparate: la skin que llevas en 3D (solo la skin: sin armadura, objetos ni cosméticos);
            // abajo, tu skin de siempre y las tuyas, cada una en 3D en su tarjeta
            List<String> lines = new ArrayList<>();
            lines.add(wearing == null ? "Llevas la skin de tu cuenta de Minecraft." : "Llevas «" + wearing.name() + "».");
            lines.add(mine.isEmpty() ? "Añade skins de tu ordenador o de cualquier jugador con AÑADIR SKIN."
                    : "Pulsa una skin de abajo para verla o ponértela.");
            b.hero(new PadView.Row(new ItemStack(Items.PLAYER_HEAD), wearing == null ? "Tu skin" : wearing.name(), WARDROBE_COLOR, lines, -1,
                    mine.size() + "/" + SkinServer.MAX_SKINS, wearing == null ? null : PadView.Btn.of("QUITÁRMELA", "skin:original", PadView.RED), null)
                    .image(PadView.SELF_MODEL));
            b.cell(new PadView.Cell(new ItemStack(Items.PLAYER_HEAD), "De siempre", WARDROBE_COLOR, wearing == null ? "" : "skin:original",
                    wearing == null, wearing == null ? "PUESTA" : "PONER", -1, "", wearing == null ? PadView.TONE_GREEN : PadView.TONE_BLUE)
                    .image("tfclient:skinmodel/own"));
            for (SkinServer.Skin sk : mine) {
                boolean on = sk.id().equals(worn);
                b.cell(new PadView.Cell(new ItemStack(Items.PLAYER_HEAD), sk.name(), WARDROBE_COLOR, "ver:skins:ver:" + sk.id(), on,
                        on ? "PUESTA" : "VER", -1, "", on ? PadView.TONE_GREEN : PadView.TONE_BLUE)
                        .image(SkinServer.model(sk.hash(), sk.slim())));
            }
            b.footer(PadView.Btn.of("AÑADIR SKIN", "ver:skins:nueva", PadView.GREEN));
            return b.build();
        }

        /** Una skin: grande, y ponérsela, brazos, exportarla, renombrarla o borrarla. */
        private static PadView skinView(ServerPlayer player, PadView.Builder b, SkinServer.Skin sk) {
            SkinServer.sendImages(player, List.of(sk.hash()));
            boolean on = sk.id().equals(SkinServer.worn(player.getUUID()));
            String from = sk.from().startsWith("jugador:") ? "De " + sk.from().substring(8) + "." : "Importada de tu ordenador.";
            b.hero(new PadView.Row(new ItemStack(Items.PLAYER_HEAD), sk.name(), WARDROBE_COLOR,
                    List.of(from, "Brazos " + (sk.slim() ? "finos (Alex)." : "normales (Steve).") + (on ? " La llevas puesta." : "")),
                    -1, "", null, null).image(SkinServer.model(sk.hash(), sk.slim())));
            String id = sk.id();
            if (on) b.footer(PadView.Btn.of("QUITÁRMELA", "skin:original", PadView.RED));
            else b.footer(PadView.Btn.of("PONÉRMELA", "skin:poner:" + id, PadView.GREEN));
            b.footer(PadView.Btn.of(sk.slim() ? "BRAZOS NORMALES" : "BRAZOS FINOS", "skin:brazos:" + id, PadView.BLUE));
            b.footer(PadView.Btn.of("EXPORTAR", "skin:exportar:" + id, PadView.BLUE));
            boolean sure = ("borrar:" + id).equals(PadServer.get(player, "armario.confirm", ""));
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "skin:borrar:" + id, PadView.RED));
            b.footer(PadView.Btn.of("VOLVER", "ver:skins", PadView.GOLD));
            b.input("skin:renombrar:" + id, "NUEVO NOMBRE", 24, "RENOMBRAR");
            return b.build();
        }

        /** Añadir una skin: de la carpeta del ordenador (lista) o de un jugador de Minecraft por su nombre. */
        private static PadView newSkinView(ServerPlayer player, PadView.Builder b, int have) {
            java.util.UUID uuid = player.getUUID();
            b.header("Guarda hasta " + SkinServer.MAX_SKINS + " skins (tienes " + have + "). Pon tus PNG en la carpeta skins o trae la de un jugador.");
            List<net.tierrasfantasticas.tfclient.skins.SkinNet.Local> locals = SkinServer.locals(uuid);
            if (locals == null || locals.isEmpty()) {
                b.row(new PadView.Row(new ItemStack(Items.CHEST), "Tu carpeta de skins está vacía", WARDROBE_COLOR,
                        List.of("Pulsa ABRIR CARPETA, copia ahí tus skins (.png de 64x64)", "y luego ACTUALIZAR."), -1, "", null, null));
            } else {
                for (int i = 0; i < locals.size(); i++) {
                    var l = locals.get(i);
                    b.row(new PadView.Row(new ItemStack(Items.PAPER), l.name(), WARDROBE_COLOR,
                            List.of(l.slim() ? "Brazos finos." : "Brazos normales."), -1, "",
                            PadView.Btn.of("IMPORTAR", "skin:local:" + i, PadView.GREEN), null));
                }
            }
            int bad = SkinServer.localBad(uuid);
            if (bad > 0) b.header(bad + (bad == 1 ? " archivo no es" : " archivos no son") + " una skin válida (deben ser PNG de 64x64 o 64x32).");
            String busy = SkinServer.fetching(uuid);
            if (busy != null) b.header("Trayendo la skin de " + busy + "…");
            b.footer(PadView.Btn.of("ABRIR CARPETA", "skin:carpeta", PadView.BLUE));
            b.footer(PadView.Btn.of("ACTUALIZAR", "skin:buscar", PadView.BLUE));
            b.footer(PadView.Btn.of("VOLVER", "ver:skins", PadView.GOLD));
            b.input("skin:traer", "NOMBRE DEL JUGADOR", 16, "TRAER SU SKIN");
            return b.build();
        }

        /** Las acciones de SKINS. Devuelve la pestaña a la que ir (null: la misma). */
        private static String skinAction(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 3);
            String id = a.length > 2 ? a[2] : "";
            String msg = null;
            if (!a[1].equals("borrar")) PadServer.put(player, "armario.confirm", "");
            switch (a[1]) {
                case "original" -> {
                    SkinServer.wear(player, "");
                    msg = "Vuelves a llevar tu skin.";
                }
                case "poner" -> msg = SkinServer.wear(player, id) ? "Te pusiste «" + nameOf(player, id) + "»." : "No se pudo poner esa skin.";
                case "brazos" -> {
                    SkinServer.Skin sk = SkinServer.skin(player.getUUID(), id);
                    if (sk != null) SkinServer.setSlim(player, id, !sk.slim());
                }
                case "exportar" -> msg = SkinServer.export(player, id);
                case "renombrar" -> msg = text == null || text.isBlank() ? "Escribe el nombre nuevo." : SkinServer.rename(player, id, text);
                case "borrar" -> {
                    if (!("borrar:" + id).equals(PadServer.get(player, "armario.confirm", ""))) {
                        PadServer.put(player, "armario.confirm", "borrar:" + id);
                        return null;
                    }
                    PadServer.put(player, "armario.confirm", "");
                    String name = nameOf(player, id);
                    if (SkinServer.delete(player, id)) {
                        TFPadNet.notice(player, "Borraste «" + name + "».");
                        return "skins";
                    }
                }
                case "carpeta" -> SkinServer.openFolder(player);
                case "buscar" -> SkinServer.scan(player);
                case "local" -> msg = SkinServer.importLocal(player, parse(id));
                case "traer" -> msg = SkinServer.importPlayer(player, text == null ? "" : text);
                default -> {
                    return null;
                }
            }
            if (msg != null) TFPadNet.notice(player, msg);
            return null;
        }

        private static String nameOf(ServerPlayer player, String id) {
            SkinServer.Skin sk = SkinServer.skin(player.getUUID(), id);
            return sk == null ? id : sk.name();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("ver:")) {
                String to = action.substring(4);
                if (to.equals("skins:nueva")) SkinServer.scan(player);
                return to;
            }
            if (action.startsWith("skin:")) return skinAction(player, tab, action, text);
            String[] a = action.split(":");
            int slot = a.length > 1 ? parse(a[1]) : -1;
            if (slot < 0 || slot >= TFWardrobe.SLOTS.length) return null;
            if (a[0].equals("quitar")) {
                TFWardrobe.setFromPad(player.getUUID(), slot, "");
                TFBridge.padWardrobe(player.getUUID(), TFWardrobe.SLOTS[slot], null, null);
                return null;
            }
            if (!a[0].equals("poner") || a.length < 3) return null;
            String piece = a[2];
            List<Piece> owned = OWNED.getOrDefault(player.getUUID(), List.of());
            Piece found = null;
            for (Piece p : owned) if (p.key().equals(piece) && p.slot.equals(TFWardrobe.SLOTS[slot])) found = p;
            if (found == null || !TFWardrobe.setFromPad(player.getUUID(), slot, piece)) {
                TFPadNet.notice(player, "Esa pieza no se puede poner ahí.");
                return null;
            }
            TFBridge.padWardrobe(player.getUUID(), TFWardrobe.SLOTS[slot], found.set, found.id);
            TFPadNet.notice(player, "Te pusiste " + (found.name.isEmpty() ? found.key() : found.name) + ".");
            return null;
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static final class Effects implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            boolean skills = tab.equals("skills");
            PadServer.put(player, "efectos.tab", skills ? "skills" : "");
            String[] eq = VfxServer.equipped(player.getUUID());
            PadView.Builder b = PadView.of("efectos").tab("", "AL MATAR").tab("skills", "SKILLS").selected(skills ? "skills" : "");
            if (!skills) {
                killsTab(b, eq[0]);
            } else {
                skillsTab(player, b);
            }
            return b.build();
        }

        /**
         * AL MATAR (1.4): una galería con la imagen de cada efecto (la de la web: el zombi con el efecto puesto),
         * agrupados por categoría (la vitrina, de su color) y con la categoría en la etiqueta. Pulsar uno lo equipa; el
         * equipado sale marcado (oro, «EQUIPADO») y se quita con QUITAR EFECTO abajo.
         */
        private static void killsTab(PadView.Builder b, String equipped) {
            List<VfxCatalog.Kill> kills = new ArrayList<>(VfxCatalog.kills().values());
            kills.sort(java.util.Comparator.comparingInt(k -> killMeta(k.id()).order()));
            VfxCatalog.Kill on = VfxCatalog.kill(equipped);
            if (on != null) {
                String desc = killMeta(on.id()).desc();
                b.header("Equipado: " + on.name() + (desc.isEmpty() ? "." : " · " + desc));
                b.footer(PadView.Btn.of("QUITAR EFECTO", "quitar:kill", PadView.RED));
            } else {
                b.header("Elige el efecto que sale al matar. Gratis.");
            }
            b.gallery();
            for (VfxCatalog.Kill k : kills) {
                FxMeta m = killMeta(k.id());
                boolean sel = on != null && k.id().equals(on.id());
                b.cell(new PadView.Cell(new ItemStack(Items.NETHER_STAR), k.name(), m.color(), sel ? "" : "kill:" + k.id(), sel,
                        sel ? "EQUIPADO" : m.cat().isEmpty() ? "EQUIPAR" : m.cat(), -1, "", sel ? PadView.TONE_GREEN : PadView.TONE_BLUE)
                        .image(PadView.padImage("fx/kill_" + k.id())));
            }
            if (kills.isEmpty()) b.empty("Sin efectos.");
        }

        /**
         * SKILLS: las clases que tiene (las compradas en la web y, si el staff le dio otra, esa), con ACTIVAR o
         * DESACTIVAR; la activa, con sus skills y la tecla de cada una.
         */
        private static void skillsTab(ServerPlayer player, PadView.Builder b) {
            UUID uuid = player.getUUID();
            String active = SkillServer.classOf(uuid);
            List<String> mine = new ArrayList<>(SkillServer.ownedOf(uuid));
            if (active != null && !mine.contains(active)) mine.add(0, active);
            if (mine.isEmpty()) {
                if (!SkillServer.ownedKnown(uuid) && TFBridge.connected()) {
                    b.empty("Cargando tus clases…");
                } else {
                    b.empty("Aún no tienes clases de skills. Consíguelas en la tienda web: " + TFBridge.storeHost() + " (pestaña Skills).");
                }
                return;
            }
            // 1.4: la clase activa arriba en grande (su icono, su papel y DESACTIVAR); debajo sus skills con el icono de
            // cada una, lo que hace, su recarga y su tecla; y abajo las demás clases que tienes, con su icono y ACTIVAR
            SkillDefs.ClassDef cur = active == null ? null : SkillDefs.get(active);
            String[] keys = {"5", "6", "7", "8", "9", "0"};
            if (cur != null) {
                FxMeta m = classMeta(cur.id);
                List<String> lines = new ArrayList<>();
                if (!cur.role.isEmpty()) lines.add(cur.role);
                lines.add(cur.bow() ? "Elige skill con 5-0: sale al disparar con tu arco." : "Elige skill con 5-0 y úsala con clic izquierdo.");
                b.hero(new PadView.Row(new ItemStack(Items.ENCHANTED_BOOK), cur.name, m.color(), lines, -1, "ACTIVA",
                        PadView.Btn.of("DESACTIVAR", "clase:-", PadView.RED), null).image(PadView.padImage("fx/class_" + cur.id)));
                List<SkillDefs.SkillDef> acts = cur.actives();
                int titleColor = readable(m.color());
                for (int i = 0; i < acts.size() && i < keys.length; i++) {
                    SkillDefs.SkillDef s = acts.get(i);
                    List<String> about = new ArrayList<>();
                    String cd = s.cooldown() > 0 ? "Recarga " + (s.cooldown() == Math.floor(s.cooldown()) ? String.valueOf((long) s.cooldown())
                            : String.valueOf(s.cooldown()).replace('.', ',')) + " s" : "";
                    String kind = i == cur.basicSlot() ? "Ataque básico" : "";
                    String head = kind.isEmpty() ? cd : cd.isEmpty() ? kind : kind + " · " + cd;
                    if (!s.desc().isEmpty()) about.add(head.isEmpty() ? s.desc() : head + " · " + s.desc());
                    else if (!head.isEmpty()) about.add(head);
                    b.row(new PadView.Row(new ItemStack(Items.ENCHANTED_BOOK), s.name(), titleColor, about, -1, "TECLA " + keys[i], null, null)
                            .image(cur.icons.getOrDefault(s.id(), "")));
                }
            } else {
                b.header("Una activa a la vez: elige la tuya. Luego, skill con 5-0 y clic izquierdo.");
            }
            boolean others = false;
            for (String id : mine) {
                SkillDefs.ClassDef def = SkillDefs.get(id);
                if (def == null || id.equals(active)) continue;
                if (!others && cur != null) b.text("TUS OTRAS CLASES", TEXT, List.of());
                others = true;
                FxMeta m = classMeta(id);
                List<String> lines = new ArrayList<>();
                if (!def.role.isEmpty()) lines.add(def.role);
                if (!m.cat().isEmpty()) lines.add(m.cat() + " · " + def.actives().size() + " skills");
                b.row(new PadView.Row(new ItemStack(Items.ENCHANTED_BOOK), def.name, readable(m.color()), lines, -1, "",
                        PadView.Btn.of("ACTIVAR", "clase:" + id, PadView.GREEN), null).image(PadView.padImage("fx/class_" + id)));
            }
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            if (a.length < 2) return null;
            switch (a[0]) {
                case "kill" -> {
                    VfxCatalog.Kill k = VfxCatalog.kill(a[1]);
                    if (k == null) return null;
                    VfxServer.setFromPad(player, true, a[1]);
                    TFBridge.padVfx(player.getUUID(), a[0], a[1]);
                    TFPadNet.notice(player, "Efecto equipado: " + k.name() + ".");
                }
                case "quitar" -> {
                    if (!a[1].equals("kill")) return null;
                    VfxServer.setFromPad(player, true, null);
                    TFBridge.padVfx(player.getUUID(), "kill", null);
                }
                case "clase" -> {
                    String cls = a[1].equals("-") ? null : a[1];
                    if (!SkillServer.chooseFromPad(player, cls)) {
                        TFPadNet.notice(player, "Esa clase no es tuya.");
                        return null;
                    }
                    TFPadNet.notice(player, cls == null ? "Clase desactivada." : "Clase activada: " + SkillDefs.get(cls).name + ".");
                }
                default -> {
                }
            }
            return null;
        }
    }
}
