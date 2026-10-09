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
    static final int NAVY = 0xFF18265C;
    /** Altura de la barra de arriba, en unidades de pantalla. */
    static final int BAR = 18;
    /** Apps con página propia en el cliente; las demás las dibuja el servidor. */
    static final Set<String> CLIENT_APPS = Set.of("comunidad", "camara", "ajustes");
    private static final Map<String, int[]> SIZES = new HashMap<>();

    /** Escala de la interfaz de Minecraft, píxeles reales por píxel de pad y por unidad de pantalla. */
    private double gs = 1;
    private int ps = 1, cs = 1;
    /** Esquina del marco y del cristal, en píxeles reales. */
    private int fx, fy, gx, gy;
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

    /** Recorte en unidades de pantalla (en píxeles reales: exacto). Se quita con {@link #noScissor}. */
    void scissor(GuiGraphics g, int x, int y, int w, int h) {
        g.flush();
        int rx = gx + x * cs, ry = gy + liftReal() + y * cs;
        int fbH = Minecraft.getInstance().getWindow().getHeight();
        RenderSystem.enableScissor(Math.max(0, rx), Math.max(0, fbH - (ry + h * cs)), Math.max(0, w * cs), Math.max(0, h * cs));
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
        fx = (fbW - W * ps) / 2;
        fy = (fbH - H * ps) / 2;
        gx = fx + GX * ps;
        gy = fy + GY * ps;
        unitsW = GW * ps / cs;
        unitsH = GH * ps / cs;
        // la zona de las apps: bajo la barra y a la derecha del logo a esa altura (más abajo el logo es más estrecho)
        PadPage.Y = BAR + 4;
        PadPage.X = logoRight(PadPage.Y) + 4;
        PadPage.W = unitsW - PadPage.X - 4;
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
        g.pose().scale((float) (ps / gs), (float) (ps / gs), 1);
        g.blit(tex("frame"), 0, 0, W, H, 0, 0, 1568, 1003, 1568, 1003);
        g.pose().popPose();
        // el cristal: barra y página
        g.pose().pushPose();
        g.pose().translate(gx / gs, (gy + lift) / gs, 0);
        g.pose().scale((float) (cs / gs), (float) (cs / gs), 1);
        hoverNow = null;
        drawStatus(g, mx, my);
        page.render(g, mx, my, partialTick);
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

    private int backX() {
        return logoRight(0) + 4;
    }

    /** La barra de arriba: [◀] · título centrado · hora · monedas · ajustes. */
    private void drawStatus(GuiGraphics g, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int bx = backX(), by = 3;
        if (page != home) {
            boolean hover = PadUi.inside(mx, my, bx, by, 12, 12);
            if (hover) hover("§back");
            PadUi.box(g, bx, by, 12, 12, NAVY);
            PadUi.box(g, bx + 1, by + 1, 10, 10, hover ? 0xFFF6B628 : 0xFF3496FA);
            g.fill(bx + 3, by + 5, bx + 4, by + 7, 0xFFFFFFFF);
            g.fill(bx + 4, by + 4, bx + 5, by + 8, 0xFFFFFFFF);
            g.fill(bx + 5, by + 3, bx + 6, by + 9, 0xFFFFFFFF);
            g.fill(bx + 6, by + 5, bx + 9, by + 7, 0xFFFFFFFF);
        }
        String title = page.title();
        PadFont.drawCentered(g, title, unitsW / 2, 4, admin && page == home ? 0xFFD36A : 0xFFFFFF, true);
        int right = unitsW - 4;
        boolean gearHover = PadUi.inside(mx, my, right - 11, 2, 12, 13);
        if (gearHover) hover("§gear");
        blit(g, "gear", right - 10, 4);
        if (gearHover) g.fill(right - 11, 15, right + 1, 16, 0xFFFFE680);
        right -= 16;
        TFPadNet.State s = TFPadClient.state;
        if (s != null && s.balance() >= 0) {
            String coins = PadUi.THOUSANDS.format(s.balance());
            int w = PadFont.width(coins);
            PadFont.draw(g, coins, right - w, 5, 0xFFE680, true);
            right -= w + 14;
            blit(g, "coin", right, 4);
            right -= 10;
        }
        if (mc.level != null) {
            long t = Math.floorMod(mc.level.getDayTime(), 24000L);
            String time = String.format(Locale.ROOT, "%02d:%02d", (int) ((t / 1000 + 6) % 24), (int) (t % 1000 * 60 / 1000));
            int w = PadFont.width(time);
            PadFont.draw(g, time, right - w, 5, 0xFFFFFF, true);
            right -= w + 14;
            blit(g, t < 13000 ? "sun" : "moon", right, 4);
        }
    }

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
            if (page != home && PadUi.inside(mx, my, backX(), 3, 12, 12)) {
                back();
                return true;
            }
            if (PadUi.inside(mx, my, unitsW - 15, 2, 12, 13)) {
                if (!(page instanceof PadInfoPages.Ajustes)) openApp("ajustes");
                else back();
                return true;
            }
        }
        if (page.click(mx, my, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (page.drag(ax(mouseX), ay(mouseY), dy * gs / cs)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page.scroll(ax(mouseX), ay(mouseY), delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (page.key(keyCode, scanCode, modifiers)) return true;
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
        if (page.chr(c)) return true;
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
