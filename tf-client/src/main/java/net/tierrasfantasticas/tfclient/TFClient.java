package net.tierrasfantasticas.tfclient;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/** TF Client: pantalla de carga, menú principal y música de Tierras Fantásticas. Solo actúa en el cliente. */
@Mod(TFClient.MOD_ID)
public final class TFClient {
    public static final String MOD_ID = "tfclient";
    public static final String VERSION = "1.0.9";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TFClient() {
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
