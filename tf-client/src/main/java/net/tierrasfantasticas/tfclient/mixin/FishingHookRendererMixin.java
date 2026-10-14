package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.tierrasfantasticas.tfclient.anim.AnimSettings;
import net.tierrasfantasticas.tfclient.anim.PunchyBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Punchy apagado = Minecraft normal (1.3.71): en primera persona, Punchy hace salir el sedal de la mano de su modelo
 * (cambia la llamada a NearPlane.getPointOnPlane, sin mirar si está encendido). Ese punto se guarda en el único Vec3 de
 * render; con Punchy apagado se vuelve a poner el de Minecraft: el mismo cálculo (lado de la caña, 0,525 y −0,1).
 */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 0)
    private Vec3 tfclient$sedalNormal(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.getEntityRenderDispatcher().camera == null) return point;
        boolean main = p.getMainHandItem().canPerformAction(ToolActions.FISHING_ROD_CAST);
        // Punchy apagado, o la caña apagada en Efectos › ANIMACIONES: el sedal de siempre
        if (!PunchyBridge.vanilla() && !AnimSettings.blocked(main ? p.getMainHandItem() : p.getOffhandItem())) return point;
        int side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (!main) side = -side;
        return mc.getEntityRenderDispatcher().camera.getNearPlane().getPointOnPlane(side * 0.525F, -0.1F);
    }
}
