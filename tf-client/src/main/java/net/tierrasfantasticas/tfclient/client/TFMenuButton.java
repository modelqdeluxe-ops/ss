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
 * Animación suave, sin rayas: con el ratón encima la placa sube un poco y se ilumina con un fundido; al pulsar se
 * hunde y se oscurece. Las gemas echan una chispa de vez en cuando (y brillan más con el ratón), los faroles titilan,
 * y las letras de oro de Tierras Fantásticas «respiran» despacio con una luz cálida detrás. Letra Titan One (Cinzel
 * fuera del menú) gruesa, con contorno del color de su placa oscurecido (sin sombra) y degradado suave (oro en
 * Tierras Fantásticas), del mismo tamaño en todos los botones (el mayor que cabe en todos; nítido); iconos de verdad
 * (logos de WhatsApp y Discord, globo en Web) en menu_icons.png. Todas las filas miden lo mismo (una columna).
 */
public class TFMenuButton extends Button {
    public static final ResourceLocation FONT = new ResourceLocation(TFClient.MOD_ID, "cinzel");
    /**
     * Cinzel con los glifos rasterizados a 1..12 píxeles reales por píxel de interfaz (font/cinzel_N.json; 4 = FONT):
     * con la letra a n/escala se dibuja 1:1 en pantalla, nítida.
     */
    private static final ResourceLocation[] FONT_AT_SCALE = new ResourceLocation[12];
    /**
     * La letra de los botones del menú principal: Titan One (OFL, font/OFL-TitanOne.txt), gruesa y redonda, de botón
     * de juego; también a 1..12 píxeles reales por píxel de interfaz (font/titanone_N.json).
     */
    private static final ResourceLocation[] MENU_FONT_AT_SCALE = new ResourceLocation[12];
    static {
        for (int i = 0; i < FONT_AT_SCALE.length; i++) {
            FONT_AT_SCALE[i] = i == 3 ? FONT : new ResourceLocation(TFClient.MOD_ID, "cinzel_" + (i + 1));
            MENU_FONT_AT_SCALE[i] = new ResourceLocation(TFClient.MOD_ID, "titanone_" + (i + 1));
        }
    }

    public enum Kind { PRIMARY, SECONDARY, DANGER, WHATSAPP, DISCORD }

    /** Las placas del menú principal en el orden de la composición. */
    public static final String TF = "tf", BLUE_1 = "blue1", BLUE_2 = "blue2", GRAY = "gray", RED = "red", GREEN = "green",
            PURPLE = "purple", BLUE = "blueb";
    private static final String[] MAIN = {TF, BLUE_1, BLUE_2, GRAY, RED, GREEN, PURPLE, BLUE};
    /** Filas de la composición (índices de MAIN) y grupos de botones del mismo ancho (simetría en espejo). */
    private static final int[][] ROWS = {{0}, {1}, {2}, {3, 4}, {5, 6, 7}};
    private static final int[][] GROUPS = {{0}, {1, 2}, {3, 4}, {5, 7}, {6}};
    /** Separación entre filas en la lámina: lo justo para que las gemas de una no toquen la siguiente. */
    private static final float[] GAPS = {96f, 96f, 72f, 80f};
    /** Aire a cada lado del texto (en altos de la placa), entre botones de una fila, y el ancho mínimo de los azules. */
    private static final float PAD = 0.4f, BLUE_MIN = 0.62f;
    private static final int GROUP_GAP = 14;
    /** Escala máxima de la lámina (interfaz por píxel): Mundo local de unos 21 de alto. */
    private static final float K_MAX = 0.16f;
    private static final float LOGO_FRAC = 0.44f;
    private static final int BOTTOM_MARGIN = 6;
    /** Alto de las mayúsculas de Titan One a escala 1 (interfaz) y cuánto del hueco interior ocupan como mucho. */
    private static final float CAP_HEIGHT = 6.2f, CAP_FILL = 0.5f, TF_CAP_FILL = 0.52f, INTERIOR = 0.7f;
    /**
     * Tamaño de letra común a los botones del menú principal (el mayor que cabe en todos) y el de Tierras Fantásticas,
     * más grande (su placa es más alta). Ver mainLayout.
     */
    private static float mainTextSize = 1f, tfTextSize = 1f;
    /** Lo que se corre el dibujo de las placas para quedar en el centro exacto de la pantalla (0 o ±0,5). */
    private static float mainShiftX;

