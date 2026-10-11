package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
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
 * Botón de los menús de TF. En el menú principal cada botón es una placa de la lámina del dueño (TFMenuPlates:
 * Tierras Fantásticas de madera con gemas y faroles, Mundo local y Mods azules, Opciones gris, Salir rojo, WhatsApp
 * verde, Web morado y Discord azul). En las demás pantallas (sin placa), la placa de piedra de ajustes (TFButtonTheme).
 *
 * Animación: con el ratón encima la placa sube un poco, se ilumina con un fundido suave y la cruza una línea de luz;
 * al pulsar se hunde y se oscurece. Las gemas echan una chispa de vez en cuando (y brillan más con el ratón), los
 * faroles titilan, y por Tierras Fantásticas pasa un destello cada pocos segundos. Texto Cinzel con contorno oscuro,
 * del mismo tamaño y color en todos los botones (el mayor que cabe en todos; nítido); iconos en WhatsApp, Web y Discord.
 */
public class TFMenuButton extends Button {
    public static final ResourceLocation FONT = new ResourceLocation(TFClient.MOD_ID, "cinzel");
    /**
     * Cinzel con los glifos rasterizados a 1..12 píxeles reales por píxel de interfaz (font/cinzel_N.json; 4 = FONT):
     * con la letra a n/escala se dibuja 1:1 en pantalla, nítida.
     */
    private static final ResourceLocation[] FONT_AT_SCALE = new ResourceLocation[12];
    static {
        for (int i = 0; i < FONT_AT_SCALE.length; i++) {
            FONT_AT_SCALE[i] = i == 3 ? FONT : new ResourceLocation(TFClient.MOD_ID, "cinzel_" + (i + 1));
        }
    }

    public enum Kind { PRIMARY, SECONDARY, DANGER, WHATSAPP, DISCORD }

    /** Las placas del menú principal en el orden de la composición. */
    public static final String TF = "tf", BLUE_1 = "blue1", BLUE_2 = "blue2", GRAY = "gray", RED = "red", GREEN = "green",
            PURPLE = "purple", BLUE = "blueb";
    private static final String[] MAIN = {TF, BLUE_1, BLUE_2, GRAY, RED, GREEN, PURPLE, BLUE};
    /** Cuánto se ensancha cada fila respecto a la lámina (solo por sus zonas lisas): los de abajo, para que quepa el texto. */
    private static final float[] WIDEN = {1.25f, 1.25f, 1.25f, 1.4f, 1.4f, 1.5f, 1.5f, 1.5f};
    /** Escala máxima de la lámina (interfaz por píxel): Mundo local de unos 21 de alto. */
    private static final float K_MAX = 0.15f;
    private static final int LOGO_MIN = 80, BOTTOM_MARGIN = 6;
    /** Alto de las mayúsculas de Cinzel a escala 1 (interfaz) y cuánto del hueco interior ocupan como mucho. */
    private static final float CAP_HEIGHT = 5.3f, CAP_FILL = 0.56f, INTERIOR = 0.7f;
    /** Tamaño de letra común a todos los botones del menú principal (el mayor que cabe en todos). Ver mainLayout. */
    private static float mainTextSize = 1f;

    /** Separación entre icono y texto, en píxeles de interfaz. */
    private static final int GAP = 3;
    /** El texto se dibuja en el centro - TEXT_UP (centrado a la vista de Cinzel). */
    private static final float TEXT_UP = 3.5f;
    private static final float HOVER_MS = 140f, SWEEP_MS = 850f;
    private static final long TF_SHINE_EVERY = 6500L, SPARK_CYCLE = 5200L, SPARK_MS = 900L;
    /** Mismo color en todos los botones; con el ratón, un poco más claro (la placa es la que se ilumina). */
    private static final int INK = 0xFFF3DC, INK_HOT = 0xFFFFFF, OUTLINE = 0x140A04;

