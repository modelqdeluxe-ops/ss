/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

/**
 * (1.3.171, el dueño: «en la música de las protes quiero que pongas tipo el reproductor, con la barra de progreso de la
 * canción, el volumen, que se repita indefinidamente, todo eso; esas opciones las quiero en la pestaña de música de la
 * protección») El reproductor de la música de una protección (Protección › la zona › MÚSICA), con los datos de la app
 * «zonamusica» del servidor (pad/server/PadZoneMusic). Como el de la app Música:
 * <ul>
 *   <li>La canción que suena en la zona (el vinilo gira mientras suena), quién la puso, y la barra de progreso que avanza
 *   sola: se pulsa o se arrastra para saltar a cualquier punto.</li>
 *   <li>Desde el principio, −10 s, play / pausa, +10 s y REPETIR: siempre (en bucle, sin fin) o una vez (al acabar se
 *   calla hasta darle otra vez a play).</li>
 *   <li>El volumen de la zona (barra que se arrastra, 0 a 100 %): el que oyen todos dentro.</li>
 *   <li>CAMBIAR CANCIÓN (la app Música, eligiendo para esta zona) y QUITAR (dos veces: «¿SEGURO?»).</li>
 * </ul>
 * Lo controla el dueño de la zona (o el staff); los demás lo ven sin poder tocar. Lo que se cambia lo oye al momento
 * todo el que está dentro de la zona.
 */
final class PadZonePlayerPage extends PadDataPage {
    private static final int CARD_TOP = 0xFF262E66, CARD_BOTTOM = 0xFF141A3C, GOLD = 0xFFF6B628, GOLD_LIGHT = 0xFFFFEC96;
    private static final int LAVENDER = 0xFFB8C2F0, GREEN = 0xFF7CF0B0, BAR_BG = 0xFF39407A, INK = 0xFF0C1030;

    private final String zone, zoneName;
    private int ticks;
    private boolean draggingSeek, draggingVolume;
    private long seekPreview = -1;
    private int volumePreview = -1;
    private long confirmQuit;
    private int barX, barY, barW, volX, volY, volW;

    PadZonePlayerPage(TFPadScreen pad, String zone, String zoneName) {
        super(pad, "zonamusica");
        this.zone = zone;
        this.zoneName = zoneName;
        send("zona:" + zone);
    }

    @Override
    String title() {
        return "MÚSICA DE LA ZONA";
    }

    @Override
    PadPage backTo() {
        pad.openApp("protecciones", "z:" + zone + ":a");
        return pad.page();
    }

    @Override
    void received(JsonObject o) {
        // lo que se arrastró ya lo tiene el servidor: se ve lo suyo (si no, la barra volvería atrás un instante)
        if (!draggingSeek) seekPreview = -1;
        if (!draggingVolume) volumePreview = -1;
    }

    @Override
    void tick() {
        // por si otro (el staff) la cambia: cada 5 s, los datos al día
        if (++ticks % 100 == 0 && !waiting) send("refrescar");
    }

    /** Para la prueba en el juego: pulsa ese botón (pausa, ir:ms, vol:n, repetir, inicio, saltar:±ms). */
    void actForTest(String action) {
        send(action);
    }

    private boolean has(String key) {
        return data != null && data.has(key) && data.get(key).getAsBoolean();
    }

