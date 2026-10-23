/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
 * Botón de los menús de TF (1.3.120, rehecho: el dueño, «rework completo de los botones del menú principal; no me
 * gustan las texturas actuales; como los de los diálogos de los NPC, pero mejor diseñados y con mejor efecto»): la
 * pastilla de cristal oscuro con hilo de oro de las respuestas de los caballeros ({@link TFPill}), sin texturas.
 *
 * <p>Menú principal: una columna limpia bajo el emblema, Tierras Fantásticas (el principal: más alto, doble hilo, letras
 * de oro que respiran, hilos de oro a los lados y un destello cada pocos segundos); Mundo local; Mods; Opciones | Salir;
 * WhatsApp | Web | Discord (con sus iconos). Con el ratón encima la pastilla sube un píxel, se enciende con un fundido,
 * echa un halo de su color y le crecen los hilos de oro desde el texto; al pulsar se hunde. Letra Titan One gruesa con
 * contorno oscuro, nítida (a la escala de la interfaz), centrada a la vista. En las demás pantallas (sin «plate»), la
 * misma pastilla con letra Cinzel.
 *
 * <p>1.3.121 (el dueño: «los botones están demasiado grandes, ajústalos al tamaño de la palabra; los de las redes no
 * están con su color, el de salir tampoco; quita la animación de gema reluciente porque laguea; al pasar el mouse
 * también laguea; la letra no está centrada; la animación de los palitos de los lados no la quites»): cada botón del
 * ancho de su palabra (con sitio para los hilos), filas más bajas, Salir rojo, WhatsApp verde, Web morado y Discord
 * azul siempre, sin destello, el texto 1,45 píxeles de letra más arriba (medido en la captura de la prueba de la
 * 1.3.120: las mayúsculas quedaban 3,5 píxeles por debajo del centro de la pastilla) y dibujado en un solo lote (antes
 * cada letra de cada copia del contorno se mandaba sola a la tarjeta: ~2000 veces por fotograma).
 *
 * <p>1.3.124 (el dueño: «los botones siguen algo grandes; el contorno de las letras está un poco pasado; la tipografía
 * no me termina de convencer con la forma de su botón»): filas y aire más pequeños, contorno de un píxel y letra Cinzel
 * (la de los demás menús y de los caballeros, que va con el hilo de oro) en lugar de Titan One, centrada con 3,9 (en
 * Opciones, la Cinzel con 4 queda a un píxel del centro de la pastilla).
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

    public enum Kind { PRIMARY, SECONDARY, DANGER, WHATSAPP, DISCORD, WEB }

    /** Los botones del menú principal (qué es cada uno: su color al pasar el ratón). */
    public static final String TF = "tf", BLUE_1 = "blue1", BLUE_2 = "blue2", GRAY = "gray", RED = "red", GREEN = "green",
            PURPLE = "purple", BLUE = "blueb";
    /**
     * La columna del menú principal: alto de cada fila a tamaño completo (Tierras Fantásticas; Mundo local; Mods;
     * Opciones | Salir; WhatsApp | Web | Discord), qué botones van en cada una y los huecos (en interfaz).
     */
    private static final int[] ROW_H = {21, 17, 17, 17, 15};
    private static final int[][] ROWS = {{0}, {1}, {2}, {3, 4}, {5, 6, 7}};
    private static final int GAP_Y = 7, GAP_X = 8, BOTTOM_MARGIN = 10;
    /** Alto de las mayúsculas de Titan One a escala 1 y qué parte del alto del botón ocupan (principal, demás, redes). */
    private static final float CAP_HEIGHT = 5.19f, CAP_TF = 0.44f, CAP = 0.42f, CAP_SOCIAL = 0.38f;
    /** Tamaños de letra del menú principal (nítidos: n/escala), calculados en mainLayout. */
    private static float mainTextSize = 1f, tfTextSize = 1f, socialTextSize = 1f;

    /** Separación entre icono y texto, en píxeles de interfaz. */
    private static final int GAP = 3;
    /**
     * El texto se dibuja en el centro - TEXT_UP (en píxeles de letra): así las mayúsculas quedan centradas a la vista
     * (Cinzel en las demás pantallas; Titan One en el menú, medido con tools/menu_sim.py: a ±1 píxel real).
     */
    private static final float TEXT_UP = 3.5f, MENU_TEXT_UP = 3.9f;
    /** Cuánto más juntas van las letras del menú (píxeles de glifo a escala 1 entre cada dos letras). */
    private static final float TRACKING = 0f;
    private static final float HOVER_MS = 160f;
    /** Contorno de las letras: violeta muy oscuro (oro oscuro en las de oro), sin sombra. */
    private static final int EDGE = 0x1A0E2E, GOLD_EDGE = 0x3A2306;
    /** Oro liso de las letras de Tierras Fantásticas y el de con el ratón encima (o al respirar). */
    private static final int GOLD = 0xF0CF7E, GOLD_HOT = 0xFBE7B4;
    /** Blanco crema de las demás letras; con el ratón encima, oro claro (como las respuestas de los caballeros). */
    private static final int WHITE = 0xEAE4D8, WHITE_HOT = 0xFFE9A8, DISABLED = 0x8A8494;
    /** Las letras de oro «respiran»: se aclaran y oscurecen muy despacio (un ciclo cada GOLD_BREATH_MS). */
    private static final float GOLD_BREATH_MS = 3800f;

    private final Kind kind;
    private final TFMenuIcons.Icon icon;
    /** Botón del menú principal (cuál: TF, BLUE_1...); null en las demás pantallas. */
    private final String plate;
    /** 0..1: cuánto está encendido el estado de ratón encima (se anima). */
    private float hover;
    private long lastFrame;
    /** (La prueba en el juego) El botón del menú principal con el ratón encima a la fuerza (TF, BLUE_1...). */
    public static volatile String testHot;

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
     * Rectángulos (x, y, ancho, alto) de los 8 botones del menú principal y al final {arriba de la columna}. Las filas
     * a su alto (más bajas si la ventana es baja: el emblema se queda al menos el 40 % del alto), una debajo de otra y
     * centradas; cada botón del ancho de su palabra (y su icono) más el aire de los lados, que deja sitio a los hilos de
     * oro (1.3.121). Lo que sobre de alto se reparte arriba y abajo (TitleScreenMixin baja el emblema con los botones).
     */
    public static int[][] mainLayout(int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        int s = TFPill.scale();
        float total = 3f + GAP_Y * (ROW_H.length - 1);
        for (int v : ROW_H) total += v;
        float avail = height - BOTTOM_MARGIN - Math.max(84f, height * 0.40f);
        float k = Mth.clamp(avail / total, 0.62f, 1f);
        int[] hs = new int[ROW_H.length];
        int comp = 0;
        for (int r = 0; r < ROW_H.length; r++) {
            hs[r] = Math.max(14, Math.round(ROW_H[r] * k));
            comp += hs[r];
        }
        int gy = Math.max(3, Math.round(GAP_Y * k)), extra = Math.round(3 * k); // (y un poco más bajo el principal)
        comp += gy * (ROW_H.length - 1) + extra;
        float free = height - BOTTOM_MARGIN - comp;
        float top = free - Math.max(0f, (free - 186f) / 2f);
        tfTextSize = crisp(CAP_TF * hs[0] / CAP_HEIGHT, s);
        mainTextSize = crisp(CAP * hs[1] / CAP_HEIGHT, s);
        socialTextSize = crisp(CAP_SOCIAL * hs[4] / CAP_HEIGHT, s);
        String[] labels = {TFConfig.serverName(), "Mundo local", "Mods", "Opciones", "Salir", "WhatsApp", "Web", "Discord"};
        int[][] out = new int[9][];
        float y = top;
        for (int r = 0; r < ROWS.length; r++) {
            float size = r == 0 ? tfTextSize : r == 4 ? socialTextSize : mainTextSize;
            int n = ROWS[r].length;
            float[] ws = new float[n];
            float row = GAP_X * (n - 1);
            for (int j = 0; j < n; j++) {
                int i = ROWS[r][j];
                // (1.3.125) medido con la misma letra que se dibuja (cinzel_n): con otra, el ancho no cuadraba por medio
                // píxel y el texto se encogía con una escala rota («WhatsApp» se veía comido)
                float textW = new Tracked(mc.font, labels[i], FONT_AT_SCALE[fontIndex(size, s)]).width * size;
                boolean withIcon = i >= 5;
                // aire de los lados: el radio de la pastilla, el hueco y los hilos de oro (los de icono, sin hilos)
                float pad = hs[r] * (r == 0 ? 3.0f : withIcon ? 1.0f : 2.3f);
                ws[j] = 2 * Math.round((textW + (withIcon ? (TFMenuIcons.SIZE + GAP) * size : 0f) + pad) / 2f);
                row += ws[j];
            }
            float fit = Math.min(1f, (width - 16f) / row);
            float x = (width - row * fit) / 2f;
            for (int j = 0; j < n; j++) {
                out[ROWS[r][j]] = new int[] {Math.round(x), Math.round(y), Math.round(ws[j] * fit), hs[r]};
                x += ws[j] * fit + GAP_X * fit;
            }
            y += hs[r] + gy + (r == 0 ? extra : 0);
        }
        out[8] = new int[] {Math.round(top)};
        return out;
    }

    /** La letra a la escala de la interfaz para ese tamaño (índice en FONT_AT_SCALE: n - 1). */
    private static int fontIndex(float size, int s) {
        return Mth.clamp(Math.round(s * size), 1, FONT_AT_SCALE.length) - 1;
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
                Kind.WEB, TFMenuIcons.Icon.WEB, PURPLE);
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

    /** El color de cada botón del menú principal al pasar el ratón (y el de los demás menús). */
    private TFPill.Style style() {
        if (kind == Kind.PRIMARY) return TFPill.Style.GOLD;
        if (kind == Kind.DANGER) return TFPill.Style.RED;
        if (kind == Kind.WHATSAPP) return TFPill.Style.GREEN;
        if (kind == Kind.DISCORD) return TFPill.Style.BLURPLE;
        if (kind == Kind.WEB) return TFPill.Style.PURPLE;
        return TFPill.Style.VIOLET;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int s = TFPill.scale();
        long now = Util.getMillis();
        // el foco solo cuenta con el teclado: tras un clic el botón se queda con el foco y no debe seguir encendido
        boolean hot = this.active && (this.isHovered() || this.isFocused() && mc.getLastInputType().isKeyboard()
                || plate != null && plate.equals(testHot));
        float dt = lastFrame == 0L ? 1000f : Math.min(250f, now - lastFrame);
        lastFrame = now;
        float step = dt / HOVER_MS;
        hover = hot ? Math.min(1f, hover + step) : Math.max(0f, hover - step);
        boolean pressed = this.active && this.isHovered() && mc.mouseHandler.isLeftPressed();
        float e = this.active ? hover * hover * (3f - 2f * hover) : 0f;
        float alpha = this.alpha;
        boolean menu = plate != null, primary = kind == Kind.PRIMARY && menu;
        // sube un píxel real con el ratón y se hunde medio al pulsar (en píxeles reales: nítido)
        float lift = pressed ? Math.max(1, Math.round(0.5f * s)) / (float) s : -Math.round(e * s) / (float) s;
        g.pose().pushPose();
        g.pose().translate(0f, lift, 0f);
        TFPill.paint(g, getX(), getY(), getWidth(), getHeight(), style(), e, pressed, this.active, primary, alpha);
        float size = !menu ? 1f : primary ? tfTextSize : icon != null ? socialTextSize : mainTextSize;
        float side = menu ? getHeight() * 0.5f : getWidth() * 0.14f;
        // los hilos de oro: siempre en el principal; en los demás (sin icono), crecen con el ratón
        float thread = primary ? 1f : icon == null ? e : 0f;
        paintContent(g, mc.font, s, getX() + side, getX() + getWidth() - side, getY() + getHeight() / 2f, size, e, alpha, thread);
        g.pose().popPose();
        RenderSystem.defaultBlendFunc();
    }

    /** Icono (WhatsApp, Web y Discord) y texto centrados entre x0 y x1, con contorno oscuro; y los hilos de oro. */
    private void paintContent(GuiGraphics g, Font font, int s, float x0, float x1, float centerY, float size, float e, float alpha, float thread) {
        if (alpha * 255f < 4f) return; // casi transparente: Minecraft pintaría el texto opaco
        boolean menu = plate != null;
        boolean goldText = kind == Kind.PRIMARY && menu && this.active;
        int ink = !this.active ? DISABLED : goldText ? fill(true, e) : fill(false, e);

        // Letra a la escala de la interfaz (n píxeles reales por píxel de glifo; como mucho un 10 % más que `size`): nítida
        ResourceLocation[] fonts = FONT_AT_SCALE;
        int n = Mth.clamp(Math.round(s * size), 1, fonts.length);
        if (n > 1 && n / (float) s > size * 1.1f) n--;
        float scale = n / (float) s;
        MutableComponent text = Component.literal(getMessage().getString())
                .setStyle(Style.EMPTY.withFont(fonts[n - 1]));
        // en el menú, las letras un poco más juntas (TRACKING menos entre cada dos)
        Tracked tracked = menu ? new Tracked(font, getMessage().getString(), fonts[n - 1]) : null;
        float textW = tracked != null ? tracked.width : font.width(text);
        boolean withIcon = icon != null;
        // en el menú puede ocupar también parte de las puntas redondas
        float maxW = menu ? x1 - x0 + getHeight() * 0.5f : x1 - x0;
        float iconW = withIcon ? (TFMenuIcons.SIZE + GAP) * scale : 0f; // el icono crece con la letra
        float group = textW * scale + iconW;
        // (1.3.125) si no cabe, una letra más pequeña pero nítida (n - 1 píxeles por píxel de letra), nunca una escala
        // rota: con ella se perdían columnas de píxeles de las letras
        while (menu && group > maxW && n > 1) {
            n--;
            scale = n / (float) s;
            text = Component.literal(getMessage().getString()).setStyle(Style.EMPTY.withFont(fonts[n - 1]));
            tracked = new Tracked(font, getMessage().getString(), fonts[n - 1]);
            textW = tracked.width;
            iconW = withIcon ? (TFMenuIcons.SIZE + GAP) * scale : 0f;
            group = textW * scale + iconW;
        }
        if (!menu && group > maxW && group > 0f) {
            scale *= Math.max(0.6f, (maxW - iconW) / (textW * scale));
            group = textW * scale + iconW;
        }
        float gx = snap((x0 + x1) / 2f - group / 2f, s);
        TFPill.threads(g, getX(), getY(), getWidth(), getHeight(), gx, gx + group, thread, e, alpha);
        FormattedCharSequence seq = text.getVisualOrderText();
        int edge = goldText ? GOLD_EDGE : EDGE;
        if (!menu) {
            // fuera del menú principal (Cinzel): contorno fino y un color
            int ro = Math.max(1, Math.round(s * 0.5f));
            float ty = snap(centerY - TEXT_UP * scale, s);
            int dark = TFMenuPaint.argb(0.9f * alpha, edge);
            for (int dx = -ro; dx <= ro; dx++) {
                for (int dy = -ro; dy <= ro; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == ro) drawText(g, font, seq, gx + dx / (float) s, ty + dy / (float) s, scale, dark);
                }
            }
            drawText(g, font, seq, gx, ty, scale, TFMenuPaint.argb(alpha, ink));
            return;
        }

        // Menú principal (1.3.125, el dueño: «el contorno de las letras se ve raro»): sin contorno (la Cinzel es fina y el
        // contorno de un píxel la emborronaba); una sombra suave de un píxel real debajo, como el texto de los diálogos
        float shadow = Math.max(1, Math.round(s * 0.5f)) / (float) s;
        if (withIcon) {
            float sz = TFMenuIcons.SIZE * scale;
            float iy = snap(centerY - sz / 2f, s);
            icon(g, gx, iy + shadow, sz, edge, 0.6f * alpha);
            icon(g, gx, iy, sz, ink, alpha);
            gx += iconW;
        }
        float ty = snap(centerY - MENU_TEXT_UP * scale, s), tx = gx, sc = scale;
        Tracked letters = tracked;
        // todo el texto (sombra y letra) en un solo lote: una sola vez a la tarjeta gráfica
        g.drawManaged(() -> {
            letters.draw(g, font, tx, ty + shadow, sc, TFMenuPaint.argb(0.6f * alpha, edge));
            letters.draw(g, font, tx, ty, sc, TFMenuPaint.argb(alpha, ink));
        });
    }

    /** Un texto dibujado letra a letra con las letras un poco más juntas (TRACKING píxeles de glifo menos entre cada dos). */
    private static final class Tracked {
        final FormattedCharSequence[] chars;
        final float[] x;
        final float width;

        Tracked(Font font, String text, ResourceLocation fontId) {
            int[] cps = text.codePoints().toArray();
            chars = new FormattedCharSequence[cps.length];
            x = new float[cps.length];
            float at = 0f;
            for (int i = 0; i < cps.length; i++) {
                MutableComponent c = Component.literal(new String(Character.toChars(cps[i]))).setStyle(Style.EMPTY.withFont(fontId));
                chars[i] = c.getVisualOrderText();
                x[i] = at;
                at += font.getSplitter().stringWidth(c) - (cps[i] == ' ' ? 0f : TRACKING); // ancho exacto, sin redondear
            }
            width = Math.max(0f, at + (cps.length > 0 && cps[cps.length - 1] != ' ' ? TRACKING : 0f));
        }

        void draw(GuiGraphics g, Font font, float gx, float ty, float scale, int argb) {
            for (int i = 0; i < chars.length; i++) drawText(g, font, chars[i], gx + x[i] * scale, ty, scale, argb);
        }
    }

    /** Desplazamientos (en píxeles reales) del contorno: 8 direcciones a ro y, si es grueso, 8 a la mitad, sin repetir. */
    private static int[][] ring(int ro) {
        int[][] cached = RINGS.get(ro);
        if (cached != null) return cached;
        java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>();
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int n = 8;
            float r = pass == 0 ? ro : ro / 2f;
            if (pass == 1 && ro < 3) break;
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

    /**
     * Color liso de las letras (sin franjas ni sombreado): oro cálido en Tierras Fantásticas, que respira muy despacio,
     * y blanco crema en las demás, que con el ratón encima pasa a oro claro.
     */
    private static int fill(boolean goldText, float e) {
        if (!goldText) return TFMenuPaint.lerp(0xFF000000 | WHITE, 0xFF000000 | WHITE_HOT, e) & 0xFFFFFF;
        float breath = 0.5f + 0.5f * Mth.sin(Util.getMillis() / GOLD_BREATH_MS * Mth.TWO_PI);
        return TFMenuPaint.lerp(0xFF000000 | GOLD, 0xFF000000 | GOLD_HOT, Math.min(1f, 0.25f * breath + e)) & 0xFFFFFF;
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
