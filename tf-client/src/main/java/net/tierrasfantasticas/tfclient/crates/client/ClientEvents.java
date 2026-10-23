/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client;

import net.tierrasfantasticas.tfclient.crates.client.render.CrateBakedModels;
import net.tierrasfantasticas.tfclient.crates.client.render.CrateModel;
import net.tierrasfantasticas.tfclient.crates.client.render.CrateRenderer;
import net.tierrasfantasticas.tfclient.crates.registry.ModRegistry;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.client.event.ModelEvent.RegisterAdditional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "tfclient",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class ClientEvents {
   private ClientEvents() {
   }

   @SubscribeEvent
   public static void registerLayers(RegisterLayerDefinitions event) {
      if (!net.tierrasfantasticas.tfclient.crates.FSCrates.enabled()) return;
      event.registerLayerDefinition(CrateModel.LAYER, CrateModel::createLayer);
   }

   @SubscribeEvent
   public static void registerAdditionalModels(RegisterAdditional event) {
      if (!net.tierrasfantasticas.tfclient.crates.FSCrates.enabled()) return;
      CrateBakedModels.registerAll(event);
   }

   @SubscribeEvent
   public static void registerRenderers(RegisterRenderers event) {
      if (!net.tierrasfantasticas.tfclient.crates.FSCrates.enabled()) return;
      event.registerBlockEntityRenderer((BlockEntityType)ModRegistry.CRATE_BE.get(), CrateRenderer::new);
   }
}
