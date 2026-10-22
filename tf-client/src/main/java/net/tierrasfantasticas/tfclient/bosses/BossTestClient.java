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
    private static int menuTicks;
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
        if (mc.level != null && mc.screen != null && mc.screen.isPauseScreen()) mc.setScreen(null);
        if (BossTest.closeScreen) {
            BossTest.closeScreen = false;
            mc.setScreen(null);
        }
        if (BossTest.typePad != null) {
            net.tierrasfantasticas.tfclient.pad.TFPadClient.typeForTest(BossTest.typePad);
            BossTest.typePad = null;
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

    /**
     * Las capturas, al acabar de dibujar un fotograma que empezó después de pedirla (antes se sacaban en el tick del
     * cliente, con el último fotograma dibujado: con shaders, a pocos fotogramas por segundo, podía ser de antes de la
     * vista pedida).
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!BossTest.ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
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
    private static final int[] OFF = {0, 1, 2, 4, 8, 16};
    private static final String[] OFF_NAME = {"otra", "sin_efectos", "sin_rayos_post", "sin_post", "sin_agua", "sin_plantas"};
    private static int offNext = 6;
    private static long offAt;

    private static net.tierrasfantasticas.tfclient.crates.config.CrateConfig preOpenCfg;
    private static boolean preOpenShot = true, poolShot = true;
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
                offAt = System.nanoTime() + (OFF[offNext] == 0 ? 40_000_000L : 400_000_000L);
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
