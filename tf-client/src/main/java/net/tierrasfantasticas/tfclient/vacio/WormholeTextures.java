package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Las texturas del agujero de gusano ({@link TravelFx}, shader wormhole), hechas con las del propio Minecraft (las del
 * paquete de recursos que tenga puesto el jugador) la primera vez que hacen falta:
 * <ul>
 *   <li>el panorama del menú: sus 4 caras del horizonte seguidas, 2048×512 (el Overworld: sus tarjetas y su cielo);</li>
 *   <li>un atlas de 4×4 losetas de 16 px: filas 0-1 del Overworld (hierba con su verde de llanura, tierra, piedra, hojas
 *   de roble con su verde, cerezo, troncos), fila 2 del Nether, fila 3 del End ({@link #TILES});</li>
 *   <li>el portal del Nether con el color ya multiplicado por su transparencia (el shader lo pinta opaco).</li>
 * </ul>
 * La lava, las estrellas del portal del End y el cielo del End se usan tal cual ({@link #LAVA}, {@link #STARS},
 * {@link #END_SKY}). Lo mismo hace la vista previa sin juego (tools/void/tunel_preview.py).
 */
@OnlyIn(Dist.CLIENT)
final class WormholeTextures {
    /** = tunel_preview.TILES: el orden del atlas. */
    static final String[] TILES = {"grass_block_top", "grass_block_side", "dirt", "stone", "oak_leaves", "oak_log", "cherry_leaves",
            "cherry_log", "netherrack", "nether_bricks", "glowstone", "crimson_nylium", "end_stone", "purpur_block", "obsidian",
            "crying_obsidian"};
    static final ResourceLocation PANORAMA = new ResourceLocation(TFClient.MOD_ID, "wormhole/panorama");
    static final ResourceLocation ATLAS = new ResourceLocation(TFClient.MOD_ID, "wormhole/atlas");
    static final ResourceLocation PORTAL = new ResourceLocation(TFClient.MOD_ID, "wormhole/portal");
    static final ResourceLocation LAVA = new ResourceLocation("textures/block/lava_still.png");
    static final ResourceLocation STARS = new ResourceLocation("textures/entity/end_portal.png");
    static final ResourceLocation END_SKY = new ResourceLocation("textures/environment/end_sky.png");
    private static boolean ready;
    /** Fotogramas de la lava y del portal (alto / ancho de sus tiras). */
    static float lavaFrames = 20F, portalFrames = 32F;

    private WormholeTextures() {}

    /** Las hace si no están (en el hilo de dibujo). */
    static void prepare() {
        if (ready) return;
        ready = true;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.getTextureManager().register(PANORAMA, panorama());
            DynamicTexture pano = (DynamicTexture) mc.getTextureManager().getTexture(PANORAMA);
            pano.setFilter(true, false);
            mc.getTextureManager().register(ATLAS, new DynamicTexture(atlas()));
            NativeImage portal = read("textures/block/nether_portal.png");
            if (portal != null) {
                portalFrames = Math.max(1, portal.getHeight() / Math.max(1, portal.getWidth()));
                for (int y = 0; y < portal.getHeight(); y++) {
                    for (int x = 0; x < portal.getWidth(); x++) {
                        int c = portal.getPixelRGBA(x, y);
                        int a = c >>> 24;
                        int r = (c & 255) * a / 255, g = (c >> 8 & 255) * a / 255, b = (c >> 16 & 255) * a / 255;
                        portal.setPixelRGBA(x, y, 0xFF000000 | b << 16 | g << 8 | r);
                    }
                }
                mc.getTextureManager().register(PORTAL, new DynamicTexture(portal));
            }
            try (NativeImage lava = read("textures/block/lava_still.png")) {
                if (lava != null) lavaFrames = Math.max(1, lava.getHeight() / Math.max(1, lava.getWidth()));
            }
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Void: no se pudieron preparar las texturas del agujero de gusano", t);
        }
    }

    private static NativeImage read(String path) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(path));
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            return NativeImage.read(in);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Void: no se pudo leer {}", path, e);
            return null;
        }
    }

    /** Las 4 caras del horizonte del panorama del menú, de 512 cada una (si no están, un atardecer). */
    private static DynamicTexture panorama() {
        NativeImage out = new NativeImage(2048, 512, false);
        for (int i = 0; i < 4; i++) {
            NativeImage f = read("textures/gui/title/background/panorama_" + i + ".png");
            if (f == null || f.getWidth() < 64) {
                if (f != null) f.close();
                for (int y = 0; y < 512; y++) {
                    float k = y / 511F;
                    int r = (int) (255 * (0.45F + 0.5F * k)), g = (int) (255 * (0.55F + 0.2F * k)), b = (int) (255 * (0.85F - 0.35F * k));
                    for (int x = 0; x < 512; x++) out.setPixelRGBA(i * 512 + x, y, 0xFF000000 | b << 16 | g << 8 | r);
                }
                continue;
            }
            try (f) {
                scale(f, 0, 0, f.getWidth(), f.getHeight(), out, i * 512, 0, 512, 512);
            }
        }
        return new DynamicTexture(out);
    }

    /** Las 16 losetas (el primer fotograma de cada bloque, a 16 px), con los tintes y las hojas sobre oscuro. */
    private static NativeImage atlas() {
        NativeImage out = new NativeImage(64, 64, false);
        for (int i = 0; i < TILES.length; i++) {
            String name = TILES[i];
            int ox = (i % 4) * 16, oy = (i / 4) * 16;
            NativeImage src = read("textures/block/" + name + ".png");
            NativeImage tile = new NativeImage(16, 16, false);
            if (src == null) {
                tile.fillRect(0, 0, 16, 16, 0xFF808080);
            } else {
                try (src) {
                    int w = src.getWidth();
                    scale(src, 0, 0, w, Math.min(w, src.getHeight()), tile, 0, 0, 16, 16);
                }
            }
            int tr = 255, tg = 255, tb = 255;
            if (name.equals("grass_block_top")) {
                tr = 0x7c;
                tg = 0xbd;
                tb = 0x6b;
            } else if (name.equals("oak_leaves")) {
                tr = 0x59;
                tg = 0xae;
                tb = 0x30;
            }
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int c = tile.getPixelRGBA(x, y);
                    float a = (c >>> 24) / 255F;
                    // (las hojas tienen huecos: detrás, oscuro)
                    int r = Math.round((c & 255) * tr / 255F * a + 20 * (1 - a));
                    int g = Math.round((c >> 8 & 255) * tg / 255F * a + 30 * (1 - a));
                    int b = Math.round((c >> 16 & 255) * tb / 255F * a + 18 * (1 - a));
                    out.setPixelRGBA(ox + x, oy + y, 0xFF000000 | Math.min(255, b) << 16 | Math.min(255, g) << 8 | Math.min(255, r));
                }
            }
            tile.close();
        }
        return out;
    }

    /** Copia un trozo de una imagen en otra, a otro tamaño (media de los píxeles que caen en cada uno). */
    private static void scale(NativeImage src, int sx, int sy, int sw, int sh, NativeImage dst, int dx, int dy, int dw, int dh) {
        for (int y = 0; y < dh; y++) {
            int y0 = sy + y * sh / dh, y1 = Math.max(y0 + 1, sy + (y + 1) * sh / dh);
            for (int x = 0; x < dw; x++) {
                int x0 = sx + x * sw / dw, x1 = Math.max(x0 + 1, sx + (x + 1) * sw / dw);
                long r = 0, g = 0, b = 0, a = 0;
                int n = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        int c = src.getPixelRGBA(xx, yy);
                        r += c & 255;
                        g += c >> 8 & 255;
                        b += c >> 16 & 255;
                        a += c >>> 24;
                        n++;
                    }
                }
                dst.setPixelRGBA(dx + x, dy + y, (int) (a / n) << 24 | (int) (b / n) << 16 | (int) (g / n) << 8 | (int) (r / n));
            }
        }
    }
}
