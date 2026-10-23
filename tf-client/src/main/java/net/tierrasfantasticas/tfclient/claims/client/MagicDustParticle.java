/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.claims.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * física (sin gravedad, sin choques). Usa las texturas vanilla de destello (glitter_*, las de la vara del End), que son
 * blancas y nítidas y se tiñen con el color de la protección. Cada mota lleva un fotograma FIJO (no se anima el
 * sprite), así se ve limpia y siempre igual.
 *
 * Dos modos:
 *  - PUNTO de línea: quieto, opaco, en una rejilla fija. Una «onda» de brillo recorre la línea (todas las motas usan
 *    el mismo reloj y su posición, así dos tandas seguidas se ven idénticas y la línea parece continua).
 *  - CHISPA: las chispitas que saltan en la pared cuando el jugador está cerca del límite. Suben un poco, parpadean
 *    (pulso común), se apagan rápido y se recortan en los extremos de la pared.
 */
public class MagicDustParticle extends TextureSheetParticle {
    // Sprites cacheados del atlas de partículas (se refrescan desde el contorno, por si cambian los packs)
    private static final TextureAtlasSprite[] GLITTER = new TextureAtlasSprite[8];
    private static boolean spritesReady = false;

    // Fotogramas de glitter que se usan (7/5 = rombo grande, 3 = rombo pequeño, 2 = cuadradito 3x3)
    static final int FRAME_POINT = 3;
    static final int FRAME_NODE = 5;
    static final int FRAME_BLOCK = 2;
    static final int FRAME_TIP = 3;

    // Reloj común: múltiplo de los dos periodos (onda 60 ticks, pulso 14 ticks) para que el salto al dar la vuelta no se note
    private static final long CLOCK_WRAP = 8400L;
    // Onda de las líneas: cada cuánto hay una cresta (bloques) y lo rápido que avanza (bloques por tick)
    private static final float WAVE_LENGTH = 12.0f;
    private static final float WAVE_SPEED = 0.2f;
    // Periodo del parpadeo común de las chispas (ticks)
    private static final float PULSE_PERIOD = 14.0f;
    // Margen de recorte suave en los extremos de la pared (bloques)
    private static final float CLIP_SOFT = 0.3f;

    private final boolean arrow;
    private final float baseR;
    private final float baseG;
    private final float baseB;
    private final float whiten;
    private final float baseAlpha;
    private final float baseSize;
    private final float waveCoord;
    private final int fadeIn;
    private final int fadeOut;
    // Recorte a lo largo de la pared: eje (0 = X, 1 = Z, -1 = sin recorte) y límites
    private final int clipAxis;
    private final double clipMin;
    private final double clipMax;

