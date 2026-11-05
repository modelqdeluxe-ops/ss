package com.lukeolafp.relativeblocks.prueba;

import com.lukeolafp.relativeblocks.RelativeBlocks;
import com.lukeolafp.relativeblocks.blocks.BlockClusterBlockEntity;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabBlock;
import com.lukeolafp.relativeblocks.blocks.VerticalSlabs;
import com.lukeolafp.relativeblocks.init.BlockInit;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * La prueba en el juego del port (solo con -Pprueba y RB_PRUEBA en el entorno: .github/workflows/relativeblocks.yml).
 * Crea un mundo plano, junta bloques pequeños en un hueco con clics de verdad (cliente → servidor integrado), mezcla
 * losas, pone losas verticales, rompe un bloque de un grupo y saca capturas; escribe run/pruebas/informe.txt.
 */
@Mod.EventBusSubscriber(modid = RelativeBlocks.MOD_ID, value = Dist.CLIENT)
public final class PruebaRelativeBlocks {
   static final boolean ON = System.getenv("RB_PRUEBA") != null;
   private static final List<String> LINES = new ArrayList<>();
   private static int fallos;
   private static boolean started;
   private static int menuTicks;
   private static int t;
   private static String shot;
   private static boolean done;

   // y = -60: el aire encima de la hierba del mundo plano
   private static final BlockPos A = new BlockPos(2, -60, 4);
   private static final BlockPos B = new BlockPos(4, -60, 4);
   private static final BlockPos C = new BlockPos(6, -60, 4);
   private static final BlockPos D = new BlockPos(8, -60, 4);
   private static final BlockPos E = new BlockPos(10, -60, 4);

   private PruebaRelativeBlocks() {
   }

   private static void check(String what, boolean ok, String detail) {
      LINES.add((ok ? "bien  · " : "MAL   · ") + what + (detail.isEmpty() ? "" : " (" + detail + ")"));
      if (!ok) {
         fallos++;
      }
   }

   private static void note(String text) {
      LINES.add("        " + text);
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (!ON || event.phase != TickEvent.Phase.END || done) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (!started) {
         if (mc.getOverlay() != null || mc.screen == null || ++menuTicks < 40) {
            return;
         }
         started = true;
         mc.options.pauseOnLostFocus = false;
         mc.options.renderDistance().set(5);
         LevelSettings settings = new LevelSettings("prueba_relativeblocks", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
         mc.createWorldOpenFlows().createFreshLevel("prueba_relativeblocks", settings, new WorldOptions(20261105L, false, false),
            reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
         return;
      }
      if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
         return;
      }
      if (mc.screen != null && mc.screen.isPauseScreen()) {
         mc.setScreen(null);
      }
      MinecraftServer server = mc.getSingleplayerServer();
      t++;
      try {
         step(mc, server);
      } catch (Throwable e) {
         check("paso " + t + " sin errores", false, e.toString());
         RelativeBlocks.LOGGER.error("Prueba: error en el paso {}", t, e);
         finish(mc);
      }
   }

   private static ServerPlayer serverPlayer(MinecraftServer server) {
      return server.getPlayerList().getPlayers().get(0);
   }

   private static void hold(MinecraftServer server, Item item) {
      server.execute(() -> {
         ServerPlayer p = serverPlayer(server);
         p.getInventory().setItem(p.getInventory().selected, new ItemStack(item));
      });
   }

   private static void click(Minecraft mc, BlockPos clicked, Direction side, Vec3 at) {
      mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(at, side, clicked, false));
   }

