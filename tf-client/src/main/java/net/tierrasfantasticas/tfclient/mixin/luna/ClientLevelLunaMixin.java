/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.luna;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.luna.client.BloodMoonClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (1.3.175) Con la Luna de Sangre, el cielo y las nubes de Minecraft se tiñen de rojo. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelLunaMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void tfclient$bloodMoonSky(Vec3 pos, float partial, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(BloodMoonClient.tintSky(cir.getReturnValue(), partial));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void tfclient$bloodMoonClouds(float partial, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(BloodMoonClient.tintClouds(cir.getReturnValue(), partial));
    }
}
