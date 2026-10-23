/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.crate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class PityData extends SavedData {
   public static final String NAME = "fscrates_pity";
   private final Map<String, Integer> counts = new HashMap<>();

   public static PityData get(ServerLevel anyLevel) {
      ServerLevel overworld = anyLevel.getServer().overworld();
      return (PityData)overworld.getDataStorage().computeIfAbsent(PityData::load, PityData::new, "fscrates_pity");
   }

   private static String key(UUID player, String crateId) {
      return player.toString() + "|" + crateId;
   }

   public int incrementAndGet(UUID player, String crateId) {
      String k = key(player, crateId);
      int n = this.counts.getOrDefault(k, 0) + 1;
      this.counts.put(k, n);
      this.setDirty();
      return n;
   }

   public int getCount(UUID player, String crateId) {
      return this.counts.getOrDefault(key(player, crateId), 0);
   }

   public static PityData load(CompoundTag tag) {
      PityData data = new PityData();
      CompoundTag stored = tag.getCompound("counts");

      for (String k : stored.getAllKeys()) {
         data.counts.put(k, stored.getInt(k));
      }

      return data;
   }

   public CompoundTag save(CompoundTag tag) {
      CompoundTag stored = new CompoundTag();

      for (Entry<String, Integer> e : this.counts.entrySet()) {
         stored.putInt(e.getKey(), e.getValue());
      }

      tag.put("counts", stored);
      return tag;
   }
}
