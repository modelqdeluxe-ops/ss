/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * (1.3.159) Los mensajes de los clanes, por el canal del pad (ids desde 30): cómo se ve cada clan (ClanLooks), pedir el
 * emote del clan (la tecla) y que se vea (a los de alrededor y a uno mismo).
 */
public final class ClanNet {
    private static SimpleChannel channel;

    private ClanNet() {}

    public static void register(SimpleChannel ch, int first) {
        channel = ch;
        ch.messageBuilder(Looks.class, first, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Looks::write).decoder(Looks::read).consumerMainThread(Looks::handle).add();
        ch.messageBuilder(EmoteAsk.class, first + 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EmoteAsk::write).decoder(EmoteAsk::read).consumerMainThread(EmoteAsk::handle).add();
        ch.messageBuilder(EmotePlay.class, first + 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EmotePlay::write).decoder(EmotePlay::read).consumerMainThread(EmotePlay::handle).add();
    }

    /** A uno (to) o a todos (null). */
    static void sendLooks(ServerPlayer to, String json) {
        if (channel == null) return;
        if (to == null) channel.send(PacketDistributor.ALL.noArg(), new Looks(json));
        else channel.send(PacketDistributor.PLAYER.with(() -> to), new Looks(json));
    }

    /** El emote de ese jugador, a él y a los que lo ven. */
    static void sendEmote(ServerPlayer who, int emote) {
        if (channel != null) channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> who), new EmotePlay(who.getUUID(), who.getId(), emote));
    }

    /** Desde el cliente: el emote elegido en la rueda (0 a 4, ClanEmote.IDS). */
    public static void askEmote(int emote) {
        if (channel != null) channel.sendToServer(new EmoteAsk(emote));
    }

    public record Looks(String json) {
        static void write(Looks m, FriendlyByteBuf buf) {
            buf.writeUtf(m.json, 1 << 20);
        }

        static Looks read(FriendlyByteBuf buf) {
            return new Looks(buf.readUtf(1 << 20));
        }

        static void handle(Looks m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClanLooks.apply(m.json));
            ctx.get().setPacketHandled(true);
        }
    }

    public record EmoteAsk(int emote) {
        static void write(EmoteAsk m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.emote);
        }

        static EmoteAsk read(FriendlyByteBuf buf) {
            return new EmoteAsk(buf.readVarInt());
        }

        static void handle(EmoteAsk m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ClanEmote.ask(p, m.emote);
            ctx.get().setPacketHandled(true);
        }
    }

    public record EmotePlay(UUID uuid, int entity, int emote) {
        static void write(EmotePlay m, FriendlyByteBuf buf) {
            buf.writeUUID(m.uuid);
            buf.writeVarInt(m.entity);
            buf.writeVarInt(m.emote);
        }

        static EmotePlay read(FriendlyByteBuf buf) {
            return new EmotePlay(buf.readUUID(), buf.readVarInt(), buf.readVarInt());
        }

        static void handle(EmotePlay m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.tierrasfantasticas.tfclient.clans.client.ClanEmoteClient.start(m.uuid, m.emote));
            ctx.get().setPacketHandled(true);
        }
    }
}
