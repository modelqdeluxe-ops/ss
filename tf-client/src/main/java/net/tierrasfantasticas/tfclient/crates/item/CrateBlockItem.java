package net.tierrasfantasticas.tfclient.crates.item;

import net.tierrasfantasticas.tfclient.crates.client.render.CrateItemRenderer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class CrateBlockItem extends BlockItem {
   public CrateBlockItem(Block block, Properties props) {
      super(block, props);
   }

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         private BlockEntityWithoutLevelRenderer renderer;

         public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (this.renderer == null) {
               this.renderer = new CrateItemRenderer();
            }

            return this.renderer;
         }
      });
   }
}
