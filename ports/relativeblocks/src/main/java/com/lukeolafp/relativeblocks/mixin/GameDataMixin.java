package com.lukeolafp.relativeblocks.mixin;

import com.lukeolafp.relativeblocks.blocks.VerticalSlabs;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Las losas verticales se crean cuando TODOS los mods han registrado sus bloques: en el bucle de los registros de
 * GameData.postRegisterEvents, justo antes de congelar cada registro. Solo hace algo la vez en que el registro de bloques
 * sigue abierto (el de bloques), una vez. Es una clase de Forge (también en Mohist), sin nombres de Minecraft.
 */
@Mixin(value = GameData.class, remap = false)
public abstract class GameDataMixin {
   @Inject(
      method = "postRegisterEvents",
      at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/ForgeRegistry;freeze()V", remap = false),
      remap = false
   )
   private static void relativeblocks$addVerticalSlabs(CallbackInfo info) {
      if (ForgeRegistries.BLOCKS instanceof ForgeRegistry<Block> blocks && !blocks.isLocked()) {
         VerticalSlabs.registerBlocks();
      }
   }
}
