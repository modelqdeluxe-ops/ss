package net.tierrasfantasticas.tfclient.crates.config;

import java.util.HashMap;
import java.util.Map;

public final class ParticleNames {
   private static final Map<String, String> ES = new HashMap<>();

   private ParticleNames() {
   }

   private static void put(String id, String es) {
      ES.put(id, es);
   }

   public static String spanish(String path) {
      if (path == null) {
         return "(?)";
      } else {
         String name = ES.get(path);
         if (name != null) {
            return name;
         } else {
            StringBuilder sb = new StringBuilder();
            boolean cap = true;

            for (char c : path.toCharArray()) {
               if (c == '_') {
                  sb.append(' ');
                  cap = true;
               } else {
                  sb.append(cap ? Character.toUpperCase(c) : c);
                  cap = false;
               }
            }

            return sb.toString();
         }
      }
   }

   static {
      // (1.3.109) las propias de TF (CrateParticles): las de serie de las crates
      put("crate_glow", "✦ Luz mágica (TF)");
      put("crate_spark", "✦ Destello (TF)");
      put("crate_flash", "✦ Fogonazo (TF)");
      put("dust", "Polvo de color");
      put("dust_color_transition", "Polvo bicolor");
      put("enchant", "Encantamiento");
      put("enchanted_hit", "Golpe encantado");
      put("end_rod", "Vara del End");
      put("firework", "Fuego artificial");
      put("flame", "Llama");
      put("soul_fire_flame", "Llama de alma");
      put("soul", "Alma");
      put("smoke", "Humo");
      put("large_smoke", "Humo grande");
      put("campfire_cosy_smoke", "Humo de fogata");
      put("campfire_signal_smoke", "Humo de fogata grande");
      put("ash", "Ceniza");
      put("white_ash", "Ceniza blanca");
      put("crimson_spore", "Espora carmesí");
      put("warped_spore", "Espora distorsionada");
      put("cherry_leaves", "Pétalos de cerezo");
      put("spore_blossom_air", "Esporas de flor");
      put("falling_dust", "Polvo cayendo");
      put("composter", "Compostador");
      put("dragon_breath", "Aliento de dragón");
      put("dolphin", "Estela de delfín");
      put("totem_of_undying", "Tótem de la inmortalidad");
      put("witch", "Bruja");
      put("happy_villager", "Aldeano feliz");
      put("angry_villager", "Aldeano enfadado");
      put("heart", "Corazón");
      put("note", "Nota musical");
      put("portal", "Portal");
      put("reverse_portal", "Portal inverso");
      put("nautilus", "Nautilus");
      put("crit", "Crítico");
      put("electric_spark", "Chispa eléctrica");
      put("glow", "Brillo");
      put("glow_squid_ink", "Tinta luminosa");
      put("squid_ink", "Tinta de calamar");
      put("scrape", "Raspado");
      put("wax_on", "Aplicar cera");
      put("wax_off", "Retirar cera");
      put("sneeze", "Estornudo");
      put("sculk_charge", "Carga de sculk");
      put("sculk_charge_pop", "Pop de sculk");
      put("sculk_soul", "Alma de sculk");
      put("vibration", "Vibración");
      put("shriek", "Chillido");
      put("egg_crack", "Cascarón roto");
      put("trial_spawner_detection", "Detección de bestia");
      put("ambient_entity_effect", "Efecto de entidad ambiental");
      put("elder_guardian", "Guardián anciano");
      put("falling_nectar", "Néctar cayendo");
      put("falling_spore_blossom", "Esporas de flor cayendo");
      put("flash", "Destello");
      put("lava", "Lava");
      put("mycelium", "Micelio");
      put("sonic_boom", "Estruendo sónico");
      put("spit", "Escupitajo");
      put("sweep_attack", "Barrido de espada");
      put("snowflake", "Copo de nieve");
      put("small_flame", "Llama pequeña");
      put("soul_fire_flame", "Llama de alma");
      put("nautilus", "Náutilus");
      put("crimson_spore", "Espora carmesí");
      put("warped_spore", "Espora distorsionada");
      put("dripping_water", "Goteo de agua");
      put("falling_water", "Agua cayendo");
      put("dripping_lava", "Goteo de lava");
      put("falling_lava", "Lava cayendo");
      put("landing_lava", "Impacto de lava");
      put("dripping_honey", "Goteo de miel");
      put("falling_honey", "Miel cayendo");
      put("landing_honey", "Impacto de miel");
      put("dripping_obsidian_tear", "Lágrima de obsidiana");
      put("falling_obsidian_tear", "Lágrima cayendo");
      put("landing_obsidian_tear", "Impacto de lágrima");
      put("dripping_dripstone_lava", "Lava de estalactita");
      put("dripping_dripstone_water", "Agua de estalactita");
      put("falling_dripstone_lava", "Estalactita - lava");
      put("falling_dripstone_water", "Estalactita - agua");
      put("bubble", "Burbuja");
      put("bubble_column_up", "Columna de burbujas");
      put("bubble_pop", "Burbuja explotando");
      put("splash", "Salpicadura");
      put("rain", "Lluvia");
      put("underwater", "Bajo el agua");
      put("current_down", "Corriente descendente");
      put("fishing", "Pesca");
      put("explosion", "Explosión");
      put("explosion_emitter", "Emisor de explosión");
      put("poof", "Bocanada");
      put("cloud", "Nube");
      put("effect", "Efecto");
      put("entity_effect", "Efecto de entidad");
      put("instant_effect", "Efecto instantáneo");
      put("damage_indicator", "Indicador de daño");
      put("item", "Item");
      put("item_slime", "Slime");
      put("item_snowball", "Bola de nieve");
      put("block", "Bloque");
      put("block_marker", "Marcador de bloque");
      put("falling_dust_minecraft", "Polvo cayendo");
      put("end_rod_minecraft", "Vara del End");
      put("fs_dust_red", "✦ Polvo rojo");
      put("fs_dust_orange", "✦ Polvo naranja");
      put("fs_dust_gold", "✦ Polvo dorado");
      put("fs_dust_yellow", "✦ Polvo amarillo");
      put("fs_dust_lime", "✦ Polvo lima");
      put("fs_dust_green", "✦ Polvo verde");
      put("fs_dust_aqua", "✦ Polvo aqua");
      put("fs_dust_blue", "✦ Polvo azul");
      put("fs_dust_purple", "✦ Polvo morado");
      put("fs_dust_magenta", "✦ Polvo magenta");
      put("fs_dust_pink", "✦ Polvo rosa");
      put("fs_dust_white", "✦ Polvo blanco");
      put("fs_dust_tiny", "✦ Polvo diminuto");
      put("fs_dust_huge", "✦ Polvo enorme");
      put("fs_fade_fire", "✹ Degradado fuego");
      put("fs_fade_ice", "✹ Degradado hielo");
      put("fs_fade_void", "✹ Degradado vóid");
      put("fs_fade_toxic", "✹ Degradado tóxico");
      put("fs_fade_royal", "✹ Degradado real");
      put("fs_shard_gold", "◆ Esquirla de oro");
      put("fs_shard_diamond", "◆ Esquirla de diamante");
      put("fs_shard_amethyst", "◆ Esquirla de amatista");
      put("fs_shard_emerald", "◆ Esquirla de esmeralda");
      put("fs_burst_star", "★ Estrella del Nether");
      put("fs_burst_gem", "★ Gema");
      put("fs_soul_swirl", "◌ Espiral de alma");
   }
}
