package net.tierrasfantasticas.tfclient.npcs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

/**
 * Mensajes del panel de NPC (1.3.101): el staff manda lo que pulsa ({@link Act}) y el servidor le contesta con cómo está
 * todo ({@link State}: el NPC elegido, sus cosméticos, los pins, las rutas y los NPC de alrededor).
 */
public final class NpcNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "npcs"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    /** Lo que se puede pulsar en el panel (ver TFNpcs.act). */
    public static final int REFRESH = 0, PLACE = 1, SELECT = 2, SELECT_NEAR = 3, MOVE = 4, MOVE_TO = 5, ROTATE = 6, FACE_ME = 7,
            OPTION = 8, RESET = 9, DELETE = 10, DUPLICATE = 11, PIN_SET = 12, PIN_CLEAR = 13, ROUTE_ADD = 14, ROUTE_UNDO = 15,
            ROUTE_CLEAR = 16, ROUTE_ASSIGN = 17, DESELECT = 18;

    private NpcNet() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(State.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::write).decoder(State::read).consumerMainThread(State::handle).add();
        CHANNEL.messageBuilder(Act.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Act::write).decoder(Act::read).consumerMainThread(Act::handle).add();
    }

    static void send(ServerPlayer p, State s) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s);
    }

    public static void toServer(Act a) {
        CHANNEL.sendToServer(a);
    }

    /** Un pin tal como lo ve el panel (here = está en el mundo del jugador). */
    public record PinInfo(String name, double x, double y, double z, boolean here) {}

    /** Un NPC de alrededor. */
    public record Near(int id, String pack, String mob, double dist) {}

    /**
     * Cómo está todo para ese jugador: el NPC elegido (id -1 = ninguno), dónde está y hacia dónde mira, qué opción de
     * cada grupo lleva (grupo → id de la opción), los pins, cuántos puntos tiene cada ruta (1..6), los NPC de alrededor,
     * un aviso y si hay que abrir el panel.
     */
    public record State(int id, String pack, String mob, double x, double y, double z, float yaw, Map<String, String> chosen,
                        List<PinInfo> pins, int[] routes, List<Near> near, String notice, boolean open) {
        static void write(State m, FriendlyByteBuf buf) {
            buf.writeInt(m.id);
            buf.writeUtf(m.pack, 64);
            buf.writeUtf(m.mob, 128);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeFloat(m.yaw);
            buf.writeVarInt(m.chosen.size());
            m.chosen.forEach((k, v) -> {
                buf.writeUtf(k, 32);
                buf.writeUtf(v, 128);
            });
            buf.writeVarInt(m.pins.size());
            for (PinInfo p : m.pins) {
                buf.writeUtf(p.name, 128);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeBoolean(p.here);
            }
            buf.writeVarIntArray(m.routes);
            buf.writeVarInt(m.near.size());
            for (Near n : m.near) {
                buf.writeInt(n.id);
                buf.writeUtf(n.pack, 64);
                buf.writeUtf(n.mob, 128);
                buf.writeDouble(n.dist);
            }
            buf.writeUtf(m.notice == null ? "" : m.notice, 256);
            buf.writeBoolean(m.open);
        }

        static State read(FriendlyByteBuf buf) {
            int id = buf.readInt();
            String pack = buf.readUtf(64), mob = buf.readUtf(128);
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            float yaw = buf.readFloat();
            Map<String, String> chosen = new LinkedHashMap<>();
            int c = Math.min(32, buf.readVarInt());
            for (int i = 0; i < c; i++) chosen.put(buf.readUtf(32), buf.readUtf(128));
            List<PinInfo> pins = new ArrayList<>();
            int np = Math.min(2048, buf.readVarInt());
            for (int i = 0; i < np; i++) pins.add(new PinInfo(buf.readUtf(128), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));
            int[] routes = buf.readVarIntArray(64);
            List<Near> near = new ArrayList<>();
            int nn = Math.min(256, buf.readVarInt());
            for (int i = 0; i < nn; i++) near.add(new Near(buf.readInt(), buf.readUtf(64), buf.readUtf(128), buf.readDouble()));
            return new State(id, pack, mob, x, y, z, yaw, chosen, pins, routes, near, buf.readUtf(256), buf.readBoolean());
        }

        static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.tierrasfantasticas.tfclient.npcs.client.NpcPanelScreen.receive(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Lo que pulsa el staff: qué (op), sobre qué NPC (id de entidad), dos textos y tres números (según la op). */
    public record Act(int op, int id, String a, String b, double x, double y, double z) {
        public static Act of(int op) {
            return new Act(op, -1, "", "", 0, 0, 0);
        }

        static void write(Act m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.op);
            buf.writeInt(m.id);
            buf.writeUtf(m.a == null ? "" : m.a, 128);
            buf.writeUtf(m.b == null ? "" : m.b, 128);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
        }

        static Act read(FriendlyByteBuf buf) {
            return new Act(buf.readVarInt(), buf.readInt(), buf.readUtf(128), buf.readUtf(128), buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        static void handle(Act m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) TFNpcs.act(p, m);
            ctx.get().setPacketHandled(true);
        }
    }
}
