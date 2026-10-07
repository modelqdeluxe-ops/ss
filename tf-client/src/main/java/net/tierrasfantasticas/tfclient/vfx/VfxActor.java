package net.tierrasfantasticas.tfclient.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * La víctima de un efecto de kill haciendo la animación ella misma (en lugar del muñeco del pack). Vale para todos
 * los mobs y jugadores: el cuerpo entero sigue al torso del muñeco (saltos, giros, encogerse, salir volando) y, si su
 * modelo es humanoide (zombis, esqueletos, jugadores, piglins...), la cabeza, los brazos y las piernas siguen a los del
 * muñeco (LivingEntityRendererMixin). Si el efecto convierte el cuerpo en otro material (óxido, piedra, holograma...),
 * el mob se dibuja con esa textura; si el muñeco tiene un doble (su fantasma), es otra vez el mob.
 */
public final class VfxActor {
    /** Partes del modelo humanoide en el orden del rig: cabeza, cuerpo, brazo der., brazo izq., pierna der., pierna izq. */
    private static final int PARTS = 6;

    /** El mob que se está dibujando ahora mismo como actor (para no ocultarlo y para que el mixin le ponga la pose). */
    static LivingEntity rendering;
    /** Pose de las partes humanoides: 0 nada, 1 pose, 2 oculta; giro x/y/z (rad) y escala x/y/z. */
    private static final float[][] POSE = new float[PARTS][7];
    /** Movimiento de cada pieza del muñeco (en el espacio del modelo de Blockbench, sin el del torso). */
    private static final Matrix4f[] MOTION = new Matrix4f[PARTS];
    private static final List<Object[]> SAVED = new ArrayList<>();

    private VfxActor() {}

