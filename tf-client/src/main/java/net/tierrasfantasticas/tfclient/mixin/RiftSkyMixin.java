/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.bosses.RiftSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class RiftSkyMixin {
    /** Cerca de una grieta, el color del cielo de Minecraft va hacia el del vacío (RiftSky dibuja las nebulosas encima). */
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void tfclient$riftSkyColor(Vec3 pos, float partial, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(RiftSky.tint(cir.getReturnValue(), RiftSky.FOG, partial));
    }

    /** ...y las nubes, a un morado oscuro (blancas desentonaban sobre las nebulosas). */
    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void tfclient$riftCloudColor(float partial, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(RiftSky.tint(cir.getReturnValue(), RiftSky.CLOUDS, partial));
    }
}
