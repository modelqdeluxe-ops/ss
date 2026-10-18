package net.tierrasfantasticas.tfclient.vacio;

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

/** Mensajes del Void: el diálogo del caballero, lo que eliges y la animación del viaje. */
public final class VoidNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "vacio"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    /** Lo que se elige en el diálogo. */
    public static final int GO = 0, BACK = 1, BYE = 2;
    /** Fases de la animación: sale (la luz sube hasta el blanco) y llega (el blanco se va). */
    public static final int FX_LEAVE = 0, FX_ARRIVE = 1, FX_CANCEL = 2;

    private VoidNet() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Talk.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Talk::write).decoder(Talk::read).consumerMainThread(Talk::handle).add();
        CHANNEL.messageBuilder(Choose.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Choose::write).decoder(Choose::read).consumerMainThread(Choose::handle).add();
        CHANNEL.messageBuilder(Fx.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Fx::write).decoder(Fx::read).consumerMainThread(Fx::handle).add();
    }

    static void fx(ServerPlayer p, int phase) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Fx(phase, phase == FX_LEAVE ? VoidTravel.LEAVE_TICKS : 50));
    }

    /**
     * Abre el diálogo: con quién (entidad), tu nombre, si es la primera vez que hablas con él, si es el del santuario
     * (devuelve) y, entonces, a dónde volverías.
     */
    public record Talk(int entity, String name, boolean first, boolean home, String back) {
        static void write(Talk m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.name, 64);
            buf.writeBoolean(m.first);
            buf.writeBoolean(m.home);
            buf.writeUtf(m.back, 128);
        }

        static Talk read(FriendlyByteBuf buf) {
            return new Talk(buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(), buf.readUtf(128));
        }

        static void handle(Talk m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VoidClient.talk(m));
        }
    }

    public record Choose(int entity, int what) {
        static void write(Choose m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeVarInt(m.what);
        }

        static Choose read(FriendlyByteBuf buf) {
            return new Choose(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(Choose m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) VoidTravel.choose(p, m.entity, m.what);
        }
    }

    public record Fx(int phase, int ticks) {
        static void write(Fx m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.phase);
            buf.writeVarInt(m.ticks);
        }

        static Fx read(FriendlyByteBuf buf) {
            return new Fx(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(Fx m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TravelFx.start(m.phase, m.ticks));
        }
    }
}
