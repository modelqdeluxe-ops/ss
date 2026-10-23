/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;

/**
 * Cómo se ve The Void: la luz del End (sin nubes, sin sol, el mapa de luz más claro: los mismos números que
 * {@code DimensionSpecialEffects.EndEffects}), su cielo con el agujero negro ({@link VoidSky}), sin nubes ni lluvia y
 * una niebla morada muy oscura. Con un paquete de shaders (Oculus), aquí no se dibuja: Minecraft pone el del End
 * (por eso SkyType.END) y el paquete, que usa en el Void sus programas del End ({@link ShaderPacks}), el suyo; encima,
 * al acabar el paquete, el nuestro donde se ve el cielo ({@link AfterShaders}).
 */
@OnlyIn(Dist.CLIENT)
public class VoidEffects extends DimensionSpecialEffects {
    static final Vec3 FOG = new Vec3(0.045, 0.012, 0.085);

    public VoidEffects() {
        super(Float.NaN, false, SkyType.END, true, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return FOG;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partial) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partial, PoseStack pose, Camera camera, Matrix4f projection,
                             boolean foggy, Runnable setupFog) {
        // (con shaders, Minecraft pone el del End, que el paquete cambia por el suyo, y el nuestro se dibuja después de su
        // pasada final, donde se ve el cielo: AfterShaders)
        if (ShaderPacks.inUse()) return false;
        try {
            VoidSky.render(pose, projection, partial);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Void: error al dibujar el cielo", t);
            VoidSky.shader = null;
        }
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partial, PoseStack pose, double camX, double camY, double camZ,
                                Matrix4f projection) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partial, LightTexture light, double camX, double camY,
                                     double camZ) {
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }
}
