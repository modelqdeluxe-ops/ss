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
 * animación de reposo («reposo», de tools/void/caballero_alas.py: el cuerpo, lo tranquilo del «idle» del modelo; las
 * alas, abiertas hacia atrás y aleteando despacio. 1.3.89 dejaba las del idle, plegadas −110° hacia delante: sus plumas
 * pasaban 5 píxeles por delante del pecho, «las alas le traspasan al pecho»; el script lo mide), un 40 % más
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
    /** Su espada: la grande del set Hada (blanca y dorada, con alas, animada). */
    static final ResourceLocation SWORD = new ResourceLocation(net.tierrasfantasticas.tfclient.TFClient.MOD_ID, "fairy_greatsword");
    private static net.minecraft.world.item.ItemStack swordStack;

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
            // (1.3.93) el brazo derecho, adelantado y algo cerrado: la mano sobre el puño de la espada clavada delante
            int arm = m.bone("right_arm");
            if (arm >= 0) mats[arm].rotateX((float) Math.toRadians(ARM_PITCH)).rotateZ((float) Math.toRadians(ARM_ROLL));
            pose.pushPose();
            // Flota un poco (las alas)
            pose.translate(0, 0.06 * Mth.sin((e.clientAge + partial) * 0.07F), 0);
            pose.mulPose(Axis.YP.rotationDegrees(180F - body));
            pose.scale(SCALE, SCALE, SCALE);
            ModelDraw.draw(m, mats, hidden, pose, buffers, LightTexture.FULL_BRIGHT, 0xFFFFFF, e.clientAge, 1F);
            sword(e, m, mats, pose, buffers);
            pose.popPose();
        }
        label(e, pose, buffers);
    }

    /** (1.3.93) La pose de la espada (medida con tools/void/caballero_espada.py: puño en la mano, punta en el suelo). */
    static final float ARM_PITCH = 60F, ARM_ROLL = -10F, LEAN = 6F, SWORD_SCALE = 0.64F;
    /** El centro de la mano (desde el pivote del brazo) y el puño de la espada (en su modelo), en píxeles. */
    private static final float HAND_X = 1F, HAND_Y = -9.6F, HAND_Z = 0F, GRIP_X = 8F, GRIP_Y = 3.8F, GRIP_Z = 8F;
    /** Lo que traslada su «tercera persona» (models/item/fairy_greatsword.json, display.thirdperson_righthand), en píxeles. */
    private static final float DISP_Y = 1.75F, DISP_Z = 1F;

    /**
     * La espada del set Hada ({@link #SWORD}: blanca y dorada, con alas y animada; 1.3.92, el dueño: «dale una espada
     * angelical de nuestro set»). 1.3.93, el dueño con captura: «la espada mal puesta»: en la 1.3.92 iba erguida, por
     * fuera del brazo y con el puño a 4,8 píxeles de la mano (flotaba a su lado). Ahora, como un guardián: clavada de
     * punta delante de él, algo a su derecha, y la mano sobre el puño (el puño de la espada, entre la guarda y el pomo,
     * en el centro de la mano; el brazo se adelanta y se cierra un poco en render). La hoja va en los ejes del modelo
     * (no en los del brazo): se deshace el giro que se le añadió al brazo.
     */
    private void sword(KnightEntity e, VfxModel m, Matrix4f[] mats, PoseStack pose, MultiBufferSource buffers) {
        int arm = m.bone("right_arm");
        if (arm < 0) return;
        if (swordStack == null) {
            net.minecraft.world.item.Item it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(SWORD);
            swordStack = it == null || it == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.ItemStack.EMPTY
                    : new net.minecraft.world.item.ItemStack(it);
        }
        if (swordStack.isEmpty()) return;
        pose.pushPose();
        // en píxeles del modelo, desde el hueso del brazo (que va con la animación) hasta el centro de la mano
        pose.scale(1F / 16F, 1F / 16F, 1F / 16F);
        pose.mulPoseMatrix(mats[arm]);
        // (mulPoseMatrix no gira las normales: sin esto la luz de la espada salía con el giro del brazo de menos, y con
        // el brazo adelantado 60° se veía gris oscura en vez de blanca y dorada: prueba en el juego, 1.3.93)
        pose.last().normal().mul(new Matrix4f(mats[arm]).normal(new org.joml.Matrix3f()));
        pose.translate(HAND_X, HAND_Y, HAND_Z);
        // sin el giro añadido al brazo; la hoja hacia abajo con su cara (y sus alas) de frente, la punta algo adelantada
        pose.mulPose(Axis.ZP.rotationDegrees(-ARM_ROLL));
        pose.mulPose(Axis.XP.rotationDegrees(-ARM_PITCH));
        pose.mulPose(Axis.XP.rotationDegrees(LEAN));
        pose.mulPose(Axis.ZP.rotationDegrees(180F));
        pose.mulPose(Axis.YP.rotationDegrees(90F));
        pose.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);
        // el puño de la espada, en la mano (ItemRenderer le suma su «tercera persona» y le resta medio bloque)
        pose.translate(-GRIP_X + 8F, -(GRIP_Y - 8F + DISP_Y), -(GRIP_Z - 8F + DISP_Z));
        pose.scale(16F, 16F, 16F);
        Minecraft.getInstance().getItemRenderer().renderStatic(swordStack, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
        pose.popPose();
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
