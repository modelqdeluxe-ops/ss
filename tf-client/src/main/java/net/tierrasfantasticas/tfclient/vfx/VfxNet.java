package net.tierrasfantasticas.tfclient.vfx;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;

/** Mensajes del servidor a los jugadores para los VFX: lanzar un efecto y el estado del indicador (equipado y cooldowns). */
public final class VfxNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "vfx"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    /** Distancia a la que los demás ven los efectos. */
    public static final double RANGE = 96.0;
    private static boolean registered;

    private VfxNet() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Play.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Play::write).decoder(Play::read).consumerMainThread(Play::handle).add();
        CHANNEL.messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::write).decoder(State::read).consumerMainThread(State::handle).add();
    }

    /**
     * Lanza un efecto (fx.json) en una posición. caster/victim: ids de entidad (-1 si no hay); skin: jugador cuya
     * cabeza se pone en los huesos phead (la víctima en los efectos de kill, quien lanza en las skills).
     */
    public record Play(String fx, double x, double y, double z, float yaw, float pitch, int caster, int victim,
                       UUID skin, int seed) {
        static void write(Play m, FriendlyByteBuf buf) {
            buf.writeUtf(m.fx, 64);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeFloat(m.yaw);
            buf.writeFloat(m.pitch);
            buf.writeVarInt(m.caster + 1);
            buf.writeVarInt(m.victim + 1);
            buf.writeBoolean(m.skin != null);
            if (m.skin != null) buf.writeUUID(m.skin);
            buf.writeInt(m.seed);
        }

        static Play read(FriendlyByteBuf buf) {
            String fx = buf.readUtf(64);
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            float yaw = buf.readFloat(), pitch = buf.readFloat();
            int caster = buf.readVarInt() - 1, victim = buf.readVarInt() - 1;
            UUID skin = buf.readBoolean() ? buf.readUUID() : null;
            return new Play(fx, x, y, z, yaw, pitch, caster, victim, skin, buf.readInt());
        }

        static void handle(Play m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VfxClient.play(m));
        }
    }

    /**
     * Lo que lleva equipado el jugador (para el indicador junto a la barra): efecto de kill, paquete de skills y el
     * cooldown que le queda a cada skill del paquete (en ticks).
     */
    public record State(String kill, String pack, int[] cooldowns, int[] totals) {
        static void write(State m, FriendlyByteBuf buf) {
            buf.writeUtf(m.kill == null ? "" : m.kill, 64);
            buf.writeUtf(m.pack == null ? "" : m.pack, 64);
            buf.writeVarIntArray(m.cooldowns);
            buf.writeVarIntArray(m.totals);
        }

        static State read(FriendlyByteBuf buf) {
            String kill = buf.readUtf(64);
            String pack = buf.readUtf(64);
            return new State(kill.isEmpty() ? null : kill, pack.isEmpty() ? null : pack, buf.readVarIntArray(16),
                    buf.readVarIntArray(16));
        }

        static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VfxClient.state(m));
        }
    }

    /** Manda el efecto a todos los que estén cerca (y en la misma dimensión). */
    public static void broadcast(ServerLevel level, Play msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(msg.x(), msg.y(), msg.z(), RANGE,
                level.dimension())), msg);
    }

    public static void send(ServerPlayer player, State msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
