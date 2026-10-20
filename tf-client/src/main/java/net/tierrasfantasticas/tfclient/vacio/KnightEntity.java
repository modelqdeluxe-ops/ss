package net.tierrasfantasticas.tfclient.vacio;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.vfx.VfxLayers;
import org.joml.Vector3f;

/**
 * Caelthar, el Caballero Sacro (hasta la 1.3.88 «Aurelion, caballero de luz»; el NPC «skywars» de NPCs For Mini Games
 * vol.1 de Wardenful Studio, que mandó el dueño):
 * no se mueve, no se le hace daño (salvo /kill), mira a quien tiene cerca y, con clic derecho, habla ({@link VoidNet}).
 * El de {@code /tf void npc} lleva al vacío; el del santuario ({@link #home()}) devuelve a donde estabas.
 */
public class KnightEntity extends PathfinderMob {
    /** Su nombre (el dueño: «otro nombre épico no conocido») y su título. */
    public static final String NAME = "Caelthar", TITLE = "Caballero Sacro";
    private static final EntityDataAccessor<Boolean> HOME = SynchedEntityData.defineId(KnightEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1F, 0.82F, 0.35F), 1.1F);

    /** Animaciones en el cliente. */
    public final VfxLayers layers = new VfxLayers();
    public int clientAge;
    public int shown = -1;
    /** Hasta cuándo (clientAge) brilla más: acaba de hablar o de abrir el camino. */
    public int glowUntil;

    public KnightEntity(EntityType<? extends KnightEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10F, 1F));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HOME, false);
    }

    /** ¿Es el del santuario del vacío (el que devuelve)? */
    public boolean home() {
        return this.entityData.get(HOME);
    }

    public void setHome(boolean home) {
        this.entityData.set(HOME, home);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TFHome", home());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHome(tag.getBoolean("TFHome"));
        this.setInvulnerable(true);
    }

    /** ¿Se queda quieto en su sitio? (Caelthar sí; los caballeros sacros del santuario pasean, 1.3.97) */
    protected boolean stationary() {
        return true;
    }

    /** Su nombre y su título (los de los caballeros sacros, SacredKnights). */
    public String displayName() {
        return NAME;
    }

    public String displayTitle() {
        return TITLE;
    }

    @Override
    public void tick() {
        super.tick();
        // Quieto en su sitio (aunque algo le empuje)
        if (stationary()) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(0, Math.min(0, v.y), 0);
        }
        // (1.3.89) el santuario se mudó a la punta norte: el caballero que se quedó en el centro de un Void de antes se va
        // (TFVoid pone otro en el santuario nuevo)
        if (!level().isClientSide && home() && tickCount % 40 == 0 && TFVoid.isVoid(level())
                && blockPosition().distSqr(VoidIsland.knightPos()) > 12 * 12) {
            discard();
            return;
        }
        if (level().isClientSide) {
            clientAge++;
            // Motas de luz dorada que suben a su alrededor y alguna chispa blanca (los caballeros sacros, menos)
            if (random.nextInt(stationary() ? 3 : 9) == 0) {
                double a = random.nextDouble() * Math.PI * 2, r = 0.6 + random.nextDouble() * 0.9;
                level().addParticle(GOLD, getX() + Math.cos(a) * r, getY() + 0.2 + random.nextDouble() * 2.6,
                        getZ() + Math.sin(a) * r, 0, 0.02, 0);
            }
            if (stationary() && random.nextInt(10) == 0) {
                level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 3.0,
                        getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.01, 0);
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) VoidTravel.talk(sp, this);
        if (level().isClientSide) glowUntil = clientAge + 40;
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        // Solo /kill (lo que se salta la invulnerabilidad) le quita
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amount);
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource src) {
        return !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity other) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }
}
