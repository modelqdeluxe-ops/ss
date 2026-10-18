package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.bosses.ModelDraw;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;
import org.joml.Matrix4f;

/**
 * Caelthar, el Caballero Sacro (1.3.88 «Aurelion, caballero de luz»; el dueño, en la 1.3.89: «ponle otro nombre épico no
 * conocido, y ponle Caballero Sacro»): su modelo de ModelEngine (vacio.caballero, del .bbmodel «skywars_npc») con su
 * animación de reposo («reposo»: la parte tranquila, 0-9 s, del «idle» del modelo; el resto era una reverencia con un
 * aleteo de −94° en el que las alas le atravesaban el pecho: «se buguean las alas, le traspasan al pecho»), un 40 % más
 * grande, a luz completa (es de luz: se ve igual de noche y en el vacío), la cabeza mirando a quien
 * tiene cerca y, encima, su nombre y «clic derecho para hablar» cuando estás cerca. Sin las islitas flotantes del
 * modelo original (eran del Skywars).
 */
@OnlyIn(Dist.CLIENT)
public class KnightRenderer extends EntityRenderer<KnightEntity> {
    static final String MODEL = "vacio.caballero";
    static final float SCALE = 1.4F;
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private boolean[] hidden;
    /** Lo está dibujando el retrato del diálogo (sin cartel encima). */
    static boolean inGui;

    public KnightRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.7F;
    }

    @Override
    public void render(KnightEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        VfxModel m = SkillMobs.model(MODEL);
        if (m != null) {
            if (hidden == null || hidden.length != m.bones.length) {
                hidden = new boolean[m.bones.length];
                for (String b : new String[] {"left_island", "right_island", "name"}) {
                    int i = m.bone(b);
                    if (i >= 0) hidden[i] = true;
                }
            }
            if (e.shown != 1) {
                e.layers.clear();
                e.layers.play(m, m.anims.containsKey("reposo") ? "reposo" : "idle", e.clientAge, 1F, 0, 0, 1, true);
                e.shown = 1;
            }
            e.layers.prune(e.clientAge);
            Matrix4f[] mats = new Matrix4f[m.bones.length];
            for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
            float body = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
            float headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - body);
            float headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
            e.layers.pose(m, e.clientAge + partial, Mth.clamp(headYaw, -60F, 60F), headPitch, mats);
            pose.pushPose();
            // Flota un poco (las alas)
            if (!inGui) pose.translate(0, 0.06 * Mth.sin((e.clientAge + partial) * 0.07F), 0);
            pose.mulPose(Axis.YP.rotationDegrees(180F - body));
            pose.scale(SCALE, SCALE, SCALE);
            ModelDraw.draw(m, mats, hidden, pose, buffers, LightTexture.FULL_BRIGHT, 0xFFFFFF, e.clientAge, 1F);
            pose.popPose();
        }
        if (!inGui) label(e, pose, buffers);
    }

    private void label(KnightEntity e, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d2 = mc.player.distanceToSqr(e);
        if (d2 > 24 * 24) return;
        Component name = Component.literal(KnightEntity.NAME).withStyle(s -> s.withColor(TextColor.fromRgb(0xFFD66B)).withBold(true));
        Component title = Component.literal(e.home() ? "Caballero Sacro · Guardián del santuario" : "Caballero Sacro")
                .withStyle(s -> s.withColor(TextColor.fromRgb(0xE9DDFF)));
        Component hint = Component.literal("✦ Clic derecho para hablar ✦").withStyle(s -> s.withColor(TextColor.fromRgb(0xB79CFF)));
        Font font = mc.font;
        pose.pushPose();
        pose.translate(0, e.getBbHeight() + 0.75, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f mat = pose.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.3F) * 255F) << 24;
        Component[] lines = d2 < 8 * 8 ? new Component[] {name, title, hint} : new Component[] {name, title};
        float y = -lines.length * 11;
        for (Component c : lines) {
            float x = -font.width(c) / 2F;
            font.drawInBatch(c, x, y, 0xFFFFFFFF, false, mat, buffers, Font.DisplayMode.SEE_THROUGH, bg, LightTexture.FULL_BRIGHT);
            font.drawInBatch(c, x, y, 0xFFFFFFFF, false, mat, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            y += 11;
        }
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(KnightEntity e) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(KnightEntity e) {
        return NONE;
    }
}
