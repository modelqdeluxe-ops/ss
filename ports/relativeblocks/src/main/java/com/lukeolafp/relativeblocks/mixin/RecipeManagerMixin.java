package com.lukeolafp.relativeblocks.mixin;

import com.google.gson.JsonElement;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabs;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Las recetas de las losas verticales se añaden a las que se cargan (antes de que Forge mire sus condiciones). */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
   @Inject(
      method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
      at = @At("HEAD")
   )
   private void relativeblocks$addVerticalSlabs(Map<ResourceLocation, JsonElement> recipes, ResourceManager manager, ProfilerFiller profiler, CallbackInfo info) {
      try {
         VerticalSlabs.addRecipes(recipes);
      } catch (RuntimeException e) {
         // una receta rara de otro mod no debe impedir que carguen las demás
         com.lukeolafp.relativeblocks.RelativeBlocks.LOGGER.warn("Could not work out vertical slab recipes: {}", e.toString());
      }
   }
}
