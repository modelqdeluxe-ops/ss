package com.lukeolafp.relativeblocks.mixin.client;

import com.lukeolafp.relativeblocks.init.BlockInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Al romper un grupo, el cliente no lo quita entero: lo hace el servidor, que solo quita el bloque que se mira. (El
 * sonido de picar el que se mira, que en Fabric era otro @Redirect, en Forge es BlockClusterBlock.getSoundType.)
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClusterBreakingMixin {
   @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
   private void relativeblocks$leaveClusterToTheServer(BlockPos pos, CallbackInfoReturnable<Boolean> info) {
      Minecraft client = Minecraft.getInstance();
      if (BlockInit.BLOCK_CLUSTER != null && client.level != null && client.level.getBlockState(pos).is(BlockInit.BLOCK_CLUSTER)) {
         info.setReturnValue(true);
      }
   }
}
