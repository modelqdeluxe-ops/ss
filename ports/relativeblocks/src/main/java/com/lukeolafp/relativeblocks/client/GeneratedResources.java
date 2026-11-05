package com.lukeolafp.relativeblocks.client;

import com.lukeolafp.relativeblocks.RelativeBlocks;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabBlock;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Las losas verticales se crean al arrancar, una por cada losa de cualquier mod: no pueden traer sus archivos de estado
 * ni de modelo de objeto en el jar. En Fabric se ponía el modelo directamente; en Forge, Minecraft buscaría esos
 * archivos y llenaría el registro de errores si faltan. Así que se escriben al arrancar (en
 * {@code <juego>/relativeblocks/generated}) y se cargan como un paquete de recursos fijo; el modelo de verdad lo pone
 * RelativeBlocksClient al acabar de cargar los modelos.
 */
@OnlyIn(Dist.CLIENT)
public final class GeneratedResources {
   private static final String PLACEHOLDER = RelativeBlocks.MOD_ID + ":block/vertical_slab";

   private GeneratedResources() {
   }

   public static void onAddPackFinders(AddPackFindersEvent event) {
      if (event.getPackType() != PackType.CLIENT_RESOURCES) {
         return;
      }
      Path root = FMLPaths.GAMEDIR.get().resolve(RelativeBlocks.MOD_ID).resolve("generated");
      try {
         write(root);
      } catch (IOException e) {
         RelativeBlocks.LOGGER.warn("Could not write the vertical slab resources to {}: {}", root, e.toString());
         return;
      }
      event.addRepositorySource(consumer -> {
         Pack pack = Pack.readMetaAndCreate(
            "relativeblocks_vertical_slabs", Component.literal("Relatively Placed Blocks: vertical slabs"), true,
            id -> new PathPackResources(id, root, true), PackType.CLIENT_RESOURCES, Pack.Position.BOTTOM, PackSource.BUILT_IN
         );
         if (pack != null) {
            consumer.accept(pack);
         }
      });
   }

   private static void write(Path root) throws IOException {
      if (Files.exists(root)) {
         try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
               Files.deleteIfExists(p);
            }
         }
      }
      Files.createDirectories(root);
      Files.writeString(root.resolve("pack.mcmeta"),
         "{\"pack\":{\"description\":\"Relatively Placed Blocks: vertical slabs\",\"pack_format\":15}}", StandardCharsets.UTF_8);
      Path assets = root.resolve("assets").resolve(RelativeBlocks.MOD_ID);
      for (VerticalSlabBlock vertical : VerticalSlabs.all()) {
         ResourceLocation id = VerticalSlabs.idOf(vertical);
         if (id == null) {
            continue;
         }
         Path state = assets.resolve("blockstates").resolve(id.getPath() + ".json");
         Files.createDirectories(state.getParent());
         Files.writeString(state, "{\"variants\":{\"\":{\"model\":\"" + PLACEHOLDER + "\"}}}", StandardCharsets.UTF_8);
         Path item = assets.resolve("models").resolve("item").resolve(id.getPath() + ".json");
         Files.createDirectories(item.getParent());
         Files.writeString(item, "{\"parent\":\"" + PLACEHOLDER + "\"}", StandardCharsets.UTF_8);
      }
   }
}