    /** Separación entre icono y texto, en píxeles de interfaz. */
    private static final int GAP = 3;
    /** El texto se dibuja en el centro - TEXT_UP (centrado a la vista: Cinzel en las demás pantallas, New Rocker en el menú). */
    private static final float TEXT_UP = 3.5f, MENU_TEXT_UP = 4f;
    private static final float HOVER_MS = 160f;
    private static final long SPARK_CYCLE = 5200L, SPARK_MS = 900L;
    /** Mismo color en todos los botones; con el ratón, un poco más claro (la placa es la que se ilumina). */
    private static final int INK = 0xE7E1D5, INK_HOT = 0xFFFBF2, OUTLINE = 0x140A04;
    /** Oro de las letras de Tierras Fantásticas, de arriba abajo, y alto de las mayúsculas en la celda (escala 1). */
    private static final int[] GOLD = {0xF9EBC4, 0xF4DA96, 0xE8C06A, 0xD8A955, 0xCB9C4E};
    private static final float GOLD_TOP = 0f, GOLD_BOTTOM = 6.7f;
    /** El degradado de las demás letras del menú, de arriba abajo. */
    private static final int[] WHITE = {0xE6E0D3, 0xD9D2C3, 0xBDB5A6};
    /** Contorno de las letras blancas: el color de su placa, oscurecido (no negro: menos contraste con el fondo). */
    private static final float OUTLINE_SHADE = 0.55f, GOLD_OUTLINE_SHADE = 0.72f;
    /** Las letras de oro «respiran»: se aclaran y oscurecen muy despacio (un ciclo cada GOLD_BREATH_MS). */
    private static final float GOLD_BREATH_MS = 3800f;

