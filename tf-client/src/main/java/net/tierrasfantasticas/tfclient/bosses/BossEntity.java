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
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (model != null && model.hitW > 0 && model.hitH > 0) return EntityDimensions.scalable(model.hitW, model.hitH);
        return super.getDimensions(pose);
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

    @Override
    public double healthScale() {
        return healthScale;
    }

    @Override
    public LivingEntity target() {
        return target;
    }

    @Override
    public void setAi(boolean on) {
        ai = on;
        if (!on) getNavigation().stop();
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
        this.invulnerableTime = 0;
        boolean ok = super.hurt(src, (float) hit);
        this.invulnerableTime = 0;
        if (!ok) return false;
        float dealt = Math.max(0, before - getHealth());
        Player p = attacker instanceof Player pl ? pl : src.getDirectEntity() instanceof Player pl2 ? pl2 : null;
        // La amenaza, con el golpe (con el escudo puesto no le entra daño, pero quien le pega sigue siendo su enemigo)
        double threatBy = Math.max(dealt, hit * 0.5);
        if (p != null) threat.merge(p.getUUID(), threatBy, Double::sum);
        if (p != null && cause.equals("PROJECTILE")) shotBy.put(p.getUUID(), age);
        if (owner != null && p != null) owner.threat.merge(p.getUUID(), threatBy * 0.25, Double::sum);
        // ~onDamaged con el daño del golpe, como MythicMobs (no lo que le quitó: con la Resistencia V del escudo del
        // Esqueleto eso es 0 y el escudo no se rompía nunca). Las cifras de vida del pack van en su escala (el escudo
        // aguanta «500» de su vida del pack)
        SkillMobs.damaged(this, attacker != null ? attacker : src.getDirectEntity(), cause, hit / Math.max(0.01, healthScale));
        return true;
    }

    /** Un golpe a una de sus piezas (la cabeza de la Hidra): es un golpe a él. */
    boolean hurtPart(BossPart part, DamageSource src, float amount) {
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
        return !dying() && def != null && !def.invincible;
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
            if (age == 1) SkillMobs.spawned(this);
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
        Vec3 goal = null;
        // Se acerca hasta casi tocarlo, como los mobs de Minecraft con los que van los packs (con 2 bloques de más,
        // Astwood se quedaba a 5,2 del jugador y sus puñetazos, que piden 4, no salían nunca)
        double stop = getBbWidth() / 2 + 1.0;
        if (home != null && position().distanceToSqr(home) > Rifts.LEASH * Rifts.LEASH) {
            // Se ha ido demasiado lejos de su grieta: vuelve
            goal = home;
            stop = 2;
            target = null;
        } else if (target != null && def.chases) {
            goal = target.position();
            // El cerebro: a qué distancia ponerse (la del ataque que tendrá listo) y rodearle mientras espera
            double[] plan = SkillMobs.plan(this);
            if (plan != null) {
                double want = Math.max(stop, plan[0]);
                double d = Math.sqrt(horizontalDistanceSqr(target.position()));
                Vec3 rel = position().subtract(target.position()).multiply(1, 0, 1);
                if (plan[1] != 0 && d < want + 3 && rel.lengthSqr() > 1e-4) {
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
        if (ai && goal != null && getAttributeValue(Attributes.MOVEMENT_SPEED) > 0) {
            double d = Math.sqrt(horizontalDistanceSqr(goal));
            if (d > stop) {
                if (--repath <= 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
                    repath = 10;
                }
            } else {
                getNavigation().stop();
            }
        } else if (!getNavigation().isDone()) {
            getNavigation().stop();
        }
        turnBody();
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
        if (def.chases && ai) {
            if (target != null) {
                Vec3 d = target.position().subtract(position());
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
        float track = def.chases && ai ? SkillMobs.tracking(this) : 0F;
        if (track > 0 && !moving) {
            // Atacando (antes de que el modelo se bloquee): sigue al jugador, como los jefes de los Souls al cargar el golpe
            bodyYaw += Mth.clamp(diff, -track, track);
            diff = Mth.wrapDegrees(want - bodyYaw);
            turnWait = 0;
        } else if (moving && def.chases && ai) {
            // Andando, el cuerpo va hacia donde va (como el de ModelEngine al moverse)
            bodyYaw += Mth.clamp(diff, -20F, 20F);
            turnWait = 0;
        } else {
            // Parado: la cabeza no pasa de «mh» respecto al cuerpo; si el objetivo se va más allá, arrastra al cuerpo
            // (como ModelEngine), así sus golpes nunca salen a más de «mh» grados de quien mira
            if (Math.abs(diff) > maxHead) {
                bodyYaw += Mth.clamp(diff - Math.signum(diff) * maxHead, -30F, 30F);
                diff = Mth.wrapDegrees(want - bodyYaw);
            }
            // y, pasados «rde» ticks, el cuerpo se pone de frente en «rdu»
            if (Math.abs(diff) > Math.max(1F, maxBody) || maxBody == 0 && Math.abs(diff) > 0.5F) {
                if (++turnWait >= rotDelay) {
                    float step = rotDuration <= 0 ? diff : diff * Math.min(1F, 3F / rotDuration);
                    if (Math.abs(step) < 1F && Math.abs(diff) > 1F) step = Math.signum(diff);
                    bodyYaw += step;
                }
            } else {
                turnWait = 0;
            }
        }
        float head = Mth.wrapDegrees(want - bodyYaw);
        yHeadRot = bodyYaw + Mth.clamp(head, -maxHead, maxHead);
        if (target != null) {
            Vec3 eye = getEyePosition();
            Vec3 to = target.getEyePosition().subtract(eye);
            float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
            setXRot(Mth.clamp(pitch, -Math.min(45F, maxHead), Math.min(45F, maxHead)));
        } else {
            setXRot(getXRot() * 0.8F);
        }
        syncBody();
    }

    private void syncBody() {
        bodyYaw = Mth.wrapDegrees(bodyYaw);
        yBodyRot = bodyYaw;
        yBodyRotO = bodyYaw;
        this.entityData.set(BODY_YAW, bodyYaw);
    }

    /** Objetivo: entre los jugadores a su alcance, el que más daño le ha hecho; si nadie, el más cercano. */
    private void pickTarget() {
        if (!(level() instanceof ServerLevel sl)) return;
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
        Vec3 c = home != null ? home : position();
        LivingEntity best = null;
        double bestThreat = -1, bestDist = Double.MAX_VALUE;
        double smart = difficulty.smart();
        double top = 0;
        for (double v : threat.values()) top = Math.max(top, v);
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
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
        target = best;
        setTarget(best);
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
