/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
                // (los caballeros sacros, sin el reposo de Caelthar —que flota—: su postura, sacredPose)
                if (def == null) e.layers.play(m, m.anims.containsKey("reposo") ? "reposo" : "idle", e.clientAge, 1F, 0, 0, 1, true);
                // (1.3.117, el dueño: «siguen con la animación de elevarse y caer; el único que debe volar es Caelthar») su
                // modelo es el de Caelthar y trae «idle» (flotar: sube y baja); VfxLayers pone sola la animación de reposo
                // por defecto por debajo de todo: a los sacros, ninguna (solo su postura de jugador)
                if (def != null) {
                    e.layers.setDefault("idle", "-", e.clientAge);
                    e.layers.setDefault("walk", "-", e.clientAge);
                }
                e.shown = 1;
            }
            if (def != null) sacredPose(e, def, m, partial);
            e.layers.prune(e.clientAge);
            Matrix4f[] mats = new Matrix4f[m.bones.length];
            for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
            float body = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
            float headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - body);
            float headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
            e.layers.pose(m, e.clientAge + partial, Mth.clamp(headYaw, -60F, 60F), headPitch, mats);
            // (1.3.93b) el brazo derecho, algo adelantado, como el de un jugador con algo en la mano
            int arm = m.bone("right_arm");
            if (arm >= 0 && def == null) mats[arm].rotateX((float) Math.toRadians(ARM_PITCH));
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
     * (1.3.98) La postura de cada caballero sacro, de pie en el suelo (el dueño: «todos tienen la misma animación, están
     * rotos porque vuelan también; el único que debe volar es el que teletransporta»; en la 1.3.97 llevaban el reposo de
     * Caelthar, que flota). Giros a mano sobre el pivote de cada hueso (VfxLayers.animate: lo que cuelga va con él), en
     * grados: +x adelanta un brazo o una pierna; −x inclina el cuerpo o baja la cabeza.
     * <p>
     * 1.3.115 (el dueño, otra vez: «siguen con la animación de volar»; su captura: Osric de cerca, con los brazos
     * adelante, la cabeza baja y el cuerpo inclinado de «leer el libro»): fuera las posturas propias (leer, agacharse a las
     * flores, alzar el farol, hacerse sombra): de lado parecían volar. Ahora son **como un jugador** (HumanoidModel): de pie
     * y derechos, los brazos con el vaivén suave de los jugadores quietos (AnimationUtils.bobArms), el brazo de lo que
     * llevan algo adelantado (ArmPose.ITEM) y, al andar, piernas y brazos como los de un jugador (WalkAnimationState).
     * Solo la cabeza mira a los lados de vez en cuando (Bastian y Varen, que vigilan).
     */
    private static void sacredPose(KnightEntity e, SacredKnights.Def d, VfxModel m, float partial) {
        float age = e.clientAge + partial + (e.getId() % 97) * 13F;
        float t = age;
        // brazos quietos como los de un jugador: ±3° adelante/atrás y hacia fuera (bobArms, en grados)
        float bobX = (float) Math.toDegrees(Mth.sin(age * 0.067F) * 0.05F);
        float bobZ = (float) Math.toDegrees(Mth.cos(age * 0.09F) * 0.05F + 0.05F);
        float body = 0F, bodyY = 0F, head = 0F, headY = 0F, headZ = 0F;
        // el brazo de lo que lleva, adelantado como el de un jugador con algo en la mano (18°)
        float la = -bobX, laZ = -2F - bobZ, ra = ARM_PITCH + bobX, raZ = 2F + bobZ, ll = 0F, rl = 0F;
        if (d.id() == 1 || d.id() == 2) headY = 22F * Mth.sin(t * 0.016F);
        // al andar: como un jugador (rightLeg = cos(p·0,6662)·1,4·v; brazos = cos(p·0,6662 + π)·v, el de la mano a la mitad)
        float speed = Math.min(1F, e.walkAnimation.speed(partial));
        if (speed > 0.01F) {
            float p = e.walkAnimation.position(partial) * 0.6662F;
            float leg = (float) Math.toDegrees(Mth.cos(p) * 1.4F * speed);
            float arm = (float) Math.toDegrees(Mth.cos(p) * speed);
            ll = leg;
            rl = -leg;
            la = la - arm;
            ra = ra + arm * 0.5F;
        }
        pose(e, m, "main_tail", body, bodyY, 0F);
        pose(e, m, "head", head, headY, headZ);
        pose(e, m, "left_arm", la, 0F, laZ);
        pose(e, m, "right_arm", ra, 0F, raZ);
        pose(e, m, "left_leg", ll, 0F, -1F);
        pose(e, m, "right_leg", rl, 0F, 1F);
    }

    private static final float[] ZERO3 = {0F, 0F, 0F}, ONE3 = {1F, 1F, 1F};

    private static void pose(KnightEntity e, VfxModel m, String bone, float x, float y, float z) {
        int b = m.bone(bone);
        if (b >= 0) e.layers.animate(new int[] {b}, ZERO3, new float[] {x, y, z}, ONE3, false, false, true, false);
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
