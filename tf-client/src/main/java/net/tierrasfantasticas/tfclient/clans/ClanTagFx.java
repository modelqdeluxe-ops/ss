/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * (1.3.159, el dueño: «quiero un editor completo de efecto arcoíris y degradado en el tag del clan») El efecto de la
 * etiqueta del clan:
 * <ul>
 *   <li><b>solido</b>: un color (el de siempre).</li>
 *   <li><b>degradado</b>: de un color a otro (2 o 3), letra a letra.</li>
 *   <li><b>arcoiris</b>: todos los colores, que corren (velocidad 1 a 5).</li>
 *   <li><b>onda</b>: el degradado, que va y viene por la etiqueta.</li>
 * </ul>
 * En el chat y en el TAB (que no se pueden animar: lo pinta el servidor una vez) sale como se ve en el instante 0; encima
 * de la cabeza, en la lista de jugadores, en el estandarte y en el pad lo anima el TF Client de cada uno (ClanLooks).
 */
public final class ClanTagFx {
    public static final String SOLID = "solido", GRADIENT = "degradado", RAINBOW = "arcoiris", WAVE = "onda";
    public static final String[] MODES = {SOLID, GRADIENT, RAINBOW, WAVE};

    /**
     * Un efecto: modo, colores (1 a 3), velocidad (1 a 5) y, en arcoíris (1.3.160, el dueño: «pon varios efectos de
     * arcoíris, muchos colores, como los títulos de las crates»), el estilo: 0 el arcoíris de siempre; 1 en adelante, los
     * de los títulos de las crates (FSRainbow: Fuego, Océano, Neón, Galaxia, Aurora…).
     */
    public record Fx(String mode, int[] colors, int speed, int style) {
        public Fx(String mode, int[] colors, int speed) {
            this(mode, colors, speed, 0);
        }

        public boolean animated() {
            return mode.equals(RAINBOW) || mode.equals(WAVE);
        }

        /** «modo|RRGGBB,RRGGBB|velocidad[|estilo]» (para la red y las acciones del pad). */
        public String encode() {
            StringBuilder b = new StringBuilder(mode).append('|');
            for (int i = 0; i < colors.length; i++) b.append(i == 0 ? "" : ",").append(String.format("%06X", colors[i] & 0xFFFFFF));
            b.append('|').append(speed);
            if (mode.equals(RAINBOW) && style > 0) b.append('|').append(style);
            return b.toString();
        }
    }

    /** Cuántos estilos de arcoíris hay (el 0, el de siempre, y los de las crates). */
    public static int styles() {
        return net.tierrasfantasticas.tfclient.crates.client.color.FSRainbow.count();
    }

    /** El nombre del estilo de arcoíris. */
    public static String styleName(int style) {
        return style <= 0 ? "Arcoíris" : net.tierrasfantasticas.tfclient.crates.client.color.FSRainbow.name(style);
    }

    private ClanTagFx() {}

    public static Fx solid(int rgb) {
        return new Fx(SOLID, new int[] {rgb & 0xFFFFFF}, 2);
    }

    /** Lo lee de «modo|RRGGBB,RRGGBB|velocidad» (lo que no se entienda, sólido del color base). */
    public static Fx decode(String s, int base) {
        if (s == null || s.isEmpty()) return solid(base);
        String[] p = s.split("\\|");
        String mode = p[0];
        boolean known = false;
        for (String m : MODES) known |= m.equals(mode);
        if (!known) return solid(base);
        int[] cols = parseColors(p.length > 1 ? p[1] : "", base);
        int speed = 2, style = 0;
        try {
            if (p.length > 2) speed = Math.max(1, Math.min(5, Integer.parseInt(p[2].trim())));
            if (p.length > 3) style = Math.max(0, Math.min(styles() - 1, Integer.parseInt(p[3].trim())));
        } catch (NumberFormatException ignored) {
            // la de siempre
        }
        return new Fx(mode, cols, speed, style);
    }

    static int[] parseColors(String list, int base) {
        String[] parts = list.split(",");
        int n = 0;
        int[] out = new int[3];
        for (String part : parts) {
            String h = part.trim().replace("#", "");
            if (!h.matches("[0-9a-fA-F]{6}") || n >= 3) continue;
            out[n++] = Integer.parseInt(h, 16);
        }
        if (n == 0) return new int[] {base & 0xFFFFFF};
        int[] cols = new int[n];
        System.arraycopy(out, 0, cols, 0, n);
        return cols;
    }