    private final Kind kind;
    private final TFMenuIcons.Icon icon;
    /** Placa del menú principal (null: placa de piedra). */
    private final String plate;
    /** 0..1: cuánto está encendido el estado de ratón encima (se anima). */
    private float hover;
    private long lastFrame;

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
     * Rectángulos (x, y, ancho, alto) del cuerpo de las 8 placas del menú principal y al final {arriba de la
     * composición}. En alto, la lámina con las filas separadas lo justo para que las gemas de una no toquen la siguiente
     * (GAPS), escalada para caber debajo del emblema (que se queda al menos LOGO_FRAC del alto). En ancho, cada botón a
     * la medida de su texto (con aire a los lados, PAD) y los de un mismo grupo iguales: simetría en espejo (Opciones y
     * Salir iguales; WhatsApp y Discord iguales, Web en medio). Cada fila, centrada. Null si no hay textura.
     */
    public static int[][] mainLayout(int width, int height) {
        float[] comp = TFMenuPlates.composition();
        if (comp == null) return null;
        Minecraft mc = Minecraft.getInstance();
        int s = Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
        TFMenuPlates.Plate[] p = new TFMenuPlates.Plate[MAIN.length];
        for (int i = 0; i < MAIN.length; i++) p[i] = TFMenuPlates.get(MAIN[i]);
        // alto: filas una debajo de otra, con su separación
        float[] rowY = new float[MAIN.length];
        float ny = p[0].layout[1], lastBottom = 0f;
        for (int r = 0; r < ROWS.length; r++) {
            float rt = Float.MAX_VALUE, rb = -Float.MAX_VALUE;
            for (int i : ROWS[r]) {
                rt = Math.min(rt, p[i].layout[1]);
                rb = Math.max(rb, p[i].layout[3]);
            }
            for (int i : ROWS[r]) rowY[i] = ny + p[i].layout[1] - rt;
            lastBottom = rb;
            ny += rb - rt + (r < GAPS.length ? GAPS[r] : 0f);
        }
        float compH = ny + (comp[1] - lastBottom) - comp[0];
        float k = Math.min(K_MAX, (height - BOTTOM_MARGIN - Math.round(height * LOGO_FRAC) - 6) / compH);
        float free = height - BOTTOM_MARGIN - compH * k;
        float top = free - Math.max(0f, (free - 186f) / 2f); // lo que sobre, repartido entre emblema y botones
        int[] ys = new int[MAIN.length], hs = new int[MAIN.length];
        for (int i = 0; i < MAIN.length; i++) {
            ys[i] = Math.round(top + (rowY[i] - comp[0]) * k);
            hs[i] = Math.round(top + (rowY[i] + p[i].layout[3] - p[i].layout[1] - comp[0]) * k) - ys[i];
        }
        // letra: la de los botones, la mayor que cabe a lo alto en todos; Tierras Fantásticas, más grande
        float size = Float.MAX_VALUE;
        for (int i = 1; i < MAIN.length; i++) size = Math.min(size, CAP_FILL * INTERIOR * hs[i] / CAP_HEIGHT);
        size = Math.max(1f, size);
        float tf = Math.max(size, TF_CAP_FILL * INTERIOR * hs[0] / CAP_HEIGHT);
        String[] labels = {TFConfig.serverName(), "Mundo local", "Mods", "Opciones", "Salir", "WhatsApp", "Web", "Discord"};
        float[] widths = new float[MAIN.length];
        for (int attempt = 0; attempt < 4; attempt++) { // si una fila no cabe a lo ancho, letra un poco más pequeña
            float eff = crisp(size, s), efft = crisp(tf, s);
            float[] need = new float[MAIN.length];
            for (int i = 0; i < MAIN.length; i++) {
                float sc = i == 0 ? efft : eff;
                float side = (p[i].text[0] - p[i].body[0]) * k;
                float fills = 0f;
                for (float[] f : p[i].fills) fills += f[1] - f[0];
                float minW = (p[i].bodyW() - fills * 0.75f) * k; // las zonas lisas encogen como mucho a la cuarta parte
                float textW = mc.font.width(Component.literal(labels[i]).setStyle(Style.EMPTY.withFont(MENU_FONT_AT_SCALE[3])));
                need[i] = Math.max(minW, (textW + (i >= 5 ? TFMenuIcons.SIZE + GAP : 0)) * sc + 2 * (side + PAD * hs[i]));
            }
            for (int[] group : GROUPS) {
                float w = 0f;
                for (int i : group) w = Math.max(w, need[i]);
                for (int i : group) widths[i] = w;
            }
            widths[1] = widths[2] = Math.max(widths[1], BLUE_MIN * widths[0]);
            float widest = 0f;
            for (int[] row : ROWS) {
                float w = GROUP_GAP * (row.length - 1);
                for (int i : row) w += widths[i];
                widest = Math.max(widest, w);
            }
            mainTextSize = eff;
            tfTextSize = efft;
            if (widest <= width - 16 || size <= 0.6f) break;
            size *= 0.9f;
            tf *= 0.9f;
        }
        // anchos pares (y separación par): todas las filas tienen el mismo centro exacto, las gemas en un mismo eje
        // el centro de verdad de la ventana (el ancho de la interfaz se redondea hacia arriba: width / 2 puede caer medio
        // píxel de interfaz al lado); las placas se dibujan corridas lo que falte (mainShiftX), justas en píxeles reales
        float cx = mc.getWindow().getWidth() / (2f * s);
        int[][] out = new int[MAIN.length + 1][];
        for (int[] row : ROWS) {
            int total = GROUP_GAP * (row.length - 1);
            for (int i : row) total += even(widths[i]);
            int x = Math.round(cx - total / 2f);
            mainShiftX = cx - (x + total / 2f);
            for (int i : row) {
                out[i] = new int[] {x, ys[i], even(widths[i]), hs[i]};
                x += even(widths[i]) + GROUP_GAP;
            }
        }
        out[MAIN.length] = new int[] {Math.round(top)};
        return out;
    }

