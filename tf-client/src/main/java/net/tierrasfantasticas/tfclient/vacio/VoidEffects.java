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
 * una niebla morada muy oscura.
 */
@OnlyIn(Dist.CLIENT)
public class VoidEffects extends DimensionSpecialEffects {
    static final Vec3 FOG = new Vec3(0.045, 0.012, 0.085);

    public VoidEffects() {
        super(Float.NaN, false, SkyType.NONE, true, false);
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