    private MagicDustParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              TextureAtlasSprite sprite, float r, float g, float b, float whiten, float alpha, float size,
                              int lifetime, int fadeIn, int fadeOut, boolean arrow, float waveCoord,
                              int clipAxis, double clipMin, double clipMax) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 1.0f;
        this.lifetime = lifetime;
        this.arrow = arrow;
        this.baseR = r;
        this.baseG = g;
        this.baseB = b;
        this.whiten = whiten;
        this.baseAlpha = alpha;
        this.baseSize = size;
        this.quadSize = size;
        this.waveCoord = waveCoord;
        this.fadeIn = Math.max(1, fadeIn);
        this.fadeOut = Math.max(1, fadeOut);
        this.clipAxis = clipAxis;
        this.clipMin = clipMin;
        this.clipMax = clipMax;
        this.alpha = 0.0f;
        this.setColor(r, g, b);
        this.setSprite(sprite);
    }

    /**
     * Un punto quieto de una línea del contorno (null si aún no hay textura: nunca se crea una mota sin sprite).
     * waveCoord es la coordenada a lo largo de la línea, para la onda de brillo.
     */
    static MagicDustParticle point(ClientLevel level, double x, double y, double z, float[] rgb, boolean node,
                                   float waveCoord, int lifetime, int fadeIn, int fadeOut) {
        TextureAtlasSprite sprite = sprite(node ? FRAME_NODE : FRAME_POINT);
        if (sprite == null) {
            return null;
        }
        return new MagicDustParticle(level, x, y, z, 0.0, 0.0, 0.0, sprite, rgb[0], rgb[1], rgb[2],
                node ? 0.45f : 0.0f, 1.0f, node ? 0.17f : 0.12f, lifetime, fadeIn, fadeOut, false, waveCoord, -1, 0.0, 0.0);
    }

    /** Una chispita del aviso de cerca del límite; big = rombo más grande y blanco (null si aún no hay textura). */
    static MagicDustParticle spark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                   float[] rgb, boolean big, float whiten, float alpha, float size, int lifetime,
                                   int clipAxis, double clipMin, double clipMax) {
        TextureAtlasSprite sprite = sprite(big ? FRAME_TIP : FRAME_BLOCK);
        if (sprite == null) {
            return null;
        }
        MagicDustParticle p = new MagicDustParticle(level, x, y, z, vx, vy, vz, sprite, rgb[0], rgb[1], rgb[2], whiten, alpha,
                size, lifetime, 4, 10, true, 0.0f, clipAxis, clipMin, clipMax);
        // Frena poco a poco: sube suave en vez de salir disparada
        p.friction = 0.96f;
        return p;
    }

    private static TextureAtlasSprite sprite(int frame) {
        return spritesReady ? GLITTER[Mth.clamp(frame, 0, 7)] : null;
    }

    /**
     * Vuelve a leer los sprites del atlas de partículas (barato: 8 búsquedas en un mapa). El atlas de partículas no
     * está en el ModelManager (Minecraft.getTextureAtlas daba NullPointerException y cerraba el juego): se pide al
     * TextureManager. Si aún no está cargado, no hace nada (y no salen motas).
     */
    static void refreshSprites(Minecraft mc) {
        try {
            if (!(mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES) instanceof TextureAtlas atlas)) return;
            boolean ok = true;
            for (int i = 0; i < 8; i++) {
                GLITTER[i] = atlas.getSprite(new ResourceLocation("minecraft", "glitter_" + i));
                ok &= GLITTER[i] != null;
            }
            spritesReady = ok;
        } catch (RuntimeException e) {
            spritesReady = false;
        }
    }

    static boolean spritesReady() {
        return spritesReady;
    }

    // Coordenada a lo largo de la pared (para el recorte de las chispas)
    private double alongWall(float partial) {
        return this.clipAxis == 0 ? Mth.lerp(partial, this.xo, this.x) : Mth.lerp(partial, this.zo, this.z);
    }

    @Override
    public void tick() {
        super.tick();
        // Una chispa que ya ha salido por un extremo de la pared no vuelve: fuera
        if (this.isAlive() && this.clipAxis >= 0) {
            double u = this.alongWall(1.0f);
            double v = this.clipAxis == 0 ? this.xd : this.zd;
            if ((v > 0.0 && u > this.clipMax + CLIP_SOFT) || (v < 0.0 && u < this.clipMin - CLIP_SOFT)) {
                this.remove();
            }
        }
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partial) {
        if (this.sprite == null) {
            this.remove();
            return;
        }
        float t = this.age + partial;
        // Entrada y salida cortas (sin «pops»)
        float fade = Math.min(1.0f, t / this.fadeIn) * Math.min(1.0f, (this.lifetime - t) / this.fadeOut);
        if (fade <= 0.0f) {
            return;
        }
        // Mismo reloj para todas las motas: así las tandas encajan y las chispas parpadean a la vez
        float clock = (this.level.getGameTime() % CLOCK_WRAP) + partial;
        float white;
        float size;
        float alphaMul;
        if (this.arrow) {
            // Recorte suave en los extremos de la pared
            if (this.clipAxis >= 0) {
                double u = this.alongWall(partial);
                double edge = Math.min(u - this.clipMin, this.clipMax - u);
                if (edge <= 0.0) {
                    return;
                }
                fade *= (float)Math.min(1.0, edge / CLIP_SOFT);
            }
            // Pulso común: se encienden y apagan todas juntas, sin bajar tanto que se pierdan
            float pulse = 0.5f + 0.5f * Mth.sin(clock * Mth.TWO_PI / PULSE_PERIOD);
            white = this.whiten + (1.0f - this.whiten) * 0.35f * pulse;
            size = this.baseSize * (0.85f + 0.25f * pulse);
            alphaMul = 0.6f + 0.4f * pulse;
        } else {
            // Onda de brillo que recorre la línea: una cresta estrecha que blanquea y agranda un poco las motas
            float c = 0.5f + 0.5f * Mth.cos((clock * WAVE_SPEED - this.waveCoord) * Mth.TWO_PI / WAVE_LENGTH);
            c = c * c;
            c = c * c;
            c = c * c;
            white = this.whiten + (1.0f - this.whiten) * 0.6f * c;
            size = this.baseSize * (1.0f + 0.35f * c);
            alphaMul = 1.0f;
        }
        this.rCol = this.baseR + (1.0f - this.baseR) * white;
        this.gCol = this.baseG + (1.0f - this.baseG) * white;
        this.bCol = this.baseB + (1.0f - this.baseB) * white;
        this.quadSize = size;
        this.alpha = Mth.clamp(this.baseAlpha * alphaMul * fade, 0.0f, 1.0f);
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
