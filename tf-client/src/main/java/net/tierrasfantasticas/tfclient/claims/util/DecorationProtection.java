package net.tierrasfantasticas.tfclient.claims.util;

import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

public final class DecorationProtection {
    private DecorationProtection() {
    }

    public static boolean isDecoration(Entity entity) {
        return !ClaimConfig.get().protectDecoration ? false : entity instanceof HangingEntity || entity instanceof ArmorStand;
    }

    public static Claim claimFor(Level level, Entity entity) {
        if (level != null && entity != null) {
            BlockPos blockpos;
            ClaimManager claimmanager = ClaimManager.getInstance();
            Claim claim = claimmanager.getClaimAt(level, blockpos = entity.blockPosition());
            if (claim != null) {
                return claim;
            }
            Direction direction = entity.getDirection();
            return direction != null ? claimmanager.getClaimAt(level, blockpos.relative(direction.getOpposite())) : null;
        }
        return null;
    }

    private static boolean isBypassing(Player player) {
        return player.hasPermissions(2) && ClaimManager.getInstance().isBypassing(player.getUUID());
    }

    public static boolean blocksPlayer(Claim claim, Player player) {
        if (claim == null || player == null) {
            return false;
        }
        if (!claim.canModify(player) && !DecorationProtection.isBypassing(player)) {
            ClaimFlags claimflags = claim.getFlags();
            return claimflags.blockBuilding || claimflags.blockEntityInteract || claimflags.publicMode;
        }
        return false;
    }

    public static boolean blocksDamage(Entity entity, DamageSource damagesource) {
        if (!DecorationProtection.isDecoration(entity)) {
            return false;
        }
        Level level = entity.level();
        if (level != null && !level.isClientSide()) {
            Claim claim = DecorationProtection.claimFor(level, entity);
            if (claim == null) {
                return false;
            }
            Player player = DecorationProtection.responsiblePlayer(damagesource);
            if (player != null) {
                return DecorationProtection.blocksPlayer(claim, player);
            }
            ClaimFlags claimflags = claim.getFlags();
            return damagesource != null && damagesource.is(DamageTypeTags.IS_EXPLOSION) ? ClaimConfig.get().protectDecorationFromExplosions && (claimflags.blockExplosions || claimflags.publicMode) : claimflags.blockBuilding || claimflags.publicMode;
        }
        return false;
    }

    public static Player responsiblePlayer(DamageSource damagesource) {
        if (damagesource == null) {
            return null;
        }
        Entity entity = damagesource.getEntity();
        if (entity instanceof Player) {
            return (Player)entity;
        }
        Entity entity1 = damagesource.getDirectEntity();
        return entity1 instanceof Player ? (Player)entity1 : DecorationProtection.ownerOf(entity1);
    }

    public static Player ownerOf(Entity entity) {
        Entity entity1;
        if (entity instanceof Projectile && (entity1 = ((Projectile)entity).getOwner()) instanceof Player) {
            return (Player)entity1;
        }
        return null;
    }
}

