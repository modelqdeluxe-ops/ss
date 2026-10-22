package net.tierrasfantasticas.tfclient.crates.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.crates.util.CrateParticles;
import net.tierrasfantasticas.tfclient.vacio.ShaderPacks;

/**
 * (1.3.109) Las partículas de las crates (CrateParticles): solo del cliente, sin registrar ni viajar por la red (como el
 * polvo mágico de las protecciones). Texturas propias en el atlas de partículas (textures/particle/crate_glow.png y
 * crate_spark.png, de tools/crates/particulas_crates.py), premultiplicadas: el alfa es un halo del color de la rareza
 * que tapa un poco lo de detrás y el centro suma luz por encima. Sumando luz sin más (la primera prueba), de día
 * salían bolas blancas sobre el cielo. A plena luz y sin escribir profundidad:
 * <ul>
 *   <li>luz: un orbe suave que aparece, flota frenándose y sube un poco, y se apaga encogiendo;</li>
 *   <li>destello: una estrella de cuatro puntas pequeña que gira despacio y parpadea;</li>
 *   <li>fogonazo: una luz grande que se abre y se apaga en un instante (la apertura).</li>
 * </ul>
 * Sin paquete de shaders van con su shader (crate_particle: el particle del juego descarta lo que tiene menos de 0,1
 * de alfa y cortaba el halo en un aro); con uno, con el particle del juego, que el paquete sustituye por el suyo.
 */
public final class CrateFxParticle extends TextureSheetParticle {
   /** Premultiplicado (ONE, ONE_MINUS_SRC_ALPHA) y sin escribir profundidad (no se tapan entre ellas). */
   static final ParticleRenderType PREMULTIPLIED = new ParticleRenderType() {
      @Override
      public void begin(BufferBuilder builder, TextureManager textures) {
         if (shader != null && !ShaderPacks.inUse()) RenderSystem.setShader(() -> shader);
         RenderSystem.depthMask(false);
         RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
         builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
      }

      @Override
      public void end(Tesselator tesselator) {
         tesselator.end();
         RenderSystem.defaultBlendFunc();
         RenderSystem.depthMask(true);
      }

      @Override
      public String toString() {
         return "TFCLIENT_CRATE_PREMULTIPLIED";
      }
   };

   /** crate_particle (lo registra {@link Shaders}); null si no cargó: entonces, el particle del juego. */
   static ShaderInstance shader;

   private static final ResourceLocation GLOW_TEX = new ResourceLocation("tfclient", "crate_glow");
   private static final ResourceLocation SPARK_TEX = new ResourceLocation("tfclient", "crate_spark");

   private final int kind;
   private final float baseSize, baseAlpha, spin, phase, baseR, baseG, baseB;

   private CrateFxParticle(ClientLevel level, int kind, double x, double y, double z, double vx, double vy, double vz, int rgb, float size,
                           TextureAtlasSprite sprite) {
      super(level, x, y, z);
      this.kind = kind;
      this.xd = vx;
      this.yd = vy;
      this.zd = vz;
      this.hasPhysics = false;
      this.setSprite(sprite);
      float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
      // un poco hacia blanco: con el color puro, el centro salía plano
      this.baseR = Mth.lerp(0.12F, r, 1.0F);
      this.baseG = Mth.lerp(0.12F, g, 1.0F);
      this.baseB = Mth.lerp(0.12F, b, 1.0F);
      this.phase = this.random.nextFloat() * 6.28F;
      switch (kind) {
         case CrateParticles.SPARK -> {
            this.lifetime = 14 + this.random.nextInt(14);
            this.baseSize = 0.07F * size * (0.8F + this.random.nextFloat() * 0.4F);
            this.baseAlpha = 0.95F;
            this.friction = 0.9F;
            this.gravity = 0.0F;
            this.spin = (this.random.nextFloat() - 0.5F) * 0.18F;
            this.roll = this.oRoll = this.random.nextFloat() * 6.28F;
         }
         case CrateParticles.FLASH -> {
            this.lifetime = 7;
            this.baseSize = 0.5F * size;
            this.baseAlpha = 0.85F;
            this.friction = 0.6F;
            this.gravity = 0.0F;
            this.spin = 0.0F;
         }
         default -> {
            this.lifetime = 22 + this.random.nextInt(22);
            this.baseSize = 0.11F * size * (0.8F + this.random.nextFloat() * 0.45F);
            this.baseAlpha = 0.7F;
            this.friction = 0.93F;
            this.gravity = -0.004F; // sube despacio, como una luz de hada
            this.spin = 0.0F;
         }
      }
      this.quadSize = this.baseSize;
      this.alpha = 0.0F;
   }

