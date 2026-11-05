package com.lukeolafp.relativeblocks.blocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class BarrierLayout {
   private static final double EPSILON = 1.0E-4;

   private BarrierLayout() {
   }

   public static BarrierLayout.Lookup of(BlockGetter world) {
      return new BarrierLayout.Lookup() {
         @Override
         public BlockClusterBlockEntity.RenderData dataAt(BlockPos pos) {
            return world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster ? cluster.getRenderData() : null;
         }

         @Override
         public BlockState stateAt(BlockPos pos) {
            return world.getBlockState(pos);
         }
      };
   }

   public static boolean lays(BlockClusterBlockEntity.RenderData data, BlockState member) {
      return BarrierJoins.hasArms(member) && BarrierJoins.splitAxis(data.layout()) != null;
   }

   public static BarrierLayout.Plan plan(BarrierLayout.Lookup lookup, BlockPos pos, BlockClusterBlockEntity.RenderData data, int cell) {
      BlockState member = data.cells()[cell];
      double[] stance = stance(lookup, pos, data, cell);
      List<BarrierLayout.Span> spans = new ArrayList<>();
      BlockState post = member;

      for (Direction direction : Plane.HORIZONTAL) {
         if (BarrierJoins.reaches(member, direction) && layArm(lookup, pos, data, cell, member, stance, direction, spans)) {
            post = withoutArm(post, direction);
         }
      }

      return new BarrierLayout.Plan(stance[0], stance[1], spans, post);
   }

   public static boolean crossesAnother(BarrierLayout.Lookup lookup, BlockPos pos, BlockClusterBlockEntity.RenderData data, int cell, Direction direction) {
      BlockState member = data.cells()[cell];
      if (member != null && BarrierJoins.splitAxis(data.layout()) != null && !BarrierJoins.reaches(member, direction.getOpposite())) {
         Axis axis = direction.getAxis();
         Axis lateral = BarrierJoins.across(axis);
         double[] mine = stance(lookup, pos, data, cell);
         double face = direction.getAxisDirection() == AxisDirection.POSITIVE ? 1.0 : 0.0;

         for (int other = 0; other < 4; other++) {
            BlockState line = data.cells()[other];
            if (other != cell && line != null && BarrierJoins.hasArms(line) && !BarrierJoins.pairs(member, line)) {
               double[] theirs = stance(lookup, pos, data, other);
               if (between(on(mine, axis), on(theirs, axis), face)) {
                  double crossing = on(mine, lateral);
                  double centre = on(theirs, lateral);
                  if (Math.abs(crossing - centre) < 1.0E-4) {
                     return true;
                  }

                  Direction toward = Direction.fromAxisAndDirection(lateral, crossing > centre ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
                  if (BarrierJoins.reaches(line, toward)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static double on(double[] stance, Axis axis) {
      return axis == Axis.X ? stance[0] : stance[1];
   }

   private static double[] stance(BarrierLayout.Lookup lookup, BlockPos pos, BlockClusterBlockEntity.RenderData data, int cell) {
      BlockState member = data.cells()[cell];
      Axis split = BarrierJoins.splitAxis(data.layout());
      Axis line = BarrierJoins.across(split);
      double acrossLine = BarrierJoins.coord(data.layout(), cell, split);
      double alongLine = 0.5;
      Direction positive = Direction.fromAxisAndDirection(line, AxisDirection.POSITIVE);
      boolean reachesBothWays = BarrierJoins.reaches(member, positive) && BarrierJoins.reaches(member, positive.getOpposite());
      if (!reachesBothWays) {
         double total = 0.0;
         int count = 0;

         for (AxisDirection sign : AxisDirection.values()) {
            Direction direction = Direction.fromAxisAndDirection(split, sign);
            if (BarrierJoins.reaches(member, direction)) {
               double port = port(lookup, pos, data, cell, member, direction, 0.5);
               if (!Double.isNaN(port)) {
                  total += port;
                  count++;
               }
            }
         }

         if (count > 0) {
            alongLine = total / (double)count;
         }
      }

      return split == Axis.X ? new double[]{acrossLine, alongLine} : new double[]{alongLine, acrossLine};
   }

   private static double port(
      BarrierLayout.Lookup lookup, BlockPos pos, BlockClusterBlockEntity.RenderData data, int cell, BlockState member, Direction direction, double own
   ) {
      BlockPos neighbourPos = pos.relative(direction);
      Axis lateral = BarrierJoins.across(direction.getAxis());
      boolean ownPinned = BarrierJoins.pinned(data.layout(), lateral);
      BlockClusterBlockEntity.RenderData there = lookup.dataAt(neighbourPos);
      if (there != null) {
         int partner = BarrierJoins.partner(data, cell, member, direction, there);
         if (partner < 0) {
            return Double.NaN;
         } else {
            double theirs = BarrierJoins.coord(there.layout(), partner, lateral);
            boolean theirsPinned = BarrierJoins.pinned(there.layout(), lateral);
            if (theirsPinned && ownPinned) {
               return (own + theirs) / 2.0;
            } else if (theirsPinned) {
               return theirs;
            } else {
               return ownPinned ? own : 0.5;
            }
         }
      } else {
         return BarrierJoins.isBarrier(lookup.stateAt(neighbourPos)) ? 0.5 : Double.NaN;
      }
   }

   private static boolean layArm(
      BarrierLayout.Lookup lookup,
      BlockPos pos,
      BlockClusterBlockEntity.RenderData data,
      int cell,
      BlockState member,
      double[] stance,
      Direction direction,
      List<BarrierLayout.Span> spans
   ) {
      Axis axis = direction.getAxis();
      Axis lateral = BarrierJoins.across(axis);
      double start = on(stance, axis);
      double end = direction.getAxisDirection() == AxisDirection.POSITIVE ? 1.0 : 0.0;
      double from = on(stance, lateral);
      double port = port(lookup, pos, data, cell, member, direction, BarrierJoins.coord(data.layout(), cell, lateral));
      double to = Double.isNaN(port) ? from : port;
      double half = halfWidth(member, direction);
      boolean leftPost = false;
      if (BarrierJoins.reaches(member, Direction.fromAxisAndDirection(lateral, AxisDirection.POSITIVE))
         && BarrierJoins.reaches(member, Direction.fromAxisAndDirection(lateral, AxisDirection.NEGATIVE))) {
         leftPost = Math.abs(to - from) > 1.0E-4;
         from = to;
      }

      BlockPos beyond = pos.relative(direction);
      BlockState plain = lookup.stateAt(beyond);
      if (lookup.dataAt(beyond) == null
         && BarrierJoins.hasArms(plain)
         && BarrierJoins.pairs(member, plain)
         && BarrierJoins.meetsSideOn(plain, direction.getOpposite(), data.layout(), cell)) {
         spans.add(new BarrierLayout.Span(direction, start, end + (end - 0.5), from, direction));
         return leftPost;
      } else {
         for (int other = 0; other < 4; other++) {
            BlockState blocker = data.cells()[other];
            if (other != cell && blocker != null && BarrierJoins.hasArms(blocker) && BarrierJoins.reaches(blocker, direction)) {
               double at = BarrierJoins.coord(data.layout(), other, axis);
               if (between(start, at, end)) {
                  double middle = (start + end) / 2.0;
                  double crossing = between(start, at, middle) ? from : to;
                  double[] theirs = stance(lookup, pos, data, other);
                  if (covers(blocker, theirs, lateral, crossing, halfWidth(blocker, direction))) {
                     end = at;
                     if (crossing == from) {
                        to = from;
                     }
                  }
               }
            }
         }

         if (Math.abs(to - from) < 1.0E-4) {
            spans.add(new BarrierLayout.Span(direction, start, end, from, direction));
            return leftPost;
         } else {
            double sign = Math.signum(end - start);
            double middle = (start + end) / 2.0;
            Direction sideways = Direction.fromAxisAndDirection(lateral, to > from ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
            spans.add(new BarrierLayout.Span(direction, start, middle + sign * half, from, direction));
            if (Math.abs(to - from) > 2.0 * half + 1.0E-4) {
               double step = Math.signum(to - from) * half;
               spans.add(new BarrierLayout.Span(sideways, from + step, to - step, middle, direction));
               spans.add(new BarrierLayout.Span(direction, middle - sign * half, end, to, direction));
            } else {
               spans.add(new BarrierLayout.Span(direction, middle + sign * half, end, to, direction));
            }

            return leftPost;
         }
      }
   }

   private static boolean covers(BlockState barrier, double[] stance, Axis line, double place, double half) {
      double centre = on(stance, line);
      if (Math.abs(place - centre) <= half + 1.0E-4) {
         return true;
      } else {
         Direction toward = Direction.fromAxisAndDirection(line, place > centre ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
         return BarrierJoins.reaches(barrier, toward);
      }
   }

   private static boolean between(double a, double value, double b) {
      return value > Math.min(a, b) + 1.0E-4 && value < Math.max(a, b) - 1.0E-4;
   }

   public static double[] stretch(Direction direction, double inner, double from, double to) {
      double face = direction.getAxisDirection() == AxisDirection.POSITIVE ? 1.0 : 0.0;
      double anchor = from + (inner - 0.5);
      double scale = (to - anchor) / (face - inner);
      if (!(scale > 0.0) || Double.isInfinite(scale)) {
         scale = (to - from) / (face - 0.5);
      }

      return new double[]{scale, to - scale * face};
   }

   public static BlockState withoutArm(BlockState state, Direction direction) {
      BooleanProperty side = BarrierJoins.side(direction);
      if (side != null && state.hasProperty(side)) {
         return (BlockState)state.setValue(side, false);
      } else {
         EnumProperty<WallSide> wall = BarrierJoins.wallSide(direction);
         return wall != null && state.hasProperty(wall) ? (BlockState)state.setValue(wall, WallSide.NONE) : state;
      }
   }

   public static BlockState withArm(BlockState state, Direction direction, Direction like) {
      BooleanProperty side = BarrierJoins.side(direction);
      if (side != null && state.hasProperty(side)) {
         return (BlockState)state.setValue(side, true);
      } else {
         EnumProperty<WallSide> wall = BarrierJoins.wallSide(direction);
         EnumProperty<WallSide> model = BarrierJoins.wallSide(like);
         if (wall != null && model != null && state.hasProperty(wall)) {
            WallSide shape = (WallSide)state.getValue(model);
            return (BlockState)state.setValue(wall, shape == WallSide.NONE ? WallSide.LOW : shape);
         } else {
            return state;
         }
      }
   }

   public static BlockState through(BlockState state, Direction direction) {
      return withArm(withArm(state, direction, direction), direction.getOpposite(), direction);
   }

   public static BlockState postOf(BlockState state) {
      for (Direction direction : Plane.HORIZONTAL) {
         state = withoutArm(state, direction);
      }

      return state;
   }

   private static VoxelShape shapeOf(BlockState state, boolean collision) {
      return collision ? state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
   }

   private static VoxelShape armShape(BlockState state, Direction direction, boolean collision) {
      BlockState through = through(state, direction);
      return Shapes.joinUnoptimized(shapeOf(through, collision), shapeOf(withoutArm(through, direction), collision), BooleanOp.ONLY_FIRST);
   }

   private static double halfWidth(BlockState state, Direction direction) {
      VoxelShape arm = armShape(state, direction, false);
      if (arm.isEmpty()) {
         return 0.0625;
      } else {
         AABB box = arm.bounds();
         return direction.getAxis() == Axis.X ? (box.maxZ - box.minZ) / 2.0 : (box.maxX - box.minX) / 2.0;
      }
   }

   public static VoxelShape shape(BarrierLayout.Lookup lookup, BlockPos pos, BlockClusterBlockEntity.RenderData data, int cell, boolean collision) {
      BlockState member = data.cells()[cell];
      BarrierLayout.Plan plan = plan(lookup, pos, data, cell);
      VoxelShape shape = shapeOf(postOf(member), collision).move(plan.x() - 0.5, 0.0, plan.z() - 0.5);

      for (BarrierLayout.Span span : plan.spans()) {
         BlockState source = span.bend() ? withArm(member, span.direction(), span.like()) : member;
         VoxelShape arm = armShape(source, span.direction(), collision);
         if (!arm.isEmpty()) {
            boolean alongX = span.direction().getAxis() == Axis.X;
            boolean positive = span.direction().getAxisDirection() == AxisDirection.POSITIVE;
            AABB bounds = arm.bounds();
            double inner = alongX ? (positive ? bounds.minX : bounds.maxX) : (positive ? bounds.minZ : bounds.maxZ);
            double[] laid = stretch(span.direction(), inner, span.from(), span.to());
            double sideways = span.at() - 0.5;

            for (AABB box : arm.toAabbs()) {
               double a = (alongX ? box.minX : box.minZ) * laid[0] + laid[1];
               double b = (alongX ? box.maxX : box.maxZ) * laid[0] + laid[1];
               double lo = Math.max(0.0, Math.min(a, b));
               double hi = Math.min(1.0, Math.max(a, b));
               if (!(hi - lo < 1.0E-4)) {
                  VoxelShape piece = alongX
                     ? Shapes.box(lo, box.minY, box.minZ + sideways, hi, box.maxY, box.maxZ + sideways)
                     : Shapes.box(box.minX + sideways, box.minY, lo, box.maxX + sideways, box.maxY, hi);
                  shape = Shapes.or(shape, piece);
               }
            }
         }
      }

      return shape;
   }

   public interface Lookup {
      @Nullable
      BlockClusterBlockEntity.RenderData dataAt(BlockPos var1);

      BlockState stateAt(BlockPos var1);
   }

   public static record Plan(double x, double z, List<BarrierLayout.Span> spans, BlockState post) {
   }

   public static record Span(Direction direction, double from, double to, double at, Direction like) {
      public boolean bend() {
         return this.direction != this.like;
      }
   }
}
