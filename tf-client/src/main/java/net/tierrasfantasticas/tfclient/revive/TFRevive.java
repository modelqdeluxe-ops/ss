/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import java.nio.file.Path;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;

/**
 * (1.3.157) Revivir: el Revive Mod del dueño (revivemod 2.1.0, «ReviveForge-1.20.1») dentro del TF Client, igual que
 * era. El dueño: «es mi mod, será nueva integración a TF Client… exactamente todo igual, solo lo añadirás; en la config
 * pon que puedan rematarme los jugadores, ojo no mobs (opcional en la config, funcional), y que /tf reload aplique los
 * cambios en tiempo real».
 * <ul>
 *   <li>Al morir te desangras tumbado con un contador (barra «Desangrándose»); te reviven con clic derecho (más rápido
 *   entre varios) y el que te revive es invencible mientras lo hace. Rendirse (E) y auto-revivir pagando niveles (F),
 *   con teclas propias; /revive con lo mismo que tenía (surrender, self, status, reload, force, kill, down, set).</li>
 *   <li>Lo nuevo: {@link ReviveConfig#playersCanFinish} (por defecto sí: un golpe de otro jugador a un noqueado lo mata
 *   de verdad, con su tumba y su mensaje de muerte) y {@link ReviveConfig#mobsCanFinish} (por defecto no).</li>
 *   <li>La config es config/tfclient/revivir.json (la primera vez se copia de config/revivemod.json, con lo que tuviera
 *   el dueño); /tf reload y /revive reload la aplican al momento.</li>
 *   <li>Si el revivemod sigue instalado, este no se activa (no habría dos noqueos a la vez): el aviso sale en el
 *   registro. El canal de red se registra siempre, para que servidor y jugadores tengan los mismos.</li>
 * </ul>
 */
public final class TFRevive {
    /** El mod de antes: con él instalado, el de TF Client se queda quieto. */
    public static final String OLD_MOD = "revivemod";
    public static final String FILE = "revivir.json";

    private static ReviveConfig CONFIG = new ReviveConfig();
    private static Path CONFIG_PATH;
    private static boolean active;

    private TFRevive() {}

    /** Desde el constructor del mod (servidor y cliente, como el revivemod). */
    public static void init() {
        active = !ModList.get().isLoaded(OLD_MOD);
        if (!active) {
            TFClient.LOGGER.warn("TF Revivir: el mod «revivemod» sigue instalado: el revivir del TF Client no se activa. "
                    + "Quita revivemod (ReviveForge) de la carpeta mods del servidor y del AutoModpack: el TF Client ya lo trae.");
            return;
        }
        CONFIG_PATH = TFConfigDir.fileFrom(FILE, FMLPaths.CONFIGDIR.get().resolve("revivemod.json"));
        CONFIG = ReviveConfig.load(CONFIG_PATH);
        MinecraftForge.EVENT_BUS.register(new CombatEvents());
        MinecraftForge.EVENT_BUS.register(new DownTicker());
        MinecraftForge.EVENT_BUS.register(new ConnectionEvents());
        MinecraftForge.EVENT_BUS.register(new InteractionEvents());
        MinecraftForge.EVENT_BUS.register(new RestrictionEvents());
        MinecraftForge.EVENT_BUS.register(new ReviveCommands());
        MinecraftForge.EVENT_BUS.register(new TFRevive.Stopping());
        TFClient.LOGGER.info("TF Revivir: activo. downTime={}s, reviveDistance={} blocks, reviveTime={} ticks, rematan jugadores={}, rematan mobs={}",
                CONFIG.downTimeSeconds, CONFIG.reviveDistance, CONFIG.reviveTimeTicks, CONFIG.playersCanFinish, CONFIG.mobsCanFinish);
    }

    /** Si el revivir del TF Client está encendido (no lo está si el revivemod sigue instalado). */
    public static boolean active() {
        return active;
    }

    public static ReviveConfig getConfig() {
        return CONFIG;
    }

    public static void saveConfig() {
        if (CONFIG_PATH != null) CONFIG.save(CONFIG_PATH);
    }

    /**
     * Vuelve a leer config/tfclient/revivir.json (/tf reload, /revive reload). Lo nuevo vale al momento: cada cosa se lee
     * de la config cuando se usa (los que ya están noqueados siguen con el tiempo que tenían). Si el archivo tiene un
     * error, se queda la config de antes y el archivo no se toca.
     */
    public static void reloadConfig() throws Exception {
        if (!active || CONFIG_PATH == null) return;
        CONFIG = ReviveConfig.read(CONFIG_PATH);
        CONFIG.save(CONFIG_PATH);
        TFClient.LOGGER.info("TF Revivir: config recargada (rematan jugadores={}, rematan mobs={})", CONFIG.playersCanFinish, CONFIG.mobsCanFinish);
    }

    /** Para la prueba en el juego: cambia la config como si el dueño editara el archivo (y la guarda). */
    public static void testSet(boolean players, boolean mobs) {
        CONFIG.playersCanFinish = players;
        CONFIG.mobsCanFinish = mobs;
        saveConfig();
    }

    /** Para la prueba en el juego: el archivo de la config. */
    public static Path testPath() {
        return CONFIG_PATH;
    }

    public static final class Stopping {
        @SubscribeEvent
        public void onServerStopping(ServerStoppingEvent event) {
            TFClient.LOGGER.info("TF Revivir: guardando la config y quitando los noqueos...");
            saveConfig();
            DownManager.clearAll(event.getServer());
        }
    }
}
