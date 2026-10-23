/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.tierrasfantasticas.tfclient.client.TFButtonTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Deslizadores (volumen, FOV...) con el tema de TF fuera de una partida. */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
    @Shadow
    protected double value;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void tfclient$themeSlider(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (TFButtonTheme.renderSlider((AbstractSliderButton) (Object) this, this.value, g)) ci.cancel();
    }
}
