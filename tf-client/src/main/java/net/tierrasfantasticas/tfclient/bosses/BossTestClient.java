/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.vacio.TravelFx;

/**
 * El cliente de la prueba de los jefes (BossTest): en cuanto sale el menú, crea un mundo plano; saca las capturas que
 * pide la prueba (run/pruebas/*.png) y las del viaje por el agujero de gusano, cuenta las barras de jefe que se ven y
 * cierra el juego al acabar.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class BossTestClient {
    private static boolean started, quitting;
    private static int menuTicks, musicTicks;
    private static Field barsField;
    private static String shotSeen;

    private BossTestClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!BossTest.ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            if (mc.getOverlay() != null || mc.screen == null || ++menuTicks < 40) return;
            started = true;
            // En una pantalla virtual no hay foco: que no se pause
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(6);
            mc.options.framerateLimit().set(30);
            LevelSettings settings = new LevelSettings("pruebas_jefes", GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("pruebas_jefes", settings, new WorldOptions(20261017L, false, false),
                    reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            return;
        }
        if (mc.level != null && mc.screen != null && mc.screen.isPauseScreen() && !BossTest.keepMenu) mc.setScreen(null);
        if (BossTest.closeScreen) {
            BossTest.closeScreen = false;
            net.tierrasfantasticas.tfclient.client.TFMenuButton.testHot = null;
            net.tierrasfantasticas.tfclient.client.TFButtonTheme.testForce = false;
            if (mc.options.guiScale().get() != 0) {
                mc.options.guiScale().set(0);
                mc.resizeDisplay();
            }
            mc.setScreen(null);
        }
        if (BossTest.openMenu != null) {
            String what = BossTest.openMenu;
            BossTest.openMenu = null;
            net.tierrasfantasticas.tfclient.client.TFMenuButton.testHot = what.equals("principal-raton") || what.equals("principal-escala2")
                    ? net.tierrasfantasticas.tfclient.client.TFMenuButton.BLUE_1 : null;
            // (1.3.125) el menú también a escala 2 de interfaz; lo demás, a la automática
            int scale = what.equals("principal-escala2") ? 2 : 0;
            if (mc.options.guiScale().get() != scale) {
                mc.options.guiScale().set(scale);
                mc.resizeDisplay();
            }
            net.tierrasfantasticas.tfclient.client.TFButtonTheme.testForce = what.equals("opciones");
            // (que no pausen: son pantallas de pausa y, en un mundo de un jugador, el servidor se pararía y la prueba
            // no seguiría nunca)
            if (what.equals("automodpack")) {
                net.tierrasfantasticas.tfclient.client.TFButtonTheme.testForce = true;
                mc.setScreen(new net.tierrasfantasticas.tfclient.client.TFAutoModpack.Missing(new net.minecraft.client.gui.screens.TitleScreen()) {
                    @Override
                    public boolean isPauseScreen() {
                        return false;
                    }
                });
            } else mc.setScreen(what.equals("opciones")
                    ? new net.minecraft.client.gui.screens.OptionsScreen(new net.minecraft.client.gui.screens.TitleScreen(), mc.options) {
                        @Override
                        public boolean isPauseScreen() {
                            return false;
                        }
                    }
                    : new net.minecraft.client.gui.screens.TitleScreen() {
                        @Override
                        public boolean isPauseScreen() {
                            return false;
                        }
                    });
        }
        if ("corta".equals(BossTest.currentId())) mc.options.hideGui = BossTest.hideGui;
        if (mc.level != null && mc.player != null && "corta".equals(BossTest.currentId())) BossTest.clientItemsLight = itemsLight(mc);
        // (1.3.114) los caballeros sacros: lo más rápido que van y lo que se mueven sus piernas (walkAnimation)
        if (mc.level != null && "corta".equals(BossTest.currentId())) {
            for (net.minecraft.world.entity.Entity en : mc.level.entitiesForRendering()) {
                if (!(en instanceof net.tierrasfantasticas.tfclient.vacio.SacredKnight k)) continue;
                double v = Math.hypot(k.getX() - k.xo, k.getZ() - k.zo);
                float leg = k.walkAnimation.speed();
                KNIGHT_MAX.merge(k.displayName(), new double[] {v, leg, k.walkAnimation.position()},
                        (o, n) -> new double[] {Math.max(o[0], n[0]), Math.max(o[1], n[1]), n[2]});
            }
            StringBuilder b = new StringBuilder();
            for (var e : KNIGHT_MAX.entrySet()) b.append(String.format("%s: hasta %.2f bloques/tick, piernas hasta %.2f; ", e.getKey(), e.getValue()[0], e.getValue()[1]));
            // (1.3.117) la animación de reposo que les pone VfxLayers por debajo («-» = ninguna; «idle» era flotar)
            java.util.Set<String> idles = new java.util.TreeSet<>();
            for (net.minecraft.world.entity.Entity en : mc.level.entitiesForRendering()) {
                if (en instanceof net.tierrasfantasticas.tfclient.vacio.SacredKnight k) idles.add(k.layers.idleName());
            }
            b.append("reposo por debajo: ").append(idles);
            BossTest.clientKnights = b.toString();
        }
        if (BossTest.cameraType >= 0) {
            mc.options.setCameraType(net.minecraft.client.CameraType.values()[Math.min(2, BossTest.cameraType)]);
            BossTest.cameraType = -1;
        }
        if (BossTest.typePad != null) {
            net.tierrasfantasticas.tfclient.pad.TFPadClient.typeForTest(BossTest.typePad);
            BossTest.typePad = null;
        }
        // (1.3.134) la app Música para la prueba «tienda»: una canción en la carpeta, compartirla y lo que hay en COMPARTIDOS
        if (BossTest.musicCmd != null) {
            String cmd = BossTest.musicCmd;
            BossTest.musicCmd = null;
            music(cmd);
        }
        if ("tienda".equals(BossTest.currentId()) && musicTicks++ % 10 == 0) {
            var shared = net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.playlist(net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.SHARED);
            StringBuilder b = new StringBuilder();
            b.append(net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.tracks().size()).append(" canciones; COMPARTIDOS: ");
            if (shared == null) b.append("NO EXISTE");
            else {
                b.append(shared.tracks().size());
                for (String id : shared.tracks()) {
                    var tr = net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.get(id);
                    if (tr != null) b.append(" «").append(tr.shownTitle()).append("» (").append(tr.file()).append(", ").append(tr.url()).append(")");
                }
            }
            java.nio.file.Path pend = net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.dir().resolve("compartidas-pendientes.json");
            b.append("; pendientes: ").append(java.nio.file.Files.exists(pend) ? "sí" : "ninguna");
            BossTest.clientMusic = b.toString();
        }
        // (1.3.109) la lista de jugadores (TAB) y cuántos hay en ella, y cuántos con su skin (los bots)
        mc.options.keyPlayerList.setDown(BossTest.tabList);
        if (mc.getConnection() != null) {
            int n = 0, skins = 0;
            for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
                n++;
                if (!info.getSkinLocation().equals(net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(info.getProfile().getId()))) skins++;
            }
            BossTest.clientTab = n;
            BossTest.clientTabSkins = skins;
        }
        if (BossTest.actPad != null && BossTest.openPad == null) {
            net.tierrasfantasticas.tfclient.pad.TFPadClient.actForTest(BossTest.actPad);
            BossTest.actPad = null;
        }
        if (BossTest.openInventory && mc.player != null) {
            BossTest.openInventory = false;
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        }
        if (BossTest.openPad != null) {
            String app = BossTest.openPad;
            BossTest.openPad = null;
            net.tierrasfantasticas.tfclient.pad.TFPadClient.openForTest(app);
        }
        BossTest.clientBars = bars(mc);
        BossTest.clientItems = net.tierrasfantasticas.tfclient.skills.SkillClient.debugItems();
        BossTest.clientItemCount = net.tierrasfantasticas.tfclient.skills.SkillClient.debugItemCount();
        if (BossTest.dialogPress >= 0 && mc.screen instanceof net.tierrasfantasticas.tfclient.vacio.KnightScreen ks) {
            ks.testPress(BossTest.dialogPress);
            BossTest.dialogPress = -1;
        }
        // (1.3.96) para los panoramas, se ve más lejos y más claro (con 6 chunks y brillo normal el Nether era niebla roja y
        // el End, negro)
        if ("panoramas".equals(BossTest.currentId()) && mc.options.renderDistance().get() < 12) {
            mc.options.renderDistance().set(12);
            mc.options.gamma().set(1.0);
        }
        // (1.3.96) los panoramas para el agujero de gusano: la herramienta de Minecraft (6 caras de 90°), mirando al sur
        String pano = BossTest.panorama;
        if (pano != null && mc.level != null && mc.player != null && mc.screen == null) {
            BossTest.panorama = null;
            File dir = new File(mc.gameDirectory, "pruebas/panoramas/" + pano);
            dir.mkdirs();
            mc.player.setYRot(0F);
            mc.player.setXRot(0F);
            try {
                net.minecraft.network.chat.Component c = mc.grabPanoramixScreenshot(dir, 512, 512);
                TFClient.LOGGER.info("Prueba de jefes: panorama {}: {}", pano, c.getString());
            } catch (Throwable e) {
                TFClient.LOGGER.warn("Prueba de jefes: no se pudo sacar el panorama {}", pano, e);
            }
        }
        if (BossTest.finished && !quitting) {
            quitting = true;
            mc.stop();
        }
    }

    /** (1.3.134) Prueba «tienda»: crear (una canción WAV de 3 s en la carpeta), compartir, lista:<id>, hover, nohover. */
    private static void music(String cmd) {
        try {
            if (cmd.equals("crear")) {
                java.nio.file.Path dir = net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.dir();
                java.nio.file.Files.createDirectories(dir);
                int rate = 22050, n = rate * 3;
                java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + n * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                b.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                        .putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(n * 2);
                for (int i = 0; i < n; i++) b.putShort((short) (Math.sin(2 * Math.PI * 440 * i / rate) * 6000 * Math.min(1, (n - i) / 2000.0)));
                java.nio.file.Files.write(dir.resolve("prueba_compartir.wav"), b.array());
                net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.scanFolder();
            } else if (cmd.equals("compartir")) {
                for (var tr : net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.tracks()) {
                    if (tr.file().contains("prueba_compartir")) {
                        TFClient.LOGGER.info("Prueba de jefes: compartir «{}»: {}", tr.shownTitle(), net.tierrasfantasticas.tfclient.pad.music.MusicShare.share(tr));
                        return;
                    }
                }
                TFClient.LOGGER.warn("Prueba de jefes: no está la canción de la prueba para compartirla");
            } else if (cmd.startsWith("lista:")) {
                net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.selectList(cmd.substring(6));
            } else if (cmd.equals("hover") || cmd.equals("nohover")) {
                net.tierrasfantasticas.tfclient.pad.TFPadClient.hoverMusicForTest(cmd.equals("hover"));
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("Prueba de jefes: música «{}» falló", cmd, e);
        }
    }

    private static Field lightPixels;

    /**
     * (1.3.117, objetos negros con shaders en el Void) Lo que ilumina un objeto: el paquete, la luz del jugador (bloque y
     * cielo) y el mapa de luz de Minecraft en sus esquinas (ABGR: oscuridad total, solo bloque, solo cielo, todo; la
     * interfaz usa «todo»).
     */
    private static String itemsLight(Minecraft mc) {
        StringBuilder b = new StringBuilder(mc.level.dimension().location().toString()).append(", ")
                .append(net.tierrasfantasticas.tfclient.vacio.VoidTestHooks.shaders());
        int packed = mc.getEntityRenderDispatcher().getPackedLightCoords(mc.player, 1F);
        b.append("; luz del jugador: bloque ").append(net.minecraft.client.renderer.LightTexture.block(packed)).append(", cielo ")
                .append(net.minecraft.client.renderer.LightTexture.sky(packed));
        try {
            if (lightPixels == null) {
                for (Field f : net.minecraft.client.renderer.LightTexture.class.getDeclaredFields()) {
                    if (f.getType() == com.mojang.blaze3d.platform.NativeImage.class) {
                        f.setAccessible(true);
                        lightPixels = f;
                    }
                }
            }
            com.mojang.blaze3d.platform.NativeImage img = (com.mojang.blaze3d.platform.NativeImage) lightPixels.get(mc.gameRenderer.lightTexture());
            b.append(String.format("; mapa de luz: 0/0 %08X, bloque 15 %08X, cielo 15 %08X, 15/15 %08X", img.getPixelRGBA(0, 0), img.getPixelRGBA(15, 0),
                    img.getPixelRGBA(0, 15), img.getPixelRGBA(15, 15)));
        } catch (Throwable t) {
            b.append("; mapa de luz: ").append(t);
        }
        return b.toString();
    }

    /**
     * Las capturas, al acabar de dibujar un fotograma que empezó después de pedirla (antes se sacaban en el tick del
     * cliente, con el último fotograma dibujado: con shaders, a pocos fotogramas por segundo, podía ser de antes de la
     * vista pedida).
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!BossTest.ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        loadingShots(mc);
        tunnelShots();
        skyShots();
        crateShots(mc);
        String shot = BossTest.SHOTS_PENDING.peek();
        if (shot == null || mc.level == null) {
            shotSeen = null;
            return;
        }
        if (!shot.equals(shotSeen)) {
            shotSeen = shot;
            return;
        }
        shotSeen = null;
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            File dir = new File(mc.gameDirectory, "pruebas");
            dir.mkdirs();
            img.writeToFile(new File(dir, shot + ".png"));
        } catch (Exception e) {
            TFClient.LOGGER.warn("Prueba de jefes: no se pudo sacar la captura {}", shot, e);
        }
        BossTest.SHOTS_PENDING.poll();
    }

    private static int loadingFrames;

    /**
     * (1.3.136) La pantalla de carga del primer arranque (en la prueba no hay fml.toml: la de Forge envuelta por la de
     * TF, como la primera vez del dueño tras el AutoModpack). Una captura cada 20 fotogramas, «carga_N», y qué pantalla envuelve.
     */
    private static void loadingShots(Minecraft mc) {
        if (!(mc.getOverlay() instanceof net.tierrasfantasticas.tfclient.client.TFLoadingOverlay tf)) return;
        int n = ++loadingFrames;
        if (n % 20 != 0 || n > 200) return;
        boolean noFix = net.tierrasfantasticas.tfclient.client.TFLoadingOverlay.testNoFix;
        String name = "00_carga_" + n + (noFix ? "_sin_arreglo" : "");
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            File dir = new File(mc.gameDirectory, "pruebas");
            dir.mkdirs();
            img.writeToFile(new File(dir, name + ".png"));
            TFClient.LOGGER.info("Prueba de jefes: captura {} de la pantalla de carga ({}x{}; envuelve {})", name,
                    mc.getWindow().getWidth(), mc.getWindow().getHeight(), tf.wrapped());
        } catch (Exception e) {
            TFClient.LOGGER.warn("Prueba de jefes: no se pudo sacar la captura de la carga", e);
        }
        // la primera, sin el arreglo (así se ve que la prueba reproduce lo del dueño); las demás, con él
        net.tierrasfantasticas.tfclient.client.TFLoadingOverlay.testNoFix = false;
    }

    /** (1.3.91) Capturas del viaje por el agujero de gusano, a los tiempos del túnel (no a los de la prueba). */
    private static final String[] TUNNEL = {"00_deforma", "01_rasgado", "02_tramo1", "03_puerta1", "04_tramo2", "05_puerta2",
            "06_tramo3", "07_puerta3", "08_tramo4", "09_salida", "10_luz", "11_aparece"};
    /** Segundos desde que empieza el viaje (las 9 primeras; 1.3.92: cuatro tramos, = TravelFx) o desde que llega (las 3 últimas). */
    private static final float[] TUNNEL_AT = {0.7F, 1.7F, 3.3F, 4.35F, 5.6F, 6.75F, 8.0F, 9.15F, 10.4F, 0.75F, 1.4F, 2.1F};
    private static final int TUNNEL_FROM_START = 9;
    private static int tunnelTrip = -1, tunnelNext;

    private static void tunnelShots() {
        float c = TravelFx.clock();
        if (c < 0F) return;
        if (TravelFx.tripId() != tunnelTrip) {
            tunnelTrip = TravelFx.tripId();
            tunnelNext = 0;
        }
        if (tunnelNext >= TUNNEL.length) return;
        float at = TUNNEL_AT[tunnelNext];
        float arrived = TravelFx.arrivedAt();
        if (tunnelNext < TUNNEL_FROM_START ? c < at : arrived < 0F || c < arrived + at) return;
        BossTest.clientShot((TravelFx.back() ? "vuelta" : "ida") + "_tunel_" + TUNNEL[tunnelNext]);
        tunnelNext++;
    }

    /**
     * (1.3.93) Capturas de la grieta del cielo, a los tiempos de su animación (segundos desde que llega cada una):
     * preludio; se rompe (grietas, fogonazo, se abre, abierta); se sella (cerrándose, cicatriz); se vuelve a abrir; el
     * final.
     */
    private static final String[][] SKY = {{}, {"preludio", "relampago"}, {"rompe", "fogonazo", "abre", "grieta"}, {"cierra", "cicatriz"},
            {"reabre", "reabierta"}, {"final", "sin_grieta"}};
    /**
     * (1.3.95, la grieta 2.0: el preludio dura 12 s, con relámpagos a 3,6 / 6,4 / 8,7 / 10,3 / 11,4 s; al romperse, la
     * telaraña crece a saltos, estalla a los 1,8 s y se abre hasta los 5,5 s; al volver a abrirse, estalla a los 1,2 s)
     */
    private static final float[][] SKY_AT = {{}, {7.5F, 8.66F}, {1.0F, 1.85F, 3.5F, 7.0F}, {1.4F, 5F}, {1.5F, 4.5F}, {2.5F, 6F}};
    private static int skySeen = -1, skyNext;

    private static void skyShots() {
        int fx = net.tierrasfantasticas.tfclient.vacio.SkyRiftClient.testFx();
        if (fx < 1 || fx >= SKY.length) return;
        int n = net.tierrasfantasticas.tfclient.vacio.SkyRiftClient.testFxCount();
        if (n != skySeen) {
            skySeen = n;
            skyNext = 0;
        }
        if (skyNext >= SKY[fx].length || net.tierrasfantasticas.tfclient.vacio.SkyRiftClient.testSince() < SKY_AT[fx][skyNext]) return;
        BossTest.clientShot("cielo_" + SKY[fx][skyNext]);
        skyNext++;
    }

    /** (1.3.99) Capturas de la cinemática de las crates, a sus tiempos (ticks desde que empieza). */
    private static final String[] CRATE = {"llegada", "haz", "llave", "golpe", "tiembla", "abre", "anillo", "ruleta", "para", "premio", "final"};
    private static final float[] CRATE_AT = {12, 40, 56, 68, 92, 107, 140, 196, 252, 272, 318};
    private static Object crateSeen;
    private static int crateNext, crateRun;

    /** (1.3.99) La pantalla de antes de abrir (con el santuario detrás), como cuando el jugador toca la crate. */
    static void showPreOpen(net.tierrasfantasticas.tfclient.crates.config.CrateConfig cfg, net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            preOpenCfg = cfg;
            preOpenShot = false;
            preOpenAt = System.nanoTime();
            mc.setScreen(new net.tierrasfantasticas.tfclient.crates.client.screen.CratePreOpenScreen(cfg, pos));
        });
    }

    /** (1.3.107) Las capturas de antes de abrir sin cada cosa (CrateCinematicScreen.testOff). */
    // (1.3.113) primero lo que más importa para las rayas (si la cinemática se abre antes, faltan las últimas)
    private static final int[] OFF = {0, 32, 64, 4, 128, 1, 8, 16, 2};
    private static final String[] OFF_NAME = {"otra", "sin_supermuestreo", "sin_nitidez", "sin_post", "sin_rendijas", "sin_efectos",
            "sin_agua", "sin_plantas", "sin_rayos_post"};
    private static int offNext = OFF.length;
    private static long offAt;

    private static net.tierrasfantasticas.tfclient.crates.config.CrateConfig preOpenCfg;
    private static boolean preOpenShot = true, poolShot = true;
    private static final java.util.Map<String, double[]> KNIGHT_MAX = new java.util.TreeMap<>();
    private static long preOpenAt, poolAt;

    private static void crateShots(Minecraft mc) {
        if (!preOpenShot && mc.screen instanceof net.tierrasfantasticas.tfclient.crates.client.screen.CratePreOpenScreen
                && System.nanoTime() - preOpenAt > 1_200_000_000L) {
            preOpenShot = true;
            TFClient.LOGGER.info("Prueba de jefes: captura de antes de abrir (pantalla {}, mundo {})", mc.screen.getClass().getSimpleName(),
                    mc.level != null);
            BossTest.clientShot("crate" + (crateRun + 1) + "_preabrir");
            // (1.3.107) en la primera, la misma vista otra vez (lo que parpadea de un fotograma a otro) y sin cada cosa
            // (para saber qué pinta las rayas del dueño); luego, la de las recompensas, encima del santuario
            offNext = crateRun == 0 ? 0 : OFF.length;
            offAt = 0L;
            poolShot = offNext < OFF.length;
            poolAt = 0L;
        }
        if (offNext < OFF.length && mc.screen instanceof net.tierrasfantasticas.tfclient.crates.client.screen.CratePreOpenScreen
                && BossTest.SHOTS_PENDING.isEmpty()) {
            if (offAt == 0L) {
                net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicScreen.testOff = OFF[offNext];
                offAt = System.nanoTime() + (OFF[offNext] == 0 ? 40_000_000L : 300_000_000L);
            } else if (System.nanoTime() > offAt) {
                BossTest.clientShot("crate1_preabrir_" + OFF_NAME[offNext]);
                offNext++;
                offAt = 0L;
                if (offNext >= OFF.length) {
                    net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicScreen.testOff = 0;
                    poolShot = false;
                }
            }
            return;
        }
        // (1.3.109) si la cinemática se abre antes de acabar las de «sin cada cosa», se dejan: con lo último apagado
        // la cinemática salía «sin shader de la escena»
        if (offNext < OFF.length && mc.screen instanceof net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicScreen) {
            TFClient.LOGGER.info("Prueba de jefes: la cinemática se abrió antes de acabar las capturas sin cada cosa (van {} de {})", offNext, OFF.length);
            net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicScreen.testOff = 0;
            offNext = OFF.length;
            poolShot = true;
        }
        // (las recompensas, cuando ya se ha sacado la de antes de abrir)
        if (!poolShot && poolAt == 0L && BossTest.SHOTS_PENDING.isEmpty()
                && mc.screen instanceof net.tierrasfantasticas.tfclient.crates.client.screen.CratePreOpenScreen pre) {
            poolAt = System.nanoTime() + 300_000_000L;
            mc.setScreen(new net.tierrasfantasticas.tfclient.crates.client.screen.CratePoolScreen(preOpenCfg, pre));
        }
        if (!poolShot && mc.screen instanceof net.tierrasfantasticas.tfclient.crates.client.screen.CratePoolScreen
                && poolAt != 0L && System.nanoTime() > poolAt) {
            poolShot = true;
            BossTest.clientShot("crate" + (crateRun + 1) + "_recompensas");
        }
        if (!(mc.screen instanceof net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicScreen cs)) return;
        if (cs != crateSeen) {
            crateSeen = cs;
            crateNext = 0;
            crateRun++;
        }
        // (1.3.107) la de la apertura, otra vez en el fotograma siguiente (con la cámara en marcha: lo que parpadea)
        if (abreOtra && BossTest.SHOTS_PENDING.isEmpty()) {
            abreOtra = false;
            BossTest.clientShot("crate1_05_abre_otra");
            return;
        }
        if (crateNext >= CRATE.length || cs.clock() < CRATE_AT[crateNext]) return;
        BossTest.clientShot("crate" + crateRun + "_" + String.format("%02d", crateNext) + "_" + CRATE[crateNext]);
        abreOtra = crateRun == 1 && "abre".equals(CRATE[crateNext]);
        crateNext++;
    }

    private static boolean abreOtra;

    /** Las barras de jefe que ve el jugador (BossHealthOverlay.events). */
    private static int bars(Minecraft mc) {
        try {
            BossHealthOverlay o = mc.gui.getBossOverlay();
            if (barsField == null) {
                for (Field f : BossHealthOverlay.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        barsField = f;
                    }
                }
            }
            return barsField == null ? -1 : ((Map<?, ?>) barsField.get(o)).size();
        } catch (Exception e) {
            return -1;
        }
    }
}
