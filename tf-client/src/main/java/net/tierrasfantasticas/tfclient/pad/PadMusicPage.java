package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.pad.music.MusicDownloader;
import net.tierrasfantasticas.tfclient.pad.music.MusicLibrary;
import net.tierrasfantasticas.tfclient.pad.music.MusicPlayer;

/**
 * Música: un reproductor completo dentro del pad (solo lo oye el jugador).
 * <ul>
 *   <li>Izquierda, «sonando»: la portada grande con el vinilo que asoma y gira, título y artista, un ecualizador que
 *       baila con la canción de verdad, la barra para saltar a cualquier punto (se arrastra), los controles (aleatorio,
 *       anterior, play/pausa, siguiente, repetir: todas / una / no), el volumen (barra vertical; clic en el altavoz:
 *       silencio) y PORTADA para ponerle una imagen desde un link.</li>
 *   <li>Derecha, «tu música»: la biblioteca (clic: suena; la X la borra con confirmación), la descarga en curso con su
 *       barra y, abajo, el campo para pegar el link directo de una canción (MP3, OGG o WAV; los links de compartir de
 *       Google Drive y Dropbox se convierten solos).</li>
 * </ul>
 * La música sigue sonando con el pad cerrado (ver {@link MusicPlayer}). Teclas: espacio = play/pausa, ←/→ = 5 s.
 */
final class PadMusicPage extends PadPage {
    private static final int CARD_TOP = 0xFF262E66, CARD_BOTTOM = 0xFF141A3C, GOLD = 0xFFF6B628, GOLD_LIGHT = 0xFFFFEC96;
    private static final int LAVENDER = 0xFFB8C2F0, GREEN = 0xFF7CF0B0;

    private String input = "";
    private boolean focused;
    /** Modo portada: el campo pide el link de una imagen para esta canción. */
    private MusicLibrary.Track coverFor;
    private int listScroll;
    private boolean draggingSeek, draggingVolume;
    private long seekPreview = -1;
    private String confirmDelete;
    private long confirmAt;
    private float savedVolume = -1;
    private float spin;
    private long lastNs = System.nanoTime();
    private MusicDownloader.Job shownJob;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    PadMusicPage(TFPadScreen pad) {
        super(pad, "musica");
    }

    @Override
    String title() {
        return "MÚSICA";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Geometría
    // ---------------------------------------------------------------------------------------------------------------

    private int leftW() {
        return Math.max(150, Math.min(240, Math.round(W * 0.42F)));
    }

    private int barH() {
        return Math.max(3, pad.big(3));
    }

    /** Lo que ocupa todo lo de debajo de la portada: título, artista, barra, tiempos y controles. */
    private int belowCover() {
        return 4 + pad.big(9) + 2 + 13 + barH() + 3 + 10 + pad.big(22) + 4;
    }

    private int coverSize() {
        int byWidth = Math.round((leftW() - 16 - pad.big(16)) / 1.3F);
        int byHeight = H - 8 - belowCover();
        return Math.max(32, Math.min(byWidth, byHeight));
    }

    private int titleY() {
        return coverY() + coverSize() + 4;
    }

    private int artistY() {
        return titleY() + pad.big(9) + 2;
    }

    private int coverX() {
        return X + 8;
    }

    private int coverY() {
        return Y + 8;
    }

    /** La barra de progreso: x, y, ancho. */
    private int[] seekBar() {
        return new int[] {X + 8, artistY() + 13, leftW() - 16};
    }

    /** La barra del volumen (vertical, a la derecha de la portada): x, y, alto. */
    private int[] volumeBar() {
        int c = coverSize();
        int x = X + leftW() - 8 - pad.big(6);
        int top = coverY() + pad.big(16);
        return new int[] {x, top, c - pad.big(16)};
    }

    private int listX() {
        return X + leftW() + 6;
    }

    private int listW() {
        return W - leftW() - 6;
    }

    private static final int ROW = 30;

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        long ns = System.nanoTime();
        float dt = Math.min(0.1F, (ns - lastNs) / 1e9F);
        lastNs = ns;
        if (MusicPlayer.playing()) spin = (spin + dt * 160) % 360;
        checkJob();
        drawNowPlaying(g, mx, my);
        drawLibrary(g, mx, my);
    }

    /** Avisa cuando acaba una descarga (bien o mal) y la olvida. */
    private void checkJob() {
        MusicDownloader.Job j = MusicDownloader.job();
        if (j == null || !j.finished || j == shownJob) return;
        shownJob = j;
        if (j.error != null) {
            pad.showNotice(j.error);
            pad.sound("back", 0.6F);
        } else if (j.result != null) {
            pad.showNotice(j.cover ? "Portada puesta." : "Guardada: " + j.result.shownTitle());
            pad.sound("like", 0.8F);
            if (j.cover) coverFor = null;
        }
        MusicDownloader.clear();
    }

