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
import net.minecraft.client.renderer.texture.DynamicTexture;
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
 *   <li>El cristal tiene un fondo animado (1.4, {@link #drawBackdrop}): luces que suben, un haz que lo cruza y destellos,
 *       por detrás del logo; las apps se dibujan con el diseño de {@link PadSkin}.</li>
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
    static final int MAX_EXTRA = 48;
    /**
     * Y para hacerlo más ancho (1.3.38): se repite una columna lisa a cada lado de la gema de arriba (columnas de la
     * imagen, simétricas respecto al centro 784: por las barras azules de arriba y de abajo y el cristal), hasta
     * MAX_EXTRA_X píxeles de pad por lado.
     */
    static final int STRETCH_L = 540, STRETCH_R = 1028;
    static final int MAX_EXTRA_X = 24;
    /**
     * Lo que se ve del marco (frame.png tiene márgenes transparentes, sobre todo abajo): de x 10 a 382 y de y 2 a 235
     * en píxeles de pad. El tamaño y el centrado se calculan con esto, no con la imagen entera: así el pad sale más
     * grande y centrado de verdad en la pantalla.
     */
    static final int VIS_L = 10, VIS_R = 382, VIS_T = 2, VIS_B = 235;
    static final int NAVY = 0xFF18265C;
    /** Altura de la barra de arriba, en unidades de pantalla. */
    /** Alto de la barra de arriba con escala grande 1 (ver {@link #bar}). */
    static final int BAR = 18;
    /** Apps con página propia en el cliente; las demás las dibuja el servidor. */
    static final Set<String> CLIENT_APPS = Set.of("comunidad", "camara", "ajustes", "musica", "web", "gachapon", "pase");
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
    /** Píxeles de pad que se añaden arriba y abajo del cristal (ver {@link #STRETCH_U}) y a cada lado (STRETCH_L/R). */
    private int extra, extraX;
    /** El cristal en unidades de pantalla. */
    int unitsW = GW, unitsH = GH;
    final boolean admin;
    private PadPage page;
    private final PadHomePage home;
    private String hovered, hoverNow;
    /** El aviso que se ve (uno solo: el nuevo sustituye al anterior), cuándo salió, cuándo se va y cuánto se lee. */
    private String notice;
    private long noticeUntil, noticeAt, noticeLife;
    private int noticeKind;
    /** Dónde quedó el aviso (unidades de pantalla), para cerrarlo con un clic o pararlo con el ratón encima. */
    private float[] noticeBox;
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
        return size(tex(name));
    }

    /** Tamaño en píxeles de cualquier textura ({0, 0} si no existe); se mira una vez y se recuerda. */
    static int[] size(ResourceLocation loc) {
        return SIZES.computeIfAbsent(loc.toString(), n -> {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
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

    /**
     * Textura entera del pad metida en w x h unidades, suavizada (para las que tienen más detalle que una unidad por
     * píxel, como los emojis de 44x44: se ven bien a cualquier escala).
     */
    void blitFit(GuiGraphics g, String name, int x, int y, int w, int h) {
        int[] sz = size(name);
        if (sz[0] == 0) return;
        RenderSystem.enableBlend();
        ResourceLocation loc = tex(name);
        Minecraft.getInstance().getTextureManager().getTexture(loc).setFilter(true, false);
        g.blit(loc, x, y, w, h, 0, 0, sz[0], sz[1], sz[0], sz[1]);
    }

    /** Reducciones ya hechas para {@link #blitSharp}: «nombre_píxeles» → textura (null si no existe el original). */
    private static final Map<String, Optional<ResourceLocation>> SHARP = new HashMap<>();

    /**
     * Textura grande (los emojis de Comunidad: 96x96) en u x u unidades y nítida a cualquier escala: la primera vez se
     * reduce al tamaño exacto en píxeles reales con que sale (u por los píxeles reales de una unidad: siempre entero),
     * con media por área, y luego se pinta 1 píxel de textura = 1 píxel real, sin filtro. Una reducción por tamaño.
     */
    void blitSharp(GuiGraphics g, String name, int x, int y, int u) {
        int px = u * (local ? big : cs);
        if (px <= 0) return;
        Optional<ResourceLocation> loc = SHARP.computeIfAbsent(name + "_" + px, k -> reduce(name, px));
        if (loc.isEmpty()) return;
        RenderSystem.enableBlend();
        g.blit(loc.get(), x, y, u, u, 0, 0, px, px, px, px);
    }

    private static Optional<ResourceLocation> reduce(String name, int px) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex(name));
        if (res.isEmpty()) return Optional.empty();
        try (InputStream in = res.get().open(); NativeImage src = NativeImage.read(in)) {
            int sw = src.getWidth(), sh = src.getHeight();
            NativeImage out = new NativeImage(px, px, true);
            double kx = sw / (double) px, ky = sh / (double) px;
            for (int oy = 0; oy < px; oy++) {
                double y0 = oy * ky, y1 = y0 + ky;
                for (int ox = 0; ox < px; ox++) {
                    double x0 = ox * kx, x1 = x0 + kx;
                    // media de lo que cae dentro del píxel, con el color pesado por su alfa (sin bordes oscuros)
                    double area = 0, a = 0, c0 = 0, c1 = 0, c2 = 0;
                    for (int sy = (int) y0; sy < Math.min(sh, Math.ceil(y1)); sy++) {
                        double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                        for (int sx = (int) x0; sx < Math.min(sw, Math.ceil(x1)); sx++) {
                            double w = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                            if (w <= 0) continue;
                            int p = src.getPixelRGBA(sx, sy);
                            double al = (p >>> 24) * w;
                            area += w;
                            a += al;
                            c0 += (p & 255) * al;
                            c1 += (p >> 8 & 255) * al;
                            c2 += (p >> 16 & 255) * al;
                        }
                    }
                    int pa = area > 0 ? (int) Math.round(a / area) : 0;
                    int r0 = a > 0 ? (int) Math.round(c0 / a) : 0, r1 = a > 0 ? (int) Math.round(c1 / a) : 0,
                            r2 = a > 0 ? (int) Math.round(c2 / a) : 0;
                    out.setPixelRGBA(ox, oy, pa << 24 | Math.min(255, r2) << 16 | Math.min(255, r1) << 8 | Math.min(255, r0));
                }
            }
            ResourceLocation loc = new ResourceLocation(TFClient.MOD_ID, "pad_sharp/" + name + "_" + px);
            Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(out));
            return Optional.of(loc);
        } catch (Exception e) {
            return Optional.empty();
        }
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

    /** Píxeles reales por unidad de contenido. */
    int cs() {
        return cs;
    }

    /** Píxeles reales por píxel de textura a la escala grande. */
    int bigPx() {
        return big;
    }

    /** Tamaño en unidades de algo que mide n píxeles de textura a la escala grande. */
    int big(int n) {
        return Math.round(n * bs);
    }

    /** Píxeles reales por unidad donde se está dibujando (en las páginas a escala, más que en la barra de arriba). */
    int unitPx() {
        return local ? big : cs;
    }

    /** Recortes abiertos (en píxeles reales, como los pide OpenGL): el de arriba es el que vale. */
    private final java.util.ArrayDeque<int[]> clips = new java.util.ArrayDeque<>();

    /** Un rectángulo en unidades de la página, en píxeles reales de OpenGL {x, y desde abajo, ancho, alto}. */
    private int[] realClip(int x, int y, int w, int h) {
        double k = cs * (local ? bs : 1F);
        int ox = local ? saved[0] * cs : 0, oy = local ? saved[1] * cs : 0;
        int rx = gx + ox + (int) Math.floor(x * k), ry = gy + liftReal() + oy + (int) Math.floor(y * k);
        int rw = (int) Math.ceil(w * k), rh = (int) Math.ceil(h * k);
        int fbH = Minecraft.getInstance().getWindow().getHeight();
        return new int[] {Math.max(0, rx), Math.max(0, fbH - (ry + rh)), Math.max(0, rw), Math.max(0, rh)};
    }

    /** Recorte en unidades de la página (en píxeles reales: exacto). Se quita con {@link #noScissor}. */
    void scissor(GuiGraphics g, int x, int y, int w, int h) {
        g.flush();
        clips.clear();
        int[] r = realClip(x, y, w, h);
        clips.push(r);
        RenderSystem.enableScissor(r[0], r[1], r[2], r[3]);
    }

    /**
     * Un recorte dentro del que ya hay (se quedan con lo que tienen en común): para lo que no debe salirse de una
     * tarjeta dentro de una lista que ya recorta. Se quita con {@link #clipPop}, que deja el de antes.
     */
    void clipPush(GuiGraphics g, int x, int y, int w, int h) {
        g.flush();
        int[] r = realClip(x, y, w, h);
        int[] top = clips.peek();
        if (top != null) {
            int x0 = Math.max(r[0], top[0]), y0 = Math.max(r[1], top[1]);
            int x1 = Math.min(r[0] + r[2], top[0] + top[2]), y1 = Math.min(r[1] + r[3], top[1] + top[3]);
            r = new int[] {x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)};
        }
        clips.push(r);
        RenderSystem.enableScissor(r[0], r[1], r[2], r[3]);
    }

    void clipPop(GuiGraphics g) {
        g.flush();
        if (!clips.isEmpty()) clips.pop();
        int[] top = clips.peek();
        if (top == null) RenderSystem.disableScissor();
        else RenderSystem.enableScissor(top[0], top[1], top[2], top[3]);
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
        clips.clear();
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

    /** Lo que tarda el aviso en entrar y en irse (ms). */
    private static final int TOAST_IN = 220, TOAST_OUT = 280;

    /**
     * Un aviso (1.4.1): una pastilla pequeña abajo en el centro del cristal, lejos de la cabecera y las pestañas. Sube
     * y se enciende, se lee unos 2,2 s (algo más si el texto es muy largo), se apaga sola, se cierra con un clic y se
     * queda mientras el ratón está encima. Uno a la vez: si llega otro, lo sustituye.
     */
    void showNotice(String text) {
        if (text == null || text.isBlank()) return;
        long now = System.currentTimeMillis();
        boolean replacing = notice != null && now < noticeUntil - TOAST_OUT;
        notice = text.strip();
        noticeKind = noticeKind(notice);
        // si ya había uno a la vista, el nuevo no vuelve a subir desde abajo: entra con un saltito
        noticeAt = replacing ? now - TOAST_IN + 110 : now;
        noticeLife = Math.min(3600, 2200 + Math.max(0, notice.length() - 50) * 28L);
        noticeUntil = now + (replacing ? 110 : TOAST_IN) + noticeLife + TOAST_OUT;
        sound("back", 0.5F);
    }

    /** 0 informa (azul), 1 salió bien (verde), 2 algo no se pudo (rojo): por cómo empieza el texto. */
    private static int noticeKind(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        String[] bad = {"error", "no ", "ese link no", "no se pudo", "no puedes", "no tienes", "no hay", "no te ", "te faltan",
                "falta", "desactivad", "sin saldo", "no está", "no es ", "ya no"};
        for (String b : bad) if (t.startsWith(b) || b.length() > 4 && t.contains(" " + b)) return 2;
        String[] good = {"publicad", "comprad", "vendid", "reclamad", "cobrad", "guardad", "añadid", "enviad", "retirad", "recogid",
                "lista «", "portada puesta", "listo", "hecho", "has ", "¡", "regalad", "borrad", "creado", "creada"};
        for (String ok : good) if (t.startsWith(ok)) return 1;
        return 0;
    }

    PadPage page() {
        return page;
    }

    void setPage(PadPage p) {
        if (page != null && page != p) page.closed();
        boolean changed = page != p;
        page = p;
        hovered = null;
        if (changed) p.shown();
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
            case "gachapon", "pase" -> { // páginas propias con los datos del servidor
                setPage(id.equals("pase") ? new PadPassPage(this) : new PadGachaPage(this));
                TFPadClient.openServerApp(id, tab);
            }
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
        int visW = VIS_R - VIS_L, visH = VIS_B - VIS_T;
        ps = Math.max(1, (int) Math.floor(Math.min(fbW * 0.97 / visW, fbH * 0.975 / visH)));
        cs = ps <= 2 ? ps : Math.max(2, Math.round(ps / 2F));
        extra = Math.max(0, Math.min(MAX_EXTRA, (int) Math.floor((fbH * 0.985 / ps - visH) / 2)));
        extraX = Math.max(0, Math.min(MAX_EXTRA_X, (int) Math.floor((fbW * 0.97 / ps - visW) / 2)));
        // centrado por lo que se ve del marco (no por la imagen entera, que tiene más margen abajo)
        fx = (fbW - (visW + extraX * 2) * ps) / 2 - VIS_L * ps;
        fy = (fbH - (visH + extra * 2) * ps) / 2 - VIS_T * ps;
        gx = fx + GX * ps;
        gy = fy + GY * ps;
        unitsW = (GW + extraX * 2) * ps / cs;
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
        // el fondo del cristal (luces que suben, un haz que lo cruza, destellos) y, encima, otra vez el logo
        g.pose().pushPose();
        g.pose().translate(gx / gs, (gy + lift) / gs, 0);
        g.pose().scale((float) (cs / gs), (float) (cs / gs), 1);
        drawBackdrop(g);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(fx / gs, (fy + lift) / gs, 0);
        g.pose().scale((float) (ps / 4.0 / gs), (float) (ps / 4.0 / gs), 1);
        RenderSystem.enableBlend();
        g.blit(tex("logo_over"), LOGO_X, LOGO_Y, 0, 0, 128, 104, 128, 104);
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
        drawNotice(g, mx, my);
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

    /** Dónde va logo_over.png (el trozo del logo que pisa el cristal) en píxeles de frame.png (tools/pad/build_skin.py). */
    private static final int LOGO_X = 52 * 4, LOGO_Y = 64 * 4;

    /**
     * Luces del fondo: x, y (0 a 1 del cristal), radio (unidades), velocidad de subida (unidades/s), vaivén (amplitud
     * y periodo en s), alfa; y su color. Las mismas que tools/pad/sim/skin.py (BOKEH).
     */
    private static final float[][] BOKEH = {
            {0.08F, 0.30F, 46, 3.0F, 14, 9.0F, 0.20F}, {0.22F, 0.85F, 30, 4.5F, 10, 7.0F, 0.26F},
            {0.37F, 0.55F, 64, 2.2F, 18, 11.0F, 0.13F}, {0.52F, 0.15F, 26, 5.0F, 8, 6.5F, 0.24F},
            {0.63F, 0.70F, 52, 2.8F, 16, 10.0F, 0.16F}, {0.78F, 0.35F, 36, 3.8F, 12, 8.0F, 0.22F},
            {0.90F, 0.80F, 58, 2.5F, 20, 12.0F, 0.14F}, {0.95F, 0.12F, 24, 4.2F, 9, 7.5F, 0.22F},
            {0.45F, 0.95F, 40, 3.4F, 13, 9.5F, 0.18F}};
    private static final int[] BOKEH_RGB = {0xFFFFFF, 0xDCF6FF, 0xFFFFFF, 0xFFF4C8, 0xFFFFFF, 0xC8EEFF, 0xFFFFFF, 0xFFECAA, 0xE6FAFF};
    /** Destellos del fondo (x, y de 0 a 1). */
    private static final float[][] STARS = {{0.12F, 0.62F}, {0.27F, 0.18F}, {0.33F, 0.82F}, {0.48F, 0.40F}, {0.58F, 0.88F},
            {0.66F, 0.22F}, {0.74F, 0.58F}, {0.86F, 0.30F}, {0.92F, 0.66F}, {0.18F, 0.92F}, {0.55F, 0.62F}, {0.81F, 0.90F},
            {0.40F, 0.12F}, {0.70F, 0.78F}};

    /**
     * El fondo del cristal (1.4), sin quitarle su azul: el rombo tallado muy suave, una sombra leve abajo, luces
     * desenfocadas que suben despacio meciéndose, un haz de luz en diagonal que lo cruza cada 13 s y destellos que
     * parpadean. Con las animaciones apagadas (Ajustes), todo quieto. Luego se vuelve a pintar el logo encima.
     */
    private void drawBackdrop(GuiGraphics g) {
        float t = PadSkin.time();
        int w = unitsW, h = unitsH;
        scissor(g, 0, 0, w, h);
        PadUi.texture(g, 0, 0, w, h);
        PadSkin.vgrad(g, 0, h * 2 / 3F, w, h, 0x00144696, 0x16144696);
        float k = (t % 13F) / 4.5F;
        if (PadSettings.animations && k < 1) PadSkin.beam(g, -120 + k * (w + 240), 70, w, h, 0.16F);
        ResourceLocation bokeh = tex("bokeh");
        for (int i = 0; i < BOKEH.length; i++) {
            float[] b = BOKEH[i];
            float r = b[2], span = h + 2 * r;
            float y = (((b[1] * span - b[3] * t) % span) + span) % span - r;
            float x = b[0] * w + b[4] * (float) Math.sin(t * 2 * Math.PI / b[5] + b[0] * 9);
            PadSkin.texture(g, bokeh, x - r, y - r, 2 * r, 2 * r, b[6], BOKEH_RGB[i], true);
        }
        for (int i = 0; i < STARS.length; i++) {
            float tw = (float) Math.max(0, Math.sin(t * 1.7F + i * 2.17F));
            PadSkin.sparkle(g, (int) (STARS[i][0] * w), (int) (STARS[i][1] * h), i % 3 == 0 ? 2 : 1, tw * tw * tw * 0.85F, 0xFFFFFF);
        }
        // en la portada, además, su cielo animado (por detrás del logo y de las fichas)
        if (page == home) home.ambient(g, t);
        noScissor(g);
    }

    /**
     * El marco en píxeles de la imagen (1568x1003), alargado extra píxeles de pad arriba y abajo del cristal y extraX a
     * cada lado de la gema de arriba: se parte en 3x3 trozos y las filas/columnas lisas de entre medias se estiran.
     */
    private void drawFrame(GuiGraphics g) {
        ResourceLocation t = tex("frame");
        int e = extra * 4, ex = extraX * 4, iw = 1568, ih = 1003;
        // columnas: [0, L) tal cual · L repetida ex veces · [L, R) tal cual · R repetida ex veces · [R, iw) tal cual
        int[][] cols = {{0, STRETCH_L, 0}, {STRETCH_L, 1, ex}, {STRETCH_L, STRETCH_R - STRETCH_L, 0}, {STRETCH_R, 1, ex},
                {STRETCH_R, iw - STRETCH_R, 0}};
        int[][] rows = {{0, STRETCH_U, 0}, {STRETCH_U, 1, e}, {STRETCH_U, STRETCH_D - STRETCH_U, 0}, {STRETCH_D, 1, e},
                {STRETCH_D, ih - STRETCH_D, 0}};
        int dy = 0;
        for (int[] r : rows) {
            int h = r[2] > 0 ? r[2] : r[1];
            if (h <= 0) continue;
            int dx = 0;
            for (int[] c : cols) {
                int w = c[2] > 0 ? c[2] : c[1];
                if (w <= 0) continue;
                g.blit(t, dx, dy, w, h, c[0], r[0], c[1], r[1], iw, ih);
                dx += w;
            }
            dy += h;
        }
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
        // 1.3.38: las monedas verdes (las del Gachapón, se ganan con el TF Pass)
        if (s != null && !admin) {
            String greens = PadUi.THOUSANDS.format(s.green());
            int w = big(PadFont.width(greens));
            right -= w;
            g.pose().pushPose();
            g.pose().translate(right, textY + bs, 0);
            g.pose().scale(bs, bs, 1);
            PadFont.draw(g, greens, 0, 0, 0x8CF0B4, true);
            g.pose().popPose();
            right -= big(14);
            blitBig(g, "coin_green", right, (bar - big(11)) / 2F);
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
        // el título, centrado; si lo de la derecha (hora, monedas…) llegara hasta él, se aparta (nunca se pisan)
        float tw = big(PadFont.width(page.title()));
        float cx = Math.min(unitsW / 2F, right - big(6) - tw / 2F);
        cx = Math.max(cx, backX() + big(12) + big(6) + tw / 2F);
        textBig(g, page.title(), cx, textY, admin && page == home ? 0xFFD36A : 0xFFFFFF);
    }

    private int musicX = -1000;

    private static final int[] TOAST_COLOR = {0xFF4FA8FF, 0xFF3CCB6A, 0xFFF0505A};

    /**
     * El aviso: pastilla azul noche translúcida (a la escala grande, como el texto de las apps) con una franja y un
     * icono del color del aviso (i, ✓ o !), el texto en blanco (hasta 3 líneas) y abajo una línea que se acorta con el
     * tiempo que le queda. Va abajo en el centro del cristal y por encima de todo.
     */
    private void drawNotice(GuiGraphics g, double mx, double my) {
        noticeBox = null;
        if (notice == null) return;
        long now = System.currentTimeMillis();
        if (now >= noticeUntil) {
            notice = null;
            return;
        }
        boolean anim = PadSettings.animations;
        float in = anim ? easeOut(Math.min(1F, (now - noticeAt) / (float) TOAST_IN)) : 1F;
        float out = anim ? Math.min(1F, (noticeUntil - now) / (float) TOAST_OUT) : 1F;
        float a = Math.min(Math.min(1F, (now - noticeAt) / (TOAST_IN * 0.6F)), out);
        if (!anim) a = 1F;
        if (a < 0.03F) return;
        float k = bs;
        int vw = (int) (unitsW / k), vh = (int) (unitsH / k);
        List<FormattedCharSequence> lines = font.split(Component.literal(notice), Math.min(vw - 70, 250));
        if (lines.size() > 3) lines = lines.subList(0, 3);
        int textW = 0;
        for (FormattedCharSequence line : lines) textW = Math.max(textW, font.width(line));
        int w = textW + 34, h = Math.max(22, lines.size() * 10 + 12);
        int x = (vw - w) / 2;
        float y = vh - 9 - h + (1 - in) * 12 + (1 - out) * 5;
        // con el ratón encima no se va (y se ilumina un poco); un clic lo cierra
        boolean hover = PadUi.inside(mx / k, my / k, x, Math.round(y), w, h);
        if (hover && now > noticeAt + TOAST_IN) noticeUntil = Math.max(noticeUntil, now + 600 + TOAST_OUT);
        noticeBox = new float[] {x * k, y * k, w * k, h * k};
        int acc = TOAST_COLOR[noticeKind];
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.pose().scale(k, k, 1);
        g.pose().translate(0, y, 0);
        int A = Math.round(a * 255);
        // sombra, borde y cuerpo
        g.fill(x + 2, h, x + w - 2, h + 1, PadSkin.alpha(NAVY, Math.round(A * 0.38F)));
        g.fill(x + 4, h + 1, x + w - 4, h + 2, PadSkin.alpha(NAVY, Math.round(A * 0.16F)));
        PadUi.box(g, x, 0, w, h, PadSkin.alpha(0x0A1030, Math.round(A * 0.94F)));
        g.fillGradient(x + 1, 1, x + w - 1, h - 1, PadSkin.alpha(hover ? 0x34488E : 0x2B3D80, Math.round(A * 0.95F)),
                PadSkin.alpha(0x172256, Math.round(A * 0.95F)));
        g.fill(x + 2, 1, x + w - 2, 2, PadSkin.alpha(0xFFFFFF, Math.round(A * 0.22F)));
        // la franja y el icono del color del aviso
        g.fillGradient(x + 1, 2, x + 3, h - 2, PadSkin.alpha(PadSkin.lighten(acc, 50), A), PadSkin.alpha(acc, A));
        int cx = x + 13, cy = h / 2;
        PadSkin.glow(g, cx + 0.5F, cy + 0.5F, 11, acc & 0xFFFFFF, Math.round(A * 0.55F));
        PadSkin.disc(g, cx + 0.5F, cy + 0.5F, 6F, PadSkin.alpha(PadSkin.darken(acc, 0.62F), A));
        PadSkin.disc(g, cx + 0.5F, cy + 0.5F, 5F, PadSkin.alpha(acc, A));
        noticeGlyph(g, cx, cy, PadSkin.alpha(0xFFFFFF, A));
        // el texto
        int tc = PadSkin.alpha(0xF4F7FF, A), sh = PadSkin.alpha(0x0A1030, Math.round(A * 0.7F));
        int ty = (h - lines.size() * 10) / 2 + 1;
        if (A >= 8) {
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 24, ty + 1, sh, false);
                g.drawString(font, line, x + 23, ty, tc, false);
                ty += 10;
            }
        }
        // lo que le queda: una línea que se acorta
        float left = Math.max(0F, Math.min(1F, (noticeUntil - TOAST_OUT - now) / (float) noticeLife));
        int lw = Math.round((w - 8) * left);
        if (lw > 0) g.fill(x + 4, h - 2, x + 4 + lw, h - 1, PadSkin.alpha(acc, Math.round(A * 0.7F)));
        g.pose().popPose();
    }

    /** El dibujo blanco dentro del círculo del aviso (7x7 centrado en cx, cy): i, ✓ o !. */
    private void noticeGlyph(GuiGraphics g, int cx, int cy, int c) {
        int x = cx - 3, y = cy - 3;
        switch (noticeKind) {
            case 1 -> {
                int[][] px = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {1, 3}, {2, 4}, {3, 3}, {4, 2}, {5, 1}};
                for (int[] p : px) g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, c);
            }
            case 2 -> {
                g.fill(x + 3, y, x + 4, y + 4, c);
                g.fill(x + 3, y + 5, x + 4, y + 6, c);
            }
            default -> {
                g.fill(x + 3, y, x + 4, y + 1, c);
                g.fill(x + 3, y + 2, x + 4, y + 6, c);
                g.fill(x + 2, y + 2, x + 3, y + 3, c);
                g.fill(x + 2, y + 6, x + 5, y + 7, c);
            }
        }
    }

    private static float easeOut(float x) {
        x = Math.max(0, Math.min(1, x));
        float k = 1 - x;
        return 1 - k * k * k;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ratón y teclado
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = ax(mouseX), my = ay(mouseY);
        // un clic en el aviso lo cierra (y no pasa a lo de debajo)
        if (noticeBox != null && mx >= noticeBox[0] && my >= noticeBox[1] && mx < noticeBox[0] + noticeBox[2] && my < noticeBox[1] + noticeBox[3]) {
            noticeUntil = Math.min(noticeUntil, System.currentTimeMillis() + TOAST_OUT);
            noticeBox = null;
            return true;
        }
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
