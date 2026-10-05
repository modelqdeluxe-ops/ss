package net.tierrasfantasticas.tfclient.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;

/**
 * Botón Discord: abre una invitación del propio servidor, no de una persona. Se pide al widget de Discord
 * (discord.com/api/guilds/ID/widget.json), que da una invitación vigente del servidor; si falla, se usa discord.url.
 */
public final class TFDiscord {
    private TFDiscord() {}

    public static void open() {
        Thread thread = new Thread(() -> {
            String url = widgetInvite();
            String target = url != null ? url : TFConfig.discordUrl();
            Minecraft.getInstance().execute(() -> openUrl(target));
        }, "TF Client Discord");
        thread.setDaemon(true);
        thread.start();
    }

    private static String widgetInvite() {
        String guild = TFConfig.discordGuildId();
        if (guild.isEmpty() || !guild.chars().allMatch(Character::isDigit)) return null;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://discord.com/api/guilds/" + guild + "/widget.json").openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setRequestProperty("User-Agent", "TFClient/" + TFClient.VERSION);
            if (connection.getResponseCode() != 200) return null;
            try (InputStream in = connection.getInputStream()) {
                JsonObject widget = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!widget.has("instant_invite") || widget.get("instant_invite").isJsonNull()) return null;
                String invite = widget.get("instant_invite").getAsString();
                return invite.startsWith("https://discord.com/") || invite.startsWith("https://discord.gg/") ? invite : null;
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudo pedir la invitación al widget de Discord: {}", e.toString());
            return null;
        }
    }

    static void openUrl(String url) {
        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            Util.getPlatform().openUri(url);
        } else {
            TFClient.LOGGER.warn("TF Client: enlace no válido: {}", url);
        }
    }
}
