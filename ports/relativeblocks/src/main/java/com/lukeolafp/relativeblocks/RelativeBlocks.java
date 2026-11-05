package com.lukeolafp.relativeblocks;

import com.lukeolafp.relativeblocks.blocks.RelativePlacement;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabs;
import com.lukeolafp.relativeblocks.init.BlockEntityInit;
import com.lukeolafp.relativeblocks.init.BlockInit;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Olaf's Relatively Placed Blocks (LukeolafP, CC0-1.0), portado de Fabric a Forge 1.20.1.
 *
 * <p>Lo que en Fabric era API de Fabric aquí es de Forge: el registro (RegisterEvent; las losas verticales de todas las
 * losas, al acabar el registro de bloques de todos los mods: {@code mixin/GameDataMixin}), los clics y las roturas
 * (PlayerInteractEvent.RightClickBlock y BlockEvent.BreakEvent, después de las protecciones) y el dibujo de los grupos
 * (ModelData en vez del Renderer de Fabric: {@code RelativeBlocksClient}).
 */
@Mod(RelativeBlocks.MOD_ID)
public class RelativeBlocks {
   public static final String MOD_ID = "relativeblocks";
   public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

   public RelativeBlocks() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      modBus.addListener(RelativeBlocks::register);
      modBus.addListener(VerticalSlabs::addToCreativeTabs);
      // Después de las protecciones y de los demás mods: si alguien canceló el clic o la rotura, aquí no se hace nada
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, RelativePlacement::onRightClickBlock);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, RelativePlacement::onBreak);
      MinecraftForge.EVENT_BUS.addListener(RelativePlacement::onTagsUpdated);
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RelativeBlocksClient.init(modBus));
      VerticalSlabs.ready();
      LOGGER.info("Relatively placed blocks ready");
   }

   private static void register(RegisterEvent event) {
      event.register(Registries.BLOCK, helper -> helper.register(id("block_cluster"), BlockInit.create()));
      event.register(Registries.BLOCK_ENTITY_TYPE, helper -> helper.register(id("block_cluster"), BlockEntityInit.create()));
      event.register(Registries.ITEM, VerticalSlabs::registerItems);
   }

   public static ResourceLocation id(String path) {
      return new ResourceLocation(MOD_ID, path);
   }
}
