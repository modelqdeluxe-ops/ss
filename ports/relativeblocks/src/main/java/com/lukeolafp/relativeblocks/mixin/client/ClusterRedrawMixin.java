package com.lukeolafp.relativeblocks.mixin.client;

import com.lukeolafp.relativeblocks.blocks.BlockClusterBlockEntity;
import com.lukeolafp.relativeblocks.init.BlockInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cuando un bloque se convierte en grupo, el cliente recibe el bloque antes que lo que lleva dentro: hasta que llega,
 * se sigue viendo el bloque de antes (no un hueco vacío un instante).
 */
@Mixin(LevelRenderer.class)
public abstract class ClusterRedrawMixin {
   @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
   private void relativeblocks$waitForMembers(BlockGetter world, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo info) {
      if (arrivingEmpty(pos, oldState, newState)) {
         info.cancel();
      }
   }

   @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"), cancellable = true)
   private void relativeblocks$waitForMembersToo(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo info) {
      if (arrivingEmpty(pos, oldState, newState)) {
         info.cancel();
      }
   }

   private static boolean arrivingEmpty(BlockPos pos, BlockState oldState, BlockState newState) {
      if (BlockInit.BLOCK_CLUSTER == null || !newState.is(BlockInit.BLOCK_CLUSTER) || oldState.is(BlockInit.BLOCK_CLUSTER)) {
         return false;
      }
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.level.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster && cluster.memberCount() == 0) {
         cluster.setStandIn(oldState);
         return true;
      }
      return false;
   }
}
