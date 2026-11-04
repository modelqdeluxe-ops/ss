/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.items;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.tierrasfantasticas.tfclient.items.TFMace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (1.3.171) Brecha, el encantamiento de la maza (TFMace): la armadura del que recibe el golpe protege un 15 % menos por
 * nivel. Es la cuenta de Minecraft (CombatRules.getDamageAfterAbsorb) con lo que protege la armadura multiplicado por
 * (1 − brecha), como hace la 1.21 (EnchantmentHelper.modifyArmorEffectiveness). Solo para el golpe que la lleva: TFMace
 * lo apunta en el LivingHurtEvent, justo antes de esto, en el mismo hilo.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBreachMixin {
    @Shadow
    protected abstract void hurtArmor(DamageSource source, float amount);

    @Shadow
    public abstract int getArmorValue();

    @Shadow
    public abstract double getAttributeValue(Attribute attribute);

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$breach(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        try {
            float breach = TFMace.takeBreach((LivingEntity) (Object) this);
            if (breach <= 0.0F || source.is(DamageTypeTags.BYPASSES_ARMOR)) return;
            this.hurtArmor(source, amount);
            float armor = this.getArmorValue();
            float toughness = (float) this.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float f = 2.0F + toughness / 4.0F;
            float protect = Mth.clamp(armor - amount / f, armor * 0.2F, 20.0F) / 25.0F;
            cir.setReturnValue(amount * (1.0F - protect * (1.0F - breach)));
        } catch (Throwable ignored) {
            // nunca romper el daño por esto: sin Brecha, como siempre
        }
    }
}
