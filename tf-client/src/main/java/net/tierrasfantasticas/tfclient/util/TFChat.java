package net.tierrasfantasticas.tfclient.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Avisos de juego y economía (oficios, misiones, cazas, TF Pass, tienda web, ruleta, regalos...) con un único formato:
 * una sola línea de chat, prefijo pequeño de color y texto breve, por ejemplo «[Oficios] Minero nivel 12 · +35 monedas».
 * El dueño no quiere títulos, avisos encima de la barra rápida ni carteles: todo va al chat y corto.
 */
public final class TFChat {
    public static final String SEP = " · ";

    private TFChat() {}

    /** «[Oficios] » con el nombre en su color y los corchetes en gris oscuro. */
    public static MutableComponent tag(String name, ChatFormatting color) {
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(name).withStyle(color))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Una línea: el prefijo y las partes seguidas. Un String va en gris (texto normal); un Component va tal cual
     * (para resaltar con {@link #hl}, {@link #money} o el color de un oficio o rango).
     */
    public static MutableComponent line(String name, ChatFormatting color, Object... parts) {
        MutableComponent out = tag(name, color);
        for (Object part : parts) {
            if (part instanceof Component c) out.append(c);
            else if (part != null) out.append(Component.literal(part.toString()).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    /** Texto resaltado (lo que recibiste, el nombre de la misión...). */
    public static MutableComponent hl(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    /** Cantidades de monedas o premios («+35 monedas»). */
    public static MutableComponent money(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }

    public static void send(ServerPlayer player, String name, ChatFormatting color, Object... parts) {
        player.sendSystemMessage(line(name, color, parts));
    }
}
