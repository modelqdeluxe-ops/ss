package net.tierrasfantasticas.tfclient.trail;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.mixin.PostChainAccessor;

/**
 * Colores vivos (1.3.67): en lugar del pack Punchy (que es un pack de texturas de otro autor, sin permiso para
 * repartirlo), un filtro de pantalla que satura y da un poco de contraste a todo lo que se ve, con cuánto en el pad
 * (Efectos › COMBATE). Es el color_convolve de Minecraft (shaders/post/vivid.json). Con shaders (Oculus) no se usa.
 */
public final class VividColors {
    private static final ResourceLocation CHAIN = new ResourceLocation(TFClient.MOD_ID, "shaders/post/vivid.json");
    private static PostChain chain;
    private static boolean failed;
    private static int width = -1, height = -1, amount = -1;
    private static Boolean shaders;

    private VividColors() {}

    /** ¿Se puede usar? (no con Oculus: sus shaders ya llevan su propio color). */
    public static boolean available() {
        if (shaders == null) shaders = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
        return !shaders && !failed;
    }

    public static void apply(float partial) {
        if (!TrailSettings.vivid || !available()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
                width = height = amount = -1;
            }
            int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
            if (w != width || h != height) {
                chain.resize(w, h);
                width = w;
                height = h;
            }
            if (amount != TrailSettings.vividAmount) {
                amount = TrailSettings.vividAmount;
                float k = amount / 100F;
                for (PostPass pass : ((PostChainAccessor) chain).tfclient$passes()) {
                    pass.getEffect().safeGetUniform("Saturation").set(1F + 0.8F * k);
                    // un poco de contraste alrededor del gris medio
                    float scale = 1F + 0.1F * k;
                    pass.getEffect().safeGetUniform("ColorScale").set(scale, scale, scale);
                    float off = -0.5F * (scale - 1F);
                    pass.getEffect().safeGetUniform("Offset").set(off, off, off);
                }
            }
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(partial);
            mc.getMainRenderTarget().bindWrite(true);
        } catch (Exception e) {
            failed = true;
            TFClient.LOGGER.warn("TF Client: no se pudo usar el filtro de colores vivos", e);
            if (chain != null) chain.close();
            chain = null;
        }
    }
}
