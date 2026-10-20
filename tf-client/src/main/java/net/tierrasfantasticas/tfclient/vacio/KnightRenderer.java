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
public class KnightRenderer<T extends KnightEntity> extends EntityRenderer<T> {
    static final String MODEL = "vacio.caballero";
    static final float SCALE = 1.4F;
    /** (1.3.97) Los caballeros sacros: algo más pequeños que Caelthar, sin alas, con su metal y lo suyo en la mano. */
    static final float SACRED_SCALE = 1.25F;
    private static final String[] WINGS = {"left_wing", "left_wing2", "right_wing", "right_wing2"};
    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> ITEMS = new java.util.HashMap<>();
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private boolean[] hidden, hiddenSacred;
    /** Su espada: la grande del set Hada (blanca y dorada, con alas, animada). */
    static final ResourceLocation SWORD = new ResourceLocation(net.tierrasfantasticas.tfclient.TFClient.MOD_ID, "fairy_greatsword");
    private static net.minecraft.world.item.ItemStack swordStack;

    public KnightRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.7F;
    }

    @Override
    public void render(T e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        SacredKnights.Def def = e instanceof SacredKnight sk ? sk.def() : null;
        VfxModel m = SkillMobs.model(def != null ? def.model() : MODEL);
        if (m != null) {
            if (hidden == null || hidden.length != m.bones.length) {
                hidden = new boolean[m.bones.length];
                hiddenSacred = new boolean[m.bones.length];
                for (String b : new String[] {"left_island", "right_island", "name"}) {
                    int i = m.bone(b);
                    if (i >= 0) hidden[i] = hiddenSacred[i] = true;
                }
                for (String b : WINGS) {
                    int i = m.bone(b);
                    if (i >= 0) hiddenSacred[i] = true;
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
            // (1.3.93b) el brazo derecho, algo adelantado, como el de un jugador con algo en la mano
            int arm = m.bone("right_arm");
            if (arm >= 0) mats[arm].rotateX((float) Math.toRadians(ARM_PITCH));
            if (def != null) walk(e, m, mats, partial);
            pose.pushPose();
            // Flota un poco (las alas; los caballeros sacros andan por el suelo)
            if (def == null) pose.translate(0, 0.06 * Mth.sin((e.clientAge + partial) * 0.07F), 0);
            pose.mulPose(Axis.YP.rotationDegrees(180F - body));
            float sc = def != null ? SACRED_SCALE : SCALE;
            pose.scale(sc, sc, sc);
            ModelDraw.draw(m, mats, def != null ? hiddenSacred : hidden, pose, buffers, LightTexture.FULL_BRIGHT, 0xFFFFFF, e.clientAge, 1F);
            sword(e, m, mats, pose, buffers, def != null ? held(def.item()) : null);
            pose.popPose();
        }
        label(e, pose, buffers);
    }

    /**
     * (1.3.93b) El brazo adelantado como el de un jugador que lleva algo en la mano (HumanoidModel.ArmPose.ITEM: su xRot
     * pasa a xRot·0,5 − π/10, 18°; en nuestros ejes, con +y arriba, es un giro de +18°).
     */
    static final float ARM_PITCH = 18F;

    /**
     * La espada del set Hada ({@link #SWORD}: blanca y dorada, con alas y animada; 1.3.92, el dueño: «dale una espada
     * angelical de nuestro set»), como la lleva un jugador (1.3.93b, el dueño: «la pusiste como mierda, recuerda que él
     * vuela y se ve mal; pónsela normal, tal cual fuera un jugador»: la 1.3.93 la clavaba en el suelo con la mano en el
     * puño). Lo mismo que ItemInHandLayer con HumanoidModel.translateToHand: el brazo del caballero es el de un jugador
     * (pivote 5, 22, 0; el antebrazo de x −1,25 a 3,25 y la mano a 10,25 píxeles), con +y hacia arriba en vez de hacia
     * abajo: los ejes de Minecraft son los nuestros con x e y al revés. Medido con tools/void/caballero_espada.py: el
     * puño a 0,75 píxeles del centro de la mano.
     */
    private void sword(KnightEntity e, VfxModel m, Matrix4f[] mats, PoseStack pose, MultiBufferSource buffers,
            net.minecraft.world.item.ItemStack other) {
        int arm = m.bone("right_arm");
        if (arm < 0) return;
        if (swordStack == null) {
            net.minecraft.world.item.Item it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(SWORD);
            swordStack = it == null || it == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.ItemStack.EMPTY
                    : new net.minecraft.world.item.ItemStack(it);
        }
        net.minecraft.world.item.ItemStack stack = other != null ? other : swordStack;
        if (stack.isEmpty()) return;
        pose.pushPose();
        // el hueso del brazo (en píxeles, con la animación), y de vuelta a bloques
        pose.scale(1F / 16F, 1F / 16F, 1F / 16F);
        pose.mulPoseMatrix(mats[arm]);
        // (mulPoseMatrix no gira las normales: sin esto la luz de la espada salía con el giro del brazo de menos: gris
        // oscura con el brazo adelantado, prueba en el juego de la 1.3.93)
        pose.last().normal().mul(new Matrix4f(mats[arm]).normal(new org.joml.Matrix3f()));
        pose.scale(16F, 16F, 16F);
        // a los ejes de los modelos de Minecraft, y lo de ItemInHandLayer.renderArmWithItem (brazo derecho)
        pose.scale(-1F, -1F, 1F);
        pose.mulPose(Axis.XP.rotationDegrees(-90F));
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        pose.translate(1F / 16F, 0.125F, -0.625F);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
        pose.popPose();
    }

    /** (1.3.97) Lo que lleva en la mano un caballero sacro (un objeto de Minecraft o de los sets). */
    private static net.minecraft.world.item.ItemStack held(String id) {
        return ITEMS.computeIfAbsent(id, k -> {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(k);
            net.minecraft.world.item.Item it = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
            return it == null || it == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(it);
        });
    }

    /**
     * (1.3.97) Al andar, las piernas y los brazos se mueven como los de un jugador (el modelo no trae animación de andar):
     * HumanoidModel.setupAnim, con el paso y la velocidad de Minecraft (WalkAnimationState).
     */
    private static void walk(KnightEntity e, VfxModel m, Matrix4f[] mats, float partial) {
        float speed = Math.min(1F, e.walkAnimation.speed(partial));
        if (speed < 0.01F) return;
        float pos = e.walkAnimation.position(partial);
        float swing = Mth.cos(pos * 0.6662F) * 1.2F * speed;
        int l = m.bone("left_leg"), r = m.bone("right_leg"), la = m.bone("left_arm");
        if (l >= 0) mats[l].rotateX(swing);
        if (r >= 0) mats[r].rotateX(-swing);
        if (la >= 0) mats[la].rotateX(-swing * 0.6F);
    }

    private void label(KnightEntity e, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d2 = mc.player.distanceToSqr(e);
        if (d2 > 24 * 24) return;
        Component name = Component.literal(e.displayName()).withStyle(s -> s.withColor(TextColor.fromRgb(0xFFD66B)).withBold(true));
        String t = e instanceof SacredKnight ? e.displayTitle() : e.home() ? "Caballero Sacro · Guardián del santuario" : "Caballero Sacro";
        Component title = Component.literal(t).withStyle(s -> s.withColor(TextColor.fromRgb(0xE9DDFF)));
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
    protected boolean shouldShowName(T e) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return NONE;
    }
}
