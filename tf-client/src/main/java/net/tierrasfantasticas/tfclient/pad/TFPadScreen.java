package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import net.tierrasfantasticas.tfclient.TFConfig;

/**
 * El TF Pad: el marco del servidor con las apps dentro. Todo se dibuja en «píxeles de pad» (el marco mide 392x251;
 * marco.png es 4 veces más grande) y se escala a un número entero de píxeles de pantalla para que quede nítido.
 * Las posiciones son las de tools/pad/build_pad.py, que genera las texturas.
 * <ul>
 *   <li>Oficios, Protecciones, Tienda y GTS: las abre el servidor (sus ventanas de siempre).</li>
 *   <li>Monedero, Mi rango, Comunidad, Armario y Efectos: páginas del pad (Armario y Efectos se eligen en la web,
 *       que es la que sabe qué tiene comprado cada uno).</li>
 * </ul>
 */
public final class TFPadScreen extends Screen {
    private static final int W = 392;
    private static final int H = 251;
    private static final int NAVY = 0xFF18265C;
    private static final int TEXT = 0xFF18265C;
    private static final int MUTED = 0xFF4A6694;

    private record App(String id, int cx, int y) {}

    private static final List<App> APPS = List.of(
            new App("oficios", 84, 80), new App("protecciones", 140, 80), new App("tienda", 196, 80),
            new App("gts", 252, 80), new App("monedero", 308, 80),
            new App("armario", 112, 138), new App("efectos", 168, 138), new App("rango", 224, 138),
            new App("comunidad", 280, 138));
    private static final int TILE = 40;
    private static final int PANEL_X = 62, PANEL_Y = 90, PANEL_W = 268, PANEL_H = 100;
    private static final int BACK_X = 70, BACK_Y = 69;

