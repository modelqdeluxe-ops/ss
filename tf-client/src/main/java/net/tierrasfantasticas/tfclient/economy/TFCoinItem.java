package net.tierrasfantasticas.tfclient.economy;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * La moneda como objeto (de oro o verde). Solo debería verse como icono, pero quien tenga monedas sueltas en el
 * inventario (de kits o recompensas de antes, que las daban como objeto) las ingresa en su monedero con clic derecho.
 */
public final class TFCoinItem extends Item {
    private final boolean green;

    public TFCoinItem(Properties properties, boolean green) {
        super(properties);
        this.green = green;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            int n = stack.getCount();
            if (TFCoins.credit(sp, stack.copy())) {
                stack.setCount(0);
                sp.displayClientMessage(Component.literal("+" + n + (green ? (n == 1 ? " Fantastic Gacha Coin" : " Fantastic Gacha Coins") : (n == 1 ? " Fantastic Coin" : " Fantastic Coins"))
                        + " en tu monedero").withStyle(green ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
                sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.7F, 1.2F);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Clic derecho: ingrésalas en tu monedero").withStyle(ChatFormatting.GRAY));
    }
}
