package com.lukeolafp.relativeblocks.blocks;

import com.lukeolafp.relativeblocks.init.BlockInit;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BlockClusterBlock extends BaseEntityBlock {
   public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light_level", 0, 15);
   private static final int SWAP_IN_FLAGS = 52;
   private static final int SWAP_OUT_FLAGS = 50;
   private static final int COLUMN_REACH = 16;
   private static BlockClusterBlock.CrosshairCell crosshair = pos -> -1;

   public BlockClusterBlock(Properties settings) {
      super(settings);
      this.registerDefaultState((BlockState)this.defaultBlockState().setValue(LIGHT_LEVEL, 0));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{LIGHT_LEVEL});
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new BlockClusterBlockEntity(pos, state);
   }

   public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         for (int var11 = 0; var11 < 4; var11++) {
            BlockState member = cluster.getCell(var11);
            if (member != null) {
               ClusterParticleOffset.begin(cluster.cellOffsetX(var11), cluster.cellOffsetZ(var11));

               try {
                  member.getBlock().animateTick(member, world, pos, random);
               } finally {
                  ClusterParticleOffset.end();
               }
            }
         }
      }
   }

   public boolean isSignalSource(BlockState state) {
      return true;
   }

   public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
      return this.strongestMemberPower(world, pos, direction, false);
   }

   public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
      return this.strongestMemberPower(world, pos, direction, true);
   }

   private int strongestMemberPower(BlockGetter world, BlockPos pos, Direction direction, boolean strong) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         int var9 = 0;

         for (int cell = 0; cell < 4; cell++) {
            BlockState member = cluster.getCell(cell);
            if (member != null) {
               var9 = Math.max(var9, strong ? member.getDirectSignal(world, pos, direction) : member.getSignal(world, pos, direction));
            }
         }

         return var9;
      } else {
         return 0;
      }
   }

   public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster && cluster.getLayout() == 5 && cluster.memberCount() == 2) {
         return world.getMaxLightLevel();
      }

      return 0;
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         BlockState member = RelativePlacement.memberUnderCrosshair(player, pos, cluster);
         if (member != null) {
            return member.getDestroyProgress(player, world, pos);
         }
      }

      return super.getDestroyProgress(state, player, world, pos);
   }

   public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      VoxelShape shape = this.combinedShape(world, pos, false);
      return shape.isEmpty() ? Shapes.block() : shape;
   }

   public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      return this.combinedShape(world, pos, true);
   }

   private VoxelShape combinedShape(BlockGetter world, BlockPos pos, boolean collision) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         VoxelShape var8 = Shapes.empty();

         for (int cell = 0; cell < 4; cell++) {
            VoxelShape shape = cellShape(world, pos, cluster, cell, collision);
            if (!shape.isEmpty()) {
               var8 = Shapes.or(var8, shape);
            }
         }

         return var8;
      } else {
         return Shapes.empty();
      }
   }

   public static VoxelShape cellShape(BlockGetter world, BlockPos pos, BlockClusterBlockEntity cluster, int cell, boolean collision) {
      BlockState member = cluster.getCell(cell);
      if (member == null) {
         return Shapes.empty();
      } else {
         VoxelShape cached = cluster.cachedShape(cell, collision);
         if (cached != null) {
            return cached;
         } else {
            BlockClusterBlockEntity.RenderData data = cluster.getRenderData();
            VoxelShape shape;
            if (BarrierLayout.lays(data, member)) {
               shape = BarrierLayout.shape(BarrierLayout.of(world), pos, data, cell, collision);
            } else {
               shape = memberShape(world, pos, member, collision).move(cluster.cellOffsetX(cell), cluster.cellOffsetY(cell), cluster.cellOffsetZ(cell));
            }

            cluster.cacheShape(cell, collision, shape);
            return shape;
         }
      }
   }

   public static VoxelShape memberShape(BlockGetter world, BlockPos pos, BlockState member, boolean collision) {
      VoxelShape shape = collision ? member.getCollisionShape(world, pos) : member.getShape(world, pos);
      if (!shape.isEmpty() && member.hasOffsetFunction()) {
         Vec3 nudge = member.getOffset(world, pos);
         return shape.move(-nudge.x, -nudge.y, -nudge.z);
      } else {
         return shape;
      }
   }

   public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         boolean var8 = false;

         for (int cell = 0; cell < 4; cell++) {
            BlockState member = cluster.getCell(cell);
            if (member != null) {
               var8 = true;
               if (member.canSurvive(world, pos)) {
                  return true;
               }
            }
         }

         return !var8;
      } else {
         return true;
      }
   }

   public VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         VoxelShape var8 = Shapes.empty();

         for (int cell = 0; cell < 4; cell++) {
            BlockState member = cluster.getCell(cell);
            if (member != null) {
               var8 = Shapes.or(var8, member.getBlockSupportShape(world, pos));
            }
         }

         return var8;
      } else {
         return Shapes.empty();
      }
   }

   public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
      if (!state.canSurvive(world, pos)) {
         return Blocks.AIR.defaultBlockState();
      } else {
         world.scheduleTick(pos, this, 1);
         return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
      }
   }

   public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
      if (!state.is(newState.getBlock()) && world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         for (int cell = 0; cell < 4; cell++) {
            BlockState member = cluster.getCell(cell);
            if (member != null) {
               Block.dropResources(member, world, pos);
            }
         }
      }

      super.onRemove(state, world, pos, newState, moved);
   }

   public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (world.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         ItemStack stack = player.getItemInHand(hand);
         InteractionResult result = this.interactWithMember(
            world,
            pos,
            hit,
            member -> {
               InteractionResult memberResult = member.use(world, player, hand, hit);
               if (!memberResult.consumesAction() && !stack.isEmpty() && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                  boolean var10000;
                  label29: {
                     if (stack.getItem() instanceof BlockItem blockItem
                        && member.is(blockItem.getBlock())
                        && member.canBeReplaced(new BlockPlaceContext(player, hand, stack, hit))) {
                        var10000 = true;
                        break label29;
                     }

                     var10000 = false;
                  }

                  boolean selfStack = var10000;
                  if (!selfStack && stack.getItem() instanceof BlockItem) {
                     return memberResult;
                  } else {
                     InteractionResult used = stack.useOn(new UseOnContext(player, hand, hit));
                     return used.consumesAction() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
                  }
               } else {
                  return memberResult;
               }
            }
         );
         return result != null ? result : InteractionResult.PASS;
      }
   }

   @Nullable
   private <T> T interactWithMember(Level world, BlockPos pos, BlockHitResult hit, Function<BlockState, T> interaction) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         int cell = RelativePlacement.nearestCell(cluster, pos, hit.getLocation());
         return cell < 0 ? null : this.runAsMember(world, pos, cell, interaction);
      } else {
         return null;
      }
   }

   @Nullable
   private <T> T runAsMember(Level world, BlockPos pos, int cell, Function<BlockState, T> interaction) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster && cluster.getCell(cell) != null) {
         BlockState member = cluster.getCell(cell);
         BlockState[] cells = new BlockState[4];

         for (int i = 0; i < 4; i++) {
            cells[i] = cluster.getCell(i);
         }

         int layout = cluster.getLayout();
         cluster.clearCells();
         world.setBlock(pos, member, 52);

         T result;
         BlockState after;
         try {
            result = interaction.apply(world.getBlockState(pos));
         } finally {
            // lo que quedó en el hueco tras usar el bloque (p. ej. una palanca encendida); se vuelve a poner el grupo
            after = world.getBlockState(pos);
            world.setBlock(pos, this.defaultBlockState(), 50);
         }

         if (!(world.getBlockEntity(pos) instanceof BlockClusterBlockEntity restored)) {
            return result;
         }

         restored.setLayout(layout);

         for (int var18 = 0; var18 < 4; var18++) {
            restored.setCell(var18, cells[var18]);
         }

         restored.setCell(cell, !after.isAir() && !after.is(this) ? after : null);
         if (restored.memberCount() == 0) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         } else {
            RelativePlacement.sync(world, pos, restored);
         }

         return result;
      }

      return null;
   }

   public static void followPendingTicks(Level world, BlockPos pos, BlockClusterBlockEntity cluster) {
      if (world instanceof ServerLevel serverWorld && hasQueuedMemberTick(serverWorld, pos, cluster)) {
         serverWorld.scheduleTick(pos, BlockInit.BLOCK_CLUSTER, 1);
      }
   }

   public static void settleNow(Level world, BlockPos pos) {
      if (world instanceof ServerLevel serverWorld && BlockInit.BLOCK_CLUSTER instanceof BlockClusterBlock cluster) {
         for (BlockPos at : new BlockPos[]{pos, pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (serverWorld.getBlockState(at).is(cluster)) {
               cluster.refreshMemberConnections(serverWorld, at);
            }
         }

         return;
      }
   }

   public static void refreshConnectionsAround(Level world, BlockPos pos) {
      if (world instanceof ServerLevel serverWorld) {
         scheduleRefresh(serverWorld, pos);

         for (Direction direction : Direction.values()) {
            scheduleRefresh(serverWorld, pos.relative(direction));
         }
      }
   }

   private static void scheduleRefresh(ServerLevel world, BlockPos pos) {
      if (world.getBlockState(pos).is(BlockInit.BLOCK_CLUSTER) && !world.getBlockTicks().hasScheduledTick(pos, BlockInit.BLOCK_CLUSTER)) {
         world.scheduleTick(pos, BlockInit.BLOCK_CLUSTER, 1);
      }
   }

   private static boolean hasQueuedMemberTick(ServerLevel world, BlockPos pos, BlockClusterBlockEntity cluster) {
      for (int cell = 0; cell < 4; cell++) {
         BlockState member = cluster.getCell(cell);
         if (member != null && world.getBlockTicks().hasScheduledTick(pos, member.getBlock())) {
            return true;
         }
      }

      return false;
   }

   public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
      this.cullUnsupportedMembers(world, pos);
      this.refreshMemberConnections(world, pos);
      if (!RelativePlacement.settleLastMember(world, pos, true)) {
         if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
            if (hasQueuedMemberTick(world, pos, cluster)) {
               world.scheduleTick(pos, this, 1);
            } else {
               for (int cell = 0; cell < 4; cell++) {
                  if (!(world.getBlockEntity(pos) instanceof BlockClusterBlockEntity current)) {
                     return;
                  }

                  BlockState member = current.getCell(cell);
                  if (member != null && member.hasProperty(BlockStateProperties.POWERED) && (Boolean)member.getValue(BlockStateProperties.POWERED)) {
                     this.runAsMember(world, pos, cell, swapped -> {
                        swapped.tick(world, pos, random);
                        return null;
                     });
                  }
               }
            }
         }
      }
   }

   public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
      for (int cell = 0; cell < 4; cell++) {
         if (!(world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster)) {
            return;
         }

         BlockState member = cluster.getCell(cell);
         if (member != null && member.isRandomlyTicking()) {
            withColumnAsCell(world, pos, cell, cluster.getLayout(), () -> {
               BlockState swapped = world.getBlockState(pos);
               if (!swapped.isAir()) {
                  swapped.randomTick(world, pos, random);
               }
            });
         }
      }
   }

   /** Hace como si la columna de grupos fuera solo el bloque de esa celda mientras corre action (crecer, sobrevivir…). */
   public static void withColumnAsCell(Level world, BlockPos pos, int cell, int layout, Runnable action) {
      Block self = BlockInit.BLOCK_CLUSTER;
      BlockPos bottom = pos;

      while (pos.getY() - bottom.getY() < 16 && world.getBlockState(bottom.below()).is(self)) {
         bottom = bottom.below();
      }

      BlockPos top = pos;

      while (top.getY() - pos.getY() < 16 && world.getBlockState(top.above()).is(self)) {
         top = top.above();
      }

      int height = top.getY() - bottom.getY() + 1;
      BlockPos frontier = top.above();
      boolean frontierWasClear = world.getBlockState(frontier).isAir();
      BlockState[][] stash = new BlockState[height][];
      int[] layouts = new int[height];
      BlockState[] originals = new BlockState[height];

      for (int i = 0; i < height; i++) {
         BlockPos here = bottom.above(i);
         originals[i] = world.getBlockState(here);
         if (!(world.getBlockEntity(here) instanceof BlockClusterBlockEntity cluster)) {
            layouts[i] = layout;
         } else {
            stash[i] = new BlockState[4];

            for (int j = 0; j < 4; j++) {
               stash[i][j] = cluster.getCell(j);
            }

            layouts[i] = cluster.getLayout();
            cluster.clearCells();
         }

         BlockState shown = stash[i] != null && stash[i][cell] != null ? stash[i][cell] : Blocks.AIR.defaultBlockState();
         world.setBlock(here, shown, 52);
      }

      try {
         action.run();
      } finally {
         boolean[] changed = new boolean[height];

         for (int ix = 0; ix < height; ix++) {
            BlockPos here = bottom.above(ix);
            BlockState became = world.getBlockState(here);
            BlockState now = !became.isAir() && !became.is(self) ? became : null;
            BlockState before = stash[ix] == null ? null : stash[ix][cell];
            if (stash[ix] == null && now == null) {
               world.setBlock(here, originals[ix], SWAP_OUT_FLAGS);
            } else {
               world.setBlock(here, stash[ix] == null ? BlockInit.BLOCK_CLUSTER.defaultBlockState() : originals[ix], SWAP_OUT_FLAGS);
               if (world.getBlockEntity(here) instanceof BlockClusterBlockEntity restored) {
                  restored.setLayout(layouts[ix]);

                  for (int jx = 0; jx < 4; jx++) {
                     restored.setCell(jx, stash[ix] == null ? null : stash[ix][jx]);
                  }

                  restored.setCell(cell, now);
                  changed[ix] = now == null ? before != null : !now.equals(before);
               }
            }
         }

         for (int ix = 0; ix < height; ix++) {
            BlockPos here = bottom.above(ix);
            if (changed[ix] && world.getBlockEntity(here) instanceof BlockClusterBlockEntity restored) {
               RelativePlacement.sync(world, here, restored);
            }
         }

         gatherGrowthBeyond(world, frontier, frontierWasClear, cell, layouts[height - 1]);
      }
   }

   private static void gatherGrowthBeyond(Level world, BlockPos frontier, boolean wasClear, int cell, int layout) {
      if (wasClear) {
         BlockState grown = world.getBlockState(frontier);
         if (!grown.isAir() && !grown.is(BlockInit.BLOCK_CLUSTER)) {
            world.setBlock(frontier, BlockInit.BLOCK_CLUSTER.defaultBlockState(), 2);
            if (world.getBlockEntity(frontier) instanceof BlockClusterBlockEntity cluster) {
               cluster.setLayout(layout);
               cluster.setCell(cell, grown);
               RelativePlacement.sync(world, frontier, cluster);
               RelativePlacement.notifyColumnBelow(world, frontier, cell);
            }
         }
      }
   }

   public static boolean notifyCell(Level world, BlockPos pos, int cell, Direction direction, BlockState neighbour, BlockPos neighbourPos) {
      if (world.getBlockState(pos).getBlock() instanceof BlockClusterBlock block && world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         BlockState before = cluster.getCell(cell);
         if (before == null) {
            return false;
         }

         block.runAsMember(world, pos, cell, member -> {
            BlockState updated = member.updateShape(direction, neighbour, world, pos, neighbourPos);
            if (!updated.equals(world.getBlockState(pos))) {
               world.setBlock(pos, updated, 2);
            }

            return null;
         });
         if (!(world.getBlockEntity(pos) instanceof BlockClusterBlockEntity after)) {
            return true;
         }

         BlockState now = after.getCell(cell);
         return now == null || !now.equals(before);
      }

      return false;
   }

   private void cullUnsupportedMembers(ServerLevel world, BlockPos pos) {
      for (int cell = 0; cell < 4; cell++) {
         if (!(world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster)) {
            return;
         }

         BlockState member = cluster.getCell(cell);
         if (member != null) {
            boolean standsOnItsOwnCell = peekAsCell(world, pos.below(), cell, () -> member.canSurvive(world, pos));
            if (!standsOnItsOwnCell && member.canSurvive(world, pos)) {
               cluster.setCell(cell, null);
               Block.dropResources(member, world, pos);
               world.levelEvent(2001, pos, Block.getId(member));
               if (cluster.memberCount() == 0) {
                  world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                  return;
               }

               RelativePlacement.sync(world, pos, cluster);
            }
         }
      }
   }

   public static <T> T peekAsCell(Level world, BlockPos pos, int cell, Supplier<T> question) {
      if (world.getBlockState(pos).is(BlockInit.BLOCK_CLUSTER) && world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         BlockState var6 = cluster.getCell(cell);
         return peekAs(world, pos, var6 != null ? var6 : Blocks.AIR.defaultBlockState(), question);
      } else {
         return question.get();
      }
   }

   private void refreshMemberConnections(ServerLevel world, BlockPos pos) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         boolean var15 = false;

         for (int cell = 0; cell < 4; cell++) {
            BlockState member = cluster.getCell(cell);
            if (member != null) {
               BlockState updated = member;

               for (Direction direction : Direction.values()) {
                  BlockPos neighbour = pos.relative(direction);
                  BlockState seen = RelativePlacement.seenBeside(world, pos, cluster, cell, direction);
                  BlockState next = updated.updateShape(direction, seen, world, pos, neighbour);
                  if (!next.isAir()) {
                     updated = next;
                  }
               }

               if (!updated.equals(member)) {
                  cluster.setCell(cell, updated);
                  var15 = true;
               }
            }
         }

         if (var15) {
            RelativePlacement.sync(world, pos, cluster);
         }

         this.refreshPlainNeighbours(world, pos);
      }
   }

   private void refreshPlainNeighbours(ServerLevel world, BlockPos pos) {
      BlockState self = world.getBlockState(pos);
      if (self.is(this)) {
         for (Direction direction : Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockState neighbour = world.getBlockState(neighbourPos);
            if (BarrierJoins.hasArms(neighbour)) {
               BlockState updated = neighbour.updateShape(direction.getOpposite(), self, world, neighbourPos, pos);
               if (!updated.equals(neighbour) && !updated.isAir()) {
                  world.setBlock(neighbourPos, updated, 3);
               }
            }
         }
      }
   }

   /** Hace como si en pos estuviera shown mientras se pregunta question (y lo deja como estaba). */
   public static <T> T peekAs(Level world, BlockPos pos, BlockState shown, Supplier<T> question) {
      BlockState original = world.getBlockState(pos);
      if (original.equals(shown)) {
         return question.get();
      } else {
         BlockState[] cells = null;
         int layout = 0;
         if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
            cells = new BlockState[4];

            for (int i = 0; i < 4; i++) {
               cells[i] = cluster.getCell(i);
            }

            layout = cluster.getLayout();
            cluster.clearCells();
         }

         world.setBlock(pos, shown, SWAP_IN_FLAGS);
         try {
            return question.get();
         } finally {
            world.setBlock(pos, original, SWAP_OUT_FLAGS);
            if (cells != null && world.getBlockEntity(pos) instanceof BlockClusterBlockEntity restored) {
               restored.setLayout(layout);

               for (int i = 0; i < 4; i++) {
                  restored.setCell(i, cells[i]);
               }

               restored.setChanged();
            }
         }
      }
   }

   public static void watchCrosshair(BlockClusterBlock.CrosshairCell watcher) {
      crosshair = watcher;
   }

   /** El sonido (al picar, al andar…) es el del bloque del grupo que se mira (o el primero). */
   @Override
   public net.minecraft.world.level.block.SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable net.minecraft.world.entity.Entity entity) {
      if (level.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         int looking = crosshair.at(pos);
         BlockState member = looking < 0 ? null : cluster.getCell(looking);
         for (int cell = 0; cell < 4 && member == null; cell++) {
            member = cluster.getCell(cell);
         }
         if (member != null && !member.is(this)) {
            return member.getSoundType(level, pos, entity);
         }
      }
      return super.getSoundType(state, level, pos, entity);
   }

   public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         int looking = crosshair.at(pos);
         BlockState member = looking < 0 ? null : cluster.getCell(looking);
         if (member == null) {
            for (int cell = 0; cell < 4 && member == null; cell++) {
               member = cluster.getCell(cell);
            }
         }

         return member == null ? ItemStack.EMPTY : member.getBlock().getCloneItemStack(world, pos, member);
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static int lightOf(@Nullable BlockClusterBlockEntity cluster) {
      if (cluster == null) {
         return 0;
      } else {
         int light = 0;

         for (int cell = 0; cell < 4; cell++) {
            BlockState member = cluster.getCell(cell);
            if (member != null) {
               light = Math.max(light, member.getLightEmission());
            }
         }

         return light;
      }
   }

   public interface CrosshairCell {
      int at(BlockPos var1);
   }
}
