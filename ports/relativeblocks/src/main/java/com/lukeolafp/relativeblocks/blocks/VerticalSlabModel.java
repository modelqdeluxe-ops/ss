package com.lukeolafp.relativeblocks.blocks;

import com.lukeolafp.relativeblocks.client.QuadEdit;
import com.lukeolafp.relativeblocks.client.Quads;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Una losa vertical se dibuja con el modelo de su losa doble, recortado a la mitad que toca (y las texturas, ajustadas
 * al recorte). En Fabric, con FabricBakedModel; aquí, quads de Forge (con el tinte del mundo si la losa lo tiene).
 */
@OnlyIn(Dist.CLIENT)
public class VerticalSlabModel implements BakedModel {
   private static final ModelProperty<Quads> PREPARED = new ModelProperty<>();
   private final VerticalSlabBlock block;
   private final Map<Direction, Quads> untinted = new ConcurrentHashMap<>();
   private volatile Boolean tinted;

   public VerticalSlabModel(VerticalSlabBlock block) {
      this.block = block;
   }

   private BakedModel full() {
      return Minecraft.getInstance().getBlockRenderer().getBlockModel(this.block.doubled());
   }

   private static Direction facingOf(@Nullable BlockState state) {
      return state != null && state.hasProperty(VerticalSlabBlock.FACING) ? state.getValue(VerticalSlabBlock.FACING) : Direction.EAST;
   }

   private boolean tinted() {
      Boolean known = this.tinted;
      if (known == null) {
         known = false;
         BlockState full = this.block.doubled();
         RandomSource random = RandomSource.create(42L);
         for (Direction face : BlockClusterBakedModel.FACES) {
            for (BakedQuad quad : this.full().getQuads(full, face, random)) {
               known |= quad.isTinted();
            }
         }
         this.tinted = known;
      }
      return known;
   }

   /** Los quads de la losa vertical (con el tinte del mundo si lo hay); también para dibujarla dentro de un grupo. */
   Quads quads(@Nullable BlockState state, @Nullable BlockAndTintGetter view, @Nullable BlockPos pos) {
      Direction facing = facingOf(state);
      if (view == null || !this.tinted()) {
         return this.untinted.computeIfAbsent(facing, f -> this.build(f, null, null));
      }
      return this.build(facing, view, pos);
   }

   private Quads build(Direction facing, @Nullable BlockAndTintGetter view, @Nullable BlockPos pos) {
      BlockState full = this.block.doubled();
      BakedModel model = this.full();
      Quads out = new Quads();
      boolean ao = model.useAmbientOcclusion();
      Axis axis = facing.getAxis();
      float low = facing.getAxisDirection() == AxisDirection.POSITIVE ? 0.5F : 0.0F;
      float high = low + 0.5F;
      RandomSource random = RandomSource.create();
      ChunkRenderTypeSet types = model.getRenderTypes(full, random, ModelData.EMPTY);

      for (RenderType type : types) {
         for (Direction face : BlockClusterBakedModel.FACES) {
            random.setSeed(pos == null ? 42L : full.getSeed(pos));
            for (BakedQuad quad : model.getQuads(full, face, random, ModelData.EMPTY, type)) {
               QuadEdit emitter = new QuadEdit(quad);
               float[] uFit = emitter.fitTexture(true);
               float[] vFit = emitter.fitTexture(false);
               float[][] cut = new float[4][];
               boolean moved = false;
               float first = Float.NaN;
               boolean flat = true;
               for (int vertex = 0; vertex < 4; vertex++) {
                  float[] at = new float[]{emitter.x(vertex), emitter.y(vertex), emitter.z(vertex)};
                  int i = axis.ordinal();
                  float clamped = Math.max(low, Math.min(high, at[i]));
                  moved |= clamped != at[i];
                  at[i] = clamped;
                  if (vertex == 0) {
                     first = clamped;
                  } else if (clamped != first) {
                     flat = false;
                  }
                  cut[vertex] = at;
               }
               // una cara que, recortada, queda plana sobre el eje de la losa: sobra (salvo las de ese mismo eje)
               if (!flat || quad.getDirection().getAxis() == axis) {
                  for (int vertex = 0; vertex < 4; vertex++) {
                     emitter.pos(vertex, cut[vertex][0], cut[vertex][1], cut[vertex][2]);
                  }
                  emitter.repaint(uFit, vFit, cut);
                  Direction cull = moved && quad.getDirection().getAxis() == axis ? null : face;
                  out.add(type, cull, BlockClusterBakedModel.finish(emitter, quad, Minecraft.getInstance().getBlockColors(), full, view, pos, ao), ao && quad.hasAmbientOcclusion());
               }
            }
         }
      }
      return out;
   }

   @Override
   public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
      if (!this.tinted()) {
         return data;
      }
      return data.derive().with(PREPARED, this.build(facingOf(state), level, pos)).build();
   }

   @Override
   public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
      Quads prepared = data.get(PREPARED);
      if (prepared == null) {
         prepared = this.quads(state, null, null);
      }
      return prepared.get(renderType, side);
   }

   @Override
   public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
      return this.getQuads(state, side, random, ModelData.EMPTY, null);
   }

   @Override
   public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
      return this.full().getRenderTypes(this.block.doubled(), rand, ModelData.EMPTY);
   }

   @Override
   public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
      List<RenderType> out = new ArrayList<>();
      for (RenderType type : this.full().getRenderTypes(this.block.doubled(), RandomSource.create(42L), ModelData.EMPTY)) {
         out.add(RenderTypeHelper.getEntityRenderType(type, fabulous));
      }
      return out.isEmpty() ? List.of(RenderTypeHelper.getEntityRenderType(RenderType.solid(), fabulous)) : out;
   }

   @Override
   public boolean useAmbientOcclusion() {
      return this.full().useAmbientOcclusion();
   }

   @Override
   public boolean isGui3d() {
      return true;
   }

   @Override
   public boolean usesBlockLight() {
      return true;
   }

   @Override
   public boolean isCustomRenderer() {
      return false;
   }

   @Override
   public TextureAtlasSprite getParticleIcon() {
      return this.full().getParticleIcon();
   }

   @Override
   public ItemTransforms getTransforms() {
      return this.full().getTransforms();
   }

   @Override
   public ItemOverrides getOverrides() {
      return ItemOverrides.EMPTY;
   }
}
