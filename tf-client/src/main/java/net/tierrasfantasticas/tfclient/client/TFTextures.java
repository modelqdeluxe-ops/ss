package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.locating.IModFile;
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
        String path = "assets/" + TFClient.MOD_ID + "/textures/gui/" + name;
        var modFile = ModList.get() == null ? null : ModList.get().getModFileById(TFClient.MOD_ID);
        if (modFile != null) {
            IModFile file = modFile.getFile();
            Path resource = file.findResource(path.split("/"));
            if (Files.exists(resource)) {
                try (InputStream in = Files.newInputStream(resource)) {
                    return NativeImage.read(in);
                }
            }
        }
        try (InputStream in = TFTextures.class.getResourceAsStream("/" + path)) {
            if (in == null) throw new IOException("No se encuentra " + path);
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
