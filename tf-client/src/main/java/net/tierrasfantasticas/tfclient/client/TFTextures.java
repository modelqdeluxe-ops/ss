package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Imágenes de TF. Se cargan directamente del .jar del mod (no de los resource packs) para que estén disponibles desde
 * el primer fotograma de la pantalla de carga, antes de que el juego cargue sus recursos.
 */
public enum TFTextures {
    /** Pantalla de carga: el banner con las letras de Tierras Fantásticas. */
    LOADING("loading_background.png"),
    /** Menú principal y resto de menús: el paisaje sin letras. */
    MENU("menu_background.png"),
    /** Emblema TF que va encima de los botones. */
    LOGO("logo.png"),
    /** Halo que late detrás del emblema. */
    LOGO_GLOW("logo_glow.png"),
    /** Fotogramas (5x4) del destello que cruza el emblema; sin mipmaps para no mezclar fotogramas vecinos. */
    LOGO_SHINE("logo_shine.png", Mode.NO_MIPMAPS),
    /** Chispa de la gema de la corona. */
    SPARKLE("sparkle.png"),
    /** Línea luminosa de los botones de oro (la del emblema, más suave). */
    BUTTON_SHINE("button_shine.png", Mode.NO_MIPMAPS),
    /** Botones en tres piezas (puntas fijas y centro estirable; 3 estados apilados). Ver TFButtonTheme. */
    BUTTON_SLICE("button_slice.png"),
    BUTTON_SLICE_PRIMARY("button_slice_primary.png");

    /** Cómo se sube la imagen: suavizada con mipmaps, suavizada sin mipmaps, o pixel art sin suavizar. */
    public enum Mode { MIPMAPS, NO_MIPMAPS, PIXEL_ART }

    private static final int MIP_LEVELS = 4;

    private final String file;
    private final Mode mode;
    private final ResourceLocation id;
    private int width = 1;
    private int height = 1;
    private boolean loaded;
    private boolean failed;

    TFTextures(String file) {
        this(file, Mode.MIPMAPS);
    }

    TFTextures(String file, Mode mode) {
        this.file = file;
        this.mode = mode;
        this.id = new ResourceLocation(TFClient.MOD_ID, "dynamic/" + name().toLowerCase(java.util.Locale.ROOT));
    }

    public ResourceLocation id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Carga la imagen la primera vez. Debe llamarse desde el hilo de render. */
    public boolean ready() {
        if (loaded) return true;
        if (failed) return false;
        try (InputStream in = TFResources.open("assets/" + TFClient.MOD_ID + "/textures/gui/" + file)) {
            NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in);
            width = image.getWidth();
            height = image.getHeight();
            StaticTexture texture = new StaticTexture();
            Minecraft.getInstance().getTextureManager().register(id, texture);
            if (mode != Mode.MIPMAPS) {
                // Pixel art: sin suavizado para que los píxeles queden nítidos. Sin mipmaps: suavizado de un solo nivel.
                TextureUtil.prepareImage(texture.getId(), 0, width, height);
                image.upload(0, 0, 0, 0, 0, width, height, mode == Mode.NO_MIPMAPS, true, false, true);
            } else {
                // Con mipmaps la imagen se ve nítida también cuando se dibuja más pequeña que su tamaño real.
                NativeImage[] levels = MipmapGenerator.generateMipLevels(new NativeImage[] {image}, MIP_LEVELS);
                TextureUtil.prepareImage(texture.getId(), MIP_LEVELS, width, height);
                for (int level = 0; level < levels.length; level++) {
                    NativeImage mip = levels[level];
                    mip.upload(level, 0, 0, 0, 0, mip.getWidth(), mip.getHeight(), true, true, true, true);
                }
            }
            loaded = true;
        } catch (Throwable e) {
            failed = true;
            TFClient.LOGGER.error("TF Client: no se pudo cargar {}", file, e);
        }
        return loaded;
    }

    /** Textura ya subida a la tarjeta gráfica por nosotros; no se carga de los resource packs. */
    private static final class StaticTexture extends AbstractTexture {
        @Override
        public void load(ResourceManager resourceManager) {
        }
    }
}
