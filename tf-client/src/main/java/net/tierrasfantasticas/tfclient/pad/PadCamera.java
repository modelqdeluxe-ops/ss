/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La cámara del pad. «Modo foto»: se esconde la interfaz para encuadrar (puedes moverte y mirar), el clic izquierdo
 * dispara (sin pegar a nada) y Esc vuelve al pad (no al menú de pausa). La foto se guarda en .minecraft/tfclient/fotos (solo en tu ordenador) y el pad vuelve a abrirse en la Cámara
 * con ella, para publicarla en Comunidad si quieres.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class PadCamera {
    private static boolean active;
    private static boolean prevHideGui;
    /**
     * (1.3.120, el dueño: «en el modo cámara agrega selfie y zoom») selfie: la cámara de delante (como F5 dos veces) con
     * el clic derecho; zoom: la rueda del ratón (de 1x a 8x); al salir, todo como estaba.
     */
    private static boolean selfie;
    private static net.minecraft.client.CameraType prevCamera;
    private static double zoom = 1.0;
    private static int shootIn = -1;
    /** Para el destello del pad al volver. */
    static long flashAt;
    static Path lastPhoto;
    /** La foto recién hecha a 960x540, para enseñarla al momento (y nítida) sin esperar a que se guarde el archivo. */
    static com.mojang.blaze3d.platform.NativeImage preview;

    private PadCamera() {}

    static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("tfclient").resolve("fotos");
    }

    /** Las fotos del jugador, de la más nueva a la más vieja. */
    static List<Path> photos() {
        List<Path> out = new ArrayList<>();
        Path d = dir();
        if (!Files.isDirectory(d)) return out;
        try (Stream<Path> s = Files.list(d)) {
            s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).forEach(out::add);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo leer la carpeta de fotos", e);
        }
        out.sort(Comparator.comparingLong((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (Exception e) {
                return 0L;
            }
        }).reversed());
        return out;
    }

    static boolean active() {
        return active;
    }

    /** Entra en modo foto (cierra el pad y esconde la interfaz). */
    static void start() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        active = true;
        prevHideGui = mc.options.hideGui;
        mc.options.hideGui = true;
        prevCamera = mc.options.getCameraType();
        selfie = false;
        zoom = 1.0;
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                Component.literal("Modo foto"), Component.literal("Clic izq.: foto · Clic der.: selfie · Rueda: zoom · Esc: volver"));
    }

    static void stop() {
        shootIn = -1;
        if (!active) return;
        active = false;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = prevHideGui;
        if (prevCamera != null) mc.options.setCameraType(prevCamera);
        selfie = false;
        zoom = 1.0;
    }

    /** Clic derecho en modo foto: selfie (la cámara de delante) o vuelta a la de antes. */
    private static void toggleSelfie() {
        Minecraft mc = Minecraft.getInstance();
        selfie = !selfie;
        mc.options.setCameraType(selfie ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                : prevCamera != null ? prevCamera : net.minecraft.client.CameraType.FIRST_PERSON);
    }

    /** La rueda en modo foto: zoom (no cambia de objeto en la barra). */
    @SubscribeEvent
    public static void onScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (!active || Minecraft.getInstance().screen != null) return;
        event.setCanceled(true);
        zoom = Math.max(1.0, Math.min(8.0, zoom * (event.getScrollDelta() > 0 ? 1.15 : 1 / 1.15)));
        if (zoom < 1.02) zoom = 1.0;
    }

    /** El zoom: el campo de visión dividido (como el catalejo). */
    @SubscribeEvent
    public static void onFov(net.minecraftforge.client.event.ViewportEvent.ComputeFov event) {
        if (!active || zoom <= 1.0 || !event.usedConfiguredFov()) return;
        event.setFOV(event.getFOV() / zoom);
    }

    /** Clic izquierdo en modo foto: no pega ni rompe; dispara. Clic derecho: selfie (no usa nada). */
    @SubscribeEvent
    public static void onMouse(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        if (!active || (event.getButton() != 0 && event.getButton() != 1) || Minecraft.getInstance().screen != null) return;
        event.setCanceled(true);
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS || shootIn >= 0) return;
        if (event.getButton() == 0) shoot();
        else toggleSelfie();
    }

    /** Fuera avisos y, dos fotogramas después, la foto. */
    static void shoot() {
        Minecraft.getInstance().getToasts().clear();
        shootIn = 2;
    }

    /** Esc en modo foto: en vez del menú de pausa, se sale del modo foto y se vuelve a la Cámara del pad. */
    @SubscribeEvent
    public static void onScreen(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        if (!active || !(event.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        event.setCanceled(true);
        stop();
        Minecraft.getInstance().tell(() -> TFPadClient.openTo("camara"));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        Minecraft mc = Minecraft.getInstance();
        // Esc (u otra ventana) sale del modo foto
        if (mc.player == null || (mc.screen != null && shootIn < 0)) stop();
    }

    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || shootIn < 0) return;
        if (shootIn-- > 0) return;
        Minecraft mc = Minecraft.getInstance();
        // si entre la C y la foto se abrió otra pantalla (Esc, chat, morir…) o se salió del mundo, no hay foto
        if (mc.player == null || mc.screen != null) {
            stop();
            return;
        }
        NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        // la vista previa, ya: recorte 16:9 centrado y reducido
        try {
            int sw = img.getWidth(), sh = img.getHeight();
            int cw = sw, ch = sw * 9 / 16;
            if (ch > sh) {
                ch = sh;
                cw = sh * 16 / 9;
            }
            NativeImage small = new NativeImage(PadCameraPage.PREVIEW_W, PadCameraPage.PREVIEW_H, false);
            img.resizeSubRectTo((sw - cw) / 2, (sh - ch) / 2, cw, ch, small);
            if (preview != null) preview.close();
            preview = small;
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo preparar la vista previa de la foto", e);
        }
        String base = "foto_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
        Path file = dir().resolve(base + ".png");
        for (int i = 2; Files.exists(file); i++) file = dir().resolve(base + "_" + i + ".png");
        Path saved = file;
        lastPhoto = saved;
        Util.ioPool().execute(() -> {
            try (img) {
                Files.createDirectories(saved.getParent());
                img.writeToFile(saved);
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Pad: no se pudo guardar la foto", e);
                mc.execute(() -> {
                    if (saved.equals(lastPhoto)) lastPhoto = null;
                });
            }
        });
        stop();
        flashAt = System.currentTimeMillis();
        PadSettings.load();
        if (PadSettings.sounds) TFPadClient.sound("foto", 0.8F * PadSettings.volume / 80F);
        // de vuelta al pad, en la Cámara, con la foto nueva
        TFPadClient.openTo("camara");
    }
}
