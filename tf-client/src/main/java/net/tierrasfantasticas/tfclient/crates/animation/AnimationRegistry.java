/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnimationRegistry {
   private static final Map<String, CrateAnimation> REGISTRY = new LinkedHashMap<>();

   private AnimationRegistry() {
   }

   private static void reg(String id, String name, CrateAnimation.Style style, CrateAnimation.Theme theme, int duration, String desc) {
      REGISTRY.put(id, new CrateAnimation(id, name, style, theme, duration, desc));
   }

   public static CrateAnimation register(String id, String name, CrateAnimation.Style style, CrateAnimation.Theme theme, int duration, String desc) {
      CrateAnimation a = new CrateAnimation(id, name, style, theme, duration, desc);
      REGISTRY.put(id, a);
      return a;
   }

   public static CrateAnimation get(String id) {
      return REGISTRY.getOrDefault(id, REGISTRY.get("roulette"));
   }

   public static boolean exists(String id) {
      return REGISTRY.containsKey(id);
   }

   public static List<CrateAnimation> all() {
      return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
   }

   public static List<String> ids() {
      return Collections.unmodifiableList(new ArrayList<>(REGISTRY.keySet()));
   }

   public static String defaultId() {
      return "roulette";
   }

   static {
      reg(
         "roulette",
         "Ruleta clásica",
         CrateAnimation.Style.ROULETTE,
         CrateAnimation.Theme.CLASSIC,
         440,
         "Carrusel horizontal de items que desacelera hasta el premio."
      );
      reg("roulette_fast", "Ruleta rápida", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.CLASSIC, 348, "Como la clásica pero más corta.");
      reg("roulette_casino", "Ruleta casino", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.CASINO, 485, "Ruleta con confeti y fuegos al ganar.");
      reg("roulette_neon", "Ruleta neón", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.NEON, 485, "Ruleta con haz de luz de neón.");
      reg("roulette_infernal", "Ruleta infernal", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.INFERNAL, 485, "Ruleta con llamas y haz rojo.");
      reg(
         "roulette_celestial",
         "Ruleta celestial",
         CrateAnimation.Style.ROULETTE,
         CrateAnimation.Theme.CELESTIAL,
         501,
         "Ruleta con haz de luz divino y destellos."
      );
      reg("roulette_arcane", "Ruleta arcana", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.MAGIC, 501, "Ruleta con energía mágica y haz morado.");
      reg("roulette_ancient", "Ruleta ancestral", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.ANCIENT, 501, "Ruleta con runas y haz dorado.");
      reg("roulette_nature", "Ruleta natural", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.NATURE, 469, "Ruleta con esporas y pétalos.");
      reg("slot", "Tragamonedas", CrateAnimation.Style.SLOT_MACHINE, CrateAnimation.Theme.CASINO, 485, "Carrete vertical que para en el premio.");
      reg(
         "slot_jackpot",
         "Tragamonedas jackpot",
         CrateAnimation.Style.SLOT_MACHINE,
         CrateAnimation.Theme.CASINO,
         538,
         "Carrete vertical con fuegos artificiales al ganar."
      );
      reg(
         "slot_celestial", "Tragamonedas celestial", CrateAnimation.Style.SLOT_MACHINE, CrateAnimation.Theme.CELESTIAL, 513, "Carrete vertical con haz de luz."
      );
      reg("instant", "Instantáneo", CrateAnimation.Style.INSTANT, CrateAnimation.Theme.CLASSIC, 1, "Sin animación: entrega inmediata.");
   }
}
