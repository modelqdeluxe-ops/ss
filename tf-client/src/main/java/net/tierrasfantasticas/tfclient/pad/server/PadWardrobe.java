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
    private static final String[] LABELS = {"CABEZA", "PECHO", "PIERNAS", "PIES", "ESPALDA"};

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
            int slot = 0;
            for (int i = 0; i < TFWardrobe.SLOTS.length; i++) if (TFWardrobe.SLOTS[i].equals(tab)) slot = i;
            String key = TFWardrobe.SLOTS[slot];
            PadServer.put(player, "armario.tab", key);
            PadView.Builder b = PadView.of("armario");
            String[] worn = TFWardrobe.worn(player.getUUID());
            for (int i = 0; i < LABELS.length; i++) b.tab(TFWardrobe.SLOTS[i], worn[i].isEmpty() ? LABELS[i] : LABELS[i] + " ·");
            b.selected(key);
            TFBridge.padWant(player.getUUID());
            List<Piece> owned = OWNED.get(player.getUUID());
            if (owned == null) {
                return b.empty(TFBridge.connected() ? "Cargando…" : "Armario no disponible.").build();
            }
            b.header("Solo estético.");
            int n = 0;
            for (Piece p : owned) {
                if (!p.slot.equals(key)) continue;
                n++;
                boolean on = worn[slot].equals(p.key());
                List<String> lines = new ArrayList<>();
                if (!p.from.isEmpty()) lines.add("De " + p.from + ".");
                PadView.Btn btn = on ? PadView.Btn.of("QUITAR", "quitar:" + slot, PadView.RED) : PadView.Btn.of("PONER", "poner:" + slot + ":" + p.key(), PadView.GREEN);
                b.row(new PadView.Row(stack(p.set, p.id), p.name.isEmpty() ? p.key() : p.name, TEXT, lines, -1, on ? "PUESTO" : "", btn, null)
                        .selected(on));
            }
            if (n == 0) b.empty("No tienes nada para " + LABELS[slot].toLowerCase() + ". Los sets de los rangos y de las crates de la tienda web aparecen aquí.");
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
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
                b.header("Efecto al matar. Gratis.");
                for (VfxCatalog.Kill k : VfxCatalog.kills().values()) {
                    boolean on = k.id().equals(eq[0]);
                    b.row(new PadView.Row(new ItemStack(Items.WITHER_SKELETON_SKULL), k.name(), TEXT, List.of(), -1, on ? "EQUIPADO" : "",
                            on ? PadView.Btn.of("QUITAR", "quitar:kill", PadView.RED) : PadView.Btn.of("EQUIPAR", "kill:" + k.id(), PadView.GREEN), null)
                            .selected(on));
                }
                if (VfxCatalog.kills().isEmpty()) b.empty("Sin efectos.");
            } else {
                skillsTab(player, b);
            }
            return b.build();
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
            b.header("Una activa a la vez. Elige skill con 5-0 y úsala con clic izquierdo.");
            for (String id : mine) {
                SkillDefs.ClassDef def = SkillDefs.get(id);
                if (def == null) continue;
                boolean on = id.equals(active);
                List<String> lines = new ArrayList<>();
                if (!def.role.isEmpty()) lines.add(def.role);
                if (on) {
                    String[] keys = {"5", "6", "7", "8", "9", "0"};
                    List<SkillDefs.SkillDef> acts = def.actives();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < acts.size() && i < keys.length; i++) {
                        if (sb.length() > 0) sb.append(" · ");
                        sb.append(keys[i]).append(" ").append(acts.get(i).name());
                    }
                    lines.add(sb.toString());
                    if (def.bow()) lines.add("Clase de arco: la skill sale al disparar con tu arco.");
                }
                PadView.Btn btn = on ? PadView.Btn.of("DESACTIVAR", "clase:-", PadView.RED) : PadView.Btn.of("ACTIVAR", "clase:" + id, PadView.GREEN);
                b.row(new PadView.Row(new ItemStack(Items.ENCHANTED_BOOK), def.name, TEXT, lines, -1, on ? "ACTIVA" : "", btn, null)
                        .selected(on));
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
