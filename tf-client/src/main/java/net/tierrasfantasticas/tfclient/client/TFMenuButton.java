package net.tierrasfantasticas.tfclient.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;

/**
 * Botón del menú principal de TF (y de las pantallas que salen de él). Diseño plano y sobrio:
 * <ul>
 *   <li><b>Principal</b> (entrar al servidor): dorado liso, borde fino más oscuro y una línea de luz arriba; letras
 *   oscuras.</li>
 *   <li><b>Secundario</b>: panel oscuro casi negro semitransparente con un borde fino claro. Al pasar el ratón (o con el
 *   foco del teclado) el panel se aclara un poco y el borde y el texto se vuelven dorados.</li>
 *   <li><b>Salir</b>: igual, pero en rojo al pasar el ratón.</li>
 *   <li><b>WhatsApp y Discord</b>: los únicos con icono, en el color de cada marca (verde y azul), que es también el
 *   del borde al pasar el ratón.</li>
 * </ul>
 * Sin degradados de cristal, halos ni destellos. Todo se dibuja en píxeles reales (TFMenuPaint, TFMenuIcons) y la letra
 * Cinzel se elige con los glifos hechos para la escala de interfaz actual: nítido en cualquier escala. Pulsado: más
 * oscuro.
 */
public class TFMenuButton extends Button {
    public static final ResourceLocation FONT = new ResourceLocation(TFClient.MOD_ID, "cinzel");
    /** Cinzel con los glifos rasterizados a 1..6 píxeles reales por píxel de interfaz (font/cinzel_N.json; 4 = FONT). */
    private static final ResourceLocation[] FONT_AT_SCALE = {
            new ResourceLocation(TFClient.MOD_ID, "cinzel_1"), new ResourceLocation(TFClient.MOD_ID, "cinzel_2"),
            new ResourceLocation(TFClient.MOD_ID, "cinzel_3"), FONT,
            new ResourceLocation(TFClient.MOD_ID, "cinzel_5"), new ResourceLocation(TFClient.MOD_ID, "cinzel_6")};

    public enum Kind { PRIMARY, SECONDARY, DANGER, WHATSAPP, DISCORD }

    /** Separación entre icono y texto, en píxeles de interfaz. */
    private static final int GAP = 4;
    /** El texto va en y + alto/2 - TEXT_UP: así las letras de Cinzel quedan centradas a la vista. */
    private static final float TEXT_UP = 3f;
    private static final float HOVER_MS = 110f;
    private static final int GOLD = 0xE9C46A, GOLD_TEXT = 0xFFE3A0, RED = 0xE2556B, RED_TEXT = 0xFFC9D2;
    private static final int WHATSAPP_GREEN = 0x25D366, DISCORD_BLURPLE = 0x5865F2;

    private final Kind kind;
    private final TFMenuIcons.Icon icon;
    /** 0..1: cuánto está encendido el estado de ratón encima (se anima). */
    private float hover;
    private long lastFrame;

