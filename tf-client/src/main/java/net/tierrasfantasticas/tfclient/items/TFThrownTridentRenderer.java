/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * (1.3.171) La lanza o el tridente de un set, lanzado (TFThrownTrident): su propio modelo 3D (el del objeto), no el
 * tridente de Minecraft. Los modelos de los sets van a lo largo, con la punta arriba (+Y, como se llevan en la mano):
 * se gira como el tridente de Minecraft (hacia donde vuela) y se le da la vuelta para que la punta vaya delante; la
 * punta queda un poco por delante de donde está la entidad (donde se clava), a la escala que tiene en la mano.
 */
public class TFThrownTridentRenderer extends EntityRenderer<TFThrownTrident> {
    private final ItemRenderer items;
    /** Lo que ocupa el modelo de cada objeto (en bloques, 0 a 1 = un bloque): minX, minY, minZ, maxX, maxY, maxZ. */
    private static final Map<Item, float[]> BOUNDS = new HashMap<>();
    /** Para la prueba en el juego: cuántas lanzas se han dibujado con su modelo. */
    public static volatile int testDrawn;

    public TFThrownTridentRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(TFThrownTrident entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = entity.shownItem();
        if (!stack.isEmpty()) {
            BakedModel model = items.getModel(stack, entity.level(), null, entity.getId());
            float[] b = BOUNDS.computeIfAbsent(stack.getItem(), k -> bounds(model));
            float scale = model.getTransforms().thirdPersonRightHand.scale.y();
            if (scale <= 0.0F || scale > 4.0F) scale = 1.0F;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, entity.yRotO, entity.getYRot()) - 90.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partial, entity.xRotO, entity.getXRot()) + 90.0F));
            // la punta del modelo (+Y) hacia donde vuela (el tridente de Minecraft apunta con su -Y)
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            pose.scale(scale, scale, scale);
            float cx = (b[0] + b[3]) / 2.0F, cz = (b[2] + b[5]) / 2.0F;
            pose.translate(0.5F - cx, 0.5F - b[4] + 0.15F, 0.5F - cz);
            items.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
            testDrawn++;
        }
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    /** Lo que ocupa el modelo (de sus vértices); un bloque si no tiene caras (un modelo especial). */
    private static float[] bounds(BakedModel model) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        int stride = DefaultVertexFormat.BLOCK.getIntegerSize();
        RandomSource random = RandomSource.create(42L);
        boolean any = false;
        for (int d = -1; d < Direction.values().length; d++) {
            Direction side = d < 0 ? null : Direction.values()[d];
            for (BakedQuad q : model.getQuads(null, side, random)) {
                int[] v = q.getVertices();
                for (int i = 0; i + 2 < v.length; i += stride) {
                    float x = Float.intBitsToFloat(v[i]), y = Float.intBitsToFloat(v[i + 1]), z = Float.intBitsToFloat(v[i + 2]);
                    b[0] = Math.min(b[0], x);
                    b[1] = Math.min(b[1], y);
                    b[2] = Math.min(b[2], z);
                    b[3] = Math.max(b[3], x);
                    b[4] = Math.max(b[4], y);
                    b[5] = Math.max(b[5], z);
                    any = true;
                }
            }
        }
        return any ? b : new float[] {0F, 0F, 0F, 1F, 1F, 1F};
    }

    /** Cuando se recargan los recursos (F3+T), se vuelven a medir. */
    public static void clearBounds() {
        BOUNDS.clear();
    }

    @Override
    public ResourceLocation getTextureLocation(TFThrownTrident entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
