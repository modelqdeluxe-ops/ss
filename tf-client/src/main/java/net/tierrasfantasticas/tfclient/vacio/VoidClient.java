package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/** Lo del Void en el cliente: el dibujo del caballero, los efectos de la dimensión, el shader del cielo y el viaje. */
public final class VoidClient {
    private VoidClient() {}

    /** El servidor abre el diálogo del caballero. */
    static void talk(VoidNet.Talk m) {
        Minecraft.getInstance().setScreen(new KnightScreen(m));
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TFVoid.KNIGHT.get(), KnightRenderer::new);
        }

        @SubscribeEvent
        public static void onEffects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(new ResourceLocation(TFClient.MOD_ID, "void"), new VoidEffects());
        }

        @SubscribeEvent
        public static void onShaders(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(TFClient.MOD_ID, "void_sky"),
                        DefaultVertexFormat.POSITION), s -> {
                    VoidSky.shader = s;
                    VoidSky.reset();
                });
            } catch (IOException e) {
                TFClient.LOGGER.error("TF Void: no se pudo cargar el shader del cielo", e);
            }
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("vacio_viaje", TravelFx::render);
        }
    }
}
