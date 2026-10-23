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

public class OpenEditorPacket {
   private final CompoundTag configNbt;
   private final BlockPos pos;

   public OpenEditorPacket(CompoundTag configNbt) {
      this(configNbt, null);
   }

   public OpenEditorPacket(CompoundTag configNbt, BlockPos pos) {
      this.configNbt = configNbt;
      this.pos = pos;
   }

   public static void encode(OpenEditorPacket msg, FriendlyByteBuf buf) {
      buf.writeNbt(msg.configNbt);
      boolean hasPos = msg.pos != null;
      buf.writeBoolean(hasPos);
      if (hasPos) {
         buf.writeBlockPos(msg.pos);
      }
   }

   public static OpenEditorPacket decode(FriendlyByteBuf buf) {
      CompoundTag nbt = buf.readNbt();
      BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
      return new OpenEditorPacket(nbt, pos);
   }

   public static void handle(OpenEditorPacket msg, Supplier<Context> ctx) {
      Context context = ctx.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openEditor(msg.configNbt, msg.pos)));
      context.setPacketHandled(true);
   }
}
