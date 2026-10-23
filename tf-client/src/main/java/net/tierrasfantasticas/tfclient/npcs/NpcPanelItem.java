/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.npcs;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * (1.3.101) El panel de control de NPC: lo da /tf npc a quien lo ejecuta (staff). Clic derecho al aire: abre el panel;
 * clic derecho a un NPC de escena: lo elige (BossEntity.mobInteract → TFNpcs.panelInteract) y abre el panel con él.
 */
public final class NpcPanelItem extends Item {
    public NpcPanelItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.hasPermissions(TFNpcs.LEVEL)) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("El panel de NPC es solo para el staff.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer sp) TFNpcs.send(sp, "", true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Solo para el staff").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Clic derecho: abre el panel (colocar NPC de escenas)").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Clic derecho a un NPC: elegirlo para moverlo, girarlo o vestirlo").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("En la mano: se ven los pins, las rutas y los marcadores").withStyle(ChatFormatting.GRAY));
    }
}
