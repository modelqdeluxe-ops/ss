package net.tierrasfantasticas.tfclient.bosses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.tierrasfantasticas.tfclient.skills.SkillAI;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;

/**
 * Un mob de un jefe: el jefe (tfclient:jefe) o uno de sus esbirros (tfclient:esbirro: los súbditos del Esqueleto, los
 * brotes de la Abominación, los retoños de Astwood). Es una entidad de verdad: se le pega, tiene vida, persigue a su
 * objetivo y muere; lo que hace (sus ataques, sus fases) son sus skills de MythicMobs (ver {@link SkillMobs}), y se ve
 * con su modelo de ModelEngine (el actor que lleva encima, ver BossRenderer).
 *
 * <p>Lo que no es de Minecraft: la vida no tiene el tope de 1024 (los jefes en Demencial pasan de 6.000), varios
 * jugadores pueden pegarle a la vez (cada uno con su propio respiro de golpes, no el de todos), elige objetivo por la
 * amenaza (quién le ha hecho más daño) y el cuerpo gira como en ModelEngine (la cabeza primero y el cuerpo detrás, con
 * bodyrotation). No se guarda en el mundo: si se descarga, su grieta vuelve a estar lista.
 */
public class BossEntity extends PathfinderMob implements SkillMobs.Host, Enemy {
    private static final EntityDataAccessor<String> KEY = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> MAX_HP = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BODY_YAW = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.FLOAT);
    /** model{hitboxscale} / scale{hitbox=true}: escala de su caja. */
    private static final EntityDataAccessor<Float> HB_SCALE = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.FLOAT);
    /** 1 = model{hitbox=false} (la caja de su tipo de Minecraft), 2 = model{showhitbox=false} (no se le apunta). */
    private static final EntityDataAccessor<Byte> HB_FLAGS = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.BYTE);
    /** Cajas de golpe por piezas que puede tener un jefe (las cabezas, cuellos y cola de la Hidra son 35). */
    public static final int MAX_PARTS = 40;

    private final boolean main;
    private final BossPart[] parts;
    private SkillDefs.ClassDef cls;
    private SkillDefs.MobDef def;
    private VfxModel model;
    private String loadedKey = "";
    /** El jefe del grupo (null = él mismo). */
    private BossEntity owner;
    private Difficulty difficulty = Difficulty.NORMAL;
    private double healthScale = 1.0;
    private double speedMul = 1.0;
    private boolean speedSet;
    private boolean ai = true;
    private boolean locked;
    private float maxHead = 30F, maxBody = 30F;
    private int rotDelay, rotDuration;
    private int turnWait;
    private int blendUntil;
    private int nextBackstep;
    private float bodyYaw;
    /** Hacia dónde mira la entidad (ver {@link #lookYaw()}): sigue a su objetivo aunque el modelo gire despacio. */
    private float lookYaw;
    private int age;
    private int deathLength = 60;
    private LivingEntity target;
    private final Map<UUID, Double> threat = new HashMap<>();
    private final Map<UUID, Integer> lastHitBy = new HashMap<>();
    /** Cuándo le disparó cada jugador por última vez (el cerebro va a por los que disparan desde lejos). */
    private final Map<UUID, Integer> shotBy = new HashMap<>();
    private ServerBossEvent bar;
    private UUID rift;
    private Vec3 home;
    private int repath;
    /** setinvulnerable{v} del pack: no le entran golpes. */
    private boolean packInvulnerable;
    /** goto{} del pack: a dónde anda (deja de perseguir) y hasta qué tick, a qué velocidad. */
    private Vec3 gotoGoal;
    private int gotoUntil;
    private double gotoSpeed = 1.0;
    /** Dónde apareció (@SpawnLocation): la grieta o, si no tiene, su primer sitio. */
    private Vec3 spawnAt;
    /** move{} de ModelEngine: hasta qué tick lo mueve el pack (sin buscar camino). */
    private int moveUntil;
    /** model{syncbody}: el cuerpo sigue a la cabeza. */
    private boolean syncBodyOn = true;
    /**
     * Su IA de MythicMobs ahora (AIGoalSelectors y AITargetSelectors sobre la de su tipo, ver SkillAI): la de su
     * MobDef al aparecer; runaigoalselector, runaitargetselector y resetai la cambian.
     */
    private List<String> goals = List.of();
    private List<String> aiTargets = List.of();
    /** MeleeAttackGoal: ticks hasta que puede volver a pegar (20 entre golpes). */
    private int meleeWait;
    /** RandomStrollGoal, PanicGoal, FleeSunGoal: a dónde va sin objetivo, a qué velocidad y si es huyendo. */
    private Vec3 wanderGoal;
    private double wanderSpeed = 1.0;
    private boolean panicking;
    /** LookAtPlayerGoal: a quién mira (sin objetivo) y hasta cuándo. */
    private Player lookPlayer;
    private int lookUntil;

    public BossEntity(EntityType<? extends BossEntity> type, Level level, boolean main) {
        super(type, level);
        this.main = main;
        this.xpReward = 0;
        this.setMaxUpStep(1.1F);
        this.setPersistenceRequired();
        if (main) {
            parts = new BossPart[MAX_PARTS];
            for (int i = 0; i < MAX_PARTS; i++) parts[i] = new BossPart(this, i);
            // Las piezas van justo después del jefe (como el dragón del End): mismos ids en el servidor y el cliente
            this.setId(this.getId());
        } else {
            parts = new BossPart[0];
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.ARMOR, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(KEY, "");
        this.entityData.define(MAX_HP, 0F);
        this.entityData.define(BODY_YAW, 0F);
        this.entityData.define(HB_SCALE, 1F);
        this.entityData.define(HB_FLAGS, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        // Sin objetivos de Minecraft: anda, mira y ataca con lo de abajo y con sus skills
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                // El cuerpo lo gira este mob (bodyrotation de ModelEngine), no Minecraft
            }
        };
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        return animStep;
    }

    // ------------------------------------------------------------------------------------------- qué es

    /** Lo pone en el mundo como el mob «mob» del jefe «boss», con esa dificultad. */
    public void configure(String boss, String mob, Difficulty d, int players, BossEntity owner) {
        this.entityData.set(KEY, boss + "|" + mob);
        load();
        this.owner = owner;
        this.difficulty = d == null ? Difficulty.NORMAL : d;
        if (def == null) return;
        goals = new ArrayList<>(def.goals);
        aiTargets = new ArrayList<>(def.aiTargets);
        // Options: NoAI (los efectos de los packs que se mueven a golpe de teleport): sin IA desde que aparece
        if (def.noAi) setAi(false);
        // El empuje de su golpe (ATTACK_KNOCKBACK de su tipo de Minecraft: el devastador empuja, el hoglin lanza)
        getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(switch (def.type) {
            case "RAVAGER" -> 1.5;
            case "HOGLIN", "ZOGLIN" -> 1.0;
            default -> 0.0;
        });
        SkillDefs.BossInfo bi = cls.bossInfo;
        double hp;
        if (owner == null && bi != null && def.name.equalsIgnoreCase(bi.mob)) {
            hp = difficulty.healthFor(bi.health, players);
            healthScale = hp / Math.max(1, bi.packHealth);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(bi.damage * difficulty.damage());
            getAttribute(Attributes.ARMOR).setBaseValue(difficulty.armor());
        } else {
            // Un esbirro: su vida del pack con la dificultad; su daño, el del pack con la dificultad
            hp = Math.max(1, (def.health > 0 ? def.health : 20) * difficulty.health());
            healthScale = difficulty.health();
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Math.max(1, def.damage) * difficulty.damage());
            getAttribute(Attributes.ARMOR).setBaseValue(Math.min(20, difficulty.armor() / 2));
        }
        this.entityData.set(MAX_HP, (float) hp);
        setHealth((float) hp);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Math.max(16, Math.min(96, def.followRange)));
        applySpeed();
        bodyYaw = getYRot();
        lookYaw = bodyYaw;
        yBodyRot = bodyYaw;
        yHeadRot = bodyYaw;
        this.entityData.set(BODY_YAW, bodyYaw);
        if (model != null && model.anims.containsKey("death")) deathLength = Math.max(40, (int) (model.anims.get("death").length * 20) + 15);
        if (owner == null) {
            String color = def.barColor != null ? def.barColor : "PURPLE";
            bar = new ServerBossEvent(barTitle(), color(color), BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setDarkenScreen(false);
        }
    }

    public void setRift(UUID rift, Vec3 home) {
        this.rift = rift;
        this.home = home;
    }

    public UUID rift() {
        return rift;
    }

    private void load() {
        String key = this.entityData.get(KEY);
        if (key.equals(loadedKey)) return;
        loadedKey = key;
        int bar = key.indexOf('|');
        if (bar < 0) return;
        cls = SkillDefs.bosses().get(key.substring(0, bar));
        def = cls == null ? null : cls.mob(key.substring(bar + 1));
        model = def == null || def.model == null ? null : SkillMobs.model(def.model);
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KEY.equals(key)) load();
        if (HB_SCALE.equals(key) || HB_FLAGS.equals(key)) refreshDimensions();
    }

    /**
     * Su caja, como ModelEngine: la del hueso «hitbox» de su modelo (o, con model{hitbox=false}, la de su tipo de
     * Minecraft en el pack), a la escala de model{hitboxscale} / scale{hitbox=true}.
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float scale = 1F;
        byte flags = 0;
        try {
            scale = this.entityData.get(HB_SCALE);
            flags = this.entityData.get(HB_FLAGS);
        } catch (RuntimeException notYet) {
            // aún construyéndose
        }
        EntityDimensions d = null;
        if ((flags & 1) == 0 && model != null && model.hitW > 0 && model.hitH > 0) {
            d = EntityDimensions.scalable(model.hitW, model.hitH);
        } else if ((flags & 1) != 0 && def != null) {
            EntityType<?> t = EntityType.byString("minecraft:" + def.type.toLowerCase(java.util.Locale.ROOT)).orElse(null);
            if (t != null) d = t.getDimensions();
        }
        if (d == null) d = super.getDimensions(pose);
        return scale == 1F ? d : d.scale(scale);
    }

    public String bossId() {
        return cls == null ? "" : cls.id;
    }

    public boolean isMainBoss() {
        return owner == null && cls != null && cls.bossInfo != null && def != null && def.name.equalsIgnoreCase(cls.bossInfo.mob);
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    /** Es de una prueba de /tf boss (ni él ni su jefe son de una grieta). */
    public boolean isTest() {
        return rift == null && (owner == null || owner.rift == null);
    }

    public VfxModel model() {
        return model;
    }

    // ------------------------------------------------------------------------------------------- SkillMobs.Host

    @Override
    public Mob mob() {
        return this;
    }

    @Override
    public SkillDefs.ClassDef cls() {
        return cls;
    }

    @Override
    public SkillDefs.MobDef def() {
        return def;
    }

    @Override
    public SkillMobs.Host boss() {
        return owner != null ? owner : this;
    }

    @Override
    public double damageMul() {
        return difficulty.damage();
    }

    /**
     * Ritmo de ataque de la dificultad y, con el cerebro, la furia por fases: por debajo del 50 % y del 25 % de vida sus
     * cooldowns se acortan un poco más (hasta un 12 % cada fase con inteligencia 1).
     */
    @Override
    public double rhythm() {
        double r = difficulty.rhythm();
        double smart = difficulty.smart();
        if (smart >= 0.5 && isMainBoss()) {
            float hp = getHealth() / Math.max(1F, getMaxHealth());
            if (hp < 0.5F) r *= 1 + 0.12 * smart;
            if (hp < 0.25F) r *= 1 + 0.12 * smart;
        }
        return r;
    }

    @Override
    public double smart() {
        return difficulty.smart();
    }

    /**
     * Lo rápido que ataca: la velocidad de la dificultad (Fácil 0,9 … Demencial 1,25) y algo más cuanto más listo (hasta
     * +12 %). Sus animaciones y los retardos de sus ataques van a la vez, así los golpes caen donde se ven.
     */
    @Override
    public double tempo() {
        return difficulty.speed() * (1 + 0.12 * difficulty.smart());
    }

    @Override
    public int minLerp() {
        return age < blendUntil ? 5 : 0;
    }

    @Override
    public void blend(int ticks) {
        blendUntil = age + ticks;
    }

    @Override
    public double[] motion() {
        return new double[] {speedMul, speedSet ? 1 : 0, locked ? 1 : 0, maxHead, maxBody, rotDelay, rotDuration, ai ? 1 : 0};
    }

    @Override
    public void restoreMotion(double[] m) {
        if (m == null || m.length < 7) return;
        speedMul = m[0];
        speedSet = m[1] > 0;
        applySpeed();
        locked = m[2] > 0;
        setBodyRotation((float) m[3], (float) m[4], (int) m[5], (int) m[6]);
        if (m.length > 7 && (m[7] > 0) != ai) setAi(m[7] > 0);
    }

    /** Un salto corto hacia atrás (hacer sitio para su siguiente ataque, como los jefes de los Souls). */
    @Override
    public boolean backstep(Vec3 away) {
        if (!onGround() || age < nextBackstep || locked || !ai || dying()) return false;
        Vec3 d = new Vec3(away.x, 0, away.z);
        if (d.lengthSqr() < 1e-4) return false;
        d = d.normalize().scale(0.8);
        setDeltaMovement(d.x, 0.3, d.z);
        hurtMarked = true;
        nextBackstep = age + 70 + getRandom().nextInt(60);
        return true;
    }

    @Override
    public double healthScale() {
        return healthScale;
    }

    @Override
    public LivingEntity target() {
        return target;
    }

    /**
     * setai{ai=false} / Options: NoAI: como el setAI de Bukkit, el NoAI de Minecraft: ni piensa, ni anda, ni cae, ni
     * le mueven los empujones (sus skills siguen: es lo que usan los packs para dejarlo quieto en una pose).
     */
    @Override
    public void setAi(boolean on) {
        ai = on;
        setNoAi(!on);
        if (!on) getNavigation().stop();
    }

    /** resetai de MythicMobs: vuelve a los objetivos de IA de su tipo de Minecraft (aunque el pack los quitara). */
    @Override
    public void resetAi() {
        if (def != null) goals = new ArrayList<>(SkillAI.vanillaGoals(def.type));
        setAi(true);
    }

    @Override
    public void addAiGoal(String goal, boolean isTarget) {
        boolean clear = (isTarget ? SkillAI.targetName(goal) : SkillAI.goalName(goal)).equals("clear");
        if (isTarget) {
            aiTargets = SkillAI.apply(aiTargets, List.of(goal), true);
            // Al quitar sus objetivos de buscar, Minecraft olvida a quien perseguía (TargetGoal.stop)
            if (clear) {
                target = null;
                setTarget(null);
            }
        } else {
            goals = SkillAI.apply(goals, List.of(goal), false);
            if (clear) {
                getNavigation().stop();
                wanderGoal = null;
            }
        }
    }

    /** ¿Persigue a su objetivo? (meleeattack, movetowardstarget...: su IA de ahora). */
    private boolean chases() {
        return def != null && SkillAI.chases(goals);
    }

    @Override
    public boolean ai() {
        return ai;
    }

    @Override
    public void threat(LivingEntity who, String mode, double amount) {
        if (!(who instanceof Player p)) return;
        UUID id = p.getUUID();
        double now = threat.getOrDefault(id, 0.0);
        double top = 0;
        for (double v : threat.values()) top = Math.max(top, v);
        double v = switch (mode) {
            case "SUBTRACT", "REMOVE" -> now - amount;
            case "MULTIPLY" -> now * amount;
            case "DIVIDE" -> amount == 0 ? now : now / amount;
            case "SET" -> amount;
            case "RESET" -> 0;
            // Pasa a ser el primero (rally, taunt): por encima del que más, con el margen con el que cambia de objetivo
            case "FORCE" -> Math.max(now, top * 1.25 + 10 + amount);
            default -> now + amount;
        };
        threat.put(id, Math.max(0, v));
        if (mode.equals("FORCE")) {
            target = p;
            setTarget(p);
        }
    }

    @Override
    public void clearThreat() {
        threat.clear();
        target = null;
        setTarget(null);
    }

    @Override
    public List<LivingEntity> threatTable() {
        List<LivingEntity> out = new ArrayList<>();
        if (!(level() instanceof ServerLevel sl)) return out;
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
        threat.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).forEach(e -> {
            Player p = sl.getPlayerByUUID(e.getKey());
            if (p != null && p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(this) <= range * range) out.add(p);
        });
        return out;
    }

    @Override
    public void setPackInvulnerable(boolean on) {
        packInvulnerable = on;
    }

    @Override
    public void goTo(Vec3 at, double speed) {
        gotoGoal = at;
        gotoUntil = age + 200;
        gotoSpeed = speed <= 0 ? 1.0 : speed;
        repath = 0;
    }

    @Override
    public void setMaxHealth(double max, boolean alsoHealth) {
        float hp = (float) Math.max(1, max);
        this.entityData.set(MAX_HP, hp);
        if (alsoHealth || getHealth() > hp) setHealth(hp);
    }

    /** hitboxconfig del pack: hueso de la caja → cuánto daño deja pasar. */
    private final Map<String, Double> partPass = new HashMap<>();

    @Override
    public void hitboxPass(String part, double pass) {
        if (part == null || part.isEmpty()) return;
        partPass.put(part.toLowerCase(java.util.Locale.ROOT), Math.max(0, pass));
    }

    @Override
    public Vec3 spawnPos() {
        return home != null ? home : spawnAt != null ? spawnAt : position();
    }

    @Override
    public void suicide() {
        // Sin pasar por hurt (su ~onDamaged podría anularlo): muere ya, con su ~onDeath y su animación
        if (dying()) return;
        setHealth(0F);
        die(damageSources().genericKill());
    }

    @Override
    public void setSpeedMul(double m) {
        speedMul = Math.max(0, m);
        speedSet = true;
        applySpeed();
    }

    /**
     * Velocidad de andar: la de sus opciones (MovementSpeed) hasta que una skill pone setspeed{s}, que es la del tipo
     * de Minecraft por «s» (la Abominación sale con 0 y su setspeed{s=1.25} al aparecer la pone a andar).
     */
    private void applySpeed() {
        if (def == null) return;
        double sp = (speedSet ? def.typeSpeed : def.speed) * speedMul * difficulty.speed();
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.max(0, sp));
        if (sp <= 0) getNavigation().stop();
    }

    @Override
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public void setBodyRotation(float maxHead, float maxBody, int delay, int duration) {
        this.maxHead = Math.max(0, maxHead);
        this.maxBody = Math.max(0, maxBody);
        this.rotDelay = Math.max(0, delay);
        this.rotDuration = Math.max(0, duration);
    }

    @Override
    public void turn(float yaw, boolean relative) {
        bodyYaw = relative ? bodyYaw + yaw : yaw;
        yHeadRot = relative ? yHeadRot + yaw : bodyYaw;
        lookYaw = relative ? lookYaw + yaw : bodyYaw;
        syncBody();
    }

    @Override
    public void face(Vec3 at, boolean immediately) {
        Vec3 d = at.subtract(position());
        if (d.x * d.x + d.z * d.z < 1e-6) return;
        float want = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        if (immediately) bodyYaw = want;
        else bodyYaw += Mth.wrapDegrees(want - bodyYaw) * 0.5F;
        yHeadRot = want;
        lookYaw = want;
        syncBody();
    }

    @Override
    public float lookYaw() {
        return lookYaw;
    }

    @Override
    public void barStyle(String color, String style) {
        if (bar == null) return;
        if (color != null) bar.setColor(color(color));
    }

    /** barcreate / barremove del pack sobre su propia barra: se ve o se quita. */
    @Override
    public void barVisible(boolean on) {
        if (bar != null) bar.setVisible(on);
    }

    @Override
    public void selfDamage(double amount, boolean ignoreResistance) {
        if (dying() || amount <= 0) return;
        float hp = getHealth();
        setHealth((float) Math.max(0, hp - amount));
        if (getHealth() <= 0) die(damageSources().generic());
    }

    @Override
    public String faction() {
        return def == null ? null : def.faction;
    }

    @Override
    public int age() {
        return age;
    }

    @Override
    public boolean dying() {
        return isDeadOrDying() || isRemoved();
    }

    // ------------------------------------------------------------------------------------------- ModelEngine en la entidad

    /**
     * move{x;y;z;speed;global} de ModelEngine: este tick anda en esa dirección como si le empujara su «mando» (como un
     * caballo montado: la entrada por su velocidad × speed, en sus ejes o en los del mundo con global; sube escalones con
     * su step). Mientras el pack lo mueve, no busca camino.
     */
    @Override
    public void move(double side, double up, double front, double speed, boolean global) {
        if (dying()) return;
        Vec3 in = new Vec3(side, up, front);
        if (in.lengthSqr() < 1e-8) return;
        if (in.lengthSqr() > 1) in = in.normalize();
        Vec3 v = in.scale(getAttributeValue(Attributes.MOVEMENT_SPEED) * speed);
        if (!global) {
            float sin = Mth.sin(bodyYaw * Mth.DEG_TO_RAD), cos = Mth.cos(bodyYaw * Mth.DEG_TO_RAD);
            v = new Vec3(v.x * cos - v.z * sin, v.y, v.z * cos + v.x * sin);
        }
        setDeltaMovement(getDeltaMovement().add(v));
        moveUntil = age + 2;
        getNavigation().stop();
    }

    /** syncyaw de ModelEngine: «yaw» es hacia dónde mira la entidad, «body» el modelo y «head» su cabeza. */
    @Override
    public void syncYaw(String to, String from) {
        float v = switch (from) {
            case "yaw" -> lookYaw;
            case "body" -> bodyYaw;
            default -> yHeadRot;
        };
        if (to.equals("yaw") || to.equals("all")) {
            lookYaw = v;
            setYRot(v);
        }
        if (to.equals("head") || to.equals("all")) yHeadRot = v;
        if (to.equals("body") || to.equals("all")) {
            bodyYaw = v;
            syncBody();
        }
    }

    @Override
    public void setModelHitbox(boolean on) {
        byte f = this.entityData.get(HB_FLAGS);
        this.entityData.set(HB_FLAGS, (byte) (on ? f & ~1 : f | 1));
    }

    @Override
    public void setHitboxScale(float scale) {
        this.entityData.set(HB_SCALE, Math.max(0.01F, scale));
    }

    @Override
    public void setShowHitbox(boolean on) {
        byte f = this.entityData.get(HB_FLAGS);
        this.entityData.set(HB_FLAGS, (byte) (on ? f & ~2 : f | 2));
    }

    @Override
    public void setSyncBody(boolean on) {
        syncBodyOn = on;
    }

    /** model{renderfire;forcefire} de ModelEngine (el cliente sabe cuál tiene su modelo). */
    @Override
    public boolean displayFireAnimation() {
        int mode = level().isClientSide ? SkillMobs.clientFire.applyAsInt(this) : 0;
        if (mode > 0) return true;
        if (mode < 0) return false;
        return super.displayFireAnimation();
    }

    // ------------------------------------------------------------------------------------------- vida

    /**
     * La vida máxima de un jefe (sin el tope de 1024 del atributo), o 0 si no es un jefe o aún no tiene: la usa
     * BossHealthMixin para LivingEntity.getMaxHealth, que es final.
     */
    public static float bossMaxHealth(LivingEntity e) {
        if (!(e instanceof BossEntity b) || b.entityData == null) return 0F;
        try {
            return b.entityData.get(MAX_HP);
        } catch (RuntimeException notYet) {
            return 0F; // aún construyéndose (sin sus datos definidos)
        }
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        if (def == null || dying() || def.invincible || isInvulnerableTo(src)) return false;
        if (packInvulnerable && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        Entity attacker = src.getEntity();
        // Los de su grupo no se hacen daño entre ellos
        if (attacker != null && SkillMobs.host(attacker) != null && SkillMobs.host(attacker).boss() == boss()) return false;
        String cause = net.tierrasfantasticas.tfclient.skills.SkillServer.damageType(src);
        if (cause.equals("SUFFOCATION") || cause.equals("CRAMMING")) return false; // tan grandes, se meterían en las paredes
        double mod = def.damageModifiers.getOrDefault(cause, 1.0);
        if (mod <= 0) return false;
        // Cada jugador tiene su propio respiro de golpes (si no, en una incursión casi todos los golpes se pierden)
        if (attacker instanceof Player p) {
            Integer last = lastHitBy.get(p.getUUID());
            if (last != null && age - last < 8 && !src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return false;
            lastHitBy.put(p.getUUID(), age);
        }
        float before = getHealth();
        double hit = amount * mod;
        Player p = attacker instanceof Player pl ? pl : src.getDirectEntity() instanceof Player pl2 ? pl2 : null;
        // ~onDamaged ANTES de que entre, como MythicMobs (su EntityDamageEvent): con el daño del golpe (no lo que le
        // quitará: con la Resistencia V del escudo del Esqueleto eso es 0 y el escudo no se rompía nunca), la vida de
        // antes para sus umbrales, y sus skills pueden anularlo (cancelevent) o cambiarlo (modifydamage). Las cifras de
        // vida del pack van en su escala (el escudo aguanta «500» de su vida del pack)
        SkillMobs.Hit ev = SkillMobs.damaged(this, attacker != null ? attacker : src.getDirectEntity(), cause, hit);
        if (dying()) return false; // una de sus skills lo ha matado (suicide)
        if (ev.cancelled || ev.amount <= 0) {
            // Anulado (una fase invulnerable): quien le pega sigue siendo su enemigo
            if (p != null) threat.merge(p.getUUID(), hit * 0.5, Double::sum);
            return false;
        }
        hit = ev.amount;
        this.invulnerableTime = 0;
        boolean ok = super.hurt(src, (float) hit);
        this.invulnerableTime = 0;
        if (!ok) return false;
        float dealt = Math.max(0, before - getHealth());
        // La amenaza, con el golpe (con el escudo puesto no le entra daño, pero quien le pega sigue siendo su enemigo)
        double threatBy = Math.max(dealt, hit * 0.5);
        if (p != null) threat.merge(p.getUUID(), threatBy, Double::sum);
        if (p != null && cause.equals("PROJECTILE")) shotBy.put(p.getUUID(), age);
        if (owner != null && p != null) owner.threat.merge(p.getUUID(), threatBy * 0.25, Double::sum);
        return true;
    }

    @Override
    public boolean isCurrentlyGlowing() {
        // glowbone de ModelEngine: Minecraft solo dibuja contornos de entidades «brillantes»
        return super.isCurrentlyGlowing() || level().isClientSide && SkillMobs.clientGlow.test(this);
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        // ~onInteract (clic derecho), una vez por clic (no por cada mano)
        if (!level().isClientSide && def != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND) SkillMobs.interacted(this, player);
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        // ~onKill / ~onPlayerKill (con sus golpes y sus skills: los dos llevan al jefe como causante)
        if (def != null) SkillMobs.killed(this, victim);
        return super.killedEntity(level, victim);
    }

    /** Un golpe a una de sus piezas (la cabeza de la Hidra): es un golpe a él. */
    boolean hurtPart(BossPart part, DamageSource src, float amount) {
        if (!level().isClientSide && def != null) SkillMobs.hitPart(this, part.index());
        // hitboxconfig{pass} de esa caja (el hueso ob_/b_ que la pone, en su modelo tal como está ahora)
        if (!partPass.isEmpty()) {
            String bone = SkillMobs.partBone(this, part.index());
            Double pass = bone == null ? null : partPass.get(bone.toLowerCase(java.util.Locale.ROOT));
            if (pass != null) {
                if (pass <= 0) return false;
                amount *= pass.floatValue();
            }
        }
        return hurt(src, amount);
    }

    @Override
    public void die(DamageSource src) {
        if (this.dead || isRemoved()) return;
        super.die(src);
        if (level().isClientSide) return;
        SkillMobs.died(this, src.getEntity());
        if (bar != null) bar.setProgress(0F);
        if (owner == null) {
            // Los esbirros caen con su jefe
            for (BossEntity m : level().getEntitiesOfClass(BossEntity.class, getBoundingBox().inflate(96), e -> e.owner == this && e.isAlive())) {
                m.kill();
            }
            if (isMainBoss()) Rifts.bossKilled(this, threatPlayers());
        }
    }

    /** Quien le ha hecho daño (para las recompensas), del que más al que menos. */
    public List<ServerPlayer> threatPlayers() {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(level() instanceof ServerLevel sl)) return out;
        threat.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).forEach(e -> {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null) out.add(p);
        });
        return out;
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= deathLength && !level().isClientSide && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropAllDeathLoot(DamageSource src) {
        // Como en el pack (PreventOtherDrops): lo que suelta lo dan sus skills (el cofre del Esqueleto) y la grieta
    }

    @Override
    public boolean fireImmune() {
        return def != null && def.damageModifiers.getOrDefault("FIRE", 1.0) <= 0 || super.fireImmune();
    }

    @Override
    public boolean causeFallDamage(float dist, float mul, DamageSource src) {
        return def != null && def.damageModifiers.getOrDefault("FALL", 1.0) > 0 && !isMainBoss() && super.causeFallDamage(dist, mul, src);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !dying() && def != null && !def.invincible && (this.entityData.get(HB_FLAGS) & 2) == 0;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** No pasa por portales: su copia en el otro mundo no tendría su programa (y se iría de su grieta). */
    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    // ------------------------------------------------------------------------------------------- cada tick

    @Override
    public void tick() {
        load();
        super.tick();
        age++;
        if (level().isClientSide) {
            // El cuerpo mira a donde dice el servidor (suave)
            float want = this.entityData.get(BODY_YAW);
            this.yBodyRotO = this.yBodyRot;
            this.yBodyRot += Mth.wrapDegrees(want - this.yBodyRot) * 0.5F;
        } else if (def != null) {
            if (age == 1) {
                spawnAt = position();
                SkillMobs.spawned(this);
            }
            SkillMobs.tick(this);
            if (bar != null) updateBar();
            if (owner != null && (owner.isRemoved() || owner.dying() && owner.deathTime > 20)) discard();
        }
        if (main) placeParts();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (def == null || dying()) return;
        if (age % 10 == 0 || target != null && (!target.isAlive() || target.isRemoved())) pickTarget();
        aiExtras();
        if (age < moveUntil) {
            // move{} del pack: lo lleva el pack este tick
            meleeStep();
            turnBody();
            return;
        }
        Vec3 goal = null;
        double speed = 1.0;
        // Se acerca hasta casi tocarlo, como los mobs de Minecraft con los que van los packs (con 2 bloques de más,
        // Astwood se quedaba a 5,2 del jugador y sus puñetazos, que piden 4, no salían nunca)
        double stop = getBbWidth() / 2 + 1.0;
        if (home != null && position().distanceToSqr(home) > Rifts.LEASH * Rifts.LEASH) {
            // Se ha ido demasiado lejos de su grieta: vuelve
            goal = home;
            stop = 2;
            target = null;
            gotoGoal = null;
        } else if (gotoGoal != null && age < gotoUntil) {
            // goto{} del pack: anda hasta allí antes de volver a perseguir
            goal = gotoGoal;
            stop = 1.2;
            if (Math.sqrt(horizontalDistanceSqr(gotoGoal)) <= stop) gotoGoal = null;
        } else if (target != null && chases()) {
            goal = target.position();
            // El cerebro: a qué distancia ponerse (la del ataque que tendrá listo) y rodearle mientras espera
            double[] plan = SkillMobs.plan(this);
            if (plan != null) {
                double want = Math.max(stop, plan[0]);
                double min = plan.length > 2 ? plan[2] : 0;
                double d = Math.sqrt(horizontalDistanceSqr(target.position()));
                Vec3 rel = position().subtract(target.position()).multiply(1, 0, 1);
                if (d < min - 0.2 && rel.lengthSqr() > 1e-4 && !SkillMobs.attacking(this)) {
                    // Demasiado cerca para lo que va a hacer (la Abominación: todos sus ataques piden 3 bloques o más; si
                    // te pegabas a ella se quedaba quieta): paso atrás y se aparta
                    if (difficulty.smart() >= 0.6 && d < min - 0.8) backstep(rel);
                    goal = position().add(rel.normalize().scale(4));
                    stop = 0.6;
                } else if (plan[1] != 0 && d < want + 3 && rel.lengthSqr() > 1e-4) {
                    // Rodear: un punto 25° más allá alrededor del objetivo, a la distancia que busca
                    double ang = Math.toRadians(25 * plan[1]);
                    Vec3 dir = rel.normalize();
                    Vec3 turned = new Vec3(dir.x * Math.cos(ang) - dir.z * Math.sin(ang), 0, dir.x * Math.sin(ang) + dir.z * Math.cos(ang));
                    goal = target.position().add(turned.scale(Math.max(want, 2.5)));
                    stop = 0.6;
                } else if (d < want - 2.5 && rel.lengthSqr() > 1e-4) {
                    // Demasiado cerca para lo que quiere hacer (un ataque a distancia): se aparta
                    goal = position().add(rel.normalize().scale(4));
                    stop = 0.6;
                } else {
                    stop = want;
                }
            }
        }
        if (goal == gotoGoal && goal != null) speed = gotoSpeed;
        if (goal != null) {
            // Persigue, vuelve a su grieta o hace su goto: deja de pasear o de huir
            wanderGoal = null;
            panicking = false;
        }
        if (goal == null && (goal = idleGoal()) != null) {
            stop = 1.0;
            speed = wanderSpeed;
        }
        if (ai && goal != null && getAttributeValue(Attributes.MOVEMENT_SPEED) > 0) {
            double d = Math.sqrt(horizontalDistanceSqr(goal));
            if (d > stop) {
                if (--repath <= 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
                    repath = 10;
                }
            } else {
                getNavigation().stop();
                if (goal == wanderGoal) wanderGoal = null;
            }
        } else if (!getNavigation().isDone()) {
            getNavigation().stop();
        }
        meleeStep();
        turnBody();
    }

    /**
     * Lo que hace sin nadie a quien perseguir (o, sin objetivos que persigan, aunque lo tenga): huir un rato si le
     * pegan (PanicGoal), buscar sombra si arde al sol (FleeSunGoal), volver a donde apareció (gotospawnlocation) o
     * pasear (RandomStrollGoal: de vez en cuando, a un sitio a menos de 10 bloques, sin salir de su grieta).
     */
    private Vec3 idleGoal() {
        if (!ai) return null;
        if (target != null && chases()) return null;
        if (wanderGoal != null && getNavigation().isDone() && Math.sqrt(horizontalDistanceSqr(wanderGoal)) <= 1.0) wanderGoal = null;
        if (panicking && wanderGoal == null) panicking = false;
        if (SkillAI.has(goals, "panic") && !panicking && (getLastHurtByMob() != null || isOnFire())) {
            Vec3 to = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this, 5, 4);
            if (to != null && inLeash(to)) {
                wanderGoal = to;
                wanderSpeed = 1.25;
                panicking = true;
            }
        }
        if (wanderGoal == null && target == null && SkillAI.has(goals, "fleesun") && level().isDay() && isOnFire()
                && level().canSeeSky(blockPosition())) {
            Vec3 to = shade();
            if (to != null) {
                wanderGoal = to;
                wanderSpeed = 1.0;
            }
        }
        if (wanderGoal == null && SkillAI.has(goals, "gotospawnlocation") && spawnAt != null
                && Math.sqrt(horizontalDistanceSqr(spawnAt)) > 2) {
            wanderSpeed = 1.0;
            return spawnAt;
        }
        if (wanderGoal == null && SkillAI.has(goals, "randomstroll") && getRandom().nextInt(120) == 0) {
            Vec3 to = net.minecraft.world.entity.ai.util.LandRandomPos.getPos(this, 10, 7);
            if (to != null && inLeash(to)) {
                wanderGoal = to;
                wanderSpeed = 1.0;
            }
        }
        return wanderGoal;
    }

    private boolean inLeash(Vec3 at) {
        return home == null || at.distanceToSqr(home) < Rifts.LEASH * Rifts.LEASH;
    }

    /** FleeSunGoal.getHidePos: un sitio cerca sin cielo encima. */
    private Vec3 shade() {
        BlockPos here = blockPosition();
        for (int i = 0; i < 10; i++) {
            BlockPos p = here.offset(getRandom().nextInt(20) - 10, getRandom().nextInt(6) - 3, getRandom().nextInt(20) - 10);
            if (!level().canSeeSky(p) && getWalkTargetValue(p) < 0.0F) return Vec3.atBottomCenterOf(p);
        }
        return null;
    }

    /**
     * Lo que su IA hace cada tick además de andar: flotar en el agua (FloatGoal), saltar sobre su objetivo
     * (LeapAtTargetGoal: a 2-4 bloques, desde el suelo), no andar al sol (RestrictSunGoal) y arder al sol si su tipo lo
     * hace (Mob.isSunBurnTick, salvo con PreventSunburn).
     */
    private void aiExtras() {
        if (!ai) return;
        if (SkillAI.has(goals, "float") && (isInWater() && getFluidHeight(net.minecraft.tags.FluidTags.WATER) > getFluidJumpThreshold()
                || isInLava()) && getRandom().nextFloat() < 0.8F) {
            getJumpControl().jump();
        }
        if (target != null && onGround() && SkillAI.has(goals, "leapattarget")) {
            double d = distanceToSqr(target);
            // canUse se mira cada 2 ticks con 1 entre 3 (reducedTickDelay(5)): 1 entre 6 por tick
            if (d >= 4 && d <= 16 && getRandom().nextInt(6) == 0) {
                Vec3 dir = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
                if (dir.lengthSqr() > 1.0E-7) dir = dir.normalize().scale(0.4).add(getDeltaMovement().scale(0.2));
                setDeltaMovement(dir.x, 0.4, dir.z);
            }
        }
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation g) {
            g.setAvoidSun(SkillAI.has(goals, "restrictsun") && level().isDay());
        }
    }

    @Override
    public void aiStep() {
        // Se quema al sol como su tipo de Minecraft (zombi, esqueleto...), si el pack no lo evita con PreventSunburn
        if (!level().isClientSide && def != null && def.sunburn && ai && isAlive() && isSunBurnTick()) setSecondsOnFire(8);
        super.aiStep();
    }

    // ------------------------------------------------------------------------------------------- el golpe de Minecraft

    /**
     * MeleeAttackGoal (meleeattack, zombieattack, spiderattack): con su objetivo al alcance ((ancho × 2)² + el ancho del
     * objetivo, en distancia al cuadrado; spiderattack, 4 + el ancho) y 20 ticks desde el anterior, le pega.
     */
    private void meleeStep() {
        if (!ai || target == null || dying() || !SkillAI.melee(goals) || !target.isAlive()) {
            meleeWait = 0; // MeleeAttackGoal.start: el primer golpe sale en cuanto llega
            return;
        }
        if (meleeWait > 0) meleeWait--;
        double reach = SkillAI.has(goals, "meleeattack", "zombieattack") ? Math.pow(getBbWidth() * 2.0, 2) + target.getBbWidth()
                : 4.0 + target.getBbWidth();
        if (meleeWait <= 0 && distanceToSqr(target.getX(), target.getY(), target.getZ()) <= reach) {
            meleeWait = 20;
            doHurtTarget(target);
        }
    }

    /**
     * El golpe de Minecraft de su tipo (Mob.doHurtTarget y los suyos: el del gólem entre media y vez y media su daño
     * y lanza hacia arriba, el del hoglin lanza, el del husk da hambre, el del esqueleto del Wither marchita, el de la
     * araña de cueva envenena). Antes de que entre, sus ~onAttack (cancelevent lo anula, modifydamage lo cambia).
     */
    @Override
    public boolean doHurtTarget(Entity victim) {
        if (def == null || level().isClientSide || !(victim instanceof LivingEntity le)) return false;
        String type = def.type;
        float base = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean golem = type.equals("IRON_GOLEM"), hoglin = type.equals("HOGLIN") || type.equals("ZOGLIN");
        float dmg = (golem || hoglin) && (int) base > 0 ? base / 2F + getRandom().nextInt((int) base) : base;
        SkillMobs.Hit hit = SkillMobs.attack(this, le, dmg);
        if (dying() || hit.cancelled || hit.amount <= 0 || !le.isAlive()) return false;
        float kb = (float) getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        if (!le.hurt(damageSources().mobAttack(this), (float) hit.amount)) return false;
        double res = le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (golem) {
            le.setDeltaMovement(le.getDeltaMovement().add(0, 0.4 * Math.max(0, 1 - res), 0));
        } else if (hoglin) {
            // HoglinBase.throwTarget
            double push = kb - res;
            if (push > 0) {
                Vec3 v = new Vec3(le.getX() - getX(), 0, le.getZ() - getZ()).normalize()
                        .scale(push * (getRandom().nextFloat() * 0.5 + 0.2)).yRot(getRandom().nextInt(21) - 10);
                le.push(v.x, push * getRandom().nextFloat() * 0.5, v.z);
                le.hurtMarked = true;
            }
        } else if (kb > 0) {
            le.knockback(kb * 0.5F, Mth.sin(lookYaw * Mth.DEG_TO_RAD), -Mth.cos(lookYaw * Mth.DEG_TO_RAD));
            setDeltaMovement(getDeltaMovement().multiply(0.6, 1, 0.6));
        }
        float diff = level().getCurrentDifficultyAt(blockPosition()).getEffectiveDifficulty();
        switch (type) {
            case "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN", "PIG_ZOMBIE" -> {
                if (isOnFire() && getRandom().nextFloat() < diff * 0.3F) le.setSecondsOnFire(2 * (int) diff);
                if (type.equals("HUSK")) {
                    le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER,
                            140 * (int) diff), this);
                }
            }
            case "WITHER_SKELETON" -> le.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WITHER, 200), this);
            case "CAVE_SPIDER" -> {
                int secs = switch (level().getDifficulty()) {
                    case NORMAL -> 7;
                    case HARD -> 15;
                    default -> 0;
                };
                if (secs > 0) le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,
                        secs * 20, 0), this);
            }
            default -> {
            }
        }
        doEnchantDamageEffects(this, le);
        setLastHurtMob(le);
        return true;
    }

    private double horizontalDistanceSqr(Vec3 p) {
        double dx = p.x - getX(), dz = p.z - getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Hacia dónde mira la entidad y hacia dónde gira el cuerpo del modelo, como con ModelEngine: la entidad mira a su
     * objetivo (30° por tick, como la de Minecraft) y con eso se miden sus ataques; el modelo, mientras anda, sigue esa
     * dirección enseguida y, parado, gira la cabeza hasta «mh» y el cuerpo detrás (tras «rde» ticks, en «rdu»).
     * lockmodel congela el modelo, no a dónde mira.
     */
    private void turnBody() {
        float want = bodyYaw;
        Vec3 v = getDeltaMovement();
        boolean moving = v.x * v.x + v.z * v.z > 0.0009 && !getNavigation().isDone();
        // Anda por su cuenta (persigue, pasea o huye) y a quién mira (su objetivo, o un jugador cerca con lookatplayers)
        boolean walks = ai && (chases() || wanderGoal != null);
        Entity look = ai ? lookAt() : null;
        if (walks || look != null) {
            if (look != null) {
                Vec3 d = look.position().subtract(position());
                if (d.x * d.x + d.z * d.z > 1e-4) want = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            } else if (moving) {
                want = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
            }
            lookYaw += Mth.clamp(Mth.wrapDegrees(want - lookYaw), -30F, 30F);
        } else {
            lookYaw = bodyYaw;
        }
        lookYaw = Mth.wrapDegrees(lookYaw);
        if (locked) {
            syncBody();
            return;
        }
        float diff = Mth.wrapDegrees(want - bodyYaw);
        float track = walks ? SkillMobs.tracking(this) : 0F;
        if (track > 0 && !moving) {
            // Atacando (antes de que el modelo se bloquee): sigue al jugador, como los jefes de los Souls al cargar el golpe
            bodyYaw += Mth.clamp(diff, -track, track);
            diff = Mth.wrapDegrees(want - bodyYaw);
            turnWait = 0;
        } else if (moving && walks) {
            // Andando, el cuerpo va hacia donde va (como el de ModelEngine al moverse)
            bodyYaw += Mth.clamp(diff, -20F, 20F);
            turnWait = 0;
        } else if (!syncBodyOn) {
            // model{syncbody=false}: el cuerpo se queda; solo gira la cabeza (hasta «mh»)
            turnWait = 0;
        } else {
            // Parado: la cabeza no pasa de «mh» respecto al cuerpo; si el objetivo se va más allá, arrastra al cuerpo
            // (como ModelEngine), así sus golpes nunca salen a más de «mh» grados de quien mira
            if (Math.abs(diff) > maxHead) {
                bodyYaw += Mth.clamp(diff - Math.signum(diff) * maxHead, -30F, 30F);
                diff = Mth.wrapDegrees(want - bodyYaw);
            }
            // y, pasados «rde» ticks, el cuerpo se pone de frente en «rdu» (más rápido cuanto más listo: con 100 %, el
            // retraso es un 20 % y el giro un 30 % de los del pack, que pesaban 2 s + 3 s)
            double sm = difficulty.smart();
            int delay = (int) Math.round(rotDelay * (1 - 0.8 * sm));
            int duration = (int) Math.round(rotDuration * (1 - 0.7 * sm));
            if (Math.abs(diff) > Math.max(1F, maxBody) || maxBody == 0 && Math.abs(diff) > 0.5F) {
                if (++turnWait >= delay) {
                    float step = duration <= 0 ? diff : diff * Math.min(1F, 3F / duration);
                    if (Math.abs(step) < 1F && Math.abs(diff) > 1F) step = Math.signum(diff);
                    bodyYaw += step;
                }
            } else {
                turnWait = 0;
            }
        }
        float head = Mth.wrapDegrees(want - bodyYaw);
        yHeadRot = bodyYaw + Mth.clamp(head, -maxHead, maxHead);
        if (look != null) {
            Vec3 eye = getEyePosition();
            Vec3 to = look.getEyePosition().subtract(eye);
            float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
            setXRot(Mth.clamp(pitch, -Math.min(45F, maxHead), Math.min(45F, maxHead)));
        } else if (ai) {
            // Sin nadie a quien mirar, la cabeza vuelve al frente (LookControl); sin IA se queda como esté
            setXRot(getXRot() * 0.8F);
        }
        syncBody();
    }

    /**
     * A quién mira: a su objetivo si lo persigue o tiene lookattarget{r} (a menos de r bloques); si no, con
     * lookatplayers{r} (LookAtPlayerGoal: 8 bloques), de vez en cuando (2 % por tick) a un jugador cerca, 2-4 s.
     */
    private Entity lookAt() {
        if (target != null) {
            if (chases()) return target;
            String g = SkillAI.find(goals, "lookattarget");
            if (g != null) {
                double r = SkillAI.arg(g, 64, "r", "range", "radius");
                if (target.distanceToSqr(this) <= r * r) return target;
            }
        }
        String g = SkillAI.find(goals, "lookatplayers");
        if (g == null) return null;
        double r = SkillAI.arg(g, 8, "r", "range", "radius");
        if (lookPlayer != null && age < lookUntil && lookPlayer.isAlive() && lookPlayer.distanceToSqr(this) <= r * r) return lookPlayer;
        lookPlayer = null;
        if (getRandom().nextFloat() < 0.02F) {
            Player p = level().getNearestPlayer(this, r);
            if (p != null && !p.isSpectator()) {
                lookPlayer = p;
                lookUntil = age + 40 + getRandom().nextInt(40);
            }
        }
        return lookPlayer;
    }

    private void syncBody() {
        bodyYaw = Mth.wrapDegrees(bodyYaw);
        yBodyRot = bodyYaw;
        yBodyRotO = bodyYaw;
        this.entityData.set(BODY_YAW, bodyYaw);
    }

    /**
     * Objetivo, según sus AITargetSelectors: con «players», entre los jugadores a su alcance el que más daño le ha
     * hecho (si nadie, el más cercano); con «hurtbytarget» solo, entre los que le han pegado; con «monsters», si no
     * hay jugador, el monstruo más cercano que no sea de su grupo. Sin ninguno (clear), solo sus skills le dan objetivo.
     */
    private void pickTarget() {
        if (!(level() instanceof ServerLevel sl)) return;
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
        // SpiderTargetGoal: a los jugadores solo a oscuras (el que ya tiene lo sigue)
        boolean players = SkillAI.hasTarget(aiTargets, "players")
                || SkillAI.hasTarget(aiTargets, "type:spider") && (target != null || getLightLevelDependentMagicValue() < 0.5F);
        boolean hurtBy = SkillAI.hasTarget(aiTargets, "hurtbytarget");
        boolean monsters = SkillAI.hasTarget(aiTargets, "monsters");
        if (!players && !hurtBy && !monsters) {
            if (target != null && (!target.isAlive() || target.isRemoved() || target.distanceToSqr(this) > range * range)) {
                target = null;
                setTarget(null);
            }
            return;
        }
        Vec3 c = home != null ? home : position();
        LivingEntity best = null;
        double bestThreat = -1, bestDist = Double.MAX_VALUE;
        double smart = difficulty.smart();
        double top = 0;
        for (double v : threat.values()) top = Math.max(top, v);
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            if (!players && !threat.containsKey(p.getUUID())) continue;
            double d = p.distanceToSqr(this);
            if (d > range * range || home != null && p.position().distanceToSqr(c) > (Rifts.LEASH + 8) * (Rifts.LEASH + 8)) continue;
            double t = threat.getOrDefault(p.getUUID(), 0.0);
            // Listo: va a por quien se cura, a por quien le dispara desde lejos y remata a quien está a punto de caer
            if (smart >= 0.6) {
                if (net.tierrasfantasticas.tfclient.skills.SkillBrain.healing(p)) t += top * 0.4 + 20;
                Integer shot = shotBy.get(p.getUUID());
                if (shot != null && age - shot < 100 && d > 64) t += top * 0.15 + 8;
                if (smart >= 0.8 && p.getHealth() < p.getMaxHealth() * 0.3F) t += top * 0.1 + 5;
            }
            if (t > bestThreat || t == bestThreat && d < bestDist) {
                best = p;
                bestThreat = t;
                bestDist = d;
            }
        }
        if (target != null && best != null && target != best && target.isAlive()) {
            // Solo cambia si el nuevo le ha hecho bastante más daño
            double now = threat.getOrDefault(target.getUUID(), 0.0);
            if (bestThreat < now * 1.2 + 5 && target.distanceToSqr(this) <= range * range) best = target;
        }
        LivingEntity pick = best;
        if (pick == null && monsters) {
            // NearestAttackableTargetGoal de monstruos (el gólem no va a por los creepers); los de su grupo no cuentan
            if (target != null && !(target instanceof Player) && target.isAlive() && target.distanceToSqr(this) <= range * range) {
                pick = target;
            } else {
                double bd = Double.MAX_VALUE;
                for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(range), e -> e instanceof Enemy && e != this
                        && e.isAlive() && !(def.type.equals("IRON_GOLEM") && e instanceof net.minecraft.world.entity.monster.Creeper))) {
                    SkillMobs.Host h = SkillMobs.host(m);
                    if (h != null && h.boss() == boss()) continue;
                    double d = m.distanceToSqr(this);
                    if (d < bd && d <= range * range) {
                        bd = d;
                        pick = m;
                    }
                }
            }
        }
        target = pick;
        setTarget(pick);
    }

    private void updateBar() {
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        if (age % 10 != 0) return;
        bar.setName(barTitle());
        double r = def.barRange > 0 ? Math.max(48, def.barRange) : 64;
        List<ServerPlayer> now = new ArrayList<>();
        if (level() instanceof ServerLevel sl) for (ServerPlayer p : sl.players()) if (p.distanceToSqr(this) <= r * r) now.add(p);
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (!now.contains(p)) bar.removePlayer(p);
        for (ServerPlayer p : now) bar.addPlayer(p);
    }

    private Component barTitle() {
        SkillDefs.BossInfo bi = cls == null ? null : cls.bossInfo;
        String name = bi != null ? bi.name : def != null && def.display != null ? def.display : "Jefe";
        return Component.literal(name).withStyle(s -> s.withColor(TextColor.fromRgb(bi != null ? bi.color : 0xFFFFFF)).withBold(true))
                .append(Component.literal("  ·  " + difficulty.label()).withStyle(s -> s.withColor(TextColor.fromRgb(difficulty.color())).withBold(false)));
    }

    private static BossEvent.BossBarColor color(String c) {
        try {
            return BossEvent.BossBarColor.valueOf(c.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossEvent.BossBarColor.PURPLE;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            // ~onDespawn: se va sin morir (se cierra la grieta, se descarga su chunk), antes de olvidarlo
            if (def != null && reason != RemovalReason.KILLED && reason != RemovalReason.CHANGED_DIMENSION && !dying()) {
                try {
                    SkillMobs.despawned(this);
                } catch (RuntimeException e) {
                    net.tierrasfantasticas.tfclient.TFClient.LOGGER.debug("TF Jefes: error en ~onDespawn de {}", def.name, e);
                }
            }
            if (bar != null) bar.removeAllPlayers();
            SkillMobs.removed(this);
            if (isMainBoss() && reason != RemovalReason.KILLED) Rifts.bossGone(this);
        }
        super.remove(reason);
    }

    @Override
    public Component getName() {
        if (cls != null && cls.bossInfo != null && isMainBoss()) return Component.literal(cls.bossInfo.name);
        if (def != null && def.display != null) return Component.literal(def.display);
        return super.getName();
    }

    // ------------------------------------------------------------------------------------------- piezas (cajas ob_)

    @Override
    public boolean isMultipartEntity() {
        return main;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    /** Pone cada pieza donde está su hueso ahora (en el servidor para los golpes; en el cliente para apuntar). */
    private void placeParts() {
        List<AABB> boxes = level().isClientSide
                ? net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> BossClient.hitboxes(this))
                : SkillMobs.hitboxes(this);
        for (int i = 0; i < parts.length; i++) {
            AABB b = boxes != null && i < boxes.size() ? boxes.get(i) : null;
            parts[i].place(b);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = model == null ? 0 : net.tierrasfantasticas.tfclient.vfx.VfxHitboxes.reach(model);
        return r <= 0 ? super.getBoundingBoxForCulling() : getBoundingBox().inflate(r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    public BlockPos homePos() {
        return home == null ? blockPosition() : BlockPos.containing(home);
    }
}
