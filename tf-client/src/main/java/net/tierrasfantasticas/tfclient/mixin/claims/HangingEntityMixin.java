package net.tierrasfantasticas.tfclient.mixin.claims;

import net.tierrasfantasticas.tfclient.claims.TFClaims;
import net.tierrasfantasticas.tfclient.claims.util.DecorationProtection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={HangingEntity.class, ItemFrame.class, ArmorStand.class})
public abstract class HangingEntityMixin {
    @Inject(method={"hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void claimblocks$protectDecoration(DamageSource damagesource, float f, CallbackInfoReturnable<Boolean> callbackinforeturnable) {
        try {
            Entity entity = (Entity)(Object)this;
            if (DecorationProtection.blocksDamage(entity, damagesource)) {
                callbackinforeturnable.setReturnValue(false);
            }
        }
        catch (Throwable throwable) {
            TFClaims.LOGGER.error("[FantasticClaims] Fallo protegiendo una decoracion", throwable);
        }
    }
}

