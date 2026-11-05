package com.lukeolafp.relativeblocks;

import com.lukeolafp.relativeblocks.blocks.BlockClusterBakedModel;
import com.lukeolafp.relativeblocks.blocks.BlockClusterBlock;
import com.lukeolafp.relativeblocks.blocks.ClusterMemberFocus;
import com.lukeolafp.relativeblocks.blocks.ClusterOutlineRenderer;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabBlock;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabModel;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabs;
import com.lukeolafp.relativeblocks.client.GeneratedResources;
import com.lukeolafp.relativeblocks.init.BlockInit;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Lo del cliente (en Fabric, ClientModInitializer con ModelLoadingPlugin y WorldRenderEvents): el modelo del grupo
 * envuelve al suyo (ModelEvent.ModifyBakingResult), cada losa vertical usa su VerticalSlabModel (con un estado y un
 * modelo de objeto generados para que Minecraft no se queje de que faltan: GeneratedResources) y el contorno del bloque
 * que se mira (RenderHighlightEvent).
 */
@OnlyIn(Dist.CLIENT)
public final class RelativeBlocksClient {
   private RelativeBlocksClient() {
   }

   public static void init(IEventBus modBus) {
      modBus.addListener(RelativeBlocksClient::setup);
      modBus.addListener(RelativeBlocksClient::onModifyBakingResult);
      modBus.addListener(GeneratedResources::onAddPackFinders);
      MinecraftForge.EVENT_BUS.addListener(ClusterOutlineRenderer::onHighlight);
   }

   private static void setup(FMLClientSetupEvent event) {
      BlockClusterBlock.watchCrosshair(ClusterMemberFocus::cellUnderCrosshair);
   }

   private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
      Map<ResourceLocation, BakedModel> models = event.getModels();
      if (BlockInit.BLOCK_CLUSTER != null) {
         for (BlockState state : BlockInit.BLOCK_CLUSTER.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
            BakedModel base = models.get(key);
            if (base != null) {
               models.put(key, new BlockClusterBakedModel(base));
            }
         }
      }
      for (VerticalSlabBlock vertical : VerticalSlabs.all()) {
         VerticalSlabModel model = new VerticalSlabModel(vertical);
         for (BlockState state : vertical.getStateDefinition().getPossibleStates()) {
            models.put(BlockModelShaper.stateToModelLocation(state), model);
         }
         ResourceLocation id = VerticalSlabs.idOf(vertical);
         if (id != null) {
            models.put(new ModelResourceLocation(id, "inventory"), model);
         }
      }
   }
}
