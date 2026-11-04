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
import net.minecraft.network.chat.Component;
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
 * Al ponerla sale unida (lo de siempre) y se le abre el pad en esa piedra con la elección (Protección › la piedra:
 * UNIDA / INDEPENDIENTE, que cambia cuando quiera). Solo su dueño; el staff también con
 * {@code /tf web claims modo <zona> unir|independiente}. (Los jugadores no tienen /tf claims desde la 1.3.24.)
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
     * Cambia el modo de la piedra: true = independiente, false = unida. player null = el staff desde la consola. Devuelve
     * el texto para quien lo pidió (también si no se puede).
     */
    public static String setMode(ServerPlayer player, Claim claim, boolean independent) {
        if (claim == null) return "Esa zona ya no existe.";
        if (player != null && !claim.isOwner(player.getUUID()) && !player.hasPermissions(2)) return "Solo el dueño de la piedra elige cómo está.";
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

    /**
     * Al poner la piedra dentro de la zona de un grupo: el aviso en el chat y el pad abierto en esa piedra, con la elección
     * (UNIDA / INDEPENDIENTE) arriba.
     */
    public static void offer(ServerPlayer player, Claim claim) {
        ClaimGroup group = groupAround(claim);
        if (group == null) return;
        boolean pad = net.tierrasfantasticas.tfclient.pad.TFPadNet.openApp(player, "protecciones", "z:" + claim.getClaimId() + ":a");
        player.sendSystemMessage(Component.literal("Tu piedra está dentro de la zona «" + group.getName() + "». Elige en el pad"
                + (pad ? "" : " (Protección › tu piedra)") + ": UNIDA (una sola zona con la grande) o INDEPENDIENTE (tu zona, con tu borde, tu mensaje y tus reglas, dentro de la grande).")
                .withStyle(ChatFormatting.AQUA));
    }
}