    public TFMenuButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
        this(x, y, width, height, message, onPress, primary ? Kind.PRIMARY : Kind.SECONDARY, null);
    }

    public TFMenuButton(int x, int y, int width, int height, Component message, OnPress onPress, Kind kind, TFMenuIcons.Icon icon) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.kind = kind;
        this.icon = icon;
    }

    /** Texto con la fuente de TF. */
    public static MutableComponent label(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT));
    }

    /** Botón principal: conexión directa a Tierras Fantásticas. */
    public static TFMenuButton server(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label(TFConfig.serverName()),
                button -> TFServer.join(Minecraft.getInstance().screen), Kind.PRIMARY, null);
    }

    /** Botón de la web del servidor. */
    public static TFMenuButton web(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("Web"), button -> openWeb(), Kind.SECONDARY, null);
    }

    /** Botón del Discord del servidor (invitación del servidor, no de una persona). */
    public static TFMenuButton discord(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("Discord"), button -> TFDiscord.open(), Kind.DISCORD, TFMenuIcons.Icon.DISCORD);
    }

    /** Botón del grupo de WhatsApp del servidor (la web redirige /whatsapp al enlace del grupo). */
    public static TFMenuButton whatsapp(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("WhatsApp"),
                button -> TFDiscord.openUrl(TFConfig.webUrl().replaceAll("/+$", "") + "/whatsapp"), Kind.WHATSAPP, TFMenuIcons.Icon.WHATSAPP);
    }

    private static void openWeb() {
        TFDiscord.openUrl(TFConfig.webUrl());
    }

    /** Copia un botón del menú normal con el diseño de TF y el mismo efecto al pulsarlo. */
    public static TFMenuButton wrapping(Button original, int x, int y, int width, String text, TFMenuIcons.Icon icon, Kind kind) {
        TFMenuButton button = new TFMenuButton(x, y, width, 20, label(text), pressed -> original.onPress(), kind, icon);
        button.active = original.active;
        button.setTooltip(original.getTooltip());
        return button;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int s = Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
        long now = Util.getMillis();
        boolean hot = this.active && this.isHoveredOrFocused();
        float dt = lastFrame == 0L ? 1000f : Math.min(250f, now - lastFrame);
        lastFrame = now;
        float step = dt / HOVER_MS;
        hover = hot ? Math.min(1f, hover + step) : Math.max(0f, hover - step);
        boolean pressed = this.active && this.isHovered() && mc.mouseHandler.isLeftPressed();
        float e = this.active ? hover * hover * (3f - 2f * hover) : 0f;
        float alpha = this.alpha;

        // Placa, en píxeles reales
        int r = 2 * s;
        int t = s < 4 ? 1 : 2;
        int x = getX() * s;
        int y = getY() * s;
        int w = getWidth() * s;
        int h = getHeight() * s;
        g.pose().pushPose();
        g.pose().scale(1f / s, 1f / s, 1f);
        if (kind == Kind.PRIMARY) paintGold(g, x, y, w, h, r, t, s, e, pressed, alpha);
        else paintPanel(g, x, y, w, h, r, t, e, pressed, alpha);
        g.pose().popPose();

        paintContent(g, mc.font, s, x, y, h, e, alpha);
    }

    /** El color del borde y del texto al pasar el ratón. */
    private int accent() {
        return switch (kind) {
            case DANGER -> RED;
            case WHATSAPP -> WHATSAPP_GREEN;
            case DISCORD -> DISCORD_BLURPLE;
            default -> GOLD;
        };
    }

    /** Dorado liso del botón principal. */
    private void paintGold(GuiGraphics g, int x, int y, int w, int h, int r, int t, int s, float e, boolean pressed, float alpha) {
        int top = TFMenuPaint.lerp(0xFFF3C553, 0xFFFFD46A, e);
        int bot = TFMenuPaint.lerp(0xFFDDA437, 0xFFEBB445, e);
        if (pressed) {
            top = TFMenuPaint.lerp(top, 0xFFB98322, 0.5f);
            bot = TFMenuPaint.lerp(bot, 0xFFB98322, 0.5f);
        }
        if (!this.active) {
            top = TFMenuPaint.lerp(top, 0xFF5E5E66, 0.75f);
            bot = TFMenuPaint.lerp(bot, 0xFF4C4C54, 0.75f);
        }
        int topA = TFMenuPaint.withAlpha(top, alpha), botA = TFMenuPaint.withAlpha(bot, alpha);
        int lightA = TFMenuPaint.argb(0.55f * alpha, 0xFFF3C8);
        int ringA = TFMenuPaint.argb(0.95f * alpha, 0x7A4E0E);
        int line = Math.max(1, s / 2);
        g.drawManaged(() -> {
            TFMenuPaint.roundFill(g, x, y, w, h, r, t, 0, h, topA, botA);
            g.fill(x + r, y + t, x + w - r, y + t + line, lightA); // línea de luz arriba
            TFMenuPaint.roundRing(g, x, y, w, h, r, t, ringA, ringA);
        });
    }

    /** Panel oscuro de los demás botones. */
    private void paintPanel(GuiGraphics g, int x, int y, int w, int h, int r, int t, float e, boolean pressed, float alpha) {
        int fill = TFMenuPaint.lerp(TFMenuPaint.argb(0.74f, 0x0A0C14), TFMenuPaint.argb(0.84f, 0x171B28), e);
        if (pressed) fill = TFMenuPaint.argb(0.85f, 0x05060A);
        int ring = TFMenuPaint.lerp(TFMenuPaint.argb(0.22f, 0xFFFFFF), TFMenuPaint.argb(0.95f, accent()), e);
        if (!this.active) ring = TFMenuPaint.argb(0.10f, 0xFFFFFF);
        int fillA = TFMenuPaint.withAlpha(fill, alpha), ringA = TFMenuPaint.withAlpha(ring, alpha);
        g.drawManaged(() -> {
            TFMenuPaint.roundFill(g, x, y, w, h, r, t, 0, h, fillA, fillA);
            TFMenuPaint.roundRing(g, x, y, w, h, r, t, ringA, ringA);
        });
    }

    /** Icono (solo WhatsApp y Discord) y texto centrados juntos, con una sombra suave. */
    private void paintContent(GuiGraphics g, Font font, int s, int x, int y, int h, float e, float alpha) {
        if (alpha * 255f < 4f) return; // casi transparente: Minecraft pintaría el texto opaco
        boolean gold = kind == Kind.PRIMARY;
        int ink;
        if (!this.active) ink = 0x7C8299;
        else if (gold) ink = 0x2A1A04;
        else {
            int hotInk = switch (kind) {
                case DANGER -> RED_TEXT;
                case WHATSAPP, DISCORD -> 0xFFFFFF;
                default -> GOLD_TEXT;
            };
            ink = TFMenuPaint.lerp(0xFFE9ECF5, 0xFF000000 | hotInk, e) & 0xFFFFFF;
        }

        MutableComponent text = Component.literal(getMessage().getString()).setStyle(Style.EMPTY.withFont(fontFor(s)));
        int textW = font.width(text);
        int maxW = getWidth() - 2 * Math.min(10, getWidth() / 6);
        boolean withIcon = icon != null && textW + TFMenuIcons.SIZE + GAP <= maxW;
        int group = textW + (withIcon ? TFMenuIcons.SIZE + GAP : 0);
        float scale = group > maxW && group > 0 ? Math.max(0.6f, maxW / (float) group) : 1f;
        float gx = snap(getX() + (getWidth() - group * scale) / 2f, s);
        int shadowOff = Math.max(1, s / 2);

        if (withIcon) {
            int n = TFMenuIcons.SIZE * s;
            int ix = Math.round(gx * s);
            int iy = y + (h - n) / 2;
            int brand = this.active ? accent() : 0x5E6580;
            g.pose().pushPose();
            g.pose().scale(1f / s, 1f / s, 1f);
            TFMenuIcons.draw(g, icon, ix, iy, s, TFMenuPaint.argb(alpha, brand));
            g.pose().popPose();
            gx += TFMenuIcons.SIZE + GAP;
        }

        FormattedCharSequence seq = text.getVisualOrderText();
        float ty = snap(getY() + getHeight() / 2f - TEXT_UP * scale, s);
        if (!gold) drawText(g, font, seq, gx, ty + shadowOff / (float) s, scale, TFMenuPaint.argb(0.5f * alpha, 0x000000));
        drawText(g, font, seq, gx, ty, scale, TFMenuPaint.argb(alpha, ink));
    }

    private static void drawText(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, float scale, int argb) {
        if ((argb >>> 24) < 4) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0f, 0f, argb, false);
        g.pose().popPose();
    }

    /** Redondea a un píxel real. */
    private static float snap(float gui, int s) {
        return Math.round(gui * s) / (float) s;
    }

    /** Cinzel con los glifos hechos para esta escala de interfaz (1:1 con la pantalla). */
    private static ResourceLocation fontFor(int s) {
        return FONT_AT_SCALE[Mth.clamp(s, 1, FONT_AT_SCALE.length) - 1];
    }
}
