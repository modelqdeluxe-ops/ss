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
 * El TF Pad: el marco del servidor con sus apps dentro. Todo se dibuja en «píxeles de pad» (el marco mide 392x251;
 * marco.png es 4 veces más grande) y se escala a un número entero de píxeles de pantalla para que quede nítido.
 * <ul>
 *   <li>Arriba, la barra: el nombre de la página (con la flecha para volver), la hora del mundo, tus monedas y el
 *       engranaje de Ajustes.</li>
 *   <li>Portada: 20 apps en dos páginas de 5x2 ({@link PadHomePage}).</li>
 *   <li>Oficios, Protección, Tienda y GTS abren sus ventanas del servidor; Misiones, Cazas, Kits, Viajes, Explorar,
 *       Clanes, Títulos, Jugadores, Ranking y Ayuda las dibuja el servidor dentro del pad ({@link PadViewPage});
 *       Comunidad y Cámara tienen sus páginas; Monedero, Mi rango, Armario, Efectos y Ajustes, las suyas.</li>
 * </ul>
 */
public final class TFPadScreen extends Screen {
    static final int W = 392;
    static final int H = 251;
    static final int NAVY = 0xFF18265C;
    /** Apps que son ventanas del servidor (cofres). */
    static final Set<String> CHEST_APPS = Set.of("oficios", "protecciones", "tienda", "gts");
    /** Apps que dibuja el servidor dentro del pad. */
    static final Set<String> VIEW_APPS = Set.of("misiones", "cazas", "kits", "viajes", "explorar", "clanes", "titulos",
            "jugadores", "ranking", "ayuda");
    private static final Map<String, int[]> SIZES = new HashMap<>();

    private float scale = 1;
    private int ox, oy;
    private PadPage page;
    private final PadHomePage home;
    private String hovered, hoverNow;
    private String notice;
    private long noticeUntil;
    private final long openedAt = System.currentTimeMillis();

    public TFPadScreen() {
        super(Component.literal("TF Pad"));
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

    /** Recorte en píxeles de pad (se pasa a coordenadas de pantalla). */
    void scissor(GuiGraphics g, int x, int y, int w, int h) {
        g.enableScissor(Math.round(ox + x * scale), Math.round(oy + lift() + y * scale),
                Math.round(ox + (x + w) * scale), Math.round(oy + lift() + (y + h) * scale));
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

    void back() {
        sound("back", 0.8F);
        setPage(home);
    }

    /** Abre una app de la portada. */
    void openApp(String id) {
        if (CHEST_APPS.contains(id)) {
            sound("select", 0.8F);
            TFPadClient.openServerApp(id);
            return;
        }
        sound("page", 0.8F);
        if (VIEW_APPS.contains(id)) {
            setPage(new PadViewPage(this, id));
            TFPadClient.openServerApp(id);
            return;
        }
        switch (id) {
            case "monedero" -> setPage(new PadInfoPages.Monedero(this));
            case "rango" -> setPage(new PadInfoPages.Rango(this));
            case "armario", "efectos" -> setPage(new PadInfoPages.Web(this, id));
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
        float fit = Math.min(width / (float) W, height / (float) H);
        scale = fit >= 1 ? (float) Math.floor(fit) : fit;
        ox = Math.round((width - W * scale) / 2);
        oy = Math.round((height - H * scale) / 2);
    }

    private int lift() {
        if (!PadSettings.animations) return 0;
        float p = Math.min(1F, (System.currentTimeMillis() - openedAt) / 160F);
        return Math.round((1 - p) * (1 - p) * 14);
    }

    private double ax(double mx) {
        return (mx - ox) / scale;
    }

    private double ay(double my) {
        return (my - oy - lift()) / scale;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        double mx = ax(mouseX), my = ay(mouseY);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(ox, oy + lift(), 0);
        g.pose().scale(scale, scale, 1);
        g.blit(tex("frame"), 0, 0, W, H, 0, 0, 1568, 1003, 1568, 1003);
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

    /** La barra de arriba: [◀] título · hora · monedas · ajustes. */
    private void drawStatus(GuiGraphics g, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int x = 96;
        if (page != home) {
            boolean hover = PadUi.inside(mx, my, 86, 66, 12, 12);
            if (hover) hover("§back");
            PadUi.box(g, 86, 66, 12, 12, NAVY);
            PadUi.box(g, 87, 67, 10, 10, hover ? 0xFFF6B628 : 0xFF3496FA);
            // flecha ◀
            g.fill(89, 71, 90, 73, 0xFFFFFFFF);
            g.fill(90, 70, 91, 74, 0xFFFFFFFF);
            g.fill(91, 69, 92, 75, 0xFFFFFFFF);
            g.fill(92, 71, 95, 73, 0xFFFFFFFF);
            x = 102;
        }
        PadFont.draw(g, page.title(), x, 67, 0xFFFFFF, true);
        int right = 330;
        boolean gearHover = PadUi.inside(mx, my, right - 11, 66, 12, 12);
        if (gearHover) hover("§gear");
        blit(g, "gear", right - 10, 67);
        if (gearHover) g.fill(right - 11, 78, right + 1, 79, 0xFFFFE680);
        right -= 16;
        TFPadNet.State s = TFPadClient.state;
        if (s != null && s.balance() >= 0) {
            String coins = PadInfoPages.THOUSANDS.format(s.balance());
            int w = PadFont.width(coins);
            PadFont.draw(g, coins, right - w, 68, 0xFFE680, true);
            right -= w + 14;
            blit(g, "coin", right, 67);
            right -= 10;
        }
        if (mc.level != null) {
            long t = Math.floorMod(mc.level.getDayTime(), 24000L);
            String time = String.format(Locale.ROOT, "%02d:%02d", (int) ((t / 1000 + 6) % 24), (int) (t % 1000 * 60 / 1000));
            int w = PadFont.width(time);
            PadFont.draw(g, time, right - w, 68, 0xFFFFFF, true);
            right -= w + 14;
            blit(g, t < 13000 ? "sun" : "moon", right, 67);
        }
    }

    /** Aviso del servidor: tarjeta azul marino con borde de oro abajo de la pantalla del pad. */
    private void drawNotice(GuiGraphics g) {
        if (notice == null) return;
        if (System.currentTimeMillis() > noticeUntil) {
            notice = null;
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(notice), 236);
        int h = lines.size() * 10 + 8, w = 252, x = 70, y = 188 - h;
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
            if (page != home && PadUi.inside(mx, my, 86, 66, 12, 12)) {
                back();
                return true;
            }
            if (PadUi.inside(mx, my, 319, 66, 12, 12)) {
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
        if (page instanceof PadHomePage h) h.released(ax(mouseX));
        return super.mouseReleased(mouseX, mouseY, button);
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
        sound("close", 0.8F);
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
