/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.tierrasfantasticas.tfclient.items.TFItems;
import org.slf4j.Logger;

/**
 * TF Client: pantalla de carga, menú principal y música de Tierras Fantásticas en el cliente.
 * Registra los objetos de los sets (armas, herramientas, armaduras y cosméticos, ver {@link TFItems}) y el comando /tf.
 * En un servidor dedicado hace de puente con la web (ver {@link net.tierrasfantasticas.tfclient.server.TFBridge}).
 */
@Mod(TFClient.MOD_ID)
public final class TFClient {
    public static final String MOD_ID = "tfclient";
    public static final String VERSION = "1.3.159";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TFClient() {
        TFItems.register(FMLJavaModLoadingContext.get().getModEventBus());
        // Jefes: sus entidades, los objetos que sueltan y el enlace del motor de skills con el mundo
        net.tierrasfantasticas.tfclient.bosses.TFBosses.init(FMLJavaModLoadingContext.get().getModEventBus());
        // The Void: la dimensión del vacío, su isla y el caballero de luz que lleva allí
        net.tierrasfantasticas.tfclient.vacio.TFVoid.init(FMLJavaModLoadingContext.get().getModEventBus());
        // TF Claims: protecciones de zona (antes Fantastic Claims)
        net.tierrasfantasticas.tfclient.claims.TFClaims.init(FMLJavaModLoadingContext.get().getModEventBus());
        // TF Crates: las crates físicas (antes Fantastic Crates; sus objetos y datos siguen con el nombre «fscrates»)
        net.tierrasfantasticas.tfclient.crates.FSCrates.init(FMLJavaModLoadingContext.get().getModEventBus());
        net.tierrasfantasticas.tfclient.menu.TFPanelMenu.MENUS.register(FMLJavaModLoadingContext.get().getModEventBus());
        // (1.3.157) Revivir: el Revive Mod del dueño dentro del TF Client (noqueo, revivir y, nuevo, rematar)
        net.tierrasfantasticas.tfclient.revive.TFRevive.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
                    net.tierrasfantasticas.tfclient.vfx.VfxNet.register();
                    net.tierrasfantasticas.tfclient.skills.SkillNet.register();
                    net.tierrasfantasticas.tfclient.items.TFWardrobe.register();
                    net.tierrasfantasticas.tfclient.pad.TFPadNet.register();
                    net.tierrasfantasticas.tfclient.bosses.BossNet.register();
                    net.tierrasfantasticas.tfclient.vacio.VoidNet.register();
                    net.tierrasfantasticas.tfclient.npcs.NpcNet.register();
                    net.tierrasfantasticas.tfclient.server.TFStaffLock.register();
                    net.tierrasfantasticas.tfclient.server.TFBirthday.register();
                    net.tierrasfantasticas.tfclient.revive.ReviveNetwork.register();
                }));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                TFConfig.load();
                TFConfig.disableForgeEarlyWindow();
            } catch (Throwable t) {
                LOGGER.error("TF Client: error al iniciar la configuración", t);
            }
        }
    }
}
