package net.tierrasfantasticas.tfclient.pad;

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
 * El altavoz de la app Música: el que pone música con el altavoz encendido avisa al servidor de qué suena (el link de
 * la canción, título, artista, por dónde va y cuánto dura) y el servidor se lo cuenta a los jugadores que tiene cerca
 * (y a los que se acercan; a los que se alejan, que pare). Cada cliente baja la canción del mismo link y la oye
 * situada en el jugador (ver MusicSpeaker y PadSpeakers).
 */
public final class PadSpeakerNet {
    private static SimpleChannel channel;

    private PadSpeakerNet() {}

    static void register(SimpleChannel ch, int base) {
        channel = ch;
        ch.messageBuilder(Up.class, base, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Up::write).decoder(Up::read).consumerMainThread(Up::handle).add();
        ch.messageBuilder(Down.class, base + 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Down::write).decoder(Down::read).consumerMainThread(Down::handle).add();
    }

    public static void toServer(Up m) {
        if (channel != null) channel.sendToServer(m);
    }

    public static void toPlayer(ServerPlayer p, Down m) {
        if (channel != null && TFPadNet.hasPad(p)) channel.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    /** Del que pone la música: lo que suena ahora (playing false = apagó el altavoz o paró). */
    public record Up(boolean playing, String url, String title, String artist, long positionMs, long durationMs) {
        static void write(Up m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.playing);
            buf.writeUtf(m.url, 512);
            buf.writeUtf(m.title, 80);
            buf.writeUtf(m.artist, 80);
            buf.writeVarLong(Math.max(0, m.positionMs));
            buf.writeVarLong(Math.max(0, m.durationMs));
        }

        static Up read(FriendlyByteBuf buf) {
            return new Up(buf.readBoolean(), buf.readUtf(512), buf.readUtf(80), buf.readUtf(80), buf.readVarLong(), buf.readVarLong());
        }

        static void handle(Up m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) net.tierrasfantasticas.tfclient.pad.server.PadSpeakers.update(p, m);
            ctx.get().setPacketHandled(true);
        }
    }

    /** A los de cerca: el altavoz de speaker suena (por positionMs) o se paró. */
    public record Down(UUID speaker, boolean playing, String url, String title, String artist, long positionMs, long durationMs) {
        static void write(Down m, FriendlyByteBuf buf) {
            buf.writeUUID(m.speaker);
            buf.writeBoolean(m.playing);
            buf.writeUtf(m.url, 512);
            buf.writeUtf(m.title, 80);
            buf.writeUtf(m.artist, 80);
            buf.writeVarLong(Math.max(0, m.positionMs));
            buf.writeVarLong(Math.max(0, m.durationMs));
        }

        static Down read(FriendlyByteBuf buf) {
            return new Down(buf.readUUID(), buf.readBoolean(), buf.readUtf(512), buf.readUtf(80), buf.readUtf(80), buf.readVarLong(),
                    buf.readVarLong());
        }

        static void handle(Down m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.tierrasfantasticas.tfclient.pad.music.MusicSpeaker.heard(m));
            ctx.get().setPacketHandled(true);
        }
    }
}
