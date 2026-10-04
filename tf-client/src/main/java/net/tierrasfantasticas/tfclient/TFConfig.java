package net.tierrasfantasticas.tfclient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;

/** Ajustes en config/tfclient.properties (se crea con los valores por defecto la primera vez). */
public final class TFConfig {
    private static final String DEFAULT_NAME = "Tierras Fantásticas";
    private static final String DEFAULT_ADDRESS = "216.163.187.40:19001";

    private static String serverName = DEFAULT_NAME;
    private static String serverAddress = DEFAULT_ADDRESS;

    private TFConfig() {}

    public static String serverName() {
        return serverName;
    }

    public static String serverAddress() {
        return serverAddress;
    }

    public static void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("tfclient.properties");
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                TFClient.LOGGER.warn("No se pudo leer {}: {}", file, e.getMessage());
            }
        }
        serverName = props.getProperty("server.name", DEFAULT_NAME).trim();
        serverAddress = props.getProperty("server.address", DEFAULT_ADDRESS).trim();
        if (serverAddress.isEmpty()) serverAddress = DEFAULT_ADDRESS;

        if (!Files.exists(file)) {
            props.setProperty("server.name", serverName);
            props.setProperty("server.address", serverAddress);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(writer, "TF Client - servidor al que conecta el boton Jugar");
            } catch (IOException e) {
                TFClient.LOGGER.warn("No se pudo crear {}: {}", file, e.getMessage());
            }
        }
    }

    /**
     * Desactiva la ventana de arranque de Forge (config/fml.toml, earlyWindowControl) para que desde el siguiente
     * arranque se vea la pantalla de carga de TF desde el principio. Forge lee ese archivo antes de cargar los mods,
     * así que el cambio se nota a partir del segundo arranque.
     */
    public static void disableForgeEarlyWindow() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("fml.toml");
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, "earlyWindowControl = false\n", StandardCharsets.UTF_8);
                return;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String updated;
            if (text.matches("(?s).*earlyWindowControl\\s*=.*")) {
                updated = text.replaceAll("earlyWindowControl\\s*=\\s*true", "earlyWindowControl = false");
            } else {
                updated = text + (text.endsWith("\n") ? "" : "\n") + "earlyWindowControl = false\n";
            }
            if (!updated.equals(text)) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
                TFClient.LOGGER.info("TF Client: ventana de arranque de Forge desactivada para el siguiente arranque");
            }
        } catch (IOException e) {
            TFClient.LOGGER.warn("TF Client: no se pudo cambiar {}: {}", file, e.getMessage());
        }
    }
}
