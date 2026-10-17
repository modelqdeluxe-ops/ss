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
                    // Una incursión que estaba en marcha al cerrar: la grieta vuelve a estar lista
                    int st = (int) TFJson.num(r, "estado", RiftEntity.READY);
                    rift.state = st == RiftEntity.COOLDOWN ? RiftEntity.COOLDOWN : RiftEntity.READY;
                    rift.readyAt = TFJson.num(r, "listaEn", 0);
                    if (SkillDefs.bosses().containsKey(rift.boss)) RIFTS.put(rift.id, rift);
                } catch (RuntimeException ex) {
                    TFClient.LOGGER.warn("TF Jefes: grieta mal guardada: {}", ex.getMessage());
                }
            }
        }
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
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("grietas", arr);
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
            if (active && !anyoneNear(level, r.pos, LEASH + 24)) {
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
                    boolean anyone = anyoneNear(level, r.pos, LEASH + 24);
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
                    dirty = true;
                    level.playSound(null, r.pos.x, r.pos.y + 2, r.pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2F, 0.7F);
                }
            }
            default -> { }
        }
        e.show(r.boss, r.state, r.left(now), r.diff);
    }

    /** Lo que pasa mientras despierta: el cristal vibra, sube el sonido y la luz de su color, y al final estalla. */
    private static void wakingFx(ServerLevel level, Rift r, int left) {
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(r.boss);
        int color = cls != null && cls.bossInfo != null ? cls.bossInfo.color : 0xB45CF0;
        Vector3f c = new Vector3f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F);
        Vec3 p = r.pos.add(0, 2.6, 0);
        int done = RiftEntity.WAKE_TICKS - left;
        if (done % 4 == 0) {
            level.sendParticles(new DustParticleOptions(c, 1.6F), p.x, p.y, p.z, 6 + done / 6, 0.9, 1.4, 0.9, 0.02);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.6, 1.2, 0.6, 0.04);
        }
        if (done % 14 == 0) {
            level.playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 3F, 0.6F + done / 70F);
        }
        if (done == 20 || done == 45) level.playSound(null, p.x, p.y, p.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.5F, 0.6F);
        if (left == 1) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(c, 2F), p.x, p.y, p.z, 120, 2.2, 2.2, 2.2, 0.2);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 60, 1.5, 2, 1.5, 0.25);
            level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3F, 0.7F);
            level.playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 4F, 0.5F);
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
    private static Vec3 ground(ServerLevel level, Vec3 pos) {
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
        for (ServerPlayer p : playersNear(level, at, 64)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 16));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        }
    }

    /** ¿Hay alguien (también en creativo: el staff probando) cerca de la incursión? */
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
        r.state = RiftEntity.WAKING;
        r.wakeAt = e.level().getGameTime() + RiftEntity.WAKE_TICKS;
        dirty = true;
        e.level().playSound(null, e.getX(), e.getY() + 2, e.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3F, 0.6F);
        e.level().playSound(null, e.getX(), e.getY() + 2, e.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 3F, 0.5F);
        e.show(r.boss, r.state, r.left(e.level().getGameTime()), r.diff);
    }

    /** Clic derecho en el cristal: qué jefe guarda y cómo está. */
    static void info(RiftEntity e, ServerPlayer p) {
        Rift r = RIFTS.get(e.riftId);
        if (r == null) return;
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(r.boss);
        if (cls == null || cls.bossInfo == null) return;
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
        }
        if (bi == null) return;
        Component title = Component.literal(bi.name.toUpperCase(java.util.Locale.ROOT) + " HA CAÍDO")
                .withStyle(s -> s.withColor(TextColor.fromRgb(bi.color)).withBold(true));
        if (boss.level() instanceof ServerLevel sl) {
            for (ServerPlayer p : playersNear(sl, boss.position(), 80)) {
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

    public static List<Rift> list() {
        return new ArrayList<>(RIFTS.values());
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
        Rift r = RIFTS.remove(id);
        if (r == null) return false;
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
        if (r == null) return false;
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
        p.teleportTo(level, r.pos.x + 3, r.pos.y, r.pos.z + 3, p.getYRot(), p.getXRot());
        return true;
    }
}
