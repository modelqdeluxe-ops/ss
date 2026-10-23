/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.config;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class EsNames {
   private static final Map<String, String> ENCH = new HashMap<>();
   private static final Map<String, String> EFFECT = new HashMap<>();
   private static final Map<String, String> ATTR = new HashMap<>();

   private EsNames() {
   }

   private static void e(String id, String es) {
      ENCH.put("minecraft:" + id, es);
   }

   private static void f(String id, String es) {
      EFFECT.put("minecraft:" + id, es);
   }

   private static void a(String id, String es) {
      ATTR.put("minecraft:" + id, es);
   }

   public static String enchant(ResourceLocation rl) {
      if (rl == null) {
         return "(encantamiento)";
      } else {
         String s = ENCH.get(rl.toString());
         return s != null ? s : prettify(rl.getPath());
      }
   }

   public static String effect(ResourceLocation rl) {
      if (rl == null) {
         return "(efecto)";
      } else {
         String s = EFFECT.get(rl.toString());
         return s != null ? s : prettify(rl.getPath());
      }
   }

   public static String attribute(ResourceLocation rl) {
      if (rl == null) {
         return "(atributo)";
      } else {
         String s = ATTR.get(rl.toString());
         return s != null ? s : prettify(rl.getPath().replace('.', '_'));
      }
   }

   public static String attributeByRawId(String rawId) {
      if (rawId != null && !rawId.isEmpty()) {
         String s = ATTR.get(rawId.contains(":") ? rawId : "minecraft:" + rawId);
         return s != null ? s : prettify(rawId.substring(rawId.indexOf(58) + 1).replace('.', '_'));
      } else {
         return "(atributo)";
      }
   }

   public static String prettify(String path) {
      if (path != null && !path.isEmpty()) {
         StringBuilder sb = new StringBuilder();
         boolean cap = true;

         for (char c : path.toCharArray()) {
            if (c != '_' && c != '.') {
               sb.append(cap ? Character.toUpperCase(c) : c);
               cap = false;
            } else {
               sb.append(' ');
               cap = true;
            }
         }

         return sb.toString();
      } else {
         return "(?)";
      }
   }

   static {
      e("protection", "Protección");
      e("fire_protection", "Protección contra el fuego");
      e("feather_falling", "Caída de pluma");
      e("blast_protection", "Protección contra explosiones");
      e("projectile_protection", "Protección contra proyectiles");
      e("respiration", "Respiración");
      e("aqua_affinity", "Afinidad acuática");
      e("thorns", "Espinas");
      e("depth_strider", "Agilidad acuática");
      e("frost_walker", "Paso helado");
      e("binding_curse", "Maldición de vinculación");
      e("soul_speed", "Velocidad del alma");
      e("swift_sneak", "Sigilo veloz");
      e("sharpness", "Filo");
      e("smite", "Golpeo");
      e("bane_of_arthropods", "Perdición de los artrópodos");
      e("knockback", "Empuje");
      e("fire_aspect", "Aspecto ígneo");
      e("looting", "Botín");
      e("sweeping", "Filo de barrido");
      e("sweeping_edge", "Filo de barrido");
      e("efficiency", "Eficiencia");
      e("silk_touch", "Toque de seda");
      e("unbreaking", "Irrompibilidad");
      e("fortune", "Fortuna");
      e("power", "Poder");
      e("punch", "Retroceso");
      e("flame", "Fuego");
      e("infinity", "Infinidad");
      e("luck_of_the_sea", "Suerte marina");
      e("lure", "Atracción");
      e("loyalty", "Lealtad");
      e("impaling", "Empalamiento");
      e("riptide", "Propulsión acuática");
      e("channeling", "Canalización");
      e("multishot", "Disparo múltiple");
      e("quick_charge", "Carga rápida");
      e("piercing", "Perforación");
      e("mending", "Reparación");
      e("vanishing_curse", "Maldición de desaparición");
      f("speed", "Velocidad");
      f("slowness", "Lentitud");
      f("haste", "Prisa");
      f("mining_fatigue", "Fatiga minera");
      f("strength", "Fuerza");
      f("instant_health", "Curación instantánea");
      f("instant_damage", "Daño instantáneo");
      f("jump_boost", "Salto");
      f("nausea", "Náuseas");
      f("regeneration", "Regeneración");
      f("resistance", "Resistencia");
      f("fire_resistance", "Resistencia al fuego");
      f("water_breathing", "Respiración acuática");
      f("invisibility", "Invisibilidad");
      f("blindness", "Ceguera");
      f("night_vision", "Visión nocturna");
      f("hunger", "Hambre");
      f("weakness", "Debilidad");
      f("poison", "Veneno");
      f("wither", "Marchitamiento");
      f("health_boost", "Impulso de salud");
      f("absorption", "Absorción");
      f("saturation", "Saturación");
      f("glowing", "Brillo");
      f("levitation", "Levitación");
      f("luck", "Suerte");
      f("unluck", "Mala suerte");
      f("slow_falling", "Caída lenta");
      f("conduit_power", "Poder del conducto");
      f("dolphins_grace", "Gracia del delfín");
      f("bad_omen", "Mal presagio");
      f("hero_of_the_village", "Héroe de la aldea");
      f("darkness", "Oscuridad");
      a("generic.max_health", "Vida máxima");
      a("generic.follow_range", "Rango de seguimiento");
      a("generic.knockback_resistance", "Resistencia al empuje");
      a("generic.movement_speed", "Velocidad de movimiento");
      a("generic.flying_speed", "Velocidad de vuelo");
      a("generic.attack_damage", "Daño de ataque");
      a("generic.attack_knockback", "Empuje de ataque");
      a("generic.attack_speed", "Velocidad de ataque");
      a("generic.armor", "Armadura");
      a("generic.armor_toughness", "Dureza de armadura");
      a("generic.luck", "Suerte");
      a("horse.jump_strength", "Fuerza de salto (caballo)");
      a("zombie.spawn_reinforcements", "Refuerzos de zombi");
      a("generic.scale", "Escala (tamaño)");
      a("generic.step_height", "Altura de paso");
      a("generic.gravity", "Gravedad");
      a("generic.safe_fall_distance", "Distancia de caída segura");
      a("generic.fall_damage_multiplier", "Multiplicador de daño de caída");
      a("generic.jump_strength", "Fuerza de salto");
      a("generic.oxygen_bonus", "Bono de oxígeno");
      a("generic.burning_time", "Tiempo ardiendo");
      a("generic.explosion_knockback_resistance", "Resistencia a empuje de explosión");
      a("generic.water_movement_efficiency", "Eficiencia de movimiento en agua");
      a("generic.movement_efficiency", "Eficiencia de movimiento");
      a("generic.attack_damage", "Daño de ataque");
      a("player.entity_interaction_range", "Alcance de interacción con entidades");
      a("player.block_interaction_range", "Alcance de interacción con bloques");
      a("player.block_break_speed", "Velocidad de romper bloques");
      a("player.mining_efficiency", "Eficiencia de minado");
      a("player.sneaking_speed", "Velocidad al agacharse");
      a("player.submerged_mining_speed", "Velocidad de minado sumergido");
      a("player.sweeping_damage_ratio", "Proporción de daño en barrido");
   }
}
