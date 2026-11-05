package com.lukeolafp.relativeblocks.blocks;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

@OnlyIn(Dist.CLIENT)
public final class ClusterMemberFocus {
   private static final ThreadLocal<Integer> DAMAGED_CELL = ThreadLocal.withInitial(() -> -1);

   private ClusterMemberFocus() {
   }

   public static int cellUnderCrosshair(BlockPos pos) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null) {
         return -1;
      } else {
         if (!(client.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) {
            return -1;
         }

         if (client.level.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
            return RelativePlacement.nearestCell(cluster, pos, hit.getLocation());
         }

         return -1;
      }
   }

   public static void beginDamage(BlockPos pos) {
      DAMAGED_CELL.set(cellUnderCrosshair(pos));
   }

   public static void endDamage() {
      DAMAGED_CELL.set(-1);
   }

   public static int damagedCell() {
      return DAMAGED_CELL.get();
   }
}
