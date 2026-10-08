package net.tierrasfantasticas.tfclient.claims;

import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.claims.item.ClaimItems;
import net.tierrasfantasticas.tfclient.claims.item.ProtectionItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class ClaimBlocks {
    public static final String NBT_KEY = "claimblocks";
    public static final String NBT_TIER_FIELD = "tier";

    private ClaimBlocks() {
    }

    /** El bloque de TF Claims de ese tamaño (la textura de TF de su color). */
    public static Block blockForTier(ClaimTier claimtier) {
        Block block = ClaimItems.blockFor(claimtier != null ? claimtier : ClaimTier.VALUES[0]);
        return block != null ? block : Blocks.WHITE_CONCRETE;
    }

    /** El concreto con el que Fantastic Claims marcaba cada tamaño: las zonas viejas lo siguen teniendo hasta que se cambia. */
    public static Block legacyBlockForTier(ClaimTier claimtier) {
        if (claimtier == null) {
            return Blocks.WHITE_CONCRETE;
        }
        return switch (claimtier.id) {
            case "claimstone_25x25" -> Blocks.LIGHT_GRAY_CONCRETE;
            case "claimstone_40x40" -> Blocks.CYAN_CONCRETE;
            case "claimstone_64x64" -> Blocks.LIGHT_BLUE_CONCRETE;
            case "claimstone_80x80" -> Blocks.LIME_CONCRETE;
            case "claimstone_100x100" -> Blocks.YELLOW_CONCRETE;
            case "claimstone_150x150" -> Blocks.ORANGE_CONCRETE;
            case "claimstone_250x250" -> Blocks.PINK_CONCRETE;
            case "claimstone_300x300" -> Blocks.MAGENTA_CONCRETE;
            case "claimstone_500x500" -> Blocks.PURPLE_CONCRETE;
            default -> Blocks.WHITE_CONCRETE;
        };
    }

    public static Item itemForTier(ClaimTier claimtier) {
        Item item = ClaimItems.itemFor(claimtier);
        return item != null ? item : ClaimBlocks.legacyBlockForTier(claimtier).asItem();
    }

    /** La piedra de esa zona: la de TF Claims o el concreto de Fantastic Claims. */
    public static boolean isClaimConcreteForTier(Block block, ClaimTier claimtier) {
        return block == ClaimBlocks.blockForTier(claimtier) || block == ClaimBlocks.legacyBlockForTier(claimtier);
    }

    public static boolean isAnyClaimConcrete(Block block) {
        for (ClaimTier claimtier : ClaimTier.VALUES) {
            if (!ClaimBlocks.isClaimConcreteForTier(block, claimtier)) continue;
            return true;
        }
        return false;
    }

    public static ChatFormatting colorForTier(ClaimTier claimtier) {
        String s1;
        if (claimtier == null) {
            return ChatFormatting.WHITE;
        }
        String s = claimtier.id;
        return switch (s1 = claimtier.id) {
            case "claimstone_10x10" -> ChatFormatting.WHITE;
            case "claimstone_25x25" -> ChatFormatting.GRAY;
            case "claimstone_40x40" -> ChatFormatting.AQUA;
            case "claimstone_64x64" -> ChatFormatting.BLUE;
            case "claimstone_80x80" -> ChatFormatting.GREEN;
            case "claimstone_100x100" -> ChatFormatting.YELLOW;
            case "claimstone_150x150" -> ChatFormatting.GOLD;
            case "claimstone_250x250" -> ChatFormatting.LIGHT_PURPLE;
            case "claimstone_300x300" -> ChatFormatting.LIGHT_PURPLE;
            case "claimstone_500x500" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    public static ItemStack createTierItem(ClaimTier claimtier, int i) {
        Item item = ClaimItems.itemFor(claimtier);
        ChatFormatting chatformatting = ClaimBlocks.colorForTier(claimtier);
        MutableComponent mutablecomponent = Component.literal((String)("Protecci\u00f3n " + claimtier.label())).setStyle(Style.EMPTY.withColor(chatformatting).withBold(Boolean.valueOf(true)).withItalic(Boolean.valueOf(false)));
        if (item != null) {
            ItemStack itemstack1 = new ItemStack((ItemLike)item, i);
            itemstack1.setHoverName((Component)mutablecomponent);
            return itemstack1;
        }
        ItemStack itemstack = new ItemStack((ItemLike)ClaimBlocks.itemForTier(claimtier), i);
        CompoundTag compoundtag = itemstack.getOrCreateTag();
        CompoundTag compoundtag1 = new CompoundTag();
        compoundtag1.putString(NBT_TIER_FIELD, claimtier.id);
        compoundtag.put(NBT_KEY, (Tag)compoundtag1);
        ListTag listtag = new ListTag();
        CompoundTag compoundtag2 = new CompoundTag();
        compoundtag2.putString("id", "minecraft:unbreaking");
        compoundtag2.putInt("lvl", 1);
        listtag.add(compoundtag2);
        compoundtag.put("Enchantments", (Tag)listtag);
        compoundtag.putInt("HideFlags", 1);
        itemstack.setHoverName((Component)mutablecomponent);
        ArrayList<Component> arraylist = new ArrayList<Component>();
        arraylist.add((Component)Component.literal((String)("Radio: " + claimtier.radius + " \u00b7 Hasta el cielo \u00b7 " + claimtier.height + " bajo la piedra")).withStyle(ChatFormatting.GRAY));
        arraylist.add((Component)Component.literal((String)"Coloca para crear una protecci\u00f3n").withStyle(chatformatting));
        ClaimBlocks.setLore(itemstack, arraylist);
        return itemstack;
    }

    public static void setLore(ItemStack itemstack, List<Component> list) {
        CompoundTag compoundtag = itemstack.getOrCreateTagElement("display");
        ListTag listtag = new ListTag();
        for (Component component : list) {
            listtag.add(StringTag.valueOf((String)Component.Serializer.toJson((Component)component)));
        }
        compoundtag.put("Lore", (Tag)listtag);
    }

    public static String readTierId(ItemStack itemstack) {
        if (itemstack != null && !itemstack.isEmpty()) {
            Item item = itemstack.getItem();
            if (item instanceof ProtectionItem) {
                ProtectionItem protectionitem = (ProtectionItem)item;
                return protectionitem.tier.id;
            }
            CompoundTag compoundtag = itemstack.getTag();
            if (compoundtag != null && compoundtag.contains(NBT_KEY, 10)) {
                CompoundTag compoundtag1 = compoundtag.getCompound(NBT_KEY);
                return !compoundtag1.contains(NBT_TIER_FIELD, 8) ? null : compoundtag1.getString(NBT_TIER_FIELD);
            }
            return null;
        }
        return null;
    }

    public static ClaimTier readTier(ItemStack itemstack) {
        String s = ClaimBlocks.readTierId(itemstack);
        return s == null ? null : ClaimTier.byId(s);
    }
}