    private static final Map<String, int[]> SIZES = new HashMap<>();
    /** 1.500 y 15.000 (el formato es-ES de Java deja 1500 sin punto). */
    private static final java.text.DecimalFormat THOUSANDS = new java.text.DecimalFormat("#,##0",
            java.text.DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES")));

    /** Botón de una página: textura btn_<id>, posición y qué hace. */
    private record Button(String id, int x, int y, Runnable action) {}

    private String page = "home";
    private final List<Button> buttons = new ArrayList<>();
    private float scale = 1;
    private int ox, oy;
    /** La app bajo el ratón (para el tic al pasar por encima). */
    private String hovered;
    /** Aviso del servidor y hasta cuándo se ve. */
    private String notice;
    private long noticeUntil;
    /** Al abrirlo, el pad sube unos píxeles (como sacarlo del bolsillo). */
    private final long openedAt = System.currentTimeMillis();

    public TFPadScreen() {
        super(Component.literal("TF Pad"));
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/" + name + ".png");
    }

    /** Tamaño de una textura del pad (se lee una vez). */
    private static int[] size(String name) {
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

    @Override
    protected void init() {
        float fit = Math.min(width / (float) W, height / (float) H);
        scale = fit >= 1 ? (float) Math.floor(fit) : fit;
        ox = Math.round((width - W * scale) / 2);
        oy = Math.round((height - H * scale) / 2);
        setPage(page);
    }

    private void setPage(String id) {
        page = id;
        buttons.clear();
        int bx = PANEL_X + 12, by = PANEL_Y + 72;
        String web = TFConfig.webUrl().replaceAll("/+$", "");
        switch (id) {
            case "monedero" -> {
                bx = addButton("oficios", bx, by, () -> TFPadClient.openServerApp("oficios"));
                bx = addButton("tienda", bx, by, () -> TFPadClient.openServerApp("tienda"));
                addButton("gts", bx, by, () -> TFPadClient.openServerApp("gts"));
            }
            case "rango" -> addButton("rangos", bx, by, () -> link(web + "/tienda#rangos"));
            case "comunidad" -> {
                bx = addButton("discord", bx, by, () -> link(web + "/discord"));
                bx = addButton("whatsapp", bx, by, () -> link(web + "/whatsapp"));
                addButton("web", bx, by, () -> link(web));
            }
            case "armario" -> addButton("abrirweb", bx, by, () -> link(web + "/cuenta"));
            case "efectos" -> addButton("abrirweb", bx, by, () -> link(web + "/tienda#vfx"));
            default -> {
            }
        }
    }

    private int addButton(String id, int x, int y, Runnable action) {
        buttons.add(new Button(id, x, y, action));
        return x + size("btn_" + id)[0] + 6;
    }

    private void link(String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmLinkScreen(ok -> {
            if (ok) Util.getPlatform().openUri(url);
            mc.setScreen(this);
        }, url, true));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        float p = Math.min(1F, (System.currentTimeMillis() - openedAt) / 160F);
        int lift = Math.round((1 - p) * (1 - p) * 14);
        double ax = (mouseX - ox) / scale, ay = (mouseY - oy - lift) / scale;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(ox, oy + lift, 0);
        g.pose().scale(scale, scale, 1);
        g.blit(tex("frame"), 0, 0, W, H, 0, 0, 1568, 1003, 1568, 1003);
        drawStatus(g);
        String over = null;
        if (page.equals("home")) {
            for (App app : APPS) {
                int x = app.cx - TILE / 2;
                boolean hover = inside(ax, ay, x, app.y, TILE, TILE);
                if (hover) over = app.id;
                blit(g, hover ? "tile_h" : "tile", x, app.y);
                blit(g, "icon_" + app.id, x + 4, app.y + 4);
                int lw = size("label_" + app.id)[0];
                blit(g, "label_" + app.id, app.cx - lw / 2, app.y + 45);
            }
        } else {
            boolean hoverBack = inside(ax, ay, BACK_X, BACK_Y, 16, 16);
            blit(g, hoverBack ? "back_h" : "back", BACK_X, BACK_Y);
            blit(g, "label_" + page, BACK_X + 22, BACK_Y + 4);
            blit(g, "panel", PANEL_X, PANEL_Y);
            blit(g, "icon_" + page, PANEL_X + 12, PANEL_Y + 12);
            drawPage(g);
            for (Button b : buttons) {
                int[] sz = size("btn_" + b.id);
                boolean hover = inside(ax, ay, b.x, b.y, sz[0], sz[1] - 2);
                if (hover) over = "btn_" + b.id;
                blit(g, hover ? "btn_" + b.id + "_h" : "btn_" + b.id, b.x, b.y);
            }
            if (hoverBack) over = "back";
        }
        if (over != null && !over.equals(hovered)) TFPadClient.sound("hover", 0.35F);
        hovered = over;
        drawNotice(g);
        g.pose().popPose();
        RenderSystem.disableBlend();
    }

    private void blit(GuiGraphics g, String name, int x, int y) {
        int[] sz = size(name);
        if (sz[0] == 0) return;
        RenderSystem.enableBlend(); // el texto la apaga
        g.blit(tex(name), x, y, 0, 0, sz[0], sz[1], sz[0], sz[1]);
    }

    public void showNotice(String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 4500;
        TFPadClient.sound("back", 0.6F);
    }

    /** El aviso del servidor: una tarjeta azul marino con borde de oro abajo de la pantalla del pad. */
    private void drawNotice(GuiGraphics g) {
        if (notice == null) return;
        long left = noticeUntil - System.currentTimeMillis();
        if (left <= 0) {
            notice = null;
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(notice), 236);
        int h = lines.size() * 10 + 8, w = 252, x = 70, y = 186 - h;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x + 1, y, x + w - 1, y + h, 0xFFB8741A);
        g.fill(x, y + 1, x + w, y + h - 1, 0xFFB8741A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, NAVY);
        int ty = y + 5;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + (w - font.width(line)) / 2, ty, 0xFFFFE9A8, false);
            ty += 10;
        }
        g.pose().popPose();
    }

    /** Arriba a la derecha: la hora del mundo y las monedas, como la barra de un móvil. */
    private void drawStatus(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int right = 330;
        TFPadNet.State s = TFPadClient.state;
        if (s != null && s.balance() >= 0) {
            String coins = THOUSANDS.format(s.balance());
            int tw = font.width(coins);
            outlined(g, coins, right - tw, 70, 0xFFFFE680);
            right -= tw + 14;
            blit(g, "coin", right, 68);
            right -= 12;
        }
        if (mc.level != null) {
            long t = mc.level.getDayTime() % 24000L;
            int hours = (int) ((t / 1000 + 6) % 24);
            int minutes = (int) (t % 1000 * 60 / 1000);
            String time = String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
            int tw = font.width(time);
            outlined(g, time, right - tw, 70, 0xFFFFFFFF);
            right -= tw + 14;
            blit(g, t < 13000 ? "sun" : "moon", right, 68);
        }
    }

    private void drawPage(GuiGraphics g) {
        TFPadNet.State s = TFPadClient.state;
        int x = PANEL_X + 56, y = PANEL_Y + 14, w = PANEL_W - 70;
        switch (page) {
            case "monedero" -> {
                small(g, "Tu saldo", x, y);
                String amount = s == null ? "…" : s.balance() < 0 ? "—"
                        : THOUSANDS.format(s.balance()) + " " + s.currency();
                big(g, amount, x, y + 12, 0xFFC27A10);
                text(g, "Gana monedas con los oficios, vendiendo en la tienda y en el GTS.", x, y + 36, w);
            }
            case "rango" -> {
                small(g, "Tu rango", x, y);
                if (s == null) big(g, "…", x, y + 12, TEXT);
                else if (s.rank().isEmpty()) big(g, "Sin rango", x, y + 12, MUTED);
                else big(g, s.rank(), x, y + 12, 0xFF000000 | darker(s.rankColor()));
                String homes = s != null && s.homes() >= 0 ? "Hogares: " + s.homes() + ". " : "";
                text(g, homes + "Mira en la web lo que trae cada rango.", x, y + 36, w);
            }
            case "comunidad" -> {
                big(g, "Únete a la comunidad", x, y + 2, TEXT);
                text(g, "Habla con los demás jugadores, entérate de los eventos y pide ayuda.", x, y + 26, w);
            }
            case "armario" -> {
                big(g, "Tu armario", x, y + 2, TEXT);
                text(g, "Elige lo que llevas puesto desde tu cuenta en la web. Se aplica al momento en el servidor.", x, y + 26, w);
            }
            case "efectos" -> {
                big(g, "Tus efectos", x, y + 2, TEXT);
                text(g, "Elige tu efecto de kill y tus habilidades en la web. Se aplica al momento en el servidor.", x, y + 26, w);
            }
            default -> {
            }
        }
    }

    /** Oscurece un poco los colores muy claros para que se lean sobre el panel blanco. */
    private static int darker(int rgb) {
        int r = rgb >> 16 & 255, gr = rgb >> 8 & 255, b = rgb & 255;
        int max = Math.max(r, Math.max(gr, b));
        if (max < 200) return rgb;
        return (r * 3 / 4) << 16 | (gr * 3 / 4) << 8 | (b * 3 / 4);
    }

    private void small(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, MUTED, false);
    }

    /** Texto grande (al doble); si no cabe dentro del panel, a tamaño normal y centrado en la misma altura. */
    private void big(GuiGraphics g, String text, int x, int y, int color) {
        int room = PANEL_X + PANEL_W - 8 - x;
        if (font.width(text) * 2 > room) {
            g.drawString(font, text, x, y + 4, color, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void text(GuiGraphics g, String text, int x, int y, int width) {
        for (FormattedCharSequence line : font.split(Component.literal(text), width)) {
            g.drawString(font, line, x, y, TEXT, false);
            y += 10;
        }
    }

    private void outlined(GuiGraphics g, String text, int x, int y, int color) {
        for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            g.drawString(font, text, x + d[0], y + d[1], NAVY, false);
        }
        g.drawString(font, text, x, y, color, false);
    }

    private static boolean inside(double x, double y, int x0, int y0, int w, int h) {
        return x >= x0 && y >= y0 && x < x0 + w && y < y0 + h;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Clics y teclas
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        double ax = (mouseX - ox) / scale, ay = (mouseY - oy) / scale;
        if (page.equals("home")) {
            for (App app : APPS) {
                if (inside(ax, ay, app.cx - TILE / 2, app.y, TILE, TILE)) {
                    if (TFPadNet.SERVER_APPS.contains(app.id)) {
                        TFPadClient.sound("select", 0.8F);
                        TFPadClient.openServerApp(app.id);
                    } else {
                        TFPadClient.sound("page", 0.8F);
                        setPage(app.id);
                    }
                    return true;
                }
            }
        } else {
            if (inside(ax, ay, BACK_X, BACK_Y, 16, 16)) {
                TFPadClient.sound("back", 0.8F);
                setPage("home");
                return true;
            }
            for (Button b : buttons) {
                int[] sz = size("btn_" + b.id);
                if (inside(ax, ay, b.x, b.y, sz[0], sz[1] - 2)) {
                    TFPadClient.sound("select", 0.8F);
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (TFPadClient.KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == 259 && !page.equals("home")) { // retroceso: volver a la portada
            TFPadClient.sound("back", 0.8F);
            setPage("home");
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Cerrar con Esc o con la tecla del pad: suena el «apagar». Al abrir una ventana del servidor no se llama. */
    @Override
    public void onClose() {
        TFPadClient.sound("close", 0.8F);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
