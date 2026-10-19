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
        BossTest.clientBars = bars(mc);
        BossTest.clientItems = net.tierrasfantasticas.tfclient.skills.SkillClient.debugItems();
        BossTest.clientItemCount = net.tierrasfantasticas.tfclient.skills.SkillClient.debugItemCount();
        if (BossTest.dialogPress >= 0 && mc.screen instanceof net.tierrasfantasticas.tfclient.vacio.KnightScreen ks) {
            ks.testPress(BossTest.dialogPress);
            BossTest.dialogPress = -1;
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
    private static final float[] TUNNEL_AT = {0.7F, 1.7F, 2.7F, 3.3F, 4.1F, 4.8F, 5.5F, 6.2F, 7.0F, 0.75F, 1.4F, 2.1F};
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
