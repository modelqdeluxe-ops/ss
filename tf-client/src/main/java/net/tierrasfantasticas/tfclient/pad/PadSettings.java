/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/** Ajustes del pad (en el cliente): config/tfclient/pad-cliente.properties. */
final class PadSettings {
    static boolean sounds = true;
    static boolean hoverTick = true;
    static boolean animations = true;
    /** Volumen de los sonidos del pad, 0-100. */
    static int volume = 80;
    /** El tema del pad (color del cristal): índice de {@link #THEMES}. */
    static int theme = 0;
    /** (1.3.155) El fondo del menú principal: «tema» (el del cristal), «c:» un color, «i:» uno dibujado, «f:» un PNG tuyo. */
    static String wallpaper = "tema";
    private static boolean loaded;

    private PadSettings() {}

    /** Un tema del pad: su nombre, el color del cristal arriba y abajo, el de los monogramas TF y el de los neones y gemas. */
    record Theme(String name, int top, int bottom, int deco, int neon) {}

    /** Las paletas del pad (Ajustes › Tema). La primera es la de siempre (el azul del marco). */
    static final Theme[] THEMES = {
            new Theme("Cielo", 0x54D0FC, 0x60D5FC, 0xFFFFFF, 0),
            new Theme("Rosa", 0xFFB9DC, 0xFF8EC6, 0xFFFFFF, 0xFF6EC7),
            new Theme("Lavanda", 0xCDB9FF, 0xA88CF5, 0xFFFFFF, 0xB58CFF),
            new Theme("Menta", 0xA2F2D5, 0x5ED8AB, 0xFFFFFF, 0x3DF0B0),
            new Theme("Atardecer", 0xFFCC92, 0xFF9670, 0xFFFFFF, 0xFF9A3D),
            new Theme("Oro", 0xFFE69A, 0xF4C24F, 0xFFFFFF, 0xFFD23D),
            new Theme("Fresa", 0xFFA6B2, 0xF0647C, 0xFFFFFF, 0xFF4D6D),
            new Theme("Noche", 0x3B4E9E, 0x1D2860, 0xA9B8FF, 0x6F8CFF),
    };

    static Theme theme() {
        return THEMES[Math.max(0, Math.min(THEMES.length - 1, theme))];
    }

    private static Path file() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("pad-cliente.properties", "tfclient-pad.properties");
    }

    static void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            p.load(r);
            sounds = Boolean.parseBoolean(p.getProperty("sonidos", "true"));
            hoverTick = Boolean.parseBoolean(p.getProperty("tic.al.pasar", "true"));
            animations = Boolean.parseBoolean(p.getProperty("animaciones", "true"));
            volume = Math.max(0, Math.min(100, Integer.parseInt(p.getProperty("volumen", "80").trim())));
            theme = Math.max(0, Math.min(THEMES.length - 1, Integer.parseInt(p.getProperty("tema", "0").trim())));
            wallpaper = p.getProperty("fondo", "tema").trim();
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudieron leer los ajustes", e);
        }
    }

    static void save() {
        Properties p = new Properties();
        p.setProperty("sonidos", Boolean.toString(sounds));
        p.setProperty("tic.al.pasar", Boolean.toString(hoverTick));
        p.setProperty("animaciones", Boolean.toString(animations));
        p.setProperty("volumen", Integer.toString(volume));
        p.setProperty("tema", Integer.toString(theme));
        p.setProperty("fondo", wallpaper == null ? "tema" : wallpaper);
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "TF Pad - ajustes del jugador");
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudieron guardar los ajustes", e);
        }
    }
}
