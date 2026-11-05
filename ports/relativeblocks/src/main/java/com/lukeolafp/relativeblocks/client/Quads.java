package com.lukeolafp.relativeblocks.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** Quads ya hechos, por tipo de render y por cara de recorte (null = sin recorte). */
@OnlyIn(Dist.CLIENT)
public final class Quads {
   private final Map<RenderType, Map<Direction, List<BakedQuad>>> byType = new LinkedHashMap<>();
   private final Map<RenderType, List<BakedQuad>> unculled = new LinkedHashMap<>();
   private final Map<RenderType, Boolean> ao = new LinkedHashMap<>();

   public void add(RenderType type, @Nullable Direction cull, BakedQuad quad, boolean ambientOcclusion) {
      if (cull == null) {
         this.unculled.computeIfAbsent(type, t -> new ArrayList<>()).add(quad);
      } else {
         this.byType.computeIfAbsent(type, t -> new EnumMap<>(Direction.class)).computeIfAbsent(cull, d -> new ArrayList<>()).add(quad);
      }
      // la oclusión ambiental del tipo: solo si todo lo de ese tipo la usa
      this.ao.merge(type, ambientOcclusion, Boolean::logicalAnd);
   }

   public List<BakedQuad> get(@Nullable RenderType type, @Nullable Direction cull) {
      if (type == null) {
         List<BakedQuad> all = new ArrayList<>();
         for (RenderType t : this.types()) {
            all.addAll(this.get(t, cull));
         }
         return all;
      }
      if (cull == null) {
         return this.unculled.getOrDefault(type, List.of());
      }
      Map<Direction, List<BakedQuad>> faces = this.byType.get(type);
      return faces == null ? List.of() : faces.getOrDefault(cull, List.of());
   }

   public List<RenderType> types() {
      List<RenderType> out = new ArrayList<>(this.ao.keySet());
      return out;
   }

   public boolean ambientOcclusion(@Nullable RenderType type) {
      if (type == null) {
         return !this.ao.containsValue(false);
      }
      return this.ao.getOrDefault(type, true);
   }

   public boolean isEmpty() {
      return this.ao.isEmpty();
   }
}
