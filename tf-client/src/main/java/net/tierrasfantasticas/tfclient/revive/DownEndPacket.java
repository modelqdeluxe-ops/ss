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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.tierrasfantasticas.tfclient.revive.client.RevivemodClient;

/** Del servidor al jugador: ya no estás noqueado. */
public class DownEndPacket {
    public static void encode(DownEndPacket msg, FriendlyByteBuf buf) {
    }

    public static DownEndPacket decode(FriendlyByteBuf buf) {
        return new DownEndPacket();
    }

    public static void handle(DownEndPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> RevivemodClient::onDownEnd));
        c.setPacketHandled(true);
    }
}
