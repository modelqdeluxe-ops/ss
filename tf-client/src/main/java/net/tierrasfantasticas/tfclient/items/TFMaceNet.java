/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.173) El golpe aplastante de la maza, a los jugadores que están cerca: el cliente hace lo que la 1.21 (la
 * explosión de polvo del suelo y el sonido de la maza; TFMaceClient). Como en Minecraft, el sonido sale de quien pega
 * y el polvo, del bloque que pisa el que recibe.
 */
public final class TFMaceNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "maza"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    /** A quién le llega (bloques): el sonido se oye a 16 y el polvo se ve de lejos. */
    static final double RANGE = 64.0;
    private static boolean registered;

    private TFMaceNet() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Smash.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Smash::write).decoder(Smash::read).consumerMainThread(Smash::handle).add();
    }

    /**
     * Un golpe aplastante. ground: el bloque que pisa el que recibe (el polvo; si es aire, no sale); onGround: el que
     * recibe estaba en el suelo (si no, el sonido «en el aire»); heavy: el que pega caía más de 5 bloques (el sonido
     * fuerte); x, y, z: dónde suena (el que pega); windBurst: el nivel de Ráfaga de viento (0 si no).
     */
    public record Smash(BlockPos ground, boolean onGround, boolean heavy, double x, double y, double z, int windBurst) {
        static void write(Smash m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.ground);
            buf.writeBoolean(m.onGround);
            buf.writeBoolean(m.heavy);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeVarInt(m.windBurst);
        }

        static Smash read(FriendlyByteBuf buf) {
            return new Smash(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readVarInt());
        }

        static void handle(Smash m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TFMaceClient.smash(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** A los jugadores con el TF Client a menos de RANGE bloques del golpe. */
    static void send(ServerLevel level, Smash m) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(m.x, m.y, m.z) > RANGE * RANGE) continue;
            try {
                if (CHANNEL.isRemotePresent(p.connection.connection)) {
                    CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), m);
                }
            } catch (RuntimeException ignored) {
                // un jugador de mentira (bots) sin conexión de verdad
            }
        }
    }
}
