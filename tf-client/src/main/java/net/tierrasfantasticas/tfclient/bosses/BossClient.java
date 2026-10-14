package net.tierrasfantasticas.tfclient.bosses;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillClient;

/** Lo de los jefes en el cliente: cómo se dibujan, la ventana /tf boss y los golpes con sus armas. */
public final class BossClient {
    private BossClient() {}

    /** La ventana /tf boss: la abre o, si ya está abierta, la pone al día. */
    static void open(BossNet.Open m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BossScreen s) s.update(m);
        else mc.setScreen(new BossScreen(m));
    }

    /** Las cajas de golpe por piezas de un jefe (las cabezas de la Hidra), donde se ven ahora. */
    static List<AABB> hitboxes(BossEntity e) {
        return SkillClient.hitboxes(e);
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TFBosses.JEFE.get(), BossRenderer::new);
            event.registerEntityRenderer(TFBosses.ESBIRRO.get(), BossRenderer::new);
            event.registerEntityRenderer(TFBosses.GRIETA.get(), RiftRenderer::new);
            event.registerEntityRenderer(TFBosses.ASIENTO.get(), NoopRenderer::new);
        }

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent event) {
            // Los escudos de jefe se ven cubriéndose como el de Minecraft (su modelo «blocking»)
            event.enqueueWork(() -> {
                for (RegistryObject<Item> o : TFBosses.DROPS.values()) {
                    if (o.get() instanceof BossItems.Shield) {
                        ItemProperties.register(o.get(), new ResourceLocation("blocking"), (stack, level, entity, seed) ->
                                entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
                    }
                }
            });
        }
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        /** Clic izquierdo con un arma de jefe: el servidor lanza sus skills (también al aire). */
        @SubscribeEvent
        public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (!event.isAttack() || mc.player == null) return;
            if (mc.player.getMainHandItem().getItem() instanceof BossItems.Weapon) BossNet.CHANNEL.sendToServer(new BossNet.Swing());
        }
    }
}
