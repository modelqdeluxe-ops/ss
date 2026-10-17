package net.tierrasfantasticas.tfclient.bosses;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;

/**
 * La prueba de los jefes en el juego de verdad (no en el simulador): solo con la variable de entorno
 * TFCLIENT_PRUEBA_JEFES (el workflow jefes-prueba.yml la pone y arranca el cliente en una pantalla virtual). En un mundo
 * plano, por cada jefe: abre una grieta, la despierta, un jugador de prueba pelea con él (se acerca y se aleja, le
 * rodea y le pega con una espada), lo mata y mira su muerte. Mide lo que el dueño ve en el juego: si el jefe
 * desaparece o reaparece, si sale volando o se va lejos, si anda hacia atrás o de lado, si ignora al jugador, si le
 * pega, qué animaciones pone, si se queda sin animación, los errores, la barra después de morir, los objetos y la
 * experiencia que caen y los esbirros que quedan. Pide capturas al cliente (BossTestClient) y lo escribe todo en
 * run/pruebas/informe.txt.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class BossTest {
    public static final boolean ON = System.getenv("TFCLIENT_PRUEBA_JEFES") != null;
    /** Ticks de pelea por jefe y de mirar su muerte. */
    private static final int FIGHT = 800, DEATH = 320;
    /** Ticks de la pelea con captura. */
    private static final Set<Integer> SHOTS = Set.of(40, 200, 360, 520, 680);

    static volatile String shotRequest;
    static volatile boolean finished;
    /** Barras de jefe que ve el cliente ahora (BossTestClient). */
    static volatile int clientBars = -1;

    private static final List<Rep> REPS = new ArrayList<>();
    private static volatile Rep current;
    private static List<String> ids;
    private static int index = -1, phase, t;
    private static long waitPlayer;
    private static Rifts.Rift rift;
    private static BossEntity boss;
    private static Vec3 arena;
    private static Vec3 lastPos;
    private static int wantDist = 6, side = 1;
    /** Tick (de la fase de muerte) en que salió el relevo (-1 = ninguno pendiente) y desde el que se mira la muerte. */
    private static int relayAt = -1, deathFrom, lastState = -1;

    private BossTest() {}

    /** Lo que se ha visto de un jefe. */
    static final class Rep {
        final String id;
        final List<String> events = new ArrayList<>();
        final Set<UUID> bosses = new LinkedHashSet<>();
        final Map<String, Integer> anims = new TreeMap<>();
        final Map<String, Integer> errors = new LinkedHashMap<>();
        int fightTicks, targetTicks, nearTicks, movingTicks, backTicks, sideTicks, airTicks, fastTicks, teleports;
        int noModelTicks, noAnimTicks, hitsTaken, playerAttacks, minionsMax;
        double dmgTaken, maxHome, maxAir, hpStart = -1, hpEnd = -1;
        int barsAfterDeath = -1, items = -1, xp = -1, minionsLeft = -1;
        boolean removedAfterDeath;
        String deathAnims = "";

        Rep(String id) {
            this.id = id;
        }

        void event(String s) {
            events.add("t=" + t + " " + s);
        }
    }

    static {
        if (ON) installLogTap();
    }

    /** Los avisos y errores del juego (también los «debug» de TF Jefes) cuentan para el jefe que se está probando. */
    private static void installLogTap() {
        try {
            Configurator.setLevel(TFClient.LOGGER.getName(), Level.DEBUG);
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration cfg = ctx.getConfiguration();
            AbstractAppender tap = new AbstractAppender("tfprueba", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent ev) {
                    String msg = ev.getMessage() == null ? "" : ev.getMessage().getFormattedMessage();
                    boolean ours = msg.contains("TF Jefes") || msg.contains("tfclient");
                    boolean bad = ev.getLevel().isMoreSpecificThan(Level.WARN);
                    if (!(ours && (bad || msg.contains("error"))) && !(bad && msg.toLowerCase().contains("texture"))) return;
                    Rep r = current;
                    String key = msg.length() > 160 ? msg.substring(0, 160) : msg;
                    if (ev.getThrown() != null) key += " [" + ev.getThrown() + "]";
                    if (r != null) {
                        synchronized (r.errors) {
                            r.errors.merge(key, 1, Integer::sum);
                        }
                    }
                }
            };
            tap.start();
            cfg.getRootLogger().addAppender(tap, Level.DEBUG, null);
            ctx.updateLoggers();
        } catch (Throwable e) {
            TFClient.LOGGER.warn("Prueba de jefes: no se pudo escuchar el registro", e);
        }
    }

    /** BossEntity.remove: se va del mundo (para ver si desaparece sin morir). */
    static void removed(BossEntity b, Entity.RemovalReason reason) {
        Rep r = current;
        if (r == null || !b.isMainBoss()) return;
        r.event("se quita " + b.getUUID().toString().substring(0, 8) + " (" + reason + (b.isDeadOrDying() ? ", muerto" : ", VIVO") + ")");
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!ON || !(event.getEntity() instanceof ServerPlayer p) || current == null) return;
        Entity src = event.getSource().getEntity();
        if (src instanceof BossEntity || src != null && SkillMobs.host(src) != null) {
            current.hitsTaken++;
            current.dmgTaken += event.getAmount();
        }
        // El jugador de prueba no muere: así se ve la pelea entera
        if (event.getAmount() >= p.getHealth() - 1) event.setAmount(Math.max(0F, p.getHealth() - 1));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END || finished) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = server.overworld();
        try {
            step(server, level, player);
        } catch (Throwable e) {
            TFClient.LOGGER.error("Prueba de jefes: fallo en la prueba", e);
            if (current != null) current.event("FALLO DE LA PRUEBA: " + e);
            next(level);
        }
    }

    private static void command(MinecraftServer server, String cmd) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    private static void step(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        if (ids == null) {
            if (waitPlayer == 0) waitPlayer = level.getGameTime();
            if (level.getGameTime() - waitPlayer < 100) return;
            ids = new ArrayList<>(SkillDefs.bosses().keySet());
            String only = System.getenv("TFCLIENT_PRUEBA_JEFES");
            if (only != null && !only.isBlank() && !only.equals("1")) ids.retainAll(List.of(only.split(",")));
            for (String c : new String[] {"gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set 6000",
                    "weather clear", "gamerule doMobSpawning false", "gamerule keepInventory true", "difficulty normal",
                    "gamerule doImmediateRespawn true"}) {
                command(server, c);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
            TFClient.LOGGER.info("Prueba de jefes: {} jefes: {}", ids.size(), ids);
            next(level);
            return;
        }
        if (index >= ids.size()) return;
        t++;
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        Rep r = current;
        switch (phase) {
            case 0 -> {
                // Llegar a la arena (dejar que se carguen los chunks) y poner la grieta en medio
                if (t == 1) player.teleportTo(level, arena.x, arena.y, arena.z, 0F, 0F);
                if (t == 40) {
                    rift = Rifts.place(player, r.id, Difficulty.NORMAL);
                    player.teleportTo(level, arena.x, arena.y, arena.z - 12, 0F, 10F);
                }
                if (t == 60) {
                    lastState = rift.state;
                    rift.state = RiftEntity.WAKING;
                    rift.wakeAt = level.getGameTime() + RiftEntity.WAKE_TICKS;
                    phase = 1;
                    t = 0;
                }
            }
            case 1 -> {
                if (rift.state != lastState) {
                    r.event("grieta: estado " + lastState + " -> " + rift.state);
                    lastState = rift.state;
                }
                if (t % 100 == 1) {
                    net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(rift.pos);
                    r.event(String.format("diagnóstico: grieta en %.0f %.0f %.0f, jugador en %.0f %.0f %.0f; cargada %s, activa %s, forzados %d",
                            rift.pos.x, rift.pos.y, rift.pos.z, player.getX(), player.getY(), player.getZ(), level.isLoaded(bp),
                            level.isPositionEntityTicking(bp), level.getForcedChunks().size()));
                }
                if (rift.state == RiftEntity.FIGHT && bossNow(level) != null) {
                    boss = bossNow(level);
                    r.hpStart = boss.getHealth();
                    r.event("sale el jefe (" + (int) boss.getMaxHealth() + " de vida)");
                    lastPos = boss.position();
                    phase = 2;
                    t = 0;
                } else if (t > 600) {
                    r.event("NO SALE el jefe (la grieta está en " + rift.state + ")");
                    next(level);
                }
            }
            case 2 -> fight(level, player, r);
            case 3 -> death(level, player, r);
            default -> { }
        }
    }

    private static BossEntity bossNow(ServerLevel level) {
        Entity e = rift.bossEntity == null ? null : level.getEntity(rift.bossEntity);
        return e instanceof BossEntity b && !b.isRemoved() ? b : null;
    }

    private static void fight(ServerLevel level, ServerPlayer player, Rep r) {
        BossEntity now = bossNow(level);
        if (now != boss) {
            r.event(now == null ? "el jefe YA NO ESTÁ (grieta: " + rift.state + ")" : "aparece OTRO jefe " + now.getUUID().toString().substring(0, 8));
            boss = now;
            if (now != null) lastPos = now.position();
        }
        if (boss == null) {
            // ¿Vuelve a salir? (la grieta lo saca otra vez si se quedó lista)
            if (rift.state == RiftEntity.READY) {
                rift.state = RiftEntity.WAKING;
                rift.wakeAt = level.getGameTime() + RiftEntity.WAKE_TICKS;
            }
            if (t > FIGHT) kill(level, r);
            return;
        }
        r.bosses.add(boss.getUUID());
        r.fightTicks++;
        Vec3 bp = boss.position();
        // El jugador: cambia de distancia cada 5 s, le rodea despacio, le mira y le pega si llega
        if (t % 100 == 1) {
            wantDist = new int[] {3, 6, 10, 16, 4, 8}[(t / 100) % 6];
            side = -side;
        }
        Vec3 rel = player.position().subtract(bp).multiply(1, 0, 1);
        double d = rel.length();
        if (d < 1e-3) rel = new Vec3(0, 0, -1);
        Vec3 dir = rel.normalize();
        double reach = wantDist + boss.getBbWidth() / 2;
        double radial = Mth.clamp((reach - d) * 0.15, -0.25, 0.25);
        Vec3 tan = new Vec3(-dir.z, 0, dir.x).scale(0.08 * side);
        Vec3 np = player.position().add(dir.scale(radial)).add(tan);
        // Como en una pelea de verdad: el jugador no se va de la arena (a 22 bloques de la grieta como mucho)
        Vec3 fromRift = np.subtract(rift.pos).multiply(1, 0, 1);
        if (fromRift.length() > 22) np = rift.pos.add(fromRift.normalize().scale(22)).add(0, np.y - rift.pos.y, 0);
        // Para las capturas, se aparta hasta ver al jefe entero
        boolean view = SHOTS.contains(t + 6) || SHOTS.contains(t + 3) || SHOTS.contains(t);
        if (view) np = bp.add(dir.scale(Math.max(12, boss.getBbWidth() * 1.5 + boss.getBbHeight() * 1.2)));
        int gy = ground(level, Mth.floor(np.x), Mth.floor(np.z));
        Vec3 look = boss.position().add(0, boss.getBbHeight() * 0.5, 0).subtract(np.x, gy + 1.62, np.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        if (!player.isPassenger()) player.teleportTo(level, np.x, gy, np.z, yaw, pitch);
        if (!view && d < 3.5 + boss.getBbWidth() / 2 && t % 12 == 0) {
            player.attack(boss);
            player.resetAttackStrengthTicker();
            r.playerAttacks++;
        }
        // Lo que hace el jefe
        if (boss.target() == player) r.targetTicks++;
        if (d < 30) r.nearTicks++;
        Vec3 mv = bp.subtract(lastPos);
        lastPos = bp;
        double hs = Math.sqrt(mv.x * mv.x + mv.z * mv.z);
        if (hs > 4) {
            r.teleports++;
        } else if (hs > 0.8) {
            r.fastTicks++;
        }
        List<String> anims = SkillMobs.animNames(boss);
        String what = anims == null ? "sin modelo" : String.join(",", anims.subList(Math.min(1, anims.size()), anims.size()));
        boolean back = false;
        if (hs > 0.03 && hs <= 4) {
            r.movingTicks++;
            float go = (float) Math.toDegrees(Math.atan2(-mv.x, mv.z));
            float diff = Math.abs(Mth.wrapDegrees(go - boss.yBodyRot));
            if (diff > 120) {
                r.backTicks++;
                back = true;
            } else if (diff > 60) {
                r.sideTicks++;
            }
        }
        episode(r, "anda hacia atrás", back, what);
        if (hs > 4) r.event("se teletransporta " + String.format("%.0f", hs) + " bloques [" + what + "]");
        int ground = ground(level, Mth.floor(bp.x), Mth.floor(bp.z));
        double air = bp.y - ground;
        r.maxAir = Math.max(r.maxAir, air);
        if (air > 3) r.airTicks++;
        episode(r, "en el aire", air > 3, what + String.format(" a %.1f", air));
        double home = bp.distanceTo(rift.pos);
        r.maxHome = Math.max(r.maxHome, home);
        episode(r, "fuera de la arena (>40)", home > 40, what);
        episode(r, "sin objetivo", boss.target() == null, what);
        if (anims == null) {
            r.noModelTicks++;
        } else {
            if (anims.size() <= 1) r.noAnimTicks++;
            for (String a : anims) r.anims.merge(a, 1, Integer::sum);
        }
        int minions = level.getEntitiesOfClass(BossEntity.class, boss.getBoundingBox().inflate(64), e -> e != boss && e.isAlive()).size();
        r.minionsMax = Math.max(r.minionsMax, minions);
        if (SHOTS.contains(t)) shot(r, "pelea_" + t / 20 + "s");
        if (t >= FIGHT) kill(level, r);
    }

    private static void kill(ServerLevel level, Rep r) {
        BossEntity b = bossNow(level);
        r.hpEnd = b == null ? -1 : b.getHealth();
        if (b != null) {
            r.event("lo mato (" + (int) b.getHealth() + " de vida)");
            b.suicide();
        }
        phase = 3;
        t = 0;
        relayAt = -1;
        deathFrom = 0;
    }

    private static void death(ServerLevel level, ServerPlayer player, Rep r) {
        // Con relevo (el Mamut del Druida): sale otro jefe; pelea un poco y también se mata (y se mira su muerte)
        BossEntity now = bossNow(level);
        if (now != null && now != boss && now.isAlive()) {
            r.event("sale el relevo " + now.getName().getString());
            boss = now;
            relayAt = t;
        }
        if (relayAt >= 0 && boss != null && boss.isAlive() && t - relayAt > 100) {
            boss.suicide();
            r.event("mato al relevo");
            relayAt = -1;
            deathFrom = t;
        }
        int dt = t - deathFrom;
        if (boss != null && !boss.isRemoved() && dt % 10 == 0) {
            List<String> anims = SkillMobs.animNames(boss);
            if (anims != null) r.deathAnims = String.join(",", anims);
        }
        if (boss != null && !boss.isRemoved() && dt < 200) {
            // Mirando su muerte desde lejos (se ve entero, con sus rayos)
            Vec3 bp = boss.position();
            Vec3 dir = player.position().subtract(bp).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : dir.normalize();
            Vec3 np = bp.add(dir.scale(Math.max(14, boss.getBbWidth() * 1.5 + boss.getBbHeight() * 1.5)));
            int gy = ground(level, Mth.floor(np.x), Mth.floor(np.z));
            Vec3 look = bp.add(0, boss.getBbHeight() * 0.5, 0).subtract(np.x, gy + 1.62, np.z);
            player.teleportTo(level, np.x, gy, np.z, (float) Math.toDegrees(Math.atan2(-look.x, look.z)),
                    (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z))));
        }
        if (dt == 10 || dt == 70 || dt == 130 || dt == 155) shot(r, "muerte_" + dt);
        if (dt == 60) r.barsAfterDeath = clientBars;
        if (dt >= DEATH && relayAt < 0) {
            AABB box = new AABB(arena, arena).inflate(64);
            r.items = level.getEntitiesOfClass(ItemEntity.class, box).size();
            r.xp = level.getEntitiesOfClass(ExperienceOrb.class, box).size();
            r.minionsLeft = level.getEntitiesOfClass(BossEntity.class, box, Entity::isAlive).size();
            r.removedAfterDeath = boss == null || boss.isRemoved();
            next(level);
        }
    }

    /** La altura del suelo en x, z (generando el chunk si aún no está: sin él, la altura sale la del fondo del mundo). */
    private static int ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static final Map<String, Integer> EPISODES = new java.util.HashMap<>();

    /** Apunta cuándo empieza y cuánto dura algo raro (con lo que hacía el jefe al empezar). */
    private static void episode(Rep r, String name, boolean on, String what) {
        Integer since = EPISODES.get(name);
        if (on && since == null) {
            EPISODES.put(name, t);
            r.event(name + " [" + what + "]");
        } else if (!on && since != null) {
            EPISODES.remove(name);
            if (t - since >= 10) r.event("   ... " + name + " " + (t - since) + " ticks");
        }
    }

    private static void shot(Rep r, String what) {
        shotRequest = String.format("%02d_%s_%s", index, r.id, what);
    }

    /** Recoge la arena y pasa al siguiente jefe (o acaba). */
    private static void next(ServerLevel level) {
        if (rift != null) {
            Rifts.remove(rift.id);
            AABB box = new AABB(arena, arena).inflate(96);
            for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> e instanceof Mob || e instanceof ItemEntity
                    || e instanceof ExperienceOrb || e instanceof BossEntity)) {
                e.discard();
            }
        }
        rift = null;
        boss = null;
        EPISODES.clear();
        index++;
        phase = 0;
        t = 0;
        if (index >= ids.size()) {
            current = null;
            write();
            finished = true;
            return;
        }
        int ground = ground(level, 1000 + index * 600, 0);
        arena = new Vec3(1000 + index * 600 + 0.5, ground, 0.5);
        current = new Rep(ids.get(index));
        REPS.add(current);
        TFClient.LOGGER.info("Prueba de jefes: {} ({}/{})", current.id, index + 1, ids.size());
    }

    private static void write() {
        StringBuilder sb = new StringBuilder("PRUEBA DE LOS JEFES EN EL JUEGO (TF Client " + TFClient.VERSION + ")\n\n");
        for (Rep r : REPS) {
            sb.append("== ").append(r.id).append('\n');
            for (String e : r.events) sb.append("   ").append(e).append('\n');
            int ft = Math.max(1, r.fightTicks);
            sb.append(String.format("   pelea: %d ticks; jefes distintos: %d; vida %.0f -> %.0f%n", r.fightTicks, r.bosses.size(), r.hpStart, r.hpEnd));
            sb.append(String.format("   objetivo = el jugador: %d%% del tiempo (cerca: %d%%); golpes que me da: %d (%.1f de daño); los míos: %d%n",
                    100 * r.targetTicks / ft, 100 * r.nearTicks / ft, r.hitsTaken, r.dmgTaken, r.playerAttacks));
            sb.append(String.format("   anda: %d ticks, hacia atrás %d, de lado %d; rápido (>0,8 b/t) %d; teletransportes %d%n",
                    r.movingTicks, r.backTicks, r.sideTicks, r.fastTicks, r.teleports));
            sb.append(String.format("   en el aire (>3 bloques) %d ticks, como mucho %.1f; lo más lejos de la grieta %.1f; esbirros a la vez %d%n",
                    r.airTicks, r.maxAir, r.maxHome, r.minionsMax));
            sb.append(String.format("   sin modelo %d ticks; sin animación %d ticks%n", r.noModelTicks, r.noAnimTicks));
            sb.append("   animaciones (ticks): ").append(r.anims).append('\n');
            sb.append(String.format("   al morir: anims [%s]; barras que ve el cliente a los 3 s: %d; quitado: %s; objetos: %d; experiencia: %d; esbirros vivos: %d%n",
                    r.deathAnims, r.barsAfterDeath, r.removedAfterDeath, r.items, r.xp, r.minionsLeft));
            if (!r.errors.isEmpty()) {
                sb.append("   errores y avisos:\n");
                synchronized (r.errors) {
                    r.errors.forEach((k, v) -> sb.append("      ×").append(v).append(' ').append(k).append('\n'));
                }
            }
            sb.append('\n');
        }
        try {
            Path dir = Path.of("pruebas");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("informe.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TFClient.LOGGER.error("Prueba de jefes: no se pudo escribir el informe", e);
        }
        TFClient.LOGGER.info("Prueba de jefes: terminada\n{}", sb);
    }
}
