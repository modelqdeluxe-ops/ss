package net.tierrasfantasticas.tfclient.claims.chat;

import net.tierrasfantasticas.tfclient.claims.TFClaims;
import net.tierrasfantasticas.tfclient.claims.gui.AdminClaimSubMenuHandler;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimMenuHandler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ChatPromptRouter {
    private static final long SUPPRESS_WINDOW_MS = 2000L;
    private static final Map<UUID, Suppression> suppressions = new ConcurrentHashMap<UUID, Suppression>();
    private static volatile boolean packetCaptureActive;

    private ChatPromptRouter() {
    }

    public static boolean isPacketCaptureActive() {
        return packetCaptureActive;
    }

    public static boolean hasPending(UUID uuid) {
        return uuid != null && (ClaimMenuHandler.hasPrompt(uuid) || AdminClaimSubMenuHandler.hasPendingTransfer(uuid));
    }

    public static boolean consume(ServerPlayer serverplayer, String s) {
        if (serverplayer != null && s != null) {
            MinecraftServer minecraftserver = serverplayer.getServer();
            if (minecraftserver == null) {
                return false;
            }
            UUID uuid = serverplayer.getUUID();
            UUID uuid1 = AdminClaimSubMenuHandler.popPendingTransfer(uuid);
            if (uuid1 != null) {
                String s2 = ChatPromptRouter.stripControlChars(s);
                ChatPromptRouter.markSuppressed(uuid, s);
                minecraftserver.execute(() -> ClaimMenuHandler.dispatchAdminTransfer(serverplayer, uuid1, s2));
                return true;
            }
            ClaimMenuHandler.PendingChat claimmenuhandler$pendingchat = ClaimMenuHandler.popPrompt(uuid);
            if (claimmenuhandler$pendingchat != null) {
                String s1 = ChatPromptRouter.stripControlChars(s);
                ChatPromptRouter.markSuppressed(uuid, s);
                minecraftserver.execute(() -> ClaimMenuHandler.dispatchPrompt(serverplayer, claimmenuhandler$pendingchat, s1));
                return true;
            }
            return false;
        }
        return false;
    }

    private static void markSuppressed(UUID uuid, String s) {
        suppressions.put(uuid, new Suppression(s, System.currentTimeMillis() + 2000L));
    }

    public static boolean shouldSuppress(UUID uuid, String s) {
        if (uuid != null && s != null) {
            Suppression chatpromptrouter$suppression = suppressions.get(uuid);
            if (chatpromptrouter$suppression == null) {
                return false;
            }
            if (System.currentTimeMillis() > chatpromptrouter$suppression.expiresAt()) {
                suppressions.remove(uuid, chatpromptrouter$suppression);
                return false;
            }
            if (!chatpromptrouter$suppression.rawText().equals(s)) {
                return false;
            }
            suppressions.remove(uuid, chatpromptrouter$suppression);
            return true;
        }
        return false;
    }

    public static void onPlayerDisconnect(UUID uuid) {
        if (uuid != null) {
            suppressions.remove(uuid);
        }
    }

    public static void markPacketCaptureActive() {
        if (!packetCaptureActive) {
            packetCaptureActive = true;
            TFClaims.LOGGER.info("[TF Claims] Captura de respuestas a nivel de paquete ACTIVA (compatible con Mohist y plugins de chat).");
        }
    }

    public static String stripControlChars(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder stringbuilder = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ++i) {
            char c0 = s.charAt(i);
            if (c0 == '\n' || c0 == '\r' || c0 == '\t') {
                stringbuilder.append(' ');
                continue;
            }
            if (c0 < ' ') continue;
            stringbuilder.append(c0);
        }
        return stringbuilder.toString().trim();
    }

    public static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder stringbuilder = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ++i) {
            char c0 = s.charAt(i);
            if ((c0 == '\u00a7' || c0 == '&') && i + 1 < s.length() && ChatPromptRouter.isColorCode(s.charAt(i + 1))) {
                ++i;
                continue;
            }
            if (c0 == '\n' || c0 == '\r' || c0 == '\t') {
                stringbuilder.append(' ');
                continue;
            }
            if (c0 < ' ') continue;
            stringbuilder.append(c0);
        }
        return stringbuilder.toString().trim().replaceAll("\\s{2,}", " ");
    }

    private static boolean isColorCode(char c0) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c0) >= 0;
    }

    public static boolean isCancel(String s) {
        if (s != null && !s.isBlank()) {
            String s1 = s.trim();
            return s1.equalsIgnoreCase("cancelar") || s1.equalsIgnoreCase("cancel") || s1.startsWith("/");
        }
        return true;
    }

    public static String extractPlayerName(String s) {
        String s1 = ChatPromptRouter.sanitize(s);
        if (!s1.isEmpty() && !ChatPromptRouter.isValidName(s1)) {
            String[] astring = s1.split(" ");
            for (int i = astring.length - 1; i >= 0; --i) {
                if (!ChatPromptRouter.isValidName(astring[i])) continue;
                return astring[i];
            }
            return s1;
        }
        return s1;
    }

    private static boolean isValidName(String s) {
        if (s != null && s.length() >= 3 && s.length() <= 16) {
            for (int i = 0; i < s.length(); ++i) {
                boolean flag;
                char c0 = s.charAt(i);
                boolean bl = flag = c0 >= 'a' && c0 <= 'z' || c0 >= 'A' && c0 <= 'Z' || c0 >= '0' && c0 <= '9' || c0 == '_';
                if (flag) continue;
                return false;
            }
            return true;
        }
        return false;
    }

    private record Suppression(String rawText, long expiresAt) {
    }
}

