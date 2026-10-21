package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.tierrasfantasticas.tfclient.pad.TFPadScreen;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fluidez del pad (1.3.67): el pad pinta «en lote» (drawManaged: rectángulos y textos seguidos van juntos a la tarjeta
 * gráfica). Las texturas (blit) se pintan al momento, sin lote; para que no se pongan delante de lo que iba antes, en
 * modo lote se vacía el lote justo antes. Fuera del lote no cambia nada.
 * <p>
 * 1.3.70: al vaciar el lote, Minecraft apaga la mezcla (blend) al acabar, y la textura que venía detrás (nubes, rayos,
 * luces, con transparencia) salía opaca: lo transparente, blanco (la captura del dueño). Y lo del lote se pintaba con el
 * color y la transparencia ya puestos para esa textura. Ahora, en el pad, el lote se vacía en blanco y luego se deja todo
 * como estaba: color de la textura, mezcla encendida y la normal (el pad siempre pinta así).
 * <p>
 * 1.3.106: lo mismo antes de cada texto del pad, que además va 1 por delante (tfclient$textOnTop).
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Shadow
    private boolean managed;

    /** Cuántos textos del pad están subidos 1 ahora mismo (tfclient$textOnTop). */
    @Unique
    private int tfclient$lifted;

    @Inject(method = {"innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V",
            "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V"}, at = @At("HEAD"))
    private void tfclient$flushBeforeBlit(CallbackInfo ci) {
        if (!managed) return;
        GuiGraphics g = (GuiGraphics) (Object) this;
        if (!TFPadScreen.drawing()) {
            g.flush();
            return;
        }
        TFPadScreen.flushBatch(g);
    }

    /**
     * (1.3.106) El texto del pad (el de Minecraft: lo que se escribe en Música, las etiquetas, los títulos), como la
     * letra pixel (PadFont, 1.3.103): primero se pinta lo que había debajo (el lote) y el texto va 1 por delante. En el
     * juego del dueño, el texto sobre recuadros de color no salía (sobre una textura, sí): algo de su juego pinta los
     * recuadros después del texto. Así, aunque los pinte después, el texto queda delante.
     */
    @Inject(method = {"drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"}, at = @At("HEAD"))
    private void tfclient$textOnTop(CallbackInfoReturnable<Integer> cir) {
        if (!managed || !TFPadScreen.drawing() || TFPadScreen.testNoLift) return;
        GuiGraphics g = (GuiGraphics) (Object) this;
        TFPadScreen.flushBatch(g);
        g.pose().pushPose();
        g.pose().translate(0F, 0F, 1F);
        tfclient$lifted++;
    }

    /** La prueba en el juego (TFPadScreen.testBoxesLast): los recuadros del pad, al final, después del texto. */
    @Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void tfclient$boxLast(RenderType type, int x1, int y1, int x2, int y2, int z, int color, CallbackInfo ci) {
        if (!managed || !TFPadScreen.deferBox()) return;
        GuiGraphics g = (GuiGraphics) (Object) this;
        TFPadScreen.testBoxes.add(new Object[] {new Matrix4f(g.pose().last().pose()), type, new int[] {0, x1, y1, x2, y2, z, color, 0}});
        ci.cancel();
    }

    @Inject(method = "fillGradient(Lnet/minecraft/client/renderer/RenderType;IIIIIII)V", at = @At("HEAD"), cancellable = true)
    private void tfclient$gradientLast(RenderType type, int x1, int y1, int x2, int y2, int from, int to, int z, CallbackInfo ci) {
        if (!managed || !TFPadScreen.deferBox()) return;
        GuiGraphics g = (GuiGraphics) (Object) this;
        TFPadScreen.testBoxes.add(new Object[] {new Matrix4f(g.pose().last().pose()), type, new int[] {1, x1, y1, x2, y2, z, from, to}});
        ci.cancel();
    }

    @Inject(method = {"drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"}, at = @At("RETURN"))
    private void tfclient$textOnTopEnd(CallbackInfoReturnable<Integer> cir) {
        if (tfclient$lifted == 0) return;
        tfclient$lifted--;
        ((GuiGraphics) (Object) this).pose().popPose();
    }
}
