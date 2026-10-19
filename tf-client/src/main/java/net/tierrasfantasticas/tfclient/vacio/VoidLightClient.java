package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La corrosión del Vacío en pantalla (1.3.96, {@link VoidLight}; el dueño: «no quiero un contador en pantalla, más bien
 * un efecto de corrosión que en su máximo punto no sea molesto»). Sin números ni barras:
 * <ul>
 *   <li>venas de sombra violeta que entran desde los bordes de la pantalla y crecen con la corrosión (como mucho a la
 *   mitad de opacidad: se sigue viendo todo); al máximo laten despacio y, al hacer daño, se oscurecen un momento;</li>
 *   <li>alrededor del jugador, con mucha corrosión, motas violetas que suben (pocas);</li>
 *   <li>en un sitio de luz (un Pozo o el Velo del Alba), un resplandor dorado en los bordes mientras limpia, y un destello
 *   al entrar, con su sonido; al cruzar el Portal del Alba, un fundido dorado.</li>
 * </ul>
 * Solo en el Void y jugando (no en creativo ni en espectador).
 */
@OnlyIn(Dist.CLIENT)
public final class VoidLightClient {
    private static final ResourceLocation VEINS = new ResourceLocation(TFClient.MOD_ID, "textures/gui/luz_vinieta.png");
    private static final ResourceLocation GOLD = new ResourceLocation(TFClient.MOD_ID, "textures/gui/luz_vinieta_oro.png");
    private static final SoundEvent S_ENTER = sound("luz.velo_entrar"), S_LEAVE = sound("luz.velo_salir"), S_EMPTY = sound("luz.sin_luz"),
            S_PORTAL = sound("viaje.llegada");

    /** Lo que dijo el servidor (luz 0..100: 100 sin corrosión) y lo que se enseña (se mueve suave hacia ella). */
    private static float light = VoidLight.MAX, shown = VoidLight.MAX;
    private static boolean safe, has;
    private static long goldAt, hurtAt, portalAt = -1, lastFrame;

    private VoidLightClient() {}

    private static SoundEvent sound(String id) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(TFClient.MOD_ID, id));
    }

    static void receive(VoidNet.Light m) {
        light = m.light();
        safe = m.safe();
        if (!has) shown = light;
        has = true;
        long now = System.currentTimeMillis();
        switch (m.event()) {
            case VoidLight.EV_WELL, VoidLight.EV_ENTER -> {
                goldAt = now;
                play(S_ENTER, m.event() == VoidLight.EV_WELL ? 0.6F : 0.8F);
            }
            case VoidLight.EV_LEAVE -> play(S_LEAVE, 0.5F);
            case VoidLight.EV_EMPTY -> play(S_EMPTY, 0.9F);
            case VoidLight.EV_HURT -> hurtAt = now;
            case VoidLight.EV_PORTAL -> {
                portalAt = now;
                play(S_PORTAL, 0.8F);
            }
            default -> {
            }
        }
    }

    private static void play(SoundEvent s, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, 1F, volume));
    }

    /** ¿Se enseña? (en el Void, jugando: no en creativo ni en espectador) */
    private static boolean visible(Minecraft mc) {
        return has && mc.level != null && mc.player != null && TFVoid.isVoid(mc.level) && !mc.player.isCreative()
                && !mc.player.isSpectator() && !mc.options.hideGui;
    }

    /** Para la prueba en el juego: lo que ve el cliente. */
    public static String describe() {
        return has ? String.format("corrosión %.0f %%%s", VoidLight.MAX - light, safe ? " (en la luz)" : "") : "sin datos";
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    static void draw(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        // el fundido del portal (también si ya no está en el Void: dura 1,6 s)
        if (portalAt >= 0) {
            float t = (now - portalAt) / 1600F;
            if (t >= 1F) {
                portalAt = -1;
            } else {
                float a = (t < 0.45F ? t / 0.45F : (1F - t) / 0.55F) * 0.92F;
                g.fill(0, 0, width, height, ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | 0xFFF1D2);
            }
        }
        if (!visible(mc)) return;
        float dt = lastFrame == 0 ? 0F : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        shown += (light - shown) * Math.min(1F, dt * 4F);
        float c = 1F - shown / VoidLight.MAX;
        float t = (now % 100_000L) / 1000F;
        // las venas de sombra (como mucho a la mitad: se sigue viendo)
        float veins = smooth(0.04F, 1F, c) * 0.5F;
        if (c > 0.97F) veins += 0.07F * (0.5F + 0.5F * (float) Math.sin(t * 2.4));
        float hurt = 1F - (now - hurtAt) / 600F;
        if (hurt > 0F) veins += 0.18F * hurt;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (veins > 0.01F) {
            RenderSystem.setShaderColor(1F, 1F, 1F, Mth.clamp(veins, 0F, 0.75F));
            g.blit(VEINS, 0, 0, width, height, 0F, 0F, 256, 144, 256, 144);
        }
        // el resplandor dorado: mientras limpia y al entrar en la luz
        float gold = 0F;
        if (safe && shown < light - 0.5F) gold = 0.22F;
        float flash = 1F - (now - goldAt) / 1300F;
        if (flash > 0F) gold = Math.max(gold, 0.45F * flash);
        if (gold > 0.01F) {
            RenderSystem.setShaderColor(1F, 1F, 1F, gold);
            g.blit(GOLD, 0, 0, width, height, 0F, 0F, 256, 144, 256, 144);
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** Motas violetas que suben alrededor del jugador cuando la corrosión es alta (pocas). */
    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (!visible(mc) || mc.isPaused()) return;
            float c = 1F - shown / VoidLight.MAX;
            if (c < 0.5F || mc.level.getGameTime() % Math.max(2, (int) (10 - 8 * c)) != 0) return;
            var p = mc.player;
            var r = mc.level.random;
            mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, p.getX() + (r.nextDouble() - 0.5) * 1.2, p.getY() + r.nextDouble() * 1.6,
                    p.getZ() + (r.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
        }
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "tf_corrosion_vacio", (gui, g, partial, width, height) -> draw(g, width, height));
        }
    }
}
