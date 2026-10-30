/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.command;

import net.tierrasfantasticas.tfclient.crates.client.color.FSText;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.JsonCrateLoader;
import net.tierrasfantasticas.tfclient.crates.config.RewardEntry;
import net.tierrasfantasticas.tfclient.crates.crate.CrateRegistry;
import net.tierrasfantasticas.tfclient.crates.crate.LootEngine;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import net.tierrasfantasticas.tfclient.crates.network.FSNetwork;
import net.tierrasfantasticas.tfclient.crates.network.OpenEditorPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class FSCrateCommand {
   private FSCrateCommand() {
   }

   public static LiteralArgumentBuilder<CommandSourceStack> command() {
      LiteralArgumentBuilder<CommandSourceStack> root = (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal("crates").requires(s -> s.hasPermission(4)))
         .executes(FSCrateCommand::help);
      root.then(Commands.literal("help").executes(FSCrateCommand::help));
      root.then(Commands.literal("create").executes(FSCrateCommand::create));
      root.then(Commands.literal("list").executes(FSCrateCommand::list));
      root.then(
         ((LiteralArgumentBuilder)Commands.literal("editor").executes(c -> usage(c, "/tf crates editor give <jugador>")))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("give").executes(c -> usage(c, "/tf crates editor give <jugador>")))
                  .then(Commands.argument("player", EntityArgument.player()).executes(FSCrateCommand::giveEditorWand))
            )
      );
      root.then(
         ((LiteralArgumentBuilder)Commands.literal("give").executes(c -> usage(c, "/tf crates give <jugador> <crate>")))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player()).executes(c -> usage(c, "/tf crates give <jugador> <crate>")))
                  .then(Commands.argument("crate", StringArgumentType.word()).suggests((c, b) -> suggestCrates(c, b)).executes(FSCrateCommand::giveCrate))
            )
      );
      root.then(
         ((LiteralArgumentBuilder)Commands.literal("key").executes(c -> usage(c, "/tf crates key give <jugador> [cantidad]")))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("give").executes(c -> usage(c, "/tf crates key give <jugador> [cantidad]")))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player()).executes(FSCrateCommand::giveKey))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(FSCrateCommand::giveKey))
                  )
            )
      );
      root.then(
         ((LiteralArgumentBuilder)Commands.literal("preview").executes(c -> usage(c, "/tf crates preview <crate>")))
            .then(Commands.argument("crate", StringArgumentType.word()).suggests((c, b) -> suggestCrates(c, b)).executes(FSCrateCommand::preview))
      );
      root.then(
         ((LiteralArgumentBuilder)Commands.literal("delete").executes(c -> usage(c, "/tf crates delete <crate>")))
            .then(Commands.argument("crate", StringArgumentType.word()).suggests((c, b) -> suggestCrates(c, b)).executes(FSCrateCommand::delete))
      );
      root.then(
         ((LiteralArgumentBuilder)Commands.literal("reload").executes(FSCrateCommand::reload))
            .then(Commands.argument("crate", StringArgumentType.word()).suggests((c, b) -> suggestCrates(c, b)).executes(FSCrateCommand::reloadOne))
      );
      return root;
   }

   private static int usage(CommandContext<CommandSourceStack> ctx, String usage) {
      ((CommandSourceStack)ctx.getSource()).sendSystemMessage(Component.literal("§eUso: §f" + usage));
      return 1;
   }

   private static int help(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack s = (CommandSourceStack)ctx.getSource();
      s.sendSystemMessage(Component.literal("§d✦ §fTF Crates §d✦ §7comandos:"));
      s.sendSystemMessage(Component.literal("§e/tf crates create §7- crea y abre el editor de una crate nueva"));
      s.sendSystemMessage(Component.literal("§e/tf crates editor give <jugador> §7- da la §dVarita del Editor §7(click derecho en un cofre para editarlo)"));
      s.sendSystemMessage(Component.literal("§e/tf crates give <jugador> <crate> §7- da el item de la crate"));
      s.sendSystemMessage(Component.literal("§e/tf crates key give <jugador> [cantidad] §7- da la §dFantastic Key §7(llave universal)"));
      s.sendSystemMessage(Component.literal("§e/tf crates preview <crate> §7- simula 5 aperturas"));
      s.sendSystemMessage(Component.literal("§e/tf crates list §7- lista las crates guardadas"));
      s.sendSystemMessage(Component.literal("§e/tf crates delete <crate> §7- elimina una crate"));
      s.sendSystemMessage(Component.literal("§e/tf crates reload §7- recarga las cajas desde §fconfig/fscrates/cajas/*.json"));
      return 1;
   }

   private static CompletableFuture<Suggestions> suggestCrates(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
      try {
         return SharedSuggestionProvider.suggest(CrateRegistry.get(((CommandSourceStack)ctx.getSource()).getLevel()).ids(), builder);
      } catch (Exception var3) {
         return builder.buildFuture();
      }
   }

   private static int create(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = player(ctx);
      if (player == null) {
         return 0;
      } else {
         CrateConfig crate = new CrateConfig("crate_" + System.currentTimeMillis() % 100000L);
         crate.rewards.add(exampleItem("minecraft:diamond", 60, "Diamante"));
         crate.rewards.add(exampleItem("minecraft:emerald", 10, "Esmeralda"));
         JsonCrateLoader.saveToFile(crate);
         FSNetwork.sendToClient(player, new OpenEditorPacket(crate.save()));
         return 1;
      }
   }

   private static RewardEntry exampleItem(String id, int chance, String label) {
      RewardEntry r = new RewardEntry(RewardEntry.Type.ITEM);
      r.chance = (double)chance;
      r.label = label;
      Item item = (Item)ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
      r.item = item == null ? ItemStack.EMPTY : new ItemStack(item);
      return r;
   }

   private static int giveEditorWand(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer target = playerArg(ctx);
      if (target == null) {
         return 0;
      } else {
         ItemStack wand = CrateItems.buildEditorWand();
         if (!target.getInventory().add(wand)) {
            target.drop(wand, false);
         }

         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
               () -> Component.literal(
                     "§dVarita del Editor§a entregada a " + target.getName().getString() + ". §7Click derecho sobre un cofre para editarlo."
                  ),
               true
            );
         return 1;
      }
   }

   private static int giveCrate(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer target = playerArg(ctx);
      if (target == null) {
         return 0;
      } else {
         String id = StringArgumentType.getString(ctx, "crate");
         CrateConfig crate = CrateRegistry.get(target.serverLevel()).get(id);
         if (crate == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("No existe la crate '" + id + "'."));
            return 0;
         } else {
            ItemStack item = CrateItems.buildCrate(crate);
            if (!target.getInventory().add(item)) {
               target.drop(item, false);
            }

            if (crate.uniqueKeyEnabled) {
               ItemStack uniqueKey = CrateItems.buildUniqueKey(crate);
               if (!target.getInventory().add(uniqueKey)) {
                  target.drop(uniqueKey, false);
               }
            }

            ((CommandSourceStack)ctx.getSource())
               .sendSuccess(
                  () -> Component.literal(
                        "§aCrate '" + id + "' entregada a " + target.getName().getString() + (crate.uniqueKeyEnabled ? " §7(+ su llave única)" : "")
                     ),
                  true
               );
            return 1;
         }
      }
   }

   private static int giveKey(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer target = playerArg(ctx);
      if (target == null) {
         return 0;
      } else {
         int amount = 1;

         try {
            amount = IntegerArgumentType.getInteger(ctx, "amount");
         } catch (IllegalArgumentException var6) {
            amount = 1;
         }

         int remaining = Math.max(1, amount);

         while (remaining > 0) {
            int take = Math.min(remaining, 64);
            remaining -= take;
            ItemStack key = CrateItems.buildKey();
            key.setCount(take);
            if (!target.getInventory().add(key)) {
               target.drop(key, false);
            }
         }

         int given = Math.max(1, amount);
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("§d✦ Fantastic Key ✦§a x" + given + " entregada(s) a " + target.getName().getString()), true);
         return 1;
      }
   }

   private static int preview(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = player(ctx);
      if (player == null) {
         return 0;
      } else {
         String id = StringArgumentType.getString(ctx, "crate");
         CrateConfig crate = CrateRegistry.get(player.serverLevel()).get(id);
         if (crate == null) {
            player.sendSystemMessage(Component.literal("§cNo existe la crate '" + id + "'."));
            return 0;
         } else {
            Random random = new Random();
            player.sendSystemMessage(Component.literal("§d✦ Vista previa de " + LootEngine.colorize(FSText.toLegacy(crate.displayName)) + "§r§d:"));

            for (int i = 0; i < 5; i++) {
               List<RewardEntry> rolled = LootEngine.roll(crate, random);
               StringBuilder sb = new StringBuilder("§7- ");

               for (RewardEntry r : rolled) {
                  sb.append("§f").append(r.describe()).append("§7, ");
               }

               player.sendSystemMessage(Component.literal(sb.toString()));
            }

            return 1;
         }
      }
   }

   private static int delete(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = player(ctx);
      if (player == null) {
         return 0;
      } else {
         String id = StringArgumentType.getString(ctx, "crate");
         boolean removed = CrateRegistry.get(player.serverLevel()).remove(id);
         player.sendSystemMessage(Component.literal(removed ? "§aCrate '" + id + "' eliminada." : "§cNo existe la crate '" + id + "'."));
         return removed ? 1 : 0;
      }
   }

   private static int reload(CommandContext<CommandSourceStack> ctx) {
      int loaded = JsonCrateLoader.loadAll(((CommandSourceStack)ctx.getSource()).getServer());
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(() -> Component.literal("§aTF Crates: §fConfiguración recargada desde JSON §7(" + loaded + " caja(s))."), true);
      return 1;
   }

   private static int reloadOne(CommandContext<CommandSourceStack> ctx) {
      String id = StringArgumentType.getString(ctx, "crate").toLowerCase(Locale.ROOT);
      Path file = JsonCrateLoader.fileFor(id);
      if (!Files.isRegularFile(file)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("§cNo existe §f" + file.getFileName() + "§c en config/fscrates/cajas/"));
         return 0;
      } else {
         try {
            CrateConfig config = JsonCrateLoader.read(file);
            if (config == null) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("§cEl archivo no tiene un JSON válido."));
               return 0;
            } else {
               CrateRegistry.get(((CommandSourceStack)ctx.getSource()).getServer().overworld()).put(config);
               CrateRegistry.bumpGeneration();
               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> Component.literal("§aRecargada §f" + config.id + "§a desde JSON §7(" + config.rewards.size() + " recompensa(s))."), true);
               return 1;
            }
         } catch (Exception var4) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("§cError leyendo el archivo: §f" + var4.getMessage()));
            return 0;
         }
      }
   }

   private static int list(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = player(ctx);
      if (player == null) {
         return 0;
      } else {
         Set<String> ids = CrateRegistry.get(player.serverLevel()).ids();
         if (ids.isEmpty()) {
            player.sendSystemMessage(Component.literal("§eNo hay crates. Crea una con §f/tf crates create"));
            return 1;
         } else {
            player.sendSystemMessage(Component.literal("§dCrates (" + ids.size() + "): §f" + String.join(", ", ids)));
            return 1;
         }
      }
   }

   private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
      try {
         return ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      } catch (Exception var2) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Este comando debe ejecutarlo un jugador."));
         return null;
      }
   }

   private static ServerPlayer playerArg(CommandContext<CommandSourceStack> ctx) {
      try {
         return EntityArgument.getPlayer(ctx, "player");
      } catch (Exception var2) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Jugador no encontrado."));
         return null;
      }
   }
}
