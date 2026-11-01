/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.164, el dueño: «la cabeza del jugador traspasa la armadura y se ve mi skin salida del casco») con un casco o
 * sombrero cosmético de los sets en la cabeza (o el del Armario), no se dibuja la segunda capa de la skin (el «gorro»,
 * medio píxel más grande que la cabeza): con skins que la usan entera asomaba por las paredes del casco. Los que aun
 * así quedaban dentro de la cabeza se agrandaron (tools/ajustar_cascos.py).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFHeadHatClient {
    private static boolean hidden;

    private TFHeadHatClient() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        ItemStack shown = TFWardrobe.headLayer(p, p.getItemBySlot(EquipmentSlot.HEAD));
        PlayerModel<?> model = event.getRenderer().getModel();
        if (shown.getItem() instanceof TFItemTypes.Cosmetic && model.hat.visible) {
            model.hat.visible = false;
            hidden = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPost(RenderPlayerEvent.Post event) {
        if (hidden) {
            event.getRenderer().getModel().hat.visible = true;
            hidden = false;
        }
    }
}
