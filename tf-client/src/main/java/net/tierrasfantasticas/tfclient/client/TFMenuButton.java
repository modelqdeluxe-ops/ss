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
 * Botón del menú principal de TF (y de las pantallas que salen de él). Diseño:
 * <ul>
 *   <li><b>Principal</b> (entrar al servidor): placa de oro con degradado, luz arriba, labio oscuro abajo, filo
 *   metálico (claro arriba, oscuro abajo), halo dorado y la línea luminosa del emblema que la cruza; letras oscuras.</li>
 *   <li><b>Secundario</b>: cristal azul noche semitransparente con un reflejo arriba y un filo fino claro. Al pasar el
 *   ratón (o con el foco del teclado) sube un poco, el filo se vuelve dorado, aparece un halo dorado y lo cruza un
 *   destello suave una vez.</li>
 *   <li><b>Salir</b>: como el secundario, pero el filo y el halo se vuelven rosa rojizo.</li>
 * </ul>
 * Todo se dibuja en píxeles reales (TFMenuPaint, TFMenuIcons) y la letra Cinzel se elige con los glifos hechos para la
 * escala de interfaz actual: nítido en cualquier escala. Pulsado: baja un poco y se oscurece.
 */
public class TFMenuButton extends Button {
    public static final ResourceLocation FONT = new ResourceLocation(TFClient.MOD_ID, "cinzel");
    /** Cinzel con los glifos rasterizados a 1..6 píxeles reales por píxel de interfaz (font/cinzel_N.json; 4 = FONT). */
    private static final ResourceLocation[] FONT_AT_SCALE = {
            new ResourceLocation(TFClient.MOD_ID, "cinzel_1"), new ResourceLocation(TFClient.MOD_ID, "cinzel_2"),
            new ResourceLocation(TFClient.MOD_ID, "cinzel_3"), FONT,
            new ResourceLocation(TFClient.MOD_ID, "cinzel_5"), new ResourceLocation(TFClient.MOD_ID, "cinzel_6")};

    public enum Kind { PRIMARY, SECONDARY, DANGER }

    /** Separación entre icono y texto, en píxeles de interfaz. */
    private static final int GAP = 4;
    /** El texto va en y + alto/2 - TEXT_UP: así las letras de Cinzel quedan centradas a la vista. */
    private static final float TEXT_UP = 3f;
    private static final float HOVER_MS = 110f;
    private static final long HOVER_SWEEP_MS = 650L;