    // ---- «sonando» ------------------------------------------------------------------------------------------------

    private void drawNowPlaying(GuiGraphics g, double mx, double my) {
        int x = X, y = Y, w = leftW(), h = H;
        // tarjeta oscura con ribete de oro
        PadUi.box(g, x, y, w, h, PadUi.NAVY);
        gradient(g, x + 1, y + 1, w - 2, h - 2, CARD_TOP, CARD_BOTTOM);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF4A56A0);
        outlineRect(g, x + 2, y + 2, w - 4, h - 4, 0x55F6B628);

        MusicLibrary.Track t = MusicPlayer.current();
        int c = coverSize(), cx = coverX(), cy = coverY();
        // el vinilo asoma por la derecha de la portada y gira
        int disc = Math.round(c * 0.92F);
        float slide = t == null ? 0.12F : MusicPlayer.playing() ? 0.30F : 0.2F;
        int dcx = cx + c - disc / 2 + Math.round(c * slide), dcy = cy + c / 2;
        drawVinyl(g, dcx, dcy, disc / 2, t);
        // portada (con sombra)
        g.fill(cx + 2, cy + 2, cx + c + 2, cy + c + 2, 0x66000000);
        drawCover(g, t, cx, cy, c);
        outlineRect(g, cx - 1, cy - 1, c + 2, c + 2, 0xFF0C1030);
        // ecualizador sobre la parte de abajo de la portada
        if (t != null) drawEqualizer(g, cx + 3, cy + c - 3, c - 6, Math.round(c * 0.28F));

        // volumen
        drawVolume(g, mx, my);

        // título (grande) y artista
        int ty = titleY(), ay = artistY();
        int textW = w - 16 - pad.big(18);
        if (t == null) {
            bigText(g, "Nada sonando", x + 8, ty, 0xFFFFFFFF, textW);
            PadUi.text(g, "Elige una canción de tu lista", x + 8, ay, LAVENDER);
        } else {
            bigText(g, t.shownTitle(), x + 8, ty, 0xFFFFFFFF, textW);
            PadUi.text(g, PadUi.fitEnd(t.shownArtist(), textW), x + 8, ay, LAVENDER);
            // botón PORTADA (cuadrito con un paisaje)
            int bx = x + w - 8 - pad.big(14), by = ty;
            boolean hover = PadUi.inside(mx, my, bx, by, pad.big(14), pad.big(12));
            iconButton(g, bx, by, pad.big(14), pad.big(12), hover, coverFor != null, "§cover");
            drawGlyph(g, IMAGE, bx + pad.big(3), by + pad.big(2), hover || coverFor != null ? GOLD_LIGHT : LAVENDER);
            MusicLibrary.Track track = t;
            hits.add(new Hit(bx, by, pad.big(14), pad.big(12), () -> {
                coverFor = coverFor == null ? track : null;
                focused = coverFor != null;
                input = "";
                pad.sound("select", 0.6F);
            }));
        }

        // barra de progreso
        int[] bar = seekBar();
        long dur = t == null ? 0 : t.durationMs();
        long pos = seekPreview >= 0 ? seekPreview : MusicPlayer.position();
        float f = dur > 0 ? Math.min(1F, pos / (float) dur) : 0F;
        boolean barHover = t != null && PadUi.inside(mx, my, bar[0] - 2, bar[1] - 4, bar[2] + 4, 10);
        int bh = barH();
        PadUi.box(g, bar[0], bar[1], bar[2], bh + 2, 0xFF0C1030);
        g.fill(bar[0] + 1, bar[1] + 1, bar[0] + bar[2] - 1, bar[1] + 1 + bh, 0xFF39407A);
        int fw = Math.round((bar[2] - 2) * f);
        if (fw > 0) {
            gradientH(g, bar[0] + 1, bar[1] + 1, fw, bh, 0xFFFFC94A, 0xFFFF8A3C);
            g.fill(bar[0] + 1, bar[1] + 1, bar[0] + 1 + fw, bar[1] + 2, 0x66FFFFFF);
        }
        if (t != null) {
            int kx = bar[0] + 1 + fw, ky = bar[1] + 1 + bh / 2;
            int kr = barHover || draggingSeek ? pad.big(4) : pad.big(3);
            disc(g, kx, ky, kr + 1, 0xFF0C1030);
            disc(g, kx, ky, kr, barHover || draggingSeek ? 0xFFFFFFFF : GOLD_LIGHT);
            if (barHover) pad.hover("§seek");
        }
        String left = time(pos), right = dur > 0 ? time(dur) : "--:--";
        PadUi.text(g, left, bar[0], bar[1] + bh + 4, LAVENDER);
        PadUi.text(g, right, bar[0] + bar[2] - PadUi.font().width(right), bar[1] + bh + 4, LAVENDER);

