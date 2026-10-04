package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
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

    /** Fuera de una partida, los menús (opciones, mundos, conexión...) usan el paisaje de TF en vez de la tierra. */
    @SubscribeEvent
    public static void onBackground(ScreenEvent.BackgroundRendered event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (mc.level != null || screen instanceof TitleScreen) return;
        try {
            if (TFDraw.cover(event.getGuiGraphics(), TFTextures.MENU, screen.width, screen.height, 1f)) {
                event.getGuiGraphics().fill(0, 0, screen.width, screen.height, TFDraw.argb(0.45f, 0x000000));
            }
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
