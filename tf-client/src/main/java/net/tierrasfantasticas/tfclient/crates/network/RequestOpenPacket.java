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
import net.tierrasfantasticas.tfclient.crates.crate.CrateOpeningService;
import net.tierrasfantasticas.tfclient.crates.crate.OpenAuthorization;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

public class RequestOpenPacket {
   private final BlockPos pos;
   private final boolean skipAnimation;

   public RequestOpenPacket(BlockPos pos, boolean skipAnimation) {
      this.pos = pos;
      this.skipAnimation = skipAnimation;
   }

   public static void encode(RequestOpenPacket msg, FriendlyByteBuf buf) {
      buf.writeBlockPos(msg.pos);
      buf.writeBoolean(msg.skipAnimation);
   }

   public static RequestOpenPacket decode(FriendlyByteBuf buf) {
      return new RequestOpenPacket(buf.readBlockPos(), buf.readBoolean());
   }

   public static void handle(RequestOpenPacket msg, Supplier<Context> ctx) {
      Context context = ctx.get();
      context.enqueueWork(
         () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
               if (player.level().isLoaded(msg.pos)
                  && !(player.distanceToSqr((double)msg.pos.getX() + 0.5, (double)msg.pos.getY() + 0.5, (double)msg.pos.getZ() + 0.5) > 64.0)) {
                  if (player.level().getBlockEntity(msg.pos) instanceof CrateBlockEntity be) {
                     if (OpenAuthorization.consume(player, msg.pos)) {
                        CrateConfig crate = be.getConfig();
                        ItemStack key = CrateItems.findUsableKey(player, crate);
                        if (key.isEmpty()) {
                           player.sendSystemMessage(Component.literal("§cNecesitas §f" + CrateItems.requiredKeyName(crate) + "§c para abrir esta caja."));
                        } else {
                           CrateOpeningService.open(player, crate, msg.pos, key, msg.skipAnimation);
                        }
                     }
                  }
               }
            }
         }
      );
      context.setPacketHandled(true);
   }
}
