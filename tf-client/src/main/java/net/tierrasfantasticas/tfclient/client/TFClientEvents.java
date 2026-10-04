package net.tierrasfantasticas.tfclient.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFClientEvents {
    private TFClientEvents() {}

    /** Cambia el menú principal de Minecraft por el de TF. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen) {
            event.setNewScreen(new TFTitleScreen());
        }
    }

    /** Fuera de una partida, los menús (opciones, mundos, conexión...) usan el fondo de TF en vez de tierra. */
    @SubscribeEvent
    public static void onBackground(ScreenEvent.BackgroundRendered event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (mc.level != null || screen instanceof TFTitleScreen) return;
        GuiGraphics g = event.getGuiGraphics();
        TFDraw.background(g, screen.width, screen.height, 1f, Util.getMillis(), -1, -1, 0.62f);
    }
}
