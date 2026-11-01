/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.server.PadClanBank;
import net.tierrasfantasticas.tfclient.pad.server.PadClanProgress;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;

/**
 * (1.3.159) Poner y quitar el cofre del banco y los estandartes del clan (el dueño: «que lo puedas romper, ojo solo el
 * líder o colíder los rompe, y se lo devuelve al inventario tipo las claim blocks»):
 * <ul>
 *   <li>Al ponerlo, es del clan de quien lo pone (sin clan no se puede). El cofre: uno por clan (si ya tiene uno puesto,
 *   no deja poner otro).</li>
 *   <li>Al romperlo: solo el líder o un colíder de su clan (o un administrador). No se rompe de verdad: desaparece y le
 *   vuelve al inventario (si está lleno, cae a sus pies). Los demás no pueden; las explosiones y los pistones tampoco
 *   (resistencia y PushReaction en el bloque).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class ClanBlockEvents {
    private ClanBlockEvents() {}

    private static boolean ours(Block b) {
        return b == ClanBlocks.BANK.get() || b == ClanBlocks.BANNER.get();
    }

    private static String kind(Block b) {
        return b == ClanBlocks.BANK.get() ? "cofres" : "estandartes";
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        BlockState state = event.getPlacedBlock();
        if (!ours(state.getBlock()) || !(event.getLevel() instanceof Level level) || level.isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) {
            event.setCanceled(true);
            return;
        }
        String clan = PadClans.clanIdOf(p.getUUID());
        boolean bank = state.getBlock() == ClanBlocks.BANK.get();
        if (clan == null) {
            p.displayClientMessage(Component.literal(bank ? "El cofre del banco es de un clan: únete a uno para ponerlo."
                    : "El estandarte es de un clan: únete a uno para ponerlo.").withStyle(ChatFormatting.RED), true);
            event.setCanceled(true);
            return;
        }
        if (bank) {
            String at = PadClanBank.chestOf(clan);
            if (at != null) {
                p.displayClientMessage(Component.literal("Tu clan ya tiene su cofre en " + at + ": rómpelo primero para moverlo.")
                        .withStyle(ChatFormatting.RED), true);
                event.setCanceled(true);
                return;
            }
        }
        BlockPos pos = event.getPos();
        PadClanBank.placed(kind(state.getBlock()), PadClanBank.posKey(level, pos), clan);
        if (!bank && level.getBlockEntity(pos) instanceof ClanBannerBlockEntity be) be.setClan(clan);
        ClanLooks.dirty();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (!ours(state.getBlock()) || !(event.getLevel() instanceof Level level) || level.isClientSide) return;
        event.setCanceled(true);
        if (!(event.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        BlockPos pos = event.getPos();
        String kind = kind(state.getBlock());
        String key = PadClanBank.posKey(level, pos);
        String owner = PadClanBank.ownerAt(kind, key);
        if (owner == null && state.getBlock() == ClanBlocks.BANNER.get() && level.getBlockEntity(pos) instanceof ClanBannerBlockEntity be && !be.clan().isEmpty()) {
            owner = be.clan();
        }
        boolean admin = p.hasPermissions(2);
        boolean officer;
        if (owner != null) {
            JsonObject c = PadClans.clanForEvents(owner);
            officer = c != null && PadClanProgress.treasurer(c, p.getUUID());
            if (c == null) officer = true; // el clan ya no existe: cualquiera lo quita
        } else {
            // uno de antes, sin dueño apuntado: lo quita un líder o colíder de cualquier clan
            String mine = PadClans.clanIdOf(p.getUUID());
            JsonObject c = mine == null ? null : PadClans.clanForEvents(mine);
            officer = c != null && PadClanProgress.treasurer(c, p.getUUID());
        }
        if (!officer && !admin) {
            p.displayClientMessage(Component.literal(state.getBlock() == ClanBlocks.BANK.get()
                    ? "Solo el líder o un colíder de su clan puede quitar este cofre." : "Solo el líder o un colíder de su clan puede quitar este estandarte.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        level.removeBlock(pos, false);
        PadClanBank.removed(kind, key);
        ItemStack back = new ItemStack(state.getBlock() == ClanBlocks.BANK.get() ? ClanBlocks.BANK_ITEM.get() : ClanBlocks.BANNER_ITEM.get());
        if (!p.getAbilities().instabuild || !p.getInventory().contains(back)) {
            if (!p.getInventory().add(back)) p.drop(back, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.1F);
        p.displayClientMessage(Component.literal(state.getBlock() == ClanBlocks.BANK.get() ? "El cofre del clan vuelve a tu inventario."
                : "El estandarte vuelve a tu inventario.").withStyle(ChatFormatting.GREEN), true);
        ClanLooks.dirty();
    }
}
