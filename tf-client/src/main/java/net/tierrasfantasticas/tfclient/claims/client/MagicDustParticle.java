package net.tierrasfantasticas.tfclient.claims.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Una mota de «polvo mágico» del contorno de las protecciones. Es una partícula solo del cliente (no se registra ni
 * viaja por la red): se mete directamente en el motor de partículas, con el color de la zona, brillo propio y sin
 * física. Usa las texturas vanilla de destello (glitter_*, las de la vara del End) y de polvo (generic_*), que son
 * blancas y se tiñen con el color de la protección.
 *
 * Dos modos:
 *  - LÍNEA: brilla suave y titila por su cuenta (cada mota a su ritmo).
 *  - CORTINA: cuando el jugador está pegado al límite; todas parpadean a la vez (pulso común) y se mueven en diagonal.
 */
public class MagicDustParticle extends TextureSheetParticle {
    // Sprites cacheados del atlas de partículas (se refrescan una vez por tick desde el contorno, por si cambian los packs)
    private static final TextureAtlasSprite[] GLITTER = new TextureAtlasSprite[8];
    private static final TextureAtlasSprite[] DUST = new TextureAtlasSprite[8];
    private static boolean spritesReady = false;
    // Periodo del parpadeo de la cortina (ticks) y alfa mínimo del pulso
    private static final float PULSE_PERIOD = 14.0f;

    private final TextureAtlasSprite[] frames;
    private final boolean curtain;
    private final float baseAlpha;
    private final float baseSize;
    private final float twinklePhase;

    MagicDustParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                      float r, float g, float b, boolean curtain, boolean glitter, int lifetime, float size, float alpha) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 0.985f;
        this.lifetime = lifetime;
        this.curtain = curtain;
        this.frames = glitter ? GLITTER : DUST;
        this.baseSize = size;
        this.quadSize = size;
        this.baseAlpha = alpha;
        this.alpha = 0.0f;
        this.twinklePhase = this.random.nextFloat() * Mth.TWO_PI;
        this.setColor(r, g, b);
        this.pickFrame();
    }

    /** Vuelve a leer los sprites del atlas de partículas (barato: 16 búsquedas en un mapa). */
    static void refreshSprites(Minecraft mc) {
        Function<ResourceLocation, TextureAtlasSprite> atlas = mc.getTextureAtlas(TextureAtlas.LOCATION_PARTICLES);
        for (int i = 0; i < 8; i++) {
            GLITTER[i] = atlas.apply(new ResourceLocation("minecraft", "glitter_" + i));
            DUST[i] = atlas.apply(new ResourceLocation("minecraft", "generic_" + i));
        }
        spritesReady = true;
    }

    static boolean spritesReady() {
        return spritesReady;
    }

    // Como la vara del End: empieza con el destello grande y se va apagando hasta el punto pequeño
    private void pickFrame() {
        int i = 7 - Mth.clamp(this.age * 8 / Math.max(1, this.lifetime), 0, 7);
        TextureAtlasSprite sprite = this.frames[i];
        if (sprite != null) {
            this.setSprite(sprite);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            this.pickFrame();
        }
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partial) {
        float t = this.age + partial;
        // Aparece y se desvanece suave (sin «pops»)
        float fade = Math.min(1.0f, t / 4.0f) * Math.min(1.0f, (this.lifetime - t) / 6.0f);
        float glow;
        if (this.curtain) {
            // Pulso común a toda la cortina: con el reloj del mundo, así todas las motas parpadean a la vez
            float clock = (this.level.getGameTime() % 100000L) + partial;
            float pulse = 0.5f + 0.5f * Mth.sin(clock * Mth.TWO_PI / PULSE_PERIOD);
            glow = 0.2f + 0.8f * pulse;
        } else {
            // Titileo individual, suave
            glow = 0.65f + 0.35f * Mth.sin(t * 0.9f + this.twinklePhase);
        }
        this.alpha = Mth.clamp(this.baseAlpha * glow * Math.max(0.0f, fade), 0.0f, 1.0f);
        this.quadSize = this.baseSize * (0.8f + 0.2f * glow);
        if (this.alpha > 0.01f) {
            super.render(consumer, camera, partial);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // Brillo propio: se ve igual de noche o bajo tierra
    @Override
    protected int getLightColor(float partial) {
        return LightTexture.FULL_BRIGHT;
    }
}