    private static int even(float w) {
        return 2 * Math.round(w / 2f);
    }

    /** El tamaño de letra nítido que usa paintContent: n/escala con n entero, como mucho un 10 % más que size. */
    private static float crisp(float size, int s) {
        int n = Mth.clamp(Math.round(s * size), 1, FONT_AT_SCALE.length);
        if (n > 1 && n / (float) s > size * 1.1f) n--;
        return n / (float) s;
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
        // el foco solo cuenta con el teclado: tras un clic el botón se queda con el foco y no debe seguir encendido
        boolean hot = this.active && (this.isHovered() || this.isFocused() && mc.getLastInputType().isKeyboard());
        float dt = lastFrame == 0L ? 1000f : Math.min(250f, now - lastFrame);
        lastFrame = now;
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
        float grow = pressed ? 0.98f : 1f + 0.015f * e;
        float lift = pressed ? 0.5f : -0.6f * e;
        g.pose().pushPose();
        g.pose().translate(cx + mainShiftX, cy + lift, 0f);
        g.pose().scale(grow, grow, 1f);
        g.pose().translate(-cx, -cy, 0f);

        TFMenuPlates.Placed at = new TFMenuPlates.Placed(p, getX(), getY(), getWidth(), getHeight(), s);
        float shade = !this.active ? 0.45f : pressed ? 0.8f : 1f;
        g.setColor(shade, shade, shade, alpha);
        TFMenuPlates.draw(g, at);
        if (e > 0.01f && !pressed) {
            // Iluminada: la misma placa sumada encima (el oro y las gemas brillan; lo oscuro casi no cambia)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            g.setColor(1f, 1f, 1f, 0.16f * e * alpha);
            TFMenuPlates.draw(g, at);
            RenderSystem.defaultBlendFunc();
        }
        g.setColor(1f, 1f, 1f, 1f);
        if (this.active) paintLights(g, at, now, e, alpha);

        float tx0 = at.x(p.text[0]), tx1 = at.x(p.text[2]);

        float textY = at.y((p.body[1] + p.body[3]) / 2f);
        paintContent(g, mc.font, s, tx0, tx1, textY, kind == Kind.PRIMARY ? tfTextSize : mainTextSize, e, alpha);
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

        // Letra a la escala de la interfaz (n píxeles reales por píxel de glifo; como mucho un 10 % más que `size`): nítida
        ResourceLocation[] fonts = plate != null ? MENU_FONT_AT_SCALE : FONT_AT_SCALE;
        int n = Mth.clamp(Math.round(s * size), 1, fonts.length);
        if (n > 1 && n / (float) s > size * 1.1f) n--;
        float scale = n / (float) s;
        MutableComponent text = Component.literal(getMessage().getString())
                .setStyle(Style.EMPTY.withFont(fonts[n - 1]));
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
        FormattedCharSequence seq = text.getVisualOrderText();
        if (plate == null) {
            // fuera del menú principal: contorno fino y un color
            int ro = Math.max(1, Math.round(s * 0.5f));
            float o = ro / (float) s;
            float ty = snap(centerY - TEXT_UP * scale, s);
            drawText(g, font, seq, gx, ty + o + Math.max(1, s / 2) / (float) s, scale, TFMenuPaint.argb(0.6f * alpha, OUTLINE));
            int dark = TFMenuPaint.argb(0.9f * alpha, OUTLINE);
            for (int dx = -ro; dx <= ro; dx++) {
                for (int dy = -ro; dy <= ro; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == ro) drawText(g, font, seq, gx + dx / (float) s, ty + dy / (float) s, scale, dark);
                }
            }
            drawText(g, font, seq, gx, ty, scale, TFMenuPaint.argb(alpha, ink));
            return;
        }

