/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Un bot de /tf bots como jugador de verdad (1.3.125, el dueño: «no tienen rango de LuckPerms al unirlos; no los reconoce
 * el TAB, ni el servidor, ni la consola como jugadores activos; no salen en la lista al hacer /tpa»). Entra como un
 * jugador: {@code PlayerList.placeNewPlayer} con una conexión falsa (como los bots de Carpet), así que Minecraft lo
 * pone en la lista de jugadores, Mohist le crea su jugador de Bukkit y los plugins lo ven (TAB lo cuenta, LuckPerms le
 * pone su grupo por defecto, EssentialsX y /tpa lo sugieren); en la consola sale su entrada y su salida.
 * <ul>
 *   <li>Sin cuerpo para nadie: no se manda a ningún jugador ({@link #broadcastToPlayer}).</li>
 *   <li>Para el juego cuenta como espectador ({@link #isSpectator}): los monstruos no lo atacan, no estorba para dormir
 *   y no choca con nada. En la lista del TAB sigue en supervivencia (lo que se manda es su modo de juego).</li>
 *   <li>No recibe daño. La conexión no manda nada (los paquetes se tiran); si lo echan (/kick), sale como cualquiera.</li>
 * </ul>
 * Con bots.reales=false en servidor.properties, o si algo falla al meterlo, el bot vuelve a ser solo una entrada de la
 * lista del TAB (TFBots).
 */
public final class TFBotPlayer extends ServerPlayer {
    /**
     * (1.3.157) Un jugador de prueba («diana») para la prueba en el juego de revivir: entra igual que un bot, pero es un
     * jugador normal (se ve, recibe daño, muere). Nunca lo usa el servidor de verdad.
     */
    private boolean target;

    private TFBotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile);
    }

    /** (1.3.157) Para las pruebas en el juego: mete una «diana» (un jugador que recibe daño) en level, en (x, y, z). */
    @Nullable
    public static ServerPlayer spawnTarget(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
        try {
            GameProfile profile = new GameProfile(java.util.UUID.nameUUIDFromBytes(("tf-diana-" + name).getBytes()), name);
            TFBotPlayer player = new TFBotPlayer(server, level, profile);
            player.target = true;
            server.getPlayerList().placeNewPlayer(new BotConnection(), player);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.teleportTo(level, x, y, z, 180F, 0F);
            return player;
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Bots: no se pudo meter la diana {}", name, t);
            return null;
        }
    }

    /** (1.3.157) Si es una diana de las pruebas en el juego. */
    public static boolean isTarget(ServerPlayer player) {
        return player instanceof TFBotPlayer b && b.target;
    }

    /** (1.3.157) Saca una diana de la prueba (como si se desconectara). */
    public static void removeTarget(ServerPlayer player) {
        despawn(player);
    }

    /** Lo mete en el servidor como un jugador; null si no se pudo (entonces va solo en la lista). */
    @Nullable
    static ServerPlayer spawn(MinecraftServer server, TFBots.Bot bot, int ping) {
        try {
            GameProfile profile = new GameProfile(bot.id(), bot.name());
            profile.getProperties().put("textures", new Property("textures", bot.value(), bot.signature()));
            TFBotPlayer player = new TFBotPlayer(server, server.overworld(), profile);
            // (LuckPerms carga sus datos al entrar por la red; aquí se le piden antes, para que tenga su grupo)
            TFLuckPerms.loadUser(bot.id(), bot.name());
            server.getPlayerList().placeNewPlayer(new BotConnection(), player);
            player.setInvisible(true);
            player.setInvulnerable(true);
            player.setSilent(true);
            player.setNoGravity(true);
            player.latency = ping;
            return player;
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Bots: no se pudo meter a {} como jugador: va solo en la lista del TAB", bot.name(), t);
            ServerPlayer half = server.getPlayerList().getPlayer(bot.id());
            if (half != null) despawn(half);
            return null;
        }
    }

    /** Lo saca como si se desconectara (con su mensaje de salida). */
    static void despawn(ServerPlayer player) {
        try {
            player.connection.disconnect(Component.translatable("multiplayer.disconnect.quitting"));
        } catch (Throwable t) {
            TFClient.LOGGER.warn("TF Bots: {} no salió bien: {}", player.getGameProfile().getName(), t.toString());
            player.server.getPlayerList().remove(player);
        }
    }

    /** Nadie lo ve: no se manda su entidad a ningún jugador (sí su entrada en la lista del TAB). */
    @Override
    public boolean broadcastToPlayer(ServerPlayer viewer) {
        if (target) return super.broadcastToPlayer(viewer);
        return false;
    }

    /** Para el juego, espectador: ni monstruos, ni dormir, ni empujones. El TAB enseña su modo de juego (supervivencia). */
    @Override
    public boolean isSpectator() {
        if (target) return super.isSpectator();
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (target) return super.isInvulnerableTo(source);
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (target) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        if (target) return super.isPushable();
        return false;
    }

    @Override
    public void tick() {
        try {
            super.tick();
        } catch (Throwable t) {
            // un bot no debe tirar el servidor por nada suyo
        }
    }

    /**
     * La conexión del bot: un canal de Netty de mentira (para que Forge y Mohist vean una conexión abierta) que no manda
     * nada. Lo que se envía se da por enviado: así un /kick lo saca (Minecraft cierra la conexión al «enviar» el aviso).
     */
    private static final class BotConnection extends Connection {
        BotConnection() {
            super(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel();
            for (Field f : Connection.class.getDeclaredFields()) {
                try {
                    if (f.getType() == Channel.class) {
                        f.setAccessible(true);
                        f.set(this, channel);
                    } else if (f.getType() == SocketAddress.class) {
                        f.setAccessible(true);
                        f.set(this, new InetSocketAddress(InetAddress.getLoopbackAddress(), 25565));
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    TFClient.LOGGER.warn("TF Bots: conexión falsa: no se pudo poner {}: {}", f.getName(), e.toString());
                }
            }
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            if (listener != null) listener.onSuccess();
        }
    }
}
