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

/**
 * El cliente de la prueba de los jefes (BossTest): en cuanto sale el menú, crea un mundo plano; saca las capturas que
 * pide la prueba (run/pruebas/*.png), cuenta las barras de jefe que se ven y cierra el juego al acabar.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class BossTestClient {
    private static boolean started, quitting;
    private static int menuTicks;
    private static Field barsField;

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
        if (BossTest.dialogPress >= 0 && mc.screen instanceof net.tierrasfantasticas.tfclient.vacio.KnightScreen ks) {
            ks.testPress(BossTest.dialogPress);
            BossTest.dialogPress = -1;
        }
        String shot = BossTest.shotRequest;
        if (shot != null && mc.level != null) {
            BossTest.shotRequest = null;
            try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                File dir = new File(mc.gameDirectory, "pruebas");
                dir.mkdirs();
                img.writeToFile(new File(dir, shot + ".png"));
            } catch (Exception e) {
                TFClient.LOGGER.warn("Prueba de jefes: no se pudo sacar la captura {}", shot, e);
            }
        }
        if (BossTest.finished && !quitting) {
            quitting = true;
            mc.stop();
        }
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
