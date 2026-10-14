package net.tierrasfantasticas.tfclient.bosses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.tierrasfantasticas.tfclient.skills.SkillDefs;

/**
 * Mensajes de los jefes: la ventana /tf boss (el servidor manda la dificultad elegida de cada jefe y las grietas; el
 * staff manda lo que pulsa) y el golpe con un arma de jefe.
 */
public final class BossNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "bosses"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    /** Lo que se puede pulsar en la ventana. */
    public static final int SAVE = 0, PLACE = 1, TEST = 2, RIFT_TP = 3, RIFT_SAVE = 4, RIFT_REMOVE = 5, RIFT_RESET = 6,
            REFRESH = 7, CLEAR_TESTS = 8;

    private BossNet() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Open.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Open::write).decoder(Open::read).consumerMainThread(Open::handle).add();
        CHANNEL.messageBuilder(Action.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Action::write).decoder(Action::read).consumerMainThread(Action::handle).add();
        CHANNEL.messageBuilder(Swing.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Swing::write).decoder(Swing::read).consumerMainThread(Swing::handle).add();
    }

    /** Una grieta tal como la ve la ventana. */
    public record RiftInfo(UUID id, String boss, Difficulty diff, int state, int left, String world, double x, double y, double z,
                           double dist) {}

    /** Abre (o pone al día) la ventana: dificultad elegida de cada jefe, las grietas y un aviso de lo último que se hizo. */
    public record Open(List<String> bosses, List<Difficulty> chosen, List<RiftInfo> rifts, String notice) {
        static void write(Open m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.bosses.size());
            for (int i = 0; i < m.bosses.size(); i++) {
                buf.writeUtf(m.bosses.get(i), 64);
                m.chosen.get(i).write(buf);
            }
            buf.writeVarInt(m.rifts.size());
            for (RiftInfo r : m.rifts) {
                buf.writeUUID(r.id);
                buf.writeUtf(r.boss, 64);
                r.diff.write(buf);
                buf.writeVarInt(r.state);
                buf.writeVarInt(r.left);
                buf.writeUtf(r.world, 128);
                buf.writeDouble(r.x);
                buf.writeDouble(r.y);
                buf.writeDouble(r.z);
                buf.writeDouble(r.dist);
            }
            buf.writeUtf(m.notice == null ? "" : m.notice, 256);
        }

        static Open read(FriendlyByteBuf buf) {
            int n = Math.min(256, buf.readVarInt());
            List<String> bosses = new ArrayList<>();
            List<Difficulty> chosen = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                bosses.add(buf.readUtf(64));
                chosen.add(Difficulty.read(buf));
            }
            int r = Math.min(1024, buf.readVarInt());
            List<RiftInfo> rifts = new ArrayList<>();
            for (int i = 0; i < r; i++) {
                rifts.add(new RiftInfo(buf.readUUID(), buf.readUtf(64), Difficulty.read(buf), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf(128), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
            }
            return new Open(bosses, chosen, rifts, buf.readUtf(256));
        }

        static void handle(Open m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BossClient.open(m));
        }
    }

    /** Lo que pulsa el staff: qué, con qué jefe y dificultad, y sobre qué grieta (si es de grietas). */
    public record Action(int kind, String boss, Difficulty diff, UUID rift) {
        static void write(Action m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.kind);
            buf.writeUtf(m.boss == null ? "" : m.boss, 64);
            (m.diff == null ? Difficulty.NORMAL : m.diff).write(buf);
            buf.writeBoolean(m.rift != null);
            if (m.rift != null) buf.writeUUID(m.rift);
        }

        static Action read(FriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            String boss = buf.readUtf(64);
            Difficulty d = Difficulty.read(buf);
            UUID rift = buf.readBoolean() ? buf.readUUID() : null;
            return new Action(kind, boss, d, rift);
        }

        static void handle(Action m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !p.hasPermissions(2)) return;
            String notice = act(p, m);
            send(p, notice);
        }
    }

    /** Clic izquierdo con un arma de jefe en la mano. */
    public record Swing() {
        static void write(Swing m, FriendlyByteBuf buf) {
        }

        static Swing read(FriendlyByteBuf buf) {
            return new Swing();
        }

        static void handle(Swing m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) BossItems.swing(p);
        }
    }

    // ------------------------------------------------------------------------------------------- servidor

    /** /tf boss: abre la ventana. */
    static void open(ServerPlayer p) {
        send(p, "");
    }

    private static void send(ServerPlayer p, String notice) {
        List<String> ids = new ArrayList<>();
        List<Difficulty> chosen = new ArrayList<>();
        for (String id : SkillDefs.bosses().keySet()) {
            ids.add(id);
            chosen.add(BossConfig.get(id));
        }
        List<RiftInfo> rifts = new ArrayList<>();
        for (Rifts.Rift r : Rifts.list()) {
            ServerLevel level = p.getServer().getLevel(r.dim);
            long now = level == null ? 0 : level.getGameTime();
            double dist = level == p.level() ? Math.sqrt(p.position().distanceToSqr(r.pos)) : -1;
            rifts.add(new RiftInfo(r.id, r.boss, r.diff, r.state, r.left(now), r.dim.location().getPath(), r.pos.x, r.pos.y, r.pos.z, dist));
        }
        // Las de este mundo primero, de la más cercana a la más lejana
        rifts.sort((a, b) -> a.dist < 0 ? (b.dist < 0 ? 0 : 1) : b.dist < 0 ? -1 : Double.compare(a.dist, b.dist));
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Open(ids, chosen, rifts, notice));
    }

    private static String act(ServerPlayer p, Action m) {
        SkillDefs.ClassDef cls = m.boss == null ? null : SkillDefs.bosses().get(m.boss);
        String name = cls != null && cls.bossInfo != null ? cls.bossInfo.name : m.boss;
        Difficulty d = m.diff == null ? Difficulty.NORMAL : m.diff.clamped();
        switch (m.kind) {
            case SAVE -> {
                if (cls == null) return "";
                BossConfig.set(m.boss, d);
                return "Guardada la dificultad de " + name + ": " + d.label();
            }
            case PLACE -> {
                if (cls == null) return "";
                BossConfig.set(m.boss, d);
                Rifts.place(p, m.boss, d);
                return "Grieta de " + name + " (" + d.label() + ") puesta donde estás";
            }
            case TEST -> {
                if (cls == null || !(p.level() instanceof ServerLevel level)) return "";
                BossConfig.set(m.boss, d);
                Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
                float yaw = p.getYRot() + 180F;
                BossEntity b = Rifts.spawnBoss(level, at, m.boss, d, 1, yaw);
                return b == null ? "No se pudo invocar" : "Prueba: " + name + " (" + d.label() + ") delante de ti";
            }
            case CLEAR_TESTS -> {
                int n = 0;
                for (BossEntity b : p.level().getEntitiesOfClass(BossEntity.class, p.getBoundingBox().inflate(160), e -> e.rift() == null)) {
                    b.discard();
                    n++;
                }
                return n == 0 ? "No hay jefes de prueba cerca" : "Quitados " + n + " jefes y esbirros de prueba";
            }
            case RIFT_TP -> {
                return m.rift != null && Rifts.teleport(p, m.rift) ? "Teletransportado a la grieta" : "No existe esa grieta";
            }
            case RIFT_SAVE -> {
                if (cls == null || m.rift == null) return "";
                return Rifts.edit(m.rift, m.boss, d) ? "Grieta guardada: " + name + " · " + d.label() : "No existe esa grieta";
            }
            case RIFT_REMOVE -> {
                return m.rift != null && Rifts.remove(m.rift) ? "Grieta quitada" : "No existe esa grieta";
            }
            case RIFT_RESET -> {
                return m.rift != null && Rifts.reset(m.rift) ? "Grieta lista otra vez" : "No existe esa grieta";
            }
            default -> {
                return "";
            }
        }
    }

    /** Para el cliente: los presets por id (la ventana los enseña con su nombre y color). */
    static Map<String, Difficulty.Preset> presets() {
        Map<String, Difficulty.Preset> out = new java.util.LinkedHashMap<>();
        for (Difficulty.Preset p : Difficulty.PRESETS) out.put(p.id(), p);
        return out;
    }
}
