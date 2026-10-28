/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Las luces de la fiesta de cumpleaños ({@link TFBirthdayClient}): brillos, farolillos, globos, corazones y notas, con
 * los dibujos de partículas de Minecraft (no hay que registrar nada), de colores, siempre con toda la luz (se ven de
 * noche y con shaders, que dibujan las partículas como siempre). Pueden volar de un sitio a otro (las estrellas que
 * escriben el nombre), subir meciéndose (farolillos y globos) o quedarse quietas parpadeando.
 */
@OnlyIn(Dist.CLIENT)
public final class TFPartyParticle extends TextureSheetParticle {
    /** Dibujos del atlas de partículas de Minecraft. */
    public static final String GLOW = "glow", BUBBLE = "bubble", HEART = "heart", NOTE = "note", SPARK = "spark_4", GLITTER = "glitter_5", FLASH = "flash";

    private final float baseSize, r, g, b;
    private final int fadeIn, fadeOut;
    private final float sway, flicker;
    private final double swayPhase;
    private Vec3 from, to;
    private int travel;

    private TFPartyParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, int rgb, float size, int life,
                            float sway, float flicker) {
        super(level, x, y, z);
        setSprite(sprite);
        this.r = ((rgb >> 16) & 0xFF) / 255F;
        this.g = ((rgb >> 8) & 0xFF) / 255F;
        this.b = (rgb & 0xFF) / 255F;
        this.baseSize = size;
        this.quadSize = size;
        this.lifetime = life;
        this.fadeIn = Math.min(12, life / 4);
        this.fadeOut = Math.min(30, life / 3);
        this.sway = sway;
        this.flicker = flicker;
        this.swayPhase = level.random.nextDouble() * Math.PI * 2;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1F;
        this.xd = this.yd = this.zd = 0;
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }

    private static TextureAtlasSprite sprite(String name) {
        if (!(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES) instanceof TextureAtlas atlas)) return null;
        return atlas.getSprite(new ResourceLocation("minecraft", name));
    }

    /** Una luz quieta (o con la velocidad que se le dé). Devuelve null si el atlas aún no está. */
    public static TFPartyParticle spawn(String sprite, double x, double y, double z, double vx, double vy, double vz, int rgb, float size,
                                        int life, float sway, float flicker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        TextureAtlasSprite s = sprite(sprite);
        if (s == null) return null;
        TFPartyParticle p = new TFPartyParticle(mc.level, x, y, z, s, rgb, size, life, sway, flicker);
        p.xd = vx;
        p.yd = vy;
        p.zd = vz;
        mc.particleEngine.add(p);
        return p;
    }

    /** Que vuele de su sitio a ese en tantos ticks (frenando al llegar) y se quede allí. */
    public TFPartyParticle flyTo(Vec3 target, int ticks) {
        this.from = new Vec3(x, y, z);
        this.to = target;
        this.travel = Math.max(1, ticks);
        this.xd = this.yd = this.zd = 0;
        return this;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        if (to != null) {
            float k = Math.min(1F, age / (float) travel);
            float e = 1F - (1F - k) * (1F - k) * (1F - k);
            setPos(Mth.lerp(e, from.x, to.x), Mth.lerp(e, from.y, to.y), Mth.lerp(e, from.z, to.z));
            return;
        }
        double sx = 0, sz = 0;
        if (sway > 0) {
            sx = Math.sin(age * 0.07 + swayPhase) * sway * 0.03;
            sz = Math.cos(age * 0.05 + swayPhase) * sway * 0.03;
        }
        move(xd + sx, yd, zd + sz);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partial) {
        float t = age + partial;
        float fade = Math.min(1F, t / Math.max(1, fadeIn)) * Math.min(1F, (lifetime - t) / Math.max(1, fadeOut));
        if (fade <= 0F) return;
        float f = flicker > 0 ? 1F - flicker * (0.5F + 0.5F * Mth.sin(t * 0.6F + (float) swayPhase * 3F)) : 1F;
        alpha = Mth.clamp(fade * f, 0F, 1F);
        quadSize = baseSize * (0.85F + 0.15F * f);
        super.render(consumer, camera, partial);
    }

    @Override
    protected int getLightColor(float partial) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
