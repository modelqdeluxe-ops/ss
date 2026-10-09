package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El TF Pad: el marco del servidor con sus apps dentro.
 * <ul>
 *   <li>El marco (392x251 «píxeles de pad»; marco.png es 4 veces más grande) se dibuja al mayor número entero de
 *       píxeles de pantalla que quepa, sea cual sea la escala de la interfaz: grande y nítido.</li>
 *   <li>Lo de dentro (la barra de arriba y las apps) va en el cristal del marco con su propia escala, la mitad de la
 *       del marco: cabe el doble a lo ancho y a lo alto y sigue siendo nítido. Esas son las coordenadas de las páginas
 *       ({@link PadPage#X} …), en «unidades de pantalla».</li>
 *   <li>Arriba, la barra: volver, el nombre de la página centrado, la hora del mundo, tus monedas y Ajustes.</li>
 *   <li>Todas las apps se usan dentro del pad: las dibuja el servidor ({@link PadViewPage}) salvo Comunidad, Cámara y
 *       Ajustes, que tienen sus páginas. Con el pad de administrador, las apps son las de configurar el servidor.</li>
 * </ul>
 */
public final class TFPadScreen extends Screen {
    static final int W = 392;
    static final int H = 251;
    /** El cristal del marco, en píxeles de pad (el logo TF se mete por arriba a la izquierda). */
    static final int GX = 52, GY = 64, GW = 288, GH = 132;
    /**
     * Para hacer el pad más alto sin deformarlo (más aire entre las filas de apps), se repite una fila lisa de arriba y
     * otra de abajo de frame.png (filas de la imagen: 4 por píxel de pad), por encima y por debajo de los adornos de
     * oro de los lados. Lo que se alarga depende de lo alta que sea la pantalla.
     */
    static final int STRETCH_U = 430, STRETCH_D = 596;
    static final int MAX_EXTRA = 40;
    static final int NAVY = 0xFF18265C;
    /** Altura de la barra de arriba, en unidades de pantalla. */
    /** Alto de la barra de arriba con escala grande 1 (ver {@link #bar}). */
    static final int BAR = 18;
    /** Apps con página propia en el cliente; las demás las dibuja el servidor. */
    static final Set<String> CLIENT_APPS = Set.of("comunidad", "camara", "ajustes", "musica", "web");
    private static final Map<String, int[]> SIZES = new HashMap<>();

    /** Escala de la interfaz de Minecraft, píxeles reales por píxel de pad y por unidad de pantalla. */
    private double gs = 1;
    private int ps = 1, cs = 1;
    /**
     * Escala «grande» (portada, barra de arriba, iconos): px reales por píxel de textura = max(cs, round(ps*0,75));
     * bs = big / cs en unidades (1,5 a 1080p). Siempre da píxeles enteros: las texturas se ven nítidas.
     */
    private int big = 1;
    float bs = 1F;
    int bar = BAR;
    /** Esquina del marco y del cristal, en píxeles reales. */
    private int fx, fy, gx, gy;
    /** Píxeles de pad que se añaden arriba y abajo del cristal (ver {@link #STRETCH_U}). */
    private int extra;
    /** El cristal en unidades de pantalla. */
    int unitsW = GW, unitsH = GH;
    final boolean admin;
    private PadPage page;
    private final PadHomePage home;
    private String hovered, hoverNow;
    private String notice;
    private long noticeUntil;
    private final long openedAt = System.currentTimeMillis();

    public TFPadScreen() {
        this(false);
    }

    public TFPadScreen(boolean admin) {
        super(Component.literal(admin ? "TF Pad Admin" : "TF Pad"));
        this.admin = admin;
        PadSettings.load();
        home = new PadHomePage(this);
        page = home;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ayudantes para las páginas
    // ---------------------------------------------------------------------------------------------------------------

    static ResourceLocation tex(String name) {
        return new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/" + name + ".png");
    }

    static int[] size(String name) {
        return SIZES.computeIfAbsent(name, n -> {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex(n));
            if (res.isEmpty()) return new int[] {0, 0};
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                return new int[] {img.getWidth(), img.getHeight()};
            } catch (Exception e) {
                return new int[] {0, 0};
            }
        });
    }

    void blit(GuiGraphics g, String name, int x, int y) {
        int[] sz = size(name);
        if (sz[0] == 0) return;
        RenderSystem.enableBlend();
        g.blit(tex(name), x, y, 0, 0, sz[0], sz[1], sz[0], sz[1]);
    }

    /** Textura del pad a la escala grande, con su esquina en (x, y) unidades. */
    void blitBig(GuiGraphics g, String name, float x, float y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(bs, bs, 1);
        blit(g, name, 0, 0);
        g.pose().popPose();
    }

    /** Letra pixel a la escala grande, centrada en cx; y es la parte de arriba de la celda. */
    void textBig(GuiGraphics g, String text, float cx, float y, int rgb) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(bs, bs, 1);
        PadFont.drawCentered(g, text, 0, 0, rgb, true);
        g.pose().popPose();
    }

    /** Tamaño en unidades de algo que mide n píxeles de textura a la escala grande. */
    int big(int n) {
        return Math.round(n * bs);
    }

    /** Recorte en unidades de la página (en píxeles reales: exacto). Se quita con {@link #noScissor}. */
    void scissor(GuiGraphics g, int x, int y, int w, int h) {
        g.flush();
        double k = cs * (local ? bs : 1F);
        int ox = local ? saved[0] * cs : 0, oy = local ? saved[1] * cs : 0;
        int rx = gx + ox + (int) Math.floor(x * k), ry = gy + liftReal() + oy + (int) Math.floor(y * k);
        int rw = (int) Math.ceil(w * k), rh = (int) Math.ceil(h * k);
        int fbH = Minecraft.getInstance().getWindow().getHeight();
        RenderSystem.enableScissor(Math.max(0, rx), Math.max(0, fbH - (ry + rh)), Math.max(0, rw), Math.max(0, rh));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Páginas a escala: mientras se dibujan o reciben el ratón, PadPage.X/Y/W/H están en unidades de vista
    // ---------------------------------------------------------------------------------------------------------------

    private boolean local;
    private final int[] saved = new int[8];

    private boolean enter() {
        if (local || !page.scaled() || bs == 1F) return false;
        saved[0] = PadPage.X;
        saved[1] = PadPage.Y;
        saved[2] = PadPage.W;
        saved[3] = PadPage.H;
        saved[4] = PadPage.SW;
        saved[5] = PadPage.SH;
        saved[6] = PadPage.OX;
        saved[7] = PadPage.OY;
        PadPage.X = 0;
        PadPage.Y = 0;
        PadPage.W = (int) (saved[2] / bs);
        PadPage.H = (int) (saved[3] / bs);
        PadPage.OX = (int) Math.floor(-saved[0] / bs);
        PadPage.OY = (int) Math.floor(-saved[1] / bs);
        PadPage.SW = (int) Math.ceil(unitsW / bs);
        PadPage.SH = (int) Math.ceil(unitsH / bs);
        local = true;
        return true;
    }

    private void exit(boolean entered) {
        if (!entered) return;
        PadPage.X = saved[0];
        PadPage.Y = saved[1];
        PadPage.W = saved[2];
        PadPage.H = saved[3];
        PadPage.SW = saved[4];
        PadPage.SH = saved[5];
        PadPage.OX = saved[6];
        PadPage.OY = saved[7];
        local = false;
    }

    private double lx(double x) {
        return local ? (x - saved[0]) / bs : x;
    }

    private double ly(double y) {
        return local ? (y - saved[1]) / bs : y;
    }

    void noScissor(GuiGraphics g) {
        g.flush();
        RenderSystem.disableScissor();
    }

    void sound(String name, float volume) {
        if (PadSettings.sounds) TFPadClient.sound(name, volume * PadSettings.volume / 80F);
    }

    /** El ratón está sobre algo que se puede pulsar (para el tic suave al cambiar). */
    void hover(String id) {
        hoverNow = id;
    }

    void link(String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmLinkScreen(ok -> {
            if (ok) Util.getPlatform().openUri(url);
            mc.setScreen(this);
        }, url, true));
    }

    void showNotice(String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 4500;
        sound("back", 0.6F);
    }

    PadPage page() {
        return page;
    }

    void setPage(PadPage p) {
        if (page != null && page != p) page.closed();
        page = p;
        hovered = null;
    }

    /** Volver (flecha de arriba, Esc o retroceso): dentro de una app, primero a su pantalla anterior; luego, a la portada. */
    void back() {
        sound("back", 0.6F);
        if (page instanceof PadViewPage vp && vp.goBack()) return;
        setPage(home);
    }

    /** Abre una app de la portada. */
    void openApp(String id) {
        openApp(id, "");
    }

    /** Abre una app en una pestaña (tab vacío: la de entrada). */
    void openApp(String id, String tab) {
        if (id.equals("web")) { // va directa a la página del servidor, en el navegador
            sound("select", 0.7F);
            Util.getPlatform().openUri(net.tierrasfantasticas.tfclient.TFConfig.webUrl());
            showNotice("Abriendo " + net.tierrasfantasticas.tfclient.TFConfig.webUrl().replaceFirst("^https?://", "")
                    .replace("xn--tierrasfantsticas-hpb", "tierrasfantásticas") + " en tu navegador…");
            return;
        }
        sound("page", 0.55F);
        if (!CLIENT_APPS.contains(id)) {
            setPage(new PadViewPage(this, id));
            TFPadClient.openServerApp(id, tab);
            return;
        }
        switch (id) {
            case "comunidad" -> setPage(new PadCommunityPage(this));
            case "camara" -> setPage(new PadCameraPage(this));
            case "ajustes" -> setPage(new PadInfoPages.Ajustes(this));
            case "musica" -> setPage(new PadMusicPage(this));
            default -> {
            }
        }
    }

    /** Llegó una vista del servidor: si es la app que está abierta, se pinta. */
    void view(PadView v) {
        if (page instanceof PadViewPage vp && vp.app.equals(v.app())) vp.set(v);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        var window = Minecraft.getInstance().getWindow();
        gs = window.getGuiScale();
        int fbW = window.getWidth(), fbH = window.getHeight();
        ps = Math.max(1, (int) Math.floor(Math.min(fbW * 0.97 / W, fbH * 0.97 / H)));
        cs = ps <= 2 ? ps : Math.max(2, Math.round(ps / 2F));
        extra = Math.max(0, Math.min(MAX_EXTRA, (int) Math.floor((fbH * 0.985 / ps - H) / 2)));
        fx = (fbW - W * ps) / 2;
        fy = (fbH - (H + extra * 2) * ps) / 2;
        gx = fx + GX * ps;
        gy = fy + GY * ps;
        unitsW = GW * ps / cs;
        unitsH = (GH + extra * 2) * ps / cs;
        big = Math.max(cs, Math.round(ps * 0.75F));
        bs = big / (float) cs;
        bar = Math.round(12 * bs) + 6;
        // la zona de las apps: bajo la barra, a la derecha del logo a esa altura y con el mismo margen a la derecha, para
        // que la ventana de cada app quede centrada en el cristal (la portada usa todo el ancho: ver PadHomePage)
        PadPage.Y = bar + 4;
        PadPage.X = logoRight(PadPage.Y) + 4;
        PadPage.W = unitsW - PadPage.X * 2;
        PadPage.H = unitsH - PadPage.Y - 4;
        PadPage.SW = unitsW;
        PadPage.SH = unitsH;
    }

    /** Píxeles de pad → unidades de pantalla. */
    int units(int padPx) {
        return (int) Math.ceil(padPx * ps / (double) cs);
    }

    /** Lo que ocupa el logo arriba a la izquierda (en unidades): hasta dónde llega a la altura y. */
    int logoRight(int y) {
        double padY = GY + y * cs / (double) ps;
        int padX = padY < 66 ? 81 : padY < 70 ? 79 : padY < 80 ? 71 : padY < 86 ? 61 : GX;
        return units(padX - GX);
    }

    private int liftReal() {
        if (!PadSettings.animations) return 0;
        float p = Math.min(1F, (System.currentTimeMillis() - openedAt) / 160F);
        return Math.round((1 - p) * (1 - p) * 14 * ps);
    }

    private double ax(double mx) {
        return (mx * gs - gx) / cs;
    }

    private double ay(double my) {
        return (my * gs - gy - liftReal()) / cs;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        double mx = ax(mouseX), my = ay(mouseY);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int lift = liftReal();
        // el marco, a escala entera
        g.pose().pushPose();
        g.pose().translate(fx / gs, (fy + lift) / gs, 0);
        g.pose().scale((float) (ps / 4.0 / gs), (float) (ps / 4.0 / gs), 1);
        drawFrame(g);
        g.pose().popPose();
        // el cristal: barra y página
        g.pose().pushPose();
        g.pose().translate(gx / gs, (gy + lift) / gs, 0);
        g.pose().scale((float) (cs / gs), (float) (cs / gs), 1);
        hoverNow = null;
        drawStatus(g, mx, my);
        boolean in = enter();
        if (in) {
            g.pose().pushPose();
            g.pose().translate(saved[0], saved[1], 0);
            g.pose().scale(bs, bs, 1);
        }
        try {
            page.render(g, lx(mx), ly(my), partialTick);
        } finally {
            if (in) g.pose().popPose();
            exit(in);
        }
        if (hoverNow != null && !hoverNow.equals(hovered) && PadSettings.hoverTick) sound("hover", 0.35F);
        hovered = hoverNow;
        drawNotice(g);
        g.pose().popPose();
        page.renderOver(g, mouseX, mouseY);
        // destello de la cámara al volver de hacer una foto
        long since = System.currentTimeMillis() - PadCamera.flashAt;
        if (since < 350) {
            int a = (int) (230 * (1 - since / 350F));
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(0, 0, width, height, a << 24 | 0xFFFFFF);
            g.pose().popPose();
        }
        RenderSystem.disableBlend();
    }

    /** El marco en píxeles de la imagen (1568x1003), alargado extra píxeles de pad arriba y abajo del cristal. */
    private void drawFrame(GuiGraphics g) {
        ResourceLocation t = tex("frame");
        int e = extra * 4, iw = 1568, ih = 1003;
        g.blit(t, 0, 0, iw, STRETCH_U, 0, 0, iw, STRETCH_U, iw, ih);
        if (e > 0) g.blit(t, 0, STRETCH_U, iw, e, 0, STRETCH_U, iw, 1, iw, ih);
        g.blit(t, 0, STRETCH_U + e, iw, STRETCH_D - STRETCH_U, 0, STRETCH_U, iw, STRETCH_D - STRETCH_U, iw, ih);
        if (e > 0) g.blit(t, 0, STRETCH_D + e, iw, e, 0, STRETCH_D, iw, 1, iw, ih);
        g.blit(t, 0, STRETCH_D + e * 2, iw, ih - STRETCH_D, 0, STRETCH_D, iw, ih - STRETCH_D, iw, ih);
    }

    private int backX() {
        return logoRight(0) + 4;
    }

    private int backY() {
        return (bar - big(12)) / 2;
    }

    private int gearX() {
        return unitsW - 4 - big(12);
    }

    /** La barra de arriba: [◀] · título centrado · música · hora · monedas · ajustes, todo a la escala grande. */
    private void drawStatus(GuiGraphics g, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int bx = backX(), by = backY(), bsz = big(12);
        if (page != home) {
            boolean hover = PadUi.inside(mx, my, bx, by, bsz, bsz);
            if (hover) hover("§back");
            g.pose().pushPose();
            g.pose().translate(bx, by, 0);
            g.pose().scale(bs, bs, 1);
            // botón de volver con relieve, como los de dentro de las apps (dorado al pasar el ratón)
            PadUi.box(g, 0, 0, 12, 12, PadUi.INK);
            g.fillGradient(1, 1, 11, 10, hover ? 0xFFFFD650 : 0xFF5AB4FF, hover ? 0xFFE8961A : 0xFF2C74E4);
            g.fill(2, 1, 10, 2, hover ? 0xFFFFF2B0 : 0xFFB4E0FF);
            g.fill(1, 9, 11, 11, hover ? 0xFFA05E0E : 0xFF1A4AA8);
            g.fill(3, 5, 4, 6, 0xFFFFFFFF);
            g.fill(4, 4, 5, 7, 0xFFFFFFFF);
            g.fill(5, 3, 6, 8, 0xFFFFFFFF);
            g.fill(6, 5, 9, 6, 0xFFFFFFFF);
            g.pose().popPose();
        }
        float textY = bar / 2F - 5.5F * bs;
        textBig(g, page.title(), unitsW / 2F, textY, admin && page == home ? 0xFFD36A : 0xFFFFFF);
        int right = gearX();
        boolean gearHover = PadUi.inside(mx, my, right - 1, by, bsz + 2, bsz);
        if (gearHover) hover("§gear");
        g.pose().pushPose();
        g.pose().translate(right + bsz / 2F, bar / 2F, 0);
        g.pose().scale(bs, bs, 1);
        blit(g, "gear", -5, -5);
        if (gearHover) g.fill(-6, 6, 6, 7, 0xFFF6B628); // subrayado de oro al pasar el ratón (sin animación)
        g.pose().popPose();
        right -= big(8);
        TFPadNet.State s = TFPadClient.state;
        if (s != null && s.balance() >= 0) {
            String coins = PadUi.THOUSANDS.format(s.balance());
            int w = big(PadFont.width(coins));
            right -= w;
            g.pose().pushPose();
            g.pose().translate(right, textY + bs, 0);
            g.pose().scale(bs, bs, 1);
            PadFont.draw(g, coins, 0, 0, 0xFFE680, true);
            g.pose().popPose();
            right -= big(14);
            blitBig(g, "coin", right, (bar - big(11)) / 2F);
            right -= big(8);
        }
        if (mc.level != null) {
            long t = Math.floorMod(mc.level.getDayTime(), 24000L);
            String time = String.format(Locale.ROOT, "%02d:%02d", (int) ((t / 1000 + 6) % 24), (int) (t % 1000 * 60 / 1000));
            int w = big(PadFont.width(time));
            right -= w;
            g.pose().pushPose();
            g.pose().translate(right, textY + bs, 0);
            g.pose().scale(bs, bs, 1);
            PadFont.draw(g, time, 0, 0, 0xFFFFFF, true);
            g.pose().popPose();
            right -= big(14);
            blitBig(g, t < 13000 ? "sun" : "moon", right, (bar - big(11)) / 2F);
            right -= big(8);
        }
        // sonando: barritas de ecualizador que bailan con la canción (clic: abre Música)
        if (net.tierrasfantasticas.tfclient.pad.music.MusicPlayer.playing()) {
            int w = big(11);
            right -= w;
            musicX = right;
            boolean hover = PadUi.inside(mx, my, right - 1, by, w + 2, bsz);
            if (hover) hover("§music");
            long pos = net.tierrasfantasticas.tfclient.pad.music.MusicPlayer.position();
            for (int i = 0; i < 4; i++) {
                float lv = net.tierrasfantasticas.tfclient.pad.music.MusicPlayer.levelAt(pos + i * 70L);
                float wob = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / (110.0 + i * 37) + i * 1.7));
                int h = Math.max(big(2), Math.round(big(10) * Math.min(1F, 0.25F + lv * 0.9F * (0.6F + 0.4F * wob))));
                int x = right + i * big(3);
                int y1 = bar / 2 + big(5);
                g.fill(x - 1, y1 - h - 1, x + big(2) + 1, y1 + 1, 0xFF18265C);
                g.fill(x, y1 - h, x + big(2), y1, hover ? 0xFFFFE680 : 0xFF7CF0B0);
            }
        } else {
            musicX = -1000;
        }
    }

    private int musicX = -1000;

    /** Aviso del servidor: tarjeta azul marino con borde de oro, abajo, centrada en la pantalla. */
    private void drawNotice(GuiGraphics g) {
        if (notice == null) return;
        if (System.currentTimeMillis() > noticeUntil) {
            notice = null;
            return;
        }
        int w = Math.min(unitsW - 40, 320);
        List<FormattedCharSequence> lines = font.split(Component.literal(notice), w - 16);
        int h = lines.size() * 10 + 8, x = (unitsW - w) / 2, y = unitsH - 8 - h;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        PadUi.box(g, x, y, w, h, 0xFFB8741A);
        PadUi.box(g, x + 1, y + 1, w - 2, h - 2, NAVY);
        int ty = y + 5;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + (w - font.width(line)) / 2, ty, 0xFFFFE9A8, false);
            ty += 10;
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ratón y teclado
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = ax(mouseX), my = ay(mouseY);
        if (button == 0) {
            if (page != home && PadUi.inside(mx, my, backX(), backY(), big(12), big(12))) {
                back();
                return true;
            }
            if (PadUi.inside(mx, my, gearX() - 1, backY(), big(12) + 2, big(12))) {
                if (!(page instanceof PadInfoPages.Ajustes)) openApp("ajustes");
                else back();
                return true;
            }
            if (PadUi.inside(mx, my, musicX - 1, backY(), big(11) + 2, big(12)) && !(page instanceof PadMusicPage)) {
                openApp("musica");
                return true;
            }
        }
        boolean in = enter();
        try {
            if (page.click(lx(mx), ly(my), button)) return true;
        } finally {
            exit(in);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean in = enter();
        try {
            page.release(lx(ax(mouseX)), ly(ay(mouseY)), button);
        } finally {
            exit(in);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        boolean in = enter();
        try {
            if (page.drag(lx(ax(mouseX)), ly(ay(mouseY)), dy * gs / cs / (in ? bs : 1F))) return true;
        } finally {
            exit(in);
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean in = enter();
        try {
            if (page.scroll(lx(ax(mouseX)), ly(ay(mouseY)), delta)) return true;
        } finally {
            exit(in);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean in = enter();
        try {
            if (page.key(keyCode, scanCode, modifiers)) return true;
        } finally {
            exit(in);
        }
        if (!page.typing() && TFPadClient.KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if ((keyCode == 259 || keyCode == 256) && page != home) { // retroceso o Esc dentro de una app: volver
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        boolean in = enter();
        try {
            if (page.chr(c)) return true;
        } finally {
            exit(in);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public void tick() {
        page.tick();
    }

    /** Cerrar con Esc (desde la portada) o con la tecla del pad: suena el «apagar». */
    @Override
    public void onClose() {
        sound("close", 0.6F);
        super.onClose();
    }

    @Override
    public void removed() {
        page.closed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
