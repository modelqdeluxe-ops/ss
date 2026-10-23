/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Los objetos en el Void con shaders (1.3.117, el dueño: «los items se me ponen negros cuando uso shaders dentro del
 * Void; fuera, normales»). Medido en la prueba con Complementary (commit a64be32): el objeto en la mano, luminancia 45 en el
 * santuario frente a 108 en el mundo normal. En el Void no hay luz de cielo ni de bloque (0, 0) y el paquete, con sus
 * programas del End ({@link ShaderPacks}), solo le da su luz ambiente, muy baja. Así que, con paquete y en el Void, lo
 * que se tiene en la mano y los objetos tirados se dibujan con luz de cielo 15 y de bloque {@link #BLOCK} (como con un
 * farol cerca): se ven con sus colores. Sin paquete no hace falta (el mapa de luz del Void ya es claro).
 */
@OnlyIn(Dist.CLIENT)
public final class VoidItemLight {
    public static final int BLOCK = 12;

    private VoidItemLight() {}

    public static int lift(int packed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !TFVoid.isVoid(mc.level) || !ShaderPacks.inUse()) return packed;
        return LightTexture.pack(Math.max(BLOCK, LightTexture.block(packed)), 15);
    }
}
