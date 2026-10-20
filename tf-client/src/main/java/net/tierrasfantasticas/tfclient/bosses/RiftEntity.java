package net.tierrasfantasticas.tfclient.bosses;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.tierrasfantasticas.tfclient.vfx.VfxLayers;

/**
 * La grieta: un cristal enorme sobre un pedestal con un círculo de runas. Golpearlo (cuando está listo) despierta al
 * jefe que guarda: el cristal tiembla, se agrieta y estalla, y el jefe sale de ahí. Mientras dura la incursión y
 * mientras se recarga, el cristal es solo un brillo pequeño sobre el círculo; luego vuelve a crecer. Lo que pasa lo
 * decide {@link Rifts} (que la guarda en el mundo); la entidad solo se ve, se golpea y dice cómo está.
 */
public class RiftEntity extends Entity {
    public static final int READY = 0, WAKING = 1, FIGHT = 2, COOLDOWN = 3;
    /** Ticks que tarda en despertar al jefe desde el golpe. */
    public static final int WAKE_TICKS = 70;

    private static final EntityDataAccessor<String> BOSS = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEFT = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LABEL = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LABEL_COLOR = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
    /** 0 la grieta normal, 1 la grieta madre (1.3.89: más grande, en carmesí). */
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
    /** ¿Es la grieta del Void (la fija del centro de la isla, con sus jefes en /tf boss → VOID)? */
    private static final EntityDataAccessor<Boolean> FIXED = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int NORMAL = 0, MOTHER = 1;

    UUID riftId;
    /** Animaciones del cristal en el cliente (reposo, despertar, apagado, volver a crecer). */
    public final VfxLayers layers = new VfxLayers();
    public int clientAge;
    public int shownState = -1;
    /** clientAge cuando cambió de estado (en su tick, o al dibujarla si aún no hizo ninguno): abrirse, cerrarse... */
    public int stateSince;
    /** El estado de antes del de ahora (en el cliente). */
    public int prevState = -1;
    /** El tipo (copia de KIND, para el tamaño: getDimensions no debe leer los datos sincronizados). */
    private int kindNow;

