package com.lukeolafp.relativeblocks.mixin.client;

import com.lukeolafp.relativeblocks.blocks.ClusterMemberFocus;
import com.lukeolafp.relativeblocks.init.BlockInit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Las grietas al picar salen solo en el bloque del grupo que se mira. En Forge, LevelRenderer llama a la versión de
 * renderBreakingTexture con ModelData (la añade Forge; no tiene nombre de Minecraft).
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class ClusterDamageMixin {
   @Inject(
      method = "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraftforge/client/model/data/ModelData;)V",
      at = @At("HEAD"),
      remap = false
   )
   private void relativeblocks$onlyCrackOneMember(
      BlockState state, BlockPos pos, BlockAndTintGetter world, PoseStack matrices, VertexConsumer vertexConsumer, ModelData data, CallbackInfo info
   ) {
      if (BlockInit.BLOCK_CLUSTER != null && state.is(BlockInit.BLOCK_CLUSTER)) {
         ClusterMemberFocus.beginDamage(pos);
      }
   }

   @Inject(
      method = "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraftforge/client/model/data/ModelData;)V",
      at = @At("RETURN"),
      remap = false
   )
   private void relativeblocks$doneCracking(
      BlockState state, BlockPos pos, BlockAndTintGetter world, PoseStack matrices, VertexConsumer vertexConsumer, ModelData data, CallbackInfo info
   ) {
      ClusterMemberFocus.endDamage();
   }
}
