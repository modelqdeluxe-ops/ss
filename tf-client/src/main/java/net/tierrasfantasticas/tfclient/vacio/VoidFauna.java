package net.tierrasfantasticas.tfclient.vacio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.bosses.BossEntity;
import net.tierrasfantasticas.tfclient.bosses.Difficulty;
import net.tierrasfantasticas.tfclient.bosses.TFBosses;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;

/**
 * Las criaturas del Páramo del Alba Rota (1.3.96; el dueño: «un bioma completo con estas criaturas hostiles»): la fauna
 * «criaturas_vacio» (EndMobsVol1 sin el End Strider, convertida por tools/bosses/build_bosses.py) sale sola alrededor de
 * los jugadores que andan por el Páramo, con las reglas de su randomspawn de MythicMobs ({@link SkillDefs.Spawn}): cada
 * {@link #EVERY} ticks y por jugador se busca un sitio del Páramo por donde anda la fauna ({@link VoidIsland#randomFaunaSpot},
 * entre {@link #MIN} y {@link #MAX} bloques) y, en orden de prioridad, la primera regla que no está en recarga, pasa su
 * probabilidad y no tiene ya más de los que permite su «mobsInRadius» cerca. Lo que en el pack era «world_the_end» y
 * sus biomas aquí es el Páramo. Como mucho {@link #CAP} criaturas cerca de cada jugador, nunca junto a un Pozo de Luz ni
 * en el Velo del Alba, y las que se quedan lejos de todos se van. No se guardan en el mundo (como los jefes).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class VoidFauna {
    public static final String PACK = "criaturas_vacio";
    /** Cada cuánto se intenta (por jugador; 1.3.97, el dueño: «que no se generen muchísimas»: cada 10 s). */
    static final int EVERY = 200;
    /** Distancias del sitio al jugador. */
    static final double MIN = 28, MAX = 64;
    /** Como mucho, criaturas a menos de 64 bloques de un jugador (los espectros del Vigía cuentan). */
    static final int CAP = 2;
    /** Más lejos que esto de todos los jugadores, se van. */
    static final double GONE = 110;
    /** Ni junto a un Pozo de Luz (a menos de esto). */
    static final double WELL_CLEAR = 10;
    private static final Map<String, Long> COOLDOWN = new HashMap<>();
    private static final Random RND = new Random();
    /** /tf void fauna: apagada no sale ninguna (las que hay se quedan). */
    static boolean enabled = true;
    /** Para la prueba en el juego: cuántas han salido. */
    static int spawned;

    private VoidFauna() {}

    private static SkillDefs.ClassDef cls() {
        return SkillDefs.fauna().get(PACK);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel level = event.getServer().getLevel(TFVoid.VOID);
        if (level == null || level.players().isEmpty() || VoidIsland.get() == null) return;
        long now = level.getGameTime();
        if (now % 200 == 0) despawnFar(level);
        if (!enabled || now % EVERY != 0) return;
        // (como los mobs de Minecraft: nada con doMobSpawning apagado ni en pacífico)
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        SkillDefs.ClassDef cls = cls();
        if (cls == null || cls.bossInfo == null || cls.bossInfo.spawns.isEmpty()) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !near(p)) continue;
            trySpawn(level, cls, p, now);
        }
    }

    /** ¿Anda por el Páramo (o a su lado)? */
    private static boolean near(ServerPlayer p) {
        Vec3 c = VoidIsland.nearestFaunaWalkable(p.getX(), p.getY(), p.getZ());
        return c != null && c.distanceToSqr(p.getX(), c.y, p.getZ()) < 40 * 40;
    }

    private static void trySpawn(ServerLevel level, SkillDefs.ClassDef cls, ServerPlayer p, long now) {
        if (fauna(level, p.position(), 64).size() >= CAP) return;
        Vec3 spot = VoidIsland.randomFaunaSpot(p.getX(), p.getZ(), MIN, MAX, RND);
        if (spot == null || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(spot))) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(spot.x), (int) Math.floor(spot.z));
        if (y <= level.getMinBuildHeight() + 1) return;
        Vec3 at = new Vec3(spot.x, y, spot.z);
        if (VoidIsland.inBubble(at.x, at.y, at.z)) return;
        for (net.minecraft.core.BlockPos w : VoidIsland.shards()) {
            if (w.distToCenterSqr(at.x, w.getY(), at.z) < WELL_CLEAR * WELL_CLEAR) return;
        }
        List<SkillDefs.Spawn> rules = new ArrayList<>(cls.bossInfo.spawns);
        java.util.Collections.shuffle(rules, RND);
        rules.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        for (SkillDefs.Spawn r : rules) {
            Long ready = COOLDOWN.get(r.name());
            if (ready != null && now < ready) continue;
            if (RND.nextDouble() >= r.chance()) continue;
            if (r.radius() > 0) {
                int there = 0;
                for (BossEntity e : fauna(level, at, r.radius())) if (e.def() != null && e.def().name.equalsIgnoreCase(r.mob())) there++;
                if (there > r.maxNear()) continue;
            }
            if (spawn(level, r.mob(), at, RND.nextFloat() * 360F) != null) {
                COOLDOWN.put(r.name(), now + r.cooldown() * 20L);
                return;
            }
        }
    }

    /** Pone una criatura del Páramo (tipo de MythicMobs del pack) en ese sitio. */
    public static BossEntity spawn(ServerLevel level, String mob, Vec3 at, float yaw) {
        SkillDefs.ClassDef cls = cls();
        if (cls == null || cls.mobs.get(mob.toLowerCase(java.util.Locale.ROOT)) == null) return null;
        BossEntity e = TFBosses.ESBIRRO.get().create(level);
        if (e == null) return null;
        e.moveTo(at.x, at.y, at.z, yaw, 0F);
        e.configure(PACK, mob, Difficulty.NORMAL, 1, null);
        if (e.def() == null) return null;
        level.addFreshEntity(e);
        spawned++;
        return e;
    }

    private static List<BossEntity> fauna(ServerLevel level, Vec3 at, double r) {
        return level.getEntitiesOfClass(BossEntity.class, new AABB(at, at).inflate(r), e -> e.isFauna() && e.isAlive()
                && e.distanceToSqr(at) <= r * r);
    }

    /** Las que se han quedado lejos de todos los jugadores, fuera (sin soltar nada). */
    private static void despawnFar(ServerLevel level) {
        for (BossEntity e : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(BossEntity.class), BossEntity::isFauna)) {
            boolean close = false;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(e) < GONE * GONE) {
                    close = true;
                    break;
                }
            }
            if (!close) e.discard();
        }
    }

    /** Para la prueba en el juego y /tf void fauna: cuántas hay de cada una. */
    public static String describe(ServerLevel level) {
        Map<String, Integer> n = new java.util.TreeMap<>();
        for (BossEntity e : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(BossEntity.class), BossEntity::isFauna)) {
            n.merge(e.def() == null ? "?" : e.def().name, 1, Integer::sum);
        }
        SkillDefs.ClassDef cls = cls();
        return (cls == null ? "SIN LA FAUNA (criaturas_vacio)" : cls.bossInfo.spawns.size() + " reglas de aparición") + "; salidas "
                + spawned + "; ahora " + n;
    }
}
