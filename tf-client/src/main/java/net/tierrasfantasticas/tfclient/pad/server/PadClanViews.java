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
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.util.TFJson;

/** (1.3.156) Las pantallas nuevas de la app de clanes: la ficha del clan, las misiones y el estilo (insignia y color). */
final class PadClanViews {
    private PadClanViews() {}

    /** «3 d 4 h», «5 h 12 min», «8 min». */
    static String until(long ms) {
        long m = Math.max(0, ms / 60000), h = m / 60, d = h / 24;
        if (d > 0) return d + " d " + (h % 24) + " h";
        if (h > 0) return h + " h " + (m % 60) + " min";
        return Math.max(1, m) + " min";
    }

    static String fmt(double v) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-ES")).format(Math.round(v));
    }

    /**
     * La ficha del clan, arriba de MIEMBROS (1.3.158: MISIONES tiene la suya): su insignia, el nombre y la etiqueta, el nivel con su barra, el
     * prestigio y los miembros; y, al líder y los colíderes, las Fantastic Clan Coins del clan.
     */
    static PadView.Row hero(JsonObject c, UUID me) {
        int lvl = PadClanProgress.level(c);
        long xp = PadClanProgress.xp(c), need = PadClanProgress.xpFor(lvl);
        boolean max = lvl >= PadClanProgress.MAX_LEVEL;
        int n = PadClans.members(c).size();
        List<String> lines = new ArrayList<>(List.of(
                "Nivel " + lvl + (max ? " (el máximo)" : " · " + fmt(xp) + " de " + fmt(need) + " XP para el " + (lvl + 1)),
                "Prestigio " + fmt(PadClanProgress.prestige(c)) + " · " + n + "/" + PadClanProgress.maxMembers(c) + " miembros"));
        // (1.3.156) los aliados, la guerra y el potenciador, si los hay
        List<String> extra = new ArrayList<>();
        for (String a : PadClans.allies(c)) extra.add("aliado de [" + TFJson.str(PadClans.clan(a), "etiqueta", "?") + "]");
        if (PadClanProgress.boosted(c)) extra.add("misiones " + PadClanConfig.boostText());
        if (!extra.isEmpty()) lines.add(String.join(" · ", extra));
        String coins = PadClanProgress.treasurer(c, me) ? PadView.clanCoins(PadClanProgress.coins(c)) : "";
        return new PadView.Row(new ItemStack(Items.WHITE_BANNER), TFJson.str(c, "nombre", "?") + " [" + TFJson.str(c, "etiqueta", "?") + "]",
                PadClans.rgbOf(c), lines, max ? 1F : (float) Math.min(1.0, xp / (double) need), coins, null, null)
                .image(PadClanProgress.badge(c).image());
    }

    /**
     * MISIONES: las 4 de la semana (meta según los miembros), con su barra, lo que dan y quién aportó más. Los aliados
     * suman la mitad de lo suyo.
     */
    static PadView missions(PadView.Builder b, ServerPlayer player, String id, JsonObject c) {
        JsonObject ms = PadClanProgress.missions(id, c);
        long day = net.tierrasfantasticas.tfclient.util.TFRotation.day();
        long left = 7 - Math.floorMod(day, 7);
        JsonArray list = ms.has("lista") ? ms.getAsJsonArray("lista") : new JsonArray();
        // (1.3.158, el dueño: «frases repetidas») arriba, lo de la semana (no la ficha del clan otra vez, que ya está en
        // MIEMBROS)
        int doneCount = 0;
        long earned = 0;
        for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            if (!TFJson.bool(o, "hecha", false)) continue;
            doneCount++;
            PadClanProgress.Mission m = PadClanProgress.byId(TFJson.str(o, "id", ""));
            if (m != null) earned += m.coins();
        }
        b.hero(new PadView.Row(new ItemStack(Items.WRITABLE_BOOK), "Misiones de la semana", PadClans.rgbOf(c),
                List.of(doneCount + " de " + list.size() + " hechas" + (earned > 0 ? " · " + earned + " Clan Coins ganadas" : "") + ".",
                        "Quedan " + left + (left == 1 ? " día" : " días") + (PadClans.allies(c).isEmpty() ? "." : "; los aliados suman " + PadClanConfig.allyText() + " de lo suyo.")),
                list.isEmpty() ? -1F : doneCount / (float) list.size(), "", null, null).image(PadClanProgress.badge(c).image()));
        b.header("Las cumplen entre todo el clan: cada uno que aporta gana 1 verde.");
        for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            PadClanProgress.Mission m = PadClanProgress.byId(TFJson.str(o, "id", ""));
            if (m == null) continue;
            double meta = TFJson.dec(o, "meta", m.amount()), got = TFJson.dec(o, "progreso", 0);
            boolean done = TFJson.bool(o, "hecha", false);
            // (las Clan Coins van en la burbuja de la derecha; la verde, en la cabecera)
            List<String> lines = List.of(m.desc() + " " + fmt(got) + " de " + fmt(meta) + ".",
                    "+" + m.xp() + " XP del clan · +" + m.prestige() + " de prestigio");
            b.row(new PadView.Row(m.icon(), m.name(), done ? 0x1E7C2C : 0x18265C, lines, (float) Math.min(1.0, got / Math.max(1, meta)),
                    done ? "HECHA" : PadView.clanCoins(m.coins()), null, null));
        }
        Map<UUID, Double> top = PadClanProgress.contributors(ms);
        if (!top.isEmpty()) {
            b.row(new PadView.Row(new ItemStack(Items.GOLDEN_HELMET), "Los que más aportan", 0xC27A10,
                    List.of("Esta semana, entre todas las misiones."), -1, "", null, null));
            int i = 0;
            for (Map.Entry<UUID, Double> e : top.entrySet()) {
                if (i++ >= 5) break;
                String name = PadStats.nameOf(e.getKey());
                b.row(new PadView.Row(PadClans.head(e.getKey(), name), i + ". " + name, 0x18265C, List.of(), -1, fmt(e.getValue()), null, null));
            }
        }
        return b.build();
    }

    /**
     * ESTILO (el líder): la etiqueta con su color y su insignia; PALETA abre la paleta de colores del pad (como la de
     * Paint: cuadro de saturación y brillo, barra de tono, los colores básicos y el código); el campo de abajo pone un
     * color escrito; y todas las insignias para elegir.
     */
    static PadView style(PadView.Builder b, JsonObject c) {
        int cur = PadClans.rgbOf(c);
        String tag = TFJson.str(c, "etiqueta", "?");
        String hex = String.format("%06X", cur);
        b.header("La etiqueta [" + tag + "] y la insignia. PALETA abre la paleta de colores.");
        b.hero(new PadView.Row(new ItemStack(Items.WHITE_BANNER), "[" + tag + "]", cur,
                List.of("Así se ve al lado de sus nombres.", "#" + hex + " · insignia: " + PadClanProgress.badge(c).name()), -1, "",
                PadView.Btn.of("PALETA", "§color:clanes:estilo:" + hex + ":" + tag, PadView.GOLD), null)
                .image(PadClanProgress.badge(c).image()));
        String now = PadClanProgress.badge(c).id();
        for (PadClanProgress.Badge bd : PadClanProgress.badges()) {
            b.cell(new PadView.Cell(new ItemStack(Items.WHITE_BANNER), bd.name(), 0x18265C, "insignia:" + bd.id(), bd.id().equals(now), "", -1, "", 0,
                    bd.image()));
        }
        b.cards();
        b.input("colhex", "Color en hex, por ejemplo #FF8800", 7, "PONER");
        return b.build();
    }

    /** El ranking de clanes del pad (pestaña CLANES): por prestigio, con su insignia, nivel y miembros. */
    static void ranking(PadView.Builder b, ServerPlayer player) {
        String mine = PadClans.clanIdOf(player.getUUID());
        List<Map.Entry<String, JsonObject>> all = PadClanProgress.byPrestige();
        int i = 0;
        for (Map.Entry<String, JsonObject> e : all) {
            if (i >= 20) break;
            JsonObject c = e.getValue();
            boolean me = e.getKey().equals(mine);
            String leader = PadStats.nameOf(UUID.fromString(TFJson.str(c, "lider", new UUID(0, 0).toString())));
            i++;
            b.row(new PadView.Row(new ItemStack(Items.WHITE_BANNER), i + ". " + TFJson.str(c, "nombre", "?") + " [" + TFJson.str(c, "etiqueta", "?") + "]"
                    + (me ? " (el tuyo)" : ""), PadClans.rgbOf(c),
                    List.of("Nivel " + PadClanProgress.level(c) + " · " + PadClans.members(c).size() + " miembros · líder " + leader),
                    -1, fmt(PadClanProgress.prestige(c)) + " prestigio", null, null).image(PadClanProgress.badge(c).image()).selected(me));
        }
        if (i == 0) b.empty("Aún no hay clanes.");
    }

    /** La lista de clanes de un JsonArray de ids (para las alianzas). */
    static List<String> ids(JsonObject c, String key) {
        List<String> out = new ArrayList<>();
        if (c.has(key) && c.get(key).isJsonArray()) for (JsonElement e : c.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }
}
