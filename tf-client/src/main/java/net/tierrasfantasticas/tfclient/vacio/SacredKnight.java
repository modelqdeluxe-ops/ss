/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Un caballero sacro del santuario (1.3.97, {@link SacredKnights}): el mismo modelo que Caelthar con la armadura de su
 * metal y sin alas, que pasea despacio por su rincón del santuario ({@link SacredKnights.Def#radius}), mira a quien
 * tiene cerca, le saluda en el chat al acercarse y, con clic derecho, se para y habla ({@link KnightScreen}). No se le
 * hace daño ni se le empuja (como a Caelthar).
 */
public class SacredKnight extends KnightEntity {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(SacredKnight.class, EntityDataSerializers.INT);
    private BlockPos home = BlockPos.ZERO;
    /** Hasta cuándo (tickCount) se queda quieto: acaba de hablar con alguien. */
    private int talkUntil;

    public SacredKnight(EntityType<? extends SacredKnight> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8F, 1F));
        this.goalSelector.addGoal(2, new Stroll());
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANT, 0);
    }

    public int variant() {
        return this.entityData.get(VARIANT);
    }

    public SacredKnights.Def def() {
        return SacredKnights.def(variant());
    }

    void setup(SacredKnights.Def d, BlockPos at) {
        this.entityData.set(VARIANT, d.id());
        this.home = at;
        moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, d.yaw(), 0F);
        setYHeadRot(d.yaw());
        setYBodyRot(d.yaw());
    }

    @Override
    protected boolean stationary() {
        return false;
    }

    @Override
    public String displayName() {
        SacredKnights.Def d = def();
        return d == null ? NAME : d.name();
    }

    @Override
    public String displayTitle() {
        SacredKnights.Def d = def();
        return d == null ? TITLE : d.title();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TFVariante", variant());
        tag.putLong("TFCasa", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(VARIANT, tag.getInt("TFVariante"));
        home = BlockPos.of(tag.getLong("TFCasa"));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 10 != 0) return;
        // (si su casa cambió, p. ej. un santuario que se mudó, la de ahora)
        SacredKnights.Def d = def();
        if (d != null && (tickCount % 200 == 10 || home.equals(BlockPos.ZERO)) && level() instanceof net.minecraft.server.level.ServerLevel sl)
            home = SacredKnights.ground(sl, SacredKnights.home(d));
        Player p = level().getNearestPlayer(this, 6.0);
        if (p instanceof ServerPlayer sp && !sp.isSpectator()) SacredKnights.greet(this, sp);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        getNavigation().stop();
        talkUntil = tickCount + 200;
        getLookControl().setLookAt(player, 30F, 30F);
        if (level().isClientSide) {
            glowUntil = clientAge + 40;
            SacredKnights.Def d = def();
            if (d != null) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VoidClient.sacredTalk(this, d));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Pasea por su rincón: cada pocos segundos, a un sitio a menos de su radio de casa (y se para un rato). */
    private final class Stroll extends Goal {
        private int wait;

        Stroll() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            SacredKnights.Def d = def();
            if (d == null || d.radius() < 1F || tickCount < talkUntil) return false;
            if (--wait > 0) return false;
            wait = 120 + random.nextInt(200);
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * d.radius();
            Vec3 to = new Vec3(home.getX() + 0.5 + Math.cos(a) * r, home.getY(), home.getZ() + 0.5 + Math.sin(a) * r);
            return getNavigation().moveTo(to.x, to.y, to.z, 0.55);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && tickCount >= talkUntil;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }
}
