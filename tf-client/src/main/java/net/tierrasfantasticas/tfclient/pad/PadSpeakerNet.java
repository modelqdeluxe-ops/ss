/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
        ch.messageBuilder(Need.class, base + 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Need::write).decoder(Need::read).consumerMainThread(Need::handle).add();
        ch.messageBuilder(UpPart.class, base + 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpPart::write).decoder(UpPart::read).consumerMainThread(UpPart::handle).add();
        ch.messageBuilder(Get.class, base + 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Get::write).decoder(Get::read).consumerMainThread(Get::handle).add();
        ch.messageBuilder(DownPart.class, base + 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DownPart::write).decoder(DownPart::read).consumerMainThread(DownPart::handle).add();
        // (1.3.134) compartir una canción con un disco (SharedDiscItem)
        ch.messageBuilder(Share.class, base + 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Share::write).decoder(Share::read).consumerMainThread(Share::handle).add();
        ch.messageBuilder(Shared.class, base + 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Shared::write).decoder(Shared::read).consumerMainThread(Shared::handle).add();
    }

    /**
     * (1.3.114, el dueño: «el mensaje al prender el parlante dice que las personas no pueden oírme, wtf») las canciones
     * sin link (las de la carpeta de música) se oían solo en tu juego: los demás no tenían de dónde bajarlas. Ahora van
     * por el servidor: su «link» es tfaudio:&lt;sha1 del archivo&gt;; el servidor, si no la tiene, se la pide al que la
     * pone (Need), este la sube a trozos (UpPart) y los de cerca la piden (Get) y la bajan a trozos (DownPart).
     */
    public static final String LOCAL = "tfaudio:";
    /** El tamaño de cada trozo: de subida, lo que cabe en un paquete al servidor; de bajada, más. */
    public static final int UP_PART = 30_000, DOWN_PART = 262_144;
    /** Lo más grande que se reparte (como las que se bajan de un link). */
    public static final int MAX_BYTES = 24 * 1024 * 1024;

    public static void toPlayer(ServerPlayer p, Object m) {
        if (channel != null && TFPadNet.hasPad(p)) channel.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    public static void toServerAny(Object m) {
        if (channel != null) channel.sendToServer(m);
    }

    /** El servidor al que pone la música: súbeme esta canción (no la tengo). */
    public record Need(String hash) {
        static void write(Need m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 64);
        }

        static Need read(FriendlyByteBuf buf) {
            return new Need(buf.readUtf(64));
        }

        static void handle(Need m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.tierrasfantasticas.tfclient.pad.music.MusicSpeaker.upload(m.hash));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Un trozo de la canción que sube el que la pone (index de total). */
    public record UpPart(String hash, int index, int total, int size, byte[] data) {
        static void write(UpPart m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 64);
            buf.writeVarInt(m.index);
            buf.writeVarInt(m.total);
            buf.writeVarInt(m.size);
            buf.writeByteArray(m.data);
        }

        static UpPart read(FriendlyByteBuf buf) {
            return new UpPart(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(UP_PART));
        }

        static void handle(UpPart m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) net.tierrasfantasticas.tfclient.pad.server.PadSpeakers.uploaded(p, m);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Uno de cerca pide la canción de un altavoz (tfaudio:). */
    public record Get(String hash) {
        static void write(Get m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 64);
        }

        static Get read(FriendlyByteBuf buf) {
            return new Get(buf.readUtf(64));
        }

        static void handle(Get m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) net.tierrasfantasticas.tfclient.pad.server.PadSpeakers.requested(p, m.hash);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Un trozo de la canción que baja el que la oye. */
    public record DownPart(String hash, int index, int total, int size, byte[] data) {
        static void write(DownPart m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 64);
            buf.writeVarInt(m.index);
            buf.writeVarInt(m.total);
            buf.writeVarInt(m.size);
            buf.writeByteArray(m.data);
        }

        static DownPart read(FriendlyByteBuf buf) {
            return new DownPart(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(DOWN_PART));
        }

        static void handle(DownPart m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.tierrasfantasticas.tfclient.pad.music.MusicSpeaker.part(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** (1.3.134) El que comparte: hazme el disco de esta canción (link o tfaudio:, título, artista, duración). */
    public record Share(String url, String title, String artist, long durationMs) {
        static void write(Share m, FriendlyByteBuf buf) {
            buf.writeUtf(m.url, 512);
            buf.writeUtf(m.title, 80);
            buf.writeUtf(m.artist, 80);
            buf.writeVarLong(Math.max(0, m.durationMs));
        }

        static Share read(FriendlyByteBuf buf) {
            return new Share(buf.readUtf(512), buf.readUtf(80), buf.readUtf(80), buf.readVarLong());
        }

        static void handle(Share m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) net.tierrasfantasticas.tfclient.pad.server.PadMusicShare.share(p, m);
            ctx.get().setPacketHandled(true);
        }
    }

    /** (1.3.134) Al que gasta el disco: la canción que se guarda en su música (COMPARTIDOS) y quién se la pasó. */
    public record Shared(String url, String title, String artist, long durationMs, String from) {
        static void write(Shared m, FriendlyByteBuf buf) {
            buf.writeUtf(m.url, 512);
            buf.writeUtf(m.title, 80);
            buf.writeUtf(m.artist, 80);
            buf.writeVarLong(Math.max(0, m.durationMs));
            buf.writeUtf(m.from, 40);
        }

        static Shared read(FriendlyByteBuf buf) {
            return new Shared(buf.readUtf(512), buf.readUtf(80), buf.readUtf(80), buf.readVarLong(), buf.readUtf(40));
        }

        static void handle(Shared m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.tierrasfantasticas.tfclient.pad.music.MusicShare.received(m));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void toServer(Up m) {
        if (channel != null) channel.sendToServer(m);
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