    /** El efecto del clan (en su JSON: tagEfecto, tagColores, tagVelocidad; si no tiene, sólido de su color). */
    public static Fx of(JsonObject clan, int base) {
        if (clan == null || !clan.has("tagEfecto")) return solid(base);
        String mode = clan.get("tagEfecto").getAsString();
        // (1.3.160) el arcoíris se compra en la tienda del clan: sin él, de un color
        if (mode.equals(RAINBOW) && !rainbowOwned(clan)) return solid(base);
        StringBuilder cols = new StringBuilder();
        if (clan.has("tagColores") && clan.get("tagColores").isJsonArray()) {
            JsonArray a = clan.getAsJsonArray("tagColores");
            for (JsonElement e : a) cols.append(cols.length() == 0 ? "" : ",").append(e.getAsString());
        }
        int speed = clan.has("tagVelocidad") ? clan.get("tagVelocidad").getAsInt() : 2;
        int style = clan.has("tagEstilo") ? clan.get("tagEstilo").getAsInt() : 0;
        Fx fx = decode(mode + "|" + cols + "|" + speed + "|" + style, base);
        // el primer color manda en lo que no se anima (y es «el color del clan»)
        return fx.mode().equals(SOLID) ? solid(base) : fx;
    }

    /** Lo guarda en el JSON del clan. */
    public static void store(JsonObject clan, Fx fx) {
        clan.addProperty("tagEfecto", fx.mode());
        JsonArray a = new JsonArray();
        for (int c : fx.colors()) a.add(String.format("#%06X", c & 0xFFFFFF));
        clan.add("tagColores", a);
        clan.addProperty("tagVelocidad", fx.speed());
        clan.addProperty("tagEstilo", fx.style());
        clan.addProperty("color", String.format("#%06x", fx.colors()[0] & 0xFFFFFF));
    }

    /** El color de la letra i de n en el instante t (segundos). */
    public static int colorAt(Fx fx, int i, int n, double t) {
        int[] c = fx.colors();
        double u = n <= 1 ? 0 : i / (double) (n - 1);
        double v = 0.18 + 0.22 * fx.speed();
        switch (fx.mode()) {
            case RAINBOW -> {
                if (fx.style() > 0) {
                    // los estilos de los títulos de las crates: su paleta corriendo letra a letra
                    return net.tierrasfantasticas.tfclient.crates.client.color.FSRainbow.color(fx.style(), (float) (i / Math.max(1.0, n) * 0.85),
                            (float) (-t * v * 0.5)) & 0xFFFFFF;
                }
                double h = (i / Math.max(1.0, n) * 0.85 - t * v * 0.5) % 1.0;
                if (h < 0) h += 1;
                return java.awt.Color.HSBtoRGB((float) h, 0.72F, 1F) & 0xFFFFFF;
            }
            case GRADIENT -> {
                return along(c, u);
            }
            case WAVE -> {
                double w = (u * 0.9 - t * v * 0.6) % 2.0;
                if (w < 0) w += 2;
                return along(c, w > 1 ? 2 - w : w);
            }
            default -> {
                return c[0];
            }
        }
    }

    /** (1.3.160) ¿El clan compró los efectos arcoíris (tienda del clan, oferta «arcoiris»)? */
    public static boolean rainbowOwned(JsonObject clan) {
        return clan != null && clan.has("arcoiris") && clan.get("arcoiris").getAsBoolean();
    }

    /** El color en f (0 a 1) a lo largo de los colores. */
    static int along(int[] c, double f) {
        if (c.length == 1) return c[0];
        f = Math.max(0, Math.min(1, f)) * (c.length - 1);
        int k = Math.min(c.length - 2, (int) Math.floor(f));
        return mix(c[k], c[k + 1], f - k);
    }

    static int mix(int a, int b, double f) {
        int r = (int) Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * f);
        int g = (int) Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * f);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return r << 16 | g << 8 | bl;
    }

    /** El texto con su color letra a letra (en el instante t), con negrita si bold. */
    public static MutableComponent component(String text, Fx fx, double t, boolean bold) {
        MutableComponent out = Component.empty();
        int[] cps = text.codePoints().toArray();
        if (fx.mode().equals(SOLID)) {
            return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(fx.colors()[0])).withBold(bold));
        }
        for (int i = 0; i < cps.length; i++) {
            out.append(Component.literal(new String(Character.toChars(cps[i])))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colorAt(fx, i, cps.length, t))).withBold(bold)));
        }
        return out;
    }

    /** «&amp;#RRGGBB» letra a letra (para el sufijo de LuckPerms: el chat y el TAB lo entienden). */
    public static String legacyHex(String text, Fx fx) {
        if (fx.mode().equals(SOLID)) return "&" + String.format("#%06X", fx.colors()[0]) + text;
        StringBuilder b = new StringBuilder();
        int[] cps = text.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            b.append('&').append(String.format("#%06X", colorAt(fx, i, cps.length, 0))).appendCodePoint(cps[i]);
        }
        return b.toString();
    }
}
