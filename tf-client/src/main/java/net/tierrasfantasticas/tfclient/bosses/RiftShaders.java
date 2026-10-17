package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/** Los shaders de la grieta 3.0 (shaders/core/rift_obsidian y rift_fx): se cargan con los recursos. */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RiftShaders {
    static ShaderInstance obsidian, fx;

    private RiftShaders() {}

    static boolean ready() {
        return obsidian != null && fx != null;
    }

    @SubscribeEvent
    public static void onShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(TFClient.MOD_ID, "rift_obsidian"),
                    DefaultVertexFormat.NEW_ENTITY), s -> obsidian = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(TFClient.MOD_ID, "rift_fx"),
                    DefaultVertexFormat.POSITION_COLOR_TEX), s -> fx = s);
        } catch (IOException e) {
            TFClient.LOGGER.error("TF Jefes: no se pudieron cargar los shaders de la grieta", e);
        }
    }
}
