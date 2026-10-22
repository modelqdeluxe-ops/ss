package net.tierrasfantasticas.tfclient.crates.util;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;

/**
 * Los sonidos de las crates: la del mundo (CrateBlockEntity, la oyen todos los de alrededor) y la apertura clásica
 * (CrateCinematicClassic). (1.3.109, el dueño: «ese sonido horrendo de las crates, odio ese sonido») antes eran los de
 * Fantastic Crates: el estampido y la carga del Warden, truenos, el Wither al nacer y sus gruñidos y los lamentos del
 * valle de las almas, cinco o diez a la vez. Ahora una paleta mágica y limpia: encantamiento, brújula imantada, faro,
 * conducto, cofre, subir de nivel, desafío completado y fuegos artificiales; cuanto más rara la crate, más capas (no
 * más ruido).
 */
public final class CrateSfx {
   private CrateSfx() {
   }

   /** Cuántas capas lleva cada rareza (común 0 … mítica 4). */
   private static int tier(Rarity r) {
      return r == null ? 0 : Math.min(4, r.ordinal());
   }

   /** La llave entra y gira. */
   public static void unlock(CrateSfx.Sink s, Rarity r) {
      int t = tier(r);
      s.play(SoundEvents.LODESTONE_COMPASS_LOCK, 0.9F, 1.0F);
      s.play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.7F, 1.15F - t * 0.04F);
      if (t >= 2) s.play(SoundEvents.BEACON_POWER_SELECT, 0.45F, 1.3F);
   }

   /** Empieza a cargarse (la espiral de luz). */
   public static void spiralCharge(CrateSfx.Sink s, Rarity r) {
      int t = tier(r);
      s.play(SoundEvents.BEACON_AMBIENT, 0.5F, 1.3F - t * 0.05F);
      if (t >= 2) s.play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.5F, 0.9F);
   }

   /** La espiral sube (p de 0 a 1): un brillo que va subiendo de tono. */
   public static void spiralRise(CrateSfx.Sink s, Rarity r, float p) {
      float vol = 0.25F + p * 0.25F;
      s.play(SoundEvents.BEACON_POWER_SELECT, vol, Math.min(1.8F, 0.8F + p * 0.9F));
      if (tier(r) >= 3 && p > 0.5F) s.play(SoundEvents.ENCHANTMENT_TABLE_USE, vol * 0.8F, 1.0F + p * 0.5F);
   }

   /** Lo más alto de la espiral, justo antes de abrirse. */
   public static void spiralPeak(CrateSfx.Sink s, Rarity r) {
      int t = tier(r);
      s.play(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.35F - t * 0.05F);
      s.play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.7F, 1.4F);
      if (t >= 3) s.play(SoundEvents.CONDUIT_ACTIVATE, 0.5F, 1.4F);
   }

   /** ¡Se abre! */
   public static void openAccent(CrateSfx.Sink s, Rarity r) {
      int t = tier(r);
      s.play(SoundEvents.CHEST_OPEN, 0.8F, 1.05F);
      s.play(SoundEvents.BEACON_ACTIVATE, 0.9F, 1.2F);
      if (t >= 1) s.play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.0F);
      if (t >= 2) s.play(SoundEvents.TOTEM_USE, 0.45F, 1.25F);
      if (t >= 3) s.play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.7F, 1.0F);
      if (t >= 4) s.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.6F, 1.0F);
   }

   /** Mientras gira la ruleta (p de 0 a 1): un zumbido suave. */
   public static void openSustain(CrateSfx.Sink s, Rarity r, float p) {
      s.play(SoundEvents.BEACON_AMBIENT, 0.25F + p * 0.2F, 1.2F + p * 0.3F);
   }

   /** El premio. */
   public static void win(CrateSfx.Sink s, Rarity r) {
      int t = tier(r);
      s.play(SoundEvents.PLAYER_LEVELUP, 0.9F, 1.0F);
      if (t >= 1) s.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.75F, 1.0F);
      if (t >= 2) s.play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.7F, 1.05F);
      if (t >= 3) s.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.6F, 1.0F);
      if (t >= 4) s.play(SoundEvents.TOTEM_USE, 0.5F, 1.15F);
   }

   /** Lo que suena un poco después del premio. */
   public static void winTail(CrateSfx.Sink s, Rarity r) {
      int t = tier(r);
      s.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.2F);
      if (t >= 3) s.play(SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 0.6F, 1.0F);
   }

   /** Se cierra. */
   public static void close(CrateSfx.Sink s, Rarity r) {
      s.play(SoundEvents.CHEST_CLOSE, 0.6F, 1.05F);
      s.play(SoundEvents.BEACON_DEACTIVATE, 0.4F, 1.2F);
   }

   public interface Sink {
      void play(SoundEvent var1, float var2, float var3);
   }
}