    public RiftEntity(EntityType<? extends RiftEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BOSS, "");
        this.entityData.define(STATE, READY);
        this.entityData.define(LEFT, 0);
        this.entityData.define(LABEL, "");
        this.entityData.define(LABEL_COLOR, 0xFFFFFF);
        this.entityData.define(KIND, NORMAL);
        this.entityData.define(FIXED, false);
    }

    public int kind() {
        return this.entityData.get(KIND);
    }

    public boolean fixed() {
        return this.entityData.get(FIXED);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key)) {
            kindNow = kind();
            refreshDimensions();
        }
    }

    public String boss() {
        return this.entityData.get(BOSS);
    }

    public int state() {
        return this.entityData.get(STATE);
    }

    /** Ticks que le quedan a lo que esté haciendo (despertar o recarga). */
    public int left() {
        return this.entityData.get(LEFT);
    }

    /** La dificultad que se enseña encima («Demencial») y su color. */
    public String label() {
        return this.entityData.get(LABEL);
    }

    public int labelColor() {
        return this.entityData.get(LABEL_COLOR);
    }

    void show(String boss, int state, int left, Difficulty d, int kind, boolean fixed) {
        if (kind != kind()) this.entityData.set(KIND, kind);
        if (fixed != fixed()) this.entityData.set(FIXED, fixed);
        if (!boss.equals(boss())) this.entityData.set(BOSS, boss);
        if (state != state()) this.entityData.set(STATE, state);
        // Lo que falta, al segundo (no cada tick: no hace falta mandarlo tanto)
        int l = state == COOLDOWN && left >= 0 ? (left + 19) / 20 * 20 : left;
        if (l != left()) this.entityData.set(LEFT, l);
        if (d != null) {
            if (!d.label().equals(label())) this.entityData.set(LABEL, d.label());
            if (d.color() != labelColor()) this.entityData.set(LABEL_COLOR, d.color());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientAge++;
            int st = state();
            if (st != shownState) {
                // (aquí y no al dibujarla: la distorsión y los sonidos del cierre van aunque no se vea)
                stateSince = shownState < 0 ? -1000 : clientAge;
                prevState = shownState;
                shownState = st;
                // (1.3.91) el cristal se hace añicos al abrirse la grieta
                if (st == FIGHT && prevState == WAKING) {
                    double y = getY() + (kindNow == MOTHER ? 8.6 : 6.2);
                    level().playLocalSound(getX(), y, getZ(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.HOSTILE,
                            4F, 0.6F, false);
                    level().playLocalSound(getX(), y, getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                            net.minecraft.sounds.SoundSource.HOSTILE, 4F, 0.7F, false);
                }
            }
            // (1.3.91) al juntarse la grieta que se cierra (RiftRenderer: 30 ticks), un chasquido
            if (st == COOLDOWN && prevState == FIGHT && clientAge - stateSince == 30) {
                double y = getY() + 11.5 * scale();
                level().playLocalSound(getX(), y, getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                        net.minecraft.sounds.SoundSource.HOSTILE, 4F, 0.45F, false);
                level().playLocalSound(getX(), y, getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE,
                        2.5F, 1.7F, false);
            }
            clientFx();
        } else if (riftId == null || !Rifts.owns(this)) {
            discard();
        }
    }

    /** El morado del vacío de las grietas (partículas) y el carmesí de la grieta madre. */
    static final org.joml.Vector3f VOID_PURPLE = new org.joml.Vector3f(0.55F, 0.16F, 0.98F);
    static final org.joml.Vector3f MOTHER_RED = new org.joml.Vector3f(0.95F, 0.14F, 0.38F);

    /** Lo grande que es (la madre ×1,5) y su color de partículas. */
    public float scale() {
        return kindNow == MOTHER ? 1.5F : 1F;
    }

    org.joml.Vector3f color() {
        return kindNow == MOTHER ? MOTHER_RED : VOID_PURPLE;
    }

    /**
     * Lo que se ve alrededor (solo en el cliente): chispas moradas que suben del círculo, polvo violeta flotando y, con
     * el desgarro abierto (1.3.91: al despertar y en toda la pelea; lista y en la recarga es una rendija cerrada), las
     * partículas de portal que se traga y destellos.
     */
    private void clientFx() {
        net.minecraft.util.RandomSource r = random;
        int st = state();
        boolean open = st == WAKING || st == FIGHT;
        int n = st == FIGHT ? 5 : st == WAKING ? 4 : st == READY ? 2 : 1;
        for (int i = 0; i < n; i++) {
            double k = scale();
            double a = r.nextDouble() * Math.PI * 2, d = (2 + r.nextDouble() * 4.2) * k;
            if (r.nextInt(3) == 0) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * d, getY() + 0.15,
                        getZ() + Math.sin(a) * d, 0, 0.04 + r.nextDouble() * 0.06, 0);
            }
            if (open) {
                // Se las traga el desgarro: salen alrededor y van hacia él
                double y = getY() + (2.5 + r.nextDouble() * 10) * k;
                level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, getX() + (r.nextDouble() - 0.5) * 5 * k, y,
                        getZ() + (r.nextDouble() - 0.5) * 5 * k, (r.nextDouble() - 0.5) * 2, (r.nextDouble() - 0.5) * 1.5,
                        (r.nextDouble() - 0.5) * 2);
            }
            if (r.nextInt(4) == 0) {
                level().addParticle(new net.minecraft.core.particles.DustParticleOptions(color(), 1.4F),
                        getX() + (r.nextDouble() - 0.5) * 8 * k, getY() + r.nextDouble() * 11 * k, getZ() + (r.nextDouble() - 0.5) * 8 * k, 0, 0.01, 0);
            }
        }
        if (open ? r.nextInt(3) == 0 : st == READY && r.nextInt(25) == 0) {
            level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, getX() + (r.nextDouble() - 0.5) * 0.8,
                    getY() + (3 + r.nextDouble() * 8) * scale(), getZ() + (r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.08,
                    (r.nextDouble() - 0.5) * 0.08, (r.nextDouble() - 0.5) * 0.08);
        }
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || state() != READY) return false;
        if (src.getEntity() instanceof ServerPlayer p && !p.isSpectator()) Rifts.wake(this, p);
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer p) Rifts.info(this, p);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() {
        return state() == READY && !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return state() == READY;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return state() != READY;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // (el cristal de la madre está a 8,6 bloques: más alto)
        return kindNow == MOTHER ? EntityDimensions.scalable(4.6F, 12.5F) : EntityDimensions.scalable(3.2F, 8.5F);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // (1.3.88, la grieta 3.0) las garras llegan a ~10 bloques del centro, el círculo de runas a 13,5 y el desgarro y el
        // cartel a ~24 de alto
        // y la madre, ×1,5
        AABB b = getBoundingBox();
        float k = scale();
        return new AABB(b.minX - 14 * k, b.minY - 1, b.minZ - 14 * k, b.maxX + 14 * k, b.minY + 25 * k, b.maxZ + 14 * k);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        double far = 192 * scale();
        return d < far * far;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Component getName() {
        return Component.literal(kindNow == MOTHER ? "Grieta madre" : fixed() ? "Grieta del Void" : "Grieta");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
