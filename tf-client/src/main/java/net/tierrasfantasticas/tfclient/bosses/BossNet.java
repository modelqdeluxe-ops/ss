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

    /** Lo que se puede pulsar en la ventana (VOID_*: la pestaña de la grieta del Void; arg = tipo o tipo×1024+fila). */
    public static final int SAVE = 0, PLACE = 1, TEST = 2, RIFT_TP = 3, RIFT_SAVE = 4, RIFT_REMOVE = 5, RIFT_RESET = 6,
            REFRESH = 7, CLEAR_TESTS = 8, VOID_ACTIVE = 9, VOID_ADD = 10, VOID_REMOVE = 11, VOID_TP = 12, VOID_RESET = 13,
    // (1.3.96) la secuencia de la grieta del Void: subir, bajar, editar, los mismos atributos para todos, en orden o al
    // azar (arg 1/0), al acabar (arg = Rifts.END_*) y el siguiente
            VOID_UP = 14, VOID_DOWN = 15, VOID_EDIT = 16, VOID_ALL = 17, VOID_MODE = 18, VOID_END = 19, VOID_NEXT = 20;

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

    /**
     * La grieta del Void tal como la ve la ventana: si existe (el mundo tiene el Void), cuál está en el centro, sus dos
     * listas y cómo está (jefe que espera, estado, lo que falta).
     */
    public record VoidInfo(boolean exists, int active, List<List<Rifts.Entry>> pools, String boss, Difficulty diff, int state, int left,
                           boolean sequence, int endMode, int current, int next) {
        static final VoidInfo NONE = new VoidInfo(false, 0, List.of(List.of(), List.of()), "", Difficulty.NORMAL, 0, 0, true, 0, -1, 0);

        void write(FriendlyByteBuf buf) {
            buf.writeBoolean(exists);
            buf.writeVarInt(active);
            for (int k = 0; k < 2; k++) {
                List<Rifts.Entry> pool = pools.get(k);
                buf.writeVarInt(pool.size());
                for (Rifts.Entry e : pool) {
                    buf.writeUtf(e.boss(), 64);
                    e.diff().write(buf);
                }
            }
            buf.writeUtf(boss, 64);
            diff.write(buf);
            buf.writeVarInt(state);
            buf.writeVarInt(left);
            buf.writeBoolean(sequence);
            buf.writeVarInt(endMode);
            buf.writeVarInt(current + 1);
            buf.writeVarInt(next);
        }

        static VoidInfo read(FriendlyByteBuf buf) {
            boolean exists = buf.readBoolean();
            int active = buf.readVarInt();
            List<List<Rifts.Entry>> pools = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                int n = Math.min(64, buf.readVarInt());
                List<Rifts.Entry> pool = new ArrayList<>();
                for (int i = 0; i < n; i++) pool.add(new Rifts.Entry(buf.readUtf(64), Difficulty.read(buf)));
                pools.add(pool);
            }
            return new VoidInfo(exists, active, pools, buf.readUtf(64), Difficulty.read(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt());
        }
    }

    /** Abre (o pone al día) la ventana: dificultad elegida de cada jefe, las grietas y un aviso de lo último que se hizo. */
    public record Open(List<String> bosses, List<Difficulty> chosen, List<RiftInfo> rifts, VoidInfo voidRift, String notice) {
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
            m.voidRift.write(buf);
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
            VoidInfo v = VoidInfo.read(buf);
            return new Open(bosses, chosen, rifts, v, buf.readUtf(256));
        }

        static void handle(Open m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BossClient.open(m));
        }
    }

    /** Lo que pulsa el staff: qué, con qué jefe y dificultad, sobre qué grieta (si es de grietas) y un número más. */
    public record Action(int kind, String boss, Difficulty diff, UUID rift, int arg) {
        public Action(int kind, String boss, Difficulty diff, UUID rift) {
            this(kind, boss, diff, rift, 0);
        }

        static void write(Action m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.kind);
            buf.writeVarInt(m.arg);
            buf.writeUtf(m.boss == null ? "" : m.boss, 64);
            (m.diff == null ? Difficulty.NORMAL : m.diff).write(buf);
            buf.writeBoolean(m.rift != null);
            if (m.rift != null) buf.writeUUID(m.rift);
        }

        static Action read(FriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            int arg = buf.readVarInt();
            String boss = buf.readUtf(64);
            Difficulty d = Difficulty.read(buf);
            UUID rift = buf.readBoolean() ? buf.readUUID() : null;
            return new Action(kind, boss, d, rift, arg);
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
        VoidInfo v = VoidInfo.NONE;
        Rifts.Rift vr = Rifts.voidRift();
        if (vr != null) {
            ServerLevel level = p.getServer().getLevel(vr.dim);
            long now = level == null ? 0 : level.getGameTime();
            Rifts.VoidConfig c = Rifts.voidConfig();
            int k = c.active == RiftEntity.MOTHER ? 1 : 0;
            v = new VoidInfo(true, c.active, List.of(List.copyOf(c.pools.get(0)), List.copyOf(c.pools.get(1))), vr.boss, vr.diff, vr.state,
                    vr.left(now), c.sequence, c.endMode, c.current[k], c.next[k]);
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Open(ids, chosen, rifts, v, notice));
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
                for (BossEntity b : p.level().getEntitiesOfClass(BossEntity.class, p.getBoundingBox().inflate(160), BossEntity::isTest)) {
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
            case VOID_ACTIVE -> {
                return Rifts.voidRift() == null ? "Este mundo no tiene el Void" : Rifts.voidSetActive(m.arg);
            }
            case VOID_ADD -> {
                if (cls == null) return "";
                return Rifts.voidAdd(m.arg, m.boss, d);
            }
            case VOID_REMOVE -> {
                return Rifts.voidRemove(m.arg / 1024, m.arg % 1024);
            }
            case VOID_TP -> {
                return Rifts.teleport(p, Rifts.VOID_ID) ? "Teletransportado a la grieta del Void" : "Este mundo no tiene el Void";
            }
            case VOID_RESET -> {
                return Rifts.reset(Rifts.VOID_ID) ? "Grieta del Void lista otra vez" : "Este mundo no tiene el Void";
            }
            case VOID_UP, VOID_DOWN -> {
                return Rifts.voidMove(m.arg / 1024, m.arg % 1024, m.kind == VOID_UP ? -1 : 1);
            }
            case VOID_EDIT -> {
                if (cls == null) return "";
                return Rifts.voidEdit(m.arg / 1024, m.arg % 1024, m.boss, d);
            }
            case VOID_ALL -> {
                return Rifts.voidAll(m.arg, d);
            }
            case VOID_MODE -> {
                return Rifts.voidMode(m.arg != 0);
            }
            case VOID_END -> {
                return Rifts.voidEnd(m.arg);
            }
            case VOID_NEXT -> {
                return Rifts.voidNext(m.arg / 1024, m.arg % 1024);
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
