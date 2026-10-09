package net.tierrasfantasticas.tfclient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;

/** Ajustes en config/tfclient/cliente.properties (se crea con los valores por defecto la primera vez). */
public final class TFConfig {
    private static final String DEFAULT_NAME = "Tierras Fantásticas";
    /** Servidor de pruebas por ahora. El de siempre es 216.163.187.40:19001. */
    private static final String DEFAULT_ADDRESS = "216.163.187.40:19229";
    private static final String OLD_DEFAULT_ADDRESS = "216.163.187.40:19001";
    /** tierrasfantásticas.store escrito en ASCII (punycode), como lo usa internet. */
    private static final String DEFAULT_WEB = "https://xn--tierrasfantsticas-hpb.store";
    private static final String OLD_DEFAULT_WEB = "https://tienda.tierrasfantasticas.net";
    private static final String DEFAULT_DISCORD_GUILD = "1439703812368765082";
    private static final String DEFAULT_DISCORD_URL = "https://discord.gg/tRrunHBZE";

    private static String serverName = DEFAULT_NAME;
    private static String serverAddress = DEFAULT_ADDRESS;
    private static String webUrl = DEFAULT_WEB;
    private static String discordGuildId = DEFAULT_DISCORD_GUILD;
    private static String discordUrl = DEFAULT_DISCORD_URL;

    private TFConfig() {}

    public static String serverName() {
        return serverName;
    }

    public static String serverAddress() {
        return serverAddress;
    }

    public static String webUrl() {
        return webUrl;
    }

    /** ID del servidor de Discord: el botón pide a su widget una invitación del servidor (no de una persona). */
    public static String discordGuildId() {
        return discordGuildId;
    }

    /** Enlace de reserva si el widget de Discord no responde. */
    public static String discordUrl() {
        return discordUrl;
    }

    public static void load() {
        Path file = net.tierrasfantasticas.tfclient.util.TFConfigDir.file("cliente.properties", "tfclient.properties");
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
        // Quien tenía la IP por defecto anterior pasa a la nueva (si la cambió a mano, se respeta).
        boolean oldAddress = serverAddress.isEmpty() || serverAddress.equals(OLD_DEFAULT_ADDRESS);
        if (oldAddress) serverAddress = DEFAULT_ADDRESS;
        webUrl = props.getProperty("web.url", DEFAULT_WEB).trim();
        // La dirección provisional de versiones anteriores pasa a ser el dominio de verdad.
        boolean oldWeb = webUrl.isEmpty() || webUrl.equals(OLD_DEFAULT_WEB);
        if (oldWeb) webUrl = DEFAULT_WEB;
        discordGuildId = props.getProperty("discord.guild.id", DEFAULT_DISCORD_GUILD).trim();
        discordUrl = props.getProperty("discord.url", DEFAULT_DISCORD_URL).trim();

        // Se reescribe si falta alguna clave (por ejemplo al actualizar desde una versión anterior)
        boolean missing = oldWeb || oldAddress || !props.containsKey("web.url") || !props.containsKey("discord.guild.id") || !props.containsKey("discord.url");
        if (!Files.exists(file) || missing) {
            props.setProperty("server.name", serverName);
            props.setProperty("server.address", serverAddress);
            props.setProperty("web.url", webUrl);
            props.setProperty("discord.guild.id", discordGuildId);
            props.setProperty("discord.url", discordUrl);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(writer, "TF Client - servidor del boton principal, web del boton WEB y Discord");
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
