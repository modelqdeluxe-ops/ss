/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (1.3.156) El cofre del Banco del clan. No guarda nada dentro: el banco es digital (pad/server/PadClanBank, solo el id
 * del objeto y su cantidad), así que cualquier cofre abre el banco del clan de quien lo usa, en la app de clanes del pad
 * (pestaña BANCO). Lo da la pestaña BANCO al líder y a los colíderes.
 * <p>
 * (1.3.159, el dueño: «el cofre se ve muy pequeño, hazlo más grande, que solo te dé 1 el pad, y que lo puedas romper, ojo
 * solo el líder o colíder, y se lo devuelve al inventario tipo las claim blocks; y que brille en la oscuridad como las
 * crates») Del tamaño de una crate, uno por clan (PadClanBank), lo rompen el líder y los colíderes de su clan y les vuelve
 * al inventario (ClanBlockEvents); lo dibuja ClanBankRenderer a plena luz. Lo abren los de su clan.
 */
public class ClanBankBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    public ClanBankBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F, 3_600_000F).sound(SoundType.WOOD).lightLevel(s -> 7).noOcclusion()
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClanBankBlockEntity(pos, state);
    }

    /** Lo dibuja ClanBankRenderer (a plena luz), no el mundo. */
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        String mine = net.tierrasfantasticas.tfclient.pad.server.PadClans.clanIdOf(sp.getUUID());
        if (mine == null) {
            sp.displayClientMessage(Component.literal("Es el banco de un clan: únete a uno (pad › Clanes) para usarlo.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        // (1.3.159) cada cofre es de un clan: lo abren los suyos (uno de antes, sin dueño, se queda con el clan que lo usa)
        String key = net.tierrasfantasticas.tfclient.pad.server.PadClanBank.posKey(level, pos);
        String owner = net.tierrasfantasticas.tfclient.pad.server.PadClanBank.ownerAt("cofres", key);
        if (owner == null && net.tierrasfantasticas.tfclient.pad.server.PadClanBank.chestOf(mine) == null) {
            net.tierrasfantasticas.tfclient.pad.server.PadClanBank.placed("cofres", key, mine);
            owner = mine;
        }
        if (owner != null && !owner.equals(mine)) {
            sp.displayClientMessage(Component.literal("Es el banco de otro clan.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 0.9F);
        if (!net.tierrasfantasticas.tfclient.pad.TFPadNet.openApp(sp, "clanes", "banco")) {
            sp.displayClientMessage(Component.literal("El banco del clan se usa con el TF Pad (TF Client).").withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.CONSUME;
    }
}
