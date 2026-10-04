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
}
