/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El canal de revivir (los mismos cuatro mensajes que el revivemod): rendirse y auto-revivir (jugador → servidor) y
 * empieza / acaba el noqueo (servidor → jugador). Se registra siempre, aunque el revivir no esté activo, para que el
 * servidor y los jugadores tengan los mismos canales.
 */
public final class ReviveNetwork {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "revivir"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    private ReviveNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(SurrenderTogglePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SurrenderTogglePacket::encode).decoder(SurrenderTogglePacket::decode).consumerNetworkThread(SurrenderTogglePacket::handle).add();
        CHANNEL.messageBuilder(SelfReviveTogglePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelfReviveTogglePacket::encode).decoder(SelfReviveTogglePacket::decode).consumerNetworkThread(SelfReviveTogglePacket::handle).add();
        CHANNEL.messageBuilder(DownStartPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DownStartPacket::encode).decoder(DownStartPacket::decode).consumerNetworkThread(DownStartPacket::handle).add();
        CHANNEL.messageBuilder(DownEndPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DownEndPacket::encode).decoder(DownEndPacket::decode).consumerNetworkThread(DownEndPacket::handle).add();
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        try {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
        } catch (Throwable ignored) {
            // (un jugador sin conexión de verdad, como un bot: no pasa nada)
        }
    }
}
