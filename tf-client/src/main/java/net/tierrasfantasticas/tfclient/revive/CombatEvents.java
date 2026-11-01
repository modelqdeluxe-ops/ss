/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * El daño a los noqueados y la muerte que se convierte en noqueo (como el CombatEvents del revivemod), con el remate de
 * la 1.3.157:
 * <ul>
 *   <li>Un noqueado no recibe daño, salvo el del vacío y /kill (como antes) y, si la config lo deja, el de quien lo
 *   remata: otro jugador ({@link ReviveConfig#playersCanFinish}, por defecto sí) o un mob ({@link ReviveConfig#mobsCanFinish},
 *   por defecto no). Las máquinas de otros mods (jugadores falsos) no rematan.</li>
 *   <li>El remate respeta las protecciones: si una zona no deja PvP, el golpe ya se cancela antes (TF Claims) y no
 *   llega. Si llega y hace daño (una bola de nieve, no), vale lo que haga falta para matarlo de un golpe
 *   ({@link #onHurt}, lo último de todo).</li>
 *   <li>Y entonces se deja morir de verdad, por ese golpe ({@link #onDeath}): tumba, mensaje de muerte y el asesinato
 *   para el que lo remata (efecto de kill, guerras de clanes…).</li>
 * </ul>
 * (1.3.157) onDeath va con prioridad alta: el noqueo cancela la muerte antes que lo demás del TF Client la cuente como
 * muerte (los duelos, con la más alta, siguen antes: en un duelo no se noquea).
 */
public final class CombatEvents {
    /** Lo que vale el golpe que remata (de sobra para cualquier vida, armadura y absorción). */
    private static final float FINISH_DAMAGE = 1.0E6F;

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DamageSource source = event.getSource();
            if (DownManager.isReviving(player.getUUID()) && !isLethalAllowed(source)) {
                event.setCanceled(true);
            } else if (DownManager.isDown(player)) {
                if (!isLethalAllowed(source) && !canFinish(player, source)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    /** (1.3.157) El golpe que remata mata de una vez (si ha llegado hasta aquí sin cancelarse y hace daño). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DownManager.isDown(player) && event.getAmount() > 0F
                && !isLethalAllowed(event.getSource()) && canFinish(player, event.getSource())) {
            event.setAmount(Math.max(event.getAmount(), FINISH_DAMAGE));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DamageSource source = event.getSource();
            if (!DownManager.isForceKilling(player.getUUID())) {
                if (DownManager.isDown(player)) {
                    if (isLethalAllowed(source)) {
                        return;
                    }
                    if (canFinish(player, source)) {
                        DownManager.finished(player, source);
                        return;
                    }
                    event.setCanceled(true);
                } else if (!player.isCreative() && !player.isSpectator()) {
                    if (!isLethalAllowed(source)) {
                        DownManager.knockDown(player, source);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        // (1.3.157) si los mobs pueden rematar, pueden seguir al que cae
        if (target instanceof ServerPlayer sp && DownManager.isDown(sp.getUUID()) && !TFRevive.getConfig().mobsCanFinish) {
            event.setCanceled(true);
        }
    }

    private static boolean isLethalAllowed(DamageSource source) {
        return source == null ? false : source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL);
    }

    /**
     * (1.3.157) Si este daño puede rematar al noqueado: el de otro jugador de verdad (con la flecha, el tridente… cuenta el
     * que dispara) si {@link ReviveConfig#playersCanFinish}; el de un mob (o cualquier otra criatura) si
     * {@link ReviveConfig#mobsCanFinish}. Nunca el suyo propio ni el de un jugador falso (máquinas de otros mods).
     */
    public static boolean canFinish(ServerPlayer downed, DamageSource source) {
        if (source == null) return false;
        Entity by = source.getEntity();
        if (by == null || by == downed) return false;
        ReviveConfig cfg = TFRevive.getConfig();
        if (by instanceof Player p) {
            return cfg.playersCanFinish && !(p instanceof FakePlayer) && !p.isSpectator();
        }
        return by instanceof LivingEntity && cfg.mobsCanFinish;
    }
}
