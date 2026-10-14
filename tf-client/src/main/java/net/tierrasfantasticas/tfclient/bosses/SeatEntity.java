package net.tierrasfantasticas.tfclient.bosses;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;

/**
 * El asiento de un agarre (mountmodel{p=seat} de ModelEngine): la Abominación te mete en sus fauces, el Esqueleto de la
 * Torre te levanta con la mano. Es una entidad invisible que va cada tick donde está ese hueso del modelo; el jugador
 * va montado en ella (y no puede bajarse solo) hasta que el jefe lo suelta.
 */
public class SeatEntity extends Entity {
    UUID boss;
    String seat;
    boolean releasing;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Entity b = boss == null ? null : ((net.minecraft.server.level.ServerLevel) level()).getEntity(boss);
        SkillMobs.Host h = SkillMobs.host(b);
        if (h == null || b.isRemoved() || getPassengers().isEmpty()) {
            release();
            return;
        }
        Vec3 p = SkillMobs.bonePos(h, seat);
        if (p == null) p = b.position().add(0, b.getBbHeight() * 0.6, 0);
        // El jugador va con los pies un poco por debajo del hueso (el hueso marca más o menos su cintura)
        setPos(p.x, p.y - 0.6, p.z);
    }

    /** Suelta al que lleva y desaparece. */
    void release() {
        releasing = true;
        ejectPassengers();
        discard();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.3F, 0.3F);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
