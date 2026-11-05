package com.lukeolafp.relativeblocks.blocks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class VerticalSlabBlock extends Block implements SimpleWaterloggedBlock {
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
   private static final VoxelShape NORTH = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
   private static final VoxelShape SOUTH = Block.box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
   private static final VoxelShape WEST = Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);
   private static final VoxelShape EAST = Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);
   private final SlabBlock source;

   public VerticalSlabBlock(SlabBlock source) {
      super(Properties.copy(source));
      this.source = source;
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)this.getStateDefinition().any()).setValue(FACING, Direction.NORTH)).setValue(WATERLOGGED, false)
      );
   }

   public SlabBlock getSource() {
      return this.source;
   }

   public BlockState doubled() {
      BlockState state = (BlockState)this.source.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
      return state.hasProperty(BlockStateProperties.WATERLOGGED) ? (BlockState)state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
   }

   public MutableComponent getName() {
      String slab = this.source.getName().getString();
      String material = slab.endsWith(" Slab") ? slab.substring(0, slab.length() - " Slab".length()) : slab;
      return Component.translatable("block.relativeblocks.orthostat", new Object[]{material});
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING, WATERLOGGED});
   }

   public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      return switch ((Direction)state.getValue(FACING)) {
         case SOUTH -> SOUTH;
         case WEST -> WEST;
         case EAST -> EAST;
         default -> NORTH;
      };
   }

   public boolean useShapeForLightOcclusion(BlockState state) {
      return true;
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      BlockPos pos = ctx.getClickedPos();
      if (ctx.getLevel().getBlockState(pos).is(this)) {
         return this.doubled();
      } else {
         FluidState fluid = ctx.getLevel().getFluidState(pos);
         return (BlockState)((BlockState)this.defaultBlockState()
               .setValue(FACING, facingFor(ctx.getClickedFace(), ctx.getClickLocation(), ctx.getClickedPos(), ctx.getHorizontalDirection())))
            .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
      }
   }

   static Direction facingFor(Direction side, Vec3 hit, BlockPos pos, Direction look) {
      if (side.getAxis().isHorizontal()) {
         return side.getOpposite();
      } else {
         double off = offsetAlong(hit, pos, look.getAxis());
         return Math.abs(off) < 1.0E-4 ? look : Direction.fromAxisAndDirection(look.getAxis(), off > 0.0 ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
      }
   }

   private static double offsetAlong(Vec3 hit, BlockPos pos, Axis axis) {
      return axis == Axis.X ? hit.x - (double)pos.getX() - 0.5 : hit.z - (double)pos.getZ() - 0.5;
   }

   public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
      ItemStack stack = context.getItemInHand();
      if (!stack.is(this.asItem())) {
         return false;
      } else {
         return !context.replacingClickedOnBlock()
            ? true
            : reachesOpenHalf(state, context.getClickedFace(), context.getClickLocation(), context.getClickedPos());
      }
   }

   static boolean reachesOpenHalf(BlockState state, Direction side, Vec3 hit, BlockPos pos) {
      Direction open = ((Direction)state.getValue(FACING)).getOpposite();
      if (side == open) {
         return true;
      } else if (side.getAxis() == open.getAxis()) {
         return false;
      } else {
         double off = offsetAlong(hit, pos, open.getAxis());
         return open.getAxisDirection() == AxisDirection.POSITIVE ? off > 0.0 : off < 0.0;
      }
   }

   public FluidState getFluidState(BlockState state) {
      return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
   }

   public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
      if ((Boolean)state.getValue(WATERLOGGED)) {
         world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
      }

      return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
   }

   public BlockState rotate(BlockState state, Rotation rotation) {
      return (BlockState)state.setValue(FACING, rotation.rotate((Direction)state.getValue(FACING)));
   }

   public BlockState mirror(BlockState state, Mirror mirror) {
      return state.rotate(mirror.getRotation((Direction)state.getValue(FACING)));
   }

   public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
      return this.doubled().getDestroyProgress(player, world, pos);
   }

   public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
      MinecraftServer server = builder.getLevel().getServer();
      if (server.getLootData().getLootTable(this.getLootTable()) != LootTable.EMPTY) {
         return super.getDrops(state, builder);
      } else {
         ItemStack tool = (ItemStack)builder.getOptionalParameter(LootContextParams.TOOL);
         boolean harvested = !this.doubled().requiresCorrectToolForDrops() || tool != null && tool.isCorrectToolForDrops(this.doubled());
         return harvested ? List.of(new ItemStack(this)) : List.of();
      }
   }
}
