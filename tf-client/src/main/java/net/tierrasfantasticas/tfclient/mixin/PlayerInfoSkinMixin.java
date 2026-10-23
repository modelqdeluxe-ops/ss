/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.skins.SkinClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skins del Armario: la cara de la lista de jugadores (Tab) y el modelo salen de la skin elegida en el pad. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinLocation", at = @At("HEAD"), cancellable = true)
    private void tfclient$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation loc = SkinClient.texture(getProfile().getId());
        if (loc != null) cir.setReturnValue(loc);
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void tfclient$model(CallbackInfoReturnable<String> cir) {
        String model = SkinClient.model(getProfile().getId());
        if (model != null) cir.setReturnValue(model);
    }
}
