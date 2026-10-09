package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Títulos que se ganan jugando (misiones, cazas, exploraciones, fotos, likes…). El que te pones sale delante de tu
 * nombre: «Explorador» Pewez777 [TF] (la etiqueta es la del clan). Gratis siempre: no se venden.
 * Datos en &lt;mundo&gt;/tfclient/titulos.json (cuál llevas y cuáles ya te avisaron).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadTitles {
    /**
     * Los títulos están apagados desde la 1.3.24 (decisión del dueño: delante del nombre va el prefijo del rango y no
     * debe competir con nada). De esta clase queda lo que pone la etiqueta del clan junto al nombre.
     */
    static final boolean ENABLED = false;

    record Title(String id, String name, ChatFormatting color, String stat, long need, String how) {}

    static final List<Title> TITLES = List.of(
            new Title("novato", "Novato", ChatFormatting.GRAY, "", 0, "Lo tienes desde el principio."),
            new Title("aventurero", "Aventurero", ChatFormatting.GREEN, PadStats.MISSIONS, 25, "Completa 25 misiones"),
            new Title("cazador", "Cazador", ChatFormatting.RED, PadStats.HUNTS, 10, "Completa 10 cazas"),
            new Title("explorador", "Explorador", ChatFormatting.DARK_GREEN, PadStats.EXPLORES, 10, "Explora 10 veces"),
            new Title("viajero", "Viajero", ChatFormatting.AQUA, PadStats.TRAVELS, 25, "Viaja 25 veces"),
            new Title("fotografo", "Fotógrafo", ChatFormatting.LIGHT_PURPLE, PadStats.PHOTOS, 5, "Publica 5 fotos en Comunidad"),
            new Title("famoso", "Famoso", ChatFormatting.LIGHT_PURPLE, PadStats.LIKES, 50, "Recibe 50 likes en tus fotos"),
            new Title("comerciante", "Comerciante", ChatFormatting.GOLD, PadStats.GTS_SALES, 10, "Vende 10 cosas en el GTS"),
            new Title("fundador", "Fundador", ChatFormatting.YELLOW, PadStats.CLAN_FOUNDED, 1, "Funda un clan"),
            new Title("coleccionista", "Coleccionista", ChatFormatting.BLUE, PadStats.KITS, 30, "Reclama 30 kits"),
            new Title("estrella", "Estrella", ChatFormatting.GOLD, PadStats.LIKES, 250, "Recibe 250 likes en tus fotos"),
            new Title("leyenda", "Leyenda", ChatFormatting.DARK_PURPLE, PadStats.MISSIONS, 200, "Completa 200 misiones"));

    static final JsonStore STORE_IMPL = new JsonStore("titulos.json");
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.App APP = new App();

    private PadTitles() {}

    static boolean unlocked(UUID uuid, Title t) {
        return t.need <= 0 || PadStats.get(uuid, t.stat) >= t.need;
    }

    static Title equipped(UUID uuid) {
        if (!ENABLED) return null;
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        String id = p == null ? "" : TFJson.str(p, "titulo", "");
        for (Title t : TITLES) if (t.id.equals(id) && unlocked(uuid, t)) return t;
        return null;
    }

    public static String equippedName(UUID uuid) {
        Title t = equipped(uuid);
        return t == null ? "" : t.name;
    }

    /** Avisa de los títulos nuevos (lo llama PadStats al sumar). */
    static void check(ServerPlayer player) {
        if (!ENABLED) return;
        JsonObject p = STORE_IMPL.player(player.getUUID());
        Set<String> seen = new HashSet<>();
        if (p.has("vistos")) for (JsonElement e : p.getAsJsonArray("vistos")) seen.add(e.getAsString());
        boolean changed = false;
        for (Title t : TITLES) {
            if (t.need <= 0 || seen.contains(t.id) || !unlocked(player.getUUID(), t)) continue;
            seen.add(t.id);
            changed = true;
            player.sendSystemMessage(Component.literal("¡Nuevo título! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("«" + t.name + "»").withStyle(t.color))
                    .append(Component.literal(" — póntelo en el pad (C) → Títulos.").withStyle(ChatFormatting.YELLOW)));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.5F, 1.2F);
        }
        if (changed) {
            JsonArray a = new JsonArray();
            seen.forEach(a::add);
            p.add("vistos", a);
            STORE_IMPL.changed();
        }
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            UUID me = player.getUUID();
            Title on = equipped(me);
            PadView.Builder b = PadView.of("titulos").header(on == null ? "No llevas título. Elige uno de los que tengas."
                    : "Llevas «" + on.name + "»: sale delante de tu nombre.");
            for (Title t : TITLES) {
                boolean has = unlocked(me, t);
                long now = t.need <= 0 ? 0 : PadStats.get(me, t.stat);
                List<String> lines = new ArrayList<>();
                lines.add(has ? (t.need > 0 ? t.how + " · ¡hecho!" : t.how) : t.how + " · " + Math.min(now, t.need) + "/" + t.need);
                PadView.Btn btn = !has ? PadView.Btn.off("BLOQUEADO")
                        : on == t ? PadView.Btn.of("QUITAR", "quitar", PadView.RED) : PadView.Btn.of("PONER", "poner:" + t.id, PadView.GREEN);
                int color = has ? colorOf(t.color) : 0x7E8CA8;
                b.row(new PadView.Row(new ItemStack(Items.NAME_TAG), "«" + t.name + "»", color, lines,
                        has || t.need <= 0 ? -1 : Math.min(1F, now / (float) t.need), "", btn, null));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            JsonObject p = STORE_IMPL.player(player.getUUID());
            if (action.equals("quitar")) {
                p.remove("titulo");
            } else if (action.startsWith("poner:")) {
                String id = action.substring(6);
                for (Title t : TITLES) {
                    if (t.id.equals(id) && unlocked(player.getUUID(), t)) p.addProperty("titulo", id);
                }
            } else {
                return null;
            }
            STORE_IMPL.changed();
            player.refreshDisplayName();
            player.refreshTabListName();
            Title t = equipped(player.getUUID());
            TFPadNet.notice(player, t == null ? "Te quitaste el título." : "Ahora llevas «" + t.name + "».");
            return null;
        }
    }

    static int colorOf(ChatFormatting f) {
        Integer c = f.getColor();
        if (c == null) return 0x18265C;
        // sobre el panel blanco, los colores muy claros se oscurecen un poco
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return Math.max(r, Math.max(g, b)) > 200 ? (r * 3 / 4) << 16 | (g * 3 / 4) << 8 | (b * 3 / 4) : c;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Nombre: «Título» Nombre [TAG]
    // ---------------------------------------------------------------------------------------------------------------

    static MutableComponent decorate(UUID uuid, Component name) {
        MutableComponent out = Component.empty();
        Title t = equipped(uuid);
        if (t != null) out.append(Component.literal("«" + t.name + "» ").withStyle(t.color));
        out.append(name);
        Component tag = PadClans.tagOf(uuid);
        if (tag != null) out.append(Component.literal(" ")).append(tag);
        return out;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onName(PlayerEvent.NameFormat event) {
        if (event.getEntity().level().isClientSide || PadServer.server() == null) return;
        event.setDisplayname(decorate(event.getEntity().getUUID(), event.getDisplayname()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTabName(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity().level().isClientSide || PadServer.server() == null) return;
        UUID uuid = event.getEntity().getUUID();
        if (equipped(uuid) == null && PadClans.tagOf(uuid) == null) return;
        Component base = event.getDisplayName() != null ? event.getDisplayName() : Component.literal(event.getEntity().getGameProfile().getName());
        // con nombre propio en la lista, el cliente ya no pone el prefijo del rango: se pone aquí
        event.setDisplayName(PlayerTeam.formatNameForTeam(event.getEntity().getTeam(), decorate(uuid, base)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PadStats.name(p);
            p.refreshDisplayName();
            p.refreshTabListName();
        }
    }
}
