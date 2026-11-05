package com.lukeolafp.relativeblocks.blocks;

import com.lukeolafp.relativeblocks.client.QuadEdit;
import com.lukeolafp.relativeblocks.client.Quads;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * El dibujo de un grupo: los modelos de sus bloques, cada uno movido a su sitio (y las vallas y muros, con sus brazos
 * estirados hasta los vecinos). En Fabric lo hacía el Renderer de Fabric con emitBlockQuads; en Forge el bloque da sus
 * datos (BlockClusterBlockEntity.getModelData), getModelData(mundo, posición) prepara los quads con la luz y el tinte de
 * cada bloque y getQuads los da por tipo de render.
 */
@OnlyIn(Dist.CLIENT)
public class BlockClusterBakedModel extends BakedModelWrapper<BakedModel> {
   public static final ModelProperty<BlockClusterBlockEntity.RenderData> DATA = BlockClusterBlockEntity.MODEL_DATA;
   private static final ModelProperty<Quads> PREPARED = new ModelProperty<>();
   static final Direction[] FACES = new Direction[]{
      Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null
   };

   public BlockClusterBakedModel(BakedModel base) {
      super(base);
   }

   @Override
   public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
      BlockClusterBlockEntity.RenderData renderData = data.get(DATA);
      if (renderData == null && level.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         renderData = cluster.getRenderData();
      }
      if (renderData == null) {
         return data;
      }
      Quads prepared = build(level, pos, renderData, RandomSource.create(), -1);
      return data.derive().with(DATA, renderData).with(PREPARED, prepared).build();
   }

   @Override
   public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
      Quads prepared = data.get(PREPARED);
      if (prepared != null && !prepared.isEmpty()) {
         return ChunkRenderTypeSet.of(prepared.types());
      }
      BlockClusterBlockEntity.RenderData renderData = data.get(DATA);
      if (renderData != null) {
         Set<RenderType> types = new LinkedHashSet<>();
         for (BlockState member : renderData.cells()) {
            if (member != null) {
               BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(member);
               model.getRenderTypes(member, rand, ModelData.EMPTY).forEach(types::add);
            }
         }
         if (!types.isEmpty()) {
            return ChunkRenderTypeSet.of(types);
         }
      }
      return ChunkRenderTypeSet.of(RenderType.cutout());
   }

   @Override
   public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
      int only = ClusterMemberFocus.damagedCell();
      Quads prepared = data.get(PREPARED);
      if (only < 0 && prepared != null) {
         return prepared.get(renderType, side);
      }
      BlockClusterBlockEntity.RenderData renderData = data.get(DATA);
      if (renderData == null) {
         return List.of();
      }
      // sin el mundo (las grietas al picar): los bloques del grupo en su sitio; solo el que se mira
      return build(null, null, renderData, rand, only).get(renderType, side);
   }

   @Override
   public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
      return List.of();
   }

   @Override
   public boolean useAmbientOcclusion() {
      return true;
   }

   @Override
   public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
      // cada quad lleva la de su bloque (BakedQuad.hasAmbientOcclusion): las antorchas y las flores, sin
      return true;
   }

   static Quads build(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, BlockClusterBlockEntity.RenderData data, RandomSource random, int only) {
      Minecraft client = Minecraft.getInstance();
      BlockColors blockColors = client.getBlockColors();
      Quads out = new Quads();
      BlockPos seedPos = pos == null ? BlockPos.ZERO : pos;

      for (int cell = 0; cell < 4; cell++) {
         BlockState member = data.cells()[cell];
         if (member == null || only >= 0 && cell != only) {
            continue;
         }
         float dx = (float)BlockClusterBlockEntity.cellOffsetX(data.layout(), cell);
         float dy = (float)BlockClusterBlockEntity.cellOffsetY(data.layout(), cell);
         float dz = (float)BlockClusterBlockEntity.cellOffsetZ(data.layout(), cell);
         BakedModel memberModel = client.getBlockRenderer().getBlockModel(member);
         int memberLight = view != null ? LevelRenderer.getLightColor(view, member, pos) : -1;
         boolean ao = memberModel.useAmbientOcclusion();
         random.setSeed(member.getSeed(seedPos));
         ChunkRenderTypeSet types = memberModel.getRenderTypes(member, random, ModelData.EMPTY);

         if (memberModel instanceof VerticalSlabModel drawn) {
            Quads slab = drawn.quads(member, view, pos);
            for (RenderType type : slab.types()) {
               for (Direction face : FACES) {
                  for (BakedQuad quad : slab.get(type, face)) {
                     out.add(type, face, quad, ao);
                  }
               }
            }
         } else if (view != null && BarrierLayout.lays(data, member)) {
            RenderType type = types.isEmpty() ? RenderType.solid() : types.iterator().next();
            for (BarrierShapes.Piece piece : BarrierShapes.build(view, pos, data, cell, random)) {
               for (BakedQuad quad : piece.quads()) {
                  QuadEdit emitter = new QuadEdit(quad);
                  float[] uFit = emitter.fitTexture(true);
                  float[] vFit = emitter.fitTexture(false);
                  float[][] paintAt = new float[4][];
                  for (int vertex = 0; vertex < 4; vertex++) {
                     float x = emitter.x(vertex);
                     float y = emitter.y(vertex);
                     float z = emitter.z(vertex);
                     float movedX = piece.x(x);
                     float movedZ = piece.z(z);
                     paintAt[vertex] = new float[]{piece.runs() == Axis.X ? movedX : x, y, piece.runs() == Axis.Z ? movedZ : z};
                     emitter.pos(vertex, movedX, y, movedZ);
                  }
                  emitter.repaint(uFit, vFit, paintAt);
                  emitter.light(memberLight);
                  out.add(type, null, finish(emitter, quad, blockColors, member, view, pos, ao), ao && quad.hasAmbientOcclusion());
               }
            }
         } else {
            for (RenderType type : types) {
               for (Direction face : FACES) {
                  random.setSeed(member.getSeed(seedPos));
                  for (BakedQuad quad : memberModel.getQuads(member, face, random, ModelData.EMPTY, type)) {
                     QuadEdit emitter = new QuadEdit(quad);
                     for (int vertex = 0; vertex < 4; vertex++) {
                        emitter.pos(vertex, emitter.x(vertex) + dx, emitter.y(vertex) + dy, emitter.z(vertex) + dz);
                     }
                     if (view != null) {
                        emitter.light(memberLight);
                     }
                     out.add(type, face, finish(emitter, quad, blockColors, member, view, pos, ao), ao && quad.hasAmbientOcclusion());
                  }
               }
            }
         }
      }
      return out;
   }

   /** El tinte del bloque (hierba, hojas…) metido en el color de los vértices: el grupo no tiene tinte propio. */
   static BakedQuad finish(QuadEdit emitter, BakedQuad quad, BlockColors blockColors, BlockState member, @Nullable BlockAndTintGetter view, @Nullable BlockPos pos, boolean ao) {
      if (quad.isTinted()) {
         emitter.tint(blockColors.getColor(member, view, pos, quad.getTintIndex()));
         return emitter.build(-1, ao && quad.hasAmbientOcclusion());
      }
      return emitter.build(-1, ao && quad.hasAmbientOcclusion());
   }
}
