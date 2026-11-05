package com.lukeolafp.relativeblocks.blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.jetbrains.annotations.Nullable;

public final class BarrierJoins {
   private BarrierJoins() {
   }

   public static boolean hasArms(BlockState state) {
      return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof IronBarsBlock || state.getBlock() instanceof WallBlock;
   }

   public static boolean isBarrier(BlockState state) {
      return hasArms(state) || state.getBlock() instanceof FenceGateBlock;
   }

   public static boolean pairs(BlockState a, BlockState b) {
      return joinsOneWay(a, b) || joinsOneWay(b, a);
   }

   private static boolean joinsOneWay(BlockState self, BlockState other) {
      if (self.getBlock() instanceof FenceBlock) {
         return other.getBlock() instanceof FenceGateBlock
            ? true
            : other.getBlock() instanceof FenceBlock && (other.is(self.getBlock()) || other.is(BlockTags.WOODEN_FENCES) == self.is(BlockTags.WOODEN_FENCES));
      } else if (self.getBlock() instanceof IronBarsBlock) {
         return other.getBlock() instanceof IronBarsBlock || other.is(BlockTags.WALLS);
      } else {
         return !(self.getBlock() instanceof WallBlock)
            ? false
            : other.is(BlockTags.WALLS) || other.getBlock() instanceof IronBarsBlock || other.getBlock() instanceof FenceGateBlock;
      }
   }

   public static boolean reaches(BlockState state, Direction direction) {
      BooleanProperty side = switch (direction) {
         case NORTH -> BlockStateProperties.NORTH;
         case SOUTH -> BlockStateProperties.SOUTH;
         case EAST -> BlockStateProperties.EAST;
         case WEST -> BlockStateProperties.WEST;
         default -> null;
      };
      if (side != null && state.hasProperty(side)) {
         return (Boolean)state.getValue(side);
      } else {
         EnumProperty<WallSide> wall = wallSide(direction);
         return wall != null && state.hasProperty(wall) && state.getValue(wall) != WallSide.NONE;
      }
   }

   @Nullable
   public static EnumProperty<WallSide> wallSide(Direction direction) {
      return switch (direction) {
         case NORTH -> BlockStateProperties.NORTH_WALL;
         case SOUTH -> BlockStateProperties.SOUTH_WALL;
         case EAST -> BlockStateProperties.EAST_WALL;
         case WEST -> BlockStateProperties.WEST_WALL;
         default -> null;
      };
   }

   @Nullable
   public static BooleanProperty side(Direction direction) {
      return switch (direction) {
         case NORTH -> BlockStateProperties.NORTH;
         case SOUTH -> BlockStateProperties.SOUTH;
         case EAST -> BlockStateProperties.EAST;
         case WEST -> BlockStateProperties.WEST;
         default -> null;
      };
   }

   @Nullable
   public static Axis splitAxis(int layout) {
      return switch (layout) {
         case 1 -> Axis.X;
         case 2 -> Axis.Z;
         default -> null;
      };
   }

   public static double coord(int layout, int cell, Axis axis) {
      double offset = axis == Axis.X ? BlockClusterBlockEntity.cellOffsetX(layout, cell) : BlockClusterBlockEntity.cellOffsetZ(layout, cell);
      return 0.5 + offset;
   }

   public static boolean pinned(int layout, Axis axis) {
      return splitAxis(layout) == axis;
   }

   public static Axis across(Axis axis) {
      return axis == Axis.X ? Axis.Z : Axis.X;
   }

   public static boolean meetsSideOn(BlockState plain, Direction fromPlain, int layout, int cell) {
      Axis lateral = across(fromPlain.getAxis());
      if (!pinned(layout, lateral)) {
         return false;
      } else {
         double at = coord(layout, cell, lateral);
         return Math.abs(at - 0.5) < 1.0E-4
            ? false
            : reaches(plain, Direction.fromAxisAndDirection(lateral, at > 0.5 ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE));
      }
   }

   public static double fromFace(double coord, Direction face) {
      return face.getAxisDirection() == AxisDirection.POSITIVE ? 1.0 - coord : coord;
   }

