/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.claims.gui;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimGroup;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;

/**
 * (1.3.170, el dueño: «que las protecciones, bajo lo de unir protecciones, me den a elegir si mi prote es independiente o
 * la uno a la más grande. Si pongo que es independiente estará dentro igual de la prote más grande, pero se marcará la
 * zona de mi prote más pequeña, mi mensaje y todo normal; y si le doy a unir a la más grande, se hace una sola»)
 * <p>
 * Una piedra de un invitado (o del dueño) puesta dentro de la zona de un grupo puede estar:
 * <ul>
 *   <li><b>Unida</b> (como siempre): una sola zona con la grande; usa sus reglas, su mensaje y su altura.</li>
 *   <li><b>Independiente</b>: su propia zona dentro de la grande (su borde, sus mensajes, sus reglas, sus miembros y
 *   baneos). En su cuadrado manda ella; fuera, la grande. Está ahí gracias al grupo: si su dueño sale del grupo o se
 *   disuelve el grupo con la grande puesta, la piedra vuelve a su dueño como las unidas.</li>
 * </ul>
 * Al ponerla sale unida (lo de siempre) y en el chat los dos botones; se cambia cuando quiera desde la piedra (pad o
 * menú) o con {@code /tf claims merge modo <zona> unir|independiente}. Solo su dueño (o un operador).
 */
public final class ClaimNesting {
    private ClaimNesting() {}

    /** El grupo en cuya zona está la piedra (unida o independiente), o null. */
    public static ClaimGroup groupAround(Claim claim) {
        if (claim == null) return null;
        ClaimManager manager = ClaimManager.getInstance();
        if (claim.getGroupId() != null && !claim.isGroupMother()) return manager.getGroup(claim.getGroupId());
        return claim.isNestedIndependent() ? manager.getGroup(claim.getInsideGroup()) : null;
    }

    public static Claim byId(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            for (Claim claim : ClaimManager.getInstance().getAllClaims()) {
                if (claim.getClaimId().equals(uuid)) return claim;
            }
        } catch (IllegalArgumentException ignored) {
            // no es una zona
        }
        return null;
    }

    /**
     * Cambia el modo de la piedra: true = independiente, false = unida. Devuelve el texto para el jugador (también si no
     * se puede).
     */
    public static String setMode(ServerPlayer player, Claim claim, boolean independent) {
        if (claim == null) return "Esa zona ya no existe.";
        if (!claim.isOwner(player.getUUID()) && !player.hasPermissions(2)) return "Solo el dueño de la piedra elige cómo está.";
        ClaimGroup group = groupAround(claim);
        if (group == null) return "Esta piedra no está dentro de la zona de un grupo.";
        ClaimManager manager = ClaimManager.getInstance();
        if (independent) {
            if (claim.isNestedIndependent()) return "Tu zona ya es independiente dentro de «" + group.getName() + "».";
            if (!manager.makeIndependent(claim)) return "Esta piedra no se puede separar (es la principal del grupo).";
            return "Tu zona ahora es independiente dentro de «" + group.getName() + "»: tu borde, tu mensaje y tus reglas.";
        }
        if (!claim.isNestedIndependent()) return "Tu piedra ya está unida a «" + group.getName() + "».";
        if (!manager.joinFromInside(claim)) return "No se puede unir: ya no estás en el grupo «" + group.getName() + "».";
        return "Tu piedra se unió a «" + group.getName() + "»: ahora es una sola zona.";
    }

    /** En el chat: «Tu piedra está dentro de la zona «X». ¿Cómo la quieres?» con [UNIDA] [INDEPENDIENTE]. */
    public static void offer(ServerPlayer player, Claim claim) {
        ClaimGroup group = groupAround(claim);
        if (group == null) return;
        boolean independent = claim.isNestedIndependent();
        String id = claim.getClaimId().toString();
        MutableComponent line = Component.literal("Tu piedra está dentro de la zona «" + group.getName() + "». ¿Cómo la quieres? ")
                .withStyle(ChatFormatting.AQUA);
        line.append(button("UNIDA", !independent, "/tf claims merge modo " + id + " unir",
                "Una sola zona con «" + group.getName() + "»: sus reglas, su mensaje y su altura."));
        line.append(Component.literal(" "));
        line.append(button("INDEPENDIENTE", independent, "/tf claims merge modo " + id + " independiente",
                "Tu zona dentro de «" + group.getName() + "»: tu borde, tu mensaje, tus reglas y tus miembros."));
        player.sendSystemMessage(line);
    }

    private static Component button(String label, boolean chosen, String command, String hover) {
        if (chosen) {
            return Component.literal("[✔ " + label + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Así está ahora. " + hover))));
        }
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clic: " + hover))));
    }
}
