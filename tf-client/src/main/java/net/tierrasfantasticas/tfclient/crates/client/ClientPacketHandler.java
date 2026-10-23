/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client;

import net.tierrasfantasticas.tfclient.crates.FSCrates;
import net.tierrasfantasticas.tfclient.crates.block.CrateBlockEntity;
import net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicScreen;
import net.tierrasfantasticas.tfclient.crates.client.screen.CrateEditorScreen;
import net.tierrasfantasticas.tfclient.crates.client.screen.CratePreOpenScreen;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ClientPacketHandler {
   private ClientPacketHandler() {
   }

   public static void openEditor(CompoundTag configNbt) {
      openEditor(configNbt, null);
   }

   public static void openEditor(CompoundTag configNbt, BlockPos pos) {
      CrateConfig cfg = configNbt == null ? new CrateConfig() : CrateConfig.load(configNbt);
      Minecraft.getInstance().setScreen(new CrateEditorScreen(cfg, pos));
   }

   public static void openPreview(CompoundTag configTag, BlockPos pos) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         try {
            CrateConfig config = CrateConfig.load(configTag);
            mc.setScreen(new CratePreOpenScreen(config, pos));
         } catch (Throwable var4) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo abrir la pantalla de pre-apertura: {}", var4.toString());
         }
      }
   }

   private static ItemStack winnerOf(List<ItemStack> cands, int winnerIndex) {
      if (cands == null || cands.isEmpty()) {
         return ItemStack.EMPTY;
      } else {
         return cands.get(Math.max(0, Math.min(cands.size() - 1, winnerIndex)));
      }
   }

   public static void playAnimation(BlockPos pos, String animationId, int rarityColor, int winnerIndex, int winnerRarity, CompoundTag candidates, UUID opener) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      BlockEntity blockEntity;
      if (level != null && (blockEntity = level.getBlockEntity(pos)) instanceof CrateBlockEntity) {
         CrateBlockEntity be = (CrateBlockEntity)blockEntity;
         List<ItemStack> cands = CrateBlockEntity.decodeItems(candidates);
         int[] candRarities = CrateBlockEntity.decodeRarities(candidates);
         boolean isOpener = mc.player != null && opener != null && mc.player.getUUID().equals(opener);
         boolean isInstant = "instant".equals(animationId);
         boolean cinematic = isOpener && cands != null && !cands.isEmpty() && !isInstant;
         if (cinematic) {
            try {
               net.minecraft.client.gui.screens.Screen cine = CrateCinematicScreen.create(be.getConfig(), rarityColor, winnerRarity, winnerIndex, cands, candRarities);
               // (1.3.103) sus partículas, las del bloque de verdad, también en la escena
               if (cine instanceof CrateCinematicScreen cs) cs.at(pos);
               mc.setScreen(cine);
            } catch (Throwable var17) {
               be.startAnimation(animationId, rarityColor, winnerIndex, winnerRarity, candRarities, cands);
               return;
            }

            be.startSceneLid(rarityColor, winnerRarity, true, winnerOf(cands, winnerIndex));
            return;
         }

         if (!isInstant) {
            be.startSceneLid(rarityColor, winnerRarity, false, winnerOf(cands, winnerIndex));
         } else {
            be.startAnimation(animationId, rarityColor, winnerIndex, winnerRarity, candRarities, cands);
         }
      }
   }
}
