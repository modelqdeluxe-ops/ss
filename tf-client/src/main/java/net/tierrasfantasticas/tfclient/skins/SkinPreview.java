/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.skins;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * La vista previa de una skin para el Armario del pad: el jugador de frente (cabeza, cuerpo, brazos y piernas, con la
 * capa de encima: pelo, chaqueta, mangas y pantalón) en pixel art, con el contorno azul noche de los iconos del pad,
 * centrado en un cuadrado de {@link #SIZE}. Lo mismo, en Python, en tools/pad/sim/pages.py (skin_preview).
 */
final class SkinPreview {
    static final int SIZE = 34;
    /** Azul noche del pad (0x18265C) en el orden de NativeImage (ABGR). */
    private static final int OUTLINE = 0xFF5C2618;

    private SkinPreview() {}

    static NativeImage compose(NativeImage skin, boolean slim) {
        NativeImage out = new NativeImage(SIZE, SIZE, true);
        int ox = 9, oy = 1, aw = slim ? 3 : 4;
        // el cuerpo (opaco)
        part(skin, out, 8, 8, 8, 8, ox + 4, oy, false); // cabeza
        part(skin, out, 20, 20, 8, 12, ox + 4, oy + 8, false); // cuerpo
        part(skin, out, 44, 20, aw, 12, ox + 4 - aw, oy + 8, false); // brazo derecho (a la izquierda, de frente)
        part(skin, out, 36, 52, aw, 12, ox + 12, oy + 8, false); // brazo izquierdo
        part(skin, out, 4, 20, 4, 12, ox + 4, oy + 20, false); // pierna derecha
        part(skin, out, 20, 52, 4, 12, ox + 8, oy + 20, false); // pierna izquierda
        // la capa de encima
        part(skin, out, 40, 8, 8, 8, ox + 4, oy, true);
        part(skin, out, 20, 36, 8, 12, ox + 4, oy + 8, true);
        part(skin, out, 44, 36, aw, 12, ox + 4 - aw, oy + 8, true);
        part(skin, out, 52, 52, aw, 12, ox + 12, oy + 8, true);
        part(skin, out, 4, 36, 4, 12, ox + 4, oy + 20, true);
        part(skin, out, 4, 52, 4, 12, ox + 8, oy + 20, true);
        outline(out);
        return out;
    }

    private static void part(NativeImage skin, NativeImage out, int u, int v, int w, int h, int x, int y, boolean overlay) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = skin.getPixelRGBA(u + i, v + j);
                if (!overlay) {
                    out.setPixelRGBA(x + i, y + j, c | 0xFF000000);
                    continue;
                }
                int a = c >>> 24;
                if (a == 0) continue;
                out.setPixelRGBA(x + i, y + j, a == 255 ? c : over(out.getPixelRGBA(x + i, y + j), c, a));
            }
        }
    }

    /** c encima de dst con alfa a (los canales van en el mismo orden en los dos: da igual cuál es cuál). */
    private static int over(int dst, int c, int a) {
        int r = 0;
        for (int s = 0; s < 24; s += 8) {
            int d = dst >> s & 255, v = c >> s & 255;
            r |= (d + (v - d) * a / 255) << s;
        }
        return 0xFF000000 | r;
    }

    private static void outline(NativeImage img) {
        boolean[] solid = new boolean[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) solid[y * SIZE + x] = (img.getPixelRGBA(x, y) >>> 24) != 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (solid[y * SIZE + x]) continue;
                boolean edge = x > 0 && solid[y * SIZE + x - 1] || x < SIZE - 1 && solid[y * SIZE + x + 1]
                        || y > 0 && solid[(y - 1) * SIZE + x] || y < SIZE - 1 && solid[(y + 1) * SIZE + x];
                if (edge) img.setPixelRGBA(x, y, OUTLINE);
            }
        }
    }
}
