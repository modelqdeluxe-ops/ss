package com.lukeolafp.relativeblocks.mixin.client;

import com.lukeolafp.relativeblocks.blocks.ClusterParticleOffset;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Las partículas de un bloque de un grupo salen donde está ese bloque (no en el centro del hueco). */
@Mixin(ClientLevel.class)
public abstract class ClientWorldParticleOffsetMixin {
   @ModifyVariable(
      method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private double relativeblocks$offsetParticleX(double x) {
      return ClusterParticleOffset.isActive() ? x + ClusterParticleOffset.x() : x;
   }

   @ModifyVariable(
      method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
      at = @At("HEAD"),
      ordinal = 2,
      argsOnly = true
   )
   private double relativeblocks$offsetParticleZ(double z) {
      return ClusterParticleOffset.isActive() ? z + ClusterParticleOffset.z() : z;
   }
}