    private final Kind kind;
    private final TFMenuIcons.Icon icon;
    /** 0..1: cuánto está encendido el estado de ratón encima (se anima). */
    private float hover;
    private long lastFrame;
    private long sweepStart;
    private boolean wasHot;

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
                button -> TFServer.join(Minecraft.getInstance().screen), Kind.PRIMARY, TFMenuIcons.Icon.PLAY);
    }

    /** Botón de la web del servidor. */
    public static TFMenuButton web(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("Web"), button -> openWeb(), Kind.SECONDARY, TFMenuIcons.Icon.WEB);
    }

    /** Botón del Discord del servidor (invitación del servidor, no de una persona). */
    public static TFMenuButton discord(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("Discord"), button -> TFDiscord.open(), Kind.SECONDARY, TFMenuIcons.Icon.DISCORD);
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
        if (hot && !wasHot) sweepStart = now;
        wasHot = hot;
        boolean pressed = this.active && this.isHovered() && mc.mouseHandler.isLeftPressed();
        float e = this.active ? hover * hover * (3f - 2f * hover) : 0f;
        int lift = pressed ? -Math.max(1, s / 2) : Math.round(e * s);
        float alpha = this.alpha;

        // Placa, en píxeles reales
        int r = 3 * s;
        int t = s < 3 ? 1 : 2;
        int x = getX() * s;
        int y = getY() * s - lift;
        int w = getWidth() * s;
        int h = getHeight() * s;
        g.pose().pushPose();
        g.pose().scale(1f / s, 1f / s, 1f);
        if (kind == Kind.PRIMARY) paintGold(g, x, y, w, h, r, t, s, e, pressed, alpha);
        else paintGlass(g, x, y, w, h, r, t, s, e, pressed, alpha);
        g.pose().popPose();

        // Destellos: el de la placa de oro va justo después del del emblema; los demás, una vez al pasar el ratón
        g.pose().pushPose();
        g.pose().translate(0f, -lift / (float) s, 0f);
        if (kind == Kind.PRIMARY && this.active) {
            long cycle = now % TFLogoRenderer.SHINE_CYCLE_MS;
            long start = TFLogoRenderer.SHINE_MS + 200L;
            TFMenuPaint.sweep(g, getX(), getY(), getWidth(), getHeight(), (cycle - start) / 1800f, 0.42f * alpha);
        } else if (hot) {
            TFMenuPaint.sweep(g, getX(), getY(), getWidth(), getHeight(), (now - sweepStart) / (float) HOVER_SWEEP_MS, 0.16f * alpha);
        }
        g.pose().popPose();

        paintContent(g, mc.font, s, x, y, h, e, lift, alpha);
    }

    /** Placa de oro del botón principal. */
    private void paintGold(GuiGraphics g, int x, int y, int w, int h, int r, int t, int s, float e, boolean pressed, float alpha) {
        TFMenuPaint.halo(g, x, y, w, h, r, 2 * s, TFMenuPaint.argb(0.45f * alpha, 0x05040A));
        TFMenuPaint.halo(g, x, y, w, h, r, 4 * s, TFMenuPaint.argb((this.active ? 0.20f + 0.22f * e : 0.08f) * alpha, 0xFFB43C));
        int top = TFMenuPaint.lerp(0xFFFFE49A, 0xFFFFF0B8, e);
        int mid = TFMenuPaint.lerp(0xFFF2BC48, 0xFFFFCF60, e);
        int bot = TFMenuPaint.lerp(0xFFCC841C, 0xFFE09A2A, e);
        if (pressed) {
            top = TFMenuPaint.lerp(top, bot, 0.35f);
            mid = TFMenuPaint.lerp(mid, bot, 0.35f);
        }
        if (!this.active) {
            top = TFMenuPaint.lerp(top, 0xFF6A6A72, 0.7f);
            mid = TFMenuPaint.lerp(mid, 0xFF5A5A62, 0.7f);
            bot = TFMenuPaint.lerp(bot, 0xFF45454C, 0.7f);
        }
        int line = Math.max(1, s / 2);
        int topA = TFMenuPaint.withAlpha(top, alpha), midA = TFMenuPaint.withAlpha(mid, alpha), botA = TFMenuPaint.withAlpha(bot, alpha);
        int lightA = TFMenuPaint.argb(0.80f * alpha, 0xFFF9E0), lipA = TFMenuPaint.argb(0.45f * alpha, 0x8A4A06);
        int ringTop = TFMenuPaint.argb(alpha, 0xFFF4C8), ringBot = TFMenuPaint.argb(alpha, 0x6E3E06);
        g.drawManaged(() -> {
            TFMenuPaint.roundFill(g, x, y, w, h, r, t, 0, h / 2, topA, midA);
            TFMenuPaint.roundFill(g, x, y, w, h, r, t, h / 2, h, midA, botA);
            // Luz arriba y labio oscuro abajo, dentro del filo
            g.fill(x + r, y + t, x + w - r, y + t + line, lightA);
            g.fill(x + r, y + h - t - line, x + w - r, y + h - t, lipA);
            TFMenuPaint.roundRing(g, x, y, w, h, r, t, ringTop, ringBot);
        });
    }

    /** Placa de cristal de los botones secundarios (y de Salir, con su color). */
    private void paintGlass(GuiGraphics g, int x, int y, int w, int h, int r, int t, int s, float e, boolean pressed, float alpha) {
        boolean danger = kind == Kind.DANGER;
        int accentHi = danger ? 0xFF9DB0 : 0xFFE29A;
        int accentLo = danger ? 0xC2435C : 0xD9A040;
        TFMenuPaint.halo(g, x, y, w, h, r, 2 * s, TFMenuPaint.argb(0.38f * alpha, 0x04050C));
        if (e > 0f) TFMenuPaint.halo(g, x, y, w, h, r, 3 * s, TFMenuPaint.argb(0.30f * e * alpha, danger ? 0xFF4F70 : 0xFFBE50));
        int top = TFMenuPaint.lerp(TFMenuPaint.argb(0.64f, 0x1D2452), TFMenuPaint.argb(0.80f, 0x2B3572), e);
        int bot = TFMenuPaint.lerp(TFMenuPaint.argb(0.80f, 0x0A0D21), TFMenuPaint.argb(0.88f, 0x121735), e);
        if (pressed) top = TFMenuPaint.lerp(top, bot, 0.6f);
        int ringTop = TFMenuPaint.lerp(TFMenuPaint.argb(0.42f, 0xB9C6FF), TFMenuPaint.argb(0.95f, accentHi), e);
        int ringBot = TFMenuPaint.lerp(TFMenuPaint.argb(0.16f, 0x8494D6), TFMenuPaint.argb(0.70f, accentLo), e);
        if (!this.active) {
            ringTop = TFMenuPaint.argb(0.20f, 0x9AA0B8);
            ringBot = TFMenuPaint.argb(0.10f, 0x7A8098);
        }
        int topA = TFMenuPaint.withAlpha(top, alpha), botA = TFMenuPaint.withAlpha(bot, alpha);
        int glossTop = TFMenuPaint.argb((0.07f + 0.05f * e) * alpha, 0xFFFFFF), glossBot = 0x00FFFFFF;
        int ringTopA = TFMenuPaint.withAlpha(ringTop, alpha), ringBotA = TFMenuPaint.withAlpha(ringBot, alpha);
        g.drawManaged(() -> {
            TFMenuPaint.roundFill(g, x, y, w, h, r, t, 0, h, topA, botA);
            TFMenuPaint.roundFill(g, x, y, w, h, r, t, t, h / 2, glossTop, glossBot); // reflejo del cristal
            TFMenuPaint.roundRing(g, x, y, w, h, r, t, ringTopA, ringBotA);
        });
    }

    /** Icono y texto centrados juntos, con sombra suave (en la placa de oro, un brillo claro debajo). */
    private void paintContent(GuiGraphics g, Font font, int s, int x, int y, int h, float e, int lift, float alpha) {
        if (alpha * 255f < 4f) return; // casi transparente: Minecraft pintaría el texto opaco
        boolean gold = kind == Kind.PRIMARY;
        boolean danger = kind == Kind.DANGER;
        int ink;
        int inkIcon;
        if (!this.active) {
            ink = 0x7C8299;
            inkIcon = 0x5E6580;
        } else if (gold) {
            ink = 0x2A1600;
            inkIcon = 0x2A1600;
        } else {
            ink = TFMenuPaint.lerp(0xFFEEF0FA, danger ? 0xFFFFE9EE : 0xFFFFF1CF, e) & 0xFFFFFF;
            inkIcon = TFMenuPaint.lerp(0xFFAFC0F0, danger ? 0xFFFF9DB0 : 0xFFFFE29A, e) & 0xFFFFFF;
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
            g.pose().pushPose();
            g.pose().scale(1f / s, 1f / s, 1f);
            if (!gold) TFMenuIcons.draw(g, icon, ix, iy + shadowOff, s, TFMenuPaint.argb(0.45f * alpha, 0x000000));
            TFMenuIcons.draw(g, icon, ix, iy, s, TFMenuPaint.argb(alpha, inkIcon));
            g.pose().popPose();
            gx += TFMenuIcons.SIZE + GAP;
        }

        FormattedCharSequence seq = text.getVisualOrderText();
        float ty = snap(getY() + getHeight() / 2f - TEXT_UP * scale, s) - lift / (float) s;
        if (gold) {
            drawText(g, font, seq, gx, ty + shadowOff / (float) s, scale, TFMenuPaint.argb(0.55f * alpha, 0xFFF6CE));
        } else {
            drawText(g, font, seq, gx, ty + shadowOff / (float) s, scale, TFMenuPaint.argb(0.55f * alpha, 0x000000));
            if (s >= 3) drawText(g, font, seq, gx, ty + 2 * shadowOff / (float) s, scale, TFMenuPaint.argb(0.25f * alpha, 0x000000));
        }
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
