package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Mensajes de las skills de clase. El servidor lo decide todo (daño, empujes, efectos que aparecen y se mueven) y
 * manda a los jugadores cercanos lo que tienen que ver: los efectos (actores con su modelo), partículas y sonidos.
 * El jugador solo manda qué tecla de skill ha pulsado.
 */
public final class SkillNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "skills"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    /** Distancia a la que se ven los efectos de los demás. */
    public static final double RANGE = 80.0;
    private static boolean registered;

    private SkillNet() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(Cast.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Cast::write).decoder(Cast::read).consumerMainThread(Cast::handle).add();
        CHANNEL.messageBuilder(State.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::write).decoder(State::read).consumerMainThread(State::handle).add();
        CHANNEL.messageBuilder(Spawn.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Spawn::write).decoder(Spawn::read).consumerMainThread(Spawn::handle).add();
        CHANNEL.messageBuilder(Moves.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Moves::write).decoder(Moves::read).consumerMainThread(Moves::handle).add();
        CHANNEL.messageBuilder(Prop.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Prop::write).decoder(Prop::read).consumerMainThread(Prop::handle).add();
        CHANNEL.messageBuilder(Fx.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Fx::write).decoder(Fx::read).consumerMainThread(Fx::handle).add();
    }

    // ------------------------------------------------------------------------------------------- jugador → servidor

    /** Ha pulsado la tecla de la skill número «slot» de la barra (0 = la primera). */
    public record Cast(int slot) {
        static void write(Cast m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.slot);
        }

        static Cast read(FriendlyByteBuf buf) {
            return new Cast(buf.readVarInt());
        }

        static void handle(Cast m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) SkillServer.castSlot(player, m.slot);
        }
    }

    // ------------------------------------------------------------------------------------------- servidor → jugador

    /** La clase del jugador ("" = ninguna) y lo que le queda de cooldown a cada skill de la barra (ticks). */
    public record State(String classId, int[] left, int[] total) {
        static void write(State m, FriendlyByteBuf buf) {
            buf.writeUtf(m.classId, 64);
            buf.writeVarIntArray(m.left);
            buf.writeVarIntArray(m.total);
        }

        static State read(FriendlyByteBuf buf) {
            return new State(buf.readUtf(64), buf.readVarIntArray(32), buf.readVarIntArray(32));
        }

        static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkillClient.state(m));
        }
    }

    /**
     * Aparece un efecto (actor): posición, giro, lo que lleva en la cabeza (modelo de ítem), su modelo de ModelEngine,
     * si es pequeño (soporte small) y a qué entidad va pegado (-1 = a ninguna). hideHost: tapa a esa entidad (el jugador
     * convertido en el modelo, como el traje del Dragón Rojo).
     */
    public record Spawn(int id, double x, double y, double z, float yaw, float pitch, String head, String me, boolean small,
                        int follow, boolean hideHost, float headPitch, String hand, String anim) {
        static void write(Spawn m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.id);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeFloat(m.yaw);
            buf.writeFloat(m.pitch);
            buf.writeUtf(m.head == null ? "" : m.head, 256);
            buf.writeUtf(m.me == null ? "" : m.me, 128);
            buf.writeBoolean(m.small);
            buf.writeVarInt(m.follow + 1);
            buf.writeBoolean(m.hideHost);
            buf.writeFloat(m.headPitch);
            buf.writeUtf(m.hand == null ? "" : m.hand, 256);
            buf.writeUtf(m.anim == null ? "" : m.anim, 128);
        }

        static Spawn read(FriendlyByteBuf buf) {
            return new Spawn(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                    buf.readFloat(), emptyNull(buf.readUtf(256)), emptyNull(buf.readUtf(128)), buf.readBoolean(),
                    buf.readVarInt() - 1, buf.readBoolean(), buf.readFloat(), emptyNull(buf.readUtf(256)), emptyNull(buf.readUtf(128)));
        }

        static void handle(Spawn m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkillClient.spawn(m));
        }
    }

    /** Dónde están ahora los actores que se han movido este tick: id, x, y, z, giro e inclinación. */
    public record Moves(int[] ids, double[] pos, float[] rot) {
        static void write(Moves m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.ids.length);
            for (int i = 0; i < m.ids.length; i++) {
                buf.writeVarInt(m.ids[i]);
                buf.writeDouble(m.pos[i * 3]);
                buf.writeDouble(m.pos[i * 3 + 1]);
                buf.writeDouble(m.pos[i * 3 + 2]);
                buf.writeFloat(m.rot[i * 2]);
                buf.writeFloat(m.rot[i * 2 + 1]);
            }
        }

        static Moves read(FriendlyByteBuf buf) {
            int n = Math.min(4096, buf.readVarInt());
            int[] ids = new int[n];
            double[] pos = new double[n * 3];
            float[] rot = new float[n * 2];
            for (int i = 0; i < n; i++) {
                ids[i] = buf.readVarInt();
                pos[i * 3] = buf.readDouble();
                pos[i * 3 + 1] = buf.readDouble();
                pos[i * 3 + 2] = buf.readDouble();
                rot[i * 2] = buf.readFloat();
                rot[i * 2 + 1] = buf.readFloat();
            }
            return new Moves(ids, pos, rot);
        }

        static void handle(Moves m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkillClient.moves(m));
        }
    }

    /**
     * Cambia algo de un actor: «head» (modelo en la cabeza), «me» (modelo de ModelEngine), «state» (animación:
     * nombre y velocidad), «stop» (deja una animación), «part» (hueso → hueso de otro modelo), «vis» (enseña u
     * oculta un hueso), «tint» (color), «remove» (desaparece).
     */
    public record Prop(int id, String key, String a, String b, String c) {
        static void write(Prop m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.id);
            buf.writeUtf(m.key, 16);
            buf.writeUtf(m.a == null ? "" : m.a, 256);
            buf.writeUtf(m.b == null ? "" : m.b, 256);
            buf.writeUtf(m.c == null ? "" : m.c, 256);
        }

        static Prop read(FriendlyByteBuf buf) {
            return new Prop(buf.readVarInt(), buf.readUtf(16), buf.readUtf(256), buf.readUtf(256), buf.readUtf(256));
        }

        static void handle(Prop m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkillClient.prop(m));
        }
    }

    /**
     * Partículas o un sonido. kind 0: partículas en un punto (n, desvío horizontal y vertical, velocidad); 1: esfera
     * (radio); 2: anillo (radio, puntos); 3: línea hasta (x2, y2, z2) cada «radio» bloques; 9: sonido (id, volumen,
     * tono). extra: color «#rrggbb» o bloque para las de bloque.
     */
    public record Fx(int kind, String id, double x, double y, double z, int n, float hs, float vs, float speed,
                     float radius, int points, String extra, float size, double x2, double y2, double z2) {
        static void write(Fx m, FriendlyByteBuf buf) {
            buf.writeByte(m.kind);
            buf.writeUtf(m.id, 128);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeVarInt(m.n);
            buf.writeFloat(m.hs);
            buf.writeFloat(m.vs);
            buf.writeFloat(m.speed);
            buf.writeFloat(m.radius);
            buf.writeVarInt(m.points);
            buf.writeUtf(m.extra == null ? "" : m.extra, 64);
            buf.writeFloat(m.size);
            if (m.kind == 3) {
                buf.writeDouble(m.x2);
                buf.writeDouble(m.y2);
                buf.writeDouble(m.z2);
            }
        }

        static Fx read(FriendlyByteBuf buf) {
            int kind = buf.readByte();
            String id = buf.readUtf(128);
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            int n = buf.readVarInt();
            float hs = buf.readFloat(), vs = buf.readFloat(), speed = buf.readFloat(), radius = buf.readFloat();
            int points = buf.readVarInt();
            String extra = buf.readUtf(64);
            float size = buf.readFloat();
            double x2 = 0, y2 = 0, z2 = 0;
            if (kind == 3) {
                x2 = buf.readDouble();
                y2 = buf.readDouble();
                z2 = buf.readDouble();
            }
            return new Fx(kind, id, x, y, z, n, hs, vs, speed, radius, points, extra, size, x2, y2, z2);
        }

        static void handle(Fx m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkillClient.fx(m));
        }

        public static Fx sound(String id, Vec3 at, float volume, float pitch) {
            return new Fx(9, id, at.x, at.y, at.z, 0, volume, pitch, 0, 0, 0, null, 0, 0, 0, 0);
        }
    }

    private static String emptyNull(String s) {
        return s.isEmpty() ? null : s;
    }

    // ------------------------------------------------------------------------------------------- envío

    static void near(ServerLevel level, Vec3 at, Object msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(at.x, at.y, at.z, RANGE, level.dimension())), msg);
    }

    static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** Las posiciones de los actores que se movieron, en mensajes de como mucho 256. */
    static void sendMoves(ServerLevel level, List<SkillActor> moved) {
        for (int start = 0; start < moved.size(); start += 256) {
            List<SkillActor> part = new ArrayList<>(moved.subList(start, Math.min(moved.size(), start + 256)));
            int[] ids = new int[part.size()];
            double[] pos = new double[part.size() * 3];
            float[] rot = new float[part.size() * 2];
            double cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < part.size(); i++) {
                SkillActor a = part.get(i);
                ids[i] = a.id;
                pos[i * 3] = a.pos.x;
                pos[i * 3 + 1] = a.pos.y;
                pos[i * 3 + 2] = a.pos.z;
                rot[i * 2] = a.yaw;
                rot[i * 2 + 1] = a.pitch;
                cx += a.pos.x;
                cy += a.pos.y;
                cz += a.pos.z;
            }
            int n = part.size();
            // Se manda a quien esté cerca del grupo (los actores de una skill van juntos)
            near(level, new Vec3(cx / n, cy / n, cz / n), new Moves(ids, pos, rot));
        }
    }
}
