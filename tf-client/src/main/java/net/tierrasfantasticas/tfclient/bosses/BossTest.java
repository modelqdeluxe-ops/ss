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

    /** Las capturas pedidas, en orden (las saca BossTestClient). */
    static final java.util.concurrent.ConcurrentLinkedQueue<String> SHOTS_PENDING = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static int shotWait;
    private static boolean voidRescued;
    private static int updateFrom, updateWait;
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
    /** Dónde dejó la prueba al jugador el tick de antes y hasta cuándo le deja moverse solo (le empuja o arrastra el jefe). */
    private static Vec3 placed, throwFrom;
    private static double throwMax, throwTop;
    private static String throwWhat = "";
    private static int freeUntil;
    private static volatile int pushedAt = -10;

    /** SkillMovers: acabó un proyectil o misil de un jefe. */
    public static void projectile(String what, boolean hitPlayer, String info) {
        Rep r = current;
        if (r == null) return;
        int[] c = r.shots.computeIfAbsent(what, k -> new int[2]);
        c[0]++;
        if (hitPlayer) c[1]++;
        else r.missed.putIfAbsent(what, info);
    }

    /** SkillMotion.push ha movido a un jugador (what: la skill y la velocidad). */
    public static void pushed(String what) {
        pushedAt = t;
        lastPush = what;
    }

    private static volatile String lastPush = "";
    private static volatile int bossPushedAt = -100;
    private static int stillFar;
    private static volatile String bossPush = "";

    /** SkillMotion.push ha movido al jefe (velocity, leap, lunge... del pack): lo que ande así no lo decide el motor. */
    public static void selfPushed(Entity e, String skill) {
        if (e != boss) return;
        bossPushedAt = t;
        bossPush = skill;
    }

    /** SkillRuntime.hurt: una skill del jefe le ha dado al jugador (dealt = el daño que entró; 0 = no entró). */
    public static void skillHit(String skill, float dealt) {
        Rep r = current;
        if (r == null) return;
        float[] c = r.skillHits.computeIfAbsent(skill, k -> new float[3]);
        c[0]++;
        if (dealt > 0) {
            c[1]++;
            c[2] += dealt;
        }
    }
    /** Tick (de la fase de muerte) en que salió el relevo (-1 = ninguno pendiente) y desde el que se mira la muerte. */
    private static int relayAt = -1, deathFrom, lastState = -1;

    private BossTest() {}

    /** Lo que se ha visto de un jefe. */
    static final class Rep {
        final java.util.Set<String> stuck = new java.util.HashSet<>();
        /** Proyectiles y misiles del jefe: qué → {lanzados, tocan al jugador}, y cómo acabó uno que no tocó. */
        final Map<String, int[]> shots = new TreeMap<>();
        /** Por skill: veces que le da al jugador, las que le hacen daño y cuánto. */
        final Map<String, float[]> skillHits = new TreeMap<>();
        final Map<String, String> missed = new java.util.HashMap<>();
        /** Lo más lejos y alto que le ha lanzado un golpe del jefe. */
        double throwFar, throwHigh;
        final String id;
        final List<String> events = new ArrayList<>();
        final Set<UUID> bosses = new LinkedHashSet<>();
        final Map<String, Integer> anims = new TreeMap<>();
        final Map<String, Integer> errors = new LinkedHashMap<>();
        int fightTicks, targetTicks, nearTicks, movingTicks, backTicks, sideTicks, airTicks, fastTicks, teleports;
        /** Lo más que tuvo el cliente de efectos con objeto (item_display: llamas de Ignevar) y cómo estaban. */
        int clientItemMax;
        String clientItemInfo = "";
        /** Lo que anda en el suelo (bloques) y en cuántos ticks: su velocidad media al andar (1.3.89, «Ignevar es lento»). */
        double groundDist;
        int groundTicks;
        int noModelTicks, noAnimTicks, hitsTaken, playerAttacks, minionsMax, moved, backLocked, sideLocked, backPushed, sidePushed, landed, noEffect;
        double minDist = 1e9;
        int stillFarTicks, stillFarShown, flameShown;
        boolean flameSeen;
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
            // (1.3.88) «vacio»: la visita a The Void (el caballero, su diálogo, el viaje, el cielo y la isla), la primera
            ids.add(0, "vacio");
            // (1.3.96) «panoramas»: captura el Nether, el End y el Void (para el agujero de gusano); solo si se pide
            if (only != null && List.of(only.split(",")).contains("panoramas")) ids.add(0, "panoramas");
            // (1.3.96) «fauna»: las criaturas del Páramo del Void, una a una y luego cómo salen solas; solo si se pide
            if (only != null && List.of(only.split(",")).contains("fauna")) ids.add(0, "fauna");
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
        Rep r = current;
        // En el Void la prueba espera a que salga cada captura: con shaders el cliente va a pocos fotogramas por
        // segundo y la vista siguiente (o quitar el cielo propio) llegaba antes que la foto (10 s como mucho)
        if (SHOTS_PENDING.isEmpty()) shotWait = 0;
        boolean waitShot = ("vacio".equals(r.id) || "fauna".equals(r.id)) && !SHOTS_PENDING.isEmpty() && shotWait++ < 200;
        if (!waitShot) t++;
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        if ("vacio".equals(r.id)) {
            if (!waitShot) voidStep(server, level, player, r);
            return;
        }
        if ("panoramas".equals(r.id)) {
            panoramaStep(server, level, player, r);
            return;
        }
        if ("fauna".equals(r.id)) {
            if (!waitShot) faunaStep(server, level, player, r);
            return;
        }
        switch (phase) {
            case 0 -> {
                // Llegar a la arena (dejar que se carguen los chunks) y poner la grieta en medio
                if (t == 1) player.teleportTo(level, arena.x, arena.y, arena.z, 0F, 0F);
                if (t == 40) {
                    rift = Rifts.place(player, r.id, Difficulty.NORMAL);
                    // mirándola (hacia +z) y un poco hacia arriba: la grieta entera (1.3.87: más grande)
                    player.teleportTo(level, arena.x, arena.y, arena.z - 34, 0F, -17F);
                }
                if (t == 175) {
                    shot(r, "grieta_lista");
                    // (solo en el cliente del juego de un jugador, que es el de la prueba)
                    r.event("música del vacío fuera del Void: " + (RiftSky.musicWanted() ? "SUENA (mal)" : "callada (bien)"));
                }
                if (t == 180) {
                    lastState = rift.state;
                    rift.state = RiftEntity.WAKING;
                    rift.wakeAt = level.getGameTime() + RiftEntity.WAKE_TICKS;
                    phase = 1;
                    t = 0;
                }
            }
            case 1 -> {
                if (t == 55) shot(r, "grieta_despierta");
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
        if (t % 40 == 0) {
            // Efectos que no se quitan (las llamas de texto del Esqueleto viven 20 ticks): más de 15 s, es que se atascan
            for (String old : net.tierrasfantasticas.tfclient.skills.SkillMobs.oldActors(300)) {
                String key = old.replaceAll(" \\d+t", "").replaceAll(" \\(ahora \\d+\\)", "");
                if (r.stuck.add(key) && r.stuck.size() <= 8) r.event("efecto que no se quita: " + old);
            }
        }
        Vec3 bp = boss.position();
        // El jugador: cambia de distancia cada 5 s, le rodea despacio, le mira y le pega si llega
        if (t % 100 == 1) {
            wantDist = new int[] {3, 6, 10, 16, 4, 8}[(t / 100) % 6];
            side = -side;
        }
        // (para ver entero el despertar del Guardián Hueco, que despierta a menos de 8 bloques)
        if (r.id.equals("guardian_hueco") && t < 160) wantDist = 6;
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
        // Si algo del jefe le ha movido (un empujón, el rayo tractor que le arrastra hasta su boca: SkillMotion.push avisa),
        // le deja ir 1,5 s: si no, la prueba le devolvía a su sitio cada tick y esos ataques no llegaban nunca a nada (la
        // Tormenta del Wither). (Mientras se le teletransporta, el servidor no hace caso de lo que se mueve el cliente.)
        if (pushedAt == t || pushedAt == t - 1 || placed != null && !view && player.position().distanceTo(placed) > 0.5) {
            if (t >= freeUntil) {
                r.moved++;
                throwFrom = player.position();
                throwWhat = SkillMobs.animNames(boss) == null ? "" : String.join(",", SkillMobs.animNames(boss));
                throwMax = 0;
                throwTop = 0;
                throwWhat += " | " + lastPush + effects(player);
            }
            freeUntil = t + 30;
        }
        if (t < freeUntil && throwFrom != null) {
            Vec3 dd = player.position().subtract(throwFrom);
            throwMax = Math.max(throwMax, Math.sqrt(dd.x * dd.x + dd.z * dd.z));
            throwTop = Math.max(throwTop, dd.y);
            r.throwFar = Math.max(r.throwFar, throwMax);
            r.throwHigh = Math.max(r.throwHigh, throwTop);
        } else if (throwFrom != null) {
            if (throwMax > 10 || throwTop > 6) r.event(String.format("me lanza %.0f bloques y %.0f de alto [%s]", throwMax, throwTop, throwWhat));
            throwFrom = null;
        }
        boolean free = t < freeUntil && !view;
        if (!player.isPassenger() && !free) player.teleportTo(level, np.x, gy, np.z, yaw, pitch);
        placed = player.position();
        if (!view && d < 3.5 + boss.getBbWidth() / 2 && t % 12 == 0) {
            float before = boss.getHealth();
            int inv = boss.invulnerableTime;
            player.attack(boss);
            player.resetAttackStrengthTicker();
            r.playerAttacks++;
            // ¿Le hace daño? (el Mamut del Druida «no le puedo hacer más daño»)
            if (boss.getHealth() < before) r.landed++;
            else if (inv <= 10) r.noEffect++;
        }
        // Lo que hace el jefe
        if (boss.target() == player) r.targetTicks++;
        if (d < 30) r.nearTicks++;
        if (!view) r.minDist = Math.min(r.minDist, d - boss.getBbWidth() / 2);
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
        boolean back = false, side = false, lockedNow = false, pushedNow = false;
        if (clientItemCount > r.clientItemMax) {
            r.clientItemMax = clientItemCount;
            r.clientItemInfo = clientItems;
        }
        if (hs > 0.03 && hs <= 4 && boss.onGround()) {
            r.groundDist += hs;
            r.groundTicks++;
        }
        if (hs > 0.03 && hs <= 4) {
            r.movingTicks++;
            float go = (float) Math.toDegrees(Math.atan2(-mv.x, mv.z));
            float diff = Math.abs(Mth.wrapDegrees(go - boss.yBodyRot));
            // Con el modelo bloqueado por el pack (lockmodel) el cuerpo no gira: eso es de ModelEngine; se cuenta aparte
            boolean locked = boss.motion()[2] != 0;
            // Lo empuja una skill de su pack (velocity{z=-1} del Archivista: un saltito atrás): tampoco es del motor
            pushedNow = t - bossPushedAt <= 20;
            if (diff > 120) {
                r.backTicks++;
                if (locked) r.backLocked++;
                if (pushedNow) r.backPushed++;
                back = true;
            } else if (diff > 60) {
                r.sideTicks++;
                if (locked) r.sideLocked++;
                if (pushedNow) r.sidePushed++;
                side = true;
            }
            lockedNow = locked;
        }
        String why = String.format(" a %.1f del jugador%s%s%s", d, boss.hurtTime > 0 ? ", le acaban de pegar" : "",
                lockedNow ? ", modelo bloqueado (lockmodel)" : "", pushedNow ? ", lo empuja su skill " + bossPush : "");
        if (back || side) {
            // Por qué: hacia dónde va, hacia dónde mira el cuerpo y a qué punto del camino de Minecraft va
            float go = (float) Math.toDegrees(Math.atan2(-mv.x, mv.z));
            net.minecraft.world.level.pathfinder.Path path = boss.getNavigation().getPath();
            String node = "sin camino";
            if (path != null && !path.isDone()) {
                net.minecraft.core.BlockPos n = path.getNextNodePos();
                Vec3 to = Vec3.atBottomCenterOf(n).subtract(bp);
                node = String.format("nodo %d/%d a %.1f, %.0f°", path.getNextNodeIndex(), path.getNodeCount(),
                        Math.sqrt(to.x * to.x + to.z * to.z), Mth.wrapDegrees(Math.toDegrees(Math.atan2(-to.x, to.z)) - boss.yBodyRot));
            }
            Vec3 tp = player.position().subtract(bp);
            why += String.format("; va a %.0f° del cuerpo, %.2f b/t, jugador a %.0f°, %s", Mth.wrapDegrees(go - boss.yBodyRot), hs,
                    Mth.wrapDegrees(Math.toDegrees(Math.atan2(-tp.x, tp.z)) - boss.yBodyRot), node);
        }
        // Quieto y lejos del jugador más de 2 s («se queda quieto lejos y ataca sin sentido», Ignevar): por qué no anda
        if (d > 10 + boss.getBbWidth() / 2 && hs < 0.03) {
            if (++stillFar == 40 && r.stillFarShown++ < 6) {
                r.event(String.format("quieto lejos (a %.1f) [%s] suelo=%s %s", d, what, boss.onGround(),
                        net.tierrasfantasticas.tfclient.skills.SkillRuntime.debugVars(boss)));
            }
            if (stillFar >= 40) r.stillFarTicks++;
        } else {
            stillFar = 0;
        }
        // Las llamas de Ignevar («veo invisibles sus llamas»): una captura cuando el cliente ya tiene varias
        if (anims != null && !r.flameSeen && clientItemCount >= 4 && anims.stream().anyMatch(a -> a.contains("breath") || a.contains("flame"))) {
            r.flameSeen = true;
            r.event("llamas en el cliente: " + clientItems);
            shot(r, "llamas");
        }
        // Lanzallamas (Ignevar: «ataca sin sentido», sus llamas no daban): hacia dónde apunta, 3 veces por pelea
        if (anims != null && r.flameShown < 3 && t % 10 == 0 && anims.stream().anyMatch(a -> a.contains("breath") || a.contains("flame"))) {
            net.tierrasfantasticas.tfclient.skills.SkillMobs.Host hh = net.tierrasfantasticas.tfclient.skills.SkillMobs.host(boss);
            Vec3 jaw = hh == null ? null : net.tierrasfantasticas.tfclient.skills.SkillMobs.bonePos(hh, "jaw");
            Vec3 aim = hh == null ? null : net.tierrasfantasticas.tfclient.skills.SkillMobs.bonePos(hh, "flametarget");
            if (jaw != null && aim != null) {
                r.flameShown++;
                Vec3 dj = aim.subtract(jaw);
                float aimYaw = (float) Math.toDegrees(Math.atan2(-dj.x, dj.z));
                Vec3 tp = player.position().subtract(jaw);
                r.event(String.format("lanzallamas [%s]: boca %.1f %.1f %.1f (sobre el suelo %.1f), apunta a %.1f bloques, %.0f° (cuerpo %.0f°, jugador %.0f°), baja %.1f; jugador a %.1f; en el cliente: %s",
                        what, jaw.x - bp.x, jaw.y - bp.y, jaw.z - bp.z, jaw.y - bp.y, dj.length(), aimYaw, boss.yBodyRot,
                        Math.toDegrees(Math.atan2(-tp.x, tp.z)), -dj.y, Math.sqrt(tp.x * tp.x + tp.z * tp.z), clientItems));
                if (r.flameShown == 2) shot(r, "lanzallamas");
            }
        }
        episode(r, "anda hacia atrás", back, what + why);
        episode(r, "anda de lado", side, what + why);
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
        // Cómo aparece (el Guardián Hueco: «sale completo y luego se cae a pedazos»): sus primeros ticks
        if (t == 1 || t == 4 || t == 10) shot(r, "aparece_t" + t);
        // El despertar del Guardián Hueco (sale de pie, se cae y se monta, decía el dueño): cada 10 ticks
        if (r.id.equals("guardian_hueco") && t >= 30 && t <= 150 && t % 10 == 0) shot(r, String.format("despierta_t%03d", t));
        if (t == 200) {
            r.event("música en la pelea: grieta " + (RiftSky.musicWanted() ? "SUENA (mal)" : "callada")
                    + ", jefe " + (net.tierrasfantasticas.tfclient.client.TFBossMusic.isActive() ? "suena" : "no suena"));
        }
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
            // Sale el relevo: pelea con él entera, con su propio informe (el Mamut del Druida tenía fallos que la prueba
            // no veía porque lo mataba en cuanto salía)
            r.event("sale el relevo " + now.getName().getString() + " (se pelea con él: " + r.id + "_relevo)");
            Rep rr = new Rep(r.id + "_relevo");
            REPS.add(rr);
            current = rr;
            boss = now;
            rr.hpStart = now.getHealth();
            rr.event("sale (" + (int) now.getMaxHealth() + " de vida)");
            lastPos = now.position();
            relayAt = -1;
            EPISODES.clear();
            phase = 2;
            t = 0;
            return;
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

    /** Los efectos del jugador (si alguno pasa de nivel 10: el «Salto 128» era un supersalto). */
    private static String effects(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.world.effect.MobEffectInstance e : p.getActiveEffects()) {
            sb.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect()).getPath())
                    .append(' ').append(e.getAmplifier());
        }
        if (p.getTicksFrozen() > 0) sb.append(" congelado ").append(p.getTicksFrozen());
        if (p.isPassenger()) sb.append(" montado");
        return sb.length() == 0 ? "" : " | efectos:" + sb;
    }

    /** Los efectos con objeto que tiene el cliente (item_display: las llamas de Ignevar...), para el informe. */
    static volatile String clientItems = "?";
    static volatile int clientItemCount;

    /** Lo que el cliente de la prueba pulsa en el diálogo del caballero (1 = la primera respuesta; -1 = nada). */
    static volatile int dialogPress = -1;
    private static net.tierrasfantasticas.tfclient.vacio.KnightEntity knight;
    private static Vec3 origin;
    /** La persecución por la isla (1.3.92): dónde espera el jugador, lo más cerca que llega el jefe y lo más cerca del borde. */
    private static Vec3 chaseSpot;
    private static double chaseNear;
    private static float chaseEdge;
    private static boolean chaseFell;
    /** (1.3.96) Los viajes del Portal del Alba antes de cada paso de su prueba. */
    private static int portalTrips0;

    /**
     * La visita a The Void (1.3.88): pone un caballero de luz delante del jugador, le habla (saludo, «¿Qué es el
     * Void?», «Llévame al Void»), viaja con la animación, mira el cielo y la isla desde varios sitios, prueba el rescate
     * al caer al vacío, habla con el caballero del santuario y vuelve. Capturas de cada paso.
     */
    /** (1.3.96) Lo que dura la prueba de la luz del Alba (lightStep), metida tras la persecución. */
    private static final int LIGHT_TICKS = 720;
    /** Cuándo empieza (en ticks de la prueba). */
    private static final int LIGHT_AT = 1815 + 80;

    /**
     * (1.3.96) El reloj de la prueba del Void con los tiempos de antes: el viaje por el agujero de gusano dura ahora 230
     * ticks (antes 150), así que lo de después de la ida va 80 ticks más tarde y lo de después de la vuelta, 160; y tras
     * la persecución se mete la prueba de la luz ({@link #LIGHT_TICKS}). −1 mientras no toca nada (el viaje de vuelta).
     */
    private static int voidClock(int t) {
        if (t < 480) return t;
        if (t < LIGHT_AT) return t - 80;
        if (t < LIGHT_AT + LIGHT_TICKS) return -1;
        int s = t - 80 - LIGHT_TICKS;
        if (s < 2195) return s;
        return s < 2195 + 80 ? -1 : s - 80;
    }

    private static void voidStep(MinecraftServer server, ServerLevel level, ServerPlayer player, Rep r) {
        ServerLevel vl = server.getLevel(net.tierrasfantasticas.tfclient.vacio.TFVoid.VOID);
        if (t >= LIGHT_AT && t < LIGHT_AT + LIGHT_TICKS) {
            lightStep(vl, player, r, t - LIGHT_AT);
            return;
        }
        int s = voidClock(t);
        // Caer al vacío (se le tira en el tick 2030): ¿vuelve al santuario? (el teletransporte puede tardar unos ticks)
        if (s > 2030 && s < 2060 && !voidRescued) {
            if (player.getY() > 50) {
                voidRescued = true;
                r.event(String.format("caer al vacío: rescatado a los %d ticks (queda en %.1f %.1f %.1f)", s - 2030, player.getX(),
                        player.getY(), player.getZ()));
            } else if (s == 2059) {
                r.event(String.format("caer al vacío: NO RESCATADO (sigue en %.1f %.1f %.1f)", player.getX(), player.getY(), player.getZ()));
            }
        }
        if (s > 1200 && s < 1800 && chaseSpot != null && s % 5 == 0) {
            for (BossEntity b0 : vl.getEntitiesOfClass(BossEntity.class, new AABB(net.minecraft.core.BlockPos.containing(chaseSpot)).inflate(300))) {
                if (!b0.isMainBoss()) continue;
                chaseNear = Math.min(chaseNear, Math.sqrt(b0.distanceToSqr(player)));
                chaseEdge = Math.min(chaseEdge, net.tierrasfantasticas.tfclient.vacio.VoidIsland.edgeDistance(b0.getX(), b0.getZ()));
                if (b0.getY() < 40) chaseFell = true;
            }
        }
        switch (s) {
            case 1 -> {
                voidRescued = false;
                if (vl == null) {
                    r.event("NO HAY DIMENSIÓN tfclient:void");
                    next(level);
                    return;
                }
                player.teleportTo(level, arena.x, arena.y, arena.z, 180F, 0F);
            }
            case 30 -> {
                knight = net.tierrasfantasticas.tfclient.vacio.TFVoid.KNIGHT.get().create(level);
                knight.moveTo(arena.x, arena.y, arena.z - 4, 0F, 0F);
                knight.setYHeadRot(0F);
                knight.setYBodyRot(0F);
                level.addFreshEntity(knight);
                origin = player.position();
            }
            case 70 -> shot(r, "caballero_mundo");
            case 80 -> net.tierrasfantasticas.tfclient.vacio.VoidTravel.testTalk(player, knight);
            case 200 -> shot(r, "dialogo_saludo");
            case 210 -> dialogPress = 1;
            case 340 -> shot(r, "dialogo_lore");
            case 350 -> dialogPress = 0;
            // (1.3.91) el viaje por el agujero de gusano dura 150 ticks: sus capturas las saca el cliente a los tiempos
            // del túnel (BossTestClient: ida_tunel_*, vuelta_tunel_*); desde aquí, todo 120 ticks más tarde que antes
            case 560 -> {
                r.event("tras el viaje: dimensión " + player.level().dimension().location() + String.format(" en %.1f %.1f %.1f",
                        player.getX(), player.getY(), player.getZ()) + "; agujero de gusano: "
                        + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.travel());
                shot(r, "viaje_llegada");
            }
            case 640 -> {
                BlockPosHelper.report(r, player);
                r.event("cielo del vacío (shader): " + (net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.skyShaderLoaded() ? "cargado" : "NO CARGADO")
                        + "; música del vacío: " + (RiftSky.musicWanted() ? "suena" : "NO SUENA")
                        + "; shaders: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.shaders());
                shot(r, "vacio_llegada");
            }
            case 650 -> {
                Vec3 ar = net.tierrasfantasticas.tfclient.vacio.VoidIsland.arrivePos();
                player.teleportTo(vl, ar.x, ar.y, ar.z, net.tierrasfantasticas.tfclient.vacio.VoidIsland.arriveYaw(), -24F);
            }
            case 720 -> shot(r, "vacio_agujero");
            // (1.3.96) el santuario: desde el Puente de Luz (fuera del Velo del Alba, mirando al templo y al Corazón) y por
            // dentro, con el caballero
            case 730 -> {
                net.minecraft.core.BlockPos k = net.tierrasfantasticas.tfclient.vacio.VoidIsland.knightPos();
                // (1.3.96: el santuario está más al norte y es más grande: desde el principio del Puente de Luz, fuera del Velo)
                player.teleportTo(vl, k.getX() + 0.5, k.getY() - 1, k.getZ() + 54.5, 180F, -10F);
            }
            case 800 -> {
                r.event("el santuario desde el puente: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player) + "; "
                        + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sanctuary());
                shot(r, "vacio_santuario");
            }
            case 810 -> {
                net.minecraft.core.BlockPos k = net.tierrasfantasticas.tfclient.vacio.VoidIsland.knightPos();
                player.teleportTo(vl, k.getX() + 0.5, k.getY() - 1, k.getZ() + 4.5, 180F, -12F);
            }
            case 870 -> {
                r.event("dentro del Velo del Alba: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player));
                shot(r, "vacio_caballero");
            }
            // la grieta del Void en el centro de la plaza
            case 880 -> {
                net.minecraft.core.BlockPos g = net.tierrasfantasticas.tfclient.vacio.VoidIsland.riftPos();
                player.teleportTo(vl, g.getX() + 0.5, g.getY(), g.getZ() - 17.5, 0F, -14F);
            }
            case 950 -> {
                Rifts.Rift vr = Rifts.voidRift();
                r.event("grieta del Void: " + (vr == null ? "NO EXISTE" : (vr.kind == RiftEntity.MOTHER ? "madre" : "normal") + ", espera a «"
                        + vr.boss + "» (" + vr.diff.label() + "); listas: " + Rifts.voidConfig().pools.get(0).size() + " normal, "
                        + Rifts.voidConfig().pools.get(1).size() + " madre; entidad: "
                        + (vl.getEntitiesOfClass(RiftEntity.class, new AABB(player.blockPosition()).inflate(40)).isEmpty() ? "NO ESTÁ" : "está")));
                shot(r, "vacio_grieta");
            }
            case 960 -> {
                net.minecraft.core.BlockPos g = net.tierrasfantasticas.tfclient.vacio.VoidIsland.riftPos();
                player.teleportTo(vl, g.getX() + 3.0, g.getY() + 3, g.getZ() - 5.0, 20F, -28F);
            }
            case 1020 -> shot(r, "vacio_grieta_cerca");
            case 1025 -> r.event(Rifts.voidSetActive(RiftEntity.MOTHER));
            case 1030 -> {
                net.minecraft.core.BlockPos g = net.tierrasfantasticas.tfclient.vacio.VoidIsland.riftPos();
                player.teleportTo(vl, g.getX() + 0.5, g.getY(), g.getZ() - 26.5, 0F, -14F);
            }
            case 1100 -> {
                Rifts.Rift vr = Rifts.voidRift();
                r.event("grieta madre: espera a «" + (vr == null ? "?" : vr.boss) + "»");
                shot(r, "vacio_madre");
            }
            case 1110 -> {
                r.event(Rifts.voidSetActive(RiftEntity.NORMAL));
                // (1.3.92) para la persecución por la isla, uno que anda y persigue (la Abominación)
                Rifts.Rift vr0 = Rifts.voidRift();
                if (vr0 != null && net.tierrasfantasticas.tfclient.skills.SkillDefs.bosses().containsKey("abominacion")) vr0.boss = "abominacion";
                r.event("despertar la grieta del Void: " + (Rifts.testWake() ? "despierta" : "NO SE PUDO"));
            }
            case 1195 -> {
                var bosses = vl.getEntitiesOfClass(BossEntity.class, new AABB(player.blockPosition()).inflate(48));
                Rifts.Rift vr = Rifts.voidRift();
                r.event("de la grieta del Void sale: " + (bosses.isEmpty() ? "NADIE" : bosses.get(0).cls() == null ? "?" : bosses.get(0).cls().id)
                        + " (estado " + (vr == null ? -1 : vr.state) + ")");
                shot(r, "vacio_grieta_jefe");
            }
            // (1.3.92) Persecución por la isla: el jugador se va al borde del este (a 2 bloques del vacío) y el jefe tiene que
            // ir a por él por toda la isla, pero quedarse a VoidIsland.EDGE (6) bloques del borde, sin caerse
            case 1200 -> {
                double x = 0.5;
                while (x < 400 && net.tierrasfantasticas.tfclient.vacio.VoidIsland.edgeDistance(x + 1, 0.5) > 2.5F) x += 1;
                int y = vl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), 0);
                chaseSpot = new Vec3(x, y, 0.5);
                chaseNear = 1e9;
                chaseEdge = 1e9F;
                chaseFell = false;
                player.teleportTo(vl, x, y, 0.5, 270F, 10F);
                r.event(String.format("persecución por la isla: el jugador al borde del este, en %.0f %d 0 (a %.0f bloques de la grieta, a %.1f del vacío)",
                        x, y, x, net.tierrasfantasticas.tfclient.vacio.VoidIsland.edgeDistance(x, 0.5)));
            }
            case 1500 -> shot(r, "persecucion");
            case 1800 -> {
                var bosses = vl.getEntitiesOfClass(BossEntity.class, new AABB(net.minecraft.core.BlockPos.containing(chaseSpot)).inflate(300));
                BossEntity b0 = bosses.isEmpty() ? null : bosses.get(0);
                r.event(String.format("persecución por la isla: el jefe llega a %.1f bloques del jugador; lo más cerca del borde: %.1f bloques (límite %.0f); %s",
                        chaseNear, chaseEdge, net.tierrasfantasticas.tfclient.vacio.VoidIsland.EDGE,
                        chaseFell ? "SE CAE AL VACÍO" : b0 == null ? "NO ESTÁ" : String.format("sigue en la isla (%.0f %.0f %.0f)", b0.getX(), b0.getY(), b0.getZ())));
                player.teleportTo(vl, 0.5, 73, -26.5, 0F, -14F);
            }
            case 1815 -> r.event("reiniciar la grieta del Void: " + (Rifts.reset(Rifts.VOID_ID) ? "lista" : "NO"));
            // (las vistas desde el aire, volando: en la 1.3.89 se caía al vacío antes de la captura y salía el rescate)
            // (1.3.90: la isla ×1,5 y centrada en la grieta; con la vista de 6 chunks de la prueba se ve hasta ~96 bloques)
            case 1830 -> {
                fly(player, true);
                player.teleportTo(vl, 0.5, 165, 70.5, 180F, 48F);
            }
            case 1910 -> shot(r, "vacio_isla");
            case 1915 -> player.teleportTo(vl, 0.5, 200, 0.5, 180F, 90F);
            case 1955 -> shot(r, "vacio_planta");
            case 1960 -> player.teleportTo(vl, 175.5, 88, 30.5, 110F, 8F);
            case 1990 -> shot(r, "vacio_lado");
            case 2029 -> fly(player, false);
            case 2030 -> player.teleportTo(vl, 0.5, 1.0, 0.5, 0F, 0F);
            case 2060 -> {
                // (1.3.96: el santuario es más grande y el rescate deja en su puerta, a ~28 bloques de Caelthar)
                var homes = vl.getEntitiesOfClass(net.tierrasfantasticas.tfclient.vacio.KnightEntity.class,
                        new AABB(player.blockPosition()).inflate(48), net.tierrasfantasticas.tfclient.vacio.KnightEntity::home);
                r.event("caballero del santuario: " + (homes.isEmpty() ? "NO ESTÁ" : "está (" + homes.size() + ")"));
                if (!homes.isEmpty()) {
                    // (1.3.96: el rescate deja a la entrada del santuario, a ~15 bloques: el diálogo se cierra a más de 14 y
                    // el servidor no acepta la elección a más de 12; se habla delante de él, como un jugador)
                    net.tierrasfantasticas.tfclient.vacio.KnightEntity hk = homes.get(0);
                    player.teleportTo(vl, hk.getX(), hk.getY() - 1, hk.getZ() + 4, 180F, -8F);
                    net.tierrasfantasticas.tfclient.vacio.VoidTravel.testTalk(player, hk);
                }
            }
            case 2180 -> shot(r, "dialogo_santuario");
            case 2190 -> dialogPress = 0;
            // (1.3.90) un mundo con la isla de antes: se pone al día solo (el servidor del dueño)
            case 2200 -> {
                net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.pretendOldIsland(vl);
                updateFrom = t;
            }
            case 2410 -> {
                double d = origin == null ? -1 : player.position().distanceTo(origin);
                r.event("de vuelta: dimensión " + player.level().dimension().location() + String.format(" a %.1f bloques de donde salió", d)
                        + (player.level() == level && d < 3 ? "" : " (MAL)") + "; agujero de gusano: "
                        + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.travel());
                shot(r, "vuelta");
            }
            case 2450 -> {
                // espera a que la isla esté al día (como mucho 4000 ticks)
                if (!net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.islandUpdated() && updateWait++ < 4000) {
                    t--;
                    return;
                }
                net.minecraft.core.BlockPos k = net.tierrasfantasticas.tfclient.vacio.VoidIsland.knightPos().below();
                r.event("isla de antes puesta al día: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.islandUpdate()
                        + " en " + (t - updateFrom + updateWait) + " ticks; pedestal del caballero: "
                        + (net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.islandMatches(vl, k) ? "bien" : "MAL")
                        + "; columna de piedra de la plaza: "
                        + (vl.getBlockState(new net.minecraft.core.BlockPos(6, 75, 6)).isAir() ? "quitada" : "SIGUE"));
                updateWait = 0;
                // (1.3.93) la grieta del cielo: desde junto a Caelthar, mirándola
                net.tierrasfantasticas.tfclient.vacio.SkyRift.testReset();
                fly(player, true);
                skyWatch(level, player, 0, 40);
            }
            case 2470 -> {
                net.tierrasfantasticas.tfclient.vacio.SkyRift.start(level, knight.position());
                r.event("la grieta del cielo: empieza el evento sobre Caelthar");
            }
            case 2600 -> r.event("grieta del cielo, en el preludio: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sky());
            // (1.3.93b: el preludio dura 12 s; se rompe a los 240 ticks, y sus capturas siguen 10 s más)
            case 2720 -> r.event("grieta del cielo, rota: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sky());
            // (el diálogo, cuando ya está abierta, y 3 s después de llegar junto a Caelthar)
            case 2945 -> {
                // (si el diálogo del santuario no llegó a abrirse, su «pulsa 1» seguía pendiente y aquí elegía «Llévame
                // al Void»)
                dialogPress = -1;
                fly(player, false);
                player.teleportTo(level, knight.getX(), knight.getY(), knight.getZ() + 4, 180F, -4F);
            }
            case 3005 -> {
                // (mientras se estaba en el Void su chunk se descargó: al volver es otra entidad, con otro número, y
                // hablar con la de antes no abría nada en el cliente)
                var ks = level.getEntitiesOfClass(net.tierrasfantasticas.tfclient.vacio.KnightEntity.class,
                        new AABB(knight.blockPosition()).inflate(8), k -> !k.home());
                if (!ks.isEmpty()) knight = ks.get(0);
                r.event("Caelthar para el diálogo de la grieta del cielo: " + (ks.isEmpty() ? "NO ESTÁ" : "está"));
                net.tierrasfantasticas.tfclient.vacio.VoidTravel.testTalk(player, knight);
            }
            case 3125 -> shot(r, "dialogo_cielo");
            case 3135 -> dialogPress = 1;
            case 3245 -> shot(r, "dialogo_grieta");
            case 3255 -> dialogPress = 1;
            case 3375 -> shot(r, "dialogo_sellar");
            case 3385 -> dialogPress = 2;
            // (1.3.93b) a 2000 bloques: la grieta está en el mismo sitio del cielo y del mismo tamaño (como el sol)
            case 3390 -> {
                fly(player, true);
                skyWatch(level, player, 2000, 600);
            }
            case 3470 -> shot(r, "cielo_lejos");
            case 3480 -> skyWatch(level, player, 0, 40);
            // cae un jefe del Void: se sella; y se vuelve a abrir sola cuando la grieta del Void tiene otro jefe listo (30 s)
            case 3520 -> net.tierrasfantasticas.tfclient.vacio.SkyRift.voidBossKilled();
            case 3640 -> r.event("grieta del cielo, al caer un jefe del Void: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sky());
            case 4210 -> r.event("grieta del cielo, con la grieta del Void otra vez lista: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sky());
            case 4300 -> net.tierrasfantasticas.tfclient.vacio.SkyRift.testEnd();
            case 4460 -> {
                r.event("grieta del cielo, el final: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sky());
                net.tierrasfantasticas.tfclient.vacio.SkyRift.testReset();
                fly(player, false);
                // (al alejarse 600 bloques su chunk se descargó: el que hay ahora es otra entidad)
                for (net.tierrasfantasticas.tfclient.vacio.KnightEntity k : level.getEntitiesOfClass(net.tierrasfantasticas.tfclient.vacio.KnightEntity.class,
                        new AABB(knight.blockPosition()).inflate(16))) {
                    k.discard();
                }
                knight.discard();
                knight = null;
                next(level);
            }
            default -> {
            }
        }
    }

    /**
     * (1.3.96) La corrosión del Vacío y lo que la quita, tras la persecución: un Pozo de Luz del Páramo (verlo, entrar en
     * él sin luz: se limpia y se oye su eco), la corrosión alta y al máximo (medio corazón cada 3 s), la armadura del Vacío
     * (5 jefes, recogerla, ponérsela: no corroe), su soporte en el Altar del santuario y el Portal del Alba (de la isla de
     * la grieta al santuario). La vida del jugador de la prueba se rellena cada tick: la corrosión se cuenta por sus golpes.
     */
    private static void lightStep(ServerLevel vl, ServerPlayer player, Rep r, int k) {
        java.util.List<net.minecraft.core.BlockPos> shards = net.tierrasfantasticas.tfclient.vacio.VoidIsland.shards();
        // (un pozo del Páramo: los 10 primeros son los de la isla de la grieta)
        net.minecraft.core.BlockPos f = shards.size() > 10 ? shards.get(10) : shards.isEmpty() ? null : shards.get(0);
        switch (k) {
            case 0 -> {
                r.event("corrosión tras la persecución (30 s fuera de la luz): " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player)
                        + "; " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.shards(vl));
                if (f != null) viewSpot(vl, player, r, Vec3.atCenterOf(f), "el pozo de luz " + (shards.indexOf(f)));
            }
            // (7,5 s: con shaders el cliente va a ~4 fotogramas por segundo y Minecraft no dibuja las entidades de un trozo
            // de mundo que aún no ha construido)
            case 150 -> shot(r, "vacio_pozo");
            case 155 -> fly(player, false);
            case 160 -> {
                // sin luz, dentro del círculo del pozo
                net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.setLight(player, 0F);
                if (f != null) player.teleportTo(vl, f.getX() + 0.5, f.getY() - 1, f.getZ() + 2.5, 180F, 20F);
            }
            case 200 -> {
                r.event("en un pozo de luz, sin luz, 2 s: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player));
                shot(r, "vacio_pozo_dentro");
            }
            case 210 -> {
                if (f != null) player.teleportTo(vl, f.getX() + 9.5, f.getY() - 1, f.getZ() + 0.5, 90F, 0F);
                net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.setLight(player, 30F);
            }
            case 260 -> {
                r.event("corrosión alta (luz 30): " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player));
                shot(r, "vacio_corrosion");
            }
            case 270 -> net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.setLight(player, 0F);
            case 400 -> {
                r.event("corrosión al máximo (6,5 s): " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player));
                shot(r, "vacio_corrosion_max");
            }
            // la armadura del Vacío: 5 jefes, recogerla y ponérsela
            case 410 -> {
                r.event("armadura del Vacío con 4 jefes: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.armor(player, 4));
                r.event("armadura del Vacío con 5 jefes: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.armor(player, 5));
                net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.wearArmor(player, true);
                net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.setLight(player, 50F);
            }
            case 470 -> {
                r.event("con la armadura del Vacío puesta, 3 s fuera de la luz: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player));
                net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.wearArmor(player, false);
                // el Altar del Vacío del santuario, con su soporte
                net.minecraft.core.BlockPos a = net.tierrasfantasticas.tfclient.vacio.VoidIsland.altarPos();
                if (a != null) player.teleportTo(vl, a.getX() + 0.5, a.getY() - 2, a.getZ() + 7.5, 180F, 0F);
            }
            case 560 -> {
                r.event("el Altar del Vacío: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.altar(vl));
                shot(r, "vacio_altar");
            }
            // el Portal del Alba, junto a la plaza de la grieta: verlo y cruzarlo
            case 570 -> {
                // (delante del arco, del lado de la plaza: facing 0, al sur mirando al norte; 1, al este mirando al oeste)
                net.minecraft.core.BlockPos p = net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalPos();
                boolean alongX = net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalFacing() % 2 == 0;
                if (p != null) player.teleportTo(vl, p.getX() + (alongX ? 0.5 : 9.5), p.getY(), p.getZ() + (alongX ? 9.5 : 0.5),
                        alongX ? 180F : 90F, -12F);
            }
            // pasar por el velo sin pararse (4 ticks): no lleva al santuario
            case 600 -> {
                portalTrips0 = net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.portalTrips();
                net.minecraft.core.BlockPos p = net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalPos();
                if (p != null) player.teleportTo(vl, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), -12F);
            }
            case 604 -> {
                net.minecraft.core.BlockPos p = net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalPos();
                boolean alongX = net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalFacing() % 2 == 0;
                if (p != null) player.teleportTo(vl, p.getX() + (alongX ? 0.5 : 9.5), p.getY(), p.getZ() + (alongX ? 9.5 : 0.5),
                        alongX ? 180F : 90F, -12F);
            }
            case 640 -> {
                int n = net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.portalTrips() - portalTrips0;
                r.event("pasar por el Portal del Alba sin pararse: " + (n == 0 ? "no lleva (bien)" : n + " viajes (MAL)"));
                shot(r, "vacio_portal");
                portalTrips0 = net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.portalTrips();
            }
            case 650 -> {
                // (se queda en el velo: hay que estar VoidPortal.CHARGE ticks)
                net.minecraft.core.BlockPos p = net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalPos();
                if (p != null) player.teleportTo(vl, p.getX() + 0.5, p.getY(), p.getZ() + 0.5,
                        net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalFacing() % 2 == 0 ? 180F : 90F, 0F);
            }
            case 700 -> {
                Vec3 ar = net.tierrasfantasticas.tfclient.vacio.VoidIsland.arrivePos();
                r.event(String.format("quedarse en el Portal del Alba: %d viajes al santuario (a %.1f bloques de la llegada del santuario; en toda la prueba, %s)",
                        net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.portalTrips() - portalTrips0, player.position().distanceTo(ar),
                        net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.portal()));
                shot(r, "vacio_portal_llegada");
            }
            default -> {
            }
        }
    }

    /**
     * (1.3.96) La cámara (volando) a 3,5-6 bloques de «c», en un sitio libre y desde donde se le vea (con los chunks de
     * alrededor ya cargados en el servidor; si no, su altura es la mínima): en las primeras pruebas se quedó dentro de una
     * loma y detrás de una columna.
     */
    private static void viewSpot(ServerLevel vl, ServerPlayer player, Rep r, Vec3 c, String what) {
        int fy = (int) Math.floor(c.y);
        Vec3 best = null;
        for (double d : new double[] {5.0, 4.0, 6.5}) {
            for (int up = 0; up <= 3 && best == null; up++) {
                for (int a = 0; a < 8 && best == null; a++) {
                    double x = c.x + d * Math.cos(a * Math.PI / 4), z = c.z + d * Math.sin(a * Math.PI / 4);
                    net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(x, fy - 1 + up, z);
                    vl.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
                    if (!vl.getBlockState(feet).getCollisionShape(vl, feet).isEmpty()
                            || !vl.getBlockState(feet.above()).getCollisionShape(vl, feet.above()).isEmpty()) continue;
                    Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + player.getEyeHeight(), feet.getZ() + 0.5);
                    var hit = vl.clip(new net.minecraft.world.level.ClipContext(eye, c, net.minecraft.world.level.ClipContext.Block.VISUAL,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceTo(c) < 0.6) {
                        best = new Vec3(eye.x, feet.getY(), eye.z);
                    }
                }
            }
            if (best != null) break;
        }
        if (best == null) best = new Vec3(c.x, fy + 3, c.z + 3.5);
        double dx = c.x - best.x, dz = c.z - best.z, dy = c.y - (best.y + player.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        fly(player, true);
        player.teleportTo(vl, best.x, best.y, best.z, yaw, pitch);
        r.event(String.format("%s en %.0f %.0f %.0f, mirado desde %.1f %.1f %.1f", what, c.x, c.y, c.z, best.x, best.y, best.z));
    }

    // ------------------------------------------------------------------------------------------- la fauna del Páramo

    /** (1.3.96) Las criaturas del Páramo (criaturas_vacio): todas menos el End Strider. */
    private static final String[] FAUNA_MOBS = {"enderling", "enderbruiser", "ender_crawler", "ender_watchman", "end_wraith", "endermauler",
            "empowered_enderling", "empowered_enderbruiser", "empowered_ender_crawler", "empowered_ender_watchman", "empowered_end_wraith",
            "empowered_endermauler"};
    private static final int FAUNA_EACH = 170, FAUNA_WILD = 900;
    private static BossEntity faunaNow;
    private static int faunaHits;
    private static Vec3 faunaDied;
    /** Las animaciones que pone la criatura de ahora (las que ve el cliente; ticks de cada una). */
    private static final Map<String, Integer> faunaAnims = new TreeMap<>();

    /**
     * (1.3.96) La fauna del Páramo, una a una delante del jugador (en supervivencia: le atacan): a los 3 s una captura, a
     * los 6 otra (atacando) y a los 8 lo que se vio (modelo, objetivo, si le ha golpeado); luego, con la aparición natural
     * encendida, 45 s en el Páramo y cuántas salen solas.
     */
    private static void faunaStep(MinecraftServer server, ServerLevel level, ServerPlayer player, Rep r) {
        ServerLevel vl = server.getLevel(net.tierrasfantasticas.tfclient.vacio.TFVoid.VOID);
        if (vl == null) {
            r.event("NO HAY DIMENSIÓN tfclient:void");
            next(level);
            return;
        }
        int i = (t - 1) / FAUNA_EACH, k = (t - 1) % FAUNA_EACH;
        Vec3 seed = net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.faunaSpot();
        if (i < FAUNA_MOBS.length) {
            String mob = FAUNA_MOBS[i];
            if (faunaNow != null && faunaNow.isAlive() && player.getLastHurtByMob() == faunaNow) faunaHits++;
            if (faunaNow != null && faunaNow.isAlive()) {
                List<String> an = net.tierrasfantasticas.tfclient.skills.SkillMobs.animNames(faunaNow);
                if (an == null) faunaAnims.merge("SIN MODELO", 1, Integer::sum);
                else for (String a : an.subList(Math.min(1, an.size()), an.size())) faunaAnims.merge(a, 1, Integer::sum);
            }
            switch (k) {
                case 0 -> {
                    if (i == 0) {
                        player.teleportTo(vl, seed.x, seed.y, seed.z, 180F, 8F);
                        r.event("la fauna del Páramo, desde " + String.format("%.0f %.0f %.0f", seed.x, seed.y, seed.z) + ": "
                                + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.fauna(vl));
                    }
                    player.teleportTo(vl, seed.x, seed.y, seed.z, 180F, 8F);
                    faunaHits = 0;
                    faunaAnims.clear();
                    player.setLastHurtByMob(null);
                }
                case 20 -> {
                    Vec3 at = new Vec3(seed.x, seed.y, seed.z - 10);
                    int y = vl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
                    faunaNow = net.tierrasfantasticas.tfclient.vacio.VoidFauna.spawn(vl, mob, new Vec3(at.x, y, at.z), 0F);
                    if (faunaNow == null) r.event(mob + ": NO SALE");
                }
                case 80 -> shot(r, "fauna_" + mob);
                case 140 -> shot(r, "fauna_" + mob + "_ataca");
                case 160 -> {
                    BossEntity e = faunaNow;
                    r.event(e == null ? mob + ": no está" : String.format("%s («%s»): modelo %s, vida %.0f/%.0f, a %.1f bloques, objetivo %s, "
                                    + "me ha golpeado %s, barra propia %s; animaciones (ticks) %s", mob, e.getName().getString(), e.model() != null ? "sí" : "NO",
                            e.getHealth(), e.getMaxHealth(), e.distanceTo(player), e.getTarget() == player ? "el jugador" : String.valueOf(e.getTarget()),
                            faunaHits > 0 ? "sí" : "no", e.def() != null && e.def().barColor != null ? "sí" : "no", faunaAnims));
                    if (e != null && i == 0) {
                        // (la primera se mata: sus Drops del pack, la perla de ender)
                        faunaDied = e.position();
                        e.hurt(player.damageSources().playerAttack(player), 10000F);
                    } else if (e != null) {
                        e.discard();
                    }
                    faunaNow = null;
                }
                case 166 -> {
                    if (faunaDied != null) {
                        var items = vl.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(net.minecraft.core.BlockPos.containing(faunaDied)).inflate(6));
                        StringBuilder sb = new StringBuilder();
                        for (var it : items) sb.append(sb.length() == 0 ? "" : ", ").append(it.getItem().getCount()).append(' ').append(it.getItem().getItem());
                        r.event(mob + " al morir suelta: " + (sb.length() == 0 ? "nada" : sb));
                        for (var it : items) it.discard();
                        faunaDied = null;
                    }
                }
                default -> {
                }
            }
            return;
        }
        int w = t - 1 - FAUNA_MOBS.length * FAUNA_EACH;
        if (w == 0) {
            command(server, "gamerule doMobSpawning true");
            player.teleportTo(vl, seed.x, seed.y, seed.z, 0F, 0F);
        }
        if (w == FAUNA_WILD - 120) shot(r, "fauna_bioma");
        if (w == FAUNA_WILD) {
            r.event("aparición natural en el Páramo (45 s): " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.fauna(vl));
            command(server, "gamerule doMobSpawning false");
            for (BossEntity e : vl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(BossEntity.class), BossEntity::isFauna)) e.discard();
            next(level);
        }
    }

    /**
     * (1.3.93) Pone al jugador (volando) a esos bloques al este y al sur de Caelthar, mirando a la grieta del cielo (desde
     * la 1.3.93b, en una dirección fija del cielo: SkyRift.YAW, PITCH).
     */
    private static void skyWatch(ServerLevel level, ServerPlayer player, double east, double south) {
        player.teleportTo(level, knight.getX() + east, knight.getY() + 3, knight.getZ() + south,
                net.tierrasfantasticas.tfclient.vacio.SkyRift.YAW, net.tierrasfantasticas.tfclient.vacio.SkyRift.PITCH);
    }

    /** Que el jugador de la prueba vuele (o deje de volar), como en creativo. */
    private static void fly(ServerPlayer p, boolean on) {
        p.getAbilities().mayfly = on;
        p.getAbilities().flying = on;
        p.onUpdateAbilities();
    }

    // ------------------------------------------------------------------------------------------- panoramas (1.3.96)

    /**
     * Lo que el cliente tiene que capturar ahora con la herramienta de panoramas de Minecraft (6 caras de 90°, a
     * pruebas/panoramas/&lt;nombre&gt;/screenshots/panorama_N.png): null si nada. Los usa el agujero de gusano
     * (tools/void/panoramas.py los pasa a equirectangulares).
     */
    static volatile String panorama;
    private static final String[] PANORAMAS = {"nether", "end", "void"};
    private static final int PANO_TICKS = 900;

    /** El que se está probando ahora («panoramas», «vacio» o el id de un jefe; null si nada). */
    static String currentId() {
        Rep r = current;
        return r == null ? null : r.id;
    }

    /**
     * (1.3.96) El dueño, del agujero de gusano: «que muestre la dimensión; en el Nether hay fuego de textura, pero no sé si
     * estoy viendo el Nether de verdad». Se capturan de verdad: el Nether (el sitio más abierto, con lava debajo, de los
     * alrededores del origen), el End (encima del portal, con las columnas alrededor) y el Void (al norte de la isla,
     * mirando a la grieta y al agujero negro). En espectador (sin mano), mirando al sur.
     */
    private static void panoramaStep(MinecraftServer server, ServerLevel level, ServerPlayer player, Rep r) {
        int stage = t / PANO_TICKS, k = t % PANO_TICKS;
        if (stage >= PANORAMAS.length) {
            if (k == 1) {
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.teleportTo(level, 0.5, ground(level, 0, 0), 0.5, 0F, 0F);
            }
            if (k == 60) next(level);
            return;
        }
        String what = PANORAMAS[stage];
        if (k == 1) {
            ServerLevel dim;
            Vec3 at;
            switch (what) {
                case "nether" -> {
                    dim = server.getLevel(net.minecraft.world.level.Level.NETHER);
                    at = dim == null ? null : netherSpot(dim, r);
                }
                case "end" -> {
                    dim = server.getLevel(net.minecraft.world.level.Level.END);
                    at = new Vec3(0.5, 74, 18.5);
                }
                default -> {
                    dim = server.getLevel(net.tierrasfantasticas.tfclient.vacio.TFVoid.VOID);
                    at = new Vec3(-20.5, 125, -140.5);
                }
            }
            if (dim == null || at == null) {
                r.event("panorama " + what + ": NO HAY ESA DIMENSIÓN");
                t = (stage + 1) * PANO_TICKS - 1;
                return;
            }
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            player.teleportTo(dim, at.x, at.y, at.z, 0F, 0F);
            r.event("panorama " + what + ": " + dim.dimension().location() + " en " + (int) at.x + ", " + (int) at.y + ", " + (int) at.z);
        }
        if (k == 30) player.teleportTo((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), 0F, 0F);
        if (k == 800) panorama = what;
    }

    /**
     * El sitio más abierto del Nether cerca del origen (cuevas grandes; mucho mejor con el mar de lava justo debajo): de una
     * rejilla de puntos de aire, el que más lejos ve de media en 26 direcciones (hasta 56 bloques).
     */
    private static Vec3 netherSpot(ServerLevel n, Rep r) {
        net.minecraft.core.BlockPos best = new net.minecraft.core.BlockPos(0, 70, 0);
        double bestScore = -1;
        boolean bestLava = false;
        for (int gx = -4; gx <= 4; gx++) {
            for (int gz = -4; gz <= 4; gz++) {
                for (int y = 36; y <= 60; y += 4) {
                    net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(gx * 24, y, gz * 24);
                    if (!n.getBlockState(p).isAir()) continue;
                    double s = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx != 0 || dy != 0 || dz != 0) s += ray(n, p, dx, dy, dz, 56);
                            }
                        }
                    }
                    s /= 26;
                    boolean lava = false;
                    for (int d = 1; d < 14; d++) {
                        net.minecraft.world.level.block.state.BlockState b = n.getBlockState(p.below(d));
                        if (b.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                            lava = true;
                            break;
                        }
                        if (!b.isAir()) break;
                    }
                    if (lava) s *= 1.6;
                    if (s > bestScore) {
                        bestScore = s;
                        best = p;
                        bestLava = lava;
                    }
                }
            }
        }
        r.event(String.format("panorama nether: el sitio más abierto ve %.0f bloques de media%s", bestScore / (bestLava ? 1.6 : 1),
                bestLava ? ", con lava debajo" : ""));
        return net.minecraft.world.phys.Vec3.atCenterOf(best);
    }

    private static double ray(ServerLevel n, net.minecraft.core.BlockPos p, int dx, int dy, int dz, int max) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (int s = 2; s <= max; s += 2) {
            net.minecraft.core.BlockPos q = p.offset((int) Math.round(dx / len * s), (int) Math.round(dy / len * s), (int) Math.round(dz / len * s));
            if (q.getY() <= n.getMinBuildHeight() || q.getY() >= n.getMaxBuildHeight() || !n.getBlockState(q).isAir()) return s;
        }
        return max;
    }

    /** Lo que hay bajo los pies al llegar al vacío (¿se generó la isla?). */
    private static final class BlockPosHelper {
        static void report(Rep r, ServerPlayer p) {
            net.minecraft.core.BlockPos b = p.blockPosition().below();
            r.event("bajo los pies al llegar: " + p.level().getBlockState(b).getBlock().getDescriptionId() + " en " + b.toShortString()
                    + (p.level().getBlockState(b).isAir() ? " (VACÍO: ¿no se generó la isla?)" : ""));
        }
    }

    /** Una captura que pide el cliente (BossTestClient: el túnel del viaje, a sus tiempos). */
    static void clientShot(String what) {
        Rep r = current;
        if (r != null) shot(r, what);
    }

    private static void shot(Rep r, String what) {
        SHOTS_PENDING.add(String.format("%02d_%s_%s", index, r.id, what));
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
        placed = null;
        throwFrom = null;
        freeUntil = 0;
        pushedAt = -10;
        bossPushedAt = -100;
        stillFar = 0;
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
            sb.append(String.format("   objetivo = el jugador: %d%% del tiempo (cerca: %d%%); golpes que me da: %d (%.1f de daño); los míos: %d (lo más cerca que llego: %.1f del borde)%n",
                    100 * r.targetTicks / ft, 100 * r.nearTicks / ft, r.hitsTaken, r.dmgTaken, r.playerAttacks, r.minDist));
            sb.append(String.format("   mis golpes que le quitan vida: %d; sin efecto (sin estar en sus ticks de invulnerable): %d%n", r.landed, r.noEffect));
            sb.append(String.format("   me mueve el jefe (empujón, arrastre): %d veces; lo más lejos que me lanza %.1f bloques, lo más alto %.1f%n",
                    r.moved, r.throwFar, r.throwHigh));
            if (!r.skillHits.isEmpty()) {
                StringBuilder hs = new StringBuilder();
                for (Map.Entry<String, float[]> e : r.skillHits.entrySet()) {
                    float[] c = e.getValue();
                    hs.append(String.format("%s%s %.0f/%.0f (%.0f)", hs.length() == 0 ? "" : "; ", e.getKey(), c[1], c[0], c[2]));
                }
                sb.append("   skills que le dan (con daño/veces, daño): ").append(hs).append('\n');
            }
            for (Map.Entry<String, int[]> e : r.shots.entrySet()) {
                sb.append(String.format("   %s: %d, tocan al jugador %d%s%n", e.getKey(), e.getValue()[0], e.getValue()[1],
                        r.missed.containsKey(e.getKey()) ? " (uno que no: " + r.missed.get(e.getKey()) + ")" : ""));
            }
            sb.append(String.format("   anda: %d ticks, hacia atrás %d (con lockmodel del pack %d, empujado por su skill %d), de lado %d (con lockmodel %d, empujado %d); rápido (>0,8 b/t) %d; teletransportes %d%n",
                    r.movingTicks, r.backTicks, r.backLocked, r.backPushed, r.sideTicks, r.sideLocked, r.sidePushed, r.fastTicks, r.teleports));
            sb.append(String.format("   al andar por el suelo: %.3f bloques/tick de media (%d ticks)%n", r.groundTicks == 0 ? 0 : r.groundDist / r.groundTicks,
                    r.groundTicks));
            if (r.clientItemMax > 0) sb.append("   efectos con objeto en el cliente (lo más): ").append(r.clientItemInfo).append('\n');
            sb.append(String.format("   en el aire (>3 bloques) %d ticks, como mucho %.1f; lo más lejos de la grieta %.1f; esbirros a la vez %d%n",
                    r.airTicks, r.maxAir, r.maxHome, r.minionsMax));
            sb.append(String.format("   quieto y lejos del jugador (más de 2 s seguidos): %d ticks%n", r.stillFarTicks));
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
