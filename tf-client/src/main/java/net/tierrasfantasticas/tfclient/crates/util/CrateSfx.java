/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;

/**
 * Los sonidos de las crates: la del mundo (CrateBlockEntity, la oyen todos los de alrededor) y la apertura clásica
 * (CrateCinematicClassic). (1.3.109) Fuera los de Fantastic Crates (el Warden, truenos, el Wither). (1.3.113, el dueño:
 * «el sonido de la llave, la ruleta, son horribles; sonidos por rarezas, al abrir la crate, la llave, todo eso no me
 * gusta») fuera también los del juego (encantamiento, faro, brújula, botón de madera…): ahora son los propios de
 * tools/crates/sfx_crates.py (mono, para que suenen en su sitio), uno por momento y, el premio, uno por rareza.
 */
public final class CrateSfx {
   private CrateSfx() {
   }

   public static SoundEvent tf(String name) {
      return SoundEvent.createVariableRangeEvent(new ResourceLocation("tfclient", "crates." + name));
   }

   /** 1 (común) … 5 (mítica). */
   private static int tier(Rarity r) {
      return r == null ? 1 : Math.min(5, r.ordinal() + 1);
   }

   /** Las notas de la espiral que sube: Re mayor pentatónica (sube por la escala, no deslizándose fuera de tono). */
   private static final int[] RISE = {0, 2, 4, 7, 9, 12, 14, 16, 19};

   /** La llave entra y gira. */
   public static void unlock(CrateSfx.Sink s, Rarity r) {
      s.play(tf("mundo_llave"), 0.9F, 1.0F);
   }

   /** Empieza a cargarse (la espiral de luz). */
   public static void spiralCharge(CrateSfx.Sink s, Rarity r) {
      s.play(tf("mundo_carga"), 0.8F, 1.0F);
   }

   /** La espiral sube (p de 0 a 1): una nota de celesta cada vez más aguda. */
   public static void spiralRise(CrateSfx.Sink s, Rarity r, float p) {
      int step = RISE[Math.min(RISE.length - 1, (int)(p * RISE.length))];
      s.play(tf("mundo_sube"), 0.35F + p * 0.3F, (float)Math.pow(2.0, (step - 7) / 12.0));
   }

   /** Lo más alto de la espiral, justo antes de abrirse. */
   public static void spiralPeak(CrateSfx.Sink s, Rarity r) {
      s.play(tf("mundo_pico"), 0.8F, 1.0F);
   }

   /** ¡Se abre! */
   public static void openAccent(CrateSfx.Sink s, Rarity r) {
      s.play(tf("mundo_abre"), 0.9F, 1.0F);
   }

   /** Mientras gira la ruleta: nada (los tics de cada premio ya la acompañan). */
   public static void openSustain(CrateSfx.Sink s, Rarity r, float p) {
   }

   /** Un premio que pasa por la ruleta (pitch: sube al frenar). */
   public static void tick(CrateSfx.Sink s, float volume, float pitch) {
      s.play(tf("mundo_tic"), volume, pitch);
   }

   /** El premio: uno por rareza. */
   public static void win(CrateSfx.Sink s, Rarity r) {
      s.play(tf("mundo_premio" + tier(r)), 1.0F, 1.0F);
   }

   /** Lo que suena un poco después del premio: nada (el premio ya trae su cola). */
   public static void winTail(CrateSfx.Sink s, Rarity r) {
   }

   /** Se cierra. */
   public static void close(CrateSfx.Sink s, Rarity r) {
      s.play(tf("mundo_cierra"), 0.7F, 1.0F);
   }

   public interface Sink {
      void play(SoundEvent var1, float var2, float var3);
   }
}
