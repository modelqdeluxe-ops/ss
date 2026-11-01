/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFItems;
import net.tierrasfantasticas.tfclient.pad.PadSpeakerNet;
import net.tierrasfantasticas.tfclient.pad.SharedDiscItem;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;

/**
 * (1.3.134) Compartir una canción de la app Música con un disco (SharedDiscItem). El que comparte pulsa el botón de la
 * canción y el cliente manda su link (Share): uno de internet, o tfaudio:&lt;sha1&gt; si es de su carpeta. Con un link,
 * el disco sale al momento; con una de la carpeta, el servidor la pide (Need) si no la tiene, la recibe a trozos (como
 * la del altavoz, PadSpeakers.uploaded) y da el disco cuando la tiene entera. Las canciones de los discos se guardan
 * en &lt;mundo&gt;/tfclient/compartidas/ (hasta MAX_STORED; se borran las que hace más que nadie pide), aparte de las
 * del altavoz (que se borran antes), para que quien gaste el disco días después la pueda bajar.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadMusicShare {
    /** Lo que se guarda como mucho de las canciones de los discos. */
    private static final long MAX_STORED = 2L * 1024 * 1024 * 1024;
    /** Un disco cada 3 s como mucho por jugador. */
    private static final long COOLDOWN_MS = 3000;

    private record Pending(String hash, PadSpeakerNet.Share song, long since) {}

    /** El que comparte una canción de su carpeta que el servidor aún no tiene: lo que espera, por jugador. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private PadMusicShare() {}

    /** El botón de compartir de una canción. */
    public static void share(ServerPlayer p, PadSpeakerNet.Share m) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN_MS) return;
        String url = m.url().trim();
        boolean local = PadSpeakers.isLocal(url);
        if (!(url.startsWith("https://") || url.startsWith("http://") || local) || url.length() > 500) {
            TFPadNet.notice(p, "Esa canción no se puede compartir.");
            return;
        }
        LAST.put(p.getUUID(), now);
        if (!local) {
            give(p, m);
            return;
        }
        String hash = url.substring(PadSpeakerNet.LOCAL.length());
        if (keep(hash)) {
            give(p, m);
            return;
        }
        // de su carpeta y el servidor no la tiene: que la suba (el disco sale cuando llegue entera)
        PENDING.put(p.getUUID(), new Pending(hash, m, now));
        PadSpeakers.askUpload(p, hash);
        TFPadNet.notice(p, "Preparando el disco de «" + m.title() + "»…");
    }

    /** ¿Espera este jugador a subir esta canción para su disco? (PadSpeakers deja pasar sus trozos) */
    static boolean waiting(ServerPlayer p, String hash) {
        Pending w = PENDING.get(p.getUUID());
        return w != null && w.hash.equals(hash) && System.currentTimeMillis() - w.since < 10 * 60_000;
    }

    /** Llegó entera la canción que subía p: si era para un disco, se guarda aparte y sale el disco. */
    static void uploaded(ServerPlayer p, String hash) {
        Pending w = PENDING.get(p.getUUID());
        if (w == null || !w.hash.equals(hash)) return;
        PENDING.remove(p.getUUID());
        if (keep(hash)) give(p, w.song);
        else TFPadNet.notice(p, "No se pudo guardar la canción en el servidor. Inténtalo otra vez.");
    }

    /** La carpeta de las canciones de los discos. */
    static Path dir() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null
                : server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("tfclient").resolve("compartidas");
    }

    /** La canción guardada para los discos, o null. */
    static Path stored(String hash) {
        Path d = dir();
        if (d == null) return null;
        Path f = d.resolve(hash + ".audio");
        return Files.exists(f) ? f : null;
    }

    /** Se asegura de tener la canción entre las de los discos (la copia de las del altavoz si hace falta). */
    static boolean keep(String hash) {
        if (stored(hash) != null) return true;
        Path from = PadSpeakers.speakerFile(hash), d = dir();
        if (from == null || d == null || !Files.exists(from)) return false;
        try {
            Files.createDirectories(d);
            Files.copy(from, d.resolve(hash + ".audio"));
            trim();
            return true;
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo guardar la canción de un disco", e);
            return false;
        }
    }

    /** El disco, en la mano si está vacía (si no, al inventario; lleno, al suelo). */
    private static void give(ServerPlayer p, PadSpeakerNet.Share m) {
        ItemStack disc = SharedDiscItem.of(TFItems.SHARED_DISC.get(), m.url().trim(), cut(m.title(), 60), cut(m.artist(), 60),
                m.durationMs(), p.getGameProfile().getName());
        if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, disc);
        else if (!p.getInventory().add(disc)) p.drop(disc, false);
        p.inventoryMenu.broadcastChanges();
        p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.MASTER, 0.8F, 1.3F);
        TFPadNet.notice(p, "Disco de «" + cut(m.title(), 40) + "» listo: dáselo a quien quieras; con clic derecho se la guarda.");
        TFClient.LOGGER.info("TF Pad: {} comparte la canción «{}» en un disco", p.getGameProfile().getName(), m.title());
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    /** Lo guardado no pasa de MAX_STORED: se borran las que hace más que nadie pide. */
    private static void trim() {
        Path d = dir();
        if (d == null) return;
        try (java.util.stream.Stream<Path> s = Files.list(d)) {
            java.util.List<java.io.File> files = new java.util.ArrayList<>(s.map(Path::toFile).filter(f -> f.getName().endsWith(".audio")).toList());
            files.sort(java.util.Comparator.comparingLong(java.io.File::lastModified));
            long total = 0;
            for (java.io.File f : files) total += f.length();
            for (java.io.File f : files) {
                if (total <= MAX_STORED) break;
                total -= f.length();
                f.delete();
            }
        } catch (Exception ignored) {
            // se intenta la próxima vez
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
    }
}
