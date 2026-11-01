/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** (1.3.159) El estandarte del clan: de qué clan es (lo demás —insignia, color, etiqueta— lo pone ClanLooks, al día). */
public class ClanBannerBlockEntity extends BlockEntity {
    private String clan = "";

    public ClanBannerBlockEntity(BlockPos pos, BlockState state) {
        super(ClanBlocks.BANNER_BE.get(), pos, state);
    }

    public String clan() {
        return clan;
    }

    public void setClan(String id) {
        clan = id == null ? "" : id;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        clan = tag.getString("clan");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("clan", clan);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** La tela y el asta suben casi tres bloques: que se dibuje aunque solo se vea lo de arriba. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 2.2, 0).inflate(0.8, 0, 0.8);
    }
}
