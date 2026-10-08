package net.tierrasfantasticas.tfclient.claims.gui;

import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.claims.gui.AdminClaimSubMenuHandler;
import net.tierrasfantasticas.tfclient.claims.gui.AdminGlobalFlagsHandler;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;

public class AdminPanelHandler
extends ChestMenu {
    private static final int CLAIMS_PER_PAGE = 45;
    private final SimpleContainer inv;
    private final ServerPlayer viewer;
    private final int page;
    private final List<Claim> claims;

    public AdminPanelHandler(int i, Inventory inventory, int j) {
        this(i, inventory, new SimpleContainer(54), j);
    }

    private AdminPanelHandler(int i, Inventory inventory, SimpleContainer simplecontainer, int j) {
        super(MenuType.GENERIC_9x6, i, inventory, (Container)simplecontainer, 6);
        this.inv = simplecontainer;
        this.viewer = (ServerPlayer)inventory.player;
        this.page = j;
        this.claims = ClaimManager.getInstance().getAllClaims();
        this.rebuild();
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    private void rebuild() {
        ItemStack itemstack = AdminPanelHandler.withName(new ItemStack((ItemLike)Items.GRAY_STAINED_GLASS_PANE), (Component)Component.literal((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.inv.setItem(i, itemstack.copy());
        }
        int l = this.page * 45;
        int j = Math.min(l + 45, this.claims.size());
        for (int k = l; k < j; ++k) {
            this.inv.setItem(k - l, AdminPanelHandler.claimItem(this.claims.get(k)));
        }
        if (this.page > 0) {
            this.inv.setItem(45, AdminPanelHandler.withName(new ItemStack((ItemLike)Items.ARROW), (Component)Component.literal((String)"<< P\u00e1gina anterior").withStyle(ChatFormatting.AQUA)));
        }
        this.inv.setItem(46, AdminPanelHandler.withLore(AdminPanelHandler.withName(new ItemStack((ItemLike)Items.BOOK), (Component)Component.literal((String)"Estad\u00edsticas").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})), List.of(Component.literal((String)"Resumen del servidor").withStyle(ChatFormatting.GRAY))));
        this.inv.setItem(47, AdminPanelHandler.withLore(AdminPanelHandler.withName(new ItemStack((ItemLike)Items.COMPARATOR), (Component)Component.literal((String)"Flags Globales").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})), List.of(Component.literal((String)"PVP / Mob griefing / Fire").withStyle(ChatFormatting.GRAY))));
        boolean flag = ClaimManager.getInstance().isBypassing(this.viewer.getUUID());
        this.inv.setItem(48, AdminPanelHandler.withLore(AdminPanelHandler.withName(new ItemStack((ItemLike)Items.ENDER_EYE), (Component)Component.literal((String)("Modo Bypass: " + (flag ? "ON" : "OFF"))).withStyle(new ChatFormatting[]{flag ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.literal((String)"Ignorar protecciones de zonas").withStyle(ChatFormatting.GRAY))));
        this.inv.setItem(49, AdminPanelHandler.withName(new ItemStack((ItemLike)Items.BARRIER), (Component)Component.literal((String)"Cerrar panel").withStyle(ChatFormatting.WHITE)));
        if (j < this.claims.size()) {
            this.inv.setItem(53, AdminPanelHandler.withName(new ItemStack((ItemLike)Items.ARROW), (Component)Component.literal((String)"P\u00e1gina siguiente >>").withStyle(ChatFormatting.AQUA)));
        }
        this.broadcastChanges();
    }

    private static ItemStack claimItem(Claim claim) {
        ClaimTier claimtier = claim.getTier();
        Block block = claimtier != null ? ClaimBlocks.blockForTier(claimtier) : null;
        ItemStack itemstack = block != null ? new ItemStack((ItemLike)block.asItem()) : new ItemStack((ItemLike)Items.PAPER);
        MutableComponent mutablecomponent = Component.literal((String)(claim.getOwnerName() + " - " + claim.sizeLabel())).withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
        return AdminPanelHandler.withLore(AdminPanelHandler.withName(itemstack, (Component)mutablecomponent), List.of(Component.literal((String)("Posici\u00f3n: X:" + claim.getX() + " Z:" + claim.getZ())).withStyle(ChatFormatting.GRAY), Component.literal((String)("Dimensi\u00f3n: " + claim.getWorld())).withStyle(ChatFormatting.DARK_AQUA), Component.literal((String)"Clic para gestionar este claim").withStyle(ChatFormatting.YELLOW)));
    }

    static ItemStack withName(ItemStack itemstack, Component component) {
        itemstack.setHoverName(component);
        return itemstack;
    }

    static ItemStack withLore(ItemStack itemstack, List<Component> list) {
        ClaimBlocks.setLore(itemstack, list);
        return itemstack;
    }

    public void clicked(int i, int l, ClickType clicktype, Player player) {
        if (i >= 0 && i < 54) {
            if (i == 45 && this.page > 0) {
                AdminPanelHandler.open(this.viewer, this.page - 1);
            } else if (i == 53) {
                int j = (this.claims.size() - 1) / 45;
                if (this.page < j) {
                    AdminPanelHandler.open(this.viewer, this.page + 1);
                }
            } else if (i == 49) {
                this.viewer.closeContainer();
            } else if (i == 46) {
                this.viewer.closeContainer();
                this.viewer.server.getCommands().performPrefixedCommand(this.viewer.createCommandSourceStack(), "tf web claims stats");
            } else if (i == 47) {
                AdminGlobalFlagsHandler.open(this.viewer);
            } else if (i == 48) {
                ClaimManager.getInstance().toggleBypass(this.viewer.getUUID());
                this.rebuild();
            } else {
                int k = this.page * 45 + i;
                if (k < this.claims.size()) {
                    AdminClaimSubMenuHandler.open(this.viewer, this.claims.get(k).getClaimId());
                }
            }
        }
    }

    public static void open(ServerPlayer serverplayer, int i) {
        final int j = Math.max(0, i);
        NetworkHooks.openScreen((ServerPlayer)serverplayer, (MenuProvider)new MenuProvider(){

            public Component getDisplayName() {
                return Component.literal((String)"Panel de Administraci\u00f3n").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu createMenu(int k, Inventory inventory, Player player) {
                return new AdminPanelHandler(k, inventory, j);
            }
        });
    }

    public static Claim findClaim(UUID uuid) {
        for (Claim claim : ClaimManager.getInstance().getAllClaims()) {
            if (!claim.getClaimId().equals(uuid)) continue;
            return claim;
        }
        return null;
    }
}

