package com.lukeolafp.relativeblocks.blocks;

import com.lukeolafp.relativeblocks.init.BlockEntityInit;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockClusterBlockEntity extends BlockEntity {
   public static final int CELL_COUNT = 4;
   public static final int LAYOUT_QUAD = 0;
   public static final int LAYOUT_PLANAR_X = 1;
   public static final int LAYOUT_PLANAR_Z = 2;
   public static final int LAYOUT_QUAD_WALL_X = 3;
   public static final int LAYOUT_QUAD_WALL_Z = 4;
   public static final int LAYOUT_SLABS = 5;
   /** Lo que lleva dentro, para el modelo del grupo (en Fabric, getRenderData de RenderDataBlockEntity). */
   public static final ModelProperty<RenderData> MODEL_DATA = new ModelProperty<>();
   private final BlockState[] cells = new BlockState[4];
   private int layout = 0;
   private final VoxelShape[] outlines = new VoxelShape[4];
   private final VoxelShape[] collisions = new VoxelShape[4];
   @Nullable
   private BlockState standIn;

   public BlockClusterBlockEntity(BlockPos pos, BlockState state) {
      super(BlockEntityInit.BLOCK_CLUSTER, pos, state);
   }

   public int getLayout() {
      return this.layout;
   }

   public void setLayout(int layout) {
      this.layout = layout;
      this.forgetShapes();
   }

   public int capacity() {
      return this.layout != 1 && this.layout != 2 && this.layout != 5 ? 4 : 2;
   }

   @Nullable
   public BlockState getCell(int cell) {
      return this.cells[cell];
   }

   public void setCell(int cell, @Nullable BlockState state) {
      this.cells[cell] = state;
      this.forgetShapes();
   }

   public void clearCells() {
      this.forgetShapes();

      for (int i = 0; i < 4; i++) {
         this.cells[i] = null;
      }
   }

   @Nullable
   public VoxelShape cachedShape(int cell, boolean collision) {
      return (collision ? this.collisions : this.outlines)[cell];
   }

   public void cacheShape(int cell, boolean collision, VoxelShape shape) {
      (collision ? this.collisions : this.outlines)[cell] = shape;
   }

   public void forgetShapes() {
      Arrays.fill(this.outlines, null);
      Arrays.fill(this.collisions, null);
   }

   public static void forgetShapesAround(Level world, BlockPos pos) {
      for (BlockPos at : new BlockPos[]{pos, pos.north(), pos.south(), pos.east(), pos.west()}) {
         if (world.getBlockEntity(at) instanceof BlockClusterBlockEntity cluster) {
            cluster.forgetShapes();
         }
      }
   }

   public int memberCount() {
      int count = 0;

      for (BlockState cell : this.cells) {
         if (cell != null) {
            count++;
         }
      }

      return count;
   }

   public static double cellOffsetX(int layout, int cell) {
      return switch (layout) {
         case 1 -> cell == 0 ? -0.25 : 0.25;
         case 2, 4, 5 -> 0.0;
         case 3 -> (cell & 1) == 0 ? -0.25 : 0.25;
         default -> (cell & 1) == 0 ? -0.25 : 0.25;
      };
   }

   public static double cellOffsetY(int layout, int cell) {
      return switch (layout) {
         case 3, 4 -> (cell & 2) == 0 ? -0.25 : 0.25;
         default -> 0.0;
      };
   }

   public static double cellOffsetZ(int layout, int cell) {
      return switch (layout) {
         case 1, 3, 5 -> 0.0;
         case 2 -> cell == 0 ? -0.25 : 0.25;
         case 4 -> (cell & 1) == 0 ? -0.25 : 0.25;
         default -> (cell & 2) == 0 ? -0.25 : 0.25;
      };
   }

   public double cellOffsetX(int cell) {
      return cellOffsetX(this.layout, cell);
   }

   public double cellOffsetY(int cell) {
      return cellOffsetY(this.layout, cell);
   }

   public double cellOffsetZ(int cell) {
      return cellOffsetZ(this.layout, cell);
   }

   public void setStandIn(@Nullable BlockState standIn) {
      this.standIn = standIn;
      this.requestModelDataUpdate();
   }

   @Override
   public @NotNull ModelData getModelData() {
      return ModelData.builder().with(MODEL_DATA, this.getRenderData()).build();
   }

   public RenderData getRenderData() {
      if (this.standIn != null && this.memberCount() == 0) {
         BlockState[] alone = new BlockState[4];
         alone[0] = this.standIn;
         return new BlockClusterBlockEntity.RenderData(5, alone);
      } else {
         return new BlockClusterBlockEntity.RenderData(this.layout, (BlockState[])this.cells.clone());
      }
   }

   protected void saveAdditional(CompoundTag nbt) {
      super.saveAdditional(nbt);
      ListTag list = new ListTag();

      for (int i = 0; i < 4; i++) {
         if (this.cells[i] != null) {
            CompoundTag entry = new CompoundTag();
            entry.putByte("Cell", (byte)i);
            entry.put("State", NbtUtils.writeBlockState(this.cells[i]));
            list.add(entry);
         }
      }

      nbt.put("Members", list);
      nbt.putByte("Layout", (byte)this.layout);
   }

   public void load(CompoundTag nbt) {
      super.load(nbt);
      this.layout = nbt.getByte("Layout");
      this.clearCells();
      ListTag list = nbt.getList("Members", 10);

      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         int cell = entry.getByte("Cell");
         if (cell >= 0 && cell < 4) {
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("State"));
            this.cells[cell] = state.isAir() ? null : state;
         }
      }

      if (this.level != null && this.level.isClientSide) {
         this.standIn = null;
         forgetShapesAround(this.level, this.worldPosition);
         // el dibujo nuevo necesita los datos nuevos (Forge: ModelDataManager) antes de volver a dibujar el trozo
         this.requestModelDataUpdate();
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 0);
      }
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public CompoundTag getUpdateTag() {
      return this.saveWithoutMetadata();
   }

   public static record RenderData(int layout, BlockState[] cells) {
   }
}
