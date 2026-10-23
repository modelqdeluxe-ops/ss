/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class CrateStyles {
   public static final String AUTO = "";
   private static final Map<String, CrateStyles.Style> STYLES = new LinkedHashMap<>();

   private CrateStyles() {
   }

   private static ResourceLocation rl(String path) {
      return new ResourceLocation("fscrates", "block/" + path);
   }

   private static void reg(String id, String display, String model, float scale) {
      STYLES.put(id, new CrateStyles.Style(id, display, rl(model), null, null, scale));
   }

   private static void regLid(String id, String display, String model, String lidModel, float[] hinge, float scale) {
      STYLES.put(id, new CrateStyles.Style(id, display, rl(model), rl(lidModel), hinge, scale));
   }

   private static void regCine(String id, String display, String model, String lidModel, float[] hinge, float scale) {
      STYLES.put(id, new CrateStyles.Style(id, display, rl(model), rl(lidModel), hinge, scale, true));
   }

   public static CrateStyles.Style get(String id) {
      return id == null ? null : STYLES.get(id);
   }

   public static Collection<CrateStyles.Style> all() {
      return STYLES.values();
   }

   public static List<String> cycleIds() {
      ArrayList<String> ids = new ArrayList<>();
      ids.add("");
      ids.addAll(STYLES.keySet());
      return ids;
   }

   public static String displayName(String id) {
      CrateStyles.Style s = get(id);
      return s != null ? s.display : "§7Auto (por rareza)";
   }

   static {
      regLid("classic_common", "§fCofre Clásico Común", "crate_common", "crate_common_lid", new float[]{0.5F, 0.55052F, 0.79445F}, 1.9301F);
      regLid("classic_rare", "§bCofre Clásico Raro", "crate_rare", "crate_rare_lid", new float[]{0.5F, 0.57405F, 0.81105F}, 1.583F);
      regLid("classic_epic", "§dCofre Clásico Épico", "crate_epic", "crate_epic_lid", new float[]{0.5F, 0.52543F, 0.91443F}, 1.7538F);
      regLid("classic_legendary", "§6Cofre Clásico Legendario", "crate_legendary", "crate_legendary_lid", new float[]{0.47306F, 0.3235F, 0.71127F}, 1.8966F);
      regLid("classic_mythic", "§cCofre Clásico Mítico", "crate_mythic", "crate_mythic_lid", new float[]{0.5F, 0.45352F, 0.84924F}, 1.7909F);
      regLid("crate_lvl1", "§7Cofre de Madera", "crate_lvl1", "crate_lvl1_lid", new float[]{0.5F, 0.5625F, 1.125F}, 0.8299F);
      regLid("crate_lvl2", "§eCofre Dorado", "crate_lvl2", "crate_lvl2_lid", new float[]{0.5F, 0.60625F, 1.21609F}, 0.7613F);
      regLid("crate_lvl3", "§bCofre de Diamante", "crate_lvl3", "crate_lvl3_lid", new float[]{0.5F, 0.70865F, 1.19063F}, 0.8F);
      regLid("crate_lvl4", "§dCofre Arcano", "crate_lvl4", "crate_lvl4_lid", new float[]{0.5F, 0.98539F, 1.59062F}, 0.6047F);
      regLid("elite_icechest", "§bCofre de Hielo", "elite_icechest", "elite_icechest_lid", new float[]{0.5F, 0.66601F, 0.90869F}, 0.724F);
      regLid("elite_lavachest", "§cCofre de Lava", "elite_lavachest", "elite_lavachest_lid", new float[]{0.5F, 0.5F, 0.90625F}, 0.8122F);
      regLid("elite_naturechest", "§aCofre de la Naturaleza", "elite_naturechest", "elite_naturechest_lid", new float[]{0.5F, 0.71014F, 0.79987F}, 0.5935F);
      regLid("elite_windchest", "§fCofre del Viento", "elite_windchest", "elite_windchest_lid", new float[]{0.5F, 0.5F, 1.00241F}, 0.7433F);
      regLid("elite_lovechest", "§dCofre del Amor", "elite_lovechest", "elite_lovechest_lid", new float[]{0.5F, 0.76326F, 0.72751F}, 1.2242F);
      regLid("dedou_1", "§fCofre Rareza Común", "dedou_1", "dedou_1_lid", new float[]{0.5F, 0.47396F, 1.17708F}, 1.0615F);
      regLid("dedou_2", "§aCofre Rareza Raro", "dedou_2", "dedou_2_lid", new float[]{0.5F, 0.47396F, 1.17708F}, 1.0615F);
      regLid("dedou_3", "§5Cofre Rareza Épico", "dedou_3", "dedou_3_lid", new float[]{0.5F, 0.47396F, 1.17708F}, 1.0615F);
      regLid("dedou_4", "§6Cofre Rareza Legendario", "dedou_4", "dedou_4_lid", new float[]{0.5F, 0.19102F, 0.77289F}, 2.6339F);
      regLid("dedou_5", "§bCofre Rareza Divino", "dedou_5", "dedou_5_lid", new float[]{0.5F, 0.47396F, 1.17708F}, 1.0615F);
      regLid("blackgold_1", "§eCofre Black & Gold I", "blackgold_1", "blackgold_1_lid", new float[]{0.5F, 0.62725F, 1.20228F}, 0.8047F);
      regLid("blackgold_2", "§eCofre Black & Gold II", "blackgold_2", "blackgold_2_lid", new float[]{0.5F, 0.49331F, 1.08036F}, 0.6561F);
      regLid("blackgold_3", "§eCofre Black & Gold III", "blackgold_3", "blackgold_3_lid", new float[]{0.5F, 0.47524F, 0.97674F}, 0.8524F);
      regLid("greek_1b", "§eCaja Griega I·A", "greek_1b", "greek_1b_lid", new float[]{0.5F, 0.60938F, 1.3125F}, 0.7946F);
      regLid("greek_1g", "§eCaja Griega I·B", "greek_1g", "greek_1g_lid", new float[]{0.5F, 0.60938F, 1.3125F}, 0.7946F);
      regLid("greek_1i", "§eCaja Griega I·C", "greek_1i", "greek_1i_lid", new float[]{0.5F, 0.60938F, 1.3125F}, 0.7946F);
      regLid("greek_2b", "§6Caja Griega II·A", "greek_2b", "greek_2b_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8939F);
      regLid("greek_2g", "§6Caja Griega II·B", "greek_2g", "greek_2g_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8939F);
      regLid("greek_2i", "§6Caja Griega II·C", "greek_2i", "greek_2i_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8939F);
      regLid("greek_3b", "§bCaja Griega III·A", "greek_3b", "greek_3b_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8492F);
      regLid("greek_3g", "§bCaja Griega III·B", "greek_3g", "greek_3g_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8492F);
      regLid("greek_3i", "§bCaja Griega III·C", "greek_3i", "greek_3i_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8492F);
      regLid("greek_4b", "§dCaja Griega IV·A", "greek_4b", "greek_4b_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.7077F);
      regLid("greek_4g", "§dCaja Griega IV·B", "greek_4g", "greek_4g_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.7077F);
      regLid("greek_4i", "§dCaja Griega IV·C", "greek_4i", "greek_4i_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.7077F);
      regLid("toffy_explosive", "§cCofre Explosivo", "toffy_explosive", "toffy_explosive_lid", new float[]{0.5F, 0.6875F, 0.9375F}, 1.0222F);
      regLid("toffy_inhabitant", "§dCofre Habitante", "toffy_inhabitant", "toffy_inhabitant_lid", new float[]{0.5F, 0.8125F, 0.98331F}, 0.9601F);
      regLid("toffy_owl", "§eCofre Búho", "toffy_owl", "toffy_owl_lid", new float[]{0.5F, 0.53791F, 1.3125F}, 0.7935F);
      regLid("toffy_piano", "§bCofre Piano", "toffy_piano", "toffy_piano_lid", new float[]{0.5F, 0.53612F, 1.3125F}, 1.1403F);
      regLid("aquatic_4", "§3Cofre Acuático I", "aquatic_4", "aquatic_4_lid", new float[]{0.5F, 0.48629F, 1.07094F}, 0.8553F);
      regLid("aquatic_5", "§3Cofre Acuático II", "aquatic_5", "aquatic_5_lid", new float[]{0.5F, 0.53366F, 0.94789F}, 0.7697F);
      regLid("aquatic_6", "§3Cofre Acuático III", "aquatic_6", "aquatic_6_lid", new float[]{0.5F, 0.79824F, 1.04167F}, 0.7565F);
      regLid("pirate_1", "§6Cofre Pirata I", "pirate_1", "pirate_1_lid", new float[]{0.5F, 0.34574F, 0.99701F}, 1.3646F);
      regLid("pirate_2", "§6Cofre Pirata II", "pirate_2", "pirate_2_lid", new float[]{0.5F, 0.51704F, 1.20171F}, 0.7242F);
      regLid("pirate_3", "§6Cofre Pirata III", "pirate_3", "pirate_3_lid", new float[]{0.5F, 0.3447F, 1.21401F}, 1.0862F);
      regLid("pirate_4", "§6Cofre Pirata IV", "pirate_4", "pirate_4_lid", new float[]{0.5F, 0.53F, 1.09375F}, 0.9524F);
      regLid("crates1_lvl1", "§7Cofre Nivel I", "crates1_lvl1", "crates1_lvl1_lid", new float[]{0.5F, 0.5F, 1.0625F}, 1.15F);
      regLid("crates1_lvl2", "§eCofre Nivel II", "crates1_lvl2", "crates1_lvl2_lid", new float[]{0.5F, 0.5625F, 1.0625F}, 0.8659F);
      regLid("crates1_lvl3", "§bCofre Nivel III", "crates1_lvl3", "crates1_lvl3_lid", new float[]{0.5F, 0.6875F, 1.06228F}, 0.8762F);
      regLid("crates1_lvl4", "§dCofre Nivel IV", "crates1_lvl4", "crates1_lvl4_lid", new float[]{0.5F, 0.76875F, 0.9375F}, 0.7863F);
      regLid("toro_minotaur", "§4Cofre Jefe Minotauro", "toro_minotaur", "toro_minotaur_lid", new float[]{0.5F, 0.41391F, 1.00589F}, 1.0612F);
      regLid("toro_soulknight", "§8Cofre Jefe Caballero de Almas", "toro_soulknight", "toro_soulknight_lid", new float[]{0.5F, 0.58036F, 1.16741F}, 0.7206F);
      regLid("toro_xi", "§5Cofre Jefe Xi", "toro_xi", "toro_xi_lid", new float[]{0.5F, 0.58036F, 1.22835F}, 0.9436F);
      regLid("toro_slimy", "§2Cofre Jefe Slimy", "toro_slimy", "toro_slimy_lid", new float[]{0.5F, 0.4625F, 0.95312F}, 1.2778F);
      regCine("cine_common", "§fCofre Cinemático Común", "cine_common", "cine_common_lid", new float[]{0.5F, 0.67241F, 1.25646F}, 0.6662F);
      regCine("cine_rare", "§bCofre Cinemático Raro", "cine_rare", "cine_rare_lid", new float[]{0.5F, 0.66102F, 1.22988F}, 0.6777F);
      regCine("cine_epic", "§5Cofre Cinemático Épico", "cine_epic", "cine_epic_lid", new float[]{0.5F, 0.65F, 1.20594F}, 0.6961F);
      regCine("cine_legendary", "§6Cofre Cinemático Legendario", "cine_legendary", "cine_legendary_lid", new float[]{0.5F, 0.24127F, 1.21127F}, 0.6961F);
      regCine("cine_mythical", "§dCofre Cinemático Mítico", "cine_mythical", "cine_mythical_lid", new float[]{0.5F, 0.51332F, 1.28281F}, 0.6829F);
      regCine("cine_ultimate", "§cCofre Cinemático Definitivo", "cine_ultimate", "cine_ultimate_lid", new float[]{0.5F, 0.47963F, 1.10953F}, 0.8267F);
   }

   public static final class Style {
      public final String id;
      public final String display;
      public final ResourceLocation base;
      public final ResourceLocation lid;
      public final float[] hinge;
      public final float scale;
      public final boolean cinematic;

      public Style(String id, String display, ResourceLocation base, ResourceLocation lid, float[] hinge, float scale) {
         this(id, display, base, lid, hinge, scale, false);
      }

      public Style(String id, String display, ResourceLocation base, ResourceLocation lid, float[] hinge, float scale, boolean cinematic) {
         this.id = id;
         this.display = display;
         this.base = base;
         this.lid = lid;
         this.hinge = hinge;
         this.scale = scale;
         this.cinematic = cinematic;
      }

      public boolean hasLid() {
         return this.lid != null;
      }

      public boolean isCinematic() {
         return this.cinematic;
      }
   }
}
