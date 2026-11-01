/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (1.3.159) La etiqueta del clan animada (arcoíris u onda) también en la lista de jugadores (TAB): se le ponen sus
 * colores de este instante al nombre que ya trae (el del plugin TAB o el del servidor). Sin efecto animado, igual.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayClanMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void tfclient$clanTag(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (info == null || info.getProfile() == null) return;
        cir.setReturnValue(net.tierrasfantasticas.tfclient.clans.client.ClanClient.animateTag(cir.getReturnValue(), info.getProfile().getId()));
    }
}
