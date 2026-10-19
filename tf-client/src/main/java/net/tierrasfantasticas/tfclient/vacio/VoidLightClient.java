package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Tu luz en el Void, en pantalla (1.3.96, {@link VoidLight}): arriba a la izquierda, «LUZ DEL ALBA» y el número con su
 * orbe y una barra dorada (en el Velo del Alba, «a salvo» y se llena; fuera, se vacía); los Ecos del Alba que llevas. Con poca luz,
 * los bordes de la pantalla se oscurecen de violeta y entran zarcillos (más cuanto menos luz; sin luz, laten). Al coger un
 * fragmento, un destello dorado; al cruzar el Velo, su sonido.
 */
@OnlyIn(Dist.CLIENT)
public final class VoidLightClient {
    private static final ResourceLocation ORB = new ResourceLocation(TFClient.MOD_ID, "textures/gui/luz_orbe.png");
    private static final ResourceLocation ORB_OFF = new ResourceLocation(TFClient.MOD_ID, "textures/gui/luz_orbe_apagado.png");
    private static final ResourceLocation VIGNETTE = new ResourceLocation(TFClient.MOD_ID, "textures/gui/luz_vinieta.png");
    private static final SoundEvent S_ENTER = sound("luz.velo_entrar"), S_LEAVE = sound("luz.velo_salir"), S_EMPTY = sound("luz.sin_luz");
    private static final int GOLD = 0xFFF2C14F, GOLD_HI = 0xFFFFE7A0, TEXT = 0xFFF1ECFF, DIM = 0xFF9A8CC0;

    /** Lo que dijo el servidor (luz 0..100) y lo que se enseña (se mueve suave hacia ella). */
    private static float light = VoidLight.MAX, shown = VoidLight.MAX;
    private static boolean inside, has;
    private static int echoes, echoesTotal;
    private static long flashAt, lastFrame;

    private VoidLightClient() {}

    private static SoundEvent sound(String id) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(TFClient.MOD_ID, id));
    }

    static void receive(VoidNet.Light m) {
        light = m.light();
        inside = m.inside();
        echoes = m.echoes();
        echoesTotal = m.echoesTotal();
        if (!has) shown = light;
        has = true;
        switch (m.event()) {
            case VoidLight.EV_SHARD -> flashAt = System.currentTimeMillis();
            case VoidLight.EV_ENTER -> play(S_ENTER, 0.8F);
            case VoidLight.EV_LEAVE -> play(S_LEAVE, 0.7F);
            case VoidLight.EV_EMPTY -> play(S_EMPTY, 1F);
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

    /** Los Ecos del Alba que lleva este jugador (para el caballero del santuario). */
    static int echoes() {
        return echoes;
    }

    /** Para la prueba en el juego: lo que ve el cliente. */
    public static String describe() {
        return has ? String.format("luz %.0f%s, ecos %d/%d", light, inside ? " (en el Velo del Alba)" : "", echoes, echoesTotal) : "sin datos";
    }

    static void draw(GuiGraphics g, int width, int height, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0F : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        shown += (light - shown) * Math.min(1F, dt * 6F);
        float k = shown / VoidLight.MAX;
        float t = (now % 100_000L) / 1000F;
        // los bordes que se oscurecen (con poca luz; sin luz, laten)
        float dark = Mth.clamp((0.32F - k) / 0.32F, 0F, 1F);
        if (light <= 0.5F) dark = 0.85F + 0.15F * (float) Math.sin(t * 3.2);
        if (dark > 0.01F && !inside) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1F, 1F, 1F, dark);
            g.blit(VIGNETTE, 0, 0, width, height, 0F, 0F, 256, 144, 256, 144);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
        // el destello dorado al coger un fragmento
        float fl = 1F - (now - flashAt) / 700F;
        if (fl > 0F) {
            int a = (int) (Mth.clamp(fl, 0F, 1F) * 0.35F * 255);
            g.fill(0, 0, width, height, a << 24 | 0xFFE7A0);
        }
        // el panel: orbe, nombre y número, barra, ecos y (en el Velo) «a salvo». Compacto: con la interfaz pequeña, la
        // barra de un jefe empieza a ~110 px del borde y el nombre largo de antes se le montaba encima (prueba 1.3.96)
        int x = 6, y = 6;
        float pulse = inside ? 1F : 0.75F + 0.25F * (float) Math.sin(t * (2F + 6F * (1F - k)));
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, k > 0.02F ? Mth.clamp(0.45F + 0.55F * k, 0F, 1F) * pulse : 1F);
        g.blit(k > 0.02F ? ORB : ORB_OFF, x, y, 18, 18, 0F, 0F, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        int tx = x + 22;
        String label = "LUZ DEL ALBA";
        g.drawString(mc.font, label, tx, y + 1, inside ? GOLD_HI : (k < 0.25F ? 0xFFE0647A : GOLD), true);
        g.drawString(mc.font, Math.round(shown) + "", tx + mc.font.width(label) + 5, y + 1, TEXT, true);
        int bx = tx, by = y + 11, bw = 84, bh = 4;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF120A1E);
        g.fill(bx, by, bx + bw, by + bh, 0xFF2A1F3D);
        int fw = Math.round(bw * Mth.clamp(k, 0F, 1F));
        if (fw > 0) {
            g.fillGradient(bx, by, bx + fw, by + bh, k < 0.25F ? 0xFFE0647A : GOLD_HI, k < 0.25F ? 0xFF9A2F4A : 0xFFD08A2A);
            // un brillo que corre por la barra
            int sx = bx + (int) ((t * 40F) % (bw + 20)) - 10;
            if (sx > bx && sx < bx + fw - 2) g.fill(sx, by, sx + 2, by + bh, 0x80FFFFFF);
        }
        int ly = by + bh + 4;
        if (echoesTotal > 0) {
            g.drawString(mc.font, "Ecos del Alba " + echoes + "/" + echoesTotal, bx, ly, DIM, true);
            ly += 10;
        }
        if (inside) g.drawString(mc.font, "A salvo en el Velo", bx, ly, GOLD_HI, true);
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "tf_luz_del_alba", (gui, g, partial, width, height) -> draw(g, width, height, partial));
        }
    }
}
