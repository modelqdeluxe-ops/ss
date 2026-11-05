package com.lukeolafp.relativeblocks.blocks;

import com.lukeolafp.relativeblocks.RelativeBlocks;
import com.lukeolafp.relativeblocks.init.BlockInit;
import com.lukeolafp.relativeblocks.mixin.BlockItemAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

public final class RelativePlacement {
   public static final TagKey<Block> DENYLIST = TagKey.create(Registries.BLOCK, RelativeBlocks.id("relative_placement_denied"));
   public static final TagKey<Block> PAIRED = TagKey.create(Registries.BLOCK, RelativeBlocks.id("relative_placement_paired"));
   public static final TagKey<Block> PAIR_ONLY = TagKey.create(Registries.BLOCK, RelativeBlocks.id("relative_placement_pair_only"));
   public static final TagKey<Block> BESIDE_BARRIER = TagKey.create(Registries.BLOCK, RelativeBlocks.id("relative_placement_beside_barrier"));
   private static final List<Property<?>> ORIENTATION = List.of(
      BlockStateProperties.ATTACH_FACE,
      BlockStateProperties.HORIZONTAL_FACING,
      BlockStateProperties.FACING,
      BlockStateProperties.AXIS,
      BlockStateProperties.HORIZONTAL_AXIS,
      BlockStateProperties.ROTATION_16
   );
   // (Forge) el cliente y el servidor integrado lo usan a la vez: un mapa para varios hilos
   private static final Map<BlockState, RelativePlacement.SizeClass> CLASS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
   private static final int COLUMN_REACH = 16;
   private static final Map<Direction, int[]> FILL_ORDER = Map.of(
      Direction.NORTH,
      new int[]{2, 1, 3, 0},
      Direction.SOUTH,
      new int[]{1, 2, 0, 3},
      Direction.EAST,
      new int[]{0, 3, 1, 2},
      Direction.WEST,
      new int[]{3, 0, 2, 1}
   );
   private static final int LINE_REACH = 256;
   private static final ThreadLocal<Boolean> REJOINING = ThreadLocal.withInitial(() -> false);

   private RelativePlacement() {
   }

   // --- En Forge: PlayerInteractEvent.RightClickBlock (en Fabric, UseBlockCallback) y BlockEvent.BreakEvent (en Fabric,
   // PlayerBlockBreakEvents.BEFORE). Van con la prioridad más baja: si una protección (u otro mod) ya los canceló, no
   // se hace nada. ---

