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
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;

/**
 * (1.3.156, el dueño: «alianza para compartir progreso de misiones y avanzar más rápido en el prestigio del clan»)
 * Dos clanes aliados se ayudan: lo que hace cada uno suma la mitad a las misiones del otro (PadClanProgress.ALLY_SHARE).
 * El líder o un colíder propone; el líder o un colíder del otro acepta o no; cualquiera de los dos la rompe. Como mucho
 * {@link #MAX} aliados por clan; los aliados no se declaran la guerra.
 * Datos en el clan (clanes.json): «aliados» [ids] en los dos y «propuestasAlianza» [ids] en el que recibe.
 */
final class PadClanAlliances {
    static final int MAX = 2;

    private PadClanAlliances() {}

    static List<String> proposals(JsonObject c) {
        return PadClanViews.ids(c, "propuestasAlianza");
    }

    private static void setIds(JsonObject c, String key, List<String> ids) {
        JsonArray a = new JsonArray();
        for (String s : ids) a.add(s);
        c.add(key, a);
        PadClans.STORE_IMPL.changed();
    }

    /** Las filas de alianzas de la pestaña GUERRA: los aliados (ROMPER) y las propuestas (ACEPTAR / NO). */
    static void rows(PadView.Builder b, ServerPlayer player, String id, JsonObject c, boolean officer) {
        for (String a : PadClans.allies(c)) {
            JsonObject ac = PadClans.clan(a);
            boolean sure = PadServer.confirming(player, "alianza.romper:" + a);
            b.row(new PadView.Row(new ItemStack(Items.EMERALD), "Aliados: " + PadClanWar.label(ac), 0x1E7C2C,
                    List.of("Lo que hacen suma la mitad a vuestras misiones (y lo vuestro a las suyas)."), -1, "",
                    officer ? PadView.Btn.of(sure ? "¿SEGURO?" : "ROMPER", "alianza.romper:" + a, PadView.RED) : null, null)
                    .image(PadClanProgress.badge(ac).image()));
        }
        for (String p : proposals(c)) {
            JsonObject pc = PadClans.clan(p);
            if (pc == null) continue;
            b.row(new PadView.Row(new ItemStack(Items.EMERALD), PadClanWar.label(pc) + " os propone una alianza", 0x1E7C2C,
                    List.of("Os ayudáis con las misiones (la mitad de lo de cada uno). Máximo " + MAX + " aliados."), -1, "",
                    officer ? PadView.Btn.of("ACEPTAR", "alianza.aceptar:" + p, PadView.GREEN) : null,
                    officer ? PadView.Btn.of("NO", "alianza.rechazar:" + p, PadView.GRAY) : null).image(PadClanProgress.badge(pc).image()));
        }
    }

    /** Las acciones «alianza.…». */
    static String action(ServerPlayer player, String id, JsonObject c, String action) {
        if (!PadClanProgress.treasurer(c, player.getUUID())) return null;
        String verb = action.contains(":") ? action.substring(0, action.indexOf(':')) : action;
        String other = action.contains(":") ? action.substring(action.indexOf(':') + 1) : "";
        JsonObject oc = PadClans.clan(other);
        if (oc == null || other.equals(id)) return "guerra";
        List<String> mine = PadClans.allies(c), theirs = PadClans.allies(oc);
        switch (verb) {
            case "alianza.proponer" -> {
                if (mine.contains(other)) return "guerra";
                if (mine.size() >= MAX || theirs.size() >= MAX) {
                    TFPadNet.notice(player, "Como mucho " + MAX + " aliados por clan.");
                    return "guerra";
                }
                if (PadClanWar.active(id) != null && other.equals(otherSide(id))) {
                    TFPadNet.notice(player, "Estáis en guerra con ese clan.");
                    return "guerra";
                }
                // si ellos ya nos la habían propuesto, se acepta directamente
                if (proposals(c).contains(other)) return action(player, id, c, "alianza.aceptar:" + other);
                List<String> p = proposals(oc);
                if (!p.contains(id)) {
                    p.add(id);
                    setIds(oc, "propuestasAlianza", p);
                }
                PadClanProgress.tell(PadServer.server(), oc, PadClanWar.label(c) + " os propone una alianza: pad › Clanes › GUERRA.", ChatFormatting.GREEN);
                TFPadNet.notice(player, "Alianza propuesta a " + PadClanWar.label(oc) + ".");
            }
            case "alianza.aceptar" -> {
                List<String> p = proposals(c);
                if (!p.contains(other)) return "guerra";
                if (mine.size() >= MAX || theirs.size() >= MAX) {
                    TFPadNet.notice(player, "Como mucho " + MAX + " aliados por clan.");
                    return "guerra";
                }
                p.remove(other);
                setIds(c, "propuestasAlianza", p);
                List<String> a = PadClanViews.ids(c, "aliados"), bb = PadClanViews.ids(oc, "aliados");
                if (!a.contains(other)) a.add(other);
                if (!bb.contains(id)) bb.add(id);
                setIds(c, "aliados", a);
                setIds(oc, "aliados", bb);
                String text = "¡Alianza entre " + PadClanWar.label(c) + " y " + PadClanWar.label(oc) + "! Lo de cada uno suma la mitad a las misiones del otro.";
                PadClanProgress.tell(PadServer.server(), c, text, ChatFormatting.GREEN);
                PadClanProgress.tell(PadServer.server(), oc, text, ChatFormatting.GREEN);
            }
            case "alianza.rechazar" -> {
                List<String> p = proposals(c);
                p.remove(other);
                setIds(c, "propuestasAlianza", p);
            }
            case "alianza.romper" -> {
                if (!mine.contains(other) || !PadServer.confirm(player, action)) return "guerra";
                List<String> a = PadClanViews.ids(c, "aliados"), bb = PadClanViews.ids(oc, "aliados");
                a.remove(other);
                bb.remove(id);
                setIds(c, "aliados", a);
                setIds(oc, "aliados", bb);
                String text = "Se rompió la alianza entre " + PadClanWar.label(c) + " y " + PadClanWar.label(oc) + ".";
                PadClanProgress.tell(PadServer.server(), c, text, ChatFormatting.GRAY);
                PadClanProgress.tell(PadServer.server(), oc, text, ChatFormatting.GRAY);
            }
            default -> {
                return null;
            }
        }
        return "guerra";
    }

    private static String otherSide(String id) {
        JsonObject w = PadClanWar.active(id);
        if (w == null) return "";
        return PadClanWar.side(w, id).equals("A") ? net.tierrasfantasticas.tfclient.util.TFJson.str(w, "b", "")
                : net.tierrasfantasticas.tfclient.util.TFJson.str(w, "a", "");
    }

    /** Al disolver un clan: se va de las alianzas y propuestas de los demás. */
    static void clanGone(String id) {
        for (var e : PadClans.clans().entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject oc = e.getValue().getAsJsonObject();
            List<String> a = PadClanViews.ids(oc, "aliados"), p = PadClanViews.ids(oc, "propuestasAlianza");
            if (a.remove(id)) setIds(oc, "aliados", a);
            if (p.remove(id)) setIds(oc, "propuestasAlianza", p);
        }
    }

    /** Para la prueba en el juego. */
    static String testAct(ServerPlayer player, String action) {
        String id = PadClans.clanIdOf(player.getUUID());
        return id == null ? null : action(player, id, PadClans.clan(id), action);
    }
}
