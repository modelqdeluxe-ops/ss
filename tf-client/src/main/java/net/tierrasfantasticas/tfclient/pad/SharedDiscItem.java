/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

/**
 * (1.3.134, el dueño: «que podamos compartir las canciones del pad: al hacer clic en una canción, la opción de
 * compartir; me crea un disco en la mano, se lo doy a otro, lo consume y la canción se le guarda en una pestaña nueva,
 * COMPARTIDOS, de la música») El disco de una canción: lo crea la app Música (botón de compartir de cada canción, ver
 * PadMusicShare) con su link (el de internet, o tfaudio:&lt;sha1&gt; si era de la carpeta del que la comparte: la
 * guarda el servidor), título, artista, duración y quién la compartió. Clic derecho: se gasta y la canción se baja a la
 * música de quien lo usa, en COMPARTIDOS (ver MusicShare).
 */
public final class SharedDiscItem extends Item {
    /** La canción, dentro del NBT del disco. */
    public static final String TAG = "TFCancion";

    public SharedDiscItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    /** El disco de una canción. */
    public static ItemStack of(Item item, String link, String title, String artist, long durationMs, String from) {
        ItemStack stack = new ItemStack(item);
        CompoundTag c = new CompoundTag();
        c.putString("link", link);
        c.putString("titulo", title);
        c.putString("artista", artist);
        c.putLong("duracion", Math.max(0, durationMs));
        c.putString("de", from);
        stack.getOrCreateTag().put(TAG, c);
        return stack;
    }

    @Nullable
    private static CompoundTag song(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG) ? tag.getCompound(TAG) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag c = song(stack);
        String title = c == null ? "" : c.getString("titulo");
        return title.isBlank() ? super.getName(stack) : Component.translatable(getDescriptionId(stack)).append(": " + title);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag c = song(stack);
        if (c == null || c.getString("link").isBlank()) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);
        if (!TFPadNet.hasPad(sp)) {
            sp.displayClientMessage(Component.literal("Para guardarla en tu música necesitas el TF Client.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        PadSpeakerNet.toPlayer(sp, new PadSpeakerNet.Shared(c.getString("link"), c.getString("titulo"), c.getString("artista"),
                c.getLong("duracion"), c.getString("de")));
        level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag c = song(stack);
        if (c == null) return;
        if (!c.getString("artista").isBlank()) lines.add(Component.literal(c.getString("artista")).withStyle(ChatFormatting.GRAY));
        long ms = c.getLong("duracion");
        if (ms > 0) lines.add(Component.literal(String.format("%d:%02d", ms / 60000, ms / 1000 % 60)).withStyle(ChatFormatting.DARK_GRAY));
        if (!c.getString("de").isBlank()) lines.add(Component.literal("Te la comparte " + c.getString("de")).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.literal("Clic derecho: guárdala en tu Música (COMPARTIDOS)").withStyle(ChatFormatting.GOLD));
    }
}
