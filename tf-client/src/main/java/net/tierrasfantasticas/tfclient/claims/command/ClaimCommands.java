package net.tierrasfantasticas.tfclient.claims.command;

import net.tierrasfantasticas.tfclient.claims.TFClaims;

import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimMenuHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ClaimCommands {
    private static final SuggestionProvider<CommandSourceStack> CLAIMSTONE_IDS = (commandcontext, suggestionsbuilder) -> {
        String[] astring = new String[ClaimTier.VALUES.length];
        for (int i = 0; i < ClaimTier.VALUES.length; ++i) {
            astring[i] = ClaimTier.VALUES[i].id;
        }
        return SharedSuggestionProvider.suggest((String[])astring, (SuggestionsBuilder)suggestionsbuilder);
    };
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (commandcontext, suggestionsbuilder) -> SharedSuggestionProvider.suggest((Iterable)((CommandSourceStack)commandcontext.getSource()).getOnlinePlayerNames(), (SuggestionsBuilder)suggestionsbuilder);
    private static final SuggestionProvider<CommandSourceStack> MEMBER_NAMES = (commandcontext, suggestionsbuilder) -> {
        Claim claim;
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayer();
        return serverplayer != null && (claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition())) != null ? SharedSuggestionProvider.suggest(claim.getMemberNames(), (SuggestionsBuilder)suggestionsbuilder) : suggestionsbuilder.buildFuture();
    };

    /** /tf claims ...: lo de los jugadores (y lo de los operadores sobre una zona). */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"claims").then(TFClaims.mergeCommand()).executes(ClaimCommands::root)).then(Commands.literal((String)"help").executes(ClaimCommands::help))).then(Commands.literal((String)"menu").executes(ClaimCommands::menu))).then(Commands.literal((String)"info").executes(ClaimCommands::info))).then(Commands.literal((String)"list").executes(ClaimCommands::list))).then(Commands.literal((String)"remove").executes(ClaimCommands::remove))).then(((LiteralArgumentBuilder)Commands.literal((String)"ban").requires(commandsourcestack -> commandsourcestack.hasPermission(2))).then(Commands.argument((String)"jugador", (ArgumentType)EntityArgument.player()).executes(ClaimCommands::ban)))).then(((LiteralArgumentBuilder)Commands.literal((String)"unban").requires(commandsourcestack -> commandsourcestack.hasPermission(2))).then(Commands.argument((String)"jugador", (ArgumentType)EntityArgument.player()).executes(ClaimCommands::unban)))).then(((LiteralArgumentBuilder)Commands.literal((String)"transfer").requires(commandsourcestack -> commandsourcestack.hasPermission(2))).then(Commands.argument((String)"jugador", (ArgumentType)EntityArgument.player()).executes(ClaimCommands::transfer)))).then(((LiteralArgumentBuilder)Commands.literal((String)"removemember").requires(commandsourcestack -> commandsourcestack.hasPermission(2))).then(Commands.argument((String)"jugador", (ArgumentType)EntityArgument.player()).executes(ClaimCommands::removeMember))).then(Commands.literal((String)"addmember").then(Commands.argument((String)"jugador", (ArgumentType)StringArgumentType.word()).suggests(ONLINE_PLAYERS).executes(ClaimCommands::addMember))).then(Commands.literal((String)"delmember").then(Commands.argument((String)"jugador", (ArgumentType)StringArgumentType.word()).suggests(MEMBER_NAMES).executes(ClaimCommands::delMember))).then(Commands.literal((String)"members").executes(ClaimCommands::members))).then(((LiteralArgumentBuilder)Commands.literal((String)"give").requires(commandsourcestack -> commandsourcestack.hasPermission(2))).then(Commands.argument((String)"jugador", (ArgumentType)EntityArgument.players()).then(Commands.argument((String)"id", (ArgumentType)StringArgumentType.word()).suggests(CLAIMSTONE_IDS).executes(ClaimCommands::give))))).then(((LiteralArgumentBuilder)Commands.literal((String)"clear").requires(commandsourcestack -> commandsourcestack.hasPermission(2))).then(Commands.argument((String)"jugador", (ArgumentType)EntityArgument.player()).executes(ClaimCommands::clear))));
    }

    private static int help(CommandContext<CommandSourceStack> commandcontext) {
        boolean flag = ((CommandSourceStack)commandcontext.getSource()).hasPermission(2);
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> {
            MutableComponent mutablecomponent = Component.literal((String)"=== TF Claims ===\n").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD}).append((Component)Component.literal((String)"/tf claims menu  ").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"- abre el menu de la zona\n").withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)"/tf claims info  ").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"- info de la zona\n").withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)"/tf claims list  ").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"- lista tus zonas\n").withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)"/tf claims remove  ").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"- borra tu zona actual\n").withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)"/tf claims addmember <jugador>  ").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"- a\u00f1ade un miembro (aunque est\u00e9 offline)\n").withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)"/tf claims delmember <jugador>  ").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"- quita un miembro\n").withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)"/tf claims members  ").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"- lista los miembros de la zona\n").withStyle(ChatFormatting.GRAY));
            if (flag) {
                mutablecomponent.append((Component)Component.literal((String)"\n--- Solo Operadores ---\n").withStyle(ChatFormatting.RED)).append((Component)Component.literal((String)"/tf claims give <jugador> <tier>\n").withStyle(ChatFormatting.YELLOW)).append((Component)Component.literal((String)"/tf claims clear <jugador>\n").withStyle(ChatFormatting.YELLOW)).append((Component)Component.literal((String)"/tf claims ban|unban <jugador>\n").withStyle(ChatFormatting.YELLOW)).append((Component)Component.literal((String)"/tf claims transfer <jugador>\n").withStyle(ChatFormatting.YELLOW)).append((Component)Component.literal((String)"/tf claims removemember <jugador>\n").withStyle(ChatFormatting.YELLOW)).append((Component)Component.literal((String)"/tf web claims  (panel, bypass, list, stats, globalflag)\n").withStyle(ChatFormatting.YELLOW)).append((Component)Component.literal((String)"/tf web claims reload  - recarga la config").withStyle(ChatFormatting.YELLOW));
            }
            return mutablecomponent;
        }, false);
        return 1;
    }

    /** /tf claims: con el TF Client abre la app Protección del pad (tus zonas); si no, la ayuda. */
    private static int root(CommandContext<CommandSourceStack> commandcontext) {
        if (commandcontext.getSource().getEntity() instanceof ServerPlayer player
                && net.tierrasfantasticas.tfclient.pad.TFPadNet.openApp(player, "protecciones", "")) return 1;
        return ClaimCommands.help(commandcontext);
    }

    private static int menu(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No est\u00e1s en ninguna zona protegida.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!claim.isOwner((Player)serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] Solo el due\u00f1o puede abrir el menu.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ClaimMenuHandler.open(serverplayer, claim, 0);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No est\u00e1s en ninguna zona protegida.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)("=== Zona " + claim.sizeLabel() + " ===\n")).withStyle(ChatFormatting.YELLOW).append((Component)Component.literal((String)("Due\u00f1o: " + claim.getOwnerName() + "\n")).withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)("Centro: X=" + claim.getX() + " Y=" + claim.getY() + " Z=" + claim.getZ() + "\n")).withStyle(ChatFormatting.GRAY)).append((Component)Component.literal((String)("Miembros: " + claim.getMembers().size())).withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        List<Claim> list = ClaimManager.getInstance().getClaimsOf(serverplayer.getUUID());
        if (list.isEmpty()) {
            ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)"[i] No tienes zonas.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> {
            MutableComponent mutablecomponent = Component.literal((String)("=== Tus zonas (" + list.size() + ") ===\n")).withStyle(ChatFormatting.YELLOW);
            for (Claim claim : list) {
                mutablecomponent.append((Component)Component.literal((String)("- " + claim.sizeLabel() + " en X=" + claim.getX() + " Z=" + claim.getZ() + " (" + claim.getWorld() + ")\n")).withStyle(ChatFormatting.GRAY));
            }
            return mutablecomponent;
        }, false);
        return list.size();
    }

    private static int remove(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No est\u00e1s en ninguna zona protegida.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!claim.isOwner((Player)serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] Solo el due\u00f1o puede eliminar esta zona.").withStyle(ChatFormatting.RED));
            return 0;
        }
        BlockPos blockpos = claim.getCenter();
        ClaimTier claimtier = claim.getTier();
        if (claimtier != null && ClaimBlocks.isClaimConcreteForTier(serverplayer.level().getBlockState(blockpos).getBlock(), claimtier)) {
            serverplayer.level().destroyBlock(blockpos, false);
        }
        ClaimManager.getInstance().removeClaim(serverplayer.level(), blockpos);
        serverplayer.level().playSound(null, blockpos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0f, 1.0f);
        if (claimtier != null) {
            ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
            if (!serverplayer.getInventory().add(itemstack)) {
                serverplayer.drop(itemstack, false);
            }
        }
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)"\u2714 Zona eliminada. Protecci\u00f3n devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int ban(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        ServerPlayer serverplayer1 = EntityArgument.getPlayer(commandcontext, (String)"jugador");
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No est\u00e1s en ninguna zona.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!claim.isOwner((Player)serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] Solo el due\u00f1o puede banear.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (serverplayer1.hasPermissions(2) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No puedes banear a un operador.").withStyle(ChatFormatting.RED));
            return 0;
        }
        claim.banPlayer(serverplayer1.getUUID());
        ClaimManager.getInstance().save();
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)("\u2714 " + serverplayer1.getName().getString() + " baneado.")).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int unban(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        ServerPlayer serverplayer1 = EntityArgument.getPlayer(commandcontext, (String)"jugador");
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No est\u00e1s en ninguna zona.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!claim.isOwner((Player)serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] Solo el due\u00f1o puede desbanear.").withStyle(ChatFormatting.RED));
            return 0;
        }
        claim.unbanPlayer(serverplayer1.getUUID());
        ClaimManager.getInstance().save();
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)("\u2714 " + serverplayer1.getName().getString() + " desbaneado.")).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int transfer(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        ServerPlayer serverplayer1 = EntityArgument.getPlayer(commandcontext, (String)"jugador");
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No est\u00e1s en ninguna zona.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!claim.isOwner((Player)serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] Solo el due\u00f1o puede transferir.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (serverplayer1.hasPermissions(2) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No puedes transferir a un operador.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (claim.isOwner(serverplayer1.getUUID())) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] Ya es el due\u00f1o actual.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ClaimManager.getInstance().transferOwnership(claim, serverplayer1.getUUID(), serverplayer1.getName().getString());
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)("\u2714 Zona transferida a " + serverplayer1.getName().getString())).withStyle(ChatFormatting.GREEN), true);
        serverplayer1.displayClientMessage((Component)Component.literal((String)("[Protecci\u00f3n] Has recibido la propiedad de una zona en X=" + claim.getX() + " Z=" + claim.getZ())).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static Claim ownedClaimAt(CommandSourceStack commandsourcestack, ServerPlayer serverplayer) {
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            commandsourcestack.sendFailure((Component)Component.literal((String)"[x] No est\u00e1s en ninguna zona protegida.").withStyle(ChatFormatting.RED));
            return null;
        }
        if (!claim.isOwner((Player)serverplayer) && !serverplayer.hasPermissions(2)) {
            commandsourcestack.sendFailure((Component)Component.literal((String)"[x] Solo el due\u00f1o puede gestionar los miembros de esta zona.").withStyle(ChatFormatting.RED));
            return null;
        }
        return claim;
    }

    private static int addMember(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimCommands.ownedClaimAt((CommandSourceStack)commandcontext.getSource(), serverplayer);
        if (claim == null) {
            return 0;
        }
        String s = StringArgumentType.getString(commandcontext, (String)"jugador");
        return ClaimMenuHandler.addMemberByName(serverplayer, claim, s, 0, false) ? 1 : 0;
    }

    private static int delMember(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimCommands.ownedClaimAt((CommandSourceStack)commandcontext.getSource(), serverplayer);
        if (claim == null) {
            return 0;
        }
        String s = StringArgumentType.getString(commandcontext, (String)"jugador");
        return ClaimMenuHandler.removeMemberByName(serverplayer, claim, s, 0, false) ? 1 : 0;
    }

    private static int members(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimCommands.ownedClaimAt((CommandSourceStack)commandcontext.getSource(), serverplayer);
        if (claim == null) {
            return 0;
        }
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> {
            MutableComponent mutablecomponent = Component.literal((String)("=== Miembros de la zona de " + claim.getOwnerName() + " (" + claim.getMembers().size() + ") ===\n")).withStyle(ChatFormatting.YELLOW);
            if (claim.getMembers().isEmpty()) {
                mutablecomponent.append((Component)Component.literal((String)"(sin miembros)").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (int i = 0; i < claim.getMembers().size(); ++i) {
                    String s = i < claim.getMemberNames().size() ? claim.getMemberNames().get(i) : claim.getMembers().get(i).toString();
                    mutablecomponent.append((Component)Component.literal((String)("- " + s + "\n")).withStyle(ChatFormatting.WHITE));
                }
            }
            return mutablecomponent;
        }, false);
        return claim.getMembers().size();
    }

    private static int removeMember(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        ServerPlayer serverplayer1 = EntityArgument.getPlayer(commandcontext, (String)"jugador");
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No est\u00e1s en ninguna zona.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!claim.isOwner((Player)serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] Solo el due\u00f1o puede gestionar miembros.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (serverplayer1.hasPermissions(2) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)"[x] No puedes gestionar a un operador.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!claim.isMember(serverplayer1.getUUID())) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)("[x] " + serverplayer1.getName().getString() + " no es miembro.")).withStyle(ChatFormatting.RED));
            return 0;
        }
        claim.removeMember(serverplayer1.getUUID());
        ClaimManager.getInstance().save();
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)("\u2714 " + serverplayer1.getName().getString() + " eliminado de la zona.")).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        String s = StringArgumentType.getString(commandcontext, (String)"id");
        ClaimTier claimtier = ClaimTier.byId(s);
        if (claimtier == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure((Component)Component.literal((String)("[x] ID no v\u00e1lido: " + s)).withStyle(ChatFormatting.RED));
            return 0;
        }
        Collection<ServerPlayer> collection = EntityArgument.getPlayers(commandcontext, (String)"jugador");
        for (ServerPlayer serverplayer : collection) {
            ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
            if (!serverplayer.getInventory().add(itemstack)) {
                serverplayer.drop(itemstack, false);
            }
            serverplayer.displayClientMessage((Component)Component.literal((String)("[+] Recibiste Protecci\u00f3n " + claimtier.label())).withStyle(ChatFormatting.GREEN), false);
        }
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)("\u2714 Protecci\u00f3n " + claimtier.label() + " entregada a " + collection.size() + " jugador(es).")).withStyle(ChatFormatting.GREEN), true);
        return collection.size();
    }

    private static int clear(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = EntityArgument.getPlayer(commandcontext, (String)"jugador");
        int i = ClaimManager.getInstance().clearClaimsOf(serverplayer.getUUID());
        ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal((String)("\u2714 Eliminadas " + i + " zona(s) de " + serverplayer.getName().getString())).withStyle(ChatFormatting.GREEN), true);
        return i;
    }
}

