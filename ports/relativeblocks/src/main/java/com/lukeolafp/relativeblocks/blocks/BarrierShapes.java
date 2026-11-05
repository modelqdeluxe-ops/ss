package com.lukeolafp.relativeblocks.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
final class BarrierShapes {
   private static final Direction[] FACES = new Direction[]{
      Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null
   };

   private BarrierShapes() {
   }

   private static BarrierLayout.Lookup lookup(BlockAndTintGetter view) {
      return new BarrierLayout.Lookup() {
         @Override
         public BlockClusterBlockEntity.RenderData dataAt(BlockPos pos) {
            // En Fabric, getBlockEntityRenderData; en Forge, el bloque del vecino en la copia del trozo que se dibuja
            // (getRenderData copia sus bloques: no se toca nada del mundo desde el hilo de dibujo)
            return view.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster ? cluster.getRenderData() : null;
         }

         @Override
         public BlockState stateAt(BlockPos pos) {
            return view.getBlockState(pos);
         }
      };
   }

   static List<BarrierShapes.Piece> build(BlockAndTintGetter view, BlockPos pos, BlockClusterBlockEntity.RenderData data, int cell, RandomSource random) {
      BlockState member = data.cells()[cell];
      BarrierLayout.Plan plan = BarrierLayout.plan(lookup(view), pos, data, cell);
      List<BarrierShapes.Piece> pieces = new ArrayList<>();
      BlockState postSource = plan.post();
      Set<BakedQuad> armQuads = Collections.newSetFromMap(new IdentityHashMap<>());

      for (Direction direction : Plane.HORIZONTAL) {
         if (BarrierJoins.reaches(postSource, direction)) {
            armQuads.addAll(armOf(postSource, direction, pos, random));
         }
      }

      List<BakedQuad> post = new ArrayList<>();

      for (BakedQuad quad : quadsOf(postSource, pos, random)) {
         if (!armQuads.contains(quad)) {
            post.add(quad);
         }
      }

      pieces.add(new BarrierShapes.Piece(post, 1.0F, (float)(plan.x() - 0.5), 1.0F, (float)(plan.z() - 0.5), null));

      for (BarrierLayout.Span span : plan.spans()) {
         BlockState source = span.bend() ? BarrierLayout.withArm(member, span.direction(), span.like()) : member;
         List<BakedQuad> template = armOf(source, span.direction(), pos, random);
         if (!template.isEmpty()) {
            double[] laid = BarrierLayout.stretch(span.direction(), innerEnd(template, span.direction()), span.from(), span.to());
            float sideways = (float)(span.at() - 0.5);
            pieces.add(
               span.direction().getAxis() == Axis.X
                  ? new BarrierShapes.Piece(template, (float)laid[0], (float)laid[1], 1.0F, sideways, Axis.X)
                  : new BarrierShapes.Piece(template, 1.0F, sideways, (float)laid[0], (float)laid[1], Axis.Z)
            );
         }
      }

      return pieces;
   }

   static List<BakedQuad> quadsOf(BlockState state, BlockPos pos, RandomSource random) {
      BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
      List<BakedQuad> quads = new ArrayList<>();

      for (Direction face : FACES) {
         random.setSeed(state.getSeed(pos));
         quads.addAll(model.getQuads(state, face, random));
      }

      return quads;
   }

   private static List<BakedQuad> armOf(BlockState state, Direction direction, BlockPos pos, RandomSource random) {
      BlockState through = BarrierLayout.through(state, direction);
      Set<BakedQuad> rest = Collections.newSetFromMap(new IdentityHashMap<>());
      rest.addAll(quadsOf(BarrierLayout.withoutArm(through, direction), pos, random));
      List<BakedQuad> arm = new ArrayList<>();

      for (BakedQuad quad : quadsOf(through, pos, random)) {
         if (!rest.contains(quad)) {
            arm.add(quad);
         }
      }

      return arm;
   }

   private static double innerEnd(List<BakedQuad> quads, Direction direction) {
      int index = direction.getAxis() == Axis.X ? 0 : 2;
      boolean positive = direction.getAxisDirection() == AxisDirection.POSITIVE;
      float inner = positive ? Float.MAX_VALUE : -Float.MAX_VALUE;

      for (BakedQuad quad : quads) {
         int[] data = quad.getVertices();
         int stride = data.length / 4;

         for (int vertex = 0; vertex < 4; vertex++) {
            float value = Float.intBitsToFloat(data[vertex * stride + index]);
            inner = positive ? Math.min(inner, value) : Math.max(inner, value);
         }
      }

      return Math.abs(inner) == Float.MAX_VALUE ? 0.5 : (double)inner;
   }

   static record Piece(List<BakedQuad> quads, float scaleX, float shiftX, float scaleZ, float shiftZ, @Nullable Axis runs) {
      float x(float x) {
         return x * this.scaleX + this.shiftX;
      }

      float z(float z) {
         return z * this.scaleZ + this.shiftZ;
      }
   }
}
