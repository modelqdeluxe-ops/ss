package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.lwjgl.glfw.GLFW;

/**
 * El TF Pad en el cliente: la tecla para abrirlo (C por defecto, se cambia en Controles), sus sonidos y los últimos
 * datos que mandó el servidor. Solo se abre en un servidor con el TF Client (el que tiene el canal del pad).
 */
public final class TFPadClient {
    public static final KeyMapping KEY = new KeyMapping("key.tfclient.pad", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.tfclient");

    /** Lo último que llegó del servidor (null hasta la primera respuesta). */
    static TFPadNet.State state;

    private TFPadClient() {}

    static void receive(TFPadNet.State s) {
        state = s;
    }

    /** Un aviso del servidor: dentro del pad si está abierto; si no, encima de la barra rápida. */
    static void notice(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TFPadScreen pad) {
            pad.showNotice(text);
        } else if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /**
     * Sonidos propios del pad (tools/pad/build_sounds.py): open, close, select, back, page y hover. Van por el
     * volumen general, sin posición (suenan igual se mire a donde se mire).
     */
    static void sound(String name, float volume) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(new ResourceLocation(TFClient.MOD_ID, "pad." + name),
                SoundSource.MASTER, volume, 1.0F, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    /** Abre el pad (y pide al servidor los datos al día). */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (!TFPadNet.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) {
            mc.player.displayClientMessage(Component.literal("El pad solo funciona en el servidor de Tierras Fantásticas.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        TFPadNet.CHANNEL.sendToServer(new TFPadNet.Hello());
        sound("open", 0.9F);
        mc.setScreen(new TFPadScreen());
    }

    static void openServerApp(String app) {
        TFPadNet.CHANNEL.sendToServer(new TFPadNet.Open(app));
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (KEY.consumeClick()) {
                if (mc.screen == null) open();
            }
        }
    }
}