        // Menú principal: letra gruesa con contorno fino (5 % de la letra) del color de su placa oscurecido, sin sombra
        // (la sombra hacia abajo se veía como una raya oscura delante del botón) y degradado suave
        boolean goldText = kind == Kind.PRIMARY && this.active;
        int ro = Math.max(1, Math.round(10f * scale * s * 0.05f));
        int[][] ring = ring(ro);
        TFMenuPlates.Plate pl = TFMenuPlates.get(plate);
        int edge = shade(pl == null ? 0x806040 : pl.tint, goldText ? GOLD_OUTLINE_SHADE : OUTLINE_SHADE);
        if (withIcon) {
            float sz = TFMenuIcons.SIZE * scale;
            float iy = snap(centerY - sz / 2f, s);
            for (int[] d : ring) icon(g, gx + d[0] / (float) s, iy + d[1] / (float) s, sz, edge, alpha);
            icon(g, gx, iy, sz, ink, alpha);
            gx += iconW;
        }
        float ty = snap(centerY - MENU_TEXT_UP * scale, s);
        if (goldText) goldGlow(g, gx, centerY, textW * scale, scale, e, alpha);
        for (int[] d : ring) drawText(g, font, seq, gx + d[0] / (float) s, ty + d[1] / (float) s, scale, TFMenuPaint.argb(alpha, edge));
        if (this.active) gradientLetters(g, font, seq, gx, ty, scale, textW, goldText, e, alpha);
        else drawText(g, font, seq, gx, ty, scale, TFMenuPaint.argb(alpha, ink));
    }

    /** Un color RGB oscurecido (k de 0 a 1). */
    private static int shade(int rgb, float k) {
        return (Math.round(((rgb >> 16) & 255) * k) << 16) | (Math.round(((rgb >> 8) & 255) * k) << 8) | Math.round((rgb & 255) * k);
    }

    /** Desplazamientos (en píxeles reales) del contorno: 16 direcciones a ro y 8 a la mitad, sin repetir. */
    private static int[][] ring(int ro) {
        int[][] cached = RINGS.get(ro);
        if (cached != null) return cached;
        java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>();
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int n = pass == 0 ? 16 : 8;
            float r = pass == 0 ? ro : ro / 2f;
            if (pass == 1 && ro < 2) break;
            for (int i = 0; i < n; i++) {
                double a = i * 2 * Math.PI / n;
                int dx = (int) Math.round(Math.cos(a) * r), dy = (int) Math.round(Math.sin(a) * r);
                if ((dx != 0 || dy != 0) && seen.add(((long) dx << 32) ^ (dy & 0xFFFFFFFFL))) out.add(new int[] {dx, dy});
            }
        }
        cached = out.toArray(new int[0][]);
        RINGS.put(ro, cached);
        return cached;
    }

    private static final java.util.Map<Integer, int[][]> RINGS = new java.util.HashMap<>();

    /** El icono del botón (menu_icons.png: WhatsApp, Discord y el globo de la web, 256 cada uno) de sz de lado. */
    private void icon(GuiGraphics g, float x, float y, float sz, int rgb, float alpha) {
        if (!TFTextures.MENU_ICONS.ready()) return;
        int i = icon == TFMenuIcons.Icon.WHATSAPP ? 0 : icon == TFMenuIcons.Icon.DISCORD ? 1 : 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(sz / ICON_CELL, sz / ICON_CELL, 1f);
        g.blit(TFTextures.MENU_ICONS.id(), 0, 0, ICON_PAD + i * (ICON_CELL + ICON_PAD), ICON_PAD, ICON_CELL, ICON_CELL,
                TFTextures.MENU_ICONS.width(), TFTextures.MENU_ICONS.height());
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static final int ICON_CELL = 256, ICON_PAD = 16;

    /** Luz cálida que late detrás de las letras de Tierras Fantásticas (más fuerte con el ratón encima). */
    private static void goldGlow(GuiGraphics g, float gx, float cy, float w, float scale, float e, float alpha) {
        if (!TFTextures.MENU_GLOW.ready()) return;
        float pulse = 0.5f + 0.5f * Mth.sin(Util.getMillis() / 1000f * 2.2f);
        int tw = TFTextures.MENU_GLOW.width(), th = TFTextures.MENU_GLOW.height();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        g.setColor(1f, 0.72f, 0.25f, (0.22f + 0.1f * pulse + 0.18f * e) * alpha);
        g.pose().pushPose();
        g.pose().translate(gx + w / 2f, cy, 0f);
        g.pose().scale((w + 24f) / tw, 13f * scale / th, 1f);
        g.pose().translate(-tw / 2f, -th / 2f, 0f);
        g.blit(TFTextures.MENU_GLOW.id(), 0, 0, 0f, 0f, tw, th, tw, th);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * Letras en degradado (franjas de 2 píxeles reales con tijera): de oro pálido a ámbar en Tierras Fantásticas, con un
     * brillo que las cruza cada pocos segundos (y al pasar el ratón); de blanco a gris azulado en las demás.
     */
    private void gradientLetters(GuiGraphics g, Font font, FormattedCharSequence seq, float gx, float ty, float scale, int textW,
                                 boolean goldText, float e, float alpha) {
        com.mojang.blaze3d.platform.Window win = Minecraft.getInstance().getWindow();
        double gs = win.getGuiScale();
        org.joml.Matrix4f m = g.pose().last().pose();
        org.joml.Vector3f a = m.transformPosition(gx, ty + GOLD_TOP * scale, 0f, new org.joml.Vector3f());
        org.joml.Vector3f b = m.transformPosition(gx + textW * scale, ty + GOLD_BOTTOM * scale, 0f, new org.joml.Vector3f());
        int x0 = (int) Math.floor(a.x * gs) - 4, x1 = (int) Math.ceil(b.x * gs) + 4;
        int y0 = (int) Math.floor(a.y * gs), y1 = (int) Math.ceil(b.y * gs);
        int fbH = win.getHeight();
        // Fuera de la franja (por si algún trazo se sale): el color del medio
        drawText(g, font, seq, gx, ty, scale, TFMenuPaint.argb(alpha, goldText ? GOLD[2] : WHITE[1]));
        g.flush();
        int bands = Math.max(2, (y1 - y0) / 2);
        // el oro respira despacio (se aclara un poco y vuelve), sin rayas ni destellos
        float breath = goldText ? 0.12f * (0.5f + 0.5f * Mth.sin(Util.getMillis() / GOLD_BREATH_MS * Mth.TWO_PI)) : 0f;
        for (int i = 0; i < bands; i++) {
            int by0 = y0 + (y1 - y0) * i / bands, by1 = y0 + (y1 - y0) * (i + 1) / bands;
            if (by1 <= by0) continue;
            RenderSystem.enableScissor(x0, fbH - by1, x1 - x0, by1 - by0);
            float t = (i + 0.5f) / bands;
            int c = goldText ? gold(t, Math.min(1f, e + breath)) : TFMenuPaint.lerp(0xFF000000 | ramp(WHITE, t), 0xFFF6F1E6, 0.45f * e) & 0xFFFFFF;
            drawText(g, font, seq, gx, ty, scale, TFMenuPaint.argb(alpha, c));
            g.flush();
        }
        RenderSystem.disableScissor();
    }

    /** Oro de arriba (t = 0) a abajo (t = 1); con el ratón, más claro. */
    private static int gold(float t, float e) {
        return TFMenuPaint.lerp(0xFF000000 | ramp(GOLD, t), 0xFFFBEFCF, 0.35f * e) & 0xFFFFFF;
    }

    /** Color de una escala de colores (de arriba abajo) en t de 0 a 1. */
    private static int ramp(int[] cols, float t) {
        float p = Mth.clamp(t, 0f, 1f) * (cols.length - 1);
        int i = Math.min(cols.length - 2, (int) p);
        return TFMenuPaint.lerp(0xFF000000 | cols[i], 0xFF000000 | cols[i + 1], p - i) & 0xFFFFFF;
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
