package net.tierrasfantasticas.tfclient.trail;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;

/**
 * Los ajustes de las estelas de arma (1.3.67), de cada jugador: config/tfclient/estelas.properties. Se cambian en el
 * pad, app Efectos › COMBATE. Las estelas son las de Afterswing (MIT) rehechas para Forge 1.20.1 (ver {@link TrailClient}).
 */
public final class TrailSettings {
    /** Mezcla: brillo (luz que suma, la más vistosa de noche), translúcida (se ven los colores oscuros) o sólida. */
    public static final String[] BLENDS = {"Brillo", "Translúcida", "Sólida"};
    /** Cómo se apaga la cola. */
    public static final String[] FADES = {"La del estilo", "Lenta", "Lineal", "Rápida"};
    /** Colores propios (para el modo de color elegido). */
    public static final int[] COLORS = {0x66FFEE, 0x5AB4FF, 0xB36BFF, 0xFF5AA8, 0xFF5A5A, 0xFFA03C, 0xFFE15A, 0x6CFF6C, 0xFFFFFF};
    public static final String[] COLOR_NAMES = {"Turquesa", "Azul", "Violeta", "Rosa", "Rojo", "Naranja", "Oro", "Verde", "Blanco"};

    /**
     * Apagadas de fábrica (1.3.74, el dueño). Se guardan con la clave «encendidas»: la «activadas» de antes (salía true en
     * cualquiera que hubiera tocado un ajuste) ya no se lee, así que con esta versión arrancan apagadas para todos.
     */
    public static boolean enabled = false;
    public static boolean swing = true;
    public static boolean pullOut = true;
    public static boolean others = true;
    /** Distancia a la que se ven las estelas de otros jugadores (bloques; 0 = sin límite). */
    public static int othersDistance = 32;
    /** Lo que dura cada trozo de estela, en ticks (2 a 20). */
    public static int length = 6;
    /** Qué parte de la hoja cubre, desde la punta (10 a 100 %). */
    public static int width = 60;
    /** Opacidad (10 a 100 %). */
    public static int opacity = 85;
    public static int blend = 0;
    public static int fade = 0;
    /** Lo más que se apaga la cola (0 a 60 %: 0 = del todo). */
    public static int minOpacity = 0;
    /** Línea brillante en el filo (0 a 100 %). */
    public static int core = 0;
    public static int profile = 0;
    /** Color: automático (por el material y los encantamientos) o el elegido. */
    public static boolean autoColor = true;
    public static int color = 0;
    public static boolean attackOnly = true;
    public static boolean cooldown = true;
    public static boolean lowDetail = false;
    public static boolean reducedFlash = false;
    public static boolean swords = true, axes = true, pickaxes = false, shovels = false, hoes = false, others_tools = true;

    private static boolean loaded;

    private TrailSettings() {}

    private static Path file() {
        return TFConfigDir.file("estelas.properties", null);
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            p.load(r);
            enabled = bool(p, "encendidas", enabled);
            swing = bool(p, "al.golpear", swing);
            pullOut = bool(p, "al.sacar", pullOut);
            others = bool(p, "otros.jugadores", others);
            othersDistance = num(p, "otros.distancia", othersDistance, 0, 128);
            length = num(p, "duracion", length, 2, 20);
            width = num(p, "ancho", width, 10, 100);
            opacity = num(p, "opacidad", opacity, 10, 100);
            blend = num(p, "mezcla", blend, 0, BLENDS.length - 1);
            fade = num(p, "apagado", fade, 0, FADES.length - 1);
            minOpacity = num(p, "opacidad.minima", minOpacity, 0, 60);
            core = num(p, "nucleo", core, 0, 100);
            profile = num(p, "estilo", profile, 0, TrailProfile.ALL.size() - 1);
            autoColor = bool(p, "color.auto", autoColor);
            color = num(p, "color", color, 0, COLORS.length - 1);
            attackOnly = bool(p, "solo.ataques", attackOnly);
            cooldown = bool(p, "segun.carga", cooldown);
            lowDetail = bool(p, "poco.detalle", lowDetail);
            reducedFlash = bool(p, "menos.destellos", reducedFlash);
            swords = bool(p, "espadas", swords);
            axes = bool(p, "hachas", axes);
            pickaxes = bool(p, "picos", pickaxes);
            shovels = bool(p, "palas", shovels);
            hoes = bool(p, "azadas", hoes);
            others_tools = bool(p, "otras.armas", others_tools);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudieron leer los ajustes de las estelas", e);
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("encendidas", Boolean.toString(enabled));
        p.setProperty("al.golpear", Boolean.toString(swing));
        p.setProperty("al.sacar", Boolean.toString(pullOut));
        p.setProperty("otros.jugadores", Boolean.toString(others));
        p.setProperty("otros.distancia", Integer.toString(othersDistance));
        p.setProperty("duracion", Integer.toString(length));
        p.setProperty("ancho", Integer.toString(width));
        p.setProperty("opacidad", Integer.toString(opacity));
        p.setProperty("mezcla", Integer.toString(blend));
        p.setProperty("apagado", Integer.toString(fade));
        p.setProperty("opacidad.minima", Integer.toString(minOpacity));
        p.setProperty("nucleo", Integer.toString(core));
        p.setProperty("estilo", Integer.toString(profile));
        p.setProperty("color.auto", Boolean.toString(autoColor));
        p.setProperty("color", Integer.toString(color));
        p.setProperty("solo.ataques", Boolean.toString(attackOnly));
        p.setProperty("segun.carga", Boolean.toString(cooldown));
        p.setProperty("poco.detalle", Boolean.toString(lowDetail));
        p.setProperty("menos.destellos", Boolean.toString(reducedFlash));
        p.setProperty("espadas", Boolean.toString(swords));
        p.setProperty("hachas", Boolean.toString(axes));
        p.setProperty("picos", Boolean.toString(pickaxes));
        p.setProperty("palas", Boolean.toString(shovels));
        p.setProperty("azadas", Boolean.toString(hoes));
        p.setProperty("otras.armas", Boolean.toString(others_tools));
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "TF Client - estelas de arma");
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudieron guardar los ajustes de las estelas", e);
        }
    }

    /** Vuelve a lo de fábrica (el botón de la página). */
    public static void reset() {
        enabled = false;
        swing = pullOut = others = true;
        othersDistance = 32;
        length = 6;
        width = 60;
        opacity = 85;
        blend = fade = minOpacity = core = profile = color = 0;
        autoColor = attackOnly = cooldown = true;
        lowDetail = reducedFlash = false;
        swords = axes = others_tools = true;
        pickaxes = shovels = hoes = false;
    }

    private static boolean bool(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static int num(Properties p, String k, int def, int min, int max) {
        try {
            String v = p.getProperty(k);
            return v == null ? def : Math.max(min, Math.min(max, Integer.parseInt(v.trim())));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
