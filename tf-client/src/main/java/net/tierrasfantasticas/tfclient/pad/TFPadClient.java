/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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

    /** Datos de una app con página propia (Gachapón, TF Pass): si es la que está abierta, los recibe. */
    static void data(String app, String json) {
        if (Minecraft.getInstance().screen instanceof TFPadScreen pad && pad.page() instanceof PadDataPage p && p.app.equals(app)) p.set(json);
    }

    static void view(PadView v) {
        if (Minecraft.getInstance().screen instanceof TFPadScreen pad) pad.view(v);
    }

    /** Un aviso del servidor: dentro del pad si está abierto; si no, una línea en el chat (nada encima de la barra). */
    static void notice(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TFPadScreen pad) {
            pad.showNotice(text);
        } else if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("TF Pad").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(text).withStyle(ChatFormatting.GRAY)), false);
        }
    }

    /**
     * Sonidos propios del pad (tools/pad/build_sounds.py): open, close, select, back, page, tab, hover, foto y like. Van por el
     * volumen general, sin posición (suenan igual se mire a donde se mire).
     */
    static void sound(String name, float volume) {
        sound(name, volume, 1.0F);
    }

    /** Un sonido del pad con su tono (1 = el de siempre; la ruleta sube el tic cuanto más rápido va). */
    static void sound(String name, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(new ResourceLocation(TFClient.MOD_ID, "pad." + name),
                SoundSource.MASTER, volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
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

    /** El pad de administrador (desde su objeto). */
    public static void openAdmin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.screen != null) return;
        if (!TFPadNet.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) return;
        TFPadNet.CHANNEL.sendToServer(new TFPadNet.Hello());
        PadSettings.load();
        if (PadSettings.sounds) sound("open", 0.7F * PadSettings.volume / 80F);
        mc.setScreen(new TFPadScreen(true));
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

    /** (1.3.106) Para la prueba en el juego: escribe eso en la casilla del link de Música. */
    public static void typeForTest(String text) {
        PadMusicPage.testInput = text;
    }

    /** (1.3.109) La prueba en el juego: pulsa esa acción en la app abierta (p. ej. «tab:combate»). */
    public static void actForTest(String action) {
        if (Minecraft.getInstance().screen instanceof TFPadScreen pad && pad.page() instanceof PadViewPage v) v.actForTest(action);
        else if (Minecraft.getInstance().screen instanceof TFPadScreen pad && pad.page() instanceof PadCommunityPage c) c.actForTest(action);
    }

    /** La prueba en el juego (BossTest «pad»): la portada («») o una app, aunque haya otra pantalla abierta. */
    public static void openForTest(String app) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (app.isEmpty()) {
            TFPadNet.CHANNEL.sendToServer(new TFPadNet.Hello());
            PadSettings.load();
            mc.setScreen(new TFPadScreen());
        } else if (app.startsWith("a_")) {
            // (1.3.150) una app del pad de administrador
            int slash = app.indexOf('/');
            TFPadNet.CHANNEL.sendToServer(new TFPadNet.Hello());
            PadSettings.load();
            TFPadScreen pad = new TFPadScreen(true);
            mc.setScreen(pad);
            pad.openApp(slash < 0 ? app : app.substring(0, slash), slash < 0 ? "" : app.substring(slash + 1));
        } else {
            // («app/pestaña»: la prueba abre también una pestaña, p. ej. «efectos/combate» o la zona de una protección)
            int slash = app.indexOf('/');
            openTo(slash < 0 ? app : app.substring(0, slash), slash < 0 ? "" : app.substring(slash + 1));
        }
    }

    /** (1.3.156) Prueba «clanes»: en la paleta de colores abierta, pone ese color (0xRRGGBB) y, si save, lo guarda. */
    public static void colorForTest(int rgb, boolean save) {
        PadColorPicker.testSave = save;
        PadColorPicker.testColor = rgb;
    }

    /** (1.3.156) Prueba «clanes»: las Clan Coins que enseña la barra de arriba del pad (-1: ninguna). */
    public static long clanCoinsForTest() {
        return state == null ? -2 : state.clanCoins();
    }

    /** (1.3.156) Prueba «clanes»: qué página del pad está abierta (para el informe). */
    public static String pageForTest() {
        return Minecraft.getInstance().screen instanceof TFPadScreen pad && pad.page() != null ? pad.page().getClass().getSimpleName() : "ninguna";
    }

    /** (1.3.156) Prueba «clanes»: lo que dibuja la barra doble de la guerra (A, B). */
    public static float[] warForTest() {
        return new float[] {PadClanWarPage.testDrawnA, PadClanWarPage.testDrawnB};
    }

    /** (1.3.155) Prueba «pad»: pone ese fondo en el menú principal («tema», «c:menta», «i:castillo»…). */
    public static void wallpaperForTest(String id) {
        PadSettings.load();
        PadSettings.wallpaper = id;
    }

    /** (1.3.155) Prueba «pad»: una foto de muestra en la carpeta de la Cámara y el paso de publicar con ese marco. */
    public static void photoForTest(String frame) {
        try {
            java.nio.file.Files.createDirectories(PadCamera.dir());
            java.nio.file.Path f = PadCamera.dir().resolve("prueba_marco.png");
            try (com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(960, 540, false)) {
                for (int y = 0; y < 540; y++) {
                    for (int x = 0; x < 960; x++) {
                        int r = 60 + x * 120 / 960, gg = 120 + y * 100 / 540, b = 200 - y * 80 / 540;
                        img.setPixelRGBA(x, y, 0xFF000000 | b << 16 | gg << 8 | r);
                    }
                }
                img.writeToFile(f);
            }
            PadCamera.lastPhoto = f;
            PadCamera.flashAt = System.currentTimeMillis();
        } catch (Exception e) {
            net.tierrasfantasticas.tfclient.TFClient.LOGGER.warn("Prueba: no se pudo hacer la foto de muestra", e);
        }
        PadCameraPage.testFrame = frame;
    }

    /** (1.3.155) Prueba «pad»: pega un marco a una foto lisa y mira que cambie el borde y no el centro. */
    public static String frameApplyTest(String id) {
        byte[] png = PadPhotoFrames.bytes(id);
        if (png == null) return "MAL: no se encuentra el marco " + id;
        int blue = 0xFFFF6020; // ABGR
        try (com.mojang.blaze3d.platform.NativeImage photo = new com.mojang.blaze3d.platform.NativeImage(320, 180, false);
             com.mojang.blaze3d.platform.NativeImage frame = com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(png))) {
            photo.fillRect(0, 0, 320, 180, blue);
            PadPhotoFrames.apply(photo, frame);
            int corner = photo.getPixelRGBA(3, 3), center = photo.getPixelRGBA(160, 90);
            boolean ok = corner != blue && center == blue;
            return id + ": borde " + Integer.toHexString(corner) + ", centro " + Integer.toHexString(center) + (ok ? " (bien: el marco se pega y el centro queda igual)" : " (MAL)")
                    + "; " + (PadPhotoFrames.all().size() - 1) + " marcos";
        } catch (Exception e) {
            return "MAL: " + e;
        }
    }

    /** (1.3.134) Prueba «tienda»: la primera canción de Música, como con el ratón encima (sus botones a la vista). */
    public static void hoverMusicForTest(boolean on) {
        PadMusicPage.testHoverFirst = on;
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
            PadCommunityClient.tick();
            Minecraft mc = Minecraft.getInstance();
            boolean hotbarKey = mc.player != null && mc.player.isCreative() && mc.options.keySaveHotbarActivator.same(KEY);
            while (KEY.consumeClick()) {
                if (mc.screen != null) continue;
                if (PadCamera.active()) {
                    continue; // en modo foto se dispara con el clic izquierdo
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
