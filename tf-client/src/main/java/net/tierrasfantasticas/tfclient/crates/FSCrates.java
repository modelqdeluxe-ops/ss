package net.tierrasfantasticas.tfclient.crates;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.tierrasfantasticas.tfclient.crates.client.ClientSetup;
import net.tierrasfantasticas.tfclient.crates.command.FSCrateCommand;
import net.tierrasfantasticas.tfclient.crates.config.ItemReferenceGenerator;
import net.tierrasfantasticas.tfclient.crates.config.JsonCrateLoader;
import net.tierrasfantasticas.tfclient.crates.config.MediaGuide;
import net.tierrasfantasticas.tfclient.crates.network.FSNetwork;
import net.tierrasfantasticas.tfclient.crates.registry.ModRegistry;
import org.slf4j.Logger;

/**
 * TF Crates: las crates físicas de Fantastic Crates (de Pewez, el dueño), dentro de TF Client desde la 1.3.99. El comando
 * es {@code /tf crates} (antes {@code /fscrate}).
 *
 * <p>Sus bloques, objetos, recursos, datos guardados (fscrates_definitions, fscrates_cooldowns, fscrates_pity), el NBT
 * de las llaves y las crates («fscrates») y su carpeta de configuración (config/fscrates) siguen con el espacio de nombres
 * «fscrates»: las crates puestas en los mundos, las llaves de los jugadores y las cajas de config/fscrates/cajas siguen
 * valiendo tal cual. Si el jar viejo de Fantastic Crates sigue instalado, esto no se registra (habría dos «fscrates:crate»
 * y el juego no arrancaría): hay que quitarlo.
 */
public final class FSCrates {
    /** El espacio de nombres de sus bloques, objetos, recursos y datos (el de siempre de Fantastic Crates). */
    public static final String MOD_ID = "fscrates";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static boolean on;

    private FSCrates() {}

    public static void init(IEventBus modBus) {
        if (ModList.get().isLoaded(MOD_ID)) {
            LOGGER.error("[TF Crates] Fantastic Crates sigue instalado: quita su jar (las crates ya van dentro de TF Client)");
            return;
        }
        on = true;
        ModRegistry.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(FSNetwork::register));
        if (FMLEnvironment.dist == Dist.CLIENT) modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(ClientSetup::init));
        MinecraftForge.EVENT_BUS.addListener(FSCrates::onServerStarted);
        LOGGER.info("[TF Crates] Crates de Tierras Fantásticas listas");
    }

    /** ¿Está activo? (no, si sigue instalado el mod viejo) */
    public static boolean enabled() {
        return on;
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            ItemReferenceGenerator.generate(event.getServer());
        } catch (Throwable t) {
            LOGGER.error("[FSCrates] Fallo generando la referencia de items", t);
        }
        MediaGuide.write();
        try {
            JsonCrateLoader.loadAll(event.getServer());
        } catch (Throwable t) {
            LOGGER.error("[FSCrates] Fallo cargando las cajas desde JSON", t);
        }
    }

    /** {@code /tf crates ...} (antes /fscrate). */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return on ? FSCrateCommand.command() : Commands.literal("crates");
    }
}
