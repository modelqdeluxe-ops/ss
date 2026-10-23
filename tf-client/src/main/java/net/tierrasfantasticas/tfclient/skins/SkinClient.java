/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.skins;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Las skins del Armario en el cliente. Sabe qué skin lleva cada jugador (SkinNet.Active) y la dibuja en su lugar
 * (mixins de AbstractClientPlayer y PlayerInfo: textura y brazos); cada imagen se guarda por su huella en memoria
 * (una textura «tfclient:skins/&lt;huella&gt;» y sus dos vistas previas «tfclient:skinprev/&lt;huella&gt;_n|_f» para el
 * pad) y en .minecraft/tfclient/cache/skins, para no pedirla otra vez. También es la carpeta del jugador:
 * .minecraft/tfclient/skins (de donde se importan los PNG) y skins/exportadas (donde se guardan las exportadas).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class SkinClient {
    private record Active(String hash, boolean slim) {}

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    /** Huellas con sus texturas ya creadas. */
    private static final Set<String> LOADED = ConcurrentHashMap.newKeySet();
    /** Huellas pedidas al servidor (para no pedirlas dos veces). */
    private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();
    /** Las skins de la carpeta del jugador de la última lista (huella → PNG ya normalizado). */
    private static final Map<String, byte[]> LOCAL = new HashMap<>();
    private static final int CACHE_MAX = 300;

    private SkinClient() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que preguntan los mixins y el pad
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Mientras es true, los mixins de skin no cambian nada: así se lee la skin de la cuenta del jugador (la de siempre,
     * o la de Steve/Alex) aunque lleve otra del Armario. Solo en el hilo de dibujo.
     */
    public static boolean bypass;

    /** La textura de una skin del Armario por su huella, o null si aún no ha llegado. */
    public static ResourceLocation textureOf(String hash) {
        return hash != null && LOADED.contains(hash) ? skin(hash) : null;
    }

    /** La textura de la skin que lleva ese jugador, o null (la suya de siempre o aún sin cargar). */
    public static ResourceLocation texture(UUID player) {
        if (bypass) return null;
        Active a = player == null ? null : ACTIVE.get(player);
        return a != null && LOADED.contains(a.hash) ? skin(a.hash) : null;
    }

    /** «slim» o «default» si lleva una skin del Armario (ya cargada); null si no. */
    public static String model(UUID player) {
        if (bypass) return null;
        Active a = player == null ? null : ACTIVE.get(player);
        return a != null && LOADED.contains(a.hash) ? a.slim ? "slim" : "default" : null;
    }

    /**
     * Para el pad (TFPadScreen.size): las vistas previas son texturas en memoria, no recursos. null si loc no es una
     * de ellas; {0, 0} si aún no ha llegado su imagen (el pad dibuja el objeto mientras).
     */
    public static int[] previewSize(ResourceLocation loc) {
        if (!loc.getNamespace().equals(TFClient.MOD_ID) || !loc.getPath().startsWith("skinprev/")) return null;
        String p = loc.getPath().substring("skinprev/".length());
        String hash = p.length() >= 40 ? p.substring(0, 40) : p;
        return LOADED.contains(hash) ? new int[] {SkinPreview.SIZE, SkinPreview.SIZE} : new int[] {0, 0};
    }

    private static ResourceLocation skin(String hash) {
        return new ResourceLocation(TFClient.MOD_ID, "skins/" + hash);
    }

    private static ResourceLocation preview(String hash, boolean slim) {
        return new ResourceLocation(TFClient.MOD_ID, "skinprev/" + hash + (slim ? "_f" : "_n"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Red
    // ---------------------------------------------------------------------------------------------------------------

    static void active(UUID player, String hash, boolean slim) {
        if (!SkinImages.validHash(hash)) {
            ACTIVE.remove(player);
            return;
        }
        ACTIVE.put(player, new Active(hash, slim));
        ensure(hash);
    }

    private static void ensure(String hash) {
        if (LOADED.contains(hash)) return;
        byte[] cached = readCache(hash);
        if (cached != null && SkinImages.hash(cached).equals(hash)) {
            load(hash, cached);
            return;
        }
        if (ASKED.add(hash)) SkinNet.CHANNEL.sendToServer(new SkinNet.Want(hash));
    }

    static void png(String hash, byte[] png) {
        if (!SkinImages.validHash(hash) || png.length > SkinImages.MAX_PNG || !SkinImages.hash(png).equals(hash)) return;
        ASKED.remove(hash);
        if (LOADED.contains(hash)) return;
        writeCache(hash, png);
        load(hash, png);
    }

    /** Crea la textura de la skin y sus dos vistas previas (brazos normales y finos). */
    private static void load(String hash, byte[] png) {
        if (LOADED.contains(hash)) return;
        NativeImage img = null;
        try {
            img = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(png));
            if (img.getWidth() != 64 || img.getHeight() != 64) {
                img.close();
                return;
            }
            var tm = Minecraft.getInstance().getTextureManager();
            tm.register(preview(hash, false), new DynamicTexture(SkinPreview.compose(img, false)));
            tm.register(preview(hash, true), new DynamicTexture(SkinPreview.compose(img, true)));
            tm.register(skin(hash), new DynamicTexture(img));
            LOADED.add(hash);
        } catch (Exception e) {
            if (img != null) img.close();
            TFClient.LOGGER.warn("TF Skins: no se pudo cargar la skin {}: {}", hash, e.toString());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La carpeta del jugador
    // ---------------------------------------------------------------------------------------------------------------

    /** .minecraft/tfclient/skins (y skins/exportadas). */
    public static Path folder(boolean exports) {
        Path dir = FMLPaths.GAMEDIR.get().resolve("tfclient").resolve("skins");
        return exports ? dir.resolve("exportadas") : dir;
    }

    private record Found(List<SkinNet.Local> list, Map<String, byte[]> pngs, int bad) {}

    /** El servidor pide la lista: se mira la carpeta en otro hilo y se contesta desde el del juego. */
    static void scan() {
        CompletableFuture.supplyAsync(SkinClient::look, Util.backgroundExecutor()).whenComplete((found, error) ->
                Minecraft.getInstance().execute(() -> {
                    Found f = found != null ? found : new Found(List.of(), Map.of(), 0);
                    LOCAL.clear();
                    LOCAL.putAll(f.pngs);
                    f.pngs.forEach(SkinClient::load);
                    if (Minecraft.getInstance().getConnection() != null) SkinNet.CHANNEL.sendToServer(new SkinNet.LocalList(f.list, f.bad));
                }));
    }

    private static Found look() {
        Path dir = folder(false);
        List<SkinNet.Local> list = new ArrayList<>();
        Map<String, byte[]> pngs = new HashMap<>();
        int bad = 0;
        List<Path> files;
        try {
            Files.createDirectories(dir);
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).limit(100).toList();
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Skins: no se pudo mirar {}: {}", dir, e.toString());
            return new Found(list, pngs, 0);
        }
        for (Path f : files) {
            if (list.size() >= SkinNet.MAX_LOCAL) break;
            try {
                if (Files.size(f) > SkinImages.MAX_INPUT) {
                    bad++;
                    continue;
                }
                SkinImages.Result r = SkinImages.normalize(Files.readAllBytes(f));
                if (pngs.containsKey(r.hash())) continue;
                String name = f.getFileName().toString();
                name = name.substring(0, name.length() - 4);
                String low = name.toLowerCase(Locale.ROOT);
                boolean slim = r.slimGuess() || low.contains("slim") || low.contains("alex");
                pngs.put(r.hash(), r.png());
                list.add(new SkinNet.Local(name, r.hash(), slim));
            } catch (SkinImages.Bad | java.io.IOException e) {
                bad++;
            }
        }
        return new Found(list, pngs, bad);
    }

    /** El servidor pide el PNG de una de la carpeta (la que eligió el jugador en el pad). */
    static void upload(String hash) {
        byte[] png = LOCAL.get(hash);
        if (png != null) SkinNet.CHANNEL.sendToServer(new SkinNet.Upload(hash, png));
    }

    /** Guarda una skin en skins/exportadas/&lt;file&gt;.png y abre la carpeta. */
    static void export(String file, byte[] png) {
        String safe = file.replaceAll("[\\\\/:*?\"<>|]", "_").replace("..", "_").trim();
        if (safe.isEmpty()) safe = "skin";
        Path dir = folder(true);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(safe + ".png"), png);
            Util.getPlatform().openFile(dir.toFile());
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Skins: no se pudo exportar {}: {}", safe, e.toString());
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.literal("No se pudo guardar la skin en " + dir + ".").withStyle(ChatFormatting.RED), false);
            }
        }
    }

    static void openFolder(boolean exports) {
        Path dir = folder(exports);
        try {
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toFile());
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Skins: no se pudo abrir {}: {}", dir, e.toString());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Caché en disco (.minecraft/tfclient/cache/skins/<huella>.png)
    // ---------------------------------------------------------------------------------------------------------------

    private static Path cacheDir() {
        return FMLPaths.GAMEDIR.get().resolve("tfclient").resolve("cache").resolve("skins");
    }

    private static byte[] readCache(String hash) {
        Path p = cacheDir().resolve(hash + ".png");
        try {
            return Files.isRegularFile(p) && Files.size(p) <= SkinImages.MAX_PNG ? Files.readAllBytes(p) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCache(String hash, byte[] png) {
        Path dir = cacheDir();
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(hash + ".png"), png);
            List<Path> all;
            try (Stream<Path> s = Files.list(dir)) {
                all = s.filter(Files::isRegularFile).toList();
            }
            if (all.size() <= CACHE_MAX) return;
            // las más viejas fuera
            List<Path> old = new ArrayList<>(all);
            old.sort(Comparator.comparingLong(p -> p.toFile().lastModified()));
            for (int i = 0; i < old.size() - CACHE_MAX + 50 && i < old.size(); i++) Files.deleteIfExists(old.get(i));
        } catch (Exception e) {
            TFClient.LOGGER.debug("TF Skins: caché: {}", e.toString());
        }
    }

    /** Al salir de un servidor se olvida quién lleva qué (las texturas se quedan: van por huella). */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        ASKED.clear();
        LOCAL.clear();
    }
}
