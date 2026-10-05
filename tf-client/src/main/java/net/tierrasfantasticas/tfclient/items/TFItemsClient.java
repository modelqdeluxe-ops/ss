package net.tierrasfantasticas.tfclient.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;

/** Cliente: animaciones de los objetos (tensar, cargar, lanzar, bloquear) y cosméticos de la espalda. */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TFItemsClient {
    private static final ResourceLocation PULL = new ResourceLocation("pull");
    private static final ResourceLocation PULLING = new ResourceLocation("pulling");
    private static final ResourceLocation CHARGED = new ResourceLocation("charged");
    private static final ResourceLocation FIREWORK = new ResourceLocation("firework");
    private static final ResourceLocation CAST = new ResourceLocation("cast");
    private static final ResourceLocation BLOCKING = new ResourceLocation("blocking");
    private static final ResourceLocation THROWING = new ResourceLocation("throwing");

    private TFItemsClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(TFItemsClient::registerProperties);
    }

    private static List<RegistryObject<Item>> ofType(String type) {
        return TFItems.BY_TYPE.getOrDefault(type, List.of());
    }

    private static boolean using(LivingEntity entity, ItemStack stack) {
        return entity != null && entity.isUsingItem() && entity.getUseItem() == stack;
    }

    /** Las mismas propiedades que Minecraft da al arco, la ballesta, la caña, el escudo y el tridente. */
    private static void registerProperties() {
        for (RegistryObject<Item> o : ofType("bow")) {
            Item item = o.get();
            ItemProperties.register(item, PULL, (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                    : (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20.0F);
            ItemProperties.register(item, PULLING, (stack, level, entity, seed) -> using(entity, stack) ? 1.0F : 0.0F);
        }
        for (RegistryObject<Item> o : ofType("crossbow")) {
            Item item = o.get();
            ItemProperties.register(item, PULL, (stack, level, entity, seed) -> entity == null || CrossbowItem.isCharged(stack) ? 0.0F
                    : (float) (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / (float) CrossbowItem.getChargeDuration(stack));
            ItemProperties.register(item, PULLING, (stack, level, entity, seed) ->
                    using(entity, stack) && !CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(item, CHARGED, (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(item, FIREWORK, (stack, level, entity, seed) ->
                    CrossbowItem.isCharged(stack) && CrossbowItem.containsChargedProjectile(stack, Items.FIREWORK_ROCKET) ? 1.0F : 0.0F);
        }
        for (RegistryObject<Item> o : ofType("fishing_rod")) {
            ItemProperties.register(o.get(), CAST, (stack, level, entity, seed) -> {
                if (entity == null) return 0.0F;
                boolean main = entity.getMainHandItem() == stack;
                boolean off = entity.getOffhandItem() == stack;
                if (entity.getMainHandItem().getItem() instanceof FishingRodItem) off = false;
                return (main || off) && entity instanceof Player p && p.fishing != null ? 1.0F : 0.0F;
            });
        }
        for (RegistryObject<Item> o : ofType("shield")) {
            ItemProperties.register(o.get(), BLOCKING, (stack, level, entity, seed) -> using(entity, stack) ? 1.0F : 0.0F);
        }
        for (RegistryObject<Item> o : ofType("trident")) {
            ItemProperties.register(o.get(), THROWING, (stack, level, entity, seed) -> using(entity, stack) ? 1.0F : 0.0F);
        }
    }

    /** Los modelos de alas, mochilas, capas y colas puestas no son de ningún objeto: hay que cargarlos aparte. */
    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        for (TFSets.SetDef set : TFSets.all().values()) {
            for (TFSets.ItemDef def : set.items()) {
                if ("back".equals(def.type()) && def.worn() != null) event.register(new ResourceLocation(def.worn()));
            }
        }
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new BackLayer(renderer));
        }
    }

    /** Dibuja en la espalda el cosmético que el jugador lleva en el hueco del pecho (como hacen los plugins). */
    public static final class BackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public BackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!(stack.getItem() instanceof TFItemTypes.Cosmetic cosmetic) || cosmetic.wornModel() == null) return;
            if (player.isInvisible()) return;
            BakedModel model = Minecraft.getInstance().getModelManager().getModel(cosmetic.wornModel());
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            // Igual que un objeto puesto en la cabeza (así los colocan los plugins de cosméticos), pero siguiendo el cuerpo.
            pose.translate(0.0F, -0.25F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.scale(0.625F, -0.625F, -0.625F);
            Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.HEAD, false, pose, buffers, light,
                    OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
        }
    }
}
