/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.network;

import net.tierrasfantasticas.tfclient.crates.block.CrateBlockEntity;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.JsonCrateLoader;
import net.tierrasfantasticas.tfclient.crates.crate.CrateRegistry;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

public class SaveCratePacket {
   private final CompoundTag configNbt;
   private final BlockPos pos;

   public SaveCratePacket(CompoundTag configNbt) {
      this(configNbt, null);
   }

   public SaveCratePacket(CompoundTag configNbt, BlockPos pos) {
      this.configNbt = configNbt;
      this.pos = pos;
   }

   public static void encode(SaveCratePacket msg, FriendlyByteBuf buf) {
      buf.writeNbt(msg.configNbt);
      boolean hasPos = msg.pos != null;
      buf.writeBoolean(hasPos);
      if (hasPos) {
         buf.writeBlockPos(msg.pos);
      }
   }

   public static SaveCratePacket decode(FriendlyByteBuf buf) {
      CompoundTag nbt = buf.readNbt();
      BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
      return new SaveCratePacket(nbt, pos);
   }

   public static void handle(SaveCratePacket msg, Supplier<Context> ctx) {
      Context context = ctx.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null && player.hasPermissions(4) && msg.configNbt != null) {
            CrateConfig crate = CrateConfig.load(msg.configNbt);
            if (crate.id == null || crate.id.isBlank()) {
               crate.id = "crate_" + System.currentTimeMillis();
            }

            CrateRegistry.get(player.serverLevel()).put(crate);
            JsonCrateLoader.saveToFile(crate);
            if (msg.pos != null) {
               ServerLevel level = player.serverLevel();
               if (level.getBlockEntity(msg.pos) instanceof CrateBlockEntity crateBe) {
                  crateBe.setConfig(crate);
                  player.sendSystemMessage(Component.literal("§aCofre '" + crate.id + "' actualizado en el sitio."));
               } else {
                  player.sendSystemMessage(Component.literal("§eEl cofre ya no esta ahi; se guardo '" + crate.id + "' en el registro."));
               }

               if (crate.uniqueKeyEnabled) {
                  ItemStack uniqueKey = CrateItems.buildUniqueKey(crate);
                  if (!player.getInventory().add(uniqueKey)) {
                     player.drop(uniqueKey, false);
                  }

                  player.sendSystemMessage(Component.literal("§b✦ Llave única ✦§a de '" + crate.id + "' entregada a tu inventario."));
               }
            } else {
               ItemStack crateItem = CrateItems.buildCrate(crate);
               if (!player.getInventory().add(crateItem)) {
                  player.drop(crateItem, false);
               }

               if (crate.uniqueKeyEnabled) {
                  ItemStack uniqueKey = CrateItems.buildUniqueKey(crate);
                  if (!player.getInventory().add(uniqueKey)) {
                     player.drop(uniqueKey, false);
                  }

                  player.sendSystemMessage(Component.literal("§aCrate '" + crate.id + "' guardada y entregada §7(+ su §b✦ llave única ✦§7)."));
               } else {
                  player.sendSystemMessage(Component.literal("§aCrate '" + crate.id + "' guardada y entregada. Usa §e/tf crates key give§a para dar llaves."));
               }
            }
         }
      });
      context.setPacketHandled(true);
   }
}
