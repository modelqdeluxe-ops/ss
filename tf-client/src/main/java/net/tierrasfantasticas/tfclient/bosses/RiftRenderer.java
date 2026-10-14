package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;
import org.joml.Matrix4f;

/**
 * La grieta: el cristal (del color de su jefe, que brilla aunque sea de noche) con sus animaciones según cómo esté
 * (flotando y girando; temblando y agrietándose al despertar; apagado durante la incursión y la recarga; creciendo otra
 * vez) y, encima, el nombre del jefe, su dificultad y qué hacer o cuánto falta.
 */
@OnlyIn(Dist.CLIENT)
public class RiftRenderer extends EntityRenderer<RiftEntity> {
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    static final String MODEL = "grieta.cristal";

    public RiftRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.2F;
    }

    /** Pone las animaciones de la grieta según su estado (al cambiar). */
    private static void sync(RiftEntity e, VfxModel m) {
        int st = e.state();
        if (st == e.shownState) return;
        int before = e.shownState;
        e.shownState = st;
        float now = e.clientAge;
        e.layers.clear();
        switch (st) {
            case RiftEntity.WAKING -> e.layers.play(m, "activate", now - (RiftEntity.WAKE_TICKS - e.left()), 1F, 3, 0, 5, true);
            case RiftEntity.FIGHT, RiftEntity.COOLDOWN -> e.layers.play(m, "dormant", now, 1F, before == RiftEntity.WAKING ? 0 : 0, 6, 10, true);
            default -> {
                // Vuelve a estar lista: crece otra vez (si ya estaba lista al llegar, nada)
                if (before == RiftEntity.COOLDOWN || before == RiftEntity.FIGHT) e.layers.play(m, "regrow", now, 1F, 0, 4, 6, true);
            }
        }
    }

    @Override
    public void render(RiftEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        VfxModel m = SkillMobs.model(MODEL);
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(e.boss());
        int color = cls != null && cls.bossInfo != null ? cls.bossInfo.color : 0xB45CF0;
        if (m != null) {
            sync(e, m);
            e.layers.prune(e.clientAge);
            Matrix4f[] mats = new Matrix4f[m.bones.length];
            for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
            e.layers.pose(m, e.clientAge + partial, 0F, 0F, mats);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180F));
            ModelDraw.draw(m, mats, null, pose, buffers, light, color, e.clientAge, 1F);
            pose.popPose();
        }
        if (cls != null && cls.bossInfo != null) label(e, cls.bossInfo, pose, buffers);
    }

    /** El cartel de encima: jefe, dificultad y estado. */
    private void label(RiftEntity e, SkillDefs.BossInfo bi, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(e) > 40 * 40) return;
        List<Component> lines = new ArrayList<>();
        int st = e.state();
        if (st != RiftEntity.FIGHT) {
            lines.add(Component.literal(bi.name.toUpperCase(java.util.Locale.ROOT)).withStyle(s -> s.withColor(TextColor.fromRgb(bi.color)).withBold(true)));
            lines.add(Component.literal(e.label()).withStyle(s -> s.withColor(TextColor.fromRgb(e.labelColor()))));
        }
        String state = switch (st) {
            case RiftEntity.READY -> "Golpea el cristal para despertarlo";
            case RiftEntity.WAKING -> "¡Despierta!";
            case RiftEntity.FIGHT -> "Incursión en curso";
            default -> "Vuelve en " + Rifts.time(e.left());
        };
        lines.add(Component.literal(state).withStyle(s -> s.withColor(TextColor.fromRgb(st == RiftEntity.WAKING ? 0xFF6A6A : 0xD8D8E0))));
        Font font = mc.font;
        pose.pushPose();
        pose.translate(0, st == RiftEntity.FIGHT || st == RiftEntity.COOLDOWN ? 2.2 : 5.6, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.scale(-0.028F, -0.028F, 0.028F);
        Matrix4f mat = pose.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.35F) * 255F) << 24;
        float y = -lines.size() * 11;
        for (Component c : lines) {
            float x = -font.width(c) / 2F;
            font.drawInBatch(c, x, y, 0xFFFFFFFF, false, mat, buffers, Font.DisplayMode.SEE_THROUGH, bg, LightTexture.FULL_BRIGHT);
            font.drawInBatch(c, x, y, 0xFFFFFFFF, false, mat, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            y += 11;
        }
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(RiftEntity e) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(RiftEntity e) {
        return NONE;
    }
}
