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
            // (1.3.98) «corta»: lo de la 1.3.98 en menos de 10 min (el dueño: «no hagas comprobaciones de más de 10
            // minutos»): el viaje, los caballeros, el soporte del altar, la Llegada, el puente, la isla de la grieta y la fauna
            if (only != null && List.of(only.split(",")).contains("corta")) ids.add(0, "corta");
            // (1.3.99) «crates»: TF Crates (Fantastic Crates dentro del mod) y la cinemática del santuario, dos aperturas
            if (only != null && List.of(only.split(",")).contains("crates")) ids.add(0, "crates");
            // (1.3.101) «npcs»: los NPC de escenas (los 13 packs), el panel de NPC, sus pins, rutas y el guardado
            if (only != null && List.of(only.split(",")).contains("npcs")) ids.add(0, "npcs");
            // (1.3.103) «pad»: el TF Pad abierto (portada, Música, Efectos) antes de todo; «padtras»: lo mismo al final (tras
            // las crates, por si su cinemática deja algo cambiado al pintar)
            if (only != null && List.of(only.split(",")).contains("pad")) ids.add(0, "pad");
            if (only != null && List.of(only.split(",")).contains("padtras")) ids.add("padtras");
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
        // (1.3.114) también el pad: con shaders, sus capturas salían con el nombre de otra (la del inventario, como «oni_lanzas_mano»)
        boolean waitShot = ("vacio".equals(r.id) || "fauna".equals(r.id) || "corta".equals(r.id) || "pad".equals(r.id) || "padtras".equals(r.id))
                && !SHOTS_PENDING.isEmpty() && shotWait++ < 200;
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
        if ("corta".equals(r.id)) {
            if (!waitShot) cortaStep(server, level, player, r);
            return;
        }
        if ("crates".equals(r.id)) {
            cratesStep(server, level, player, r);
            return;
        }
        if ("npcs".equals(r.id)) {
            npcsStep(server, level, player, r);
            return;
        }
        if ("pad".equals(r.id) || "padtras".equals(r.id)) {
            if (!waitShot) padStep(level, player, r);
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
    /** El cliente de la prueba cierra la pantalla que tenga abierta (el panel de NPC antes de otra captura). */
    static volatile boolean closeScreen;
    /** El cliente de la prueba abre el pad en esa app («» = la portada; BossTest «pad»). */
    static volatile String openPad;
    /** (1.3.106) Lo que la prueba escribe en la casilla de Música del pad (BossTestClient). */
    static volatile String typePad;
    /** (1.3.109) Una acción para la app abierta del pad («tab:combate», «§scroll:400»). */
    static volatile String actPad;
    private static net.tierrasfantasticas.tfclient.bosses.BossEntity padNpc;
    private static java.util.UUID padClaim;
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
    /**
     * (1.3.97) Las vistas desde el aire del Void nuevo (desde dónde, hacia dónde, la captura): la Llegada con el portal, los
     * Llanos de Ceniza, el Bosque de Cristal, las Fauces de Obsidiana, las Ruinas de la Orden y la Herida. 160 ticks cada
     * una (con shaders, el trozo de mundo tarda en construirse).
     */
    private static final double[][] VIEWS = {
            {-84, 80, -182, -58, 67, -161}, {-64, 100, -232, -64, 70, -330}, {70, 104, -360, 150, 72, -450},
            {-200, 108, -360, -280, 74, -450}, {-64, 102, -520, -64, 72, -610}, {-14, 118, -372, -14, 60, -430}};
    private static final String[] VIEW_NAMES = {"vacio_llegada_portal", "vacio_llanos", "vacio_cristal", "vacio_fauces", "vacio_ruinas", "vacio_herida"};
    private static final int VIEW_TICKS = 160;
    /** (1.3.96) Lo que dura la prueba de la luz del Alba (lightStep), metida tras la persecución (y las vistas, 1.3.97). */
    private static final int LIGHT_TICKS = 720 + 6 * VIEW_TICKS;
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
                r.event("dentro del Velo del Alba: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player)
                        + "; música del santuario: " + (RiftSky.sanctuaryMusic() ? "suena" : "NO"));
                shot(r, "vacio_caballero");
                r.event(net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sacred(vl));
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
        if (k >= 720) {
            int v = (k - 720) / VIEW_TICKS, kk = (k - 720) % VIEW_TICKS;
            if (v >= VIEWS.length) return;
            double[] a = VIEWS[v];
            if (kk == 0) {
                fly(player, true);
                double dx = a[3] - a[0], dy = a[4] - a[1], dz = a[5] - a[2];
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                player.teleportTo(vl, a[0], a[1], a[2], yaw, pitch);
            } else if (kk == VIEW_TICKS - 10) {
                shot(r, VIEW_NAMES[v]);
            } else if (kk == VIEW_TICKS - 1 && v == VIEWS.length - 1) {
                fly(player, false);
            }
            return;
        }
        java.util.List<net.minecraft.core.BlockPos> shards = net.tierrasfantasticas.tfclient.vacio.VoidIsland.shards();
        // (un Ancla de los Fragmentos: las 2 primeras son las de la isla de la grieta; la 4.ª, en medio del sendero)
        net.minecraft.core.BlockPos f = shards.size() > 3 ? shards.get(3) : shards.isEmpty() ? null : shards.get(0);
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
                r.event("pedirla otra vez: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.armorAgain(player));
                net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.wearArmor(player, true);
                net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.setLight(player, 50F);
            }
            case 470 -> {
                r.event("con la armadura del Vacío puesta, 3 s fuera de la luz: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.light(player)
                        + "; " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.armorValue(player));
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
                // (delante del arco, del lado de la plaza: facing 0, al sur mirando al norte; 1, al oeste mirando al este)
                net.minecraft.core.BlockPos p = net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalPos();
                boolean alongX = net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalFacing() % 2 == 0;
                if (p != null) player.teleportTo(vl, p.getX() + (alongX ? 0.5 : -9.5), p.getY(), p.getZ() + (alongX ? 9.5 : 0.5),
                        alongX ? 180F : 270F, -12F);
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
                if (p != null) player.teleportTo(vl, p.getX() + (alongX ? 0.5 : -9.5), p.getY(), p.getZ() + (alongX ? 9.5 : 0.5),
                        alongX ? 180F : 270F, -12F);
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
                        net.tierrasfantasticas.tfclient.vacio.VoidIsland.portalFacing() % 2 == 0 ? 180F : 270F, 0F);
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
    /** (1.3.97) Solo dos (orden del dueño): el Desgarrador (Endermauler) y el Vigía (Ender Watchman, con sus espectros). */
    private static final String[] FAUNA_MOBS = {"endermauler", "ender_watchman", "end_wraith"};
    /** (1.3.98) Cada segundo: distancia al jugador, en el suelo, sin gravedad (y los espectros que ha invocado). */
    private static final StringBuilder faunaTrack = new StringBuilder();
    private static final int FAUNA_EACH = 300, FAUNA_WILD = 900;
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
            // (1.3.98, el dueño: «los mobs son inútiles, y los chiquitos que invoca el mago no me siguen y se quedan
            // bugueados en su eje»): por dónde anda cada segundo, y sus espectros
            if (faunaNow != null && faunaNow.isAlive() && k >= 20 && k < 280 && k % 20 == 0) {
                faunaTrack.append(String.format(" %.0f%s%s", faunaNow.distanceTo(player), faunaNow.onGround() ? "" : "^",
                        faunaNow.isNoGravity() ? "G" : ""));
                List<BossEntity> wr = vl.getEntitiesOfClass(BossEntity.class, faunaNow.getBoundingBox().inflate(48),
                        e -> e != faunaNow && e.isAlive() && e.def() != null && "end_wraith".equalsIgnoreCase(e.def().name));
                if (!wr.isEmpty()) {
                    faunaTrack.append('[');
                    for (BossEntity w : wr) faunaTrack.append(String.format("%.0f%s%s%s ", w.distanceTo(player), w.onGround() ? "" : "^",
                            w.isNoGravity() ? "G" : "", w.getTarget() == player ? "" : "?"));
                    faunaTrack.append(']');
                }
            }
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
                    faunaTrack.setLength(0);
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
                case 220 -> shot(r, "fauna_" + mob + "_ataca2");
                case 280 -> {
                    BossEntity e = faunaNow;
                    r.event(e == null ? mob + ": no está" : String.format("%s («%s»): modelo %s, vida %.0f/%.0f, a %.1f bloques, objetivo %s, "
                                    + "me ha golpeado %s, barra %s; animaciones (ticks) %s", mob, e.getName().getString(), e.model() != null ? "sí" : "NO",
                            e.getHealth(), e.getMaxHealth(), e.distanceTo(player), e.getTarget() == player ? "el jugador" : String.valueOf(e.getTarget()),
                            faunaHits > 0 ? "sí" : "no", e.hasBar() ? "SÍ (MAL)" : "no (bien)", faunaAnims));
                    r.event("   " + mob + " cada segundo (distancia; ^ en el aire, G sin gravedad; [espectros, ? sin objetivo el jugador]):" + faunaTrack);
                    if (e != null && i == 0) {
                        // (la primera se mata: no suelta nada, orden del dueño)
                        faunaDied = e.position();
                        e.hurt(player.damageSources().playerAttack(player), 10000F);
                    } else if (e != null) {
                        e.discard();
                    }
                    faunaNow = null;
                }
                case 286 -> {
                    if (faunaDied != null) {
                        var items = vl.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(net.minecraft.core.BlockPos.containing(faunaDied)).inflate(6));
                        StringBuilder sb = new StringBuilder();
                        for (var it : items) sb.append(sb.length() == 0 ? "" : ", ").append(it.getItem().getCount()).append(' ').append(it.getItem().getItem());
                        r.event(mob + " al morir suelta: " + (sb.length() == 0 ? "nada (bien)" : sb + " (MAL)"));
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

    // ------------------------------------------------------------------------------------------- la prueba corta (1.3.98)

    /** Desde dónde y hacia dónde: la Llegada y el portal, los escalones del Puente de Luz, la isla de la grieta y el Ancla. */
    private static final double[][] CORTA_VIEWS = {{-44, 74, -146, -55, 68, -161}, {-58, 72, -664, -64, 68, -649},
            {25, 105, 95, 64, 72, 142}, {-115, 96, -45, -50, 70, 25}, {-30, 80, 42, -51, 73, 23}};
    private static final String[] CORTA_NAMES = {"llegada_portal", "puente_escalones", "isla_sur", "isla_oeste", "ancla_grieta"};
    private static double standMin, standMax;

    private static void look(ServerPlayer p, ServerLevel vl, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        p.teleportTo(vl, x, y, z, yaw, pitch);
    }

    /**
     * (1.3.99) TF Crates: que estén registradas (bloque, objetos, /tf crates) y la cinemática del bosque en dos
     * aperturas de verdad (CrateOpeningService, como un jugador con su llave): una crate épica de estilo clásico y una
     * mítica de estilo cinemático (la tapa y el frente van al revés). Las capturas las saca el cliente a los tiempos de
     * la cinemática (BossTestClient.crateShots).
     */
    private static void cratesStep(MinecraftServer server, ServerLevel level, ServerPlayer player, Rep r) {
        net.minecraft.core.BlockPos pos = cratePos;
        if (t == 1) {
            fly(player, true);
            var blocks = net.minecraftforge.registries.ForgeRegistries.BLOCKS;
            var items = net.minecraftforge.registries.ForgeRegistries.ITEMS;
            net.minecraft.resources.ResourceLocation crate = new net.minecraft.resources.ResourceLocation("fscrates", "crate");
            r.event("TF Crates: activo " + net.tierrasfantasticas.tfclient.crates.FSCrates.enabled() + "; bloque fscrates:crate "
                    + blocks.containsKey(crate) + "; objetos crate/fantastic_key/unique_key/editor_wand " + items.containsKey(crate) + "/"
                    + items.containsKey(new net.minecraft.resources.ResourceLocation("fscrates", "fantastic_key")) + "/"
                    + items.containsKey(new net.minecraft.resources.ResourceLocation("fscrates", "unique_key")) + "/"
                    + items.containsKey(new net.minecraft.resources.ResourceLocation("fscrates", "editor_wand")));
            int res = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "tf crates help");
            r.event("/tf crates help → " + res);
            cratePos = player.blockPosition().offset(0, 1, -3);
        }
        for (int run = 0; run < 2; run++) {
            int t0 = 60 + run * 540;      // (la primera, a los 3 s: que el santuario esté preparado y la pantalla dibuje)
            // (1.3.107) en la primera, 6 s más antes de abrir: las capturas de antes de abrir sin cada cosa (BossTestClient.OFF)
            int open = t0 + (run == 0 ? 150 : 30);
            if (t == t0 && pos != null) {
                level.setBlockAndUpdate(pos, net.tierrasfantasticas.tfclient.crates.registry.ModRegistry.CRATE_BLOCK.get().defaultBlockState());
                if (level.getBlockEntity(pos) instanceof net.tierrasfantasticas.tfclient.crates.block.CrateBlockEntity be) {
                    be.setConfig(testCrate(run));
                    r.event("crate " + (run + 1) + " puesta en " + pos.toShortString() + " (" + testCrate(run).styleId + ")");
                } else {
                    r.event("crate " + (run + 1) + ": NO HAY BLOQUE DE CRATE");
                }
            }
            // (la pantalla de antes de abrir, con el santuario detrás: el cliente la abre y saca su captura)
            if (t == Math.max(2, t0 - 8) && cratePos != null) {
                BossTestClient.showPreOpen(testCrate(run), cratePos);
            }
            if (t == open && pos != null) {
                var res = net.tierrasfantasticas.tfclient.crates.crate.CrateOpeningService.open(player, testCrate(run), pos, ItemStack.EMPTY, false);
                r.event("apertura " + (run + 1) + ": " + res);
            }
            if (t == open + 395) {
                r.event("cinemática " + (run + 1) + ": " + net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicScreen.testStats);
            }
        }
        // (1.3.109, el dueño: «dale rework a los efectos VFX de la crate») la crate del mundo, en reposo, con sus luces y
        // destellos nuevos (CrateParticles), de cerca
        int w0 = 60 + 2 * 540 + 20;
        if (t == w0) closeScreen = true;
        if (t == w0 + 4 && pos != null) {
            look(player, level, pos.getX() + 0.5, pos.getY() - 0.4, pos.getZ() + 3.6, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5);
        }
        if (t == w0 + 50) shot(r, "crate_mundo");
        if (t == w0 + 56) shot(r, "crate_mundo_2");
        if (t >= w0 + 64) next(level);
    }

    private static net.minecraft.core.BlockPos cratePos;

    // ------------------------------------------------------------------------------------------- NPC de escenas (1.3.101)

    /** Lo que se pone en las filas: pack, NPC y dónde. */
    private record NpcSpot(String pack, String mob, Vec3 at, int row) {}

    /** (1.3.106) Los NPC con muebles y los muebles del panel que se ven de cerca: de 4 en 4, en fila. */
    private static final String[][] MUEBLES = {
            {"npc_townsfolks_vol3", "scene_townsfolk_sit_chair_1"}, {"npc_townsfolks_vol3", "scene_townsfolk_writer"},
            {"npc_townsfolks_vol3", "scene_townsfolk_sit_couch_1"}, {"npc_townsfolks_vol3", "scene_townsfolk_sit_bar_1"},
            {"npc_townsfolks_vol3", "scene_townsfolk_sit_chair_4"}, {"npc_townsfolks_vol3", "scene_townsfolk_lay_back_1"},
            {"npc_townsfolks_vol3", "scene_townsfolk_readers_book_2"}, {"npc_townsfolks_vol3", "scene_townsfolk_lay_front_2"},
            {"npc_tavern", "mueble_mesa_taberna"}, {"npc_tavern", "mueble_silla_taberna"}, {"npc_tavern", "mueble_jarra"},
            {"npc_tavern", "mueble_sofa"}, {"npc_tavern", "scene_tavern_bartender"}, {"npc_tavern", "mueble_barra"}};

    private static Vec3 mueble(Vec3 o, int i) {
        return new Vec3(o.x - 60 + (i % 4) * 3.5, o.y, o.z + 100 + (i / 4) * 14);
    }

    private static final List<NpcSpot> NPC_PLAN = new ArrayList<>();
    private static final List<BossEntity> NPC_PLACED = new ArrayList<>();
    /** Las capturas de las filas: {primer NPC, último + 1}. */
    private static final List<int[]> NPC_SHOTS = new ArrayList<>();
    private static BossEntity npcGuard, npcVendor, npcWalker;
    private static net.minecraft.nbt.CompoundTag npcSaved;
    private static final StringBuilder npcTrack = new StringBuilder();
    private static int npcMinerHits;
    /** Entre NPC de una fila y entre filas: la cámara va a 10 bloques de la suya (a 12, quedaba dentro del barco del pescador). */
    private static final int NPC_GAP = 5, NPC_ROW = 20, NPC_PER_SHOT = 5;
    /** Ticks por NPC al ponerlos: el jugador llega en el primero y lo pone en el último (su chunk ya cargado). */
    private static final int NPC_SLOT = 4;
    /** Ticks por captura de las filas: la cámara se mueve en el 0 y la captura sale en el 60 (los chunks cargados). */
    private static final int NPC_SHOT_TICKS = 70, NPC_SHOT_AT = 60;

    /**
     * El NPC de ese sitio, ahora: si su chunk se descargó al irse la cámara y volvió a cargarse, es una entidad nueva
     * (la del disco, con su ~onLoad); se busca cerca de donde se puso y por su mob, el más cercano.
     */
    private static BossEntity npcNear(ServerLevel level, NpcSpot sp, BossEntity old) {
        if (old != null && old.isAlive() && !old.isRemoved()) return old;
        BossEntity best = null;
        double bd = Double.MAX_VALUE;
        // (a 16 bloques: los que andan se alejan de su sitio; el «handler» lleva cajas a 10 bloques. Cada NPC sale una vez
        // por fila y las filas van a 20)
        for (BossEntity e : level.getEntitiesOfClass(BossEntity.class, new net.minecraft.world.phys.AABB(sp.at(), sp.at()).inflate(16))) {
            if (!e.isAlive() || !sp.mob().equalsIgnoreCase(e.mobName())) continue;
            double d = e.position().distanceToSqr(sp.at());
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * (1.3.101) Los NPC de escenas: el catálogo, /tf npc (el panel, a quien lo ejecuta), todos los NPC de los 13 packs
     * puestos con el panel (una fila por pack, mirando a la cámara; una captura cada 5) con su modelo y sus animaciones,
     * un cosmético del menú del pack, moverlo y girarlo, guardarlo y volver a cargarlo (su modelo y su cosmético vuelven
     * con ~onLoad), un vendedor del mercado entre dos pins, un caminante por una ruta del panel y el panel abierto.
     */
    private static void npcsStep(MinecraftServer server, ServerLevel level, ServerPlayer player, Rep r) {
        List<net.tierrasfantasticas.tfclient.npcs.NpcCatalog.Pack> packs = net.tierrasfantasticas.tfclient.npcs.NpcCatalog.packs();
        Vec3 o = arena;
        if (player.getLastHurtByMob() instanceof BossEntity b && "scene_miner_undead".equalsIgnoreCase(b.mobName())) {
            npcMinerHits++;
            player.setLastHurtByMob(null);
        }
        if (t == 1) {
            fly(player, true);
            NPC_PLAN.clear();
            NPC_PLACED.clear();
            NPC_SHOTS.clear();
            npcTrack.setLength(0);
            npcMinerHits = 0;
            int total = 0, markers = 0;
            for (int row = 0; row < packs.size(); row++) {
                var pk = packs.get(row);
                int col = 0, first = NPC_PLAN.size();
                for (var n : pk.npcs()) {
                    total++;
                    if (n.marker()) {
                        markers++;
                        continue; // los generadores y quitadores de caminantes harían su trabajo en la fila
                    }
                    NPC_PLAN.add(new NpcSpot(pk.id(), n.mob(), new Vec3(o.x + col * NPC_GAP, o.y, o.z - row * NPC_ROW), row));
                    col++;
                }
                for (int k = first; k < NPC_PLAN.size(); k += NPC_PER_SHOT) NPC_SHOTS.add(new int[] {k, Math.min(NPC_PLAN.size(), k + NPC_PER_SHOT)});
            }
            r.event("catálogo: " + packs.size() + " packs, " + total + " NPC (" + markers + " marcadores sin modelo); objeto tfclient:npc_panel "
                    + net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(new net.minecraft.resources.ResourceLocation("tfclient", "npc_panel")));
            int before = player.getInventory().countItem(net.tierrasfantasticas.tfclient.npcs.TFNpcs.PANEL.get());
            int res = server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), "tf npc");
            r.event("/tf npc → " + res + "; paneles en el inventario " + before + " -> "
                    + player.getInventory().countItem(net.tierrasfantasticas.tfclient.npcs.TFNpcs.PANEL.get()));
            for (var pk : packs) {
                StringBuilder sb = new StringBuilder(pk.id() + ": ");
                for (var n : pk.npcs()) sb.append(n.name()).append(n.marker() ? "[marcador]" : "").append(n.pins().isEmpty() ? "" : "[" + n.pins().size() + " pins]")
                        .append(n.routes().isEmpty() ? "" : "[rutas " + n.routes() + "]").append(n.options().isEmpty() ? "" : "[cosméticos " + n.options().keySet() + "]").append(", ");
                r.event("   " + sb);
            }
        }
        int placeAt = 10;
        int k = t - placeAt;
        if (k >= 0 && k < NPC_PLAN.size() * NPC_SLOT) {
            // Con el panel, como el staff: a mis pies (mirando al norte: el NPC mira al sur, a la cámara). Primero llega el
            // jugador y unos ticks después lo pone (en el mismo tick, el chunk aún no estaba cargado y no salía)
            NpcSpot sp = NPC_PLAN.get(k / NPC_SLOT);
            if (k % NPC_SLOT == 0) player.teleportTo(level, sp.at().x, sp.at().y, sp.at().z, 180F, 0F);
            if (k % NPC_SLOT == NPC_SLOT - 1) {
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, new net.tierrasfantasticas.tfclient.npcs.NpcNet.Act(
                        net.tierrasfantasticas.tfclient.npcs.NpcNet.PLACE, -1, sp.pack(), sp.mob(), 1, 0, 0));
                BossEntity e = net.tierrasfantasticas.tfclient.npcs.TFNpcs.selected(player);
                // (en un chunk que aún se está cargando, el mundo todavía no lo enseña por su UUID: se busca en su
                // captura; si entonces no está, sale «NO ESTÁ» en su fila)
                if (e == null) e = npcNear(level, sp, null);
                NPC_PLACED.add(e);
                if (e == null) r.event(sp.pack() + "/" + sp.mob() + ": aún no se ve al ponerlo (se busca en su captura)");
            }
            return;
        }
        int shots0 = placeAt + NPC_PLAN.size() * NPC_SLOT + 40;
        int si = (t - shots0) / NPC_SHOT_TICKS, sk = (t - shots0) % NPC_SHOT_TICKS;
        if (t >= shots0 && si < NPC_SHOTS.size()) {
            int[] sh = NPC_SHOTS.get(si);
            NpcSpot a = NPC_PLAN.get(sh[0]), b = NPC_PLAN.get(sh[1] - 1);
            double cx = (a.at().x + b.at().x) / 2;
            // a 12 bloques y por encima: a 10, la cámara quedaba dentro de la diana del arquero (bow training), que su
            // modelo lleva 9,3-10,3 bloques por delante y 2 de alto
            if (sk == 0) look(player, level, cx, o.y + 4.5, a.at().z + 12, cx, o.y + 1.0, a.at().z);
            if (sk == NPC_SHOT_AT) {
                shot(r, String.format("npcs_%s_%d", a.pack().substring(4), sh[0]));
                StringBuilder sb = new StringBuilder();
                for (int i = sh[0]; i < sh[1]; i++) {
                    NpcSpot sp = NPC_PLAN.get(i);
                    BossEntity was = NPC_PLACED.get(i), e = npcNear(level, sp, was);
                    NPC_PLACED.set(i, e);
                    List<String> an = e == null ? null : net.tierrasfantasticas.tfclient.skills.SkillMobs.animNames(e);
                    sb.append(sp.mob()).append(e == null ? " (NO ESTÁ)" : an == null ? " (SIN MODELO)" : " " + an)
                            .append(e != null && e != was ? " (recargado del disco)" : "").append("; ");
                }
                r.event("fila " + a.pack() + ": " + sb);
            }
            return;
        }
        int f0 = shots0 + NPC_SHOTS.size() * NPC_SHOT_TICKS + 10;
        int f = t - f0;
        Vec3 g = new Vec3(o.x - 30, o.y, o.z + 20);
        switch (f) {
            case 0 -> player.teleportTo(level, g.x, g.y, g.z, 180F, 0F);
            case 4 -> {
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PLACE, "npc_guards_vol1", "scene_guard_stand", 1, 0, 0));
                npcGuard = net.tierrasfantasticas.tfclient.npcs.TFNpcs.selected(player);
                if (npcGuard == null) r.event("guardia de la prueba: NO SALE");
                look(player, level, g.x, g.y + 2.2, g.z + 5, g.x, g.y + 1.2, g.z);
            }
            case 34 -> {
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.OPTION, "hat", "hat1", 0, 0, 0));
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.OPTION, "back", "backpack_1", 0, 0, 0));
                r.event("guardia con el sombrero 1 y la espalda 1 del menú: piezas " + (npcGuard == null ? "-" : net.tierrasfantasticas.tfclient.skills.SkillMobs.parts(npcGuard)));
            }
            case 39 -> {
                if (npcGuard == null) break;
                Vec3 p0 = npcGuard.position();
                float y0 = npcGuard.getYRot();
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.MOVE, "", "", 1, 0, 0));
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.ROTATE, "", "", 45, 0, 0));
                r.event(String.format("mover a la derecha 1 y girar 45: (%.2f %.2f %.2f) %.0f° -> (%.2f %.2f %.2f) %.0f°", p0.x, p0.y, p0.z, y0,
                        npcGuard.getX(), npcGuard.getY(), npcGuard.getZ(), npcGuard.getYRot()));
            }
            case 70 -> shot(r, "npc_cosmetico");
            case 75 -> {
                if (npcGuard == null) break;
                npcSaved = new net.minecraft.nbt.CompoundTag();
                r.event("guardar el guardia: " + npcGuard.save(npcSaved) + " (se guarda con el mundo: " + npcGuard.shouldBeSaved() + ")");
                npcGuard.discard();
            }
            case 80 -> {
                if (npcSaved == null) break;
                Entity back = net.minecraft.world.entity.EntityType.loadEntityRecursive(npcSaved, level, e -> e);
                npcGuard = back instanceof BossEntity b ? b : null;
                r.event("volver a cargarlo: " + (npcGuard != null && level.addFreshEntity(npcGuard)));
            }
            case 140 -> {
                r.event("recargado (a los 60 ticks, tras su ~onLoad): " + (npcGuard == null ? "NO ESTÁ" : String.format("%s, piezas %s, (%.2f %.2f %.2f) %.0f°",
                        net.tierrasfantasticas.tfclient.skills.SkillMobs.animNames(npcGuard), net.tierrasfantasticas.tfclient.skills.SkillMobs.parts(npcGuard),
                        npcGuard.getX(), npcGuard.getY(), npcGuard.getZ(), npcGuard.getYRot())));
                shot(r, "npc_recargado");
            }
            case 150 -> {
                // Un vendedor del mercado entre dos pins (los demás de su ronda sin poner: se para en el segundo)
                Vec3 m = new Vec3(o.x - 30, o.y, o.z + 40);
                player.teleportTo(level, m.x + 6, m.y, m.z, 180F, 0F);
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PIN_SET, "marketvendor1", "", 0, 0, 0));
                player.teleportTo(level, m.x + 6, m.y, m.z + 8, 180F, 0F);
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PIN_SET, "marketvendor2", "", 0, 0, 0));
                player.teleportTo(level, m.x, m.y, m.z, 180F, 0F);
            }
            case 154 -> {
                Vec3 m = new Vec3(o.x - 30, o.y, o.z + 40);
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PLACE, "npc_market", "scene_market_vendor_walk_1", 1, 0, 0));
                npcVendor = net.tierrasfantasticas.tfclient.npcs.TFNpcs.selected(player);
                if (npcVendor == null) r.event("vendedor de la prueba: NO SALE");
                // de lado y cerca: anda del pin 1 al 2 (a lo largo de z) y se le ven las piernas y los brazos
                look(player, level, m.x + 13, m.y + 2.2, m.z + 4, m.x + 6, m.y + 1, m.z + 4);
                npcTrack.setLength(0);
            }
            // de lado y de cerca (a 3,5 bloques de donde esté), 10 ticks antes de la captura
            case 204 -> {
                if (npcVendor != null) look(player, level, npcVendor.getX() + 3.5, npcVendor.getY() + 1.6, npcVendor.getZ(),
                        npcVendor.getX(), npcVendor.getY() + 1.0, npcVendor.getZ());
            }
            case 214 -> shot(r, "npc_pins_andando");
            case 434 -> {
                Vec3 m = new Vec3(o.x - 30, o.y, o.z + 40);
                r.event("vendedor entre pins (cada 2 s: distancia al pin 1 / al pin 2):" + npcTrack + (npcVendor == null ? " NO ESTÁ"
                        : String.format(" — al final a %.1f del pin 2; animaciones %s", npcVendor.position().distanceTo(new Vec3(m.x + 6, m.y, m.z + 8)),
                        net.tierrasfantasticas.tfclient.skills.SkillMobs.animNames(npcVendor))));
                shot(r, "npc_pins");
            }
            case 444 -> {
                // (la captura sale unos ticks después de pedirla: lo siguiente, 10 ticks más tarde)
                // Un caminante por la ruta 1 del panel (un cuadrado de 8 bloques)
                Vec3 w = new Vec3(o.x - 60, o.y, o.z);
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.ROUTE_CLEAR, "", "", 1, 0, 0));
                for (Vec3 d : new Vec3[] {Vec3.ZERO, new Vec3(8, 0, 0), new Vec3(8, 0, 8), new Vec3(0, 0, 8)}) {
                    player.teleportTo(level, w.x + d.x, w.y, w.z + d.z, 180F, 0F);
                    net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.ROUTE_ADD, "", "", 1, 0, 0));
                }
                player.teleportTo(level, w.x, w.y, w.z, 180F, 0F);
            }
            case 448 -> {
                Vec3 w = new Vec3(o.x - 60, o.y, o.z);
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PLACE, "npc_wanderers_vol1", "scene_wanderer_stick", 1, 0, 0));
                npcWalker = net.tierrasfantasticas.tfclient.npcs.TFNpcs.selected(player);
                if (npcWalker == null) r.event("caminante de la prueba: NO SALE");
                look(player, level, w.x + 4, w.y + 4, w.z + 15, w.x + 4, w.y + 1, w.z + 4);
                npcTrack.setLength(0);
            }
            case 453 -> {
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.ROUTE_ASSIGN, "", "", 1, 0, 0));
                r.event("ruta 1 para el caminante: " + net.tierrasfantasticas.tfclient.skills.SkillSaved.global("scenes_route_1"));
            }
            case 590 -> {
                if (npcWalker != null) look(player, level, npcWalker.getX() + 3.5, npcWalker.getY() + 1.6, npcWalker.getZ() + 1.0,
                        npcWalker.getX(), npcWalker.getY() + 1.0, npcWalker.getZ());
            }
            case 600 -> shot(r, "npc_ruta_andando");
            case 768 -> {
                r.event("caminante por la ruta 1 (cada 2 s: x z):" + npcTrack + (npcWalker == null || !npcWalker.isAlive() ? " NO ESTÁ"
                        : " — animaciones " + net.tierrasfantasticas.tfclient.skills.SkillMobs.animNames(npcWalker)));
                shot(r, "npc_ruta");
                r.event("golpes del minero no muerto al jugador: " + npcMinerHits);
            }
            // El panel abierto con el caminante elegido (pestaña Editar)
            case 778 -> net.tierrasfantasticas.tfclient.npcs.TFNpcs.send(player, "", true);
            case 840 -> shot(r, "npc_panel");
            // (1.3.106) sin ninguno elegido: «El más cercano» tapaba «Cerca de ti:» (la captura del dueño)
            case 842 -> {
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.DESELECT, "", "", 0, 0, 0));
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.send(player, "", true);
            }
            case 856 -> shot(r, "npc_panel_sin_elegir");
            // (1.3.102) Un pescador de cerca y de lado: el sedal cuelga de la punta de la caña (en el pack subía al cielo)
            case 860 -> {
                closeScreen = true;
                player.teleportTo(level, o.x - 30, o.y, o.z + 70, 180F, 0F);
            }
            case 864 -> {
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PLACE, "npc_fishermen", "scene_fisherman_standing", 1, 0, 0));
                BossEntity fisher = net.tierrasfantasticas.tfclient.npcs.TFNpcs.selected(player);
                r.event("pescador de la prueba: " + (fisher == null ? "NO SALE" : net.tierrasfantasticas.tfclient.skills.SkillMobs.animNames(fisher)));
                look(player, level, o.x - 30 + 6, o.y + 2.5, o.z + 70 - 1.5, o.x - 30, o.y + 1.2, o.z + 70 - 1.5);
            }
            case 944 -> shot(r, "npc_pescador");
            // (1.3.103) Un dúo con la piel del panel, desde los dos lados: la cara de cada uno, delante (la piel del 2.º
            // personaje le dejaba la cara y el pecho en la espalda; el dueño: «tienen la cara atrás»)
            case 950 -> player.teleportTo(level, o.x - 36, o.y, o.z + 70, 180F, 0F);
            case 954 -> {
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PLACE, "npc_tavern", "scene_tavern_drunk_duo", 1, 0, 0));
                BossEntity duo = net.tierrasfantasticas.tfclient.npcs.TFNpcs.selected(player);
                r.event("dúo de la prueba: " + (duo == null ? "NO SALE" : duo.getYRot() + "°"));
            }
            case 990 -> look(player, level, o.x - 36 + 3.2, o.y + 2.2, o.z + 70 + 3.2, o.x - 36, o.y + 1.1, o.z + 70);
            case 1030 -> shot(r, "npc_duo_a");
            case 1034 -> look(player, level, o.x - 36 + 3.2, o.y + 2.2, o.z + 70 - 3.2, o.x - 36, o.y + 1.1, o.z + 70);
            case 1074 -> shot(r, "npc_duo_b");
            case 1720 -> next(level);
            default -> { }
        }
        // (1.3.106) De cerca, los NPC que se sientan o se tumban en algo, con sus muebles (tools/npcs/muebles.py), y los
        // muebles de la taberna puestos con el panel (el dueño: «se sientan en algo que no está; mete su silla, su mesa»)
        if (f >= 1090 && f < 1090 + MUEBLES.length * 8) {
            int mi = (f - 1090) / 8, ms = (f - 1090) % 8;
            Vec3 mat = mueble(o, mi);
            if (ms == 0) player.teleportTo(level, mat.x, mat.y, mat.z, 180F, 0F);
            if (ms == 4) {
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PLACE, MUEBLES[mi][0], MUEBLES[mi][1], 1, 0, 0));
                BossEntity mb = net.tierrasfantasticas.tfclient.npcs.TFNpcs.selected(player);
                r.event("mueble " + MUEBLES[mi][1] + ": " + (mb == null ? "NO SALE" : "puesto (" + mb.mobName() + ")"));
            }
        }
        int m0 = 1090 + MUEBLES.length * 8 + 10;
        for (int mg = 0; mg < (MUEBLES.length + 3) / 4; mg++) {
            Vec3 ma = mueble(o, mg * 4), mz = mueble(o, Math.min(MUEBLES.length - 1, mg * 4 + 3));
            double mx = (ma.x + mz.x) / 2;
            if (f == m0 + mg * 120) look(player, level, mx + 2.5, o.y + 2.6, ma.z + 6.5, mx, o.y + 0.7, ma.z);
            if (f == m0 + mg * 120 + 50) shot(r, "npc_muebles_" + (mg + 1) + "_frente");
            if (f == m0 + mg * 120 + 60) look(player, level, mx + 7.5, o.y + 2.2, ma.z + 1.5, mx, o.y + 0.7, ma.z);
            if (f == m0 + mg * 120 + 110) shot(r, "npc_muebles_" + (mg + 1) + "_lado");
        }
        if (f > 154 && f < 434 && f % 40 == 0 && npcVendor != null) {
            Vec3 m = new Vec3(o.x - 30, o.y, o.z + 40);
            npcTrack.append(String.format(" %.1f/%.1f", npcVendor.position().distanceTo(new Vec3(m.x + 6, m.y, m.z)),
                    npcVendor.position().distanceTo(new Vec3(m.x + 6, m.y, m.z + 8))));
        }
        if (f > 453 && f < 768 && f % 40 == 0 && npcWalker != null && npcWalker.isAlive()) {
            npcTrack.append(String.format(" %.1f,%.1f", npcWalker.getX() - (o.x - 60), npcWalker.getZ() - o.z));
        }
    }

    /** (1.3.103) El TF Pad abierto: la portada, Música y Efectos (el dueño: «el pad perdió varias de sus letras»). */
    private static void padStep(ServerLevel level, ServerPlayer player, Rep r) {
        switch (t) {
            case 1 -> player.teleportTo(level, arena.x, arena.y, arena.z, 0F, 0F);
            case 20 -> openPad = "";
            case 80 -> shot(r, "pad_portada");
            case 90 -> openPad = "musica";
            // (1.3.106) algo escrito en la casilla del link: el dueño no ve lo que escribe
            case 120 -> typePad = "https://texto que escribo en la prueba";
            case 150 -> shot(r, "pad_musica");
            // (1.3.106) como en el juego del dueño: los recuadros, después del texto; primero sin el arreglo (el texto
            // sin subir: así lo veía él) y luego con él
            case 152 -> {
                net.tierrasfantasticas.tfclient.pad.TFPadScreen.testNoLift = true;
                net.tierrasfantasticas.tfclient.pad.TFPadScreen.testBoxesLast = true;
            }
            case 170 -> shot(r, "pad_musica_recuadros_despues_sin_arreglo");
            case 172 -> net.tierrasfantasticas.tfclient.pad.TFPadScreen.testNoLift = false;
            case 190 -> shot(r, "pad_musica_recuadros_despues");
            case 192 -> net.tierrasfantasticas.tfclient.pad.TFPadScreen.testBoxesLast = false;
            case 200 -> openPad = "efectos";
            case 260 -> shot(r, "pad_efectos");
            case 262 -> net.tierrasfantasticas.tfclient.pad.TFPadScreen.testBoxesLast = true;
            case 280 -> shot(r, "pad_efectos_recuadros_despues");
            case 282 -> net.tierrasfantasticas.tfclient.pad.TFPadScreen.testBoxesLast = false;
            // (1.3.109, el dueño: «la espada Valentine está invertida en la vista previa de combate y no se ve la
            // estela») con la Valentine en la mano, la pestaña COMBATE de Efectos, varias veces durante el tajo
            case 284 -> {
                net.minecraft.world.item.Item sword = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new net.minecraft.resources.ResourceLocation("tfclient", "valentine_sword"));
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(sword == null ? Items.DIAMOND_SWORD : sword));
                r.event("en la mano: " + player.getMainHandItem().getItem());
            }
            case 286 -> openPad = "efectos";
            case 330 -> actPad = "tab:combate";
            // (en el cliente de la prueba las estelas van apagadas: se encienden aquí, como el botón ENCENDER)
            case 334 -> actPad = "on";
            case 360, 362, 364, 366, 368, 370, 372 -> shot(r, "pad_combate_" + (t - 358) / 2);
            // (1.3.109, el dueño: «en el pad te comiste cosas: texto borrado en las protes») una zona de verdad, su
            // página de ajustes bajando hasta «La zona», normal y con los recuadros después del texto; y un NPC dentro
            // con «¿Arden los monstruos?» (el dueño: «los NPC se mueren dentro de la prote, se queman»)
            case 376 -> {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                var claim = net.tierrasfantasticas.tfclient.claims.data.ClaimManager.getInstance().createClaim(level, player.blockPosition(), player,
                        net.tierrasfantasticas.tfclient.claims.data.ClaimTier.byId("claimstone_300x300"));
                claim.getFlags().burnHostiles = true;
                padClaim = claim.getClaimId();
                net.tierrasfantasticas.tfclient.npcs.TFNpcs.act(player, npcAct(net.tierrasfantasticas.tfclient.npcs.NpcNet.PLACE, "npc_guards_vol1", "scene_guard_stand", 1, 0, 0));
                padNpc = net.tierrasfantasticas.tfclient.npcs.TFNpcs.selected(player);
                r.event("zona de la prueba " + padClaim + " (300x300, arden los monstruos); NPC dentro: " + (padNpc == null ? "NO SALE" : String.format(
                        "vida %.1f en %.1f %.1f %.1f", padNpc.getHealth(), padNpc.getX(), padNpc.getY(), padNpc.getZ())));
            }
            case 380 -> openPad = "protecciones/z:" + padClaim + ":a";
            case 420 -> shot(r, "pad_protecciones");
            case 422 -> actPad = "§scroll:380";
            case 432 -> shot(r, "pad_protecciones_380");
            case 434 -> actPad = "§scroll:470";
            case 444 -> shot(r, "pad_protecciones_470");
            case 446 -> net.tierrasfantasticas.tfclient.pad.TFPadScreen.testBoxesLast = true;
            case 456 -> shot(r, "pad_protecciones_470_recuadros_despues");
            case 458 -> {
                net.tierrasfantasticas.tfclient.pad.TFPadScreen.testBoxesLast = false;
                actPad = "§scroll:680";
            }
            case 466 -> shot(r, "pad_protecciones_680");
            // como en el juego del dueño: el texto, después de todo lo demás (ahí se borraba «La zona»); luego así y
            // sin el arreglo (para ver que la prueba lo reproduce)
            case 468 -> net.tierrasfantasticas.tfclient.pad.TFPadScreen.testTextLast = true;
            case 472 -> shot(r, "pad_protecciones_680_texto_despues");
            case 474 -> net.tierrasfantasticas.tfclient.pad.TFPadScreen.testNoSoft = true;
            case 478 -> {
                shot(r, "pad_protecciones_680_texto_despues_sin_arreglo");
                net.tierrasfantasticas.tfclient.pad.TFPadScreen.testNoSoft = false;
                net.tierrasfantasticas.tfclient.pad.TFPadScreen.testTextLast = false;
            }
            case 481 -> {
                closeScreen = true;
                r.event("NPC en la zona tras 5 s: " + (padNpc == null ? "-" : String.format("%s, vida %.1f, ardiendo %s, en %.1f %.1f %.1f",
                        padNpc.isAlive() ? "vivo" : "MUERTO", padNpc.getHealth(), padNpc.isOnFire() ? "SÍ" : "no", padNpc.getX(), padNpc.getY(), padNpc.getZ())));
            }
            case 482 -> {
                if (padNpc != null) padNpc.discard();
                if (padClaim != null) {
                    var cm = net.tierrasfantasticas.tfclient.claims.data.ClaimManager.getInstance();
                    var c = cm.findClaimById(padClaim);
                    if (c != null) cm.removeClaim(level, new net.minecraft.core.BlockPos(c.getX(), c.getY(), c.getZ()));
                }
            }
            // (1.3.109) los bots: 4 que entran de uno en uno, la lista de jugadores (TAB) con sus caras y fuera
            case 492 -> {
                var src = player.createCommandSourceStack().withPermission(4);
                level.getServer().getCommands().performPrefixedCommand(src, "tf bots add 4 1");
            }
            // (1.3.112) /tpa y /msg a un bot: los contesta BotCommands como EssentialsX (sin «entidad no encontrada») y la
            // petición se deniega sola a los 5 s
            case 560 -> {
                var bots = net.tierrasfantasticas.tfclient.server.TFBots.online();
                if (!bots.isEmpty()) {
                    String b = bots.get(0).name();
                    var src = player.createCommandSourceStack();
                    level.getServer().getCommands().performPrefixedCommand(src, "tpa " + b);
                    level.getServer().getCommands().performPrefixedCommand(src, "msg " + b + " hola, ¿vienes a la mina?");
                    r.event("bots: /tpa y /msg a " + b + "; peticiones esperando: "
                            + net.tierrasfantasticas.tfclient.server.BotCommands.pending(player.getUUID()));
                }
            }
            case 600 -> tabList = true;
            case 640 -> shot(r, "tablista");
            case 644 -> {
                tabList = false;
                botsFirst = botNames();
                r.event("bots: " + net.tierrasfantasticas.tfclient.server.TFBots.count() + " dentro en el servidor; el cliente ve "
                        + clientTab + " en la lista (" + clientTabSkins + " con su skin): " + botsFirst);
                var src = player.createCommandSourceStack().withPermission(4);
                level.getServer().getCommands().performPrefixedCommand(src, "tf bots remove all");
            }
            case 660 -> r.event("bots tras /tf bots remove all: " + net.tierrasfantasticas.tfclient.server.TFBots.count()
                    + " en el servidor; el cliente ve " + clientTab + " en la lista");
            // (1.3.111) otra tanda: al azar, no los mismos (los que acaban de salir no vuelven enseguida)
            case 664 -> {
                var src = player.createCommandSourceStack().withPermission(4);
                level.getServer().getCommands().performPrefixedCommand(src, "tf bots add 4 0");
            }
            case 672 -> r.event("bots: a los 5 s, peticiones esperando: "
                    + net.tierrasfantasticas.tfclient.server.BotCommands.pending(player.getUUID()) + " (0 = el bot la denegó)");
            case 670 -> {
                java.util.List<String> second = botNames();
                java.util.List<String> both = new java.util.ArrayList<>(second);
                both.retainAll(botsFirst);
                r.event("bots, segunda tanda: " + second + "; repetidos de la primera: " + both.size());
            }
            // (1.3.109, el dueño: «dale rework a la textura de las claims, pon texturas buenas, oscuras») las diez piedras
            // de protección en fila, de cerca
            case 662 -> {
                String[] tiers = {"10x10", "25x25", "40x40", "64x64", "80x80", "100x100", "150x150", "250x250", "300x300", "500x500"};
                net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(arena.x - 9, arena.y, arena.z + 7);
                for (int i = 0; i < tiers.length; i++) {
                    var blk = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                            new net.minecraft.resources.ResourceLocation("tfclient", "proteccion_" + tiers[i]));
                    if (blk != null) level.setBlock(base.offset(i * 2, 0, 0), blk.defaultBlockState(), 3);
                }
                look(player, level, arena.x, arena.y + 1.0, arena.z, arena.x, arena.y + 0.5, arena.z + 7.5);
            }
            case 676 -> shot(r, "piedras_proteccion");
            case 678 -> {
                net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(arena.x - 9, arena.y, arena.z + 7);
                for (int i = 0; i < 10; i++) level.setBlock(base.offset(i * 2, 0, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            // (1.3.113, el dueño: «los bots debe reconocerlos el pad, deben salir conectados, con trabajos y datos») la
            // lista de Jugadores del pad con los bots de la segunda tanda y la ficha de uno
            case 686 -> openPad = "jugadores";
            case 720 -> shot(r, "pad_jugadores");
            case 722 -> {
                var bots = net.tierrasfantasticas.tfclient.server.TFBots.online();
                if (!bots.isEmpty()) {
                    openPad = "jugadores/f:" + bots.get(0).id();
                    var c = net.tierrasfantasticas.tfclient.server.TFBots.card(bots.get(0),
                            new java.util.ArrayList<>(net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.jobs.keySet()),
                            net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.maxJobs);
                    r.event("pad: ficha del bot " + bots.get(0).name() + ": oficios " + c.jobs() + " " + java.util.Arrays.toString(c.jobLevels())
                            + ", " + c.missions() + " misiones, " + c.hunts() + " cazas, " + c.photos() + " fotos, " + c.likes() + " likes, "
                            + c.explores() + " exploraciones, " + c.travels() + " viajes");
                }
            }
            case 756 -> shot(r, "pad_jugadores_bot");
            // (1.3.113, el dueño: «etiqueta solapada en el TF Pass y los números muy pegados abajo»)
            case 758 -> openPad = "pase";
            case 800 -> shot(r, "pad_pase");
            case 802 -> {
                closeScreen = true;
                var src = player.createCommandSourceStack().withPermission(4);
                level.getServer().getCommands().performPrefixedCommand(src, "tf bots remove all");
                // (1.3.113, el dueño: «el mensaje de bienvenida como un título, tipo Casa de Juan, sin (protección)») una
                // zona con su mensaje como título, a la que se entra
                var claim = net.tierrasfantasticas.tfclient.claims.data.ClaimManager.getInstance().createClaim(level,
                        net.minecraft.core.BlockPos.containing(arena.x + 60, arena.y, arena.z), player,
                        net.tierrasfantasticas.tfclient.claims.data.ClaimTier.byId("claimstone_25x25"));
                if (claim != null) {
                    claim.getFlags().welcomeMessage = "Casa de Juan";
                    claim.getFlags().showWelcome = true;
                    claim.getFlags().messageAsTitle = true;
                    padClaim = claim.getClaimId();
                }
                r.event("zona con título: " + (claim == null ? "NO SE CREÓ" : padClaim));
            }
            case 806 -> player.teleportTo(level, arena.x + 60, arena.y, arena.z, 0F, 0F);
            case 826 -> shot(r, "proteccion_titulo");
            case 830 -> {
                if (padClaim != null) {
                    var cm = net.tierrasfantasticas.tfclient.claims.data.ClaimManager.getInstance();
                    var c = cm.findClaimById(padClaim);
                    if (c != null) cm.removeClaim(level, new net.minecraft.core.BlockPos(c.getX(), c.getY(), c.getZ()));
                }
                player.teleportTo(level, arena.x, arena.y, arena.z, 0F, 0F);
            }
            // (1.3.113, el dueño: «no arreglaste la textura del modelo Oni, ¿no ves esa flecha?») las lanzas de Oni en
            // las dos manos, mirando al cielo como en su captura, y desde delante
            case 834 -> {
                for (var hand : net.minecraft.world.InteractionHand.values()) {
                    net.minecraft.world.item.Item it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(
                            "tfclient", hand == net.minecraft.world.InteractionHand.MAIN_HAND ? "oni_left_spear" : "oni_right_spear"));
                    player.setItemInHand(hand, it == null ? ItemStack.EMPTY : new ItemStack(it));
                }
                r.event("en las manos: " + player.getMainHandItem().getItem() + " y " + player.getOffhandItem().getItem());
                player.teleportTo(level, arena.x, arena.y, arena.z, 20F, -40F);
            }
            case 850 -> shot(r, "oni_lanzas_mano");
            case 852 -> cameraType = 2;
            case 868 -> shot(r, "oni_lanzas_delante");
            case 870 -> {
                cameraType = 0;
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            // (1.3.114, el dueño con shaders: «mis items se ven así», los encantados como siluetas moradas) el inventario
            // con objetos encantados, con el arreglo (ShaderPacks.fixBlendForGui) y sin él
            case 874 -> {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                net.minecraft.world.item.EnchantedBookItem.addEnchantment(book,
                        new net.minecraft.world.item.enchantment.EnchantmentInstance(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 5));
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 5);
                ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
                chest.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 4);
                ItemStack[] items = {book, sword, chest, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3), new ItemStack(Items.EXPERIENCE_BOTTLE, 64),
                        new ItemStack(Items.BREAD, 16)};
                for (int i = 0; i < items.length; i++) player.getInventory().setItem(9 + i, items[i]);
                player.getInventory().setItem(0, book.copy());
                openInventory = true;
            }
            case 900 -> shot(r, "inventario_encantados");
            case 902 -> net.tierrasfantasticas.tfclient.vacio.ShaderPacks.testNoUnlock = true;
            case 920 -> shot(r, "inventario_encantados_sin_arreglo");
            case 922 -> {
                net.tierrasfantasticas.tfclient.vacio.ShaderPacks.testNoUnlock = false;
                r.event("inventario con shaders: " + net.tierrasfantasticas.tfclient.vacio.ShaderPacks.inUse() + "; la mezcla de Oculus estaba "
                        + "bloqueada al pintar un objeto " + net.tierrasfantasticas.tfclient.vacio.ShaderPacks.blendUnlocks + " veces y OpenGL "
                        + "no coincidía con la caché de Minecraft " + net.tierrasfantasticas.tfclient.vacio.ShaderPacks.blendDesyncs + " veces");
                closeScreen = true;
                for (int i = 0; i < 16; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
            }
            case 926 -> next(level);
            default -> { }
        }
    }

    /** (1.3.114) El cliente de la prueba abre el inventario (para ver los objetos encantados con shaders). */
    static volatile boolean openInventory;

    /** (1.3.114) Sin el HUD en las capturas (las de los caballeros: el chat tapaba las piernas). */
    static volatile boolean hideGui;
    /** (1.3.114) Lo que ve el cliente de los caballeros sacros: lo más rápido que van y lo más que mueven las piernas. */
    static volatile String clientKnights = "?";

    /** (1.3.113) La cámara del cliente de la prueba: 0 primera persona, 1 de espaldas, 2 de frente (-1: no se toca). */
    static volatile int cameraType = -1;

    private static java.util.List<String> botsFirst = java.util.List.of();

    private static java.util.List<String> botNames() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var b : net.tierrasfantasticas.tfclient.server.TFBots.online()) out.add(b.name());
        return out;
    }

    /** (1.3.109) La lista de jugadores abierta (la tecla del TAB) y lo que ve el cliente en ella (BossTestClient). */
    static volatile boolean tabList;
    static volatile int clientTab, clientTabSkins;

    private static net.tierrasfantasticas.tfclient.npcs.NpcNet.Act npcAct(int op, String a, String b, double x, double y, double z) {
        return new net.tierrasfantasticas.tfclient.npcs.NpcNet.Act(op, -1, a, b, x, y, z);
    }

    private static net.tierrasfantasticas.tfclient.crates.config.CrateConfig testCrate(int run) {
        var cfg = new net.tierrasfantasticas.tfclient.crates.config.CrateConfig("prueba_" + run);
        cfg.displayName = run == 0 ? "§5✦ Crate Épica ✦" : "§c✦ Crate Mítica ✦";
        cfg.rarity = run == 0 ? net.tierrasfantasticas.tfclient.crates.config.Rarity.EPIC : net.tierrasfantasticas.tfclient.crates.config.Rarity.MYTHIC;
        cfg.styleId = run == 0 ? "classic_epic" : "cine_mythical";
        cfg.consumeKey = false;
        cfg.rarityChances.clear();
        cfg.rarityChances.put(cfg.rarity, 100.0);
        net.minecraft.world.item.Item[] its = {Items.DIAMOND, Items.NETHERITE_INGOT, Items.GOLDEN_APPLE, Items.EMERALD, Items.DIAMOND_SWORD,
                Items.ENCHANTED_BOOK, Items.TOTEM_OF_UNDYING, Items.ELYTRA, Items.BEACON, Items.NETHER_STAR};
        for (net.minecraft.world.item.Item it : its) {
            var re = new net.tierrasfantasticas.tfclient.crates.config.RewardEntry();
            re.item = new ItemStack(it);
            re.chance = 10.0;
            re.label = it.getDescription().getString();
            cfg.rewards.add(re);
        }
        return cfg;
    }

    private static void cortaStep(MinecraftServer server, ServerLevel level, ServerPlayer player, Rep r) {
        ServerLevel vl = server.getLevel(net.tierrasfantasticas.tfclient.vacio.TFVoid.VOID);
        if (vl == null) {
            r.event("NO HAY DIMENSIÓN tfclient:void");
            next(level);
            return;
        }
        if (t == 1) {
            fly(player, true);
            net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sendToVoid(player);
            r.event("al Void con su viaje (las capturas del túnel)");
        }
        if (t == 420) r.event("el viaje: " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.travel());
        // los cinco caballeros sacros, de cerca
        if (t >= 440 && t < 840) {
            int i = (t - 440) / 80, k = (t - 440) % 80;
            Entity e = net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sacredKnight(vl, i + 1);
            if (k == 0 && e != null) {
                fly(player, true);
                hideGui = true;
                Vec3 f = Vec3.directionFromRotation(0, e.getYRot());
                look(player, vl, e.getX() + f.x * 4.5, e.getY() + 2.2, e.getZ() + f.z * 4.5, e.getX(), e.getY() + 1.6, e.getZ());
            }
            if (k == 60) shot(r, "caballero_" + (i + 1));
            // (1.3.114, el dueño: «los caballeros hacen la animación de volar, no caminan») que ande de lado delante de la
            // cámara: la captura y lo que mide el cliente (BossTestClient: las piernas al andar)
            if (k == 62 && e instanceof net.minecraft.world.entity.Mob mob) {
                Vec3 f = Vec3.directionFromRotation(0, e.getYRot() + 90F);
                mob.getNavigation().moveTo(e.getX() + f.x * 5, e.getY(), e.getZ() + f.z * 5, 0.55);
            }
            if (k == 76) shot(r, "caballero_" + (i + 1) + "_andando");
        }
        if (t == 840) {
            hideGui = false;
            r.event(net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.sacredGround(vl));
            r.event("caballeros en el cliente: " + clientKnights);
        }
        // el soporte de la armadura: ¿salta? (su altura durante 3 s)
        if (t >= 850 && t <= 920) {
            net.minecraft.core.BlockPos a = net.tierrasfantasticas.tfclient.vacio.VoidIsland.altarPos();
            if (t == 850 && a != null) {
                fly(player, true);
                look(player, vl, a.getX() + 0.5, a.getY() + 2, a.getZ() + 5.5, a.getX() + 0.5, a.getY() + 1, a.getZ() + 0.5);
                standMin = 1e9;
                standMax = -1e9;
            }
            Entity s = net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.altarStand(vl);
            if (t > 860 && s != null) {
                standMin = Math.min(standMin, s.getY());
                standMax = Math.max(standMax, s.getY());
            }
            if (t == 900) shot(r, "altar");
            if (t == 920) r.event(s == null ? "el soporte del altar: NO ESTÁ" : String.format("el soporte del altar: y de %.2f a %.2f (%s), sin gravedad %s",
                    standMin, standMax, standMax - standMin < 0.01 ? "quieto, bien" : "SE MUEVE", s.isNoGravity() ? "sí" : "NO")
                    + "; " + net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.standGround(vl));
        }
        // la Llegada, el puente, la isla de la grieta y el Ancla nueva
        if (t >= 940 && t < 940 + CORTA_VIEWS.length * 100) {
            int v = (t - 940) / 100, k = (t - 940) % 100;
            double[] a = CORTA_VIEWS[v];
            // (volando: tras el viaje al Void se le quitaba y caía al vacío —el rescate lo llevaba al santuario—)
            if (k == 0) fly(player, true);
            if (k <= 80) look(player, vl, a[0], a[1], a[2], a[3], a[4], a[5]);
            if (k == 80) shot(r, CORTA_NAMES[v]);
        }
        // la fauna: cada criatura, 12 s detrás del jugador
        int f0 = 940 + CORTA_VIEWS.length * 100;
        if (t >= f0 && t < f0 + 2 * 260) {
            int i = (t - f0) / 260, k = (t - f0) % 260;
            String mob = FAUNA_MOBS[i];
            Vec3 seed = net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.faunaSpot();
            if (k == 0) {
                fly(player, false);
                player.teleportTo(vl, seed.x, seed.y, seed.z, 180F, 8F);
                faunaHits = 0;
                faunaTrack.setLength(0);
                player.setLastHurtByMob(null);
            }
            if (k == 20) {
                int y = vl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(seed.x), (int) Math.floor(seed.z - 10));
                faunaNow = net.tierrasfantasticas.tfclient.vacio.VoidFauna.spawn(vl, mob, new Vec3(seed.x, y, seed.z - 10), 0F);
                if (faunaNow == null) r.event(mob + ": NO SALE");
            }
            if (faunaNow != null && faunaNow.isAlive() && player.getLastHurtByMob() == faunaNow) faunaHits++;
            if (faunaNow != null && faunaNow.isAlive() && k > 20 && k % 20 == 0) {
                faunaTrack.append(String.format(" %.0f", faunaNow.distanceTo(player)));
                List<BossEntity> wr = vl.getEntitiesOfClass(BossEntity.class, faunaNow.getBoundingBox().inflate(48),
                        e -> e != faunaNow && e.isAlive() && e.def() != null && "end_wraith".equalsIgnoreCase(e.def().name));
                if (!wr.isEmpty()) {
                    faunaTrack.append('[');
                    for (BossEntity w : wr) faunaTrack.append(String.format("%.0f%s ", w.distanceTo(player), w.onGround() ? "" : "^"));
                    faunaTrack.append(']');
                }
            }
            if (k == 160) shot(r, "fauna_" + mob);
            if (k == 250) {
                r.event(String.format("%s: me ha golpeado %s; distancia cada segundo (y sus espectros):%s", mob, faunaHits > 0 ? "sí (" + faunaHits + " ticks)" : "NO",
                        faunaTrack));
                for (BossEntity e : vl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(BossEntity.class), BossEntity::isFauna)) e.discard();
                faunaNow = null;
            }
        }
        if (t == f0 + 2 * 260 + 10) next(level);
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
