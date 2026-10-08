package net.tierrasfantasticas.tfclient.claims.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;

/**
 * La piedra de protección puesta: el centro de la zona. No suelta nada al romperse (la devuelve
 * {@code BlockProtectionEvents} al dueño), no la mueven los pistones y aguanta cualquier explosión. La cara de las
 * letras TF mira a quien la pone.
 */
public class ProtectionBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final ClaimTier tier;

    public ProtectionBlock(ClaimTier claimtier) {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0f, 3600000.0f).sound(SoundType.AMETHYST)
                .noLootTable().pushReaction(PushReaction.BLOCK).lightLevel(state -> 6));
        this.tier = claimtier;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** La piedra puesta por alguien que mira hacia «looking»: las letras quedan de cara a él. */
    public BlockState facing(Direction looking) {
        Direction d = looking == null || looking.getAxis().isVertical() ? Direction.NORTH : looking.getOpposite();
        return this.defaultBlockState().setValue(FACING, d);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Su objeto es la piedra de protección de su tamaño (no hay BlockItem). */
    @Override
    public Item asItem() {
        Item item = ClaimItems.itemFor(this.tier);
        return item != null ? item : Items.AIR;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(this.asItem());
    }
}
