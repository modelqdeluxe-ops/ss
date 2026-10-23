/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.network;

import net.tierrasfantasticas.tfclient.crates.client.ClientPacketHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class OpenPreviewPacket {
   private final CompoundTag config;
   private final BlockPos pos;

   public OpenPreviewPacket(CompoundTag config, BlockPos pos) {
      this.config = config;
      this.pos = pos;
   }

   public static void encode(OpenPreviewPacket msg, FriendlyByteBuf buf) {
      buf.writeNbt(msg.config);
      buf.writeBlockPos(msg.pos);
   }

   public static OpenPreviewPacket decode(FriendlyByteBuf buf) {
      return new OpenPreviewPacket(buf.readNbt(), buf.readBlockPos());
   }

   public static void handle(OpenPreviewPacket msg, Supplier<Context> ctx) {
      Context context = ctx.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openPreview(msg.config, msg.pos)));
      context.setPacketHandled(true);
   }
}
