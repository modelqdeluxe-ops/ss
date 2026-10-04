package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    LOGO("logo.png");

    private final String file;
    private final ResourceLocation id;
    private int width = 1;
    private int height = 1;
    private boolean loaded;
    private boolean failed;

    TFTextures(String file) {
        this.file = file;
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
            NativeImage image = NativeImage.read(in);
            width = image.getWidth();
            height = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.setFilter(true, false);
            loaded = true;
        } catch (Exception e) {
            failed = true;
            TFClient.LOGGER.error("TF Client: no se pudo cargar {}", file, e);
        }
        return loaded;
    }
}