        // controles
        drawControls(g, mx, my, bar[1] + bh + 3 + 10);
    }

    private void drawControls(GuiGraphics g, double mx, double my, int y0) {
        int w = leftW();
        int playD = pad.big(22), small = pad.big(15), gap = pad.big(6);
        int total = playD + small * 4 + gap * 4;
        int cx0 = X + (w - total) / 2;
        int cyMid = y0 + playD / 2;
        int x = cx0;
        // aleatorio
        boolean sh = MusicPlayer.shuffle();
        x = control(g, mx, my, x, cyMid, small, SHUFFLE, sh ? GREEN : LAVENDER, sh, MusicPlayer::toggleShuffle) + gap;
        x = control(g, mx, my, x, cyMid, small, PREV, 0xFFFFFFFF, false, MusicPlayer::previous) + gap;
        // play / pausa: círculo de oro
        int px = x, r = playD / 2;
        boolean hover = PadUi.inside(mx, my, px, cyMid - r, playD, playD);
        if (hover) pad.hover("§play");
        float pulse = MusicPlayer.playing() ? 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 300.0) : 0;
        if (MusicPlayer.playing()) disc(g, px + r, cyMid, r + 2 + Math.round(pulse * 2), 0x33FFD84A);
        disc(g, px + r, cyMid, r + 1, 0xFF0C1030);
        disc(g, px + r, cyMid, r, hover ? 0xFFFFD36A : GOLD);
        disc(g, px + r, cyMid - Math.round(r * 0.35F), Math.round(r * 0.55F), hover ? 0x55FFFFFF : 0x33FFFFFF);
        int[][] glyph = MusicPlayer.playing() ? PAUSE : PLAY;
        drawGlyphCentered(g, glyph, px + r + (MusicPlayer.playing() ? 0 : pad.big(1)), cyMid, 0xFF1A2350);
        hits.add(new Hit(px, cyMid - r, playD, playD, () -> {
            MusicPlayer.toggle();
            pad.sound("select", 0.6F);
        }));
        x = px + playD + gap;
        x = control(g, mx, my, x, cyMid, small, NEXT, 0xFFFFFFFF, false, MusicPlayer::next) + gap;
        MusicLibrary.Repeat rep = MusicPlayer.repeat();
        control(g, mx, my, x, cyMid, small, rep == MusicLibrary.Repeat.ONE ? REPEAT_ONE : REPEAT,
                rep == MusicLibrary.Repeat.OFF ? LAVENDER : GREEN, rep != MusicLibrary.Repeat.OFF, MusicPlayer::cycleRepeat);
    }

    /** Botón redondo pequeño con un dibujo; devuelve dónde acaba. */
    private int control(GuiGraphics g, double mx, double my, int x, int cy, int d, int[][] glyph, int color, boolean on, Runnable action) {
        int r = d / 2;
        boolean hover = PadUi.inside(mx, my, x, cy - r, d, d);
        if (hover) pad.hover("§ctl" + x);
        if (hover || on) disc(g, x + r, cy, r, hover ? 0x44FFFFFF : 0x22FFFFFF);
        drawGlyphCentered(g, glyph, x + r, cy, hover ? GOLD_LIGHT : color);
        hits.add(new Hit(x, cy - r, d, d, () -> {
            action.run();
            pad.sound("tab", 0.5F);
        }));
        return x + d;
    }

    private void drawVolume(GuiGraphics g, double mx, double my) {
        int[] v = volumeBar();
        int x = v[0], top = v[1], h = v[2], w = pad.big(6);
        float vol = MusicPlayer.volume();
        // altavoz arriba (clic: silencio)
        int sx = x + w / 2, sy = coverY() + pad.big(6);
        boolean sHover = PadUi.inside(mx, my, x - pad.big(4), coverY(), w + pad.big(8), pad.big(12));
        drawGlyphCentered(g, vol <= 0 ? MUTE : vol < 0.5F ? SPEAKER_LOW : SPEAKER, sx, sy, sHover ? GOLD_LIGHT : LAVENDER);
        hits.add(new Hit(x - pad.big(4), coverY(), w + pad.big(8), pad.big(12), () -> {
            if (MusicPlayer.volume() > 0) {
                savedVolume = MusicPlayer.volume();
                MusicPlayer.setVolume(0);
            } else {
                MusicPlayer.setVolume(savedVolume > 0 ? savedVolume : 0.8F);
            }
            MusicPlayer.saveSettings();
        }));
        boolean hover = PadUi.inside(mx, my, x - 3, top - 3, w + 6, h + 6);
        if (hover) pad.hover("§vol");
        PadUi.box(g, x, top, w, h, 0xFF0C1030);
        g.fill(x + 1, top + 1, x + w - 1, top + h - 1, 0xFF39407A);
        int fh = Math.round((h - 2) * vol);
        if (fh > 0) gradient(g, x + 1, top + h - 1 - fh, w - 2, fh, 0xFF8CF0C8, 0xFF2EB87A);
        int ky = top + h - 1 - fh;
        disc(g, x + w / 2, ky, pad.big(4) + 1, 0xFF0C1030);
        disc(g, x + w / 2, ky, pad.big(4), hover || draggingVolume ? 0xFFFFFFFF : GOLD_LIGHT);
        if (hover || draggingVolume) {
            String pct = Math.round(vol * 100) + "%";
            PadUi.text(g, pct, x + w / 2 - PadUi.font().width(pct) / 2, top + h + 3, 0xFFFFFFFF);
        }
    }

    private void drawCover(GuiGraphics g, MusicLibrary.Track t, int x, int y, int size) {
        ResourceLocation tex = MusicLibrary.cover(t);
        if (tex != null) {
            g.blit(tex, x, y, size, size, 0, 0, 256, 256, 256, 256);
            return;
        }
        // sin portada: degradado con el color de la canción y el icono de música en grande
        int hue = t == null ? 0x3A4AA0 : 0xFF000000 | java.awt.Color.HSBtoRGB((t.id().hashCode() & 0xFFFF) / 65535F, 0.55F, 0.75F);
        int darker = 0xFF000000 | ((hue >> 1) & 0x7F7F7F);
        gradient(g, x, y, size, size, 0xFF000000 | hue, darker);
        g.pose().pushPose();
        float s = size / 48F;
        g.pose().translate(x + size / 2F - 16 * s, y + size / 2F - 16 * s, 0);
        g.pose().scale(s, s, 1);
        pad.blit(g, "icon_musica", 0, 0);
        g.pose().popPose();
    }

    /** Disco de vinilo: anillos (surcos), reflejos que giran, etiqueta con el color de la canción y agujero. */
    private void drawVinyl(GuiGraphics g, int cx, int cy, int r, MusicLibrary.Track t) {
        disc(g, cx, cy, r + 1, 0xFF05060E);
        disc(g, cx, cy, r, 0xFF1C1F2E);
        for (float k : new float[] {0.92F, 0.8F, 0.68F, 0.56F}) {
            ring(g, cx, cy, Math.round(r * k), 0xFF2C3044);
        }
        // reflejos: dos arcos claros que giran con el disco
        double a0 = Math.toRadians(spin);
        for (int arc = 0; arc < 2; arc++) {
            double base = a0 + arc * Math.PI;
            for (int i = 0; i < 14; i++) {
                double a = base + i * 0.05;
                for (float rr = r * 0.48F; rr < r * 0.9F; rr += 2.5F) {
                    int px = cx + (int) Math.round(Math.cos(a) * rr), py = cy + (int) Math.round(Math.sin(a) * rr);
                    g.fill(px, py, px + 1, py + 1, 0x30FFFFFF);
                }
            }
        }
        int label = Math.round(r * 0.36F);
        int hue = t == null ? 0xE83446 : java.awt.Color.HSBtoRGB((t.id().hashCode() & 0xFFFF) / 65535F, 0.7F, 0.9F);
        disc(g, cx, cy, label, 0xFF000000 | hue);
        ring(g, cx, cy, Math.round(label * 0.75F), 0x40FFFFFF);
        // marca que gira en la etiqueta
        double a = Math.toRadians(spin * 1.0);
        int mx = cx + (int) Math.round(Math.cos(a) * label * 0.6), my = cy + (int) Math.round(Math.sin(a) * label * 0.6);
        g.fill(mx - 1, my - 1, mx + 1, my + 1, 0xCCFFFFFF);
        disc(g, cx, cy, Math.max(1, label / 4), 0xFF05060E);
    }

    /** Barras que suben y bajan con el volumen medido de la canción (y un poco de vida propia). */
    private void drawEqualizer(GuiGraphics g, int x, int bottom, int w, int maxH) {
        int n = Math.max(8, Math.min(24, w / pad.big(5)));
        int bw = Math.max(2, w / n - 1);
        long pos = MusicPlayer.position();
        boolean on = MusicPlayer.playing();
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            float lv = on ? MusicPlayer.levelAt(pos + (i % 6) * 40L) : 0F;
            float wob = (float) (0.55 + 0.45 * Math.sin(now / (90.0 + (i * 53) % 70) + i * 1.3));
            float shape = 0.6F + 0.4F * (float) Math.sin(i * 0.9 + 1);
            int h = on ? Math.max(2, Math.round(maxH * Math.min(1F, lv * shape * (0.55F + 0.6F * wob)))) : 2;
            int bx = x + i * (w / n);
            g.fill(bx, bottom - h, bx + bw, bottom, 0x9918265C);
            g.fill(bx, bottom - h, bx + bw, bottom - h + 1, 0xCCFFFFFF);
            gradient(g, bx, bottom - h + 1, bw, Math.max(0, h - 1), 0xCC7CF0B0, 0xCC2E8ADA);
        }
    }

    // ---- «tu música» ----------------------------------------------------------------------------------------------

    private void drawLibrary(GuiGraphics g, double mx, double my) {
        int x = listX(), w = listW(), y = Y;
        List<MusicLibrary.Track> tracks = MusicLibrary.tracks();
        // cabecera
        g.pose().pushPose();
        g.pose().translate(x + 2, y + 1, 0);
        g.pose().scale(pad.bs, pad.bs, 1);
        PadFont.draw(g, "TU MÚSICA", 0, 0, 0xFFFFFF, true);
        g.pose().popPose();
        String count = tracks.size() + (tracks.size() == 1 ? " canción" : " canciones");
        PadUi.text(g, count, x + w - PadUi.font().width(count) - 2, y + 3, 0xFF18265C);
        int top = y + pad.big(13) + 2;
        int bottom = Y + H - 19;
        MusicDownloader.Job job = MusicDownloader.job();
        if (job != null && !job.finished) bottom -= 24;
        PadUi.panel(g, x, top, w, bottom - top);
        int inner = bottom - top - 4;
        int content = tracks.size() * ROW;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, content - inner)));
        pad.scissor(g, x + 1, top + 2, w - 2, inner);
        MusicLibrary.Track current = MusicPlayer.current();
        int ry = top + 2 - listScroll;
        boolean inList = my >= top + 2 && my < top + 2 + inner;
        for (MusicLibrary.Track t : tracks) {
            if (ry + ROW > top && ry < bottom) drawRow(g, t, x + 3, ry, w - 10, mx, my, inList, current != null && current.id().equals(t.id()));
            ry += ROW;
        }
        pad.noScissor(g);
        if (tracks.isEmpty()) drawEmpty(g, x, top, w, bottom - top);
        if (content > inner) {
            int bx = x + w - 5, by = top + 3, bh = inner - 2;
            g.fill(bx, by, bx + 2, by + bh, 0xFFC8DCF0);
            int th = Math.max(8, bh * inner / content);
            int ty = by + (bh - th) * listScroll / Math.max(1, content - inner);
            g.fill(bx, ty, bx + 2, ty + th, 0xFF3496FA);
        }
        if (job != null && !job.finished) drawJob(g, job, x, bottom + 3, w);
        drawInput(g, mx, my, x, Y + H - 16, w);
    }

    private void drawRow(GuiGraphics g, MusicLibrary.Track t, int x, int y, int w, double mx, double my, boolean inList, boolean current) {
        boolean hover = inList && PadUi.inside(mx, my, x, y, w, ROW - 1);
        if (current) {
            PadUi.box(g, x, y, w, ROW - 1, 0xFFFFE9A8);
            g.fill(x, y + 2, x + 2, y + ROW - 3, GOLD);
        } else if (hover) {
            PadUi.box(g, x, y, w, ROW - 1, 0xFFD6EEFF);
            g.fill(x, y + 2, x + 2, y + ROW - 3, 0xFF3496FA);
        }
        g.fill(x + 2, y + ROW - 1, x + w - 2, y + ROW, 0xFFC8E4F8);
        int thumb = ROW - 6;
        drawCover(g, t, x + 5, y + 3, thumb);
        outlineRect(g, x + 4, y + 2, thumb + 2, thumb + 2, 0xFF18265C);
        int tx = x + thumb + 11;
        String dur = t.durationMs() > 0 ? time(t.durationMs()) : "";
        int right = x + w - 6;
        // borrar (al pasar el ratón)
        boolean sure = t.id().equals(confirmDelete) && System.currentTimeMillis() - confirmAt < 3000;
        int delW = sure ? PadUi.buttonWidth("¿BORRAR?") : pad.big(12);
        int delX = right - delW, delY = y + (ROW - 1 - 15) / 2;
        if (hover || sure) {
            boolean dh = PadUi.inside(mx, my, delX, delY, delW, 15);
            if (sure) {
                PadUi.button(g, delX, delY, delW, "¿BORRAR?", PadView.RED, dh, true);
            } else {
                PadUi.box(g, delX, delY + 1, delW, 13, dh ? 0xFFE83446 : 0x33E83446);
                drawGlyphCentered(g, CROSS, delX + delW / 2, delY + 8, dh ? 0xFFFFFFFF : 0xFFE83446);
            }
            if (dh) pad.hover("§del" + t.id());
            hits.add(0, new Hit(delX, delY, delW, 15, () -> {
                if (sure) {
                    MusicPlayer.removed(t.id());
                    MusicLibrary.remove(t.id());
                    confirmDelete = null;
                    pad.sound("back", 0.6F);
                } else {
                    confirmDelete = t.id();
                    confirmAt = System.currentTimeMillis();
                }
            }));
            right = delX - 6;
        }
        if (!dur.isEmpty()) {
            PadUi.text(g, dur, right - PadUi.font().width(dur), y + 10, 0xFF4A6694);
            right -= PadUi.font().width(dur) + 6;
        }
        // sonando: barritas animadas junto al título
        int titleX = tx;
        if (current && MusicPlayer.playing()) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                int h = 2 + Math.round(6 * (0.5F + 0.5F * (float) Math.sin(now / (120.0 + i * 40) + i * 2)));
                g.fill(tx + i * 3, y + 12 - h, tx + i * 3 + 2, y + 12, 0xFFC27A10);
            }
            titleX += 12;
        }
        PadUi.text(g, PadUi.fitEnd(t.shownTitle(), right - titleX), titleX, y + 5, current ? 0xFF7A4A08 : 0xFF18265C);
        PadUi.text(g, PadUi.fitEnd(t.shownArtist(), right - tx), tx, y + 16, 0xFF4A6694);
        if (inList) {
            if (hover) pad.hover("§row" + t.id());
            hits.add(new Hit(x, y, w, ROW - 1, () -> {
                if (current) MusicPlayer.toggle();
                else MusicPlayer.play(t);
                pad.sound("select", 0.6F);
            }));
        }
    }

    private void drawEmpty(GuiGraphics g, int x, int top, int w, int h) {
        int cy = top + h / 2;
        g.pose().pushPose();
        float s = pad.bs * 1.25F;
        g.pose().translate(x + w / 2F - 16 * s, cy - 40 - 16 * s + 6, 0);
        g.pose().scale(s, s, 1);
        pad.blit(g, "icon_musica", 0, 0);
        g.pose().popPose();
        String[] lines = {"Pega abajo el link directo de una canción", "(MP3, OGG o WAV) y pulsa AÑADIR.",
                "Google Drive: «Cualquier persona con el enlace»."};
        int ly = cy + 10;
        for (int i = 0; i < lines.length; i++) {
            String l = PadUi.fitEnd(lines[i], w - 16);
            PadUi.text(g, l, x + (w - PadUi.font().width(l)) / 2, ly + i * 11, i == 2 ? 0xFF4A6694 : 0xFF18265C);
        }
    }

    private void drawJob(GuiGraphics g, MusicDownloader.Job job, int x, int y, int w) {
        PadUi.box(g, x, y, w, 20, PadUi.NAVY);
        PadUi.box(g, x + 1, y + 1, w - 2, 18, 0xFFFFF6D6);
        String status = job.status;
        float p = job.progress();
        if (p >= 0) status += " " + Math.round(p * 100) + "%";
        else if (job.done > 0) status += " " + (job.done >> 10) + " KB";
        PadUi.spinner(g, x + 8, y + 3);
        PadUi.text(g, PadUi.fitEnd(status, w - 24), x + 15, y + 3, 0xFF7A4A08);
        int bx = x + 4, bw = w - 8;
        g.fill(bx, y + 13, bx + bw, y + 16, 0xFFE8D8A8);
        int fw = p >= 0 ? Math.round(bw * p) : (int) ((System.currentTimeMillis() / 8) % bw);
        if (p >= 0) g.fill(bx, y + 13, bx + fw, y + 16, GOLD);
        else g.fill(bx + Math.max(0, fw - 20), y + 13, bx + fw, y + 16, GOLD);
    }

    private void drawInput(GuiGraphics g, double mx, double my, int x, int y, int w) {
        String label = coverFor != null ? "PONER" : "AÑADIR";
        int bw = PadUi.buttonWidth(label);
        int fw = w - bw - 4;
        String hint = coverFor != null ? "Link de una imagen (PNG o JPG) para «" + coverFor.shownTitle() + "»"
                : "Pega aquí el link directo de una canción";
        boolean fieldHover = PadUi.inside(mx, my, x, y, fw, 15);
        PadUi.box(g, x, y, fw, 15, focused ? 0xFFF6B628 : PadUi.NAVY);
        PadUi.box(g, x + 1, y + 1, fw - 2, 13, 0xFFFFFFFF);
        g.fill(x + 2, y + 1, x + fw - 2, y + 2, 0xFFD6E6F6);
        String shown = input.isEmpty() ? PadUi.fitEnd(hint, fw - 10) : PadUi.fitStart(input, fw - 10);
        PadUi.text(g, shown, x + 5, y + 4, input.isEmpty() ? 0xFF96AACC : PadUi.TEXT);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 5 + (input.isEmpty() ? 0 : PadUi.font().width(shown));
            g.fill(cx, y + 3, cx + 1, y + 12, PadUi.TEXT);
        }
        if (fieldHover) pad.hover("§field");
        hits.add(new Hit(x, y, fw, 15, () -> focused = true));
        boolean busy = MusicDownloader.busy();
        boolean enabled = !input.isBlank() && !busy;
        boolean bh = enabled && PadUi.inside(mx, my, x + fw + 4, y, bw, 15);
        PadUi.button(g, x + fw + 4, y, bw, label, PadView.GREEN, bh, enabled);
        if (enabled) hits.add(new Hit(x + fw + 4, y, bw, 15, this::submit));
    }

    private void submit() {
        String link = input.trim();
        if (link.isEmpty() || MusicDownloader.busy()) return;
        boolean ok = coverFor != null ? MusicDownloader.downloadCover(coverFor, link) : MusicDownloader.download(link);
        if (!ok) {
            pad.showNotice("Ese link no vale. Pega el link completo (empieza por https://).");
            pad.sound("back", 0.6F);
            return;
        }
        input = "";
        focused = false;
        pad.sound("page", 0.6F);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujitos (en píxeles de textura, a la escala grande)
    // ---------------------------------------------------------------------------------------------------------------

    private static int[][] glyph(String... rows) {
        int[][] out = new int[rows.length][];
        for (int y = 0; y < rows.length; y++) {
            out[y] = new int[rows[y].length()];
            for (int x = 0; x < rows[y].length(); x++) out[y][x] = rows[y].charAt(x) == '#' ? 1 : 0;
        }
        return out;
    }

    private static final int[][] PLAY = glyph("#......", "###....", "#####..", "#######", "#####..", "###....", "#......");
    private static final int[][] PAUSE = glyph("##.##", "##.##", "##.##", "##.##", "##.##", "##.##", "##.##");
    private static final int[][] NEXT = glyph("#...#.#", "##..#.#", "###.#.#", "#####.#", "###.#.#", "##..#.#", "#...#.#");
    private static final int[][] PREV = glyph("#.#...#", "#.#..##", "#.#.###", "#.#####", "#.#.###", "#.#..##", "#.#...#");
    private static final int[][] SHUFFLE = glyph("##....##.", "..#..#.##", "...##....", "...##....", "..#..#.##", "##....##.", ".........");
    private static final int[][] REPEAT = glyph("..######.", ".#.....##", "#......#.", "#.......#", ".#......#", "##.....#.", ".######..");
    private static final int[][] REPEAT_ONE = glyph("..######.", ".#.....##", "#...#..#.", "#..##...#", ".#..#...#", "##..#..#.", ".######..");
    private static final int[][] SPEAKER = glyph("...#.....", "..##..#..", "####...#.", "####.#.#.", "####...#.", "..##..#..", "...#.....");
    private static final int[][] SPEAKER_LOW = glyph("...#...", "..##...", "####.#.", "####..#", "####.#.", "..##...", "...#...");
    private static final int[][] MUTE = glyph("...#.....", "..##.....", "####.#.#.", "####..#..", "####.#.#.", "..##.....", "...#.....");
    private static final int[][] CROSS = glyph("#...#", ".#.#.", "..#..", ".#.#.", "#...#");
    private static final int[][] IMAGE = glyph("#########", "#.......#", "#.##..#.#", "#.##.###.", "#..#####.", "#.#######", "#########");

    private void drawGlyph(GuiGraphics g, int[][] glyph, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(pad.bs, pad.bs, 1);
        for (int gy = 0; gy < glyph.length; gy++) {
            for (int gx = 0; gx < glyph[gy].length; gx++) {
                if (glyph[gy][gx] != 0) g.fill(gx, gy, gx + 1, gy + 1, color);
            }
        }
        g.pose().popPose();
    }

    private void drawGlyphCentered(GuiGraphics g, int[][] glyph, int cx, int cy, int color) {
        int w = Math.round(glyph[0].length * pad.bs), h = Math.round(glyph.length * pad.bs);
        drawGlyph(g, glyph, cx - w / 2, cy - h / 2, color);
    }

    /** Texto de Minecraft a la escala grande (recortado con «…» a w unidades). */
    private void bigText(GuiGraphics g, String text, int x, int y, int color, int w) {
        float s = pad.bs;
        String shown = PadUi.fitEnd(text, Math.round(w / s));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1);
        g.drawString(PadUi.font(), shown, 0, 0, color, true);
        g.pose().popPose();
    }

    private void iconButton(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean on, String id) {
        if (hover) pad.hover(id);
        PadUi.box(g, x, y, w, h, on ? 0xFFF6B628 : hover ? 0x88FFFFFF : 0x44FFFFFF);
        PadUi.box(g, x + 1, y + 1, w - 2, h - 2, on ? 0xFF7A4A08 : 0xFF262E66);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt(r * r - dy * dy + r * 0.8));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int steps = Math.max(24, r * 6);
        int lx = Integer.MIN_VALUE, ly = 0;
        for (int i = 0; i < steps; i++) {
            double a = i * 2 * Math.PI / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            if (x == lx && y == ly) continue;
            g.fill(x, y, x + 1, y + 1, color);
            lx = x;
            ly = y;
        }
    }

    private static void gradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    private static void gradientH(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        int steps = Math.max(1, Math.min(w, 32));
        for (int i = 0; i < steps; i++) {
            int x0 = x + w * i / steps, x1 = x + w * (i + 1) / steps;
            g.fill(x0, y, x1, y + h, lerp(left, right, i / (float) Math.max(1, steps - 1)));
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
            r |= Math.round(ca + (cb - ca) * t) << s;
        }
        return r;
    }

    private static void outlineRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ratón y teclado
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
        MusicLibrary.Track t = MusicPlayer.current();
        int[] bar = seekBar();
        if (t != null && t.durationMs() > 0 && PadUi.inside(mx, my, bar[0] - 2, bar[1] - 4, bar[2] + 4, 10)) {
            draggingSeek = true;
            seekPreview = seekAt(mx, t);
            return true;
        }
        int[] v = volumeBar();
        if (PadUi.inside(mx, my, v[0] - 3, v[1] - 3, pad.big(6) + 6, v[2] + 6)) {
            draggingVolume = true;
            MusicPlayer.setVolume(volumeAt(my));
            return true;
        }
        boolean wasFocused = focused;
        for (Hit h : hits) {
            if (PadUi.inside(mx, my, h.x, h.y, h.w, h.h)) {
                focused = false;
                h.action.run();
                if (wasFocused && !focused && coverFor == null) focused = false;
                return true;
            }
        }
        focused = false;
        return false;
    }

    private long seekAt(double mx, MusicLibrary.Track t) {
        int[] bar = seekBar();
        float f = (float) Math.max(0, Math.min(1, (mx - bar[0]) / bar[2]));
        return Math.round(t.durationMs() * f);
    }

    private float volumeAt(double my) {
        int[] v = volumeBar();
        return (float) Math.max(0, Math.min(1, (v[1] + v[2] - my) / v[2]));
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        MusicLibrary.Track t = MusicPlayer.current();
        if (draggingSeek && t != null) {
            seekPreview = seekAt(mx, t);
            return true;
        }
        if (draggingVolume) {
            MusicPlayer.setVolume(volumeAt(my));
            return true;
        }
        if (mx >= listX()) {
            listScroll -= (int) Math.round(dy);
            return true;
        }
        return false;
    }

    @Override
    void release(double mx, double my, int button) {
        if (draggingSeek && seekPreview >= 0) {
            MusicPlayer.seek(seekPreview);
            pad.sound("tab", 0.5F);
        }
        if (draggingVolume) MusicPlayer.saveSettings();
        draggingSeek = false;
        draggingVolume = false;
        seekPreview = -1;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        if (PadUi.inside(mx, my, volumeBar()[0] - 8, Y, pad.big(6) + 16, H) && mx < listX()) {
            MusicPlayer.setVolume(MusicPlayer.volume() + (float) Math.signum(delta) * 0.05F);
            MusicPlayer.saveSettings();
            return true;
        }
        listScroll -= (int) Math.signum(delta) * ROW;
        return true;
    }

    @Override
    boolean typing() {
        return focused;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (focused) {
            if (key == 256) { // Esc: deja de escribir (y sale del modo portada)
                focused = false;
                coverFor = null;
                return true;
            }
            if (key == 259) {
                if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
                return true;
            }
            if (key == 257 || key == 335) {
                submit();
                return true;
            }
            if (Screen.isPaste(key)) {
                add(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            if (Screen.isSelectAll(key)) {
                input = "";
                return true;
            }
            return true;
        }
        if (key == 32) { // espacio
            MusicPlayer.toggle();
            return true;
        }
        if (key == 262 || key == 263) { // → / ←
            MusicPlayer.seek(MusicPlayer.position() + (key == 262 ? 5000 : -5000));
            return true;
        }
        if (Screen.isPaste(key)) { // pegar sin hacer clic en el campo
            focused = true;
            add(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        return false;
    }

    @Override
    boolean chr(char c) {
        if (!focused || c < ' ') return false;
        add(String.valueOf(c));
        return true;
    }

    private void add(String s) {
        StringBuilder b = new StringBuilder(input);
        for (char c : s.toCharArray()) if (c >= ' ' && c != '§' && b.length() < 600) b.append(c);
        input = b.toString().trim().isEmpty() ? "" : b.toString();
    }
}
