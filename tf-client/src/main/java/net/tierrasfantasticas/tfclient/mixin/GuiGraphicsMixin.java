/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
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
 * 1.3.106: cada cosa que pinta el pad (recuadros, degradados, texturas y texto) va un poco más adelante que la anterior
 * (TFPadScreen.nextOrder): la profundidad respeta el orden aunque algo del juego cambie el orden en que se pintan. En
 * el del dueño, el texto sobre recuadros de color no salía (sobre una textura, sí: lo que escribe en Música, las
 * etiquetas): algo pinta los recuadros después del texto (lo reproduce la prueba en el juego, TFPadScreen.testBoxesLast).
 * <p>
 * 1.3.109: pero lo translúcido (brillos, sombras, velos, texturas con transparencia) que va después del texto, si se
 * pinta antes que él, lo tapaba con su profundidad aunque no se viera (al dueño se le borraba el título «La zona» de
 * Protecciones bajo el brillo del icono de la fila siguiente). Lo translúcido ya no escribe la profundidad: los recuadros
 * con alfa van en PadRenderTypes.SOFT y las texturas con alfa, con depthMask(false). Lo opaco sí (tapa lo de debajo).
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Shadow
    private boolean managed;

    /** Cuántas cosas del pad van ahora mismo con su escalón puesto (tfclient$lift / tfclient$unlift). */
    @Unique
    private int tfclient$lifted;

    /** (1.3.109) ¿Apagó la escritura de la profundidad para esta textura (tfclient$flushBeforeBlit)? */
    @Unique
    private int tfclient$noDepth;

    @Unique
    private void tfclient$lift() {
        if (!managed || !TFPadScreen.liftOn()) return;
        GuiGraphics g = (GuiGraphics) (Object) this;
        g.pose().pushPose();
        g.pose().translate(0F, 0F, TFPadScreen.nextOrder());
        tfclient$lifted++;
    }

    @Unique
    private void tfclient$unlift() {
        if (tfclient$lifted == 0) return;
        tfclient$lifted--;
        ((GuiGraphics) (Object) this).pose().popPose();
    }

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
        tfclient$lift();
        // (1.3.109) una textura con transparencia (luces, rayos, nubes) no tapa con su profundidad lo de debajo
        if (TFPadScreen.soft(Math.round(RenderSystem.getShaderColor()[3] * 255F))) {
            RenderSystem.depthMask(false);
            tfclient$noDepth++;
        }
    }

    @Inject(method = {"innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V",
            "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V"}, at = @At("RETURN"))
    private void tfclient$afterBlit(CallbackInfo ci) {
        if (tfclient$noDepth > 0) {
            tfclient$noDepth--;
            RenderSystem.depthMask(true);
        }
        tfclient$unlift();
    }

    /** Con shaders, el brillo de encantamiento mezclado como debe (1.3.114: si no, siluetas moradas; ver ShaderPacks). */
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At("HEAD"))
    private void tfclient$glintBlend(CallbackInfo ci) {
        net.tierrasfantasticas.tfclient.vacio.ShaderPacks.fixBlendForGui();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    private void tfclient$textOrder(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (managed && text != null && TFPadScreen.deferText()) {
            tfclient$deferText(font, text, x, y, color, shadow);
            cir.setReturnValue(x + font.width(text) + (shadow ? 1 : 0));
            return;
        }
        tfclient$lift();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"), cancellable = true)
    private void tfclient$textOrderSeq(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (managed && TFPadScreen.deferText()) {
            tfclient$deferText(font, text, x, y, color, shadow);
            cir.setReturnValue(x + font.width(text) + (shadow ? 1 : 0));
            return;
        }
        tfclient$lift();
    }

    /** (1.3.109) La prueba en el juego (TFPadScreen.testTextLast): el texto, con su escalón, para pintarlo al final. */
    @Unique
    private void tfclient$deferText(Font font, Object text, int x, int y, int color, boolean shadow) {
        GuiGraphics g = (GuiGraphics) (Object) this;
        Matrix4f m = new Matrix4f(g.pose().last().pose());
        if (TFPadScreen.liftOn()) m.translate(0F, 0F, TFPadScreen.nextOrder());
        TFPadScreen.testTexts.add(new Object[] {m, font, text, new int[] {x, y, color, shadow ? 1 : 0}});
    }

    @Inject(method = {"drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"}, at = @At("RETURN"))
    private void tfclient$textOrderEnd(CallbackInfoReturnable<Integer> cir) {
        tfclient$unlift();
    }

    /** Su escalón y, en la prueba en el juego (TFPadScreen.testBoxesLast), se guarda para pintarlo al final. */
    @Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void tfclient$boxOrder(RenderType type, int x1, int y1, int x2, int y2, int z, int color, CallbackInfo ci) {
        // (1.3.109) un recuadro con alfa, en la capa translúcida sin profundidad (vuelve a entrar aquí con ella)
        if (managed && type == RenderType.gui() && TFPadScreen.soft(color >>> 24)) {
            ((GuiGraphics) (Object) this).fill(TFPadScreen.softType(), x1, y1, x2, y2, z, color);
            ci.cancel();
            return;
        }
        if (managed && TFPadScreen.deferBox()) {
            GuiGraphics g = (GuiGraphics) (Object) this;
            Matrix4f m = new Matrix4f(g.pose().last().pose());
            if (TFPadScreen.liftOn()) m.translate(0F, 0F, TFPadScreen.nextOrder());
            TFPadScreen.testBoxes.add(new Object[] {m, type, new int[] {0, x1, y1, x2, y2, z, color, 0}});
            ci.cancel();
            return;
        }
        tfclient$lift();
    }

    @Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("RETURN"))
    private void tfclient$boxOrderEnd(CallbackInfo ci) {
        tfclient$unlift();
    }

    @Inject(method = "fillGradient(Lnet/minecraft/client/renderer/RenderType;IIIIIII)V", at = @At("HEAD"), cancellable = true)
    private void tfclient$gradientOrder(RenderType type, int x1, int y1, int x2, int y2, int from, int to, int z, CallbackInfo ci) {
        if (managed && type == RenderType.gui() && TFPadScreen.soft(Math.min(from >>> 24, to >>> 24))) {
            ((GuiGraphics) (Object) this).fillGradient(TFPadScreen.softType(), x1, y1, x2, y2, from, to, z);
            ci.cancel();
            return;
        }
        if (managed && TFPadScreen.deferBox()) {
            GuiGraphics g = (GuiGraphics) (Object) this;
            Matrix4f m = new Matrix4f(g.pose().last().pose());
            if (TFPadScreen.liftOn()) m.translate(0F, 0F, TFPadScreen.nextOrder());
            TFPadScreen.testBoxes.add(new Object[] {m, type, new int[] {1, x1, y1, x2, y2, z, from, to}});
            ci.cancel();
            return;
        }
        tfclient$lift();
    }

    @Inject(method = "fillGradient(Lnet/minecraft/client/renderer/RenderType;IIIIIII)V", at = @At("RETURN"))
    private void tfclient$gradientOrderEnd(CallbackInfo ci) {
        tfclient$unlift();
    }
}
