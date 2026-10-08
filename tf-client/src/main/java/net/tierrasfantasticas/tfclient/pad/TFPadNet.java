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
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimMenuHandler;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.jobs.TFJobsMenu;
import net.tierrasfantasticas.tfclient.market.TFMarketMenu;
import net.tierrasfantasticas.tfclient.server.TFRanks;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig;
import net.tierrasfantasticas.tfclient.shop.TFShopMenu;

/**
 * El canal del TF Pad. El cliente pide sus datos (Hello → State: monedas y rango, para la barra de arriba y las
 * páginas Monedero y Mi rango) y abre las apps que viven en el servidor (Open): Oficios, Protecciones, Tienda y GTS.
 * Abren las mismas ventanas que /tf jobs, /tf claims y /tf shop; el GTS solo se abre desde el pad.
 */
public final class TFPadNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "pad"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    /** Apps que se abren en el servidor. */
    public static final List<String> SERVER_APPS = List.of("oficios", "protecciones", "tienda", "gts");

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

    /** Abrir una app del servidor. */
    public record Open(String app) {
        static void write(Open m, FriendlyByteBuf buf) {
            buf.writeUtf(m.app, 32);
        }

        static Open read(FriendlyByteBuf buf) {
            return new Open(buf.readUtf(32));
        }

        static void handle(Open m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) open(player, m.app);
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Datos del jugador para el pad. balance es -1 si la economía no deja ver el saldo; rank vacío si no tiene rango;
     * homes -1 si no se sabe.
     */
    public record State(long balance, String currency, String rank, int rankColor, int homes) {
        static void write(State m, FriendlyByteBuf buf) {
            buf.writeLong(m.balance);
            buf.writeUtf(m.currency, 64);
            buf.writeUtf(m.rank, 64);
            buf.writeInt(m.rankColor);
            buf.writeInt(m.homes);
        }

        static State read(FriendlyByteBuf buf) {
            return new State(buf.readLong(), buf.readUtf(64), buf.readUtf(64), buf.readInt(), buf.readInt());
        }

        static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TFPadClient.receive(m));
            ctx.get().setPacketHandled(true);
        }
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

    static void notice(ServerPlayer player, String text) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Notice(text));
    }

    public static void sendState(ServerPlayer player) {
        OptionalLong balance = TFEconomy.balance(player.getServer(), player.getUUID());
        TFRanks.Rank rank = TFRanks.of(player.getUUID());
        State state = new State(balance.isPresent() ? balance.getAsLong() : -1, TFServerConfig.currency(),
                rank == null ? "" : rank.name(), rank == null || rank.hex() < 0 ? 0xFFFFFF : rank.hex(),
                rank == null ? -1 : rank.homes());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), state);
    }

    static void open(ServerPlayer player, String app) {
        switch (app) {
            case "oficios" -> TFJobsMenu.openMain(player);
            case "tienda" -> {
                if (!TFShopConfig.enabled && !player.hasPermissions(3)) {
                    notice(player, "La tienda está cerrada ahora mismo. Vuelve a probar más tarde.");
                } else {
                    TFShopMenu.openMain(player);
                }
            }
            case "gts" -> TFMarketMenu.openMain(player);
            case "protecciones" -> openClaims(player);
            default -> {
            }
        }
    }

    /** La zona donde está el jugador si es suya; si no, la primera que tenga. */
    private static void openClaims(ServerPlayer player) {
        ClaimManager claims = ClaimManager.getInstance();
        Claim here = claims.getClaimAt(player.level(), player.blockPosition());
        Claim claim = here != null && (here.isOwner(player) || player.hasPermissions(2)) ? here : null;
        if (claim == null) {
            for (Claim c : claims.getClaimsOf(player.getUUID())) {
                if (c.getGroupId() == null || c.isGroupMother()) {
                    claim = c;
                    break;
                }
            }
        }
        if (claim == null) {
            notice(player, "Aún no tienes zonas protegidas. Pon una protección en el suelo para crear la primera.");
            return;
        }
        ClaimMenuHandler.open(player, claim, 0);
    }
}