   /** El gancho de CrateParticles (se pone al arrancar el cliente: ClientSetup). */
   public static void install() {
      CrateParticles.sink = CrateFxParticle::spawn;
   }

   private static void spawn(Level level, int kind, double x, double y, double z, double vx, double vy, double vz, int rgb, float size) {
      Minecraft mc = Minecraft.getInstance();
      if (!(level instanceof ClientLevel cl) || mc.particleEngine == null) return;
      if (!(mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES) instanceof TextureAtlas atlas)) return;
      TextureAtlasSprite sprite = atlas.getSprite(kind == CrateParticles.SPARK ? SPARK_TEX : GLOW_TEX);
      if (sprite == null) return;
      // (las partículas que el jugador apagó en Opciones › Partículas: «mínimas», casi nada; «reducidas», la mitad)
      int opt = mc.options.particles().get().getId();
      if (opt == 2 || opt == 1 && cl.random.nextBoolean()) return;
      mc.particleEngine.add(new CrateFxParticle(cl, kind, x, y, z, vx, vy, vz, rgb, size, sprite));
   }

   @Override
   public void tick() {
      super.tick();
      this.oRoll = this.roll;
      this.roll += this.spin;
   }

   @Override
   public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
      float t = Mth.clamp((this.age + partialTicks) / (float) this.lifetime, 0.0F, 1.0F);
      float in, out, size;
      switch (this.kind) {
         case CrateParticles.SPARK -> {
            in = Math.min(1.0F, t * 6.0F);
            out = 1.0F - t * t;
            // parpadeo
            float tw = 0.65F + 0.35F * Mth.sin((this.age + partialTicks) * 0.9F + this.phase);
            this.alpha = this.baseAlpha * in * out * tw;
            size = this.baseSize * (0.7F + 0.6F * in) * (1.0F - 0.35F * t);
         }
         case CrateParticles.FLASH -> {
            this.alpha = this.baseAlpha * (1.0F - t) * (1.0F - t);
            size = this.baseSize * (0.6F + 1.4F * Mth.sqrt(t));
         }
         default -> {
            in = Math.min(1.0F, t * 5.0F);
            out = 1.0F - Mth.clamp((t - 0.45F) / 0.55F, 0.0F, 1.0F);
            this.alpha = this.baseAlpha * in * out * out;
            size = this.baseSize * (1.0F - 0.4F * t);
         }
      }
      this.quadSize = size;
      // premultiplicado: el color también se apaga con el alfa
      this.rCol = this.baseR * this.alpha;
      this.gCol = this.baseG * this.alpha;
      this.bCol = this.baseB * this.alpha;
      if (this.alpha > 0.004F) super.render(buffer, camera, partialTicks);
   }

   @Override
   protected int getLightColor(float partialTick) {
      return LightTexture.FULL_BRIGHT;
   }

   @Override
   public ParticleRenderType getRenderType() {
      return PREMULTIPLIED;
   }

   /** Lo registra Forge al cargar los recursos. */
   @Mod.EventBusSubscriber(modid = "tfclient", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
   public static final class Shaders {
      private Shaders() {}

      @SubscribeEvent
      public static void onShaders(RegisterShadersEvent event) {
         try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("tfclient", "crate_particle"),
                    DefaultVertexFormat.PARTICLE), s -> shader = s);
         } catch (IOException e) {
            TFClient.LOGGER.error("TF Crates: no se pudo cargar crate_particle (las partículas van con el shader del juego)", e);
         }
      }
   }
}
