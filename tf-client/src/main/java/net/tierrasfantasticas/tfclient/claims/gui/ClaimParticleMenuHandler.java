package net.tierrasfantasticas.tfclient.claims.gui;

import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimMenuHandler;
import net.tierrasfantasticas.tfclient.claims.render.ParticleBorder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.network.NetworkHooks;

public class ClaimParticleMenuHandler
extends ChestMenu {
    private static final int[] PARTICLE_SLOTS = new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35};
    private static final int SLOT_TOGGLE = 4;
    private static final int SLOT_DENSITY_DOWN = 48;
    private static final int SLOT_DENSITY_INFO = 49;
    private static final int SLOT_DENSITY_UP = 50;
    private static final int SLOT_BACK = 45;
    private final SimpleContainer chest;
    private final Claim claim;
    private final ServerPlayer viewer;
    private final int returnPage;

    public ClaimParticleMenuHandler(int i, Inventory inventory, Claim claim, int j) {
        this(i, inventory, new SimpleContainer(54), claim, j);
    }

    private ClaimParticleMenuHandler(int i, Inventory inventory, SimpleContainer simplecontainer, Claim claim, int j) {
        super(MenuType.GENERIC_9x6, i, inventory, (Container)simplecontainer, 6);
        this.chest = simplecontainer;
        this.claim = claim;
        this.viewer = (ServerPlayer)inventory.player;
        this.returnPage = j;
        this.rebuild();
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    private void rebuild() {
        ItemStack itemstack = ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.GRAY_STAINED_GLASS_PANE), (Component)Component.literal((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.chest.setItem(i, itemstack.copy());
        }
        boolean flag1 = this.claim.getFlags().showParticles;
        this.chest.setItem(4, ClaimParticleMenuHandler.withLore(ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)(flag1 ? Items.LIME_DYE : Items.GRAY_DYE)), (Component)Component.literal((String)(flag1 ? "Part\u00edculas: ACTIVAS" : "Part\u00edculas: INACTIVAS")).withStyle(new ChatFormatting[]{flag1 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.literal((String)"Llena tu protecci\u00f3n con part\u00edculas.").withStyle(ChatFormatting.GRAY), Component.literal((String)("Clic para " + (flag1 ? "desactivar" : "activar"))).withStyle(ChatFormatting.YELLOW))));
        String s = this.claim.getFlags().borderParticle;
        String[] astring = ParticleBorder.availableParticles();
        for (int j = 0; j < astring.length && j < PARTICLE_SLOTS.length; ++j) {
            String s1 = astring[j];
            boolean flag = s1.equals(s);
            this.chest.setItem(PARTICLE_SLOTS[j], ClaimParticleMenuHandler.withLore(ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)ClaimParticleMenuHandler.iconFor(s1)), (Component)Component.literal((String)ParticleBorder.particleLabel(s1)).withStyle(new ChatFormatting[]{flag ? ChatFormatting.GREEN : ChatFormatting.AQUA, ChatFormatting.BOLD})), List.of(Component.literal((String)(flag ? "\u2714 Part\u00edcula seleccionada" : "Clic para usar esta part\u00edcula")).withStyle(flag ? ChatFormatting.GREEN : ChatFormatting.GRAY), Component.literal((String)"Activa las part\u00edculas autom\u00e1ticamente").withStyle(ChatFormatting.DARK_GRAY))));
        }
        int k = this.claim.getFlags().particleDensity;
        this.chest.setItem(48, ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.REDSTONE), (Component)Component.literal((String)"- Menos part\u00edculas (-5)").withStyle(ChatFormatting.RED)));
        this.chest.setItem(49, ClaimParticleMenuHandler.withLore(ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.GLOWSTONE_DUST), (Component)Component.literal((String)("Densidad: " + k)).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})), List.of(Component.literal((String)"Cantidad de part\u00edculas por emisi\u00f3n.").withStyle(ChatFormatting.GRAY), Component.literal((String)"Rango 1 - 200. Recomendado 5 - 40.").withStyle(ChatFormatting.DARK_GRAY))));
        this.chest.setItem(50, ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.GLOWSTONE), (Component)Component.literal((String)"+ M\u00e1s part\u00edculas (+5)").withStyle(ChatFormatting.GREEN)));
        this.chest.setItem(45, ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.ARROW), (Component)Component.literal((String)"<< Volver").withStyle(ChatFormatting.AQUA)));
        this.broadcastChanges();
    }

    public static Item iconFor(String s) {
        switch (s) {
            case "minecraft:heart": {
                return Items.POPPY;
            }
            case "minecraft:flame": {
                return Items.BLAZE_POWDER;
            }
            case "minecraft:small_flame": {
                return Items.TORCH;
            }
            case "minecraft:soul_fire_flame": {
                return Items.SOUL_TORCH;
            }
            case "minecraft:soul": {
                return Items.SOUL_LANTERN;
            }
            case "minecraft:end_rod": {
                return Items.END_ROD;
            }
            case "minecraft:crit": {
                return Items.IRON_SWORD;
            }
            case "minecraft:enchanted_hit": {
                return Items.DIAMOND_SWORD;
            }
            case "minecraft:enchant": {
                return Items.ENCHANTED_BOOK;
            }
            case "minecraft:dragon_breath": {
                return Items.DRAGON_BREATH;
            }
            case "minecraft:portal": {
                return Items.ENDER_PEARL;
            }
            case "minecraft:reverse_portal": {
                return Items.ENDER_EYE;
            }
            case "minecraft:cloud": {
                return Items.WHITE_WOOL;
            }
            case "minecraft:electric_spark": {
                return Items.AMETHYST_SHARD;
            }
            case "minecraft:wax_on": {
                return Items.HONEYCOMB;
            }
            case "minecraft:glow": {
                return Items.GLOW_INK_SAC;
            }
            case "minecraft:totem_of_undying": {
                return Items.TOTEM_OF_UNDYING;
            }
            case "minecraft:firework": {
                return Items.FIREWORK_ROCKET;
            }
            case "minecraft:note": {
                return Items.NOTE_BLOCK;
            }
            case "minecraft:snowflake": {
                return Items.SNOWBALL;
            }
            case "minecraft:cherry_leaves": {
                return Items.CHERRY_LEAVES;
            }
            case "minecraft:spore_blossom_air": {
                return Items.SPORE_BLOSSOM;
            }
            case "minecraft:sculk_soul": {
                return Items.SCULK_CATALYST;
            }
            case "minecraft:lava": {
                return Items.LAVA_BUCKET;
            }
            case "minecraft:splash": {
                return Items.WATER_BUCKET;
            }
            case "minecraft:witch": {
                return Items.FERMENTED_SPIDER_EYE;
            }
        }
        return Items.EMERALD;
    }

    public void clicked(int i, int l, ClickType clicktype, Player player) {
        if (i >= 0 && i < 54) {
            if (i == 4) {
                this.claim.getFlags().showParticles = !this.claim.getFlags().showParticles;
                ClaimManager.getInstance().save();
                this.rebuild();
            } else if (i == 45) {
                ClaimMenuHandler.open(this.viewer, this.claim, this.returnPage);
            } else if (i != 48 && i != 50) {
                String[] astring = ParticleBorder.availableParticles();
                for (int k = 0; k < PARTICLE_SLOTS.length && k < astring.length; ++k) {
                    if (PARTICLE_SLOTS[k] != i) continue;
                    this.claim.getFlags().borderParticle = astring[k];
                    this.claim.getFlags().showParticles = true;
                    ClaimManager.getInstance().save();
                    this.viewer.displayClientMessage((Component)Component.literal((String)("\u2714 Part\u00edcula: " + ParticleBorder.particleLabel(astring[k]))).withStyle(ChatFormatting.GREEN), true);
                    this.rebuild();
                    return;
                }
            } else {
                int j = this.claim.getFlags().particleDensity + (i == 50 ? 5 : -5);
                this.claim.getFlags().particleDensity = Math.max(1, Math.min(200, j));
                ClaimManager.getInstance().save();
                this.rebuild();
            }
        }
    }

    private static ItemStack withName(ItemStack itemstack, Component component) {
        itemstack.setHoverName(component);
        return itemstack;
    }

    private static ItemStack withLore(ItemStack itemstack, List<Component> list) {
        ClaimBlocks.setLore(itemstack, list);
        return itemstack;
    }

    public static void open(ServerPlayer serverplayer, final Claim claim, final int i) {
        NetworkHooks.openScreen((ServerPlayer)serverplayer, (MenuProvider)new MenuProvider(){

            public Component getDisplayName() {
                return Component.literal((String)"Part\u00edculas de la protecci\u00f3n").withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu createMenu(int j, Inventory inventory, Player player) {
                return new ClaimParticleMenuHandler(j, inventory, claim, i);
            }
        });
    }
}

