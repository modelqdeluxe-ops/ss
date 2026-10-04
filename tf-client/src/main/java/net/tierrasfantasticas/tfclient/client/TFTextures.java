package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Fondo y emblema de TF. Se cargan directamente del .jar del mod (no de los resource packs) para que
 * estén disponibles desde el primer fotograma de la pantalla de carga, antes de que el juego cargue recursos.
 */
public final class TFTextures {
    public static final ResourceLocation BACKGROUND = new ResourceLocation(TFClient.MOD_ID, "dynamic/background");
    public static final ResourceLocation LOGO = new ResourceLocation(TFClient.MOD_ID, "dynamic/logo");

    private static int backgroundWidth = 1600;
    private static int backgroundHeight = 900;
    private static int logoWidth = 512;
    private static int logoHeight = 565;
    private static boolean loaded;
    private static boolean failed;

    private TFTextures() {}

    /** Carga las texturas la primera vez. Debe llamarse desde el hilo de render. */
    public static boolean ensureLoaded() {
        if (loaded) return true;
        if (failed) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            NativeImage background = read("background.png");
            backgroundWidth = background.getWidth();
            backgroundHeight = background.getHeight();
            DynamicTexture backgroundTexture = new DynamicTexture(background);
            mc.getTextureManager().register(BACKGROUND, backgroundTexture);
            backgroundTexture.setFilter(true, false);

            NativeImage logo = read("logo.png");
            logoWidth = logo.getWidth();
            logoHeight = logo.getHeight();
            DynamicTexture logoTexture = new DynamicTexture(logo);
            mc.getTextureManager().register(LOGO, logoTexture);
            logoTexture.setFilter(true, false);

            loaded = true;
        } catch (Exception e) {
            failed = true;
            TFClient.LOGGER.error("TF Client: no se pudieron cargar las imágenes", e);
        }
        return loaded;
    }

    private static NativeImage read(String name) throws IOException {
        try (InputStream in = TFResources.open("assets/" + TFClient.MOD_ID + "/textures/gui/" + name)) {
            return NativeImage.read(in);
        }
    }

    public static int backgroundWidth() {
        return backgroundWidth;
    }

    public static int backgroundHeight() {
        return backgroundHeight;
    }

    public static int logoWidth() {
        return logoWidth;
    }

    public static int logoHeight() {
        return logoHeight;
    }
}
