package net.tierrasfantasticas.tfclient.pad;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/** Ajustes del pad (en el cliente): config/tfclient-pad.properties. */
final class PadSettings {
    static boolean sounds = true;
    static boolean hoverTick = true;
    static boolean animations = true;
    /** Volumen de los sonidos del pad, 0-100. */
    static int volume = 80;
    private static boolean loaded;

    private PadSettings() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("tfclient-pad.properties");
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
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "TF Pad - ajustes del jugador");
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudieron guardar los ajustes", e);
        }
    }
}
