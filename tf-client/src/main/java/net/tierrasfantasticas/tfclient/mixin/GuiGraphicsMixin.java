package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluidez del pad (1.3.67): el pad pinta «en lote» (drawManaged: rectángulos y textos seguidos van juntos a la tarjeta
 * gráfica). Las texturas (blit) se pintan al momento, sin lote; para que no se pongan delante de lo que iba antes, en
 * modo lote se vacía el lote justo antes. Fuera del lote no cambia nada.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Shadow
    private boolean managed;

    @Inject(method = {"innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V",
            "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V"}, at = @At("HEAD"))
    private void tfclient$flushBeforeBlit(CallbackInfo ci) {
        if (managed) ((GuiGraphics) (Object) this).flush();
    }
}
