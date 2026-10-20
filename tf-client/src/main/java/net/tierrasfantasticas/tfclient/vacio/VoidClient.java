package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Lo del Void en el cliente: el dibujo del caballero, los efectos de la dimensión, los shaders (el cielo, el viaje, la grieta
 * del cielo y el santuario: {@link SanctuaryFx}) y el viaje.
 */
public final class VoidClient {
    private VoidClient() {}

    /** El servidor abre el diálogo del caballero. */
    static void talk(VoidNet.Talk m) {
        Minecraft.getInstance().setScreen(new KnightScreen(m));
    }

    /** (1.3.97) Hablar con un caballero sacro del santuario (lo que cuenta es fijo: no hace falta preguntar al servidor). */
    static void sacredTalk(SacredKnight k, SacredKnights.Def d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new KnightScreen(d, k.getId(), mc.player.getName().getString()));
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && TFVoid.isVoid(mc.level)) ShaderPacks.tick();
        }
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TFVoid.KNIGHT.get(), KnightRenderer::new);
            event.registerEntityRenderer(TFVoid.SACRED.get(), KnightRenderer::new);
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
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(TFClient.MOD_ID, "wormhole"),
                        DefaultVertexFormat.POSITION), s -> TravelFx.shader = s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(TFClient.MOD_ID, "sky_rift"),
                        DefaultVertexFormat.POSITION_TEX), s -> SkyRiftClient.shader = s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(TFClient.MOD_ID, "velo_alba"),
                        DefaultVertexFormat.POSITION), s -> SanctuaryFx.veil = s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(TFClient.MOD_ID, "corazon_alba"),
                        DefaultVertexFormat.POSITION_TEX), s -> SanctuaryFx.heart = s);
            } catch (IOException e) {
                TFClient.LOGGER.error("TF Void: no se pudo cargar el shader del cielo, del viaje, de la grieta del cielo o del santuario", e);
            }
        }
    }
}
