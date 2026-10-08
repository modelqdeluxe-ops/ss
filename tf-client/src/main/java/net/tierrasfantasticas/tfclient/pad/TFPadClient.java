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
    /**
     * En creativo, C + número es «guardar barra rápida» de vanilla: ahí el pad se abre al SOLTAR la C, y solo si no
     * se pulsó un número mientras tanto.
     */
    private static boolean waitingRelease;
    private static boolean usedForHotbar;

    private TFPadClient() {}

    static void receive(TFPadNet.State s) {
        state = s;
    }

    static void closePad() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TFPadScreen) mc.setScreen(null);
    }

    static void view(PadView v) {
        if (Minecraft.getInstance().screen instanceof TFPadScreen pad) pad.view(v);
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
     * Sonidos propios del pad (tools/pad/build_sounds.py): open, close, select, back, page, tab, hover, foto y like. Van por el
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
        PadSettings.load();
        if (PadSettings.sounds) sound("open", 0.7F * PadSettings.volume / 80F);
        mc.setScreen(new TFPadScreen());
    }

    /** Abre el pad directamente en una app (por ejemplo, la Cámara después de una foto, o la Protección al pulsar
     *  una piedra). Si el pad ya está abierto, cambia de app sin cerrarlo. */
    static void openTo(String app, String tab) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.screen instanceof TFPadScreen open) {
            open.openApp(app, tab);
            return;
        }
        TFPadNet.CHANNEL.sendToServer(new TFPadNet.Hello());
        PadSettings.load();
        if (PadSettings.sounds) sound("open", 0.7F * PadSettings.volume / 80F);
        TFPadScreen pad = new TFPadScreen();
        mc.setScreen(pad);
        pad.openApp(app, tab);
    }

    static void openTo(String app) {
        openTo(app, "");
    }

    static void openServerApp(String app, String tab) {
        TFPadNet.CHANNEL.sendToServer(new TFPadNet.Open(app, tab == null ? "" : tab));
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
            boolean hotbarKey = mc.player != null && mc.player.isCreative() && mc.options.keySaveHotbarActivator.same(KEY);
            while (KEY.consumeClick()) {
                if (mc.screen != null) continue;
                if (PadCamera.active()) {
                    PadCamera.shoot();
                } else if (hotbarKey) {
                    waitingRelease = true;
                    usedForHotbar = false;
                } else {
                    open();
                }
            }
            if (waitingRelease) {
                for (KeyMapping slot : mc.options.keyHotbarSlots) {
                    if (slot.isDown()) usedForHotbar = true;
                }
                if (!KEY.isDown()) {
                    waitingRelease = false;
                    if (!usedForHotbar && mc.screen == null) open();
                }
            }
        }

        /** Al salir de un servidor se olvidan sus datos (si no, el pad del siguiente enseñaría las monedas de este). */
        @SubscribeEvent
        public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            state = null;
            waitingRelease = false;
            PadCamera.stop();
            PadCommunityClient.clear();
        }
    }
}
