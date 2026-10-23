/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
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
 * Mensajes de Comunidad (van por el canal del pad). Las fotos viajan en trozos: del cliente al servidor como mucho
 * {@link #UP_CHUNK} bytes por paquete (el juego no deja más de 32 KB), y del servidor al cliente {@link #DOWN_CHUNK}.
 */
public final class PadCommunityNet {
    static final int UP_CHUNK = 28_000;
    static final int DOWN_CHUNK = 60_000;
    static final int MAX_PHOTO = 1_800_000;

    private static SimpleChannel channel;

    private PadCommunityNet() {}

    static void register(SimpleChannel ch, int base) {
        channel = ch;
        ch.messageBuilder(Upload.class, base, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Upload::write).decoder(Upload::read).consumerMainThread(Upload::handle).add();
        ch.messageBuilder(FeedReq.class, base + 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FeedReq::write).decoder(FeedReq::read).consumerMainThread(FeedReq::handle).add();
        ch.messageBuilder(Feed.class, base + 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Feed::write).decoder(Feed::read).consumerMainThread(Feed::handle).add();
        ch.messageBuilder(Act.class, base + 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Act::write).decoder(Act::read).consumerMainThread(Act::handle).add();
        ch.messageBuilder(ImgReq.class, base + 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ImgReq::write).decoder(ImgReq::read).consumerMainThread(ImgReq::handle).add();
        ch.messageBuilder(Img.class, base + 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Img::write).decoder(Img::read).consumerMainThread(Img::handle).add();
        ch.messageBuilder(Result.class, base + 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Result::write).decoder(Result::read).consumerMainThread(Result::handle).add();
    }

    static void toServer(Object msg) {
        channel.sendToServer(msg);
    }

    static void toPlayer(ServerPlayer player, Object msg) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** Un trozo de una foto que se publica (el primero lleva el texto). */
    public record Upload(int upload, int index, int total, String caption, byte[] data) {
        static void write(Upload m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.upload);
            buf.writeVarInt(m.index);
            buf.writeVarInt(m.total);
            buf.writeUtf(m.caption, 140);
            buf.writeByteArray(m.data);
        }

        static Upload read(FriendlyByteBuf buf) {
            return new Upload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(140), buf.readByteArray(UP_CHUNK + 64));
        }

        static void handle(Upload m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) PadCommunityServer.upload(p, m);
            ctx.get().setPacketHandled(true);
        }
    }

    public record FeedReq(String tab, int page) {
        static void write(FeedReq m, FriendlyByteBuf buf) {
            buf.writeUtf(m.tab, 16);
            buf.writeVarInt(m.page);
        }

        static FeedReq read(FriendlyByteBuf buf) {
            return new FeedReq(buf.readUtf(16), buf.readVarInt());
        }

        static void handle(FeedReq m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) PadCommunityServer.requestFeed(p, m.tab, m.page);
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Una foto de la lista tal como la ve un jugador. reactions: las reacciones con emoji que tiene (en el orden en que
     * se pusieron, como en Discord), cada una con cuántos la pusieron y si este jugador es uno de ellos.
     */
    public record Post(String id, UUID author, String name, String caption, long time, int likes, boolean liked,
                       boolean mine, boolean canDelete, List<Reaction> reactions) {}

    /** Una reacción de una foto: el emoji (id de {@link PadEmojis}), cuántos la pusieron y si tú la pusiste. */
    public record Reaction(String emoji, int count, boolean mine) {}

    /** Emojis distintos por foto como mucho (como en Discord: 20). */
    public static final int MAX_REACTIONS = 20;
    /** Lo más largo de una acción («react:» + el id del emoji). */
    static final int ACTION_MAX = 8 + PadEmojis.ID_MAX;

    public record Feed(String tab, int page, int pages, int total, List<Post> posts) {
        static void write(Feed m, FriendlyByteBuf buf) {
            buf.writeUtf(m.tab, 16);
            buf.writeVarInt(m.page);
            buf.writeVarInt(m.pages);
            buf.writeVarInt(m.total);
            buf.writeVarInt(m.posts.size());
            for (Post p : m.posts) {
                buf.writeUtf(p.id, 32);
                buf.writeUUID(p.author);
                buf.writeUtf(p.name, 32);
                buf.writeUtf(p.caption, 140);
                buf.writeLong(p.time);
                buf.writeVarInt(p.likes);
                buf.writeBoolean(p.liked);
                buf.writeBoolean(p.mine);
                buf.writeBoolean(p.canDelete);
                int n = Math.min(MAX_REACTIONS, p.reactions.size());
                buf.writeVarInt(n);
                for (int r = 0; r < n; r++) {
                    Reaction re = p.reactions.get(r);
                    buf.writeUtf(re.emoji, PadEmojis.ID_MAX);
                    buf.writeVarInt(re.count);
                    buf.writeBoolean(re.mine);
                }
            }
        }

        static Feed read(FriendlyByteBuf buf) {
            String tab = buf.readUtf(16);
            int page = buf.readVarInt(), pages = buf.readVarInt(), total = buf.readVarInt();
            List<Post> posts = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) {
                String id = buf.readUtf(32);
                UUID author = buf.readUUID();
                String name = buf.readUtf(32), caption = buf.readUtf(140);
                long time = buf.readLong();
                int likes = buf.readVarInt();
                boolean liked = buf.readBoolean(), mine = buf.readBoolean(), canDelete = buf.readBoolean();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_REACTIONS) throw new IllegalArgumentException("Demasiadas reacciones: " + n);
                List<Reaction> reactions = new ArrayList<>(n);
                for (int r = 0; r < n; r++) reactions.add(new Reaction(buf.readUtf(PadEmojis.ID_MAX), buf.readVarInt(), buf.readBoolean()));
                posts.add(new Post(id, author, name, caption, time, likes, liked, mine, canDelete, List.copyOf(reactions)));
            }
            return new Feed(tab, page, pages, total, posts);
        }

        static void handle(Feed m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PadCommunityClient.feed(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * like, react:&lt;emoji&gt; (pone o quita esa reacción), borrar o denunciar una publicación (y en qué pestaña/página está
     * el jugador, para mandarle la lista).
     */
    public record Act(String action, String id, String tab, int page) {
        static void write(Act m, FriendlyByteBuf buf) {
            buf.writeUtf(m.action, ACTION_MAX);
            buf.writeUtf(m.id, 32);
            buf.writeUtf(m.tab, 16);
            buf.writeVarInt(m.page);
        }

        static Act read(FriendlyByteBuf buf) {
            return new Act(buf.readUtf(ACTION_MAX), buf.readUtf(32), buf.readUtf(16), buf.readVarInt());
        }

        static void handle(Act m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) PadCommunityServer.act(p, m);
            ctx.get().setPacketHandled(true);
        }
    }

    public record ImgReq(String id) {
        static void write(ImgReq m, FriendlyByteBuf buf) {
            buf.writeUtf(m.id, 32);
        }

        static ImgReq read(FriendlyByteBuf buf) {
            return new ImgReq(buf.readUtf(32));
        }

        static void handle(ImgReq m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) PadCommunityServer.sendImage(p, m.id);
            ctx.get().setPacketHandled(true);
        }
    }

    public record Img(String id, int index, int total, byte[] data) {
        static void write(Img m, FriendlyByteBuf buf) {
            buf.writeUtf(m.id, 32);
            buf.writeVarInt(m.index);
            buf.writeVarInt(m.total);
            buf.writeByteArray(m.data);
        }

        static Img read(FriendlyByteBuf buf) {
            return new Img(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(DOWN_CHUNK + 64));
        }

        static void handle(Img m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PadCommunityClient.image(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cómo fue la publicación. */
    public record Result(boolean ok, String message) {
        static void write(Result m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.ok);
            buf.writeUtf(m.message, 200);
        }

        static Result read(FriendlyByteBuf buf) {
            return new Result(buf.readBoolean(), buf.readUtf(200));
        }

        static void handle(Result m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PadCommunityClient.result(m));
            ctx.get().setPacketHandled(true);
        }
    }
}
