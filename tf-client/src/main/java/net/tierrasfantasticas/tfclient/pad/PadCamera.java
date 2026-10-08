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
 * La cámara del pad. «Modo foto»: se esconde la interfaz para encuadrar (puedes moverte y mirar), la C dispara y Esc
 * sale. La foto se guarda en .minecraft/tfclient/fotos (solo en tu ordenador) y el pad vuelve a abrirse en la Cámara
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
                Component.literal("Modo foto"), Component.literal("C: hacer la foto · Esc: salir"));
    }

    static void stop() {
        if (!active) return;
        active = false;
        Minecraft.getInstance().options.hideGui = prevHideGui;
    }

    /** La C en modo foto: fuera avisos y, dos fotogramas después, la foto. */
    static void shoot() {
        Minecraft.getInstance().getToasts().clear();
        shootIn = 2;
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
        NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        String name = "foto_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".png";
        Path file = dir().resolve(name);
        lastPhoto = file;
        Util.ioPool().execute(() -> {
            try (img) {
                Files.createDirectories(file.getParent());
                img.writeToFile(file);
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Pad: no se pudo guardar la foto", e);
            }
        });
        stop();
        flashAt = System.currentTimeMillis();
        TFPadClient.sound("foto", 0.9F);
        // de vuelta al pad, en la Cámara, con la foto nueva
        TFPadClient.openTo("camara");
    }
}
