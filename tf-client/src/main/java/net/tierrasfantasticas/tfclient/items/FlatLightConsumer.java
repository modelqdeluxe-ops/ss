/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * (1.3.176) Pasa los vértices tal cual, pero todos con la misma normal. Para las alas y las capas de la espalda: están
 * hechas de tiras planas en ángulos distintos (el ala rosa de Primavera, 3 tiras a 0°, 22,5° y 45°) y la luz de
 * Minecraft sombrea cada cara según hacia dónde mira, así que cada tira salía de un tono (el dueño, con captura: «por qué
 * esta mierda está negra, una parte oscura como cobreada»; medido: la de dentro al 66 % de la de fuera). Con la normal
 * hacia arriba del cuerpo, todas reciben la luz entera, como en el probador de la web.
 */
@OnlyIn(Dist.CLIENT)
final class FlatLightConsumer implements VertexConsumer {
    private final VertexConsumer parent;
    private final float nx, ny, nz;

    FlatLightConsumer(VertexConsumer parent, float nx, float ny, float nz) {
        this.parent = parent;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        parent.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        parent.color(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        parent.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        parent.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        parent.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        parent.normal(nx, ny, nz);
        return this;
    }

    @Override
    public void endVertex() {
        parent.endVertex();
    }

    @Override
    public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light,
            float normalX, float normalY, float normalZ) {
        parent.vertex(x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        parent.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        parent.unsetDefaultColor();
    }
}