   @Nullable
   public static Direction comesFrom(BlockState state, Axis axis) {
      Direction positive = Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE);
      Direction negative = positive.getOpposite();
      boolean toPositive = reaches(state, positive);
      boolean toNegative = reaches(state, negative);
      if (toPositive == toNegative) {
         return null;
      } else {
         return toPositive ? positive : negative;
      }
   }

   public static int partner(
      @Nullable BlockClusterBlockEntity.RenderData here, int cell, BlockState self, Direction toward, BlockClusterBlockEntity.RenderData there
   ) {
      int chosen = choose(here, cell, self, toward, there);
      return chosen >= 0 && here != null && choose(there, chosen, there.cells()[chosen], toward.getOpposite(), here) != cell ? -1 : chosen;
   }

   private static int choose(
      @Nullable BlockClusterBlockEntity.RenderData here, int cell, BlockState self, Direction toward, BlockClusterBlockEntity.RenderData there
   ) {
      List<Integer> candidates = new ArrayList<>();

      for (int i = 0; i < 4; i++) {
         BlockState member = there.cells()[i];
         if (member != null && isBarrier(member) && pairs(self, member) && !screened(there, i, toward.getOpposite())) {
            candidates.add(i);
         }
      }

      if (candidates.isEmpty()) {
         return -1;
      } else {
         Axis along = toward.getAxis();
         Axis edge = across(along);
         Direction back = toward.getOpposite();
         Axis thereSplit = splitAxis(there.layout());
         Axis hereSplit = here == null ? null : splitAxis(here.layout());
         if (thereSplit == edge && hereSplit == along && !reaches(self, back)) {
            Direction from = comesFrom(self, edge);
            if (from != null) {
               int rank = halfRank(here.layout(), cell, toward);
               return withRank(candidates, there.layout(), from, rank);
            }
         } else if (thereSplit == along && hereSplit == edge) {
            Direction from = null;

            for (int ix : candidates) {
               if (!reaches(there.cells()[ix], toward)) {
                  from = comesFrom(there.cells()[ix], edge);
                  if (from != null) {
                     break;
                  }
               }
            }

            if (from != null) {
               int rank = halfRank(here.layout(), cell, from);
               return withRank(candidates, there.layout(), back, rank);
            }
         }

         if (candidates.size() == 1) {
            return candidates.get(0);
         } else if (thereSplit == along) {
            candidates.sort(Comparator.comparingDouble(ixx -> fromFace(coord(there.layout(), ixx, along), back)));
            return candidates.get(0);
         } else {
            double mine = here == null ? 0.5 : coord(here.layout(), cell, edge);
            candidates.sort(
               Comparator.<Integer>comparingDouble(ixx -> Math.abs(coord(there.layout(), ixx, edge) - mine))
                  .thenComparingInt(ixx -> there.cells()[ixx].is(self.getBlock()) ? 0 : 1)
            );
            return candidates.get(0);
         }
      }
   }

   public static int layoutSplitting(Axis axis) {
      return axis == Axis.X ? 1 : 2;
   }

   @Nullable
   public static int[] beyondCorner(BlockClusterBlockEntity.RenderData corner, int cell, Direction face) {
      Axis split = splitAxis(corner.layout());
      BlockState member = corner.cells()[cell];
      if (split != null && member != null && face.getAxis() == split) {
         Axis line = across(split);
         Direction from = comesFrom(member, line);
         if (from == null) {
            return null;
         } else {
            int rank = halfRank(corner.layout(), cell, face);
            int layout = layoutSplitting(line);
            int near = fromFace(coord(layout, 0, line), from) < fromFace(coord(layout, 1, line), from) ? 0 : 1;
            return new int[]{layout, rank == 0 ? near : 1 - near};
         }
      } else {
         return null;
      }
   }

   public static int barrierCount(BlockClusterBlockEntity.RenderData data) {
      int count = 0;

      for (BlockState member : data.cells()) {
         if (member != null && isBarrier(member)) {
            count++;
         }
      }

      return count;
   }

   public static boolean screened(BlockClusterBlockEntity.RenderData data, int cell, Direction face) {
      Axis axis = face.getAxis();
      double mine = fromFace(coord(data.layout(), cell, axis), face);

      for (int i = 0; i < 4; i++) {
         BlockState other = data.cells()[i];
         if (i != cell && other != null && !isBarrier(other) && fromFace(coord(data.layout(), i, axis), face) < mine - 1.0E-4) {
            return true;
         }
      }

      return false;
   }

   public static int halfRank(int layout, int cell, Direction face) {
      Axis axis = face.getAxis();
      int other = 1 - cell;
      return fromFace(coord(layout, cell, axis), face) < fromFace(coord(layout, other, axis), face) ? 0 : 1;
   }

   private static int withRank(List<Integer> candidates, int layout, Direction face, int rank) {
      for (int i : candidates) {
         if (i < 2 && halfRank(layout, i, face) == rank) {
            return i;
         }
      }

      return -1;
   }
}
