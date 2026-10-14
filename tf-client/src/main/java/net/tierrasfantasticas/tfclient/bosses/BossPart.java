package net.tierrasfantasticas.tfclient.bosses;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

/**
 * Una caja de golpe por piezas de un jefe (un hueso ob_ de ModelEngine: una cabeza de la Hidra, un tramo de su cola):
 * va donde está ese hueso y un golpe en ella es un golpe al jefe. Las que el modelo no usa se quedan apagadas.
 */
public class BossPart extends PartEntity<BossEntity> {
    private final int index;
    private boolean active;
    private float w = 0.01F, h = 0.01F;

    public BossPart(BossEntity parent, int index) {
        super(parent);
        this.index = index;
        this.noPhysics = true;
        this.refreshDimensions();
    }

    /** La pone en esa caja (null = apagada). */
    void place(AABB box) {
        if (box == null) {
            if (active) {
                active = false;
                setPos(getParent().getX(), getParent().getY(), getParent().getZ());
            }
            return;
        }
        active = true;
        float nw = (float) Math.max(box.getXsize(), box.getZsize());
        float nh = (float) box.getYsize();
        if (Math.abs(nw - w) > 0.05F || Math.abs(nh - h) > 0.05F) {
            w = nw;
            h = nh;
            refreshDimensions();
        }
        double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
        xo = getX();
        yo = getY();
        zo = getZ();
        setPos(cx, box.minY, cz);
    }

    public int index() {
        return index;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(active ? w : 0.01F, active ? h : 0.01F);
    }

    @Override
    public boolean isPickable() {
        return active && getParent().isPickable();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return active && !isInvulnerableTo(src) && getParent().hurtPart(this, src, amount);
    }

    @Override
    public boolean is(Entity e) {
        return this == e || getParent() == e;
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

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        throw new UnsupportedOperationException();
    }
}