   private static String members(ServerLevel level, BlockPos pos) {
      if (!(level.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster)) {
         return "no es un grupo: " + level.getBlockState(pos);
      }
      StringBuilder b = new StringBuilder("capa " + cluster.getLayout() + ":");
      for (int i = 0; i < 4; i++) {
         BlockState s = cluster.getCell(i);
         if (s != null) {
            b.append(' ').append(i).append('=').append(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
         }
      }
      return b.toString();
   }

   private static int count(ServerLevel level, BlockPos pos) {
      return level.getBlockEntity(pos) instanceof BlockClusterBlockEntity cluster ? cluster.memberCount() : 0;
   }

   private static void step(Minecraft mc, MinecraftServer server) {
      ServerLevel level = server.overworld();
      switch (t) {
         case 60 -> server.execute(() -> {
            ServerPlayer p = serverPlayer(server);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doDaylightCycle false");
            p.teleportTo(level, 6.5, -60, 0.5, 0F, 35F);
            int n = VerticalSlabs.all().size();
            check("losas verticales registradas (una por losa)", n >= 50, n + "");
            ResourceLocation oak = RelativeBlocks.id("vertical_slab/minecraft/oak_slab");
            check("existe la losa vertical de roble", BuiltInRegistries.BLOCK.get(oak) instanceof VerticalSlabBlock, oak.toString());
            check("y su objeto", BuiltInRegistries.ITEM.containsKey(oak), "");
         });
         // A: una amapola y, al lado en el mismo hueco, un diente de león
         case 80 -> server.execute(() -> level.setBlock(A, Blocks.POPPY.defaultBlockState(), 3));
         case 82 -> hold(server, Items.DANDELION);
         case 90 -> click(mc, A.below(), Direction.UP, new Vec3(A.getX() + 0.8, A.getY(), A.getZ() + 0.8));
         // B: dos antorchas en el mismo hueco
         case 100 -> server.execute(() -> level.setBlock(B, Blocks.TORCH.defaultBlockState(), 3));
         case 102 -> hold(server, Items.REDSTONE_TORCH);
         case 110 -> click(mc, B.below(), Direction.UP, new Vec3(B.getX() + 0.8, B.getY(), B.getZ() + 0.5));
         // C: una losa de roble abajo y una de piedra arriba (mezcladas)
         case 120 -> server.execute(() -> level.setBlock(C, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3));
         case 122 -> hold(server, Items.STONE_SLAB);
         case 130 -> click(mc, C, Direction.UP, new Vec3(C.getX() + 0.5, C.getY() + 0.5, C.getZ() + 0.5));
         // D: una losa vertical de roble y, en su otra mitad, una vertical de piedra
         case 140 -> hold(server, BuiltInRegistries.ITEM.get(RelativeBlocks.id("vertical_slab/minecraft/oak_slab")));
         case 150 -> click(mc, D.below(), Direction.UP, new Vec3(D.getX() + 0.5, D.getY(), D.getZ() + 0.2));
         case 156 -> hold(server, BuiltInRegistries.ITEM.get(RelativeBlocks.id("vertical_slab/minecraft/stone_slab")));
         case 166 -> {
            BlockState vertical = mc.level.getBlockState(D);
            if (vertical.getBlock() instanceof VerticalSlabBlock) {
               Direction open = vertical.getValue(VerticalSlabBlock.FACING).getOpposite();
               Vec3 at = Vec3.atCenterOf(D).add(open.getStepX() * 0.25, 0.5, open.getStepZ() * 0.25);
               click(mc, D, Direction.UP, at);
            }
         }
         // E: una flor sola y una valla al lado (que no se rompa nada)
         case 170 -> server.execute(() -> {
            level.setBlock(E, Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(E.east(), Blocks.BLUE_ORCHID.defaultBlockState(), 3);
         });
         case 200 -> server.execute(() -> {
            check("A: amapola + diente de león en un grupo", count(level, A) == 2, members(level, A));
            check("B: antorcha + antorcha de redstone en un grupo", count(level, B) == 2, members(level, B));
            check("C: losa de roble abajo + losa de piedra arriba", count(level, C) == 2, members(level, C));
            BlockState d = level.getBlockState(D);
            check("D: dos losas verticales en un hueco", count(level, D) == 2, members(level, D));
            check("el cliente ve los mismos grupos", mc.level.getBlockEntity(A) instanceof BlockClusterBlockEntity a && a.memberCount() == 2
               && mc.level.getBlockEntity(C) instanceof BlockClusterBlockEntity c && c.memberCount() == 2, "");
            check("el grupo tiene luz (antorchas)", level.getBlockState(B).is(BlockInit.BLOCK_CLUSTER) && level.getBlockState(B).getLightEmission() > 0,
               level.getBlockState(B).toString());
            note("D: " + d);
         });
         case 210 -> {
            mc.options.hideGui = true;
            mc.player.setYRot(0F);
            mc.player.setXRot(35F);
            shot = "1_grupos";
         }
         // romper la amapola o el diente de león mirándolo (en creativo: un golpe)
         case 240 -> server.execute(() -> {
            ServerPlayer p = serverPlayer(server);
            p.teleportTo(level, A.getX() + 0.5, A.getY(), A.getZ() - 1.5, 0F, 45F);
         });
         case 250 -> {
            mc.player.setYRot(0F);
            mc.player.setXRot(45F);
         }
         case 256 -> mc.gameMode.startDestroyBlock(A, Direction.UP);
         case 270 -> server.execute(() -> {
            check("al romper, solo se va el bloque que se mira", count(level, A) == 1, members(level, A));
            check("el grupo sigue en su sitio", level.getBlockState(A).is(BlockInit.BLOCK_CLUSTER), level.getBlockState(A).toString());
         });
         case 280 -> server.execute(() -> serverPlayer(server).teleportTo(level, 6.5, -60, 0.5, 0F, 35F));
         case 290 -> {
            mc.player.setYRot(0F);
            mc.player.setXRot(35F);
            shot = "2_tras_romper";
         }
         case 300 -> {
            // un vistazo de cerca a las losas mezcladas y verticales
            server.execute(() -> serverPlayer(server).teleportTo(level, 7.5, -59.4, 2.2, 0F, 30F));
         }
         case 310 -> {
            mc.player.setYRot(-20F);
            mc.player.setXRot(30F);
            shot = "3_losas";
         }
         case 340 -> finish(mc);
         default -> {
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (!ON || shot == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      String name = shot;
      shot = null;
      // al acabar el fotograma (con la interfaz oculta)
      mc.tell(() -> {
         try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            File dir = new File(mc.gameDirectory, "pruebas");
            dir.mkdirs();
            img.writeToFile(new File(dir, name + ".png"));
         } catch (Exception e) {
            RelativeBlocks.LOGGER.warn("Prueba: no se pudo sacar la captura {}", name, e);
         }
      });
   }

   private static void finish(Minecraft mc) {
      if (done) {
         return;
      }
      done = true;
      try {
         File dir = new File(mc.gameDirectory, "pruebas");
         dir.mkdirs();
         LINES.add(fallos == 0 ? "RELATIVE BLOCKS (FORGE): TODO BIEN" : "RELATIVE BLOCKS (FORGE): " + fallos + " FALLOS");
         Files.writeString(new File(dir, "informe.txt").toPath(), String.join("\n", LINES) + "\n", StandardCharsets.UTF_8);
      } catch (Exception e) {
         RelativeBlocks.LOGGER.error("Prueba: no se pudo escribir el informe", e);
      }
      mc.stop();
   }
}
