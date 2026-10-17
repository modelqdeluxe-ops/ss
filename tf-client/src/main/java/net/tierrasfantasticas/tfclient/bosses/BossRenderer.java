package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.skills.SkillClient;
import org.joml.Matrix4f;

/**
 * Dibuja un mob de jefe: su modelo de ModelEngine (el que le pusieron sus skills, con sus animaciones a capas, piezas
 * escondidas o cambiadas y brillos) con la luz de su sitio. La entidad en sí no se ve. Sin nombre encima: los jefes
 * llevan su barra. Al morir el jefe que acaba la pelea, los rayos de luz de su muerte épica (como el dragón del End).
 */
@OnlyIn(Dist.CLIENT)
public class BossRenderer extends EntityRenderer<BossEntity> {
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0) / 2.0);

    public BossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(BossEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        // La sombra de ModelEngine: la del hueso «shadow» del modelo (sin él, ninguna; model{showshadow=false}, tampoco)
        this.shadowRadius = SkillClient.shadowRadius(e);
        SkillClient.renderFollower(e, partial, pose, buffers, light);
        rays(e, partial, pose, buffers);
    }

    /**
     * Los rayos de la muerte épica, con las cuentas de EnderDragonRenderer: cada vez más (hasta 60) y más largos, girando,
     * blancos en el centro y del color del jefe hacia fuera; en el último 20 % crecen y se apagan. Su tamaño va con el del
     * jefe (el dragón mide unos 8 bloques).
     */
    private static void rays(BossEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
        int len = e.epicDeath();
        if (len <= 0 || e.deathTime <= 0) return;
        float f5 = Math.min(1F, (e.deathTime + partial) / len);
        float f7 = Math.min(f5 > 0.8F ? (f5 - 0.8F) / 0.2F : 0F, 1F);
        // (1.3.87: la mitad de rayos y un tercio de largo: el dueño los veía «exagerados de grandes»)
        float size = Math.max(0.4F, Math.min(0.9F, Math.max(e.getBbWidth(), e.getBbHeight()) / 8F));
        int color = e.color();
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        RandomSource rnd = RandomSource.create(432L);
        VertexConsumer vc = buffers.getBuffer(BossRenderTypes.RAYS);
        pose.pushPose();
        pose.translate(0F, e.getBbHeight() * 0.5F, 0F);
        int n = (int) ((f5 + f5 * f5) / 2F * 30F);
        for (int i = 0; i < n; i++) {
            pose.mulPose(Axis.XP.rotationDegrees(rnd.nextFloat() * 360F));
            pose.mulPose(Axis.YP.rotationDegrees(rnd.nextFloat() * 360F));
            pose.mulPose(Axis.ZP.rotationDegrees(rnd.nextFloat() * 360F));
            pose.mulPose(Axis.XP.rotationDegrees(rnd.nextFloat() * 360F));
            pose.mulPose(Axis.YP.rotationDegrees(rnd.nextFloat() * 360F));
            pose.mulPose(Axis.ZP.rotationDegrees(rnd.nextFloat() * 360F + f5 * 90F));
            float l = (rnd.nextFloat() * 7F + 2.5F + f7 * 3F) * size;
            float w = (rnd.nextFloat() * 0.8F + 0.4F + f7 * 0.6F) * size;
            Matrix4f m = pose.last().pose();
            int a = (int) (255F * (1F - f7));
            // Las tres caras del rayo (la capa va por cuadriláteros: cada triángulo, con el centro repetido)
            face(vc, m, a, -HALF_SQRT_3 * w, l, -0.5F * w, HALF_SQRT_3 * w, l, -0.5F * w, r, g, b);
            face(vc, m, a, HALF_SQRT_3 * w, l, -0.5F * w, 0F, l, w, r, g, b);
            face(vc, m, a, 0F, l, w, -HALF_SQRT_3 * w, l, -0.5F * w, r, g, b);
        }
        pose.popPose();
    }

    private static void face(VertexConsumer vc, Matrix4f m, int alpha, float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b) {
        vc.vertex(m, 0F, 0F, 0F).color(255, 255, 255, alpha).endVertex();
        vc.vertex(m, 0F, 0F, 0F).color(255, 255, 255, alpha).endVertex();
        vc.vertex(m, x1, y1, z1).color(r, g, b, 0).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, 0).endVertex();
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
