package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFClientEvents {
    private TFClientEvents() {}

    /** Si el fondo de TF ya se dibujó en este fotograma (algunas pantallas, como la de mundos, no piden fondo). */
    private static boolean backgroundDrawn;

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        backgroundDrawn = false;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) backgroundDrawn = false;
    }

    /** Fuera de una partida, los menús (opciones, mundos, conexión...) usan el paisaje de TF en vez de la tierra. */
    @SubscribeEvent
    public static void onBackground(ScreenEvent.BackgroundRendered event) {
        Screen screen = event.getScreen();
        if (Minecraft.getInstance().level != null || screen instanceof TitleScreen) return;
        drawMenuBackground(event.getGuiGraphics(), screen);
    }

    /**
     * Dibuja el fondo de TF si la pantalla actual no lo ha hecho todavía en este fotograma. Lo usan las listas, porque
     * pantallas como Seleccionar mundo confían en el fondo de tierra de la lista y no dibujan nada detrás.
     */
    public static void ensureMenuBackground(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (backgroundDrawn || mc.level != null || screen == null || screen instanceof TitleScreen) return;
        drawMenuBackground(g, screen);
    }

    private static void drawMenuBackground(GuiGraphics g, Screen screen) {
        try {
            // El mismo cielo animado que el menú principal, algo más oscuro para que se lean las opciones
            TFSky.render(g, screen.width, screen.height, 0.3f);
            backgroundDrawn = true;
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Client: error al dibujar el fondo", t);
        }
    }

    /** La música de TF suena fuera de partida (carga, menús, conexión) y se apaga al entrar a un mundo. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().level == null) TFMusic.play();
        else TFMusic.stop();
    }
}
