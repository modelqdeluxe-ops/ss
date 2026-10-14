package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.tierrasfantasticas.tfclient.pad.TFPadScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluidez del pad (1.3.67): el pad pinta «en lote» (drawManaged: rectángulos y textos seguidos van juntos a la tarjeta
 * gráfica). Las texturas (blit) se pintan al momento, sin lote; para que no se pongan delante de lo que iba antes, en
 * modo lote se vacía el lote justo antes. Fuera del lote no cambia nada.
 * <p>
 * 1.3.70: al vaciar el lote, Minecraft apaga la mezcla (blend) al acabar, y la textura que venía detrás (nubes, rayos,
 * luces, con transparencia) salía opaca: lo transparente, blanco (la captura del dueño). Y lo del lote se pintaba con el
 * color y la transparencia ya puestos para esa textura. Ahora, en el pad, el lote se vacía en blanco y luego se deja todo
 * como estaba: color de la textura, mezcla encendida y la normal (el pad siempre pinta así).
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Shadow
    private boolean managed;

    @Inject(method = {"innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V",
            "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V"}, at = @At("HEAD"))
    private void tfclient$flushBeforeBlit(CallbackInfo ci) {
        if (!managed) return;
        GuiGraphics g = (GuiGraphics) (Object) this;
        if (!TFPadScreen.drawing()) {
            g.flush();
            return;
        }
        float[] c = RenderSystem.getShaderColor();
        float r = c[0], gr = c[1], b = c[2], a = c[3];
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        g.flush();
        RenderSystem.setShaderColor(r, gr, b, a);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
