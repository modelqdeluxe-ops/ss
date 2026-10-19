package net.tierrasfantasticas.tfclient.bosses;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.util.TFJson;
import org.joml.Vector3f;

/**
 * Las grietas del mundo (las pone el staff desde /tf boss) y sus incursiones: cada una guarda un jefe con su
 * dificultad. Lista → (un golpe al cristal) despertando → (sale el jefe) incursión → (cae el jefe) recarga → lista. Si
 * todos se van de la incursión (o el jefe se descarga), el jefe se va y la grieta vuelve a estar lista. Al caer el
 * jefe, cada jugador que le hizo daño se lleva las monedas de la dificultad. Se guardan en
 * &lt;mundo&gt;/tfclient/grietas.json.
 *
 * <p>1.3.89, la grieta del Void (el dueño: «el centro de la isla debe tener la grieta, configurable con /tf boss, para
 * no estar poniendo una grieta cada rato con un jefe distinto: la pongo desde ahí a todos los que quiera; dos grietas,
 * la normal y la grieta madre, más imponente, para criaturas más poderosas; por defecto la normal»): una grieta fija
 * ({@link Rift#fixed}) en el centro de la plaza de la isla del Void, que no se quita ni se edita como las demás. Tiene
 * dos listas de jefes con su dificultad ({@link VoidConfig}: la de la grieta normal y la de la madre) y una activa: cada
 * vez que vuelve a estar lista elige al azar uno de la lista activa (sin repetir el último si hay más de uno). En la
 * ventana, la pestaña VOID. Al principio, la normal con todos los jefes en su dificultad guardada y la madre con todos
 * en Pesadilla.
 *
 * <p>1.3.96 (el dueño: «con respecto a la grieta del Void, haz que pueda configurar los atributos para todos los bosses,
 * la secuencia de bosses y así»): la lista es una <b>secuencia</b> ({@link VoidConfig#sequence}: salen en orden, uno
 * tras otro; o al azar como antes), con su orden (subir y bajar), el siguiente que se quiera, los atributos de cada uno
 * (su {@link Difficulty} entera, o la misma para todos) y qué pasa al acabar ({@link VoidConfig#endMode}): volver a
 * empezar, parar (la grieta se queda sin criaturas y la del cielo, sellada) o <b>el final</b>: al caer el último jefe de
 * la secuencia, la grieta del cielo se cierra para siempre ({@code SkyRift.voidSequenceEnded}).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class Rifts {
    /** Lo lejos que puede irse un jefe de su grieta y lo que cuenta como «estar en la incursión». */
    public static final double LEASH = 40;
    /** Ticks sin nadie cerca para que el jefe se vaya. */
    private static final int EMPTY_TICKS = 600;

    private Rifts() {}

    public static final class Rift {
        public UUID id;
        public ResourceKey<Level> dim;
        public Vec3 pos;
        public String boss;
        public Difficulty diff;
        public int state = RiftEntity.READY;
        long wakeAt;
        long readyAt;
        UUID entity;
        UUID bossEntity;
        long emptySince = -1;
        int players;
        /** Su arena cargada a la fuerza (mientras despierta o hay pelea). */
        boolean forced;
        /** 0 grieta normal, 1 grieta madre ({@link RiftEntity#MOTHER}). */
        public int kind;
        /** La grieta del Void (la fija del centro de la isla: sus jefes salen de {@link VoidConfig}). */
        public boolean fixed;

        /** Ticks que faltan (despertar o recarga). */
        public int left(long now) {
            return switch (state) {
                case RiftEntity.WAKING -> (int) Math.max(0, wakeAt - now);
                case RiftEntity.COOLDOWN -> (int) Math.max(0, readyAt - now);
                default -> 0;
            };
        }
    }

    private static final Map<UUID, Rift> RIFTS = new LinkedHashMap<>();
    private static MinecraftServer server;
    private static boolean dirty;

    /** Un jefe de la lista de la grieta del Void, con su dificultad. */
    public record Entry(String boss, Difficulty diff) {}

    /**
     * Las listas de la grieta del Void: [0] la normal, [1] la madre; y cuál está en el centro: desde la 1.3.93b, siempre
     * la madre (el dueño: «que la grieta del Void siempre sea grieta madre»). La lista normal se guarda pero no se usa.
     */
    public static final class VoidConfig {
        public int active = RiftEntity.MOTHER;
        public final List<List<Entry>> pools = List.of(new ArrayList<>(), new ArrayList<>());
        final int[] last = {-1, -1};
        /** (1.3.96) En orden (una secuencia) o al azar. */
        public boolean sequence = true;
        /** (1.3.96) Al acabar la secuencia: {@link #END_REPEAT}, {@link #END_STOP} o {@link #END_FINAL}. */
        public int endMode = END_REPEAT;
        /** (1.3.96) Por lista: el que está en la grieta (−1 ninguno) y el que sale después (en la secuencia). */
        public final int[] current = {-1, -1}, next = {0, 0};
    }

    /** Al acabar la secuencia: volver a empezar, parar, o el final (la grieta del cielo se cierra para siempre). */
    public static final int END_REPEAT = 0, END_STOP = 1, END_FINAL = 2;

    /** La grieta del Void tiene siempre el mismo id. */
    public static final UUID VOID_ID = UUID.nameUUIDFromBytes("tfclient:grieta-del-void".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static VoidConfig voidConfig = new VoidConfig();
    private static final java.util.Random PICK = new java.util.Random();

    // ------------------------------------------------------------------------------------------- guardar

    private static Path file() {
        return TFJson.worldFile(server, "grietas.json");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        RIFTS.clear();
        BossConfig.load();
        JsonObject o = TFJson.read(file());
        if (o != null && o.has("grietas") && o.get("grietas").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("grietas")) {
                try {
                    JsonObject r = e.getAsJsonObject();
                    Rift rift = new Rift();
                    rift.id = UUID.fromString(TFJson.str(r, "id", UUID.randomUUID().toString()));
                    rift.dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(TFJson.str(r, "mundo", "minecraft:overworld")));
                    rift.pos = new Vec3(TFJson.dec(r, "x", 0), TFJson.dec(r, "y", 64), TFJson.dec(r, "z", 0));
                    rift.boss = TFJson.str(r, "jefe", "");
                    rift.diff = Difficulty.fromJson(TFJson.obj(r, "dificultad"));
                    rift.kind = (int) TFJson.num(r, "tipo", RiftEntity.NORMAL) == RiftEntity.MOTHER ? RiftEntity.MOTHER : RiftEntity.NORMAL;
                    rift.fixed = rift.id.equals(VOID_ID);
                    // Una incursión que estaba en marcha al cerrar: la grieta vuelve a estar lista
                    int st = (int) TFJson.num(r, "estado", RiftEntity.READY);
                    rift.state = st == RiftEntity.COOLDOWN ? RiftEntity.COOLDOWN : RiftEntity.READY;
                    rift.readyAt = TFJson.num(r, "listaEn", 0);
                    if (rift.fixed || SkillDefs.bosses().containsKey(rift.boss)) RIFTS.put(rift.id, rift);
                } catch (RuntimeException ex) {
                    TFClient.LOGGER.warn("TF Jefes: grieta mal guardada: {}", ex.getMessage());
                }
            }
        }
        voidConfig = loadVoid(o == null ? null : TFJson.obj(o, "vacio"));
        ensureVoidRift();
        // (si el servidor se cerró de golpe en mitad de una pelea, su arena seguía cargada a la fuerza)
        for (Rift r : RIFTS.values()) {
            ServerLevel level = server.getLevel(r.dim);
            if (level != null) force(level, r, false);
        }
        TFClient.LOGGER.info("TF Jefes: {} grietas", RIFTS.size());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Rift r : RIFTS.values()) {
            ServerLevel level = server.getLevel(r.dim);
            if (level == null) continue;
            Entity b = r.bossEntity == null ? null : level.getEntity(r.bossEntity);
            if (b != null) b.discard();
            if (r.state == RiftEntity.WAKING || r.state == RiftEntity.FIGHT) r.state = RiftEntity.READY;
            if (r.forced) force(level, r, false);
        }
        save();
        server = null;
    }

    private static void save() {
        if (server == null) return;
        JsonArray arr = new JsonArray();
        for (Rift r : RIFTS.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id.toString());
            o.addProperty("mundo", r.dim.location().toString());
            o.addProperty("x", r.pos.x);
            o.addProperty("y", r.pos.y);
            o.addProperty("z", r.pos.z);
            o.addProperty("jefe", r.boss);
            o.add("dificultad", r.diff.toJson());
            o.addProperty("estado", r.state);
            o.addProperty("listaEn", r.readyAt);
            o.addProperty("tipo", r.kind);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("grietas", arr);
        root.add("vacio", saveVoid());
        TFJson.write(file(), root);
        dirty = false;
    }

    // ------------------------------------------------------------------------------------------- cada tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        for (Rift r : new ArrayList<>(RIFTS.values())) {
            try {
                tick(r);
            } catch (Throwable t) {
                TFClient.LOGGER.error("TF Jefes: error en la grieta {}", r.id, t);
            }
        }
        if (dirty && server.getTickCount() % 100 == 0) save();
        // La grieta del cielo (1.3.93): sellada mientras la del Void recarga; se vuelve a abrir cuando la del Void tiene
        // otro jefe listo (y no antes de 30 s: que se vea que se cerró)
        Rift v = voidRift();
        if (server.getTickCount() % 20 == 0 && v != null && v.state != RiftEntity.COOLDOWN && SkillDefs.bosses().containsKey(v.boss)) {
            net.tierrasfantasticas.tfclient.vacio.SkyRift.voidReady();
        }
    }

    private static void tick(Rift r) {
        ServerLevel level = server.getLevel(r.dim);
        if (level == null) return;
        long now = level.getGameTime();
        BlockPos bp = BlockPos.containing(r.pos);
        // Mientras despierta o hay pelea, su arena se queda cargada (como el dragón del End en su isla): si no, al
        // alejarse el jugador hacia el borde (o con el servidor lento al cargar) el jefe se congelaba o se descargaba
        boolean active = r.state == RiftEntity.WAKING || r.state == RiftEntity.FIGHT;
        if (active != r.forced) force(level, r, active);
        if (!level.isLoaded(bp) || !level.isPositionEntityTicking(bp)) {
            // Aún cargando: se espera. Antes se reiniciaba en el acto y el jefe seguía suelto: al golpear el cristal
            // salía otro («aparecen, se van, reaparecen»). Solo se cierra si no queda nadie cerca un buen rato
            if (active && !anyoneNear(level, r.pos, reach(level, LEASH + 24))) {
                if (r.emptySince < 0) {
                    r.emptySince = now;
                } else if (now - r.emptySince > EMPTY_TICKS) {
                    trace(r, "sin cargar y sin nadie cerca: se queda lista");
                    Entity b = r.bossEntity == null ? null : level.getEntity(r.bossEntity);
                    if (b != null) b.discard();
                    r.bossEntity = null;
                    r.state = RiftEntity.READY;
                    dirty = true;
                }
            }
            return;
        }
        RiftEntity e = r.entity == null ? null : level.getEntity(r.entity) instanceof RiftEntity re && !re.isRemoved() ? re : null;
        if (e == null) {
            e = TFBosses.GRIETA.get().create(level);
            if (e == null) return;
            e.riftId = r.id;
            e.moveTo(r.pos.x, r.pos.y, r.pos.z, 0F, 0F);
            level.addFreshEntity(e);
            r.entity = e.getUUID();
        }
        switch (r.state) {
            case RiftEntity.WAKING -> {
                int left = r.left(now);
                wakingFx(level, r, left);
                if (left <= 0) spawn(level, r);
            }
            case RiftEntity.FIGHT -> {
                Entity b = r.bossEntity == null ? null : level.getEntity(r.bossEntity);
                if (!(b instanceof BossEntity boss) || boss.isRemoved()) {
                    trace(r, "el jefe ya no está (" + (b == null ? "no se encuentra" : "quitado") + "): se queda lista");
                    r.state = RiftEntity.READY;
                    r.bossEntity = null;
                    dirty = true;
                } else if (!boss.dying()) {
                    boolean anyone = anyoneNear(level, r.pos, reach(level, LEASH + 24));
                    if (anyone) r.emptySince = -1;
                    else if (r.emptySince < 0) r.emptySince = now;
                    else if (now - r.emptySince > EMPTY_TICKS) {
                        trace(r, "nadie cerca: el jefe se va");
                        boss.discard();
                        r.state = RiftEntity.READY;
                        r.bossEntity = null;
                        dirty = true;
                    }
                }
            }
            case RiftEntity.COOLDOWN -> {
                if (now >= r.readyAt) {
                    r.state = RiftEntity.READY;
                    if (r.fixed) pickVoid(r);
                    dirty = true;
                    level.playSound(null, r.pos.x, r.pos.y + 2, r.pos.z, BossSounds.RIFT_READY, SoundSource.HOSTILE, 2.5F, 1F);
                }
            }
            default -> { }
        }
        e.show(r.boss, r.state, r.left(now), r.diff, r.kind, r.fixed);
    }

    /**
     * Lo que pasa mientras despierta: el cristal vibra, el desgarro se abre, sube la luz morada (la misma en todas las
     * grietas desde la 1.3.86) y al final estalla. El sonido que sube lo pone wake (grieta.despertar dura lo mismo).
     */
    private static void wakingFx(ServerLevel level, Rift r, int left) {
        Vector3f c = r.kind == RiftEntity.MOTHER ? RiftEntity.MOTHER_RED : RiftEntity.VOID_PURPLE;
        Vec3 p = r.pos.add(0, r.kind == RiftEntity.MOTHER ? 9.2 : 6.7, 0);
        int done = RiftEntity.WAKE_TICKS - left;
        if (done % 4 == 0) {
            level.sendParticles(new DustParticleOptions(c, 1.6F), p.x, p.y, p.z, 6 + done / 6, 0.9, 1.4, 0.9, 0.02);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.6, 1.2, 0.6, 0.04);
        }
        if (done % 6 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 14 + done / 3, 1.3, 4.2, 1.3, 0.08);
        if (left == 1) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(c, 2F), p.x, p.y, p.z, 120, 2.2, 2.2, 2.2, 0.2);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 60, 1.5, 2, 1.5, 0.25);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 220, 2, 5, 2, 0.6);
            level.playSound(null, p.x, p.y, p.z, BossSounds.RIFT_BURST, SoundSource.HOSTILE, 6F, 1F);
        }
    }

    private static void spawn(ServerLevel level, Rift r) {
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(r.boss);
        if (cls == null || cls.bossInfo == null) {
            trace(r, "jefe desconocido");
            r.state = RiftEntity.READY;
            return;
        }
        List<ServerPlayer> near = playersNear(level, r.pos, 32);
        int players = Math.max(1, near.size());
        // Mira al jugador más cercano
        float yaw = 0F;
        if (!near.isEmpty()) {
            Vec3 d = near.get(0).position().subtract(r.pos);
            yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        }
        BossEntity boss = spawnBoss(level, r.pos, r.boss, r.diff, players, yaw);
        if (boss == null) {
            trace(r, "no se pudo crear el jefe");
            r.state = RiftEntity.READY;
            return;
        }
        boss.setRift(r.id, r.pos);
        r.bossEntity = boss.getUUID();
        r.players = players;
        r.state = RiftEntity.FIGHT;
        r.emptySince = -1;
        dirty = true;
        announce(level, r.pos, cls.bossInfo, r.diff, players);
    }

    /** Pone un jefe en el mundo (también la «prueba» de la ventana /tf boss, sin grieta). */
    public static BossEntity spawnBoss(ServerLevel level, Vec3 pos, String bossId, Difficulty diff, int players, float yaw) {
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(bossId);
        if (cls == null || cls.bossInfo == null) return null;
        BossEntity boss = TFBosses.JEFE.get().create(level);
        if (boss == null) return null;
        pos = ground(level, pos);
        boss.moveTo(pos.x, pos.y, pos.z, yaw, 0F);
        boss.configure(bossId, cls.bossInfo.mob, diff, players, null);
        level.addFreshEntity(boss);
        return boss;
    }

    /**
     * Baja hasta el suelo un sitio que está en el aire (la prueba de /tf boss sale 8 bloques delante del jugador): con
     * NoAI o setai{ai=false} al aparecer, como en Minecraft, el jefe no caería y se quedaría flotando.
     */
    static Vec3 ground(ServerLevel level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos);
        if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return pos;
        int drop = 0;
        while (drop < 32 && p.getY() > level.getMinBuildHeight() && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
            p = p.below();
            drop++;
        }
        return drop == 0 ? pos : new Vec3(pos.x, p.getY(), pos.z);
    }

    private static void announce(ServerLevel level, Vec3 at, SkillDefs.BossInfo bi, Difficulty d, int players) {
        Component title = Component.literal(bi.name.toUpperCase(java.util.Locale.ROOT))
                .withStyle(s -> s.withColor(TextColor.fromRgb(bi.color)).withBold(true));
        Component sub = Component.literal(d.label()).withStyle(s -> s.withColor(TextColor.fromRgb(d.color())))
                .append(Component.literal("  ·  " + players + (players == 1 ? " jugador" : " jugadores")).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : playersNear(level, at, reach(level, 64))) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 16));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        }
    }

    /** ¿Hay alguien (también en creativo: el staff probando) cerca de la incursión? */
    /**
     * Lo lejos que llega la pelea de una grieta: en The Void, toda la isla (1.3.92: el jefe persigue por toda ella;
     * {@code BossEntity.island}); en los demás sitios, lo que se pide.
     */
    static double reach(ServerLevel level, double r) {
        return net.tierrasfantasticas.tfclient.vacio.TFVoid.isVoid(level) ? Math.max(r, 512) : r;
    }

    static boolean anyoneNear(ServerLevel level, Vec3 at, double r) {
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.position().distanceToSqr(at) <= r * r) return true;
        return false;
    }

    static List<ServerPlayer> playersNear(ServerLevel level, Vec3 at, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && !p.isCreative() && p.position().distanceToSqr(at) <= r * r) out.add(p);
        }
        out.sort((a, b) -> Double.compare(a.position().distanceToSqr(at), b.position().distanceToSqr(at)));
        return out;
    }

    // ------------------------------------------------------------------------------------------- desde las entidades

    static boolean owns(RiftEntity e) {
        Rift r = e.riftId == null ? null : RIFTS.get(e.riftId);
        return r != null && e.getUUID().equals(r.entity);
    }

    /** Un jugador golpea el cristal: empieza a despertar el jefe. */
    static void wake(RiftEntity e, ServerPlayer p) {
        Rift r = RIFTS.get(e.riftId);
        if (r == null || r.state != RiftEntity.READY) return;
        if (!SkillDefs.bosses().containsKey(r.boss)) {
            // (la grieta del Void sin criaturas en su lista)
            p.displayClientMessage(Component.literal("Esta grieta duerme: el staff le pone criaturas en /tf boss → VOID")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        r.state = RiftEntity.WAKING;
        r.wakeAt = e.level().getGameTime() + RiftEntity.WAKE_TICKS;
        dirty = true;
        e.level().playSound(null, e.getX(), e.getY() + 2, e.getZ(), BossSounds.RIFT_HIT, SoundSource.HOSTILE, 3F, 1F);
        e.level().playSound(null, e.getX(), e.getY() + 4, e.getZ(), BossSounds.RIFT_WAKE, SoundSource.HOSTILE, 4F, 1F);
        e.show(r.boss, r.state, r.left(e.level().getGameTime()), r.diff, r.kind, r.fixed);
    }

    /** Clic derecho en el cristal: qué jefe guarda y cómo está. */
    static void info(RiftEntity e, ServerPlayer p) {
        Rift r = RIFTS.get(e.riftId);
        if (r == null) return;
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(r.boss);
        if (cls == null || cls.bossInfo == null) {
            if (r.fixed) {
                p.displayClientMessage(Component.literal(r.kind == RiftEntity.MOTHER ? "Grieta madre" : "Grieta del Void")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                        .append(Component.literal(" · sin criaturas (el staff: /tf boss → VOID)").withStyle(ChatFormatting.GRAY)), true);
            }
            return;
        }
        String state = switch (r.state) {
            case RiftEntity.READY -> "Golpea el cristal para despertarlo";
            case RiftEntity.WAKING -> "Despertando...";
            case RiftEntity.FIGHT -> "Incursión en marcha";
            default -> "Se recarga en " + time(r.left(e.level().getGameTime()));
        };
        p.displayClientMessage(Component.literal(cls.bossInfo.name).withStyle(s -> s.withColor(TextColor.fromRgb(cls.bossInfo.color)).withBold(true))
                .append(Component.literal(" · " + r.diff.label()).withStyle(s -> s.withColor(TextColor.fromRgb(r.diff.color())).withBold(false)))
                .append(Component.literal(" · " + state).withStyle(ChatFormatting.GRAY)), true);
    }

    /** Radio en chunks de la arena que se queda cargada durante la pelea (3 = 7×7 chunks: la correa y algo más). */
    private static final int ARENA_CHUNKS = 3;

    /** Carga (o suelta) a la fuerza los chunks de la arena de una grieta. */
    private static void force(ServerLevel level, Rift r, boolean on) {
        int cx = SectionPos.blockToSectionCoord(r.pos.x), cz = SectionPos.blockToSectionCoord(r.pos.z);
        for (int dx = -ARENA_CHUNKS; dx <= ARENA_CHUNKS; dx++) {
            for (int dz = -ARENA_CHUNKS; dz <= ARENA_CHUNKS; dz++) level.setChunkForced(cx + dx, cz + dz, on);
        }
        r.forced = on;
    }

    /** Por qué cambia una grieta (solo en la prueba de los jefes, BossTest). */
    private static void trace(Rift r, String why) {
        if (BossTest.ON) TFClient.LOGGER.info("Prueba de jefes: grieta {} ({}): {}", r.boss, r.state, why);
    }

    /** mm:ss de unos ticks. */
    public static String time(int ticks) {
        int s = (ticks + 19) / 20;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    /** Ha caído el jefe de una grieta: recompensas y a recargar. */
    static void bossKilled(BossEntity boss, List<ServerPlayer> players) {
        Rift r = boss.rift() == null ? null : RIFTS.get(boss.rift());
        Difficulty d = r != null ? r.diff : boss.difficulty();
        SkillDefs.BossInfo bi = boss.cls() == null ? null : boss.cls().bossInfo;
        if (r != null) {
            r.state = RiftEntity.COOLDOWN;
            r.readyAt = boss.level().getGameTime() + d.respawnMin() * 1200L;
            r.bossEntity = null;
            dirty = true;
            // (1.3.93) si es la del Void, la grieta del cielo se sella... por ahora; (1.3.96) o, si era el último de la
            // secuencia y al acabar va el final, se cierra para siempre
            if (r.fixed) {
                if (sequenceFinale(r)) net.tierrasfantasticas.tfclient.vacio.SkyRift.voidSequenceEnded();
                else net.tierrasfantasticas.tfclient.vacio.SkyRift.voidBossKilled();
            }
            // (1.3.91) la grieta se cierra: se traga la luz (el cierre lo dibuja RiftRenderer y el fogonazo, RiftWarp)
            if (boss.level() instanceof ServerLevel sl) {
                Vec3 c = r.pos.add(0, r.kind == RiftEntity.MOTHER ? 16.5 : 11.5, 0);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 260, 3, 8, 3, 0.9);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 5F, 0.55F);
                sl.playSound(null, c.x, c.y, c.z, BossSounds.RIFT_HIT, SoundSource.HOSTILE, 4F, 0.5F);
            }
        }
        if (bi == null) return;
        Component title = Component.literal(bi.name.toUpperCase(java.util.Locale.ROOT) + " HA CAÍDO")
                .withStyle(s -> s.withColor(TextColor.fromRgb(bi.color)).withBold(true));
        if (boss.level() instanceof ServerLevel sl) {
            for (ServerPlayer p : playersNear(sl, boss.position(), reach(sl, 80))) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(d.label()).withStyle(s -> s.withColor(TextColor.fromRgb(d.color())))));
                sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1F, 1F);
            }
        }
        // Monedas para cada jugador que le hizo daño y sigue cerca (solo los jefes de una grieta)
        if (r == null || d.coins() <= 0) return;
        for (ServerPlayer p : players) {
            if (p.level() != boss.level() || p.distanceToSqr(boss) > 96 * 96) continue;
            if (TFEconomy.give(p.getServer(), p.getUUID(), p.getGameProfile().getName(), d.coins())) {
                p.sendSystemMessage(Component.literal("+" + d.coins() + " monedas").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(" por derrotar a " + bi.name + " (" + d.label() + ")").withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    /** El jefe se ha ido sin morir (se descargó o lo quitó el staff): su grieta vuelve a estar lista. */
    /** El jefe de la grieta ha muerto y sigue su relevo (BossEntity.becomeSuccessor). */
    static void relayed(BossEntity from, BossEntity next) {
        Rift r = from.rift() == null ? null : RIFTS.get(from.rift());
        if (r != null) {
            r.bossEntity = next.getUUID();
            dirty = true;
        }
    }

    static void bossGone(BossEntity boss) {
        Rift r = boss.rift() == null ? null : RIFTS.get(boss.rift());
        if (r != null && r.state == RiftEntity.FIGHT) {
            r.state = RiftEntity.READY;
            r.bossEntity = null;
            dirty = true;
        }
    }

    // ------------------------------------------------------------------------------------------- staff (/tf boss)

    /** Las grietas que pone el staff (sin la del Void, que va en su pestaña). */
    public static List<Rift> list() {
        List<Rift> out = new ArrayList<>();
        for (Rift r : RIFTS.values()) if (!r.fixed) out.add(r);
        return out;
    }

    public static Rift get(UUID id) {
        return RIFTS.get(id);
    }

    /** Pone una grieta donde está el jugador. */
    public static Rift place(ServerPlayer p, String boss, Difficulty d) {
        Rift r = new Rift();
        r.id = UUID.randomUUID();
        r.dim = p.level().dimension();
        r.pos = new Vec3(Math.floor(p.getX()) + 0.5, Math.floor(p.getY()), Math.floor(p.getZ()) + 0.5);
        r.boss = boss;
        r.diff = d;
        RIFTS.put(r.id, r);
        dirty = true;
        save();
        return r;
    }

    public static boolean remove(UUID id) {
        Rift cur = RIFTS.get(id);
        if (cur == null || cur.fixed) return false;
        Rift r = RIFTS.remove(id);
        ServerLevel level = server.getLevel(r.dim);
        if (level != null) {
            if (r.forced) force(level, r, false);
            Entity e = r.entity == null ? null : level.getEntity(r.entity);
            if (e != null) e.discard();
            Entity b = r.bossEntity == null ? null : level.getEntity(r.bossEntity);
            if (b != null) b.discard();
        }
        save();
        return true;
    }

    /** Cambia el jefe o la dificultad de una grieta (si está en incursión, cuenta para la siguiente). */
    public static boolean edit(UUID id, String boss, Difficulty d) {
        Rift r = RIFTS.get(id);
        if (r == null || r.fixed) return false;
        r.boss = boss;
        r.diff = d;
        save();
        return true;
    }

    /** La deja lista ya (si había incursión, el jefe se va). */
    public static boolean reset(UUID id) {
        Rift r = RIFTS.get(id);
        if (r == null) return false;
        ServerLevel level = server.getLevel(r.dim);
        Entity b = level == null || r.bossEntity == null ? null : level.getEntity(r.bossEntity);
        if (b != null) b.discard();
        r.bossEntity = null;
        r.state = RiftEntity.READY;
        save();
        return true;
    }

    public static boolean teleport(ServerPlayer p, UUID id) {
        Rift r = RIFTS.get(id);
        ServerLevel level = r == null ? null : server.getLevel(r.dim);
        if (level == null) return false;
        if (r.fixed) {
            // la del Void: delante, al norte de su círculo de runas, mirándola (con el agujero negro detrás)
            p.teleportTo(level, r.pos.x, r.pos.y, r.pos.z - (r.kind == RiftEntity.MOTHER ? 25 : 18), 0F, -12F);
            return true;
        }
        p.teleportTo(level, r.pos.x + 3, r.pos.y, r.pos.z + 3, p.getYRot(), p.getXRot());
        return true;
    }

    // ------------------------------------------------------------------------------------------- la grieta del Void

    private static VoidConfig loadVoid(JsonObject o) {
        VoidConfig c = new VoidConfig();
        if (o == null) {
            // Al principio: la normal con todos los jefes (en su dificultad guardada) y la madre con todos en Pesadilla
            Difficulty hard = Difficulty.preset("pesadilla").values();
            for (String id : SkillDefs.bosses().keySet()) {
                SkillDefs.ClassDef cls = SkillDefs.bosses().get(id);
                if (cls == null || cls.bossInfo == null) continue;
                c.pools.get(0).add(new Entry(id, BossConfig.get(id)));
                c.pools.get(1).add(new Entry(id, hard));
            }
            dirty = true;
            return c;
        }
        c.active = RiftEntity.MOTHER;
        c.sequence = !o.has("secuencia") || TFJson.bool(o, "secuencia", true);
        c.endMode = Math.max(END_REPEAT, Math.min(END_FINAL, (int) TFJson.num(o, "alAcabar", END_REPEAT)));
        String[] keys = {"normal", "madre"};
        for (int k = 0; k < 2; k++) {
            if (!o.has(keys[k]) || !o.get(keys[k]).isJsonArray()) continue;
            for (JsonElement e : o.getAsJsonArray(keys[k])) {
                try {
                    JsonObject eo = e.getAsJsonObject();
                    String boss = TFJson.str(eo, "jefe", "");
                    if (SkillDefs.bosses().containsKey(boss)) c.pools.get(k).add(new Entry(boss, Difficulty.fromJson(TFJson.obj(eo, "dificultad"))));
                } catch (RuntimeException ex) {
                    TFClient.LOGGER.warn("TF Jefes: jefe de la grieta del Void mal guardado: {}", ex.getMessage());
                }
            }
            c.current[k] = Math.min((int) TFJson.num(o, keys[k] + "Actual", -1), c.pools.get(k).size() - 1);
            c.next[k] = Math.max(0, (int) TFJson.num(o, keys[k] + "Siguiente", 0));
        }
        return c;
    }

    private static JsonObject saveVoid() {
        JsonObject o = new JsonObject();
        o.addProperty("activa", voidConfig.active);
        o.addProperty("secuencia", voidConfig.sequence);
        o.addProperty("alAcabar", voidConfig.endMode);
        String[] keys = {"normal", "madre"};
        for (int k = 0; k < 2; k++) {
            JsonArray a = new JsonArray();
            for (Entry e : voidConfig.pools.get(k)) {
                JsonObject eo = new JsonObject();
                eo.addProperty("jefe", e.boss());
                eo.add("dificultad", e.diff().toJson());
                a.add(eo);
            }
            o.add(keys[k], a);
            o.addProperty(keys[k] + "Actual", voidConfig.current[k]);
            o.addProperty(keys[k] + "Siguiente", voidConfig.next[k]);
        }
        return o;
    }

    /** La grieta del Void en el centro de la plaza (si el mundo tiene el Void); la crea la primera vez. */
    private static void ensureVoidRift() {
        if (server == null || server.getLevel(net.tierrasfantasticas.tfclient.vacio.TFVoid.VOID) == null) return;
        Rift r = RIFTS.get(VOID_ID);
        if (r == null) {
            r = new Rift();
            r.id = VOID_ID;
            r.fixed = true;
            r.diff = Difficulty.NORMAL;
            r.boss = "";
            RIFTS.put(r.id, r);
            dirty = true;
        }
        BlockPos at = net.tierrasfantasticas.tfclient.vacio.VoidIsland.riftPos();
        r.dim = net.tierrasfantasticas.tfclient.vacio.TFVoid.VOID;
        r.pos = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        r.kind = voidConfig.active;
        if (r.state == RiftEntity.READY && !inPool(r)) pickVoid(r);
    }

    private static boolean inPool(Rift r) {
        for (Entry e : voidConfig.pools.get(r.kind)) if (e.boss().equals(r.boss) && e.diff().equals(r.diff)) return true;
        return false;
    }

    /**
     * El siguiente jefe de la grieta del Void: el siguiente de la secuencia (1.3.96; al acabarla, según
     * {@link VoidConfig#endMode}) o, al azar, uno de la lista sin repetir el último si hay más.
     */
    private static void pickVoid(Rift r) {
        int k = r.kind;
        List<Entry> pool = voidConfig.pools.get(k);
        int i;
        if (pool.isEmpty()) {
            i = -1;
        } else if (voidConfig.sequence) {
            i = voidConfig.next[k];
            if (i >= pool.size()) i = voidConfig.endMode == END_REPEAT ? 0 : -1;
        } else {
            int last = voidConfig.last[k];
            i = PICK.nextInt(pool.size());
            if (pool.size() > 1 && i == last) i = (i + 1 + PICK.nextInt(pool.size() - 1)) % pool.size();
        }
        put(r, i);
    }

    /** Pone en la grieta del Void el n.º i de su lista (−1: ninguno; la secuencia ha terminado o la lista está vacía). */
    private static void put(Rift r, int i) {
        int k = r.kind;
        List<Entry> pool = voidConfig.pools.get(k);
        if (i < 0 || i >= pool.size()) {
            r.boss = "";
            r.diff = Difficulty.NORMAL;
            voidConfig.current[k] = -1;
            if (voidConfig.sequence && !pool.isEmpty()) voidConfig.next[k] = pool.size();
        } else {
            voidConfig.last[k] = i;
            voidConfig.current[k] = i;
            voidConfig.next[k] = i + 1;
            r.boss = pool.get(i).boss();
            r.diff = pool.get(i).diff();
        }
        dirty = true;
    }

    /** ¿El jefe que acaba de caer era el último de la secuencia y al acabar va el final? */
    private static boolean sequenceFinale(Rift r) {
        List<Entry> pool = voidConfig.pools.get(r.kind);
        return voidConfig.sequence && voidConfig.endMode == END_FINAL && !pool.isEmpty() && voidConfig.current[r.kind] == pool.size() - 1;
    }

    /** Para la prueba en el juego: despierta la grieta del Void (sin golpe). */
    static boolean testWake() {
        Rift r = voidRift();
        ServerLevel level = r == null || server == null ? null : server.getLevel(r.dim);
        if (level == null || r.state != RiftEntity.READY || !SkillDefs.bosses().containsKey(r.boss)) return false;
        r.state = RiftEntity.WAKING;
        r.wakeAt = level.getGameTime() + RiftEntity.WAKE_TICKS;
        dirty = true;
        return true;
    }

    public static VoidConfig voidConfig() {
        return voidConfig;
    }

    /** La grieta del Void (null si el mundo no tiene el Void). */
    public static Rift voidRift() {
        return RIFTS.get(VOID_ID);
    }

    /** La grieta del centro del Void: siempre la madre (1.3.93b, orden del dueño); se pida la que se pida. */
    public static String voidSetActive(int kind) {
        voidConfig.active = RiftEntity.MOTHER;
        Rift r = voidRift();
        if (r != null) {
            r.kind = voidConfig.active;
            if (r.state == RiftEntity.READY || r.state == RiftEntity.COOLDOWN) pickVoid(r);
        }
        save();
        return kind == RiftEntity.MOTHER ? "En el centro del Void: la grieta madre"
                : "La grieta del Void es siempre la grieta madre (sigue la madre)";
    }

    public static String voidAdd(int kind, String boss, Difficulty d) {
        if (!SkillDefs.bosses().containsKey(boss)) return "";
        List<Entry> pool = voidConfig.pools.get(kind == RiftEntity.MOTHER ? 1 : 0);
        if (pool.size() >= 64) return "La lista ya tiene 64 criaturas";
        pool.add(new Entry(boss, d));
        Rift r = voidRift();
        if (r != null && r.kind == kind && r.state == RiftEntity.READY && !SkillDefs.bosses().containsKey(r.boss)) pickVoid(r);
        save();
        return "Añadido a la " + (kind == RiftEntity.MOTHER ? "grieta madre" : "grieta del Void") + ": " + name(boss) + " (" + d.label() + ")";
    }

    public static String voidRemove(int kind, int index) {
        int k = kind == RiftEntity.MOTHER ? 1 : 0;
        List<Entry> pool = voidConfig.pools.get(k);
        if (index < 0 || index >= pool.size()) return "";
        Entry e = pool.remove(index);
        voidConfig.last[k] = -1;
        if (voidConfig.current[k] == index) voidConfig.current[k] = -1;
        else if (voidConfig.current[k] > index) voidConfig.current[k]--;
        if (voidConfig.next[k] > index) voidConfig.next[k]--;
        Rift r = voidRift();
        if (r != null && r.kind == kind && r.state == RiftEntity.READY && !inPool(r)) pickVoid(r);
        save();
        return "Quitado de la " + (kind == RiftEntity.MOTHER ? "grieta madre" : "grieta del Void") + ": " + name(e.boss());
    }

    /** (1.3.96) Sube (dir −1) o baja (+1) el n.º index de la lista (lo que está en la grieta no cambia). */
    public static String voidMove(int kind, int index, int dir) {
        int k = kind == RiftEntity.MOTHER ? 1 : 0;
        List<Entry> pool = voidConfig.pools.get(k);
        int j = index + dir;
        if (index < 0 || index >= pool.size() || j < 0 || j >= pool.size()) return "";
        java.util.Collections.swap(pool, index, j);
        int[] cur = voidConfig.current, nx = voidConfig.next;
        if (cur[k] == index) cur[k] = j;
        else if (cur[k] == j) cur[k] = index;
        if (nx[k] == index) nx[k] = j;
        else if (nx[k] == j) nx[k] = index;
        voidConfig.last[k] = -1;
        save();
        return name(pool.get(j).boss()) + ": n.º " + (j + 1) + " de la secuencia";
    }

    /** (1.3.96) Cambia el jefe y los atributos del n.º index (si es el que espera en la grieta, ya sale así). */
    public static String voidEdit(int kind, int index, String boss, Difficulty d) {
        int k = kind == RiftEntity.MOTHER ? 1 : 0;
        List<Entry> pool = voidConfig.pools.get(k);
        if (index < 0 || index >= pool.size() || !SkillDefs.bosses().containsKey(boss)) return "";
        pool.set(index, new Entry(boss, d));
        Rift r = voidRift();
        if (r != null && r.kind == k && voidConfig.current[k] == index && r.state == RiftEntity.READY) {
            r.boss = boss;
            r.diff = d;
        }
        save();
        return "N.º " + (index + 1) + " de la grieta del Void: " + name(boss) + " (" + d.label() + ")";
    }

    /** (1.3.96) Los mismos atributos para todos los de la lista (cada uno sigue siendo su jefe). */
    public static String voidAll(int kind, Difficulty d) {
        int k = kind == RiftEntity.MOTHER ? 1 : 0;
        List<Entry> pool = voidConfig.pools.get(k);
        if (pool.isEmpty()) return "La lista está vacía";
        pool.replaceAll(e -> new Entry(e.boss(), d));
        Rift r = voidRift();
        if (r != null && r.kind == k && r.state == RiftEntity.READY && SkillDefs.bosses().containsKey(r.boss)) r.diff = d;
        save();
        return "Los " + pool.size() + " jefes de la grieta del Void, en " + d.label();
    }

    /** (1.3.96) En orden o al azar. */
    public static String voidMode(boolean sequence) {
        voidConfig.sequence = sequence;
        save();
        return sequence ? "Grieta del Void: los jefes salen en orden (secuencia)" : "Grieta del Void: los jefes salen al azar";
    }

    /** (1.3.96) Qué pasa al acabar la secuencia. */
    public static String voidEnd(int mode) {
        voidConfig.endMode = Math.max(END_REPEAT, Math.min(END_FINAL, mode));
        Rift r = voidRift();
        // (si ya había acabado y ahora se repite, vuelve a empezar)
        if (r != null && r.state == RiftEntity.READY && !SkillDefs.bosses().containsKey(r.boss)) pickVoid(r);
        save();
        return switch (voidConfig.endMode) {
            case END_STOP -> "Al acabar la secuencia: la grieta del Void se queda sin criaturas";
            case END_FINAL -> "Al acabar la secuencia: EL FINAL (al caer el último, la grieta del cielo se cierra para siempre)";
            default -> "Al acabar la secuencia: vuelve a empezar";
        };
    }

    /**
     * (1.3.96) Que el n.º index sea el siguiente: si la grieta está esperando (lista, sin despertar), sale ya; si no, al
     * acabar la pelea o la recarga.
     */
    public static String voidNext(int kind, int index) {
        int k = kind == RiftEntity.MOTHER ? 1 : 0;
        List<Entry> pool = voidConfig.pools.get(k);
        if (index < 0 || index >= pool.size()) return "";
        Rift r = voidRift();
        if (r != null && r.kind == k && r.state == RiftEntity.READY) {
            put(r, index);
            save();
            return "Ahora en la grieta del Void: " + name(pool.get(index).boss()) + " (n.º " + (index + 1) + ")";
        }
        voidConfig.next[k] = index;
        save();
        return "Después saldrá " + name(pool.get(index).boss()) + " (n.º " + (index + 1) + ")";
    }

    private static String name(String boss) {
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(boss);
        return cls != null && cls.bossInfo != null ? cls.bossInfo.name : boss;
    }
}
