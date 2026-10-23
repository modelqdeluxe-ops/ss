/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class UniqueKeyItem extends Item {
   public UniqueKeyItem() {
      super(new Properties().stacksTo(64));
   }

   public static String keyName(ItemStack stack) {
      if (stack != null && stack.hasTag()) {
         CompoundTag root = stack.getTag().getCompound("fscrates");
         if (root.contains("keyName")) {
            return root.getString("keyName");
         }
      }

      return "";
   }

   public Component getName(ItemStack stack) {
      String n = keyName(stack);
      return n != null && !n.isBlank() ? Component.literal(n.replace('&', '§')) : Component.literal("✦ Llave de Crate ✦").withStyle(ChatFormatting.AQUA);
   }

   public boolean isFoil(ItemStack stack) {
      return true;
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      String crateName = "";
      String crateId = "";
      if (stack != null && stack.hasTag()) {
         CompoundTag root = stack.getTag().getCompound("fscrates");
         crateName = root.getString("crateName");
         crateId = root.getString("crateId");
      }

      String show = crateName != null && !crateName.isBlank() ? crateName.replace('&', '§') : crateId;
      tooltip.add(Component.literal("§7Esta llave abre: §r" + show));
   }
}
