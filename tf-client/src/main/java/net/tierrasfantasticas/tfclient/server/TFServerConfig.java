package net.tierrasfantasticas.tfclient.server;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Ajustes del puente en el servidor: config/tfclient-server.properties.
 * La primera vez se crea con una clave aleatoria que hay que copiar en Cloudflare (BRIDGE_SECRET).
 */
public final class TFServerConfig {
    /** tierrasfantásticas.store escrito en ASCII (punycode), como lo usa internet. */
    public static final String DEFAULT_URL = "https://xn--tierrasfantsticas-hpb.store";
    private static final int DEFAULT_INTERVAL = 10;

    private static boolean enabled = true;
    private static String url = DEFAULT_URL;
    private static String secret = "";
    private static int interval = DEFAULT_INTERVAL;

    private TFServerConfig() {}

    public static boolean enabled() {
        return enabled && !secret.isEmpty() && !url.isEmpty();
    }

    public static String url() {
        return url;
    }

    public static String secret() {
        return secret;
    }

    public static int intervalSeconds() {
        return interval;
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("tfclient-server.properties");
    }

    public static void load() {
        Path file = file();
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                TFClient.LOGGER.warn("TF Bridge: no se pudo leer {}: {}", file, e.getMessage());
            }
        }
        enabled = !"false".equalsIgnoreCase(props.getProperty("bridge.enabled", "true").trim());
        url = props.getProperty("bridge.url", DEFAULT_URL).trim().replaceAll("/+$", "");
        secret = props.getProperty("bridge.secret", "").trim();
        try {
            interval = Math.max(5, Math.min(120, Integer.parseInt(props.getProperty("bridge.interval", "" + DEFAULT_INTERVAL).trim())));
        } catch (NumberFormatException e) {
            interval = DEFAULT_INTERVAL;
        }

        boolean created = secret.isEmpty();
        if (created) {
            byte[] bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            secret = HexFormat.of().formatHex(bytes);
        }
        if (created || !props.containsKey("bridge.url") || !props.containsKey("bridge.interval") || !props.containsKey("bridge.enabled")) {
            props.setProperty("bridge.enabled", Boolean.toString(enabled));
            props.setProperty("bridge.url", url);
            props.setProperty("bridge.secret", secret);
            props.setProperty("bridge.interval", Integer.toString(interval));
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(writer, "TF Client - puente con la web. Copia bridge.secret en Cloudflare como Secret BRIDGE_SECRET");
            } catch (IOException e) {
                TFClient.LOGGER.warn("TF Bridge: no se pudo crear {}: {}", file, e.getMessage());
            }
        }
        if (created) {
            TFClient.LOGGER.info("TF Bridge: se ha creado la clave del puente en {}. Cópiala en Cloudflare como Secret BRIDGE_SECRET.", file);
        }
    }
}
