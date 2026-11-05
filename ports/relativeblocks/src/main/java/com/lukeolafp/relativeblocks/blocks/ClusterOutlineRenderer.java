package com.lukeolafp.relativeblocks.blocks;

import com.lukeolafp.relativeblocks.init.BlockInit;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHighlightEvent;

/** El contorno al mirar un grupo: solo el del bloque que se mira (en Fabric, WorldRenderEvents.BLOCK_OUTLINE). */
@OnlyIn(Dist.CLIENT)
public final class ClusterOutlineRenderer {
   private static final float OUTLINE_ALPHA = 0.4F;

   private ClusterOutlineRenderer() {
   }

   public static void onHighlight(RenderHighlightEvent.Block event) {
      if (BlockInit.BLOCK_CLUSTER == null) {
         return;
      }
      Level level = Minecraft.getInstance().level;
      BlockHitResult hit = event.getTarget();
      BlockPos pos = hit.getBlockPos();
      if (level == null || !level.getBlockState(pos).is(BlockInit.BLOCK_CLUSTER)) {
         return;
      }
      if (!(level.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster)) {
         return;
      }
      int cell = RelativePlacement.nearestCell(cluster, pos, hit.getLocation());
      if (cell < 0) {
         return;
      }
      VoxelShape shape = BlockClusterBlock.cellShape(level, pos, cluster, cell, false);
      if (shape.isEmpty()) {
         return;
      }
      Vec3 camera = event.getCamera().getPosition();
      VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
      LevelRenderer.renderVoxelShape(
         event.getPoseStack(), lines, shape, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, 0.0F, 0.0F, 0.0F, OUTLINE_ALPHA, true
      );
      event.setCanceled(true);
   }
}
