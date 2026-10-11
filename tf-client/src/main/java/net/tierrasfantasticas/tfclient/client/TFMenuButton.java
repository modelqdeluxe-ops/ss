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
 * Botón del menú principal de TF (y de las pantallas que salen de él): placas opacas con textura en pixel art
 * (textures/gui/menu_buttons.png, de tools/gen_menu_buttons.py), 1 píxel de textura por píxel de interfaz, nítidas en
 * cualquier escala. Piedra pizarra con grano y marco de metal dorado biselado con remaches; el principal, de oro. Al
 * pasar el ratón (o con el foco del teclado) la placa se ilumina con un fundido suave; Salir se pone roja. Solo WhatsApp
 * y Discord llevan icono, en el color de su marca. La placa se corta en tres (4 px a cada lado y el centro), sin estirar.
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
    private static final int GOLD_TEXT = 0xFFE08A, RED_TEXT = 0xFFC9D2;
    private static final ResourceLocation TEX = new ResourceLocation(TFClient.MOD_ID, "textures/gui/menu_buttons.png");
    /** Placas de la textura (una debajo de otra, de 200x20). */
    private static final int NORMAL = 0, HOVER = 1, PRESSED = 2, PRIMARY_NORMAL = 3, PRIMARY_HOVER = 4, PRIMARY_PRESSED = 5,
            DANGER_HOVER = 6, DISABLED = 7, TEX_W = 200, TEX_H = 160, PLATE_H = 20, SIDE = 4;
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

        // Placa: la de reposo y, encima, la iluminada con la opacidad del ratón (fundido suave)
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        boolean primary = kind == Kind.PRIMARY;
        if (!this.active) {
            plate(g, DISABLED, alpha);
        } else if (pressed) {
            plate(g, primary ? PRIMARY_PRESSED : PRESSED, alpha);
        } else {
            plate(g, primary ? PRIMARY_NORMAL : NORMAL, alpha);
            if (e > 0.01f) plate(g, primary ? PRIMARY_HOVER : kind == Kind.DANGER ? DANGER_HOVER : HOVER, alpha * e);
        }
        g.setColor(1f, 1f, 1f, 1f);

        paintContent(g, mc.font, s, getX() * s, getY() * s, getHeight() * s, e, alpha);
    }

    /** Una placa de la textura en el botón: los 4 px de cada lado tal cual y el centro cortado (o repetido si es largo). */
    private void plate(GuiGraphics g, int index, float alpha) {
        int x = getX(), y = getY(), w = getWidth(), h = Math.min(getHeight(), PLATE_H), v = index * PLATE_H;
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(TEX, x, y, 0, v, SIDE, h, TEX_W, TEX_H);
        int mid = TEX_W - 2 * SIDE;
        for (int done = 0; done < w - 2 * SIDE; done += mid) {
            g.blit(TEX, x + SIDE + done, y, SIDE, v, Math.min(mid, w - 2 * SIDE - done), h, TEX_W, TEX_H);
        }
        g.blit(TEX, x + w - SIDE, y, TEX_W - SIDE, v, SIDE, h, TEX_W, TEX_H);
    }

    /** El color del icono (WhatsApp verde, Discord azul). */
    private int accent() {
        return kind == Kind.WHATSAPP ? WHATSAPP_GREEN : kind == Kind.DISCORD ? DISCORD_BLURPLE : GOLD_TEXT;
    }

    /** Icono (solo WhatsApp y Discord) y texto centrados juntos, con una sombra suave. */
    private void paintContent(GuiGraphics g, Font font, int s, int x, int y, int h, float e, float alpha) {
        if (alpha * 255f < 4f) return; // casi transparente: Minecraft pintaría el texto opaco
        boolean gold = kind == Kind.PRIMARY;
        int ink;
        if (!this.active) ink = 0x7C8299;
        else if (gold) ink = 0x2A1A04;
        else {
            int hotInk = kind == Kind.DANGER ? RED_TEXT : GOLD_TEXT;
            ink = TFMenuPaint.lerp(0xFFF3EFFF, 0xFF000000 | hotInk, e) & 0xFFFFFF;
        }

        MutableComponent text = Component.literal(getMessage().getString()).setStyle(Style.EMPTY.withFont(fontFor(s)));
        int textW = font.width(text);
        int maxW = getWidth() - 2 * Math.min(10, getWidth() / 12);
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
        if (gold) drawText(g, font, seq, gx, ty + shadowOff / (float) s, scale, TFMenuPaint.argb(0.6f * alpha, 0xFFF0C0));
        else drawText(g, font, seq, gx, ty + shadowOff / (float) s, scale, TFMenuPaint.argb(0.75f * alpha, 0x000000));
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
