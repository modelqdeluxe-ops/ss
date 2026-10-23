/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.crate;

import net.tierrasfantasticas.tfclient.crates.block.CrateBlockEntity;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;
import net.tierrasfantasticas.tfclient.crates.config.RewardEntry;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import net.tierrasfantasticas.tfclient.crates.network.FSNetwork;
import net.tierrasfantasticas.tfclient.crates.network.PlayAnimationPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class CrateOpeningService {
   private static final Random RANDOM = new Random();

   private CrateOpeningService() {
   }

   public static CrateOpeningService.Result open(ServerPlayer player, CrateConfig crate, BlockPos pos, ItemStack keyStack, boolean skipAnimation) {
      CooldownData cooldowns = CooldownData.get(player.serverLevel());
      BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
      CrateBlockEntity b;
      CrateBlockEntity crateBe = blockEntity instanceof CrateBlockEntity ? (b = (CrateBlockEntity)blockEntity) : null;
      if (crate.requiredPermission != null && !crate.requiredPermission.isBlank() && !player.hasPermissions(4)) {
         player.sendSystemMessage(Component.literal("§cNo tienes permiso para abrir esta crate."));
         return CrateOpeningService.Result.NO_PERMISSION;
      } else if (crate.openOncePerPlayer && crateBe != null && crateBe.hasOpenedBy(player.getUUID())) {
         player.sendSystemMessage(Component.literal("§cYa abriste esta crate. Es de §eun solo uso por jugador§c."));
         return CrateOpeningService.Result.ALREADY_OPENED;
      } else {
         long remaining = cooldowns.remainingSeconds(player.getUUID(), crate.id);
         if (remaining > 0L) {
            player.sendSystemMessage(Component.literal("§cDebes esperar §e" + remaining + "s§c antes de abrir esta crate de nuevo."));
            return CrateOpeningService.Result.ON_COOLDOWN;
         } else if (crate.rewards.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cEsta crate no tiene recompensas configuradas."));
            return CrateOpeningService.Result.EMPTY;
         } else {
            if (crate.consumeKey && keyStack != null && !keyStack.isEmpty()) {
               keyStack.shrink(1);
            }

            PityData pity = PityData.get(player.serverLevel());
            int opens = pity.incrementAndGet(player.getUUID(), crate.id);
            Rarity spinRarity = crate.pityEnabled && crate.pityInterval > 0 && opens % crate.pityInterval == 0
               ? (crate.pityRarity == null ? Rarity.LEGENDARY : crate.pityRarity)
               : crate.rollRarity(RANDOM);
            spinRarity = LootEngine.resolveRarityWithItems(crate, spinRarity);
            ArrayList<RewardEntry> rolled = new ArrayList<>();

            for (RewardEntry g : crate.rewards) {
               if (g.guaranteed) {
                  rolled.add(g);
               }
            }

            RewardEntry headline = null;

            for (int i = 0; i < Math.max(1, crate.rolls); i++) {
               RewardEntry pick = LootEngine.pickFromPool(crate, spinRarity, RANDOM);
               if (pick != null) {
                  rolled.add(pick);
                  if (headline == null) {
                     headline = pick;
                  }
               }
            }

            List<RewardEntry> spinPool = LootEngine.poolForDisplay(crate, spinRarity);
            if (headline == null) {
               headline = spinPool.isEmpty() ? crate.rewards.get(0) : spinPool.get(0);
            }

            if (spinPool.isEmpty()) {
               spinPool = new ArrayList<>();
               spinPool.add(headline);
            }

            Rarity effectRarity = headline.effectiveRarity(crate.rarity);
            ArrayList<ItemStack> pool = new ArrayList<>();
            ArrayList<Integer> poolRarities = new ArrayList<>();
            int winnerIndex = 0;

            for (RewardEntry r2 : spinPool) {
               if (pool.size() >= 24) {
                  break;
               }

               if (r2 == headline) {
                  winnerIndex = pool.size();
               }

               pool.add(iconFor(r2));
               poolRarities.add(effectRarity.ordinal());
            }

            if (pool.isEmpty()) {
               pool.add(iconFor(headline));
               poolRarities.add(effectRarity.ordinal());
               winnerIndex = 0;
            }

            String animId = skipAnimation && crate.allowSkip ? "instant" : crate.animationId;
            PlayAnimationPacket packet = new PlayAnimationPacket(
               pos,
               animId,
               effectRarity.rgb(),
               winnerIndex,
               effectRarity.ordinal(),
               candidatesNbt(pool, poolRarities),
               player.getUUID().getMostSignificantBits(),
               player.getUUID().getLeastSignificantBits()
            );
            FSNetwork.sendToNear(player.serverLevel(), pos, 48.0, packet);
            // (1.3.99) el premio llega al acabar la cinemática del santuario (20 s): antes llegaba a los 12,7 s y los
            // avisos del juego («New Recipes Unlocked») salían encima del premio
            int delay = animId.equals("instant") ? 4 : CrateCinematicTiming.TOTAL_TICKS + 2;
            DelayedDelivery.schedule(player, crate, rolled, delay);
            cooldowns.startCooldown(player.getUUID(), crate.id, crate.cooldownSeconds);
            if (crate.openOncePerPlayer && crateBe != null) {
               crateBe.markOpenedBy(player.getUUID());
            }

            return CrateOpeningService.Result.OK;
         }
      }
   }

   private static ItemStack iconFor(RewardEntry r) {
      if (r == null) {
         return new ItemStack(Items.PAPER);
      } else {
         return switch (r.type) {
            case ITEM -> r.item != null && !r.item.isEmpty() ? r.item.copy() : new ItemStack(Items.PAPER);
            case KEY -> CrateItems.buildKey();
            case XP -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case EFFECT -> new ItemStack(Items.POTION);
            case COMMAND -> new ItemStack(Items.COMMAND_BLOCK);
         };
      }
   }

   private static CompoundTag candidatesNbt(List<ItemStack> pool, List<Integer> rarities) {
      CompoundTag wrap = new CompoundTag();
      ListTag list = new ListTag();

      for (ItemStack s : pool) {
         CompoundTag t = new CompoundTag();
         (s != null && !s.isEmpty() ? s : new ItemStack(Items.PAPER)).save(t);
         list.add(t);
      }

      wrap.put("items", list);
      int[] rar = new int[rarities.size()];

      for (int i = 0; i < rar.length; i++) {
         rar[i] = rarities.get(i);
      }

      wrap.putIntArray("rar", rar);
      return wrap;
   }

   public static enum Result {
      OK,
      ON_COOLDOWN,
      NO_PERMISSION,
      ALREADY_OPENED,
      EMPTY;
   }
}
