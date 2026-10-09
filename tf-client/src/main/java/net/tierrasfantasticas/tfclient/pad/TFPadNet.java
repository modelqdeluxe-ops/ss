package net.tierrasfantasticas.tfclient.pad;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.server.TFRanks;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;

/**
 * El canal del TF Pad. El cliente pide sus datos (Hello → State: monedas y rango, para la barra de arriba) y abre las
 * apps que viven en el servidor (Open → ViewMsg); cada botón es una Action. El servidor también puede abrir el pad en
 * una app (OpenApp: /tf jobs, /tf shop, /tf claims menu o al pulsar una piedra de protección).
 */
public final class TFPadNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "pad"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    private static boolean registered;

    private TFPadNet() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Hello.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Hello::write).decoder(Hello::read).consumerMainThread(Hello::handle).add();
        CHANNEL.messageBuilder(Open.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Open::write).decoder(Open::read).consumerMainThread(Open::handle).add();
        CHANNEL.messageBuilder(State.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::write).decoder(State::read).consumerMainThread(State::handle).add();
        CHANNEL.messageBuilder(Notice.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Notice::write).decoder(Notice::read).consumerMainThread(Notice::handle).add();
        CHANNEL.messageBuilder(Action.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Action::write).decoder(Action::read).consumerMainThread(Action::handle).add();
        CHANNEL.messageBuilder(ViewMsg.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ViewMsg::write).decoder(ViewMsg::read).consumerMainThread(ViewMsg::handle).add();
        CHANNEL.messageBuilder(Close.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Close::write).decoder(Close::read).consumerMainThread(Close::handle).add();
        CHANNEL.messageBuilder(OpenApp.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenApp::write).decoder(OpenApp::read).consumerMainThread(OpenApp::handle).add();
        PadCommunityNet.register(CHANNEL, 10);
        PadSpeakerNet.register(CHANNEL, 20);
    }

    /** El cliente pide sus datos al abrir el pad. */
    public record Hello() {
        static void write(Hello m, FriendlyByteBuf buf) {}

        static Hello read(FriendlyByteBuf buf) {
            return new Hello();
        }

        static void handle(Hello m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) sendState(player);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Abrir una app del servidor (en una pestaña; vacía = la de entrada). */
    public record Open(String app, String tab) {
        static void write(Open m, FriendlyByteBuf buf) {
            buf.writeUtf(m.app, 32);
            buf.writeUtf(m.tab, 64);
        }

        static Open read(FriendlyByteBuf buf) {
            return new Open(buf.readUtf(32), buf.readUtf(64));
        }

        static void handle(Open m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) PadServer.open(player, m.app, m.tab);
            ctx.get().setPacketHandled(true);
        }
    }

    /** El servidor abre el pad en una app (si el pad está abierto, cambia a ella). */
    public record OpenApp(String app, String tab) {
        static void write(OpenApp m, FriendlyByteBuf buf) {
            buf.writeUtf(m.app, 32);
            buf.writeUtf(m.tab, 64);
        }

        static OpenApp read(FriendlyByteBuf buf) {
            return new OpenApp(buf.readUtf(32), buf.readUtf(64));
        }

        static void handle(OpenApp m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TFPadClient.openTo(m.app, m.tab));
            ctx.get().setPacketHandled(true);
        }
    }

    /** ¿Tiene este jugador el pad? (el TF Client con este canal). */
    public static boolean hasPad(ServerPlayer player) {
        return player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
    }

    /**
     * Abre el pad del jugador en una app. Devuelve false si no tiene el pad (cliente sin TF Client: quien llama usa la
     * ventana de siempre).
     */
    public static boolean openApp(ServerPlayer player, String app, String tab) {
        if (!hasPad(player)) return false;
        player.closeContainer();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenApp(app, tab == null ? "" : tab));
        return true;
    }

    /**
     * Datos del jugador para el pad. balance es -1 si la economía no deja ver el saldo; rank vacío si no tiene rango;
     * homes -1 si no se sabe.
     */
    public record State(long balance, String currency, String rank, int rankColor, int homes, List<String> disabled) {
        static void write(State m, FriendlyByteBuf buf) {
            buf.writeLong(m.balance);
            buf.writeUtf(m.currency, 64);
            buf.writeUtf(m.rank, 64);
            buf.writeInt(m.rankColor);
            buf.writeInt(m.homes);
            buf.writeVarInt(Math.min(64, m.disabled.size()));
            for (int i = 0; i < Math.min(64, m.disabled.size()); i++) buf.writeUtf(m.disabled.get(i), 32);
        }

        static State read(FriendlyByteBuf buf) {
            long balance = buf.readLong();
            String currency = buf.readUtf(64), rank = buf.readUtf(64);
            int color = buf.readInt(), homes = buf.readInt();
            List<String> off = new java.util.ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) off.add(buf.readUtf(32));
            return new State(balance, currency, rank, color, homes, off);
        }

        static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TFPadClient.receive(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Un botón de una app del servidor: app, pestaña abierta, acción y lo que se escribió (si había campo). */
    public record Action(String app, String tab, String action, String text) {
        static void write(Action m, FriendlyByteBuf buf) {
            buf.writeUtf(m.app, 32);
            buf.writeUtf(m.tab, 64);
            buf.writeUtf(m.action, 128);
            buf.writeUtf(m.text, 256);
        }

        static Action read(FriendlyByteBuf buf) {
            return new Action(buf.readUtf(32), buf.readUtf(64), buf.readUtf(128), buf.readUtf(256));
        }

        static void handle(Action m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) PadServer.action(player, m.app, m.tab, m.action, m.text);
            ctx.get().setPacketHandled(true);
        }
    }

    /** La vista de una app del servidor. */
    public record ViewMsg(PadView view) {
        static void write(ViewMsg m, FriendlyByteBuf buf) {
            m.view.write(buf);
        }

        static ViewMsg read(FriendlyByteBuf buf) {
            return new ViewMsg(PadView.read(buf));
        }

        static void handle(ViewMsg m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TFPadClient.view(m.view));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cierra el pad (por ejemplo, al empezar la cuenta atrás de un viaje). */
    public record Close() {
        static void write(Close m, FriendlyByteBuf buf) {}

        static Close read(FriendlyByteBuf buf) {
            return new Close();
        }

        static void handle(Close m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TFPadClient::closePad);
            ctx.get().setPacketHandled(true);
        }
    }

    public static void close(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Close());
    }

    public static void sendView(ServerPlayer player, PadView view) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ViewMsg(view));
    }

    /** Un aviso que se ve dentro del pad (si está cerrado, encima de la barra rápida). */
    public record Notice(String text) {
        static void write(Notice m, FriendlyByteBuf buf) {
            buf.writeUtf(m.text, 256);
        }

        static Notice read(FriendlyByteBuf buf) {
            return new Notice(buf.readUtf(256));
        }

        static void handle(Notice m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TFPadClient.notice(m.text));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void notice(ServerPlayer player, String text) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Notice(text.length() > 250 ? text.substring(0, 250) : text));
    }

    public static void sendState(ServerPlayer player) {
        OptionalLong balance = TFEconomy.balance(player.getServer(), player.getUUID());
        TFRanks.Rank rank = TFRanks.full(TFRanks.of(player.getUUID()));
        State state = new State(balance.isPresent() ? balance.getAsLong() : -1, cut(TFServerConfig.currency()),
                rank == null ? "" : cut(rank.name()), rank == null || rank.hex() < 0 ? 0xFFFFFF : rank.hex(),
                rank == null ? -1 : rank.homes(), PadConfig.disabledApps());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), state);
    }

    private static String cut(String text) {
        return text == null ? "" : text.length() > 60 ? text.substring(0, 60) : text;
    }
}
