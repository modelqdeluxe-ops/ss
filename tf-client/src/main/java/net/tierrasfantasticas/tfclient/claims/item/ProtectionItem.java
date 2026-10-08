package net.tierrasfantasticas.tfclient.claims.item;

import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ProtectionItem
extends Item {
    public final ClaimTier tier;

    public ProtectionItem(ClaimTier claimtier) {
        super(new Item.Properties().stacksTo(64));
        this.tier = claimtier;
    }

    public boolean isFoil(ItemStack itemstack) {
        return true;
    }

    public void appendHoverText(ItemStack itemstack, Level level, List<Component> list, TooltipFlag tooltipflag) {
        ChatFormatting chatformatting = ClaimBlocks.colorForTier(this.tier);
        list.add((Component)Component.literal((String)("Radio: " + this.tier.radius + " \u00b7 Hasta el cielo \u00b7 " + this.tier.height + " bajo la piedra")).withStyle(ChatFormatting.GRAY));
        list.add((Component)Component.literal((String)"Coloca para crear una protecci\u00f3n").withStyle(chatformatting));
    }
}

