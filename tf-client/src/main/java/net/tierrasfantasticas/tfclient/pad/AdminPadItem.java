package net.tierrasfantasticas.tfclient.pad;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * El pad de administrador: un objeto de verdad, solo para el staff (nivel de permisos 3). Clic derecho: abre el pad en
 * modo administrador, donde se configura el pad de todos (apps, tienda, kits, viajes, GTS, Comunidad, oficios y
 * ajustes). Si lo tiene alguien que no es staff, desaparece. Se da con /tf web pad admin [jugador].
 */
public final class AdminPadItem extends Item {
    public AdminPadItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.hasPermissions(3)) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("Este pad es solo para administradores.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TFPadClient::openAdmin);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** En manos de quien no es staff, desaparece. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && level.getGameTime() % 40 == 0 && !player.hasPermissions(3)) {
            stack.setCount(0);
            player.displayClientMessage(Component.literal("El pad de administrador es solo para el staff.").withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Solo para administradores").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Clic derecho: configura el pad del servidor").withStyle(ChatFormatting.GRAY));
    }
}
