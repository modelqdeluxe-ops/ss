package net.tierrasfantasticas.tfclient.claims.event;

import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.GlobalFlags;
import net.tierrasfantasticas.tfclient.claims.util.DecorationProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class EntityProtectionEvents {
    private static final String BARRIER_TAG = "claimblocks_barrier_tick";
    private static final long BARRIER_WINDOW = 200L;
    private static final String SPAWN_OK_TAG = "claimblocks_spawn_allowed";

    private static boolean isBypassing(Player player) {
        return player.hasPermissions(2) && ClaimManager.getInstance().isBypassing(player.getUUID());
    }

    private static void deny(Player player, String s) {
        if (player instanceof ServerPlayer) {
            ServerPlayer serverplayer = (ServerPlayer)player;
            serverplayer.displayClientMessage((Component)Component.literal((String)s).withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void onHostileTick(LivingEvent.LivingTickEvent livingtickevent) {
        Claim claim;
        LivingEntity livingentity = livingtickevent.getEntity();
        Level level = livingentity.level();
        if (!level.isClientSide() && livingentity instanceof Enemy && livingentity.tickCount % 5 == 0 && (claim = ClaimManager.getInstance().getClaimAt(level, livingentity.blockPosition())) != null && claim.getFlags().burnHostiles) {
            EntityProtectionEvents.repelHostile(claim, livingentity);
        }
    }

    private static void repelHostile(Claim claim, LivingEntity livingentity) {
        double d0 = livingentity.getX();
        double d1 = livingentity.getZ();
        int i = claim.getRadius();
        double d2 = (double)claim.getX() + 0.5;
        double d3 = (double)claim.getZ() + 0.5;
        double d4 = d0 - (d2 - (double)i);
        double d5 = d2 + (double)i - d0;
        double d6 = d1 - (d3 - (double)i);
        double d7 = d3 + (double)i - d1;
        double d8 = 0.0;
        double d9 = 0.0;
        double d10 = Math.min(Math.min(d4, d5), Math.min(d6, d7));
        if (d10 == d4) {
            d8 = -1.0;
        } else if (d10 == d5) {
            d8 = 1.0;
        } else {
            d9 = d10 == d6 ? -1.0 : 1.0;
        }
        livingentity.setDeltaMovement(d8 * 1.1, 0.42, d9 * 1.1);
        livingentity.hasImpulse = true;
        livingentity.hurtMarked = true;
        ClaimConfig claimconfig = ClaimConfig.get();
        if (claimconfig.hostileBurnSeconds > 0) {
            livingentity.setSecondsOnFire(claimconfig.hostileBurnSeconds);
        }
        if (claimconfig.hostileDamage > 0.0f) {
            livingentity.invulnerableTime = 0;
            livingentity.hurt(livingentity.damageSources().generic(), claimconfig.hostileDamage);
        }
        livingentity.getPersistentData().putLong(BARRIER_TAG, livingentity.level().getGameTime());
    }

    private static boolean killedByBarrier(LivingEntity livingentity, Level level) {
        long i = livingentity.getPersistentData().getLong(BARRIER_TAG);
        if (i > 0L && level.getGameTime() - i <= 200L) {
            return true;
        }
        Claim claim = ClaimManager.getInstance().getClaimAt(level, livingentity.blockPosition());
        return claim != null && claim.getFlags().burnHostiles;
    }

    @SubscribeEvent
    public void onHostileDrops(LivingDropsEvent livingdropsevent) {
        DamageSource damagesource;
        LivingEntity livingentity = livingdropsevent.getEntity();
        Level level = livingentity.level();
        if (!level.isClientSide() && livingentity instanceof Enemy && ((damagesource = livingdropsevent.getSource()) == null || !(damagesource.getEntity() instanceof Player)) && EntityProtectionEvents.killedByBarrier(livingentity, level)) {
            livingdropsevent.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onHostileXp(LivingExperienceDropEvent livingexperiencedropevent) {
        LivingEntity livingentity = livingexperiencedropevent.getEntity();
        if (livingentity instanceof Enemy && livingexperiencedropevent.getAttackingPlayer() == null && EntityProtectionEvents.killedByBarrier(livingentity, livingentity.level())) {
            livingexperiencedropevent.setCanceled(true);
        }
    }

    private static boolean isPlayerDrivenSpawn(MobSpawnType mobspawntype) {
        if (mobspawntype == null) {
            return false;
        }
        return switch (mobspawntype) {
            case SPAWN_EGG, BUCKET, BREEDING, COMMAND, DISPENSER, CONVERSION -> true;
            default -> false;
        };
    }

    private static boolean isPassiveAnimal(Mob mob) {
        return mob instanceof Animal || mob instanceof WaterAnimal || mob instanceof AmbientCreature;
    }

    private static boolean isPlayerOwnedMob(Mob mob) {
        if (!mob.hasCustomName() && !mob.isPersistenceRequired()) {
            AgeableMob ageablemob;
            TamableAnimal tamableanimal;
            if (mob instanceof TamableAnimal && (tamableanimal = (TamableAnimal)mob).isTame()) {
                return true;
            }
            return !(mob instanceof IronGolem) && !(mob instanceof SnowGolem) ? mob instanceof AgeableMob && (ageablemob = (AgeableMob)mob).isBaby() : true;
        }
        return true;
    }

    private static boolean shouldBlockSpawn(Level level, BlockPos blockpos, Mob mob, boolean flag) {
        if (GlobalFlags.getInstance().globalNoMobSpawn) {
            return true;
        }
        Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
        if (claim == null) {
            return false;
        }
        ClaimFlags claimflags = claim.getFlags();
        if (claimflags.blockAllMobSpawn) {
            return true;
        }
        return claimflags.blockPassiveMobSpawn && EntityProtectionEvents.isPassiveAnimal(mob) ? true : flag && mob instanceof Monster && (claimflags.blockMobSpawn || claimflags.publicMode);
    }

    @SubscribeEvent
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn finalizespawn) {
        Mob mob = finalizespawn.getEntity();
        if (EntityProtectionEvents.isPlayerDrivenSpawn(finalizespawn.getSpawnType())) {
            mob.getPersistentData().putBoolean(SPAWN_OK_TAG, true);
        } else {
            ServerLevel serverlevel = finalizespawn.getLevel().getLevel();
            if (EntityProtectionEvents.shouldBlockSpawn((Level)serverlevel, BlockPos.containing((double)finalizespawn.getX(), (double)finalizespawn.getY(), (double)finalizespawn.getZ()), mob, true)) {
                finalizespawn.setSpawnCancelled(true);
                finalizespawn.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent entityjoinlevelevent) {
        Entity entity;
        Level level = entityjoinlevelevent.getLevel();
        if (!level.isClientSide() && (entity = entityjoinlevelevent.getEntity()) instanceof Mob) {
            Mob mob = (Mob)entity;
            if (mob.tickCount == 0) {
                Claim claim;
                if (!(mob instanceof Monster) || (claim = ClaimManager.getInstance().getClaimAt(level, mob.blockPosition())) == null || !claim.getFlags().blockMobSpawn && !claim.getFlags().publicMode) {
                    if (!(mob instanceof Enemy)) {
                        return;
                    }
                    if (!(entityjoinlevelevent.loadedFromDisk() || mob.getPersistentData().getBoolean(SPAWN_OK_TAG) || EntityProtectionEvents.isPlayerOwnedMob(mob))) {
                        if (EntityProtectionEvents.shouldBlockSpawn(level, mob.blockPosition(), mob, false)) {
                            entityjoinlevelevent.setCanceled(true);
                        }
                        return;
                    }
                    return;
                }
                entityjoinlevelevent.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent livinghurtevent) {
        LivingEntity livingentity = livinghurtevent.getEntity();
        Level level = livingentity.level();
        if (!level.isClientSide()) {
            DamageSource damagesource = livinghurtevent.getSource();
            Entity entity = damagesource.getEntity();
            Claim claim = ClaimManager.getInstance().getClaimAt(level, livingentity.blockPosition());
            if (livingentity instanceof Player && entity instanceof Player && !GlobalFlags.getInstance().globalPVP) {
                EntityProtectionEvents.deny((Player)entity, "[!] El PVP est\u00e1 desactivado en este servidor.");
                livinghurtevent.setCanceled(true);
            } else if (claim != null) {
                if (livingentity instanceof Player && entity instanceof Player) {
                    Player player = (Player)entity;
                    if (EntityProtectionEvents.isBypassing(player)) {
                        return;
                    }
                    if (!claim.getFlags().pvpAll && claim.getFlags().blockPVP) {
                        EntityProtectionEvents.deny(player, "[!] El PVP est\u00e1 desactivado en esta zona.");
                        livinghurtevent.setCanceled(true);
                        return;
                    }
                }
                if (!(livingentity instanceof Player) || !(entity instanceof LivingEntity) || entity instanceof Player || !claim.getFlags().blockMobDamage && !claim.getFlags().publicMode) {
                    Player player1;
                    if (livingentity instanceof Animal && entity instanceof Player && !claim.canModify(player1 = (Player)entity) && !EntityProtectionEvents.isBypassing(player1) && claim.getFlags().blockAnimalKilling) {
                        EntityProtectionEvents.deny(player1, "[!] No puedes matar animales en esta zona.");
                        livinghurtevent.setCanceled(true);
                        return;
                    }
                    if (claim.getFlags().blockExplosions && damagesource.is(DamageTypeTags.IS_EXPLOSION)) {
                        livinghurtevent.setCanceled(true);
                    }
                } else {
                    livinghurtevent.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent attackentityevent) {
        Entity entity;
        Player player = attackentityevent.getEntity();
        Level level = player.level();
        if (!(level.isClientSide() || EntityProtectionEvents.isBypassing(player) || (entity = attackentityevent.getTarget()) instanceof Enemy || entity instanceof Player)) {
            if (DecorationProtection.isDecoration(entity)) {
                if (DecorationProtection.blocksPlayer(DecorationProtection.claimFor(level, entity), player)) {
                    EntityProtectionEvents.deny(player, "[!] No puedes romper la decoraci\u00f3n de esta zona.");
                    attackentityevent.setCanceled(true);
                }
            } else {
                Claim claim = ClaimManager.getInstance().getClaimAt(level, entity.blockPosition());
                if (claim != null && !claim.canModify(player)) {
                    ClaimFlags claimflags = claim.getFlags();
                    if (claimflags.blockAllInteractions) {
                        EntityProtectionEvents.deny(player, "[!] No tienes ning\u00fan permiso de interacci\u00f3n en esta zona.");
                        attackentityevent.setCanceled(true);
                    } else if (!(entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature)) {
                        if (claimflags.blockEntityInteract) {
                            EntityProtectionEvents.deny(player, "[!] No puedes da\u00f1ar entidades aqu\u00ed.");
                            attackentityevent.setCanceled(true);
                        }
                    } else if (claimflags.blockAnimalKilling) {
                        EntityProtectionEvents.deny(player, "[!] No puedes matar animales en esta zona.");
                        attackentityevent.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific entityinteractspecific) {
        if (EntityProtectionEvents.blockInteraction(entityinteractspecific.getLevel(), entityinteractspecific.getEntity(), entityinteractspecific.getTarget())) {
            entityinteractspecific.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract entityinteract) {
        if (EntityProtectionEvents.blockInteraction(entityinteract.getLevel(), entityinteract.getEntity(), entityinteract.getTarget())) {
            entityinteract.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent projectileimpactevent) {
        Claim claim;
        Level level;
        Entity entity;
        HitResult hitresult = projectileimpactevent.getRayTraceResult();
        if (hitresult instanceof EntityHitResult && DecorationProtection.isDecoration(entity = ((EntityHitResult)hitresult).getEntity()) && (level = entity.level()) != null && !level.isClientSide() && (claim = DecorationProtection.claimFor(level, entity)) != null) {
            Player player = DecorationProtection.ownerOf((Entity)projectileimpactevent.getProjectile());
            boolean flag = player != null ? DecorationProtection.blocksPlayer(claim, player) : (claim.getFlags().blockBuilding || claim.getFlags().publicMode);
            if (flag) {
                projectileimpactevent.setCanceled(true);
                if (player != null) {
                    EntityProtectionEvents.deny(player, "[!] No puedes romper la decoraci\u00f3n de esta zona.");
                }
            }
        }
    }

    private static boolean blockInteraction(Level level, Player player, Entity entity) {
        if (level == null || level.isClientSide() || player == null || entity == null) {
            return false;
        }
        if (EntityProtectionEvents.isBypassing(player)) {
            return false;
        }
        if (DecorationProtection.isDecoration(entity)) {
            Claim claim1 = DecorationProtection.claimFor(level, entity);
            if (DecorationProtection.blocksPlayer(claim1, player)) {
                EntityProtectionEvents.deny(player, "[!] No puedes tocar la decoraci\u00f3n de esta zona.");
                return true;
            }
            return false;
        }
        Claim claim = ClaimManager.getInstance().getClaimAt(level, entity.blockPosition());
        if (claim != null && !claim.canModify(player)) {
            ClaimFlags claimflags = claim.getFlags();
            if (claimflags.blockAllInteractions) {
                EntityProtectionEvents.deny(player, "[!] No tienes ning\u00fan permiso de interacci\u00f3n en esta zona.");
                return true;
            }
            if (entity instanceof Container) {
                if (claimflags.blockChestAccess) {
                    EntityProtectionEvents.deny(player, "[!] No puedes abrir este contenedor aqu\u00ed.");
                    return true;
                }
                return false;
            }
            if (claimflags.blockEntityInteract) {
                EntityProtectionEvents.deny(player, "[!] No puedes interactuar con entidades aqu\u00ed.");
                return true;
            }
            return false;
        }
        return false;
    }
}

