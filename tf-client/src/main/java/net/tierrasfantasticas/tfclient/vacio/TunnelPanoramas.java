/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
 * Los panoramas de las dimensiones para el agujero de gusano (1.3.96; el dueño: «que muestre la dimensión, no sé si
 * estoy viendo el Nether de verdad»). El Nether, el End y el Void los capturó la prueba en el juego y los pasó a
 * equirectangulares tools/void/panoramas.py (textures/environment/tunel_*.png). El Overworld no va en el mod: se hace la
 * primera vez que hace falta con el panorama del menú de cada uno (minecraft:textures/gui/title/background/panorama_N,
 * el de Minecraft o el de su paquete de recursos), con las mismas cuentas que panoramas.py: 6 caras de 90° (0 sur, 1
 * oeste, 2 norte, 3 este, 4 arriba, 5 abajo) a una equirectangular de 1024×512 con la longitud 0 (el sur) en el centro.
 * Si no se puede, el túnel del Overworld es el de antes (su pared dibujada).
 */
@OnlyIn(Dist.CLIENT)
public final class TunnelPanoramas {
    public static final ResourceLocation NETHER = new ResourceLocation(TFClient.MOD_ID, "textures/environment/tunel_nether.png");
    public static final ResourceLocation END = new ResourceLocation(TFClient.MOD_ID, "textures/environment/tunel_end.png");
    public static final ResourceLocation VOID = new ResourceLocation(TFClient.MOD_ID, "textures/environment/tunel_void.png");
    private static final ResourceLocation OVERWORLD = new ResourceLocation(TFClient.MOD_ID, "dyn/tunel_overworld");
    private static final int W = 1024, H = 512;
    /** (adelante, derecha, arriba) de cada cara, en x este, y arriba, z sur (= panoramas.py FACES). */
    private static final int[][][] FACES = {
            {{0, 0, 1}, {-1, 0, 0}, {0, 1, 0}},
            {{-1, 0, 0}, {0, 0, -1}, {0, 1, 0}},
            {{0, 0, -1}, {1, 0, 0}, {0, 1, 0}},
            {{1, 0, 0}, {0, 0, 1}, {0, 1, 0}},
            {{0, 1, 0}, {-1, 0, 0}, {0, 0, -1}},
            {{0, -1, 0}, {-1, 0, 0}, {0, 0, 1}}};
    private static boolean tried, ready;

    private TunnelPanoramas() {}

    /** ¿Está hecho el del Overworld? */
    static boolean overworldReady() {
        return ready;
    }

    /** El del Overworld (lo hace la primera vez; si no se pudo, uno cualquiera: el shader no lo usa). */
    static ResourceLocation overworld() {
        if (!tried) {
            tried = true;
            try {
                build();
                ready = true;
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Void: no se pudo hacer el panorama del Overworld para el agujero de gusano: {}", e.toString());
            }
        }
        return ready ? OVERWORLD : NETHER;
    }

    private static void build() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        NativeImage[] faces = new NativeImage[6];
        try {
            for (int k = 0; k < 6; k++) {
                ResourceLocation rl = new ResourceLocation("minecraft", "textures/gui/title/background/panorama_" + k + ".png");
                Optional<Resource> res = mc.getResourceManager().getResource(rl);
                if (res.isEmpty()) throw new IllegalStateException("falta " + rl);
                try (InputStream in = res.get().open()) {
                    faces[k] = NativeImage.read(in);
                }
            }
            NativeImage out = new NativeImage(W, H, false);
            for (int y = 0; y < H; y++) {
                double lat = (0.5 - (y + 0.5) / H) * Math.PI;
                for (int x = 0; x < W; x++) {
                    double lon = ((x + 0.5) / W - 0.5) * 2 * Math.PI;
                    double dx = Math.cos(lat) * Math.sin(lon), dy = Math.sin(lat), dz = Math.cos(lat) * Math.cos(lon);
                    out.setPixelRGBA(x, y, sample(faces, dx, dy, dz));
                }
            }
            DynamicTexture tex = new DynamicTexture(out);
            // (suave al acercarse, como los otros con su .mcmeta «blur»)
            tex.setFilter(true, false);
            mc.getTextureManager().register(OVERWORLD, tex);
            TFClient.LOGGER.info("TF Void: panorama del Overworld para el agujero de gusano hecho ({}x{})", W, H);
        } finally {
            for (NativeImage f : faces) {
                if (f != null) f.close();
            }
        }
    }

    /** El color en esa dirección: la cara que más mira hacia ella, con interpolación (byte a byte: da igual su orden). */
    private static int sample(NativeImage[] faces, double dx, double dy, double dz) {
        int best = 0;
        double bd = -2;
        for (int k = 0; k < 6; k++) {
            int[] f = FACES[k][0];
            double d = dx * f[0] + dy * f[1] + dz * f[2];
            if (d > bd) {
                bd = d;
                best = k;
            }
        }
        int[] r = FACES[best][1], u = FACES[best][2];
        NativeImage img = faces[best];
        int n = img.getWidth(), m = img.getHeight();
        double sx = (dx * r[0] + dy * r[1] + dz * r[2]) / bd, sy = (dx * u[0] + dy * u[1] + dz * u[2]) / bd;
        double px = Math.max(0, Math.min(n - 1, (sx + 1) / 2 * (n - 1))), py = Math.max(0, Math.min(m - 1, (1 - (sy + 1) / 2) * (m - 1)));
        int x0 = (int) px, y0 = (int) py, x1 = Math.min(x0 + 1, n - 1), y1 = Math.min(y0 + 1, m - 1);
        double fx = px - x0, fy = py - y0;
        int c00 = img.getPixelRGBA(x0, y0), c10 = img.getPixelRGBA(x1, y0), c01 = img.getPixelRGBA(x0, y1), c11 = img.getPixelRGBA(x1, y1);
        int out = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            double v = ((c00 >>> sh) & 255) * (1 - fx) * (1 - fy) + ((c10 >>> sh) & 255) * fx * (1 - fy)
                    + ((c01 >>> sh) & 255) * (1 - fx) * fy + ((c11 >>> sh) & 255) * fx * fy;
            out |= (Math.min(255, (int) Math.round(v)) & 255) << sh;
        }
        return out;
    }
}
