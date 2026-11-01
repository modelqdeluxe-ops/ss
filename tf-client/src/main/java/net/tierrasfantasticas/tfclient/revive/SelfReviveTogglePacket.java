/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Del jugador al servidor: ha mantenido la tecla (como en el revivemod). */
public class SelfReviveTogglePacket {
    public static void encode(SelfReviveTogglePacket msg, FriendlyByteBuf buf) {
    }

    public static SelfReviveTogglePacket decode(FriendlyByteBuf buf) {
        return new SelfReviveTogglePacket();
    }

    public static void handle(SelfReviveTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender != null && TFRevive.active()) {
                DownManager.requestSelfToggle(sender.getUUID());
            }
        });
        c.setPacketHandled(true);
    }
}