   public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      if (BlockInit.BLOCK_CLUSTER == null) {
         return;
      }
      InteractionResult result = useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
      if (result != InteractionResult.PASS) {
         event.setCanceled(true);
         event.setCancellationResult(result);
      }
   }

   public static void onBreak(BlockEvent.BreakEvent event) {
      if (BlockInit.BLOCK_CLUSTER == null || !(event.getLevel() instanceof Level world)) {
         return;
      }
      BlockEntity blockEntity = world.getBlockEntity(event.getPos());
      if (!breakOneMember(world, event.getPlayer(), event.getPos(), event.getState(), blockEntity)) {
         event.setCanceled(true);
      }
   }

   /** Las etiquetas (la lista de bloques que no se juntan, etc.) cambiaron: se vuelve a medir cada bloque. */
   public static void onTagsUpdated(TagsUpdatedEvent event) {
      CLASS_CACHE.clear();
   }

   /**
    * Antes de tocar el mundo en el servidor: ¿puede construir ahí? Se pregunta como si pusiera un bloque (Forge:
    * EntityPlaceEvent) en el bloque que mira y en el de al lado, que es donde puede acabar lo que pone. Así lo respetan
    * las protecciones (las del TF Client, FTB Chunks…), que con un grupo no verían ninguna colocación normal.
    */
   private static boolean mayBuild(Player player, Level world, BlockHitResult hit, BlockPlaceContext context) {
      if (world.isClientSide) {
         return true;
      }
      List<BlockPos> places = new ArrayList<>();
      places.add(context.getClickedPos());
      // el bloque que se mira solo si se le puede añadir algo (un grupo, una losa o algo pequeño)
      BlockState clicked = world.getBlockState(hit.getBlockPos());
      if (!hit.getBlockPos().equals(context.getClickedPos())
         && (clicked.is(BlockInit.BLOCK_CLUSTER) || clicked.getBlock() instanceof SlabBlock || clicked.getBlock() instanceof VerticalSlabBlock
            || classify(clicked) != RelativePlacement.SizeClass.INELIGIBLE)) {
         places.add(hit.getBlockPos());
      }
      for (BlockPos pos : places) {
         if (ForgeEventFactory.onBlockPlace(player, BlockSnapshot.create(world.dimension(), world, pos), hit.getDirection())) {
            return false;
         }
      }
      return true;
   }

   private static InteractionResult useBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
      if (player.isSpectator()) {
         return InteractionResult.PASS;
      } else {
         ItemStack stack = player.getItemInHand(hand);
         if (stack.getItem() instanceof BlockItem blockItem) {
            Block placing = blockItem.getBlock();
            boolean mayAct = placing instanceof SlabBlock || placing instanceof VerticalSlabBlock
               || classify(placing.defaultBlockState()) != RelativePlacement.SizeClass.INELIGIBLE;
            if (mayAct && !mayBuild(player, world, hitResult, new BlockPlaceContext(player, hand, stack, hitResult))) {
               return InteractionResult.FAIL;
            }
            InteractionResult slabs = MixedSlabs.place(player, world, hitResult, stack, blockItem);
            if (slabs != null) {
               return slabs;
            } else if (classify(blockItem.getBlock().defaultBlockState()) == RelativePlacement.SizeClass.INELIGIBLE) {
               return InteractionResult.PASS;
            } else {
               BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hitResult);
               BlockPos target = context.getClickedPos();
               BlockState existing = world.getBlockState(target);
               BlockState candidate = ((BlockItemAccessor)blockItem).relativeblocks$invokeGetPlacementState(context);
               if (candidate == null) {
                  candidate = blockItem.getBlock().defaultBlockState();
               }

               if (existing.is(blockItem.getBlock()) && existing.canBeReplaced(context)) {
                  return InteractionResult.PASS;
               } else if (!candidate.canSurvive(world, target)) {
                  return InteractionResult.PASS;
               } else if (existing.is(BlockInit.BLOCK_CLUSTER)) {
                  return addToCluster(
                     player,
                     world,
                     target,
                     candidate,
                     stack,
                     cellPointedAt(world, hitResult.getBlockPos(), target, hitResult.getLocation()),
                     blockItem,
                     context
                  );
               } else if (classify(existing) != RelativePlacement.SizeClass.INELIGIBLE) {
                  return formCluster(player, world, target, existing, candidate, stack, blockItem, context);
               } else {
                  if (existing.canBeReplaced() && isBarrier(candidate)) {
                     InteractionResult lined = lineUpWithNeighbour(player, world, target, candidate, stack);
                     if (lined != null) {
                        return lined;
                     }
                  }

                  BlockPos clicked = hitResult.getBlockPos();
                  return existing.canBeReplaced() && world.getBlockState(clicked).is(BlockInit.BLOCK_CLUSTER)
                     ? continueFromCluster(player, world, target, clicked, candidate, stack, hitResult.getLocation(), blockItem, context)
                     : InteractionResult.PASS;
               }
            }
         } else {
            return InteractionResult.PASS;
         }
      }
   }

   public static RelativePlacement.SizeClass classify(BlockState state) {
      return CLASS_CACHE.computeIfAbsent(state, RelativePlacement::computeClass);
   }

   private static RelativePlacement.SizeClass computeClass(BlockState state) {
      if (state.is(DENYLIST)) {
         return RelativePlacement.SizeClass.INELIGIBLE;
      } else if (state.hasBlockEntity()) {
         return RelativePlacement.SizeClass.INELIGIBLE;
      } else if (state.getBlock() instanceof FenceGateBlock) {
         return RelativePlacement.SizeClass.GATE;
      } else {
         if (connectsSideways(state)) {
            state = state.getBlock().defaultBlockState();
         }

         VoxelShape outline = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
         if (outline.isEmpty()) {
            return RelativePlacement.SizeClass.INELIGIBLE;
         } else {
            AABB outlineBox = outline.bounds();
            if (outlineBox.maxY > 1.51) {
               return RelativePlacement.SizeClass.INELIGIBLE;
            } else {
               VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
               if (collision.isEmpty()) {
                  return RelativePlacement.SizeClass.FREE;
               } else {
                  AABB box = collision.bounds();
                  boolean thinX = box.getXsize() <= 0.501;
                  boolean thinY = box.getYsize() <= 0.501;
                  boolean thinZ = box.getZsize() <= 0.501;
                  if ((thinX ? 1 : 0) + (thinY ? 1 : 0) + (thinZ ? 1 : 0) < 2) {
                     return RelativePlacement.SizeClass.INELIGIBLE;
                  } else if (!thinX || !thinZ) {
                     return RelativePlacement.SizeClass.QUARTER;
                  } else if (box.maxY <= 0.751) {
                     return RelativePlacement.SizeClass.QUARTER;
                  } else {
                     return connectsSideways(state) ? RelativePlacement.SizeClass.PLANAR : RelativePlacement.SizeClass.QUARTER;
                  }
               }
            }
         }
      }
   }

   private static Axis rodAxis(BlockState state) {
      VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
      if (collision.isEmpty()) {
         return Axis.Y;
      } else {
         AABB box = collision.bounds();
         boolean thinX = box.getXsize() <= 0.501;
         boolean thinY = box.getYsize() <= 0.501;
         boolean thinZ = box.getZsize() <= 0.501;
         if (thinY && thinZ && !thinX) {
            return Axis.X;
         } else {
            return thinX && thinY && !thinZ ? Axis.Z : Axis.Y;
         }
      }
   }

   private static int gridLayout(BlockState reference) {
      Axis wall = wallAxis(reference);
      if (wall != null) {
         return wall == Axis.X ? 3 : 4;
      } else {
         return switch (rodAxis(reference)) {
            case X -> 4;
            case Z -> 3;
            default -> 0;
         };
      }
   }

   private static boolean connectsSideways(BlockState state) {
      return state.hasProperty(BlockStateProperties.NORTH)
         || state.hasProperty(BlockStateProperties.SOUTH)
         || state.hasProperty(BlockStateProperties.EAST)
         || state.hasProperty(BlockStateProperties.WEST)
         || state.hasProperty(BlockStateProperties.NORTH_WALL)
         || state.hasProperty(BlockStateProperties.SOUTH_WALL)
         || state.hasProperty(BlockStateProperties.EAST_WALL)
         || state.hasProperty(BlockStateProperties.WEST_WALL);
   }

   public static boolean isHalves(int layout) {
      return layout == 1 || layout == 2;
   }

   private static boolean isBarrier(BlockState state) {
      RelativePlacement.SizeClass sizeClass = classify(state);
      return sizeClass == RelativePlacement.SizeClass.PLANAR || sizeClass == RelativePlacement.SizeClass.GATE;
   }

   private static boolean fitsBeforeBarrier(BlockState state) {
      return state.is(BESIDE_BARRIER) && !isBarrier(state) && classify(state) != RelativePlacement.SizeClass.INELIGIBLE;
   }

   private static boolean barrierWithFront(BlockState first, BlockState second) {
      return isBarrier(first) ? fitsBeforeBarrier(second) : isBarrier(second) && fitsBeforeBarrier(first);
   }

   private static boolean holdsBarrier(BlockClusterBlockEntity cluster) {
      for (int cell = 0; cell < 4; cell++) {
         BlockState member = cluster.getCell(cell);
         if (member != null && isBarrier(member)) {
            return true;
         }
      }

      return false;
   }

   private static Axis frontAxis(BlockState barrier, Direction playerFacing) {
      return classify(barrier) == RelativePlacement.SizeClass.GATE && barrier.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
         ? ((Direction)barrier.getValue(BlockStateProperties.HORIZONTAL_FACING)).getAxis()
         : playerFacing.getAxis();
   }

   private static Axis splitAxis(int layout) {
      return layout == 1 ? Axis.X : Axis.Z;
   }

   private static boolean joinsUp(Level world, BlockPos pos, BlockState self, Direction toward, BlockPos neighbourPos, BlockState neighbour) {
      if (BarrierJoins.reaches(self.updateShape(toward, neighbour, world, pos, neighbourPos), toward)) {
         return true;
      } else {
         Direction back = toward.getOpposite();
         return BarrierJoins.reaches(neighbour.updateShape(back, self, world, neighbourPos, pos), back);
      }
   }

   @Nullable
   private static InteractionResult lineUpWithNeighbour(Player player, Level world, BlockPos target, BlockState candidate, ItemStack stack) {
      for (Direction direction : Plane.HORIZONTAL) {
         BlockPos neighbourPos = target.relative(direction);
         BlockEntity layout = world.getBlockEntity(neighbourPos);
         if (layout instanceof BlockClusterBlockEntity) {
            BlockClusterBlockEntity neighbour = (BlockClusterBlockEntity)layout;
            if (isHalves(neighbour.getLayout())) {
               int layoutx = neighbour.getLayout();
               Axis split = splitAxis(layoutx);
               boolean roundCorner = direction.getAxis() == split;
               BlockClusterBlockEntity.RenderData data = (BlockClusterBlockEntity.RenderData)neighbour.getRenderData();
               if ((!roundCorner || BarrierJoins.barrierCount(data) >= 2)
                  && (classify(candidate) != RelativePlacement.SizeClass.GATE || !roundCorner && frontAxis(candidate, direction) == split)) {
                  for (int cell = 0; cell < 2; cell++) {
                     BlockState member = neighbour.getCell(cell);
                     if (member != null && isBarrier(member) && joinsUp(world, target, candidate, direction, neighbourPos, member)) {
                        int[] half = roundCorner ? BarrierJoins.beyondCorner(data, cell, direction.getOpposite()) : new int[]{layoutx, cell};
                        if (half != null) {
                           if (world.isClientSide) {
                              return InteractionResult.SUCCESS;
                           }

                           if (!footedInCell(world, target, half[1], candidate)) {
                              return null;
                           }

                           world.setBlock(target, BlockInit.BLOCK_CLUSTER.defaultBlockState(), 2);
                           if (world.getBlockEntity(target) instanceof BlockClusterBlockEntity cluster) {
                              cluster.setLayout(half[0]);
                              cluster.setCell(half[1], candidate);
                              finishChange(player, world, target, cluster, candidate, stack, half[1]);
                              return InteractionResult.SUCCESS;
                           }

                           return InteractionResult.PASS;
                        }
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private static Set<BlockPos> carryLines(Level world, BlockPos start, int startCarrier) {
      if (world.isClientSide) {
         return Set.of(start);
      } else {
         ArrayDeque<BlockPos> queue = new ArrayDeque<>();
         Set<BlockPos> seen = new HashSet<>();
         queue.add(start);
         seen.add(start);
         int steps = 0;

         while (!queue.isEmpty() && steps++ < 256) {
            BlockPos pos = queue.poll();
            if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster && isHalves(cluster.getLayout())) {
               BlockClusterBlockEntity.RenderData data = (BlockClusterBlockEntity.RenderData)cluster.getRenderData();
               Axis split = splitAxis(cluster.getLayout());
               Axis line = BarrierJoins.across(split);
               boolean pair = BarrierJoins.barrierCount(data) >= 2;

               for (Direction direction : Direction.values()) {
                  BlockPos neighbourPos = pos.relative(direction);
                  BlockState neighbour = world.getBlockState(neighbourPos);
                  boolean along = direction.getAxis() != split;
                  if (BarrierJoins.hasArms(neighbour) && !runsAcross(neighbour, along ? line : direction.getAxis())) {
                     int carrier = pos.equals(start) ? startCarrier : carrierFor(cluster, neighbour);
                     BlockState carried = carrier >= 0 ? cluster.getCell(carrier) : null;
                     if (carried != null && BarrierJoins.hasArms(carried) && BarrierJoins.pairs(carried, neighbour)) {
                        int[] target;
                        if (along) {
                           target = new int[]{cluster.getLayout(), carrier};
                        } else if (pair) {
                           target = BarrierJoins.beyondCorner(data, carrier, direction);
                        } else {
                           target = null;
                        }

                        if (target != null && footedInCell(world, neighbourPos, target[1], neighbour)) {
                           moveIntoHalf(world, neighbourPos, neighbour, target[0], target[1]);
                           if (seen.add(neighbourPos)) {
                              queue.add(neighbourPos);
                           }
                        }
                     }
                  }
               }
            }
         }

         return seen;
      }
   }

   private static int carrierFor(BlockClusterBlockEntity cluster, BlockState neighbour) {
      for (int cell = 0; cell < 2; cell++) {
         BlockState member = cluster.getCell(cell);
         if (member != null && BarrierJoins.hasArms(member) && BarrierJoins.pairs(member, neighbour)) {
            return cell;
         }
      }

      return -1;
   }

   private static boolean runsAcross(BlockState barrier, Axis axis) {
      Axis across = BarrierJoins.across(axis);
      Direction positive = Direction.fromAxisAndDirection(across, AxisDirection.POSITIVE);
      return BarrierJoins.reaches(barrier, positive) && BarrierJoins.reaches(barrier, positive.getOpposite());
   }

   private static void moveIntoHalf(Level world, BlockPos pos, BlockState barrier, int layout, int cell) {
      world.setBlock(pos, BlockInit.BLOCK_CLUSTER.defaultBlockState(), 2);
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         cluster.setLayout(layout);
         cluster.setCell(cell, barrier);
         sync(world, pos, cluster);
      }
   }

   private static Axis lineSplit(Level world, BlockPos pos, BlockState barrier, Direction facing) {
      boolean alongX = BarrierJoins.reaches(barrier, Direction.EAST) || BarrierJoins.reaches(barrier, Direction.WEST);
      boolean alongZ = BarrierJoins.reaches(barrier, Direction.NORTH) || BarrierJoins.reaches(barrier, Direction.SOUTH);
      if (alongX != alongZ) {
         return alongX ? Axis.Z : Axis.X;
      } else {
         for (Direction direction : Plane.HORIZONTAL) {
            if (world.getBlockEntity(pos.relative(direction)) instanceof BlockClusterBlockEntity neighbour
               && isHalves(neighbour.getLayout())
               && splitAxis(neighbour.getLayout()) != direction.getAxis()
               && holdsBarrier(neighbour)) {
               return splitAxis(neighbour.getLayout());
            }
         }

         return facing.getAxis();
      }
   }

   private static int halfOfRow(Level world, BlockPos pos, BlockState barrier, int layout) {
      Axis split = splitAxis(layout);

      for (Direction direction : Plane.HORIZONTAL) {
         if (direction.getAxis() != split) {
            BlockEntity cell = world.getBlockEntity(pos.relative(direction));
            if (cell instanceof BlockClusterBlockEntity) {
               BlockClusterBlockEntity neighbour = (BlockClusterBlockEntity)cell;
               if (neighbour.getLayout() == layout) {
                  for (int cellx = 0; cellx < 2; cellx++) {
                     BlockState member = neighbour.getCell(cellx);
                     if (member != null && BarrierJoins.pairs(barrier, member)) {
                        return cellx;
                     }
                  }
               }
            }
         }
      }

      return -1;
   }

   private static boolean stillInLine(Level world, BlockPos pos, int layout, int cell, BlockState barrier) {
      Axis split = splitAxis(layout);
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity self) {
         BlockClusterBlockEntity.RenderData here = (BlockClusterBlockEntity.RenderData)self.getRenderData();

         for (Direction direction : Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockEntity member = world.getBlockEntity(neighbourPos);
            if (member instanceof BlockClusterBlockEntity) {
               BlockClusterBlockEntity neighbour = (BlockClusterBlockEntity)member;
               if (isHalves(neighbour.getLayout()) && neighbour.getLayout() != layout) {
                  int partner = BarrierJoins.partner(here, cell, barrier, direction, (BlockClusterBlockEntity.RenderData)neighbour.getRenderData());
                  BlockState memberx = partner >= 0 ? neighbour.getCell(partner) : null;
                  if (memberx != null && joinsUp(world, pos, barrier, direction, neighbourPos, memberx)) {
                     return true;
                  }
               }
            }
         }
      }

      for (Direction directionx : Direction.values()) {
         if (directionx.getAxis() != split) {
            BlockPos neighbourPos = pos.relative(directionx);
            if (world.getBlockEntity(neighbourPos) instanceof BlockClusterBlockEntity neighbour && neighbour.getLayout() == layout) {
               BlockState member = neighbour.getCell(cell);
               if (member != null && isBarrier(member) && (directionx.getAxis() == Axis.Y || joinsUp(world, pos, barrier, directionx, neighbourPos, member))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static boolean settleLastMember(Level world, BlockPos pos, boolean barriersOnly) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster && cluster.memberCount() == 1) {
         boolean slabs = cluster.getLayout() == 5;
         if (!slabs && !isHalves(cluster.getLayout())) {
            return false;
         }

         int cell = cluster.getCell(0) != null ? 0 : 1;
         BlockState survivor = cluster.getCell(cell);
         if (survivor == null) {
            return false;
         }

         if (slabs || (isBarrier(survivor) ? !stillInLine(world, pos, cluster.getLayout(), cell, survivor) : !barriersOnly)) {
            cluster.clearCells();
            world.setBlock(pos, survivor, 3);
            return true;
         }

         return false;
      }

      return false;
   }

   private static boolean pairIsFinal(BlockClusterBlockEntity cluster, BlockState candidate) {
      if (candidate.is(PAIR_ONLY)) {
         return true;
      } else {
         for (int cell = 0; cell < 4; cell++) {
            BlockState member = cluster.getCell(cell);
            if (member != null && member.is(PAIR_ONLY)) {
               return true;
            }
         }

         return false;
      }
   }

   private static void openOutPair(BlockClusterBlockEntity cluster, Direction playerFacing) {
      List<BlockState> members = new ArrayList<>();

      for (int cell = 0; cell < 4; cell++) {
         if (cluster.getCell(cell) != null) {
            members.add(cluster.getCell(cell));
         }
      }

      int grid = members.isEmpty() ? 0 : gridLayout(members.get(0));
      cluster.setLayout(grid);
      cluster.clearCells();
      int[] order = grid == 0 ? FILL_ORDER.get(playerFacing) : new int[]{0, 1, 2, 3};

      for (int i = 0; i < members.size() && i < order.length; i++) {
         cluster.setCell(order[i], members.get(i));
      }
   }

   @Nullable
   private static Axis wallAxis(BlockState state) {
      if (state.hasProperty(BlockStateProperties.ATTACH_FACE)
         && state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL
         && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
         return ((Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING)).getAxis() == Axis.Z ? Axis.X : Axis.Z;
      } else {
         return null;
      }
   }

   private static int pairLayout(BlockState reference, Direction playerFacing) {
      Axis axis = wallAxis(reference);
      if (axis == null) {
         axis = playerFacing.getAxis() == Axis.X ? Axis.Z : Axis.X;
      }

      return axis == Axis.X ? 1 : 2;
   }

   private static BlockState orientLike(BlockState candidate, BlockState reference) {
      for (Property<?> property : ORIENTATION) {
         candidate = copyProperty(candidate, reference, property);
      }

      return candidate;
   }

   private static <T extends Comparable<T>> BlockState copyProperty(BlockState target, BlockState source, Property<T> property) {
      return target.hasProperty(property) && source.hasProperty(property) ? (BlockState)target.setValue(property, source.getValue(property)) : target;
   }

   private static InteractionResult formCluster(
      Player player, Level world, BlockPos pos, BlockState existing, BlockState candidate, ItemStack stack, BlockItem blockItem, BlockPlaceContext context
   ) {
      boolean planar = classify(existing) == RelativePlacement.SizeClass.PLANAR;
      boolean front = barrierWithFront(existing, candidate);
      if (!front) {
         if (classify(existing) == RelativePlacement.SizeClass.GATE || classify(candidate) == RelativePlacement.SizeClass.GATE) {
            return InteractionResult.PASS;
         }

         if (planar != (classify(candidate) == RelativePlacement.SizeClass.PLANAR)) {
            return InteractionResult.PASS;
         }
      }

      if (world.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         candidate = resolveInCell(world, pos, -1, candidate, blockItem, context);
         if (!footedInCell(world, pos, -1, candidate)) {
            return InteractionResult.PASS;
         } else {
            world.setBlock(pos, BlockInit.BLOCK_CLUSTER.defaultBlockState(), 2);
            if (!(world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster)) {
               return InteractionResult.PASS;
            }

            candidate = orientLike(candidate, existing);
            Direction var21 = player.getDirection();
            int grid = gridLayout(existing);
            int settled;
            if (front) {
               BlockState barrier = isBarrier(existing) ? existing : candidate;
               Axis axis = frontAxis(barrier, var21);
               cluster.setLayout(axis == Axis.X ? 1 : 2);
               double playerSide = axis == Axis.X ? player.getX() - ((double)pos.getX() + 0.5) : player.getZ() - ((double)pos.getZ() + 0.5);
               int farCell = playerSide < 0.0 ? 1 : 0;
               cluster.setCell(farCell, existing);
               cluster.setCell(1 - farCell, candidate);
               settled = farCell;
            } else if (!planar && existing.is(PAIRED) && candidate.is(PAIRED)) {
               cluster.setLayout(pairLayout(existing, var21));
               cluster.setCell(0, existing);
               cluster.setCell(1, candidate);
               settled = 0;
            } else if (!planar && grid != 0) {
               cluster.setLayout(grid);
               cluster.setCell(0, existing);
               cluster.setCell(3, candidate);
               settled = 0;
            } else if (planar) {
               Axis split = lineSplit(world, pos, existing, var21);
               cluster.setLayout(BarrierJoins.layoutSplitting(split));
               int farCell = halfOfRow(world, pos, existing, cluster.getLayout());
               if (farCell < 0) {
                  double playerSide = split == Axis.X ? player.getX() - ((double)pos.getX() + 0.5) : player.getZ() - ((double)pos.getZ() + 0.5);
                  farCell = playerSide < 0.0 ? 1 : 0;
               }

               cluster.setCell(farCell, existing);
               cluster.setCell(1 - farCell, candidate);
               settled = farCell;
            } else {
               int[] order = FILL_ORDER.get(var21);
               cluster.setCell(order[0], existing);
               cluster.setCell(order[1], candidate);
               settled = order[0];
            }

            finishChange(player, world, pos, cluster, candidate, stack, settled);
            carryColumnUp(world, pos, settled, cluster.getLayout());
            return InteractionResult.SUCCESS;
         }
      }
   }

   private static void carryColumnUp(Level world, BlockPos pos, int cell, int layout) {
      BlockPos here = pos.above();

      for (int step = 0; step < 16; step++) {
         BlockState standing = world.getBlockState(here);
         if (standing.isAir() || standing.is(BlockInit.BLOCK_CLUSTER) || classify(standing) == RelativePlacement.SizeClass.INELIGIBLE) {
            return;
         }

         BlockPos above = here;
         boolean restsOnWhatMoved = !BlockClusterBlock.peekAs(world, here.below(), Blocks.AIR.defaultBlockState(), () -> standing.canSurvive(world, above));
         if (!restsOnWhatMoved) {
            return;
         }

         world.setBlock(here, BlockInit.BLOCK_CLUSTER.defaultBlockState(), 2);
         if (!(world.getBlockEntity(here) instanceof BlockClusterBlockEntity carried)) {
            return;
         }

         carried.setLayout(layout);
         carried.setCell(cell, standing);
         sync(world, here, carried);
         here = here.above();
      }
   }

   private static int cellPointedAt(Level world, BlockPos clicked, BlockPos target, Vec3 hit) {
      return !clicked.equals(target)
            && world.getBlockEntity(clicked) instanceof BlockClusterBlockEntity source
            && world.getBlockEntity(target) instanceof BlockClusterBlockEntity destination
            && source.getLayout() == destination.getLayout()
         ? nearestCell(source, clicked, hit)
         : -1;
   }

   private static InteractionResult addToCluster(
      Player player, Level world, BlockPos pos, BlockState candidate, ItemStack stack, int preferredCell, BlockItem blockItem, BlockPlaceContext context
   ) {
      if (!(world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster)) {
         return InteractionResult.PASS;
      } else {
         BlockState reference = firstMember(cluster);
         if (reference == null) {
            return InteractionResult.PASS;
         } else {
            boolean planarClass = classify(candidate) == RelativePlacement.SizeClass.PLANAR;
            boolean beforeBarrier = isHalves(cluster.getLayout()) && holdsBarrier(cluster) && fitsBeforeBarrier(candidate);
            if (!beforeBarrier && planarClass != (classify(reference) == RelativePlacement.SizeClass.PLANAR)) {
               return InteractionResult.PASS;
            } else if (classify(candidate) == RelativePlacement.SizeClass.GATE) {
               return InteractionResult.PASS;
            } else {
               boolean pairLayout = cluster.getLayout() == 1 || cluster.getLayout() == 2;
               boolean opening = pairLayout && !planarClass && !holdsBarrier(cluster) && cluster.memberCount() >= 2 && !pairIsFinal(cluster, candidate);
               if (!opening && cluster.memberCount() >= cluster.capacity()) {
                  return InteractionResult.PASS;
               } else if (world.isClientSide) {
                  return InteractionResult.SUCCESS;
               } else {
                  candidate = orientLike(candidate, reference);
                  if (opening) {
                     openOutPair(cluster, player.getDirection());
                  }

                  int chosen = -1;
                  if (preferredCell >= 0 && preferredCell < cluster.capacity() && cluster.getCell(preferredCell) == null) {
                     chosen = preferredCell;
                  } else {
                     for (int cell : FILL_ORDER.get(player.getDirection())) {
                        if (cell < cluster.capacity() && cluster.getCell(cell) == null) {
                           chosen = cell;
                           break;
                        }
                     }
                  }

                  if (chosen >= 0 && footedInCell(world, pos, chosen, candidate)) {
                     if (placeThroughColumn(world, pos, chosen, cluster.getLayout(), blockItem, context)) {
                        return InteractionResult.SUCCESS;
                     } else {
                        BlockState resolved = resolveInCell(world, pos, chosen, candidate, blockItem, context);
                        if (!resolved.is(candidate.getBlock())) {
                           candidate = orientLike(resolved, reference);
                        }

                        if (!(world.getBlockEntity(pos) instanceof BlockClusterBlockEntity target)) {
                           return InteractionResult.PASS;
                        } else {
                           target.setCell(chosen, candidate);
                           int carrier = target.capacity() == 2 && target.getCell(1 - chosen) != null ? 1 - chosen : chosen;
                           finishChange(player, world, pos, target, candidate, stack, carrier);
                           notifyColumnBelow(world, pos, chosen);
                           return InteractionResult.SUCCESS;
                        }
                     }
                  } else {
                     return InteractionResult.PASS;
                  }
               }
            }
         }
      }
   }

   private static InteractionResult continueFromCluster(
      Player player,
      Level world,
      BlockPos target,
      BlockPos sourcePos,
      BlockState candidate,
      ItemStack stack,
      Vec3 hit,
      BlockItem blockItem,
      BlockPlaceContext context
   ) {
      if (world.getBlockEntity(sourcePos) instanceof BlockClusterBlockEntity source) {
         int cell = nearestCell(source, sourcePos, hit);
         if (cell < 0) {
            return InteractionResult.PASS;
         } else {
            BlockState neighbour = source.getCell(cell);
            if (neighbour == null) {
               return InteractionResult.PASS;
            } else if (world.isClientSide) {
               return InteractionResult.SUCCESS;
            } else if (!footedInCell(world, target, cell, candidate)) {
               return InteractionResult.PASS;
            } else {
               int sourceLayout = source.getLayout();
               if (placeThroughColumn(world, target, cell, sourceLayout, blockItem, context)) {
                  return InteractionResult.SUCCESS;
               } else {
                  candidate = resolveInCell(world, target, cell, candidate, blockItem, context);
                  world.setBlock(target, BlockInit.BLOCK_CLUSTER.defaultBlockState(), 2);
                  if (world.getBlockEntity(target) instanceof BlockClusterBlockEntity cluster) {
                     cluster.setLayout(sourceLayout);
                     cluster.setCell(cell, candidate);
                     finishChange(player, world, target, cluster, candidate, stack, cell);
                     notifyColumnBelow(world, target, cell);
                     return InteractionResult.SUCCESS;
                  } else {
                     return InteractionResult.PASS;
                  }
               }
            }
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   private static boolean footedInCell(Level world, BlockPos pos, int cell, BlockState candidate) {
      BlockPos below = pos.below();
      boolean needsGround = !BlockClusterBlock.peekAs(world, below, Blocks.AIR.defaultBlockState(), () -> candidate.canSurvive(world, pos));
      if (!needsGround) {
         return true;
      } else {
         BlockState under = world.getBlockState(below);
         if (under.isAir()) {
            return false;
         } else {
            return !under.is(BlockInit.BLOCK_CLUSTER)
               ? classify(under) == RelativePlacement.SizeClass.INELIGIBLE
               : cell < 0 || BlockClusterBlock.peekAsCell(world, below, cell, () -> candidate.canSurvive(world, pos));
         }
      }
   }

   private static boolean placeThroughColumn(Level world, BlockPos target, int cell, int layout, BlockItem blockItem, BlockPlaceContext context) {
      if (world.getBlockEntity(target.below()) instanceof BlockClusterBlockEntity beneath && beneath.getCell(cell) != null) {
         boolean[] placed = new boolean[]{false};
         BlockClusterBlock.withColumnAsCell(world, target, cell, layout, () -> placed[0] = blockItem.place(context).consumesAction());
         return placed[0];
      }

      return false;
   }

   public static BlockState seenFromCell(Level world, BlockPos pos, int cell) {
      BlockState state = world.getBlockState(pos);
      if (state.is(BlockInit.BLOCK_CLUSTER)) {
         if (cell >= 0 && world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
            BlockState member = cluster.getCell(cell);
            return member != null ? member : Blocks.AIR.defaultBlockState();
         } else {
            return state;
         }
      } else {
         return classify(state) == RelativePlacement.SizeClass.INELIGIBLE ? state : Blocks.AIR.defaultBlockState();
      }
   }

   public static BlockState seenBeside(Level world, BlockPos pos, BlockClusterBlockEntity cluster, int cell, Direction direction) {
      BlockPos neighbourPos = pos.relative(direction);
      BlockState member = cluster.getCell(cell);
      if (member != null && BarrierJoins.isBarrier(member) && direction.getAxis().isHorizontal()) {
         BlockClusterBlockEntity.RenderData here = (BlockClusterBlockEntity.RenderData)cluster.getRenderData();
         BarrierLayout.Lookup lookup = BarrierLayout.of(world);
         if (!BarrierJoins.screened(here, cell, direction) && !BarrierLayout.crossesAnother(lookup, pos, here, cell, direction)) {
            if (world.getBlockEntity(neighbourPos) instanceof BlockClusterBlockEntity neighbour) {
               BlockClusterBlockEntity.RenderData there = (BlockClusterBlockEntity.RenderData)neighbour.getRenderData();
               int partner = BarrierJoins.partner(here, cell, member, direction, there);
               if (partner >= 0 && BarrierLayout.crossesAnother(lookup, neighbourPos, there, partner, direction.getOpposite())) {
                  partner = -1;
               }

               BlockState seen = partner >= 0 ? neighbour.getCell(partner) : null;
               return seen != null ? seen : Blocks.AIR.defaultBlockState();
            } else {
               BlockState plain = world.getBlockState(neighbourPos);
               return BarrierJoins.isBarrier(plain) ? plain : seenFromCell(world, neighbourPos, cell);
            }
         } else {
            return Blocks.AIR.defaultBlockState();
         }
      } else {
         return seenFromCell(world, neighbourPos, cell);
      }
   }

   @Nullable
   public static BlockState seenByPlain(LevelAccessor world, BlockState self, Direction toward, BlockPos neighbourPos) {
      if (world.getBlockEntity(neighbourPos) instanceof BlockClusterBlockEntity neighbour) {
         BlockClusterBlockEntity.RenderData var7 = (BlockClusterBlockEntity.RenderData)neighbour.getRenderData();
         int partner = BarrierJoins.partner(null, -1, self, toward, var7);
         if (partner < 0 || BarrierLayout.crossesAnother(BarrierLayout.of(world), neighbourPos, var7, partner, toward.getOpposite())) {
            return null;
         } else {
            return BarrierJoins.meetsSideOn(self, toward, var7.layout(), partner) ? null : neighbour.getCell(partner);
         }
      } else {
         return null;
      }
   }

   public static BlockState rejoinClusters(LevelAccessor world, BlockPos pos, BlockState state, Direction changed) {
      if (REJOINING.get()) {
         return state;
      } else {
         REJOINING.set(true);

         BlockState var11;
         try {
            for (Direction side : Plane.HORIZONTAL) {
               BlockPos clusterPos = pos.relative(side);
               BlockState there = world.getBlockState(clusterPos);
               if (side != changed && there.is(BlockInit.BLOCK_CLUSTER)) {
                  state = state.updateShape(side, there, world, pos, clusterPos);
               }
            }

            var11 = state;
         } finally {
            REJOINING.set(false);
         }

         return var11;
      }
   }

   private static BlockState resolveInCell(Level world, BlockPos target, int cell, BlockState candidate, BlockItem blockItem, BlockPlaceContext context) {
      BlockState under = seenFromCell(world, target.below(), cell);
      BlockState over = seenFromCell(world, target.above(), cell);
      BlockState resolved = BlockClusterBlock.peekAs(
         world,
         target.below(),
         under,
         () -> BlockClusterBlock.peekAs(world, target.above(), over, () -> ((BlockItemAccessor)blockItem).relativeblocks$invokeGetPlacementState(context))
      );
      return resolved != null && classify(resolved) != RelativePlacement.SizeClass.INELIGIBLE ? resolved : candidate;
   }

   public static void notifyColumnBelow(Level world, BlockPos pos, int cell) {
      BlockPos here = pos;

      for (int step = 0; step < 16; step++) {
         BlockState above = memberIn(world, here, cell);
         if (above == null) {
            return;
         }

         BlockPos below = here.below();
         if (!BlockClusterBlock.notifyCell(world, below, cell, Direction.UP, above, here)) {
            return;
         }

         here = below;
      }
   }

   @Nullable
   private static BlockState memberIn(Level world, BlockPos pos, int cell) {
      if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster) {
         return cluster.getCell(cell);
      } else {
         BlockState state = world.getBlockState(pos);
         return state.isAir() ? null : state;
      }
   }

   @Nullable
   private static BlockState firstMember(BlockClusterBlockEntity cluster) {
      for (int cell = 0; cell < 4; cell++) {
         if (cluster.getCell(cell) != null) {
            return cluster.getCell(cell);
         }
      }

      return null;
   }

   private static void finishChange(Player player, Level world, BlockPos pos, BlockClusterBlockEntity cluster, BlockState placed, ItemStack stack, int carrier) {
      if (!player.getAbilities().instabuild) {
         stack.shrink(1);
      }

      world.playSound(
         null,
         pos,
         placed.getSoundType().getPlaceSound(),
         SoundSource.BLOCKS,
         (placed.getSoundType().getVolume() + 1.0F) / 2.0F,
         placed.getSoundType().getPitch() * 0.8F
      );
      sync(world, pos, cluster);

      for (BlockPos moved : carryLines(world, pos, carrier)) {
         BlockClusterBlock.settleNow(world, moved);
      }
   }

   public static void sync(Level world, BlockPos pos, BlockClusterBlockEntity cluster) {
      BlockState state = world.getBlockState(pos);
      int light = BlockClusterBlock.lightOf(cluster);
      if (state.is(BlockInit.BLOCK_CLUSTER) && (Integer)state.getValue(BlockClusterBlock.LIGHT_LEVEL) != light) {
         world.setBlock(pos, (BlockState)state.setValue(BlockClusterBlock.LIGHT_LEVEL, light), 3);
         state = world.getBlockState(pos);
      }

      cluster.setChanged();
      BlockClusterBlockEntity.forgetShapesAround(world, pos);
      world.sendBlockUpdated(pos, state, state, 3);
      world.updateNeighborsAt(pos, BlockInit.BLOCK_CLUSTER);

      for (Direction direction : Direction.values()) {
         world.updateNeighborsAt(pos.relative(direction), BlockInit.BLOCK_CLUSTER);
      }

      BlockClusterBlock.refreshConnectionsAround(world, pos);
      BlockClusterBlock.followPendingTicks(world, pos, cluster);
   }

   private static boolean breakOneMember(Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
      if (state.is(BlockInit.BLOCK_CLUSTER) && blockEntity instanceof BlockClusterBlockEntity cluster) {
         if (world.isClientSide) {
            return false;
         } else {
            removeMemberUnderCrosshair(world, player, pos, cluster);
            return false;
         }
      } else {
         return true;
      }
   }

   private static void removeMemberUnderCrosshair(Level world, Player player, BlockPos pos, BlockClusterBlockEntity cluster) {
      int cell = targetedCell(player, pos, cluster);
      if (cell >= 0) {
         BlockState member = cluster.getCell(cell);
         cluster.setCell(cell, null);
         if (!player.getAbilities().instabuild) {
            ItemStack tool = player.getMainHandItem().copy();
            boolean earned = player.hasCorrectToolForDrops(member);
            player.getMainHandItem().mineBlock(world, member, pos, player);
            if (earned) {
               member.getBlock().playerDestroy(world, player, pos, member, null, tool);
            }
         }

         world.levelEvent(2001, pos, Block.getId(member));
         if (cluster.memberCount() == 0) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         } else if (!settleLastMember(world, pos, false)) {
            sync(world, pos, cluster);
         }

         BlockClusterBlock.settleNow(world, pos);
         dropColumnAbove(world, pos, cell);
         if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundBlockUpdatePacket(pos, world.getBlockState(pos)));
            if (world.getBlockEntity(pos) instanceof BlockClusterBlockEntity survivor) {
               Packet<ClientGamePacketListener> packet = survivor.getUpdatePacket();
               if (packet != null) {
                  serverPlayer.connection.send(packet);
               }
            }
         }
      }
   }

   private static void dropColumnAbove(Level world, BlockPos below, int cell) {
      for (BlockPos pos = below.above();
         world.getBlockState(pos).is(BlockInit.BLOCK_CLUSTER) && world.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster;
         pos = pos.above()
      ) {
         BlockState member = cluster.getCell(cell);
         if (member == null) {
            return;
         }

         BlockPos here = pos;
         Boolean stands = BlockClusterBlock.peekAsCell(world, pos.below(), cell, () -> member.canSurvive(world, here));
         if (stands == null || stands) {
            return;
         }

         cluster.setCell(cell, null);
         Block.dropResources(member, world, pos);
         world.levelEvent(2001, pos, Block.getId(member));
         if (cluster.memberCount() == 0) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         } else {
            sync(world, pos, cluster);
         }
      }
   }

   @Nullable
   public static BlockState memberUnderCrosshair(Player player, BlockPos pos, BlockClusterBlockEntity cluster) {
      int cell = targetedCell(player, pos, cluster);
      return cell < 0 ? null : cluster.getCell(cell);
   }

   private static int targetedCell(Player player, BlockPos pos, BlockClusterBlockEntity cluster) {
      double reach = player.isCreative() ? 5.0 : 4.5;
      HitResult crosshair = player.pick(reach + 1.0, 1.0F, false);
      Vec3 hit = crosshair.getType() == Type.BLOCK ? crosshair.getLocation() : Vec3.atCenterOf(pos);
      return nearestCell(cluster, pos, hit);
   }

   public static int nearestCell(BlockClusterBlockEntity cluster, BlockPos pos, Vec3 hit) {
      double localX = hit.x() - (double)pos.getX();
      double localY = hit.y() - (double)pos.getY();
      double localZ = hit.z() - (double)pos.getZ();
      int nearest = -1;
      double nearestDistance = Double.MAX_VALUE;
      if (cluster.getLevel() != null) {
         for (int cell = 0; cell < 4; cell++) {
            if (cluster.getCell(cell) != null) {
               VoxelShape shape = BlockClusterBlock.cellShape(cluster.getLevel(), pos, cluster, cell, false);

               for (AABB box : shape.toAabbs()) {
                  double dx = Math.max(0.0, Math.max(box.minX - localX, localX - box.maxX));
                  double dy = Math.max(0.0, Math.max(box.minY - localY, localY - box.maxY));
                  double dz = Math.max(0.0, Math.max(box.minZ - localZ, localZ - box.maxZ));
                  double distance = dx * dx + dy * dy + dz * dz;
                  if (distance < nearestDistance) {
                     nearestDistance = distance;
                     nearest = cell;
                  }
               }
            }
         }

         if (nearest >= 0) {
            return nearest;
         }
      }

      for (int cellx = 0; cellx < 4; cellx++) {
         if (cluster.getCell(cellx) != null) {
            double dx = localX - (0.5 + cluster.cellOffsetX(cellx));
            double dy = localY - (0.5 + cluster.cellOffsetY(cellx));
            double dz = localZ - (0.5 + cluster.cellOffsetZ(cellx));
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
               nearestDistance = distance;
               nearest = cellx;
            }
         }
      }

      return nearest;
   }

   public static enum SizeClass {
      FREE,
      QUARTER,
      PLANAR,
      GATE,
      INELIGIBLE;
   }
}