    /**
     * Dibuja el mob haciendo la animación: una vez por cada figura del muñeco y textura (la suya o la del material).
     * instance: transformación del efecto (con la cámara ya restada); bones: matrices del modelo ahora; visible: huesos
     * que se ven. Devuelve false si no ha dibujado nada.
     */
    static boolean render(LivingEntity entity, VfxModel model, Matrix4f view, Matrix3f viewNormal, Matrix4f instance,
                          Matrix4f instanceRotScale, Matrix4f[] bones, boolean[] visible, float yaw, float partial,
                          MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        // Tu propio cuerpo en primera persona: la cámara está dentro, no se dibuja
        if (entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return false;
        Matrix4f[] rest = model.rest();
        int savedDeath = entity.deathTime, savedHurt = entity.hurtTime;
        float[] savedRot = {entity.yBodyRot, entity.yBodyRotO, entity.yHeadRot, entity.yHeadRotO};
        // Sin la caída de lado ni el rojo de la muerte: el efecto ya es la muerte
        entity.deathTime = 0;
        entity.hurtTime = 0;
        // Mirando hacia donde mira el efecto, para que sus giros y saltos vayan en la misma dirección
        entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = yaw;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        int light = dispatcher.getPackedLightCoords(entity, partial);
        light = LightTexture.pack(15, LightTexture.sky(light)); // la luz del efecto (como el bloque de luz del pack)
        boolean shadows = mc.options.entityShadows().get();
        dispatcher.setRenderShadow(false);
        rendering = entity;
        boolean drawn = false;
        Matrix4f instInv = new Matrix4f(instanceRotScale).invert();
        try {
            for (VfxModel.Figure fig : model.figures) {
                int root = fig.rig()[VfxModel.RIG_ROOT];
                Matrix4f rootNow = bones[root];
                if (Math.abs(rootNow.determinant3x3()) < 1e-6F || !anyVisible(fig, visible)) continue; // no se ve
                // Lo que se ha movido el torso desde el reposo, en el espacio del modelo
                Matrix4f delta = new Matrix4f(rootNow).mul(new Matrix4f(rest[root]).invert());
                // Al espacio del mundo: el mob se dibuja como si el muñeco fuera él
                Matrix4f m = new Matrix4f(view).mul(instance).mul(delta).mul(instInv);
                Matrix3f normal = new Matrix3f(viewNormal).mul(new Matrix3f(instance).mul(new Matrix3f(delta))
                        .mul(new Matrix3f(instInv)).normal());
                float det = Math.abs(normal.determinant());
                if (det > 1e-6F) normal.scale(1F / (float) Math.cbrt(det));
                // El doble de material (un fantasma, un holograma) solo tiene las piezas que tiene en el pack
                computeLimbs(fig.rig(), bones, rest, visible, fig.tex()[0] != null);
                for (String tex : fig.tex()) {
                    PoseStack ps = new PoseStack();
                    ps.last().pose().set(m);
                    ps.last().normal().set(normal);
                    ResourceLocation material = tex == null ? null : ResourceLocation.tryParse(tex);
                    MultiBufferSource out = material == null ? buffers : material(buffers, material);
                    try {
                        dispatcher.render(entity, 0, 0, 0, yaw, partial, ps, out, light);
                        drawn = true;
                    } finally {
                        restoreParts();
                    }
                }
            }
        } finally {
            entity.yBodyRot = savedRot[0];
            entity.yBodyRotO = savedRot[1];
            entity.yHeadRot = savedRot[2];
            entity.yHeadRotO = savedRot[3];
            dispatcher.setRenderShadow(shadows);
            rendering = null;
            entity.deathTime = savedDeath;
            entity.hurtTime = savedHurt;
        }
        return drawn;
    }

    private static boolean anyVisible(VfxModel.Figure fig, boolean[] visible) {
        for (int i = 1; i < fig.rig().length; i++) {
            int bi = fig.rig()[i];
            if (bi >= 0 && visible[bi]) return true;
        }
        return false;
    }

    /**
     * Cómo se mueve cada pieza del muñeco, quitando lo que ya hace el torso (que mueve el mob entero): giro y escala
     * para la pieza de Minecraft y el movimiento entero para colocar su pivote (que no tiene por qué ser el del muñeco).
     */
    private static void computeLimbs(int[] rig, Matrix4f[] bones, Matrix4f[] rest, boolean[] visible, boolean hideMissing) {
        int root = rig[VfxModel.RIG_ROOT];
        Matrix4f deltaInv = new Matrix4f(bones[root]).mul(new Matrix4f(rest[root]).invert()).invert();
        Vector3f sc = new Vector3f(), e = new Vector3f();
        for (int k = 0; k < PARTS; k++) {
            int bi = rig[k + 1];
            float[] p = POSE[k];
            p[0] = 0;
            if (bi < 0) {
                if (hideMissing) p[0] = 2;
                continue;
            }
            Matrix4f a = new Matrix4f(deltaInv).mul(bones[bi]).mul(new Matrix4f(rest[bi]).invert());
            a.getScale(sc);
            if (!visible[bi] || sc.x < 1e-5F || sc.y < 1e-5F || sc.z < 1e-5F) {
                p[0] = 2; // la pieza desaparece (oculta o escala cero)
                continue;
            }
            new Matrix4f().set(new Matrix3f(a).scale(1F / sc.x, 1F / sc.y, 1F / sc.z)).getEulerAnglesZYX(e);
            // Del espacio de Blockbench al del modelo de Minecraft (X e Y al revés)
            p[0] = 1;
            p[1] = -e.x;
            p[2] = -e.y;
            p[3] = e.z;
            p[4] = sc.x;
            p[5] = sc.y;
            p[6] = sc.z;
            MOTION[k] = a;
        }
    }

    public static boolean isRendering(LivingEntity entity) {
        return rendering != null && entity == rendering;
    }

    /** Lo llama el mixin de LivingEntityRenderer después de setupAnim: pone las piezas como el muñeco. */
    public static void applyHumanoid(HumanoidModel<?> model, LivingEntity entity) {
        if (!isRendering(entity)) return;
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int k = 0; k < PARTS; k++) {
            float[] p = POSE[k];
            if (p[0] == 0) continue;
            ModelPart part = parts[k];
            save(part);
            if (p[0] == 2) {
                part.visible = false;
                continue;
            }
            // El pivote de la pieza del mob, en Blockbench (el cuello del modelo humanoide está a 24 px del suelo)
            Vector3f pivot = new Vector3f(-part.x, 24F - part.y, part.z);
            Vector3f moved = MOTION[k].transformPosition(new Vector3f(pivot)).sub(pivot);
            part.x -= moved.x;
            part.y -= moved.y;
            part.z += moved.z;
            part.xRot = p[1];
            part.yRot = p[2];
            part.zRot = p[3];
            part.xScale *= p[4];
            part.yScale *= p[5];
            part.zScale *= p[6];
        }
        save(model.hat);
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> player) {
            // La segunda capa de la skin va con su pieza
            ModelPart[][] layers = {{player.jacket, model.body}, {player.rightSleeve, model.rightArm},
                    {player.leftSleeve, model.leftArm}, {player.rightPants, model.rightLeg}, {player.leftPants, model.leftLeg}};
            for (ModelPart[] l : layers) {
                save(l[0]);
                l[0].copyFrom(l[1]);
                l[0].visible = l[0].visible && l[1].visible;
                l[0].xScale = l[1].xScale;
                l[0].yScale = l[1].yScale;
                l[0].zScale = l[1].zScale;
            }
        }
        model.hat.visible = model.hat.visible && model.head.visible;
        model.hat.xScale = model.head.xScale;
        model.hat.yScale = model.head.yScale;
        model.hat.zScale = model.head.zScale;
    }

    private static void save(ModelPart part) {
        SAVED.add(new Object[]{part, new float[]{part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale,
                part.yScale, part.zScale, part.visible ? 1 : 0}});
    }

    /** El modelo es compartido por todos los mobs de ese tipo: se deja como estaba. */
    private static void restoreParts() {
        for (int i = SAVED.size() - 1; i >= 0; i--) {
            ModelPart part = (ModelPart) SAVED.get(i)[0];
            float[] v = (float[]) SAVED.get(i)[1];
            part.x = v[0];
            part.y = v[1];
            part.z = v[2];
            part.xRot = v[3];
            part.yRot = v[4];
            part.zRot = v[5];
            part.xScale = v[6];
            part.yScale = v[7];
            part.zScale = v[8];
            part.visible = v[9] != 0;
        }
        SAVED.clear();
    }

    /**
     * Buffers que dibujan el cuerpo del mob con la textura de un material. Lo primero que pide el renderer es el cuerpo;
     * el resto (armadura, objetos en la mano, nombre) no se dibuja en esta pasada.
     */
    private static MultiBufferSource material(MultiBufferSource source, ResourceLocation texture) {
        RenderType[] body = new RenderType[1];
        return type -> {
            if (body[0] == null) body[0] = type;
            return type == body[0] ? source.getBuffer(RenderType.entityTranslucent(texture)) : DISCARD;
        };
    }

    /** Un buffer que no dibuja nada. */
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {}

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {}

        @Override
        public void unsetDefaultColor() {}
    };
}
