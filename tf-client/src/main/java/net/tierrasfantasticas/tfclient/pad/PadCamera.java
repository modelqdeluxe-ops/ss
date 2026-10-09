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
    private static int shootIn = -1;
    /** Para el destello del pad al volver. */
    static long flashAt;
    static Path lastPhoto;
    /** La foto recién hecha en pequeño (320x180), para enseñarla al momento sin esperar a que se guarde el archivo. */
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
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                Component.literal("Modo foto"), Component.literal("Clic izquierdo: foto · Esc: volver al pad"));
    }

    static void stop() {
        shootIn = -1;
        if (!active) return;
        active = false;
        Minecraft.getInstance().options.hideGui = prevHideGui;
    }

    /** Clic izquierdo en modo foto: no pega ni rompe; dispara. */
    @SubscribeEvent
    public static void onMouse(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        if (!active || event.getButton() != 0 || Minecraft.getInstance().screen != null) return;
        event.setCanceled(true);
        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS && shootIn < 0) shoot();
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
            NativeImage small = new NativeImage(320, 180, false);
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
