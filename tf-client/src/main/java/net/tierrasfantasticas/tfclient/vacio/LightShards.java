package net.tierrasfantasticas.tfclient.vacio;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Los fragmentos de luz del Void (1.3.96, ver {@link VoidLight}): esquirlas del Alba que flotan por la isla grande en los
 * sitios que eligió tools/void/isla_alba.py ({@link VoidIsland#shards}). Cada una es una entidad de exhibición de bloque
 * de Minecraft (un sapoluz perlado puesto de punta, con brillo máximo, que gira despacio y sube y baja): se ve igual con
 * paquetes de shaders y no hace falta registrar objetos. Al pasar por ella se recoge (luz y su Eco del Alba) y vuelve a
 * salir a los {@link #RESPAWN_S} s (para todos: son pocas y hay que buscarlas). Alrededor, unas chispas que se ven de
 * lejos.
 *
 * <p>Las entidades se guardan con su chunk: si al cargar uno ya está la suya, se usa; si sobra (o es de unos sitios que
 * ya no existen), se quita.
 */
public final class LightShards {
    /** Segundos hasta que vuelve a salir uno recogido. */
    static final int RESPAWN_S = 180;
    static final String TAG = "tfclient_fragmento";
    private static final SoundEvent PICK = SoundEvent.createVariableRangeEvent(new ResourceLocation(TFClient.MOD_ID, "luz.fragmento"));
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1F, 0.85F, 0.45F), 1.2F);
    /** Cuándo vuelve a salir cada uno (tiempo de juego; 0 = ya). */
    private static long[] readyAt;
    private static int picked;

    private LightShards() {}

    /** Al arrancar el servidor: todos salen (no se guarda cuáles estaban recogidos). */
    static void reset() {
        readyAt = null;
        picked = 0;
    }

    static void tick(ServerLevel level) {
        List<BlockPos> list = VoidIsland.shards();
        if (list.isEmpty()) return;
        if (readyAt == null || readyAt.length != list.size()) readyAt = new long[list.size()];
        long now = level.getGameTime();
        if (now % 4 == 0) pickups(level, list, now);
        if (now % 20 == 0) ensure(level, list, now);
        if (now % 10 == 0) sparkle(level, list, now);
        if (now % 400 == 0) cleanup(level, list);
    }

    private static boolean ready(int i, long now) {
        return now >= readyAt[i];
    }

    /** ¿Quién pasa por un fragmento que está? (a 1,6 bloques en horizontal y 2 en vertical) */
    private static void pickups(ServerLevel level, List<BlockPos> list, long now) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            for (int i = 0; i < list.size(); i++) {
                if (!ready(i, now)) continue;
                BlockPos b = list.get(i);
                double dx = p.getX() - (b.getX() + 0.5), dz = p.getZ() - (b.getZ() + 0.5), dy = p.getY() + 0.9 - (b.getY() + 0.5);
                if (dx * dx + dz * dz > 1.6 * 1.6 || Math.abs(dy) > 2.0) continue;
                readyAt[i] = now + RESPAWN_S * 20L;
                picked++;
                for (Display.BlockDisplay d : near(level, b)) d.discard();
                Vec3 c = Vec3.atCenterOf(b);
                level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 0.3, 0.4, 0.3, 0.12);
                level.sendParticles(GOLD, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.02);
                level.playSound(null, c.x, c.y, c.z, PICK, SoundSource.PLAYERS, 1F, 1F);
                VoidLight.shard(p);
            }
        }
    }

    /** Los que están, con su entidad; los recogidos, sin ella (solo en chunks con sus entidades cargadas). */
    private static void ensure(ServerLevel level, List<BlockPos> list, long now) {
        for (int i = 0; i < list.size(); i++) {
            BlockPos b = list.get(i);
            if (!level.hasChunkAt(b) || !level.areEntitiesLoaded(ChunkPos.asLong(b))) continue;
            List<Display.BlockDisplay> here = near(level, b);
            if (!ready(i, now)) {
                for (Display.BlockDisplay d : here) d.discard();
                continue;
            }
            for (int k = 1; k < here.size(); k++) here.get(k).discard();
            if (here.isEmpty()) {
                Entity e = spawn(level, b, i, now);
                if (e == null) return;
            } else if (now % 40 == 0) {
                spin(here.get(0), i, now);
            }
        }
    }

    private static List<Display.BlockDisplay> near(ServerLevel level, BlockPos b) {
        return level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(b).inflate(1.5), d -> d.getTags().contains(TAG));
    }

    /** El giro y el vaivén de ahora + 2 s (la entidad pasa de uno a otro suave: interpolación de Minecraft). */
    private static CompoundTag transform(int i, long when) {
        float a = (float) ((when / 40.0 + i * 0.37) * Math.PI / 2);
        float s = 0.55F;
        // de punta (como un rombo) y girando alrededor de la vertical
        Quaternionf q = new Quaternionf().rotateY(a).rotateX((float) Math.toRadians(35.26)).rotateZ((float) Math.toRadians(45));
        Vector3f c = q.transform(new Vector3f(0.5F * s, 0.5F * s, 0.5F * s));
        float bob = 0.12F * (float) Math.sin((when / 40.0 + i) * 1.3);
        CompoundTag t = new CompoundTag();
        t.put("left_rotation", floats(q.x, q.y, q.z, q.w));
        t.put("right_rotation", floats(0, 0, 0, 1));
        t.put("translation", floats(-c.x, -c.y + bob, -c.z));
        t.put("scale", floats(s, s, s));
        return t;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    private static Entity spawn(ServerLevel level, BlockPos b, int i, long now) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:pearlescent_froglight");
        CompoundTag props = new CompoundTag();
        props.putString("axis", "y");
        state.put("Properties", props);
        tag.put("block_state", state);
        tag.put("transformation", transform(i, now));
        CompoundTag light = new CompoundTag();
        light.putInt("block", 15);
        light.putInt("sky", 15);
        tag.put("brightness", light);
        tag.putFloat("view_range", 3F);
        tag.putFloat("shadow_radius", 0F);
        Vec3 c = Vec3.atCenterOf(b);
        Entity e = EntityType.loadEntityRecursive(tag, level, en -> {
            en.moveTo(c.x, c.y, c.z, 0F, 0F);
            return en;
        });
        if (e == null) {
            TFClient.LOGGER.warn("TF Void: no se pudo crear el fragmento de luz {}", i);
            return null;
        }
        e.addTag(TAG);
        e.addTag(TAG + "_" + i);
        level.addFreshEntity(e);
        return e;
    }

    /** Cada 2 s, el siguiente giro (con interpolación de 2 s: gira y sube y baja sin saltos). */
    private static void spin(Display.BlockDisplay d, int i, long now) {
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.put("transformation", transform(i, now + 40));
        tag.putInt("interpolation_duration", 40);
        tag.putInt("start_interpolation", 0);
        d.load(tag);
    }

    /** Chispas alrededor de los que están (se ven de lejos). */
    private static void sparkle(ServerLevel level, List<BlockPos> list, long now) {
        for (int i = 0; i < list.size(); i++) {
            if (!ready(i, now)) continue;
            BlockPos b = list.get(i);
            if (!level.hasChunkAt(b)) continue;
            Vec3 c = Vec3.atCenterOf(b);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.35, 0.45, 0.35, 0.01);
            if ((now / 10 + i) % 3 == 0) level.sendParticles(GOLD, c.x, c.y + 0.2, c.z, 2, 0.4, 0.5, 0.4, 0.0);
        }
    }

    /** Los fragmentos que no están en ningún sitio de la lista (de otra versión de la isla), fuera. */
    private static void cleanup(ServerLevel level, List<BlockPos> list) {
        for (Display.BlockDisplay d : level.getEntities(EntityTypeTest.forClass(Display.BlockDisplay.class), d -> d.getTags().contains(TAG))) {
            boolean ok = false;
            for (BlockPos b : list) {
                if (d.position().distanceToSqr(Vec3.atCenterOf(b)) < 2.25) {
                    ok = true;
                    break;
                }
            }
            if (!ok) d.discard();
        }
    }

    /** Para la prueba en el juego. */
    public static String describe(ServerLevel level) {
        List<BlockPos> list = VoidIsland.shards();
        int ready = 0, spawned = 0;
        long now = level.getGameTime();
        for (int i = 0; i < list.size(); i++) {
            if (readyAt == null || ready(i, now)) ready++;
            if (level.hasChunkAt(list.get(i)) && !near(level, list.get(i)).isEmpty()) spawned++;
        }
        return list.size() + " fragmentos, " + ready + " listos, " + spawned + " con su entidad (en chunks cargados), " + picked + " recogidos";
    }
}
