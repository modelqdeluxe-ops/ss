package net.tierrasfantasticas.tfclient.crates.block;

import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.crate.OpenAuthorization;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import net.tierrasfantasticas.tfclient.crates.network.FSNetwork;
import net.tierrasfantasticas.tfclient.crates.network.OpenEditorPacket;
import net.tierrasfantasticas.tfclient.crates.network.OpenPreviewPacket;
import net.tierrasfantasticas.tfclient.crates.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CrateBlock extends BaseEntityBlock {
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

   public CrateBlock() {
      super(Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).noOcclusion());
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return (BlockState)this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new CrateBlockEntity(pos, state);
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return SHAPE;
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      if (type != ModRegistry.CRATE_BE.get()) {
         return null;
      } else {
         return level.isClientSide
            ? (lvl, pos, st, be) -> CrateBlockEntity.clientTick(lvl, pos, st, (CrateBlockEntity)be)
            : (lvl, pos, st, be) -> CrateBlockEntity.serverTick(lvl, pos, st, (CrateBlockEntity)be);
      }
   }

   public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
      super.setPlacedBy(level, pos, state, placer, stack);
      BlockEntity blockEntity;
      CrateConfig cfg;
      if (!level.isClientSide && (cfg = CrateItems.readConfig(stack)) != null && (blockEntity = level.getBlockEntity(pos)) instanceof CrateBlockEntity) {
         CrateBlockEntity be = (CrateBlockEntity)blockEntity;
         be.setConfig(cfg);
      }
   }

   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      } else if (level.isClientSide) {
         return InteractionResult.SUCCESS;
      } else if (level.getBlockEntity(pos) instanceof CrateBlockEntity be) {
         if (player instanceof ServerPlayer serverPlayer) {
            CrateConfig var11 = be.getConfig();
            ItemStack mainHand = player.getMainHandItem();
            if (CrateItems.isEditorWand(mainHand)) {
               if (!serverPlayer.hasPermissions(4)) {
                  serverPlayer.sendSystemMessage(Component.literal("§cSolo administradores pueden usar la Varita del Editor."));
                  return InteractionResult.CONSUME;
               } else {
                  FSNetwork.sendToClient(serverPlayer, new OpenEditorPacket(var11.save(), pos));
                  serverPlayer.sendSystemMessage(Component.literal("§dEditor abierto para el cofre §f" + var11.id + "§d. Guarda para aplicar los cambios aquí."));
                  return InteractionResult.CONSUME;
               }
            } else {
               OpenAuthorization.grant(serverPlayer, pos);
               FSNetwork.sendToClient(serverPlayer, new OpenPreviewPacket(var11.save(), pos));
               return InteractionResult.CONSUME;
            }
         } else {
            return InteractionResult.PASS;
         }
      } else {
         return InteractionResult.PASS;
      }
   }
}
