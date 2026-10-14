package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.skills.SkillClient;

/**
 * Dibuja un mob de jefe: su modelo de ModelEngine (el que le pusieron sus skills, con sus animaciones a capas, piezas
 * escondidas o cambiadas y brillos) con la luz de su sitio. La entidad en sí no se ve. Sin nombre encima: los jefes
 * llevan su barra.
 */
@OnlyIn(Dist.CLIENT)
public class BossRenderer extends EntityRenderer<BossEntity> {
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");

    public BossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(BossEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        // La sombra de ModelEngine: la del hueso «shadow» del modelo (sin él, ninguna; model{showshadow=false}, tampoco)
        this.shadowRadius = SkillClient.shadowRadius(e);
        SkillClient.renderFollower(e, partial, pose, buffers, light);
    }

    @Override
    protected boolean shouldShowName(BossEntity e) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(BossEntity e) {
        return NONE;
    }
}