    private final Kind kind;
    private final TFMenuIcons.Icon icon;
    /** Placa del menú principal (null: placa de piedra). */
    private final String plate;
    /** 0..1: cuánto está encendido el estado de ratón encima (se anima). */
    private float hover;
    private long lastFrame, sweepStart = -1L;

    public TFMenuButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
        this(x, y, width, height, message, onPress, primary ? Kind.PRIMARY : Kind.SECONDARY, null, null);
    }

    public TFMenuButton(int x, int y, int width, int height, Component message, OnPress onPress, Kind kind,
                        TFMenuIcons.Icon icon, String plate) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.kind = kind;
        this.icon = icon;
        this.plate = plate;
    }

    /** Texto con la fuente de TF. */
    public static MutableComponent label(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT));
    }

    // --------------------------------------------------------------------------------------------- menú principal

    /**
     * Rectángulos (x, y, ancho, alto) del cuerpo de las 8 placas del menú principal, como en la lámina, y al final
     * {arriba de la composición}. La lámina se escala para que quepa debajo del emblema (que tiene al menos LOGO_MIN).
     * Null si no hay textura.
     */
    public static int[][] mainLayout(int width, int height) {
        float[] comp = TFMenuPlates.composition();
        if (comp == null) return null;
        float compH = comp[1] - comp[0];
        float maxWiden = 0f, halfW = 0f;
        for (int i = 0; i < MAIN.length; i++) {
            TFMenuPlates.Plate p = TFMenuPlates.get(MAIN[i]);
            maxWiden = Math.max(maxWiden, WIDEN[i]);
            halfW = Math.max(halfW, Math.max(comp[2] - p.layout[0], p.layout[2] - comp[2]) * WIDEN[i]);
        }
        float k = Math.min(K_MAX, (height - BOTTOM_MARGIN - LOGO_MIN - 6) / compH);
        k = Math.min(k, (width / 2f - 10f) / halfW);
        float free = height - BOTTOM_MARGIN - compH * k;
        float top = free - Math.max(0f, (free - 186f) / 2f); // lo que sobre, repartido entre emblema y botones
        int[][] out = new int[MAIN.length + 1][];
        float cx = width / 2f;
        for (int i = 0; i < MAIN.length; i++) {
            TFMenuPlates.Plate p = TFMenuPlates.get(MAIN[i]);
            float x0 = cx + (p.layout[0] - comp[2]) * k * WIDEN[i];
            float x1 = cx + (p.layout[2] - comp[2]) * k * WIDEN[i];
            float y0 = top + (p.layout[1] - comp[0]) * k;
            float y1 = top + (p.layout[3] - comp[0]) * k;
            out[i] = new int[] {Math.round(x0), Math.round(y0), Math.round(x1) - Math.round(x0), Math.round(y1) - Math.round(y0)};
        }
        out[MAIN.length] = new int[] {Math.round(top)};
        mainTextSize = textSizeFor(out);
        return out;
    }

    /**
     * Un solo tamaño de letra para todo el menú: el mayor con el que cada texto (y su icono) cabe en su placa y las
     * mayúsculas no pasan de CAP_FILL del hueco interior de la placa más baja.
     */
    private static float textSizeFor(int[][] rects) {
        Minecraft mc = Minecraft.getInstance();
        int s = Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
        String[] labels = {TFConfig.serverName(), "Mundo local", "Mods", "Opciones", "Salir", "WhatsApp", "Web", "Discord"};
        float size = Float.MAX_VALUE;
        for (int i = 0; i < MAIN.length; i++) {
            TFMenuPlates.Plate p = TFMenuPlates.get(MAIN[i]);
            int[] r = rects[i];
            TFMenuPlates.Placed at = new TFMenuPlates.Placed(p, r[0], r[1], r[2], r[3], s);
            float room = at.x(p.text[2]) - at.x(p.text[0]);
            float need = mc.font.width(label(labels[i])) + (i >= 5 ? TFMenuIcons.SIZE + GAP : 0);
            size = Math.min(size, room / need);
            size = Math.min(size, CAP_FILL * INTERIOR * r[3] / CAP_HEIGHT);
        }
        return Math.max(1f, size);
    }

    /** Botón principal: conexión directa a Tierras Fantásticas. */
    public static TFMenuButton server(int[] r) {
        return new TFMenuButton(r[0], r[1], r[2], r[3], label(TFConfig.serverName()),
                button -> TFServer.join(Minecraft.getInstance().screen), Kind.PRIMARY, null, TF);
    }

    /** Botón de la web del servidor. */
    public static TFMenuButton web(int[] r) {
        return new TFMenuButton(r[0], r[1], r[2], r[3], label("Web"), button -> TFDiscord.openUrl(TFConfig.webUrl()),
                Kind.SECONDARY, TFMenuIcons.Icon.WEB, PURPLE);
    }

    /** Botón del Discord del servidor (invitación del servidor, no de una persona). */
    public static TFMenuButton discord(int[] r) {
        return new TFMenuButton(r[0], r[1], r[2], r[3], label("Discord"), button -> TFDiscord.open(), Kind.DISCORD,
                TFMenuIcons.Icon.DISCORD, BLUE);
    }

    /** Botón del grupo de WhatsApp del servidor (la web redirige /whatsapp al enlace del grupo). */
    public static TFMenuButton whatsapp(int[] r) {
        return new TFMenuButton(r[0], r[1], r[2], r[3], label("WhatsApp"),
                button -> TFDiscord.openUrl(TFConfig.webUrl().replaceAll("/+$", "") + "/whatsapp"), Kind.WHATSAPP,
                TFMenuIcons.Icon.WHATSAPP, GREEN);
    }

    /** Copia un botón del menú normal con una placa de TF y el mismo efecto al pulsarlo. */
    public static TFMenuButton wrapping(Button original, int[] r, String text, Kind kind, String plate) {
        TFMenuButton button = new TFMenuButton(r[0], r[1], r[2], r[3], label(text), pressed -> original.onPress(), kind, null, plate);
        button.active = original.active;
        button.setTooltip(original.getTooltip());
        return button;
    }

    // --------------------------------------------------------------------------------------------- dibujo

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int s = Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
        long now = Util.getMillis();
        boolean hot = this.active && this.isHoveredOrFocused();
        float dt = lastFrame == 0L ? 1000f : Math.min(250f, now - lastFrame);
        lastFrame = now;
        if (hot && hover == 0f) sweepStart = now; // la línea de luz cruza al entrar el ratón
        float step = dt / HOVER_MS;
        hover = hot ? Math.min(1f, hover + step) : Math.max(0f, hover - step);
        boolean pressed = this.active && this.isHovered() && mc.mouseHandler.isLeftPressed();
        float e = this.active ? hover * hover * (3f - 2f * hover) : 0f;
        float alpha = this.alpha;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        TFMenuPlates.Plate p = plate == null ? null : TFMenuPlates.get(plate);
        if (p == null) {
            TFButtonTheme.drawPlate(g, getX(), getY(), getWidth(), getHeight(), e, this.active, alpha);
            float cy = getY() + getHeight() / 2f;
            paintContent(g, mc.font, s, getX() + getWidth() * 0.14f, getX() + getWidth() * 0.86f, cy, 1f, e, alpha);
            return;
        }

        // Sube un poco con el ratón y se hunde al pulsar (desde el centro del cuerpo)
        float cx = getX() + getWidth() / 2f, cy = getY() + getHeight() / 2f;
        float grow = pressed ? 0.975f : 1f + 0.02f * e;
        float lift = pressed ? 0.5f : -0.8f * e;
        g.pose().pushPose();
        g.pose().translate(cx, cy + lift, 0f);
        g.pose().scale(grow, grow, 1f);
        g.pose().translate(-cx, -cy, 0f);

        TFMenuPlates.Placed at = new TFMenuPlates.Placed(p, getX(), getY(), getWidth(), getHeight(), s);
        float shade = !this.active ? 0.45f : pressed ? 0.8f : 1f;
        g.setColor(shade, shade, shade, alpha);
        TFMenuPlates.draw(g, at);
        if (e > 0.01f && !pressed) {
            // Iluminada: la misma placa sumada encima (el oro y las gemas brillan; lo oscuro casi no cambia)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            g.setColor(1f, 1f, 1f, 0.24f * e * alpha);
            TFMenuPlates.draw(g, at);
            RenderSystem.defaultBlendFunc();
        }
        g.setColor(1f, 1f, 1f, 1f);
        if (this.active) paintLights(g, at, now, e, alpha);

        // Línea de luz: al entrar el ratón, y en Tierras Fantásticas también cada pocos segundos
        float tx0 = at.x(p.text[0]), tx1 = at.x(p.text[2]);
        int ix0 = Math.round(at.x(p.body[0] + p.bodyH() * 0.18f)), ix1 = Math.round(at.x(p.body[2] - p.bodyH() * 0.18f));
        int iy0 = Math.round(at.y(p.body[1] + p.bodyH() * 0.16f)), iy1 = Math.round(at.y(p.body[3] - p.bodyH() * 0.16f));
        if (sweepStart >= 0L && now - sweepStart < SWEEP_MS) {
            TFMenuPaint.sweep(g, ix0, iy0, ix1 - ix0, iy1 - iy0, (now - sweepStart) / SWEEP_MS, 0.55f * alpha);
        } else if (kind == Kind.PRIMARY && this.active) {
            long c = now % TF_SHINE_EVERY;
            if (c < SWEEP_MS * 1.4f) TFMenuPaint.sweep(g, ix0, iy0, ix1 - ix0, iy1 - iy0, c / (SWEEP_MS * 1.4f), 0.32f * alpha);
        }

        float textY = at.y((p.body[1] + p.body[3]) / 2f);
        paintContent(g, mc.font, s, tx0, tx1, textY, mainTextSize, e, alpha);
        g.pose().popPose();
    }

    /** Faroles que titilan y gemas que echan chispas (y brillan con el ratón encima). */
    private void paintLights(GuiGraphics g, TFMenuPlates.Placed at, long now, float e, float alpha) {
        TFMenuPlates.Plate p = at.plate;
        if ((p.lights.length == 0 && p.sparks.length == 0) || !TFTextures.MENU_GLOW.ready()) return;
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        float t = now / 1000f;
        for (int i = 0; i < p.lights.length; i++) {
            float[] l = p.lights[i];
            float flicker = 0.5f + 0.18f * Mth.sin(t * 7.3f + i * 2.1f) + 0.1f * Mth.sin(t * 13.7f + i * 4.3f)
                    + 0.06f * Mth.sin(t * 23.1f + i);
            float r = l[2] * at.k * (3.0f + 0.25f * flicker);
            g.setColor(1f, 0.6f, 0.22f, Mth.clamp(flicker * (0.55f + 0.25f * e), 0f, 1f) * alpha);
            glow(g, TFTextures.MENU_GLOW, at.x(l[0]), at.y(l[1]), r, 0f);
        }
        long seed = plate.hashCode() & 0xFFFFL;
        for (int i = 0; i < p.sparks.length; i++) {
            float[] gem = p.sparks[i];
            float gx = at.x(gem[0]), gy = at.y(gem[1]);
            if (e > 0.01f) {
                g.setColor(0.35f, 0.75f, 1f, 0.5f * e * alpha);
                glow(g, TFTextures.MENU_GLOW, gx, gy, gem[2] * at.k * 2.2f, 0f);
            }
            long c = (now + seed * 37L + i * (SPARK_CYCLE / Math.max(1, p.sparks.length))) % SPARK_CYCLE;
            if (c < SPARK_MS && TFTextures.SPARKLE.ready()) {
                float k = c / (float) SPARK_MS;
                float strength = Mth.sin(k * Mth.PI);
                g.setColor(1f, 1f, 1f, strength * alpha);
                glow(g, TFTextures.SPARKLE, gx - gem[2] * at.k * 0.2f, gy - gem[2] * at.k * 0.25f,
                        gem[2] * at.k * (0.7f + 0.5f * strength), k * 60f);
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }

    /** Una imagen cuadrada centrada en (x, y) con radio r y girada `deg` grados. */
    private static void glow(GuiGraphics g, TFTextures tex, float x, float y, float r, float deg) {
        if (r <= 0.2f) return;
        int tw = tex.width(), th = tex.height();
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        if (deg != 0f) g.pose().mulPose(Axis.ZP.rotationDegrees(deg));
        g.pose().scale(2f * r / tw, 2f * r / th, 1f);
        g.pose().translate(-tw / 2f, -th / 2f, 0f);
        g.blit(tex.id(), 0, 0, 0f, 0f, tw, th, tw, th);
        g.pose().popPose();
    }

    /** Icono (WhatsApp, Web y Discord) y texto centrados entre x0 y x1, con contorno oscuro. */
    private void paintContent(GuiGraphics g, Font font, int s, float x0, float x1, float centerY, float size, float e, float alpha) {
        if (alpha * 255f < 4f) return; // casi transparente: Minecraft pintaría el texto opaco
        int ink = !this.active ? 0x9A9AA6 : TFMenuPaint.lerp(0xFF000000 | INK, 0xFF000000 | INK_HOT, e) & 0xFFFFFF;

        // Letra a la escala de la interfaz (n píxeles reales por píxel de glifo; como mucho un 8 % más que `size`): nítida
        int n = Mth.clamp(Math.round(s * size), 1, FONT_AT_SCALE.length);
        if (n > 1 && n / (float) s > size * 1.08f) n--;
        float scale = n / (float) s;
        MutableComponent text = Component.literal(getMessage().getString())
                .setStyle(Style.EMPTY.withFont(FONT_AT_SCALE[n - 1]));
        int textW = font.width(text);
        float maxW = x1 - x0;
        boolean withIcon = icon != null;
        float iconW = withIcon ? (TFMenuIcons.SIZE + GAP) * scale : 0f; // el icono crece con la letra
        float group = textW * scale + iconW;
        if (group > maxW && group > 0f) {
            scale *= Math.max(0.6f, (maxW - iconW) / (textW * scale));
            group = textW * scale + iconW;
        }
        float gx = snap((x0 + x1) / 2f - group / 2f, s);
        float o = Math.max(1, Math.round(s * 0.4f)) / (float) s; // grosor del contorno

        if (withIcon) {
            int sz = TFMenuIcons.SIZE * n;
            int ix = Math.round(gx * s);
            int iy = Math.round(centerY * s - sz / 2f);
            g.pose().pushPose();
            g.pose().scale(1f / s, 1f / s, 1f);
            int ro = Math.round(o * s);
            int dark = TFMenuPaint.argb(0.85f * alpha, OUTLINE);
            TFMenuIcons.draw(g, icon, ix - ro, iy, n, dark);
            TFMenuIcons.draw(g, icon, ix + ro, iy, n, dark);
            TFMenuIcons.draw(g, icon, ix, iy - ro, n, dark);
            TFMenuIcons.draw(g, icon, ix, iy + ro + Math.max(1, s / 2), n, dark);
            TFMenuIcons.draw(g, icon, ix, iy, n, TFMenuPaint.argb(alpha, ink));
            g.pose().popPose();
            gx += iconW;
        }

        FormattedCharSequence seq = text.getVisualOrderText();
        float ty = snap(centerY - TEXT_UP * scale, s);
        int dark = TFMenuPaint.argb(0.85f * alpha, OUTLINE);
        drawText(g, font, seq, gx, ty + 1f, scale, TFMenuPaint.argb(0.5f * alpha, OUTLINE)); // sombra
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) drawText(g, font, seq, gx + dx * o, ty + dy * o, scale, dark);
            }
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
}
