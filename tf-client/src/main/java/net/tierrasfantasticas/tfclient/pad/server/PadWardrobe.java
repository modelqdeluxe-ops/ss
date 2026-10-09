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
import net.tierrasfantasticas.tfclient.util.TFJson;
import net.tierrasfantasticas.tfclient.vfx.VfxCatalog;
import net.tierrasfantasticas.tfclient.vfx.VfxServer;

/**
 * Armario y Efectos dentro del pad. Armario: lo que tienes de tus compras y de tu rango (lo dice la web por el puente)
 * por hueco (CABEZA, PECHO, PIERNAS, PIES, ESPALDA) con PONER / QUITAR; solo cambia cómo te ven, no la armadura de
 * verdad. Efectos: los efectos de kill y los paquetes de skills (gratis) con EQUIPAR / QUITAR. Los dos se aplican al
 * momento en el servidor y se guardan en la web en la siguiente consulta del puente.
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
            boolean packs = tab.equals("skills");
            String[] eq = VfxServer.equipped(player.getUUID());
            PadView.Builder b = PadView.of("efectos").tab("", "AL MATAR").tab("skills", "SKILLS").selected(packs ? "skills" : "");
            if (!packs) {
                b.header("Efecto al matar. Gratis.");
                for (VfxCatalog.Kill k : VfxCatalog.kills().values()) {
                    boolean on = k.id().equals(eq[0]);
                    b.row(new PadView.Row(new ItemStack(Items.WITHER_SKELETON_SKULL), k.name(), TEXT, List.of(), -1, on ? "EQUIPADO" : "",
                            on ? PadView.Btn.of("QUITAR", "quitar:kill", PadView.RED) : PadView.Btn.of("EQUIPAR", "kill:" + k.id(), PadView.GREEN), null)
                            .selected(on));
                }
                if (VfxCatalog.kills().isEmpty()) b.empty("Sin efectos.");
            } else {
                b.header("Skills de combate.");
                for (VfxCatalog.Pack p : VfxCatalog.packs().values()) {
                    boolean on = p.id().equals(eq[1]);
                    List<String> names = new ArrayList<>();
                    for (VfxCatalog.Skill s : p.skills()) names.add(s.name());
                    b.row(new PadView.Row(new ItemStack(Items.BLAZE_POWDER), p.name(), TEXT, List.of(String.join(" · ", names)), -1, on ? "EQUIPADO" : "",
                            on ? PadView.Btn.of("QUITAR", "quitar:pack", PadView.RED) : PadView.Btn.of("EQUIPAR", "pack:" + p.id(), PadView.GREEN), null)
                            .selected(on));
                }
                if (VfxCatalog.packs().isEmpty()) b.empty("Sin paquetes.");
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            if (a.length < 2) return null;
            switch (a[0]) {
                case "kill", "pack" -> {
                    boolean kill = a[0].equals("kill");
                    String name = kill ? (VfxCatalog.kill(a[1]) == null ? null : VfxCatalog.kill(a[1]).name())
                            : (VfxCatalog.pack(a[1]) == null ? null : VfxCatalog.pack(a[1]).name());
                    if (name == null) return null;
                    VfxServer.setFromPad(player, kill, a[1]);
                    TFBridge.padVfx(player.getUUID(), a[0], a[1]);
                    TFPadNet.notice(player, (kill ? "Efecto equipado: " : "Skills equipadas: ") + name + ".");
                }
                case "quitar" -> {
                    boolean kill = a[1].equals("kill");
                    VfxServer.setFromPad(player, kill, null);
                    TFBridge.padVfx(player.getUUID(), kill ? "kill" : "pack", null);
                }
                default -> {
                }
            }
            return null;
        }
    }
}
