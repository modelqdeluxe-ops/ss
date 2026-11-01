/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import net.tierrasfantasticas.tfclient.clans.ClanBankBlockEntity;

/**
 * (1.3.159, el dueño: «haz que el cofre brille en la oscuridad como las crates») El cofre del banco, dibujado como las
 * crates: su modelo (la crate de madera crate_lvl1, del blockstate con su giro) a plena luz, así que de noche o en una
 * cueva se ve igual de claro que de día.
 */
public class ClanBankRenderer implements BlockEntityRenderer<ClanBankBlockEntity> {
    public ClanBankRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    @SuppressWarnings("deprecation")
    public void render(ClanBankBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state, model,
                1F, 1F, 1F, LightTexture.FULL_BRIGHT, overlay);
    }

    @Override
    public boolean shouldRenderOffScreen(ClanBankBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
