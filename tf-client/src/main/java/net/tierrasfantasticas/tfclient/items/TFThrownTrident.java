/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.tierrasfantasticas.tfclient.mixin.items.ThrownTridentAccessor;

/**
 * (1.3.171, el dueño: «las lanzas de los sets, al tirarlas, se les pone la textura del tridente») El tridente o lanza de
 * un set, lanzado. Es el tridente de Minecraft (daño, Empalamiento, Lealtad, Canalización, recogerlo, que vuelva…) pero
 * guarda y manda al cliente QUÉ objeto es, para que TFThrownTridentRenderer lo dibuje con su modelo.
 */
public class TFThrownTrident extends ThrownTrident {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(TFThrownTrident.class, EntityDataSerializers.ITEM_STACK);

    public TFThrownTrident(EntityType<? extends TFThrownTrident> type, Level level) {
        super(type, level);
    }

    /** Lanzada por owner con ese objeto (como el ThrownTrident(level, owner, stack) de Minecraft). */
    public TFThrownTrident(Level level, LivingEntity owner, ItemStack stack) {
        super(TFItems.THROWN_TRIDENT.get(), level);
        setPos(owner.getX(), owner.getEyeY() - 0.1F, owner.getZ());
        setOwner(owner);
        if (owner instanceof Player) this.pickup = Pickup.ALLOWED;
        setItem(stack);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ITEM, ItemStack.EMPTY);
    }

    private void setItem(ItemStack stack) {
        ItemStack copy = stack.copy();
        ((ThrownTridentAccessor) this).tfclient$setTridentItem(copy);
        this.entityData.set(ThrownTridentAccessor.tfclient$loyalty(), (byte) EnchantmentHelper.getLoyalty(copy));
        this.entityData.set(ThrownTridentAccessor.tfclient$foil(), copy.hasFoil());
        this.entityData.set(ITEM, copy);
    }

    /** El objeto que se dibuja (en el cliente, el que mandó el servidor). */
    public ItemStack shownItem() {
        return this.entityData.get(ITEM);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Trident", 10)) setItem(ItemStack.of(tag.getCompound("Trident")));
    }
}
