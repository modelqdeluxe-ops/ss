/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.luna.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los ojos rojos que brillan de los mobs durante la Luna de Sangre (el dueño: «quiero mobs con ojos rojos brillantes»).
 * Como los ojos de las arañas de Minecraft ({@code RenderType.eyes}: suman luz, se ven en la oscuridad y, con Oculus, son
 * emisivos): se vuelve a dibujar el modelo del mob con una textura que solo tiene sus ojos en rojo y un halo tenue
 * ({@code tools/luna/texturas_luna.py}). Su luz sube poco a poco al acabar la escena ({@link BloodMoonClient#eyesOf}).
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BloodEyesLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /** Para la prueba en el juego: veces que se pintaron ojos. */
    public static int rendered;
    private final RenderType type;

    public BloodEyesLayer(RenderLayerParent<T, M> parent, ResourceLocation texture) {
        super(parent);
        this.type = RenderType.eyes(texture);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbAmount, float partial,
            float age, float headYaw, float headPitch) {
        if (BloodMoonScene.suppressEyes) return;
        float k = BloodMoonClient.eyesOf(entity, partial);
        if (k <= 0.01F) return;
        if (entity.isInvisible() && !BloodMoonScene.isCast(entity)) return;
        getParentModel().renderToBuffer(pose, buffers.getBuffer(type), 0xF00000, OverlayTexture.NO_OVERLAY, k, k, k, 1F);
        rendered++;
    }

    private static ResourceLocation eyes(String name) {
        return new ResourceLocation(TFClient.MOD_ID, "textures/entity/luna_ojos/" + name + ".png");
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        ResourceLocation humanoid = eyes("humanoide"), skeleton = eyes("esqueleto"), creeper = eyes("creeper"),
                villager = eyes("aldeano"), witch = eyes("bruja");
        add(event, EntityType.ZOMBIE, humanoid);
        add(event, EntityType.HUSK, humanoid);
        add(event, EntityType.DROWNED, humanoid);
        add(event, EntityType.SKELETON, skeleton);
        add(event, EntityType.STRAY, skeleton);
        add(event, EntityType.WITHER_SKELETON, skeleton);
        add(event, EntityType.CREEPER, creeper);
        add(event, EntityType.ZOMBIE_VILLAGER, villager);
        add(event, EntityType.PILLAGER, villager);
        add(event, EntityType.VINDICATOR, villager);
        add(event, EntityType.EVOKER, villager);
        add(event, EntityType.WITCH, witch);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void add(EntityRenderersEvent.AddLayers event, EntityType<? extends LivingEntity> type, ResourceLocation texture) {
        try {
            LivingEntityRenderer renderer = (LivingEntityRenderer) event.getRenderer((EntityType) type);
            if (renderer != null) renderer.addLayer(new BloodEyesLayer(renderer, texture));
        } catch (Throwable t) {
            TFClient.LOGGER.warn("TF Luna de Sangre: no se pudieron poner los ojos rojos a {}", type, t);
        }
    }
}
