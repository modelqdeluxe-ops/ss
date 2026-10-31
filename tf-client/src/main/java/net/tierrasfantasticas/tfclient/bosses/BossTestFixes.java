/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.claims.data.GlobalFlags;
import net.tierrasfantasticas.tfclient.claims.util.ClaimMobs;

/**
 * (1.3.156) La prueba en el juego del escenario «arreglos» ([prueba-jefes=arreglos]):
 * <ul>
 * <li>La caída del 31 oct 2026: la ficha de una venta de la GTS cuyo objeto tiene un tooltip que falla en el servidor
 * (el Item Terminal de Pretty Pipes: NoClassDefFoundError de una clase del cliente), y una vista del pad que falla
 * entera: el servidor tiene que seguir.</li>
 * <li>Las flags de spawn de las protecciones: por cada flag (ninguna, monstruos, ningún mob, pasivos y la global), cada
 * tipo de mob por los caminos de Forge (antes de crearlo, el sitio, prepararlo) y por el de Bukkit/otros mods (añadirlo
 * sin prepararlo), y el huevo de spawn (sale siempre). Luego, con el spawn natural encendido y de noche, cuenta los
 * mobs que nacen dentro y fuera de una zona 64x64 con cada flag.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
final class BossTestFixes {
    private BossTestFixes() {}

    private static Claim zone;
    private static BlockPos center;
    private static String gtsId;
    /** La noche de spawn de verdad: qué flag está puesta y lo que nace (dentro/fuera × hostil/pasivo/otro). */
    private static String trial;
    private static final Map<String, Map<String, Integer>> BORN = new LinkedHashMap<>();

    /** Los tipos que se prueban, con lo que son para las flags. */
    private static final List<EntityType<? extends Mob>> TYPES = List.of(EntityType.ZOMBIE, EntityType.SLIME, EntityType.PHANTOM,
            EntityType.COW, EntityType.COD, EntityType.BAT, EntityType.VILLAGER);

    /** Las flags: nombre → los tipos que tienen que NO nacer dentro. */
    private static final String[] CONFIGS = {"ninguna", "monstruos", "todos", "pasivos", "global"};

    private static List<String> expected(String config) {
        return switch (config) {
            case "monstruos" -> List.of("zombie", "slime", "phantom");
            case "pasivos" -> List.of("cow", "cod", "bat");
            case "todos", "global" -> List.of("zombie", "slime", "phantom", "cow", "cod", "bat", "villager");
            default -> List.of();
        };
    }

    private static void set(String config) {
        ClaimFlags f = zone.getFlags();
        f.blockMobSpawn = config.equals("monstruos");
        f.blockAllMobSpawn = config.equals("todos");
        f.blockPassiveMobSpawn = config.equals("pasivos");
        GlobalFlags.getInstance().globalNoMobSpawn = config.equals("global");
    }

    private static String name(EntityType<?> type) {
        return EntityType.getKey(type).getPath();
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        switch (t) {
            case 1 -> {
                player.teleportTo(level, arena.x, arena.y, arena.z, 0F, 0F);
                gts(player, r);
            }
            case 40 -> {
                center = player.blockPosition();
                ClaimManager cm = ClaimManager.getInstance();
                zone = cm.createClaim(level, center, player, ClaimTier.byId("claimstone_64x64"));
                r.event("spawn: zona 64x64 en " + center.toShortString() + " (radio " + zone.getRadius() + ")");
                for (String config : CONFIGS) paths(level, config, r);
                set("ninguna");
            }
            default -> { }
        }
        // La noche de spawn de verdad: 5 tandas de 800 ticks (ninguna, monstruos, pasivos, todos, global)
        int first = 60, len = 800;
        String[] order = {"ninguna", "monstruos", "pasivos", "todos", "global"};
        if (t >= first && t < first + order.length * len) {
            int k = (t - first) / len, at = (t - first) % len;
            if (at == 0) {
                set(order[k]);
                clear(level);
                if (k == 0) {
                    BossTest.command(server, "time set 18000");
                    BossTest.command(server, "gamerule doMobSpawning true");
                }
                trial = order[k];
                BORN.put(trial, new TreeMap<>());
            }
            if (at == 400 && k == 0) BossTest.shot(r, "spawn_noche_sin_flag");
            if (at == len - 1) {
                Map<String, Integer> m = BORN.get(trial);
                List<String> bad = new ArrayList<>();
                for (var e : m.entrySet()) {
                    String[] key = e.getKey().split("/");
                    if (!key[0].equals("dentro")) continue;
                    boolean blocked = switch (trial) {
                        case "monstruos" -> key[1].equals("hostil");
                        case "pasivos" -> key[1].equals("pasivo");
                        case "todos", "global" -> true;
                        default -> false;
                    };
                    if (blocked) bad.add(e.getKey() + "=" + e.getValue());
                }
                if (trial.equals("global")) {
                    for (var e : m.entrySet()) bad.add(e.getKey() + "=" + e.getValue());
                }
                String verdict;
                if (trial.equals("ninguna")) {
                    int inside = 0;
                    for (var e : m.entrySet()) if (e.getKey().startsWith("dentro")) inside += e.getValue();
                    verdict = inside > 0 ? "(bien: sin flag sí nacen dentro)" : "(sin datos: no nació nada dentro)";
                } else {
                    verdict = bad.isEmpty() ? "(bien)" : "(MAL: " + String.join(", ", bad) + ")";
                }
                r.event("spawn natural, flag «" + trial + "», 40 s de noche: nacen " + m + " " + verdict);
                trial = null;
            }
        }
        if (t == first + order.length * len + 5) {
            BossTest.command(server, "gamerule doMobSpawning false");
            BossTest.command(server, "time set 6000");
            set("ninguna");
            clear(level);
            if (zone != null) ClaimManager.getInstance().removeClaim(level, zone.getCenter());
            zone = null;
        }
        if (t == first + order.length * len + 20) BossTest.next(level);
    }

    /** La GTS: una venta con el tooltip roto y una vista entera rota. */
    private static void gts(ServerPlayer player, BossTest.Rep r) {
        try {
            player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 3));
            String err = net.tierrasfantasticas.tfclient.market.TFMarket.publish(player, 0, null, 100);
            var mine = net.tierrasfantasticas.tfclient.market.TFMarket.listingsOf(player.getUUID());
            if (err != null || mine.isEmpty()) {
                r.event("gts 0: no se pudo publicar la venta de prueba (" + err + ")");
                return;
            }
            gtsId = mine.get(0).id();
            net.tierrasfantasticas.tfclient.pad.server.PadMarket.testTooltipError = true;
            try {
                var view = net.tierrasfantasticas.tfclient.pad.server.PadMarket.APP.view(player, "l:" + gtsId);
                r.event("gts 1 (ficha de la venta con el tooltip que falla como Pretty Pipes): sale la ficha "
                        + (view != null ? "(bien: el servidor sigue)" : "(MAL: vacía)"));
            } catch (Throwable e) {
                r.event("gts 1 (ficha de la venta con el tooltip que falla): REVIENTA (MAL): " + e);
            } finally {
                net.tierrasfantasticas.tfclient.pad.server.PadMarket.testTooltipError = false;
            }
            net.tierrasfantasticas.tfclient.pad.server.PadMarket.testViewError = true;
            try {
                net.tierrasfantasticas.tfclient.pad.PadServer.refresh(player, "gts", "l:" + gtsId);
                r.event("gts 2 (la vista entera de la app falla con NoClassDefFoundError): el pad lo atrapa (bien: el servidor sigue)");
            } catch (Throwable e) {
                r.event("gts 2 (la vista entera falla): REVIENTA (MAL): " + e);
            } finally {
                net.tierrasfantasticas.tfclient.pad.server.PadMarket.testViewError = false;
            }
            net.tierrasfantasticas.tfclient.market.TFMarket.withdraw(player, gtsId);
        } catch (Throwable e) {
            r.event("gts: FALLO DE LA PRUEBA " + e);
        }
    }

    /** Cada tipo por cada camino, con una flag puesta. */
    private static void paths(ServerLevel level, String config, BossTest.Rep r) {
        set(config);
        BlockPos in = center.offset(12, 0, 12);
        // fuera de la zona (radio 64) pero en chunks cargados (a 200 no lo estaban y getEntity no lo encontraba)
        BlockPos out = center.offset(80, 0, 0);
        List<String> bornPlacement = new ArrayList<>(), bornPosition = new ArrayList<>(), bornForge = new ArrayList<>(),
                bornBukkit = new ArrayList<>(), eggNo = new ArrayList<>(), outNo = new ArrayList<>();
        for (EntityType<? extends Mob> type : TYPES) {
            String n = name(type);
            // 1) antes de crearlo (por la categoría)
            var placement = new MobSpawnEvent.SpawnPlacementCheck(type, level, MobSpawnType.NATURAL, in, level.getRandom(), true);
            MinecraftForge.EVENT_BUS.post(placement);
            if (placement.getResult() != Event.Result.DENY) bornPlacement.add(n);
            // 2) el sitio, ya creado
            Mob p = type.create(level);
            p.moveTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0F, 0F);
            var position = new MobSpawnEvent.PositionCheck(p, level, MobSpawnType.NATURAL, null);
            MinecraftForge.EVENT_BUS.post(position);
            if (position.getResult() != Event.Result.DENY) bornPosition.add(n);
            p.discard();
            // 3) Forge: prepararlo y añadirlo (lo que hace el spawn natural)
            Mob f = type.create(level);
            f.moveTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0F, 0F);
            ForgeEventFactory.onFinalizeSpawn(f, level, level.getCurrentDifficultyAt(in), MobSpawnType.NATURAL, null, null);
            level.addFreshEntityWithPassengers(f);
            if (level.getEntity(f.getUUID()) != null) bornForge.add(n);
            f.discard();
            // 4) Bukkit / otros mods: añadirlo sin prepararlo
            Mob b = type.create(level);
            b.moveTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0F, 0F);
            boolean added = level.addFreshEntity(b);
            if (added && level.getEntity(b.getUUID()) != null) bornBukkit.add(n);
            b.discard();
            // 5) el huevo de spawn: sale siempre
            Entity egg = type.spawn(level, in, MobSpawnType.SPAWN_EGG);
            if (egg == null || level.getEntity(egg.getUUID()) == null) eggNo.add(n);
            if (egg != null) egg.discard();
            // 6) fuera de la zona (con la global sí se para)
            Mob o = type.create(level);
            o.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 0F, 0F);
            boolean addedOut = level.addFreshEntity(o);
            if (!addedOut) outNo.add(n);
            o.discard();
        }
        List<String> want = expected(config);
        List<String> mustBorn = new ArrayList<>();
        for (EntityType<? extends Mob> type : TYPES) if (!want.contains(name(type))) mustBorn.add(name(type));
        String ok = "";
        ok += check("antes de crearlo", bornPlacement, mustBorn);
        ok += " " + check("el sitio", bornPosition, mustBorn);
        ok += " " + check("Forge (prepararlo)", bornForge, mustBorn);
        ok += " " + check("Bukkit (sin prepararlo)", bornBukkit, mustBorn);
        boolean eggOk = eggNo.isEmpty();
        boolean outOk = config.equals("global") ? outNo.size() == TYPES.size() : outNo.isEmpty();
        r.event("spawn flag «" + config + "»: tienen que nacer dentro " + mustBorn + ". " + ok + " Huevo: "
                + (eggOk ? "salen todos (bien)" : "NO salen " + eggNo + " (MAL)") + ". Fuera de la zona: "
                + (outNo.isEmpty() ? "nacen todos" : "no nacen " + outNo) + (outOk ? " (bien)" : " (MAL)"));
    }

    /** Compara lo que nació con lo que tenía que nacer. */
    private static String check(String what, List<String> born, List<String> mustBorn) {
        List<String> wrong = new ArrayList<>();
        for (String n : mustBorn) if (!born.contains(n)) wrong.add("no nace " + n);
        for (String n : born) if (!mustBorn.contains(n)) wrong.add("nace " + n);
        return what + ": " + (wrong.isEmpty() ? "bien" : "MAL (" + String.join(", ", wrong) + ")") + ".";
    }

    private static void clear(ServerLevel level) {
        if (center == null) return;
        AABB box = new AABB(center).inflate(220, 128, 220);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> e instanceof Mob && !(e instanceof BossEntity))) e.discard();
    }

    /** Lo que de verdad entra en el mundo (los cancelados no llegan aquí). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!BossTest.ON || trial == null || zone == null || event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof Mob m) || m instanceof BossEntity) return;
        String where = zone.contains(m.blockPosition()) ? "dentro" : "fuera";
        boolean hostile = ClaimMobs.hostile(m) || m.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
        boolean passive = m instanceof net.minecraft.world.entity.animal.Animal || m instanceof net.minecraft.world.entity.animal.WaterAnimal
                || m instanceof net.minecraft.world.entity.ambient.AmbientCreature;
        String kind = hostile ? "hostil" : passive ? "pasivo" : "otro";
        BORN.computeIfAbsent(trial, k -> new TreeMap<>()).merge(where + "/" + kind, 1, Integer::sum);
    }
}
