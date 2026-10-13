package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.skins.SkinClient;
import org.joml.Matrix4f;

/**
 * Una skin dibujada como el muñeco 3D del jugador, para el Armario: SOLO la skin (cabeza, cuerpo, brazos y piernas con
 * su capa de encima), sin armadura, sin lo que lleva en la mano, sin alas, capa ni cosméticos. Mira hacia el ratón
 * como en el inventario y respira (los brazos se mecen un poco). Las imágenes del pad que lo piden:
 * <ul>
 *   <li>{@link PadView#SELF_MODEL}: la skin que llevas ahora (la del Armario o, si no, la de tu cuenta).</li>
 *   <li>{@code tfclient:skinmodel/own}: la skin de tu cuenta de Minecraft (la de siempre; Steve o Alex si no tienes).</li>
 *   <li>{@code tfclient:skinmodel/<huella>_f|_n}: una skin del Armario, con brazos finos o normales.</li>
 * </ul>
 */
final class PadSkinModel {
    static final String PREFIX = "tfclient:skinmodel/";
    private static PlayerModel<AbstractClientPlayer> wide, slim;

    private PadSkinModel() {}

    /** ¿Es una de las imágenes que se dibujan como muñeco 3D? */
    static boolean is(String id) {
        return id != null && (id.equals(PadView.SELF_MODEL) || id.startsWith(PREFIX));
    }

    private record Skin(ResourceLocation texture, boolean slim) {}

    /** La textura y los brazos de la imagen; null si aún no ha llegado (el pad dibuja el objeto mientras). */
    private static Skin resolve(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        UUID me = mc.player.getUUID();
        if (id.equals(PadView.SELF_MODEL)) {
            ResourceLocation worn = SkinClient.texture(me);
            if (worn != null) return new Skin(worn, "slim".equals(SkinClient.model(me)));
            return own(mc, me);
        }
        String rest = id.substring(PREFIX.length());
        if (rest.equals("own")) return own(mc, me);
        if (rest.length() < 3) return null;
        ResourceLocation tex = SkinClient.textureOf(rest.substring(0, rest.length() - 2));
        return tex == null ? null : new Skin(tex, rest.endsWith("_f"));
    }

    /** La skin de la cuenta (como la resuelve el juego, sin la del Armario encima). */
    private static Skin own(Minecraft mc, UUID me) {
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(me);
        SkinClient.bypass = true;
        try {
            if (info != null) return new Skin(info.getSkinLocation(), "slim".equals(info.getModelName()));
            return new Skin(DefaultPlayerSkin.getDefaultSkin(me), "slim".equals(DefaultPlayerSkin.getSkinModelName(me)));
        } finally {
            SkinClient.bypass = false;
        }
    }

    private static PlayerModel<AbstractClientPlayer> model(boolean thin) {
        if (thin && slim == null || !thin && wide == null) {
            var models = Minecraft.getInstance().getEntityModels();
            PlayerModel<AbstractClientPlayer> m = new PlayerModel<>(models.bakeLayer(thin ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), thin);
            m.young = false;
            if (thin) slim = m;
            else wide = m;
        }
        return thin ? slim : wide;
    }

    /**
     * Dibuja la skin de id con los pies en (cx, feetY) y h de alto (de los pies a lo alto de la cabeza), mirando hacia
     * (mx, my). Devuelve false si su textura aún no ha llegado.
     */
    static boolean draw(GuiGraphics g, String id, float cx, float feetY, float h, double mx, double my) {
        Skin skin = resolve(id);
        if (skin == null) return false;
        PlayerModel<AbstractClientPlayer> m = model(skin.slim());
        // como InventoryScreen.renderEntityInInventoryFollowsMouse: el cuerpo gira un poco hacia el ratón, la cabeza más
        float eyeY = feetY - h * 0.85F;
        float ax = (float) Math.atan((cx - mx) / 40.0), ay = (float) Math.atan((eyeY - my) / 40.0);
        float ticks = PadSettings.animations ? PadSkin.time() * 20F : 0F;
        m.head.resetPose();
        m.head.yRot = ax * 20F * (float) Math.PI / 180F;
        m.head.xRot = -ay * 20F * (float) Math.PI / 180F;
        m.body.resetPose();
        m.leftLeg.resetPose();
        m.rightLeg.resetPose();
        m.rightArm.resetPose();
        m.leftArm.resetPose();
        // respira: los brazos se separan y vuelven, muy poco (AnimationUtils.bobArms)
        float bob = (float) Math.cos(ticks * 0.09F) * 0.05F + 0.05F, swing = (float) Math.sin(ticks * 0.067F) * 0.05F;
        m.rightArm.zRot += bob;
        m.leftArm.zRot -= bob;
        m.rightArm.xRot += swing;
        m.leftArm.xRot -= swing;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.setAllVisible(true);
        float s = h / 2.0F; // 32 píxeles de skin = 2 bloques, de los pies a lo alto de la cabeza
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, feetY, 150);
        ps.mulPoseMatrix(new Matrix4f().scaling(s, s, -s));
        ps.mulPose(Axis.ZP.rotationDegrees(180F));
        ps.mulPose(Axis.XP.rotationDegrees(ay * 20F));
        ps.mulPose(Axis.YP.rotationDegrees(-ax * 20F));
        ps.scale(-1F, -1F, 1F);
        ps.translate(0F, -1.501F, 0F);
        Lighting.setupForEntityInInventory();
        m.renderToBuffer(ps, g.bufferSource().getBuffer(RenderType.entityTranslucent(skin.texture())), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        g.flush();
        Lighting.setupFor3DItems();
        ps.popPose();
        return true;
    }
}
