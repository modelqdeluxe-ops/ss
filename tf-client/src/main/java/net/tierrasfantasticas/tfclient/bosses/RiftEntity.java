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

    UUID riftId;
    /** Animaciones del cristal en el cliente (reposo, despertar, apagado, volver a crecer). */
    public final VfxLayers layers = new VfxLayers();
    public int clientAge;
    public int shownState = -1;

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

    void show(String boss, int state, int left, Difficulty d) {
        if (!boss.equals(boss())) this.entityData.set(BOSS, boss);
        if (state != state()) this.entityData.set(STATE, state);
        // Lo que falta, al segundo (no cada tick: no hace falta mandarlo tanto)
        int l = state == COOLDOWN ? (left + 19) / 20 * 20 : left;
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
            clientFx();
        } else if (riftId == null || !Rifts.owns(this)) {
            discard();
        }
    }

    /** El morado del vacío de las grietas (partículas). */
    static final org.joml.Vector3f VOID_PURPLE = new org.joml.Vector3f(0.55F, 0.16F, 0.98F);

    /**
     * Lo que se ve alrededor (solo en el cliente): chispas moradas que suben del círculo, el desgarro que se traga
     * partículas de portal, polvo violeta flotando y, de vez en cuando, un destello. Durante la incursión y la recarga
     * (el desgarro es una rendija), menos.
     */
    private void clientFx() {
        net.minecraft.util.RandomSource r = random;
        int st = state();
        boolean open = st == READY || st == WAKING;
        int n = st == WAKING ? 4 : open ? 2 : 1;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 1.2 + r.nextDouble() * 2.6;
            if (r.nextInt(3) == 0) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * d, getY() + 0.15,
                        getZ() + Math.sin(a) * d, 0, 0.04 + r.nextDouble() * 0.06, 0);
            }
            if (open) {
                // Se las traga el desgarro: salen alrededor y van hacia él
                double y = getY() + 1.5 + r.nextDouble() * 6;
                level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, getX() + (r.nextDouble() - 0.5) * 3, y,
                        getZ() + (r.nextDouble() - 0.5) * 3, (r.nextDouble() - 0.5) * 2, (r.nextDouble() - 0.5) * 1.5,
                        (r.nextDouble() - 0.5) * 2);
            }
            if (r.nextInt(4) == 0) {
                level().addParticle(new net.minecraft.core.particles.DustParticleOptions(VOID_PURPLE, 1.4F),
                        getX() + (r.nextDouble() - 0.5) * 5, getY() + r.nextDouble() * 7, getZ() + (r.nextDouble() - 0.5) * 5, 0, 0.01, 0);
            }
        }
        if (open && r.nextInt(st == WAKING ? 3 : 25) == 0) {
            level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, getX() + (r.nextDouble() - 0.5) * 0.8,
                    getY() + 2 + r.nextDouble() * 5, getZ() + (r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.08,
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
        return EntityDimensions.scalable(2.4F, 5.2F);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // El desgarro sube a ~8,5 bloques y los anillos del suelo llegan a 3,75 del centro
        return getBoundingBox().inflate(3, 4, 3);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Component getName() {
        return Component.literal("Grieta");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
