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
    public static final String VERSION = "1.3.4";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TFClient() {
        TFItems.register(FMLJavaModLoadingContext.get().getModEventBus());
        net.tierrasfantasticas.tfclient.menu.TFPanelMenu.MENUS.register(FMLJavaModLoadingContext.get().getModEventBus());
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