    /** Por dónde va ahora (lo que dijo el servidor más lo que ha pasado desde entonces). */
    private long position() {
        long dur = num(data, "duracion", 0), pos = num(data, "pos", 0);
        if (has("sonando")) pos += System.currentTimeMillis() - dataAt;
        if (dur > 0) pos = has("repetir") ? Math.floorMod(pos, dur) : Math.min(pos, dur);
        return Math.max(0, pos);
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        if (loading(g)) return;
        hits.clear();
        int acc = PadSkin.accent("protecciones");
        PadSkin.window(g, X, Y, W, H, acc);
        String name = str(data, "nombre", zoneName);
        boolean can = has("puede");
        // la tarjeta oscura del reproductor
        int cx = X + 6, cy = Y + 5, cw = W - 12, ch = H - 31;
        g.fill(cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, INK);
        g.fillGradient(cx, cy, cx + cw, cy + ch, CARD_TOP, CARD_BOTTOM);
        PadUi.text(g, PadUi.fitEnd("Música de " + name, cw - 8), cx + 5, cy + 4, LAVENDER);
        if (!has("hay")) {
            String a = "Esta zona no tiene música.", b = can ? "Elige una canción de tu app Música: de un link o de tu carpeta."
                    : "Solo el dueño de la zona le pone música.";
            PadUi.text(g, a, cx + (cw - PadUi.font().width(a)) / 2, cy + ch / 2 - 8, 0xFFFFFFFF);
            PadUi.text(g, PadUi.fitEnd(b, cw - 10), cx + Math.max(5, (cw - PadUi.font().width(b)) / 2), cy + ch / 2 + 4, LAVENDER);
            bottom(g, mx, my, can, false);
            return;
        }
        boolean playing = has("sonando"), repeat = has("repetir");
        long dur = num(data, "duracion", 0);
        long pos = seekPreview >= 0 ? seekPreview : position();
        // el vinilo (gira mientras suena)
        int r = 17, vx = cx + 6 + r, vy = cy + 16 + r;
        PadMusicPage.disc(g, vx, vy, r + 1, INK);
        PadMusicPage.disc(g, vx, vy, r, 0xFF1A1A22);
        PadMusicPage.ring(g, vx, vy, r - 4, 0xFF2C2C38);
        PadMusicPage.ring(g, vx, vy, r - 8, 0xFF2C2C38);
        PadMusicPage.disc(g, vx, vy, 6, GOLD);
        double ang = playing ? System.currentTimeMillis() / 600.0 : 0.6;
        int hx = vx + (int) Math.round(Math.cos(ang) * (r - 3)), hy = vy + (int) Math.round(Math.sin(ang) * (r - 3));
        g.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xCCFFFFFF);
        PadMusicPage.disc(g, vx, vy, 1, INK);
        // título, artista y quién la puso
        int tx = vx + r + 8, tw = cx + cw - 6 - tx;
        g.drawString(PadUi.font(), PadUi.fitEnd(str(data, "titulo", "?"), tw), tx, cy + 15, 0xFFFFFFFF, true);
        String artist = str(data, "artista", "");
        String by = str(data, "por", "");
        PadUi.text(g, PadUi.fitEnd((artist.isEmpty() ? "" : artist + " · ") + (by.isEmpty() ? "" : "la puso " + by), tw), tx, cy + 26, LAVENDER);
        String state = has("acabo") ? "TERMINÓ" : has("pausa") ? "EN PAUSA" : "SONANDO EN LA ZONA";
        PadUi.text(g, state, tx, cy + 36, has("acabo") || has("pausa") ? GOLD_LIGHT : GREEN);
        // la barra de progreso (se pulsa o se arrastra)
        barX = tx;
        barY = cy + 48;
        barW = tw;
        float f = dur > 0 ? Math.min(1F, pos / (float) dur) : 0F;
        boolean barHover = can && PadUi.inside(mx, my, barX - 2, barY - 4, barW + 4, 11);
        PadUi.box(g, barX, barY, barW, 5, INK);
        g.fill(barX + 1, barY + 1, barX + barW - 1, barY + 4, BAR_BG);
        int fw = Math.round((barW - 2) * f);
        if (fw > 0) {
            g.fillGradient(barX + 1, barY + 1, barX + 1 + fw, barY + 4, 0xFFFFC94A, 0xFFFF8A3C);
            g.fill(barX + 1, barY + 1, barX + 1 + fw, barY + 2, 0x66FFFFFF);
        }
        int kx = barX + 1 + fw, ky = barY + 2;
        PadMusicPage.disc(g, kx, ky, barHover || draggingSeek ? 4 : 3, INK);
        PadMusicPage.disc(g, kx, ky, barHover || draggingSeek ? 3 : 2, barHover || draggingSeek ? 0xFFFFFFFF : GOLD_LIGHT);
        if (barHover) pad.hover("§seek");
        String left = PadMusicPage.time(pos), right = dur > 0 ? PadMusicPage.time(dur) : "--:--";
        PadUi.text(g, left, barX, barY + 8, LAVENDER);
        PadUi.text(g, right, barX + barW - PadUi.font().width(right), barY + 8, LAVENDER);
        if (can) hits.add(new Hit(barX - 2, barY - 4, barW + 4, 11, () -> {
            draggingSeek = true;
            seekPreview = seekAt(mx, dur);
        }));
        // los controles: desde el principio, −10 s, play/pausa, +10 s, repetir
        int ctlY = cy + ch - 10;
        int x = cx + 8;
        x = control(g, mx, my, x, ctlY, PadMusicPage.PREV, "inicio", "Desde el principio", can) + 5;
        x = textControl(g, mx, my, x, ctlY, "−10", "saltar:-10000", can) + 5;
        int pr = 8;
        boolean pHover = can && PadUi.inside(mx, my, x, ctlY - pr, pr * 2, pr * 2);
        if (pHover) pad.hover("§play");
        if (playing) PadMusicPage.disc(g, x + pr, ctlY, pr + 2, 0x33FFD84A);
        PadMusicPage.disc(g, x + pr, ctlY, pr + 1, INK);
        PadMusicPage.disc(g, x + pr, ctlY, pr, !can ? 0xFF7C7C90 : pHover ? 0xFFFFD36A : GOLD);
        PadMusicPage.drawGlyphCentered(g, playing ? PadMusicPage.PAUSE : PadMusicPage.PLAY, x + pr + (playing ? 0 : 1), ctlY, 0xFF1A2350);
        if (can) hits.add(new Hit(x, ctlY - pr, pr * 2, pr * 2, () -> act("pausa")));
        x += pr * 2 + 5;
        x = textControl(g, mx, my, x, ctlY, "+10", "saltar:10000", can) + 7;
        // repetir: en bucle (siempre) o una vez
        boolean rHover = can && PadUi.inside(mx, my, x, ctlY - 7, 58, 14);
        if (rHover) pad.hover("§repetir");
        PadUi.box(g, x, ctlY - 7, 58, 14, repeat ? 0xFF2EB87A : rHover ? 0x88FFFFFF : 0x44FFFFFF);
        g.fill(x + 1, ctlY - 6, x + 57, ctlY + 6, repeat ? 0xFF1B4E3E : 0xFF262E66);
        PadMusicPage.drawGlyphCentered(g, PadMusicPage.REPEAT, x + 8, ctlY, repeat ? GREEN : LAVENDER);
        PadUi.text(g, repeat ? "SIEMPRE" : "UNA VEZ", x + 16, ctlY - 4, repeat ? GREEN : LAVENDER);
        if (can) hits.add(new Hit(x, ctlY - 7, 58, 14, () -> act("repetir")));
        x += 58 + 8;
        // el volumen: el altavoz y la barra (se arrastra)
        int vol = volumePreview >= 0 ? volumePreview : (int) num(data, "volumen", 80);
        PadMusicPage.drawGlyphCentered(g, vol <= 0 ? PadMusicPage.MUTE : vol < 50 ? PadMusicPage.SPEAKER_LOW : PadMusicPage.SPEAKER, x + 5, ctlY, LAVENDER);
        volX = x + 12;
        volY = ctlY - 2;
        String pct = vol + "%";
        volW = Math.max(20, cx + cw - 8 - PadUi.font().width("100%") - 4 - volX);
        boolean vHover = can && PadUi.inside(mx, my, volX - 2, volY - 4, volW + 4, 11);
        if (vHover) pad.hover("§vol");
        PadUi.box(g, volX, volY, volW, 5, INK);
        g.fill(volX + 1, volY + 1, volX + volW - 1, volY + 4, BAR_BG);
        int vw = Math.round((volW - 2) * vol / 100F);
        if (vw > 0) g.fillGradient(volX + 1, volY + 1, volX + 1 + vw, volY + 4, 0xFF8CF0C8, 0xFF2EB87A);
        PadMusicPage.disc(g, volX + 1 + vw, volY + 2, vHover || draggingVolume ? 3 : 2, vHover || draggingVolume ? 0xFFFFFFFF : GOLD_LIGHT);
        PadUi.text(g, pct, volX + volW + 4, volY - 2, 0xFFFFFFFF);
        if (can) hits.add(new Hit(volX - 2, volY - 4, volW + 4, 11, () -> {
            draggingVolume = true;
            volumePreview = volumeAt(mx);
        }));
        bottom(g, mx, my, can, true);
    }

    /** Abajo: ATRÁS, CAMBIAR / ELEGIR CANCIÓN y QUITAR. */
    private void bottom(GuiGraphics g, double mx, double my, boolean can, boolean has) {
        int y = Y + H - 20, x = X + 8;
        x = button(g, mx, my, x, y, "ATRÁS", PadView.BLUE, true, () -> pad.back()) + 4;
        if (!can) {
            PadUi.text(g, "Solo el dueño de la zona la controla.", x + 4, y + 4, PadUi.MUTED);
            return;
        }
        String pick = has ? "CAMBIAR CANCIÓN" : "ELEGIR CANCIÓN";
        x = button(g, mx, my, x, y, pick, PadView.GREEN, true, () -> {
            PadMusicPage.zoneFor = zone;
            PadMusicPage.zoneName = str(data, "nombre", zoneName);
            PadMusicPage.zoneAt = System.currentTimeMillis();
            pad.openApp("musica");
        }) + 4;
        if (has) {
            boolean sure = System.currentTimeMillis() - confirmQuit < 4000;
            button(g, mx, my, x, y, sure ? "¿SEGURO?" : "QUITAR", PadView.RED, true, () -> {
                if (System.currentTimeMillis() - confirmQuit < 4000) {
                    confirmQuit = 0;
                    act("quitar");
                } else {
                    confirmQuit = System.currentTimeMillis();
                }
            });
        }
    }

    private int button(GuiGraphics g, double mx, double my, int x, int y, String label, int style, boolean enabled, Runnable action) {
        int w = PadUi.buttonWidth(label);
        boolean hover = PadUi.inside(mx, my, x, y, w, 16);
        if (hover) pad.hover("§b" + label);
        PadSkin.button(g, x, y, w, label, style, hover, enabled, false);
        if (enabled) hits.add(new Hit(x, y, w, 16, action));
        return x + w;
    }

    /** Un botón redondo con un dibujito; devuelve dónde acaba. */
    private int control(GuiGraphics g, double mx, double my, int x, int cy, int[][] glyph, String action, String id, boolean can) {
        int d = 14, r = d / 2;
        boolean hover = can && PadUi.inside(mx, my, x, cy - r, d, d);
        if (hover) pad.hover("§c" + id);
        PadMusicPage.disc(g, x + r, cy, r, hover ? 0x44FFFFFF : 0x22FFFFFF);
        PadMusicPage.drawGlyphCentered(g, glyph, x + r, cy, !can ? 0xFF7C7C90 : hover ? GOLD_LIGHT : 0xFFFFFFFF);
        if (can) hits.add(new Hit(x, cy - r, d, d, () -> act(action)));
        return x + d;
    }

    /** Un botón redondo con texto (−10 / +10). */
    private int textControl(GuiGraphics g, double mx, double my, int x, int cy, String label, String action, boolean can) {
        int d = 16, r = d / 2;
        boolean hover = can && PadUi.inside(mx, my, x, cy - r, d, d);
        if (hover) pad.hover("§t" + label);
        PadMusicPage.disc(g, x + r, cy, r, hover ? 0x44FFFFFF : 0x22FFFFFF);
        PadUi.text(g, label, x + r - PadUi.font().width(label) / 2, cy - 4, !can ? 0xFF7C7C90 : hover ? GOLD_LIGHT : 0xFFFFFFFF);
        if (can) hits.add(new Hit(x, cy - r, d, d, () -> act(action)));
        return x + d;
    }

    private void act(String action) {
        pad.sound("tab", 0.5F);
        send(action);
    }

    private long seekAt(double mx, long dur) {
        double f = Math.max(0, Math.min(1, (mx - barX - 1) / Math.max(1.0, barW - 2)));
        return Math.round(f * Math.max(0, dur));
    }

    private int volumeAt(double mx) {
        double f = Math.max(0, Math.min(1, (mx - volX - 1) / Math.max(1.0, volW - 2)));
        return (int) Math.round(f * 100);
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        if (draggingSeek) {
            seekPreview = seekAt(mx, num(data, "duracion", 0));
            return true;
        }
        if (draggingVolume) {
            volumePreview = volumeAt(mx);
            return true;
        }
        return false;
    }

    @Override
    void release(double mx, double my, int button) {
        boolean seek = draggingSeek && seekPreview >= 0, vol = draggingVolume && volumePreview >= 0;
        draggingSeek = false;
        draggingVolume = false;
        if (seek) act("ir:" + seekPreview);
        if (vol) act("vol:" + volumePreview);
    }
}
