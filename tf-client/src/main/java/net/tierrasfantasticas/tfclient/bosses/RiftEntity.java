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
        if (level().isClientSide) clientAge++;
        else if (riftId == null || !Rifts.owns(this)) discard();
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
        return getBoundingBox().inflate(3, 2, 3);
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
