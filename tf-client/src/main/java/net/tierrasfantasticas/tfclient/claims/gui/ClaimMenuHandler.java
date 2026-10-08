package net.tierrasfantasticas.tfclient.claims.gui;

import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.chat.ChatPromptRouter;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags;
import net.tierrasfantasticas.tfclient.claims.data.ClaimGroup;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimParticleMenuHandler;
import net.tierrasfantasticas.tfclient.claims.gui.MemberSelectMenu;
import net.tierrasfantasticas.tfclient.claims.util.PlayerLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.network.NetworkHooks;

public class ClaimMenuHandler
extends ChestMenu {
    public static final int SIZE = 54;
    private static final int[] FLAG_SLOTS_P0 = new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31};
    private static final int[] FLAG_SLOTS_P1 = new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31, 32, 33, 34, 35, 27};
    private static final ClaimFlags.FlagId[] PAGE_0 = new ClaimFlags.FlagId[]{ClaimFlags.FlagId.BUILDING, ClaimFlags.FlagId.BREAKING, ClaimFlags.FlagId.EXPLOSIONS, ClaimFlags.FlagId.FIRE, ClaimFlags.FlagId.MOB_SPAWN, ClaimFlags.FlagId.PVP, ClaimFlags.FlagId.MOB_DAMAGE, ClaimFlags.FlagId.ALERTS, ClaimFlags.FlagId.PUBLIC_MODE, ClaimFlags.FlagId.ANIMAL_KILLING, ClaimFlags.FlagId.CHEST_ACCESS, ClaimFlags.FlagId.CROP_HARVEST, ClaimFlags.FlagId.BURN_HOSTILES};
    private static final ClaimFlags.FlagId[] PAGE_1 = new ClaimFlags.FlagId[]{ClaimFlags.FlagId.ITEM_USE, ClaimFlags.FlagId.ENTITY_INTERACT, ClaimFlags.FlagId.TRAMPLING, ClaimFlags.FlagId.FLUIDS, ClaimFlags.FlagId.PVP_ALL, ClaimFlags.FlagId.TREE_CHOPPING, ClaimFlags.FlagId.SHOW_WELCOME, ClaimFlags.FlagId.ANVIL_USE, ClaimFlags.FlagId.ENDER_PEARL, ClaimFlags.FlagId.SIGN_EDITING, ClaimFlags.FlagId.DOORS_ACCESS, ClaimFlags.FlagId.EFFECT_REGEN, ClaimFlags.FlagId.EFFECT_RESIST, ClaimFlags.FlagId.EFFECT_SPEED, ClaimFlags.FlagId.ALLOW_FLIGHT, ClaimFlags.FlagId.SHOW_LEAVE, ClaimFlags.FlagId.SHOW_BORDER, ClaimFlags.FlagId.SHOW_PARTICLES};
    private static final int[] FLAG_SLOTS_P2 = new int[]{20, 22, 24};
    private static final ClaimFlags.FlagId[] PAGE_2 = new ClaimFlags.FlagId[]{ClaimFlags.FlagId.ALL_MOB_SPAWN, ClaimFlags.FlagId.PASSIVE_MOB_SPAWN, ClaimFlags.FlagId.BLOCK_ALL_INTERACT};
    private static final ClaimFlags.FlagId[][] PAGES = new ClaimFlags.FlagId[][]{PAGE_0, PAGE_1, PAGE_2};
    private static final int[][] PAGE_SLOTS = new int[][]{FLAG_SLOTS_P0, FLAG_SLOTS_P1, FLAG_SLOTS_P2};
    private static final int LAST_PAGE = PAGES.length - 1;
    private static final long PROMPT_TTL_MS = 90000L;
    private static final Map<UUID, PendingChat> pending = new ConcurrentHashMap<UUID, PendingChat>();
    private static final Map<UUID, String> pendingMergeName = new ConcurrentHashMap<UUID, String>();
    private static final Map<String, MergeInvite> invites = new ConcurrentHashMap<String, MergeInvite>();
    private final SimpleContainer chest;
    private final Claim claim;
    private final ServerPlayer viewer;
    private final int page;
    private boolean awaitingDeleteConfirm = false;

    public ClaimMenuHandler(int i, Inventory inventory, Claim claim, int j) {
        this(i, inventory, new SimpleContainer(54), claim, j);
    }

    private ClaimMenuHandler(int i, Inventory inventory, SimpleContainer simplecontainer, Claim claim, int j) {
        super(MenuType.GENERIC_9x6, i, inventory, (Container)simplecontainer, 6);
        this.chest = simplecontainer;
        this.claim = claim;
        this.viewer = (ServerPlayer)inventory.player;
        this.page = j;
        this.rebuild();
    }

    public Claim getClaim() {
        return this.claim;
    }

    public int getPage() {
        return this.page;
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    private void rebuild() {
        ClaimGroup claimgroup;
        ItemStack itemstack = ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.GRAY_STAINED_GLASS_PANE), (Component)Component.literal((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.chest.setItem(i, itemstack.copy());
        }
        ClaimGroup claimgroup1 = ClaimManager.getInstance().getGroupOf(this.claim);
        String s = claimgroup1 != null ? "Grupo: " + claimgroup1.getName() : "Zona " + this.claim.sizeLabel() + " - " + this.claim.getOwnerName();
        this.chest.setItem(4, ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.PAPER), (Component)Component.literal((String)ClaimMenuHandler.truncate(s, 30)).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})));
        this.chest.setItem(11, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.COMPASS), (Component)Component.literal((String)"Coordenadas").withStyle(ChatFormatting.AQUA)), List.of(Component.literal((String)("X=" + this.claim.getX() + " Y=" + this.claim.getY() + " Z=" + this.claim.getZ())).withStyle(ChatFormatting.WHITE))));
        this.chest.setItem(13, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.PLAYER_HEAD), (Component)Component.literal((String)"Due\u00f1o").withStyle(ChatFormatting.AQUA)), List.of(Component.literal((String)ClaimMenuHandler.truncate(this.claim.getOwnerName(), 35)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}))));
        this.chest.setItem(15, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.DIAMOND), (Component)Component.literal((String)("Zona " + this.claim.sizeLabel())).withStyle(ChatFormatting.YELLOW)), List.of(Component.literal((String)("Zona " + this.claim.sizeLabel() + " bloques")).withStyle(ChatFormatting.GRAY), Component.literal((String)("Hasta el cielo \u00b7 " + this.claim.getHeight() + " bajo la piedra")).withStyle(ChatFormatting.GRAY))));
        this.chest.setItem(17, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.MAP), (Component)Component.literal((String)"Mundo").withStyle(ChatFormatting.AQUA)), List.of(Component.literal((String)ClaimMenuHandler.truncate(this.claim.getWorld(), 35)).withStyle(ChatFormatting.GRAY))));
        ClaimFlags claimflags = this.claim.getFlags();
        ClaimFlags.FlagId[] aclaimflags$flagid = PAGES[this.pageIndex()];
        int[] aint = PAGE_SLOTS[this.pageIndex()];
        int j = ClaimMenuHandler.paidLevelOf(this.claim.getTier());
        for (int k = 0; k < aclaimflags$flagid.length; ++k) {
            ClaimFlags.FlagId claimflags$flagid = aclaimflags$flagid[k];
            int l = ClaimMenuHandler.requiredPaidLevel(claimflags$flagid);
            if (l > 0 && j < l) {
                this.chest.setItem(aint[k], this.lockedEffectButton(claimflags$flagid, l));
                continue;
            }
            this.chest.setItem(aint[k], this.flagButton(claimflags$flagid, claimflags.get(claimflags$flagid)));
        }
        this.chest.setItem(38, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.WRITABLE_BOOK), (Component)Component.literal((String)("Miembros (" + this.claim.getMembers().size() + ")")).withStyle(ChatFormatting.YELLOW)), this.buildMemberLore()));
        this.chest.setItem(40, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.NAME_TAG), (Component)Component.literal((String)"Quitar miembro").withStyle(ChatFormatting.RED)), List.of(Component.literal((String)"Pide nombre por chat").withStyle(ChatFormatting.GRAY), Component.literal((String)"Clic para eliminar a un invitado").withStyle(ChatFormatting.GRAY))));
        this.chest.setItem(42, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.PLAYER_HEAD), (Component)Component.literal((String)"A\u00f1adir miembro").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})), List.of(Component.literal((String)"Clic izq: elegir de una lista").withStyle(ChatFormatting.GRAY), Component.literal((String)"Clic der: escribir el nombre por chat").withStyle(ChatFormatting.GRAY), Component.literal((String)"Tambi\u00e9n sirve /tf claims addmember <jugador>").withStyle(ChatFormatting.DARK_GRAY))));
        this.chest.setItem(39, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.IRON_BARS), (Component)Component.literal((String)"Banear jugador").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), this.buildBanLore()));
        this.chest.setItem(41, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.TRIPWIRE_HOOK), (Component)Component.literal((String)"Desbanear jugador").withStyle(ChatFormatting.GREEN)), List.of(Component.literal((String)"Pide nombre por chat").withStyle(ChatFormatting.GRAY), Component.literal((String)"Clic para quitar del baneo").withStyle(ChatFormatting.GRAY))));
        if (this.page > 0) {
            this.chest.setItem(45, ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.ARROW), (Component)Component.literal((String)"<< P\u00e1gina anterior").withStyle(ChatFormatting.AQUA)));
        }
        if (this.awaitingDeleteConfirm) {
            this.chest.setItem(46, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.TNT), (Component)Component.literal((String)"Confirmar eliminaci\u00f3n").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.literal((String)"Haz clic de nuevo para confirmar").withStyle(ChatFormatting.YELLOW))));
            this.chest.setItem(47, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.LIME_DYE), (Component)Component.literal((String)"Cancelar").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})), List.of(Component.literal((String)"Cancela la eliminaci\u00f3n").withStyle(ChatFormatting.GRAY))));
        } else {
            this.chest.setItem(46, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.BARRIER), (Component)Component.literal((String)"Eliminar zona").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.literal((String)"Clic para iniciar eliminaci\u00f3n").withStyle(ChatFormatting.YELLOW), Component.literal((String)"Devuelve la protecci\u00f3n al inv.").withStyle(ChatFormatting.GRAY))));
        }
        this.chest.setItem(49, ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.RED_DYE), (Component)Component.literal((String)"Cerrar").withStyle(ChatFormatting.WHITE)));
        this.chest.setItem(52, ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.BOOK), (Component)Component.literal((String)"Ver lista de zonas").withStyle(ChatFormatting.AQUA)));
        if (this.page < LAST_PAGE) {
            this.chest.setItem(53, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.ARROW), (Component)Component.literal((String)"P\u00e1gina siguiente >>").withStyle(ChatFormatting.AQUA)), List.of(Component.literal((String)("P\u00e1gina " + (this.page + 1) + " de " + (LAST_PAGE + 1))).withStyle(ChatFormatting.DARK_GRAY))));
        }
        if ((claimgroup = ClaimManager.getInstance().getGroupOf(this.claim)) == null) {
            this.chest.setItem(43, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.SLIME_BALL), (Component)Component.literal((String)"Unir protecci\u00f3n").withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD})), List.of(Component.literal((String)"Crea un grupo y une zonas de tu equipo").withStyle(ChatFormatting.GRAY), Component.literal((String)"Clic: elegir nombre e invitar jugadores").withStyle(ChatFormatting.GRAY))));
        } else {
            this.chest.setItem(43, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.SLIME_BALL), (Component)Component.literal((String)ClaimMenuHandler.truncate("Grupo: " + claimgroup.getName(), 30)).withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD})), List.of(Component.literal((String)("Miembros registrados: " + claimgroup.getRegisteredPlayers().size())).withStyle(ChatFormatting.GRAY), Component.literal((String)"Clic: invitar mas jugadores").withStyle(ChatFormatting.GRAY))));
            this.chest.setItem(44, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.SHEARS), (Component)Component.literal((String)"Disolver grupo").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.literal((String)"Separa todas las piedras del grupo").withStyle(ChatFormatting.GRAY), Component.literal((String)"Cada zona vuelve a ser independiente").withStyle(ChatFormatting.GRAY))));
        }
        this.broadcastChanges();
    }

    private List<Component> buildMemberLore() {
        ArrayList<Component> arraylist = new ArrayList<Component>();
        if (this.claim.getMembers().isEmpty()) {
            arraylist.add((Component)Component.literal((String)"(sin miembros)").withStyle(ChatFormatting.DARK_GRAY));
            return arraylist;
        }
        int i = Math.min(5, this.claim.getMembers().size());
        for (int j = 0; j < i; ++j) {
            String s = j < this.claim.getMemberNames().size() ? this.claim.getMemberNames().get(j) : this.claim.getMembers().get(j).toString();
            arraylist.add((Component)Component.literal((String)ClaimMenuHandler.truncate(" - " + s, 35)).withStyle(ChatFormatting.WHITE));
        }
        if (this.claim.getMembers().size() > i) {
            arraylist.add((Component)Component.literal((String)(" - ... y " + (this.claim.getMembers().size() - i) + " m\u00e1s")).withStyle(ChatFormatting.GRAY));
        }
        return arraylist;
    }

    private List<Component> buildBanLore() {
        ArrayList<Component> arraylist = new ArrayList<Component>();
        arraylist.add((Component)Component.literal((String)"Escribe el nombre por chat para banear.").withStyle(ChatFormatting.GRAY));
        arraylist.add((Component)Component.literal((String)"Si entran, la barrera los saca de la zona.").withStyle(ChatFormatting.DARK_GRAY));
        Set<UUID> set = this.claim.getBannedPlayers();
        arraylist.add((Component)Component.literal((String)("Baneados: " + set.size())).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
        int i = 0;
        for (UUID uuid : set) {
            if (i++ >= 8) {
                arraylist.add((Component)Component.literal((String)" - ...").withStyle(ChatFormatting.GRAY));
                break;
            }
            arraylist.add((Component)Component.literal((String)ClaimMenuHandler.truncate(" - " + PlayerLookup.nameOf(this.viewer.getServer(), uuid), 35)).withStyle(ChatFormatting.WHITE));
        }
        return arraylist;
    }

    public static void requestBanPlayer(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.BAN_PLAYER, claim.getClaimId(), i));
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Escribe el nombre del jugador a BANEAR (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
    }

    public static void requestUnbanPlayer(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.UNBAN_PLAYER, claim.getClaimId(), i));
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Escribe el nombre del jugador a DESBANEAR (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
    }

    private static void handleBanPlayer(ServerPlayer serverplayer, Claim claim, String s, int i) {
        String s1 = ChatPromptRouter.extractPlayerName(s);
        UUID uuid = claim.getClaimId();
        PlayerLookup.resolveAsync(serverplayer.getServer(), s1, playerlookup$resolved -> {
            if (!serverplayer.hasDisconnected()) {
                Claim claim1 = ClaimMenuHandler.findClaimById(uuid);
                if (claim1 == null) {
                    serverplayer.displayClientMessage((Component)Component.literal((String)"[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                } else if (playerlookup$resolved == null) {
                    serverplayer.displayClientMessage((Component)Component.literal((String)("[x] Jugador no encontrado: " + s1)).withStyle(ChatFormatting.RED), false);
                    ClaimMenuHandler.open(serverplayer, claim1, i);
                } else if (claim1.isOwner(playerlookup$resolved.id())) {
                    serverplayer.displayClientMessage((Component)Component.literal((String)"[x] No puedes banear al due\u00f1o.").withStyle(ChatFormatting.RED), false);
                    ClaimMenuHandler.open(serverplayer, claim1, i);
                } else {
                    claim1.banPlayer(playerlookup$resolved.id());
                    ClaimManager.getInstance().save();
                    serverplayer.displayClientMessage((Component)Component.literal((String)("\u2714 " + playerlookup$resolved.name() + " baneado de la zona.")).withStyle(ChatFormatting.GREEN), false);
                    MutableComponent mutablecomponent = Component.literal((String)("[!] Has sido baneado de una zona de " + serverplayer.getName().getString())).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD});
                    if (playerlookup$resolved.isOnline()) {
                        playerlookup$resolved.online().displayClientMessage((Component)mutablecomponent, false);
                    } else {
                        ClaimManager.getInstance().queueMessage(playerlookup$resolved.id(), (Component)mutablecomponent);
                    }
                    ClaimMenuHandler.open(serverplayer, claim1, i);
                }
            }
        });
    }

    private static void handleUnbanPlayer(ServerPlayer serverplayer, Claim claim, String s, int i) {
        String s1 = ChatPromptRouter.extractPlayerName(s);
        UUID uuid = claim.getClaimId();
        PlayerLookup.resolveAsync(serverplayer.getServer(), s1, playerlookup$resolved -> {
            if (!serverplayer.hasDisconnected()) {
                Claim claim1 = ClaimMenuHandler.findClaimById(uuid);
                if (claim1 == null) {
                    serverplayer.displayClientMessage((Component)Component.literal((String)"[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                } else {
                    if (playerlookup$resolved != null && claim1.isBanned(playerlookup$resolved.id())) {
                        claim1.unbanPlayer(playerlookup$resolved.id());
                        ClaimManager.getInstance().save();
                        serverplayer.displayClientMessage((Component)Component.literal((String)("\u2714 " + playerlookup$resolved.name() + " desbaneado.")).withStyle(ChatFormatting.GREEN), false);
                    } else {
                        serverplayer.displayClientMessage((Component)Component.literal((String)"[x] Ese jugador no est\u00e1 baneado.").withStyle(ChatFormatting.RED), false);
                    }
                    ClaimMenuHandler.open(serverplayer, claim1, i);
                }
            }
        });
    }

    private static int paidLevelOf(ClaimTier claimtier) {
        String s2;
        if (claimtier == null) {
            return 0;
        }
        String s1 = claimtier.id;
        String s = claimtier.id;
        return switch (s2 = claimtier.id) {
            case "claimstone_250x250" -> 1;
            case "claimstone_300x300" -> 2;
            case "claimstone_500x500" -> 3;
            default -> 0;
        };
    }

    private static int requiredPaidLevel(ClaimFlags.FlagId claimflags$flagid) {
        return switch (claimflags$flagid) {
            case EFFECT_REGEN -> 1;
            case EFFECT_RESIST -> 2;
            case EFFECT_SPEED -> 2;
            case ALLOW_FLIGHT -> 3;
            default -> 0;
        };
    }

    private static String requiredTierLabel(int i) {
        return switch (i) {
            case 1 -> "250x250";
            case 2 -> "300x300";
            case 3 -> "500x500";
            default -> "?";
        };
    }

    private ItemStack lockedEffectButton(ClaimFlags.FlagId claimflags$flagid, int i) {
        ItemStack itemstack = new ItemStack((ItemLike)Items.BLACK_STAINED_GLASS_PANE);
        return ClaimMenuHandler.withLore(ClaimMenuHandler.withName(itemstack, (Component)Component.literal((String)(ClaimMenuHandler.effectName(claimflags$flagid) + " [LOCKED]")).withStyle(ChatFormatting.DARK_GRAY)), List.of(Component.literal((String)("Requiere zona " + ClaimMenuHandler.requiredTierLabel(i) + " o superior")).withStyle(ChatFormatting.GRAY), Component.literal((String)ClaimMenuHandler.effectShortDesc(claimflags$flagid)).withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static String effectShortDesc(ClaimFlags.FlagId claimflags$flagid) {
        return switch (claimflags$flagid) {
            case EFFECT_REGEN -> "Regenera vida a duenio y miembros";
            case EFFECT_RESIST -> "Reduce dano a duenio y miembros";
            case EFFECT_SPEED -> "Da velocidad a duenio y miembros";
            case ALLOW_FLIGHT -> "El duenio y los miembros pueden volar en la zona";
            default -> "Perk pasivo";
        };
    }

    private static String effectName(ClaimFlags.FlagId claimflags$flagid) {
        return switch (claimflags$flagid) {
            case EFFECT_REGEN -> "Regeneraci\u00f3n pasiva";
            case EFFECT_RESIST -> "Resistencia pasiva";
            case EFFECT_SPEED -> "Velocidad pasiva";
            case ALLOW_FLIGHT -> "Vuelo en zona";
            default -> "Perk pasivo";
        };
    }

    private ItemStack flagButton(ClaimFlags.FlagId claimflags$flagid, boolean flag) {
        ItemStack itemstack = new ItemStack((ItemLike)(flag ? Items.LIME_DYE : Items.GRAY_DYE));
        MutableComponent mutablecomponent = Component.literal((String)ClaimMenuHandler.flagDisplayName(claimflags$flagid, flag)).withStyle(new ChatFormatting[]{flag ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD});
        String[] astring = ClaimMenuHandler.flagLore(claimflags$flagid);
        return ClaimMenuHandler.withLore(ClaimMenuHandler.withName(itemstack, (Component)mutablecomponent), List.of(Component.literal((String)astring[0]).withStyle(ChatFormatting.GRAY), Component.literal((String)("Estado: " + (flag ? "ACTIVO" : "INACTIVO") + " - " + astring[1])).withStyle(ChatFormatting.GRAY)));
    }

    private static String flagDisplayName(ClaimFlags.FlagId claimflags$flagid, boolean flag) {
        return switch (claimflags$flagid) {
            default -> throw new IncompatibleClassChangeError();
            case EFFECT_REGEN -> {
                if (flag) {
                    yield "Regeneraci\u00f3n pasiva [ON]";
                }
                yield "Regeneraci\u00f3n pasiva [OFF]";
            }
            case EFFECT_RESIST -> {
                if (flag) {
                    yield "Resistencia pasiva [ON]";
                }
                yield "Resistencia pasiva [OFF]";
            }
            case EFFECT_SPEED -> {
                if (flag) {
                    yield "Velocidad pasiva [ON]";
                }
                yield "Velocidad pasiva [OFF]";
            }
            case ALLOW_FLIGHT -> {
                if (flag) {
                    yield "Vuelo en zona: ACTIVO [ON]";
                }
                yield "Vuelo en zona: inactivo [OFF]";
            }
            case BUILDING -> {
                if (flag) {
                    yield "Construir: BLOQUEADO [ON]";
                }
                yield "Construir: permitido [OFF]";
            }
            case BREAKING -> {
                if (flag) {
                    yield "Romper: BLOQUEADO [ON]";
                }
                yield "Romper: permitido [OFF]";
            }
            case EXPLOSIONS -> {
                if (flag) {
                    yield "Explosiones: BLOQUEADAS [ON]";
                }
                yield "Explosiones: permitidas [OFF]";
            }
            case FIRE -> {
                if (flag) {
                    yield "Fuego: BLOQUEADO [ON]";
                }
                yield "Fuego: permitido [OFF]";
            }
            case MOB_SPAWN -> {
                if (flag) {
                    yield "Mobs hostiles: BLOQUEADOS [ON]";
                }
                yield "Mobs hostiles: permit. [OFF]";
            }
            case PVP -> {
                if (flag) {
                    yield "PVP: BLOQUEADO [ON]";
                }
                yield "PVP: permitido [OFF]";
            }
            case MOB_DAMAGE -> {
                if (flag) {
                    yield "Da\u00f1o de mobs: BLOQUEADO [ON]";
                }
                yield "Da\u00f1o de mobs: permit. [OFF]";
            }
            case ALERTS -> {
                if (flag) {
                    yield "Alertas intrusos: ON [ON]";
                }
                yield "Alertas intrusos: OFF [OFF]";
            }
            case ITEM_USE -> {
                if (flag) {
                    yield "Usar items: BLOQUEADO [ON]";
                }
                yield "Usar items: permitido [OFF]";
            }
            case ENTITY_INTERACT -> {
                if (flag) {
                    yield "Entidades: BLOQUEADAS [ON]";
                }
                yield "Entidades: libres [OFF]";
            }
            case TRAMPLING -> {
                if (flag) {
                    yield "Cultivos: PROTEGIDOS [ON]";
                }
                yield "Cultivos: sin protec. [OFF]";
            }
            case FLUIDS -> {
                if (flag) {
                    yield "Fluidos: BLOQUEADOS [ON]";
                }
                yield "Fluidos: permitidos [OFF]";
            }
            case PVP_ALL -> {
                if (flag) {
                    yield "Zona PVP libre: ACTIVA [ON]";
                }
                yield "Zona PVP libre: inact. [OFF]";
            }
            case TREE_CHOPPING -> {
                if (flag) {
                    yield "\u00c1rboles: PROTEGIDOS [ON]";
                }
                yield "\u00c1rboles: se talan [OFF]";
            }
            case PUBLIC_MODE -> {
                if (flag) {
                    yield "Modo visita: ACTIVO [ON]";
                }
                yield "Modo visita: inactivo [OFF]";
            }
            case SHOW_WELCOME -> {
                if (flag) {
                    yield "Bienvenida custom: ON [ON]";
                }
                yield "Bienvenida custom: OFF [OFF]";
            }
            case SHOW_LEAVE -> {
                if (flag) {
                    yield "Mensaje de salida: ON [ON]";
                }
                yield "Mensaje de salida: OFF [OFF]";
            }
            case SHOW_BORDER -> {
                if (flag) {
                    yield "Ver contorno: ON [ON]";
                }
                yield "Ver contorno: OFF [OFF]";
            }
            case SHOW_PARTICLES -> {
                if (flag) {
                    yield "Ver part\u00edculas: ON [ON]";
                }
                yield "Ver part\u00edculas: OFF [OFF]";
            }
            case BURN_HOSTILES -> {
                if (flag) {
                    yield "Repeler hostiles: ON [ON]";
                }
                yield "Repeler hostiles: OFF [OFF]";
            }
            case ANIMAL_KILLING -> {
                if (flag) {
                    yield "Animales: PROTEGIDOS [ON]";
                }
                yield "Animales: se matan [OFF]";
            }
            case CHEST_ACCESS -> {
                if (flag) {
                    yield "Cofres: BLOQUEADOS [ON]";
                }
                yield "Cofres: acceso libre [OFF]";
            }
            case CROP_HARVEST -> {
                if (flag) {
                    yield "Cosecha: PROTEGIDA [ON]";
                }
                yield "Cosecha: libre [OFF]";
            }
            case ANVIL_USE -> {
                if (flag) {
                    yield "Yunques: BLOQUEADOS [ON]";
                }
                yield "Yunques: uso libre [OFF]";
            }
            case ENDER_PEARL -> {
                if (flag) {
                    yield "Ender pearl: BLOQUEADA [ON]";
                }
                yield "Ender pearl: permitida [OFF]";
            }
            case SIGN_EDITING -> {
                if (flag) {
                    yield "Letreros: BLOQUEADOS [ON]";
                }
                yield "Letreros: editables [OFF]";
            }
            case DOORS_ACCESS -> {
                if (flag) {
                    yield "Puertas/Botones: BLOQ [ON]";
                }
                yield "Puertas/Botones: libres [OFF]";
            }
            case ALL_MOB_SPAWN -> {
                if (flag) {
                    yield "Spawn de mobs: BLOQUEADO [ON]";
                }
                yield "Spawn de mobs: permitido [OFF]";
            }
            case PASSIVE_MOB_SPAWN -> {
                if (flag) {
                    yield "Animales: NO spawnean [ON]";
                }
                yield "Animales: spawnean [OFF]";
            }
            case BLOCK_ALL_INTERACT -> flag ? "Interacci\u00f3n total: BLOQ [ON]" : "Interacci\u00f3n total: libre [OFF]";
        };
    }

    private static String[] flagLore(ClaimFlags.FlagId claimflags$flagid) {
        String s;
        switch (claimflags$flagid) {
            case EFFECT_REGEN: {
                s = "Regenera vida a due\u00f1o y miembros";
                break;
            }
            case EFFECT_RESIST: {
                s = "Reduce da\u00f1o a due\u00f1o y miembros";
                break;
            }
            case EFFECT_SPEED: {
                s = "Da velocidad a due\u00f1o y miembros";
                break;
            }
            case ALLOW_FLIGHT: {
                s = "Due\u00f1o y miembros pueden volar";
                break;
            }
            case BUILDING: {
                s = "Intrusos no pueden colocar bloques";
                break;
            }
            case BREAKING: {
                s = "Intrusos no pueden romper nada";
                break;
            }
            case EXPLOSIONS: {
                s = "TNT y creepers no destruyen";
                break;
            }
            case FIRE: {
                s = "El fuego no se propaga aqu\u00ed";
                break;
            }
            case MOB_SPAWN: {
                s = "Zombies, skeletons no spawnean";
                break;
            }
            case PVP: {
                s = "Jugadores no pueden atacarse";
                break;
            }
            case MOB_DAMAGE: {
                s = "Los mobs no da\u00f1an a jugadores";
                break;
            }
            case ALERTS: {
                s = "Avisa al due\u00f1o cuando entran";
                break;
            }
            case ITEM_USE: {
                s = "Intrusos no pueden usar items";
                break;
            }
            case ENTITY_INTERACT: {
                s = "Intrusos no usan mobs/aldeanos";
                break;
            }
            case TRAMPLING: {
                s = "Intrusos no destruyen la tierra";
                break;
            }
            case FLUIDS: {
                s = "Nadie coloca agua ni lava aqu\u00ed";
                break;
            }
            case PVP_ALL: {
                s = "Todos se pueden atacar aqu\u00ed";
                break;
            }
            case TREE_CHOPPING: {
                s = "Intrusos no pueden talar \u00e1rboles";
                break;
            }
            case PUBLIC_MODE: {
                s = "Todos entran pero no modifican";
                break;
            }
            case SHOW_WELCOME: {
                s = "Mensaje personalizado al entrar";
                break;
            }
            case SHOW_LEAVE: {
                s = "Mensaje personalizado al salir";
                break;
            }
            case SHOW_BORDER: {
                s = "Dibuja el contorno de tu protecci\u00f3n (l\u00edneas)";
                break;
            }
            case SHOW_PARTICLES: {
                s = "Llena tu protecci\u00f3n con part\u00edculas";
                break;
            }
            case BURN_HOSTILES: {
                s = "Quema a los mobs hostiles que entren (d\u00eda o noche)";
                break;
            }
            case ANIMAL_KILLING: {
                s = "Intrusos no pueden matar animales";
                break;
            }
            case CHEST_ACCESS: {
                s = "Intrusos no abren cofres ni barriles";
                break;
            }
            case CROP_HARVEST: {
                s = "Intrusos no cosechan cultivos";
                break;
            }
            case ANVIL_USE: {
                s = "Intrusos no pueden usar yunques";
                break;
            }
            case ENDER_PEARL: {
                s = "Intrusos no se teletransportan";
                break;
            }
            case SIGN_EDITING: {
                s = "Intrusos no editan letreros";
                break;
            }
            case DOORS_ACCESS: {
                s = "Intrusos no usan puertas, botones ni placas";
                break;
            }
            case ALL_MOB_SPAWN: {
                s = "Nada spawnea aqu\u00ed: hostiles, animales y mobs de otros mods";
                break;
            }
            case PASSIVE_MOB_SPAWN: {
                s = "Animales, peces y murci\u00e9lagos dejan de spawnear (aldeanos no)";
                break;
            }
            case BLOCK_ALL_INTERACT: {
                s = "Intrusos no pueden interactuar con NADA en la zona";
                break;
            }
            default: {
                s = "";
            }
        }
        String s1 = claimflags$flagid != ClaimFlags.FlagId.SHOW_WELCOME && claimflags$flagid != ClaimFlags.FlagId.SHOW_LEAVE ? (claimflags$flagid == ClaimFlags.FlagId.SHOW_PARTICLES ? "Clic para elegir part\u00edcula y densidad" : "Clic para cambiar") : "Clic izq: editar | Clic der: on/off";
        return new String[]{s, s1};
    }

    private static ItemStack withName(ItemStack itemstack, Component component) {
        itemstack.setHoverName(component);
        return itemstack;
    }

    private static ItemStack withLore(ItemStack itemstack, List<Component> list) {
        ClaimBlocks.setLore(itemstack, list);
        return itemstack;
    }

    private static String truncate(String s, int i) {
        if (s == null) {
            return "";
        }
        return s.length() <= i ? s : s.substring(0, Math.max(0, i - 3)) + "...";
    }

    public void clicked(int i, int j, ClickType clicktype, Player player) {
        if (ClaimManager.getInstance().findClaimById(this.claim.getClaimId()) == null) {
            this.viewer.displayClientMessage((Component)Component.literal((String)"[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
            this.viewer.closeContainer();
        } else if (i >= 0 && i < 54) {
            if (i == 45 && this.page > 0) {
                ClaimMenuHandler.open(this.viewer, this.claim, this.page - 1);
            } else if (i == 53 && this.page < LAST_PAGE) {
                ClaimMenuHandler.open(this.viewer, this.claim, this.page + 1);
            } else if (i == 46) {
                if (!this.awaitingDeleteConfirm) {
                    this.awaitingDeleteConfirm = true;
                    this.rebuild();
                    this.viewer.displayClientMessage((Component)Component.literal((String)"[!] Haz clic de nuevo para confirmar.").withStyle(ChatFormatting.YELLOW), true);
                } else {
                    this.performDelete();
                }
            } else if (i == 47 && this.awaitingDeleteConfirm) {
                this.awaitingDeleteConfirm = false;
                this.rebuild();
                this.viewer.displayClientMessage((Component)Component.literal((String)"[i] Eliminaci\u00f3n cancelada.").withStyle(ChatFormatting.AQUA), true);
            } else {
                ClaimFlags.FlagId claimflags$flagid;
                if (this.awaitingDeleteConfirm) {
                    this.awaitingDeleteConfirm = false;
                }
                if ((claimflags$flagid = this.slotToFlag(i)) != null) {
                    int k = ClaimMenuHandler.requiredPaidLevel(claimflags$flagid);
                    if (k > 0 && ClaimMenuHandler.paidLevelOf(this.claim.getTier()) < k) {
                        this.viewer.displayClientMessage((Component)Component.literal((String)("[x] Requiere zona " + ClaimMenuHandler.requiredTierLabel(k) + " o superior.")).withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    if (claimflags$flagid == ClaimFlags.FlagId.SHOW_WELCOME) {
                        if (j == 1) {
                            this.claim.getFlags().showWelcome = !this.claim.getFlags().showWelcome;
                            ClaimManager.getInstance().save();
                            this.rebuild();
                        } else {
                            ClaimMenuHandler.requestEditWelcome(this.viewer, this.claim, this.page);
                            this.viewer.closeContainer();
                        }
                    } else if (claimflags$flagid == ClaimFlags.FlagId.SHOW_LEAVE) {
                        if (j == 1) {
                            this.claim.getFlags().showLeave = !this.claim.getFlags().showLeave;
                            ClaimManager.getInstance().save();
                            this.rebuild();
                        } else {
                            ClaimMenuHandler.requestEditLeave(this.viewer, this.claim, this.page);
                            this.viewer.closeContainer();
                        }
                    } else if (claimflags$flagid == ClaimFlags.FlagId.SHOW_BORDER) {
                        this.claim.getFlags().showBorder = !this.claim.getFlags().showBorder;
                        ClaimManager.getInstance().save();
                        this.rebuild();
                    } else if (claimflags$flagid == ClaimFlags.FlagId.SHOW_PARTICLES) {
                        ClaimParticleMenuHandler.open(this.viewer, this.claim, this.page);
                    } else {
                        this.claim.getFlags().toggle(claimflags$flagid);
                        ClaimManager.getInstance().save();
                        this.rebuild();
                    }
                } else if (i == 38) {
                    this.viewer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Miembros de la zona:").withStyle(ChatFormatting.GRAY), false);
                    if (this.claim.getMembers().isEmpty()) {
                        this.viewer.displayClientMessage((Component)Component.literal((String)"  (sin miembros)").withStyle(ChatFormatting.DARK_GRAY), false);
                    } else {
                        for (int l = 0; l < this.claim.getMembers().size(); ++l) {
                            String s = l < this.claim.getMemberNames().size() ? this.claim.getMemberNames().get(l) : this.claim.getMembers().get(l).toString();
                            this.viewer.displayClientMessage((Component)Component.literal((String)("  - " + s)).withStyle(ChatFormatting.WHITE), false);
                        }
                    }
                } else if (i == 42) {
                    if (j == 1) {
                        ClaimMenuHandler.requestAddMember(this.viewer, this.claim, this.page);
                        this.viewer.closeContainer();
                    } else {
                        MemberSelectMenu.open(this.viewer, this.claim, this.page, 0);
                    }
                } else if (i == 40) {
                    if (this.claim.getMembers().isEmpty()) {
                        this.viewer.displayClientMessage((Component)Component.literal((String)"[i] Esta zona no tiene miembros que quitar.").withStyle(ChatFormatting.YELLOW), true);
                    } else {
                        ClaimMenuHandler.requestRemoveMember(this.viewer, this.claim, this.page);
                        this.viewer.closeContainer();
                    }
                } else if (i == 39) {
                    ClaimMenuHandler.requestBanPlayer(this.viewer, this.claim, this.page);
                    this.viewer.closeContainer();
                } else if (i == 41) {
                    if (this.claim.getBannedPlayers().isEmpty()) {
                        this.viewer.displayClientMessage((Component)Component.literal((String)"[i] No hay jugadores baneados.").withStyle(ChatFormatting.YELLOW), true);
                    } else {
                        ClaimMenuHandler.requestUnbanPlayer(this.viewer, this.claim, this.page);
                        this.viewer.closeContainer();
                    }
                } else if (i == 43) {
                    ClaimGroup claimgroup = ClaimManager.getInstance().getGroupOf(this.claim);
                    if (claimgroup == null) {
                        ClaimMenuHandler.requestMergeName(this.viewer, this.claim, this.page);
                        this.viewer.closeContainer();
                    } else if (this.claim.isGroupMother()) {
                        ClaimMenuHandler.requestMergeUsers(this.viewer, this.claim, this.page);
                        this.viewer.closeContainer();
                    }
                } else if (i == 44) {
                    ClaimGroup claimgroup1 = ClaimManager.getInstance().getGroupOf(this.claim);
                    if (claimgroup1 != null && this.claim.isGroupMother()) {
                        ClaimManager.getInstance().dissolveGroupBreaking(claimgroup1.getGroupId());
                        this.viewer.displayClientMessage((Component)Component.literal((String)"\u2714 Grupo disuelto. Las piedras solapadas se devolvieron a sus duenos.").withStyle(ChatFormatting.GREEN), false);
                        this.rebuild();
                    }
                } else if (i == 49) {
                    this.viewer.closeContainer();
                } else if (i == 52) {
                    this.viewer.closeContainer();
                    this.viewer.server.getCommands().performPrefixedCommand(this.viewer.createCommandSourceStack(), "tf claims list");
                }
            }
        }
    }

    private void performDelete() {
        ClaimTier claimtier = this.claim.getTier();
        Level level = this.viewer.level();
        BlockPos blockpos = this.claim.getCenter();
        if (claimtier != null && ClaimBlocks.isClaimConcreteForTier(level.getBlockState(blockpos).getBlock(), claimtier)) {
            level.destroyBlock(blockpos, false);
        }
        level.playSound(null, blockpos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0f, 1.0f);
        ClaimManager.getInstance().removeClaim(level, blockpos);
        if (claimtier != null) {
            ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
            if (!this.viewer.getInventory().add(itemstack)) {
                this.viewer.drop(itemstack, false);
            }
        }
        this.viewer.displayClientMessage((Component)Component.literal((String)"\u2714 Zona eliminada. Protecci\u00f3n devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
        this.viewer.closeContainer();
    }

    private int pageIndex() {
        return Math.max(0, Math.min(LAST_PAGE, this.page));
    }

    private ClaimFlags.FlagId slotToFlag(int i) {
        ClaimFlags.FlagId[] aclaimflags$flagid = PAGES[this.pageIndex()];
        int[] aint = PAGE_SLOTS[this.pageIndex()];
        for (int j = 0; j < aint.length; ++j) {
            if (aint[j] != i) continue;
            return aclaimflags$flagid[j];
        }
        return null;
    }

    public static void open(ServerPlayer serverplayer, Claim claim, int i) {
        ClaimMenuHandler.open(serverplayer, claim, i, null);
    }

    public static void open(ServerPlayer serverplayer, final Claim claim, int i, String s) {
        if (claim.getGroupId() != null && !claim.isGroupMother()) {
            Claim claim1 = claim.getMother();
            String s2 = claim1 != null ? claim1.getOwnerName() : "?";
            serverplayer.displayClientMessage((Component)Component.literal((String)("[!] Esta piedra pertenece al grupo de " + s2 + ". Solo la piedra nodriza gestiona el grupo. Puedes romperla para recuperarla.")).withStyle(ChatFormatting.YELLOW), false);
        } else {
            final int j = Math.max(0, Math.min(LAST_PAGE, i));
            ClaimGroup claimgroup = ClaimManager.getInstance().getGroupOf(claim);
            final String s1 = s != null ? ClaimMenuHandler.truncate(s, 40) : (claimgroup != null ? ClaimMenuHandler.truncate("Grupo: " + claimgroup.getName(), 40) : ClaimMenuHandler.truncate("Zona " + claim.sizeLabel() + " - " + claim.getOwnerName(), 40));
            NetworkHooks.openScreen((ServerPlayer)serverplayer, (MenuProvider)new MenuProvider(){

                public Component getDisplayName() {
                    return Component.literal((String)s1).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD});
                }

                public AbstractContainerMenu createMenu(int k, Inventory inventory, Player player) {
                    return new ClaimMenuHandler(k, inventory, claim, j);
                }
            });
        }
    }

    public static void requestAddMember(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.ADD_MEMBER, claim.getClaimId(), i));
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Escribe el nombre del jugador a a\u00f1adir (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
        serverplayer.displayClientMessage((Component)Component.literal((String)"    No hace falta que est\u00e9 conectado. Alternativa: /tf claims addmember <jugador>").withStyle(ChatFormatting.DARK_GRAY), false);
    }

    public static void requestRemoveMember(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.REMOVE_MEMBER, claim.getClaimId(), i));
        StringBuilder stringbuilder = new StringBuilder();
        List<String> list = claim.getMemberNames();
        for (int j = 0; j < list.size(); ++j) {
            if (j > 0) {
                stringbuilder.append(", ");
            }
            stringbuilder.append(list.get(j));
        }
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Miembros: ").withStyle(ChatFormatting.GRAY).append((Component)Component.literal((String)stringbuilder.toString()).withStyle(ChatFormatting.WHITE)), false);
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Escribe el nombre del invitado a quitar (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
    }

    public static void requestEditWelcome(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.EDIT_WELCOME, claim.getClaimId(), i));
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Escribe tu bienvenida (max 60 chars) o 'cancelar':").withStyle(ChatFormatting.YELLOW), false);
    }

    public static void requestEditLeave(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.EDIT_LEAVE, claim.getClaimId(), i));
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Escribe tu mensaje de salida (max 60 chars) o 'cancelar':").withStyle(ChatFormatting.YELLOW), false);
    }

    public static boolean hasPrompt(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        PendingChat claimmenuhandler$pendingchat = pending.get(uuid);
        if (claimmenuhandler$pendingchat == null) {
            return false;
        }
        if (claimmenuhandler$pendingchat.isExpired()) {
            pending.remove(uuid, claimmenuhandler$pendingchat);
            return false;
        }
        return true;
    }

    public static PendingChat popPrompt(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PendingChat claimmenuhandler$pendingchat = pending.remove(uuid);
        return claimmenuhandler$pendingchat != null && !claimmenuhandler$pendingchat.isExpired() ? claimmenuhandler$pendingchat : null;
    }

    public static void clearPrompt(UUID uuid) {
        if (uuid != null) {
            pending.remove(uuid);
            pendingMergeName.remove(uuid);
        }
    }

    public static void handleChat(ServerChatEvent serverchatevent) {
        ServerPlayer serverplayer = serverchatevent.getPlayer();
        if (serverplayer != null) {
            String s = serverchatevent.getRawText();
            if (ChatPromptRouter.consume(serverplayer, s)) {
                serverchatevent.setCanceled(true);
            } else if (ChatPromptRouter.shouldSuppress(serverplayer.getUUID(), s)) {
                serverchatevent.setCanceled(true);
            }
        }
    }

    public static void dispatchPrompt(ServerPlayer serverplayer, PendingChat claimmenuhandler$pendingchat, String s) {
        if (serverplayer != null && claimmenuhandler$pendingchat != null && !serverplayer.hasDisconnected()) {
            if (ChatPromptRouter.isCancel(s)) {
                serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Cancelado.").withStyle(ChatFormatting.GRAY), false);
            } else {
                Claim claimx = ClaimMenuHandler.findClaimById(claimmenuhandler$pendingchat.claimId());
                if (claimx == null) {
                    serverplayer.displayClientMessage((Component)Component.literal((String)"[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                } else {
                    switch (claimmenuhandler$pendingchat.type()) {
                        case ADD_MEMBER: {
                            ClaimMenuHandler.handleAddMember(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        }
                        case REMOVE_MEMBER: {
                            ClaimMenuHandler.handleRemoveMember(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        }
                        case EDIT_WELCOME: {
                            ClaimMenuHandler.handleEditWelcome(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        }
                        case EDIT_LEAVE: {
                            ClaimMenuHandler.handleEditLeave(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        }
                        case BAN_PLAYER: {
                            ClaimMenuHandler.handleBanPlayer(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        }
                        case UNBAN_PLAYER: {
                            ClaimMenuHandler.handleUnbanPlayer(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        }
                        case MERGE_NAME: {
                            ClaimMenuHandler.handleMergeName(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        }
                        case MERGE_USERS: {
                            ClaimMenuHandler.handleMergeUsers(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                        }
                    }
                }
            }
        }
    }

    public static void dispatchAdminTransfer(ServerPlayer serverplayer, UUID uuid, String s) {
        if (serverplayer != null && !serverplayer.hasDisconnected()) {
            if (ChatPromptRouter.isCancel(s)) {
                serverplayer.displayClientMessage((Component)Component.literal((String)"[Protecci\u00f3n] Cancelado.").withStyle(ChatFormatting.GRAY), false);
            } else {
                ClaimMenuHandler.handleAdminTransfer(serverplayer, uuid, ChatPromptRouter.extractPlayerName(s));
            }
        }
    }

    private static void handleAdminTransfer(ServerPlayer serverplayer, UUID uuid, String s) {
        PlayerLookup.resolveAsync(serverplayer.getServer(), s, playerlookup$resolved -> {
            if (!serverplayer.hasDisconnected()) {
                ClaimMenuHandler.applyAdminTransfer(serverplayer, uuid, s, playerlookup$resolved);
            }
        });
    }

    private static void applyAdminTransfer(ServerPlayer serverplayer, UUID uuid, String s, PlayerLookup.Resolved playerlookup$resolved) {
        Claim claimx = ClaimMenuHandler.findClaimById(uuid);
        if (claimx == null) {
            serverplayer.displayClientMessage((Component)Component.literal((String)"[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
        } else if (playerlookup$resolved == null) {
            serverplayer.displayClientMessage((Component)Component.literal((String)("[x] Jugador no encontrado: " + s)).withStyle(ChatFormatting.RED), false);
        } else {
            claimx.setOwner(playerlookup$resolved.id(), playerlookup$resolved.name());
            claimx.getMembers().clear();
            claimx.getMemberNames().clear();
            ClaimManager.getInstance().save();
            serverplayer.displayClientMessage((Component)Component.literal((String)("\u2714 Zona transferida a " + playerlookup$resolved.name() + ".")).withStyle(ChatFormatting.GREEN), false);
            MutableComponent mutablecomponent = Component.literal((String)"[!] Un administrador te transfiri\u00f3 una zona ").withStyle(ChatFormatting.YELLOW).append((Component)Component.literal((String)claimx.sizeLabel()).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})).append((Component)Component.literal((String)(" en X:" + claimx.getX() + " Z:" + claimx.getZ())).withStyle(ChatFormatting.YELLOW));
            if (playerlookup$resolved.isOnline()) {
                playerlookup$resolved.online().displayClientMessage((Component)mutablecomponent, false);
            } else {
                ClaimManager.getInstance().queueMessage(playerlookup$resolved.id(), (Component)mutablecomponent);
            }
        }
    }

    private static void handleAddMember(ServerPlayer serverplayer, Claim claim, String s, int i) {
        ClaimMenuHandler.addMemberByName(serverplayer, claim, s, i, true);
    }

    public static boolean addMemberByName(ServerPlayer serverplayer, Claim claim, String s, int i, boolean flag) {
        String s1 = ChatPromptRouter.extractPlayerName(s);
        UUID uuid = claim.getClaimId();
        PlayerLookup.resolveAsync(serverplayer.getServer(), s1, playerlookup$resolved -> {
            if (!serverplayer.hasDisconnected()) {
                Claim claim1 = ClaimMenuHandler.findClaimById(uuid);
                if (claim1 == null) {
                    serverplayer.displayClientMessage((Component)Component.literal((String)"[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                } else {
                    if (playerlookup$resolved == null) {
                        serverplayer.displayClientMessage((Component)Component.literal((String)("[x] No encuentro al jugador \"" + s1 + "\". Revisa el nombre; si nunca ha entrado al servidor, no puedo resolverlo.")).withStyle(ChatFormatting.RED), false);
                    } else {
                        ClaimMenuHandler.addMemberResolved(serverplayer, claim1, playerlookup$resolved);
                    }
                    if (flag) {
                        ClaimMenuHandler.open(serverplayer, claim1, i);
                    }
                }
            }
        });
        return true;
    }

    public static boolean addMemberResolved(ServerPlayer serverplayer, Claim claim, PlayerLookup.Resolved playerlookup$resolved) {
        if (claim.isOwner(playerlookup$resolved.id())) {
            serverplayer.displayClientMessage((Component)Component.literal((String)"[x] Ese jugador ya es el due\u00f1o.").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (claim.isMember(playerlookup$resolved.id())) {
            serverplayer.displayClientMessage((Component)Component.literal((String)("[i] " + playerlookup$resolved.name() + " ya es miembro de esta zona.")).withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        int i = ClaimConfig.get().maxMembersPerClaim;
        if (i > 0 && claim.getMembers().size() >= i) {
            serverplayer.displayClientMessage((Component)Component.literal((String)("[x] Esta zona ya tiene el maximo de miembros (" + i + ").")).withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (claim.isBanned(playerlookup$resolved.id())) {
            claim.unbanPlayer(playerlookup$resolved.id());
            serverplayer.displayClientMessage((Component)Component.literal((String)("[i] " + playerlookup$resolved.name() + " estaba baneado de la zona; se le quit\u00f3 el baneo.")).withStyle(ChatFormatting.YELLOW), false);
        }
        claim.addMember(playerlookup$resolved.id(), playerlookup$resolved.name());
        ClaimManager.getInstance().save();
        serverplayer.displayClientMessage((Component)Component.literal((String)("\u2714 " + playerlookup$resolved.name() + " agregado como miembro de la zona.")).withStyle(ChatFormatting.GREEN), false);
        MutableComponent mutablecomponent = Component.literal((String)("[Protecci\u00f3n] Eres miembro de la zona de " + serverplayer.getName().getString())).withStyle(ChatFormatting.AQUA);
        if (playerlookup$resolved.isOnline()) {
            playerlookup$resolved.online().displayClientMessage((Component)mutablecomponent, false);
        } else {
            ClaimManager.getInstance().queueMessage(playerlookup$resolved.id(), (Component)mutablecomponent);
        }
        return true;
    }

    private static void handleRemoveMember(ServerPlayer serverplayer, Claim claim, String s, int i) {
        ClaimMenuHandler.removeMemberByName(serverplayer, claim, s, i, true);
    }

    public static boolean removeMemberByName(ServerPlayer serverplayer, Claim claim, String s, int i, boolean flag) {
        ServerPlayer serverplayer1;
        PlayerLookup.Resolved playerlookup$resolved;
        String s1 = ChatPromptRouter.extractPlayerName(s);
        UUID uuid = null;
        String s2 = s1;
        for (int j = 0; j < claim.getMemberNames().size() && j < claim.getMembers().size(); ++j) {
            if (!claim.getMemberNames().get(j).equalsIgnoreCase(s1)) continue;
            uuid = claim.getMembers().get(j);
            s2 = claim.getMemberNames().get(j);
            break;
        }
        if (uuid == null && (playerlookup$resolved = PlayerLookup.resolve(serverplayer.getServer(), s1)) != null && claim.isMember(playerlookup$resolved.id())) {
            uuid = playerlookup$resolved.id();
            s2 = playerlookup$resolved.name();
        }
        if (uuid == null) {
            serverplayer.displayClientMessage((Component)Component.literal((String)("[x] " + s1 + " no es miembro de esta zona.")).withStyle(ChatFormatting.RED), false);
            if (flag) {
                ClaimMenuHandler.open(serverplayer, claim, i);
            }
            return false;
        }
        claim.removeMember(uuid);
        ClaimManager.getInstance().save();
        serverplayer.displayClientMessage((Component)Component.literal((String)("\u2714 " + s2 + " fue eliminado de la zona.")).withStyle(ChatFormatting.GREEN), false);
        MutableComponent mutablecomponent = Component.literal((String)("[Protecci\u00f3n] Ya no eres miembro de la zona de " + serverplayer.getName().getString())).withStyle(ChatFormatting.YELLOW);
        ServerPlayer serverPlayer = serverplayer1 = serverplayer.getServer() == null ? null : serverplayer.getServer().getPlayerList().getPlayer(uuid);
        if (serverplayer1 != null) {
            serverplayer1.displayClientMessage((Component)mutablecomponent, false);
        } else {
            ClaimManager.getInstance().queueMessage(uuid, (Component)mutablecomponent);
        }
        if (flag) {
            ClaimMenuHandler.open(serverplayer, claim, i);
        }
        return true;
    }

    private static void handleEditWelcome(ServerPlayer serverplayer, Claim claim, String s, int i) {
        int j = ClaimConfig.get().maxWelcomeLength;
        if (s.length() > j) {
            s = s.substring(0, j);
        }
        claim.getFlags().welcomeMessage = s;
        claim.getFlags().showWelcome = !s.isBlank();
        ClaimManager.getInstance().save();
        serverplayer.displayClientMessage((Component)Component.literal((String)"\u2714 Bienvenida guardada.").withStyle(ChatFormatting.GREEN), false);
        ClaimMenuHandler.open(serverplayer, claim, i);
    }

    private static void handleEditLeave(ServerPlayer serverplayer, Claim claim, String s, int i) {
        int j = ClaimConfig.get().maxWelcomeLength;
        if (s.length() > j) {
            s = s.substring(0, j);
        }
        claim.getFlags().leaveMessage = s;
        claim.getFlags().showLeave = !s.isBlank();
        ClaimManager.getInstance().save();
        serverplayer.displayClientMessage((Component)Component.literal((String)"\u2714 Mensaje de salida guardado.").withStyle(ChatFormatting.GREEN), false);
        ClaimMenuHandler.open(serverplayer, claim, i);
    }

    public static void requestMergeName(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.MERGE_NAME, claim.getClaimId(), i));
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Grupo] Escribe el NOMBRE de la zona unida (o 'cancelar'):").withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    public static void requestMergeUsers(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.MERGE_USERS, claim.getClaimId(), i));
        serverplayer.displayClientMessage((Component)Component.literal((String)"[Grupo] Escribe el/los jugadores a invitar (separados por espacio) o 'cancelar':").withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    private static void handleMergeName(ServerPlayer serverplayer, Claim claim, String s, int i) {
        String s1 = s.length() > 32 ? s.substring(0, 32) : s;
        pendingMergeName.put(serverplayer.getUUID(), s1);
        pending.put(serverplayer.getUUID(), new PendingChat(PendingType.MERGE_USERS, claim.getClaimId(), i));
        serverplayer.displayClientMessage((Component)Component.literal((String)("[Grupo] Nombre: \"" + s1 + "\". Ahora escribe el/los jugadores a invitar (separados por espacio):")).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    private static void handleMergeUsers(ServerPlayer serverplayer, Claim claim, String s, int i) {
        ClaimManager claimmanager = ClaimManager.getInstance();
        ClaimGroup claimgroup = claimmanager.getGroupOf(claim);
        if (claimgroup == null) {
            String s1 = pendingMergeName.getOrDefault(serverplayer.getUUID(), "Grupo");
            claimgroup = claimmanager.createGroup(claim, s1);
        }
        pendingMergeName.remove(serverplayer.getUUID());
        String[] astring = ChatPromptRouter.sanitize(s).split("[ ,]+");
        int j = 0;
        for (String s2 : astring) {
            String s3 = s2.trim();
            if (s3.isEmpty()) continue;
            ServerPlayer serverplayer1 = serverplayer.server.getPlayerList().getPlayerByName(s3);
            if (serverplayer1 == null) {
                serverplayer.displayClientMessage((Component)Component.literal((String)("[x] " + s3 + " no esta en linea (debe estar conectado para invitarlo).")).withStyle(ChatFormatting.RED), false);
                continue;
            }
            if (serverplayer1.getUUID().equals(serverplayer.getUUID())) continue;
            if (claimgroup.isRegistered(serverplayer1.getUUID())) {
                serverplayer.displayClientMessage((Component)Component.literal((String)("[i] " + serverplayer1.getName().getString() + " ya esta en el grupo.")).withStyle(ChatFormatting.GRAY), false);
                continue;
            }
            String s4 = ClaimMenuHandler.genCode();
            invites.put(s4, new MergeInvite(s4, claimgroup.getGroupId(), serverplayer1.getUUID(), serverplayer.getName().getString(), claimgroup.getName()));
            ClaimMenuHandler.sendInvite(serverplayer1, serverplayer.getName().getString(), claimgroup.getName(), s4);
            ++j;
        }
        if (j > 0) {
            serverplayer.displayClientMessage((Component)Component.literal((String)("\u2714 Invitacion enviada a " + j + " jugador(es). Grupo: \"" + claimgroup.getName() + "\".")).withStyle(ChatFormatting.GREEN), false);
        }
        ClaimMenuHandler.open(serverplayer, claim, i);
    }

    private static void sendInvite(ServerPlayer serverplayer, String s, String s1, String s2) {
        serverplayer.displayClientMessage((Component)Component.literal((String)("[Grupo] " + s + " te invita a unir tu proteccion al grupo \"" + s1 + "\".")).withStyle(ChatFormatting.AQUA), false);
        MutableComponent mutablecomponent = Component.literal((String)" [\u2714 ACEPTAR] ").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(Boolean.valueOf(true)).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tf claims merge accept " + s2)));
        MutableComponent mutablecomponent1 = Component.literal((String)"[\u2718 RECHAZAR]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(Boolean.valueOf(true)).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tf claims merge reject " + s2)));
        serverplayer.displayClientMessage((Component)Component.literal((String)"").append((Component)mutablecomponent).append((Component)mutablecomponent1), false);
    }

    public static void acceptMerge(ServerPlayer serverplayer, String s) {
        MergeInvite claimmenuhandler$mergeinvite = invites.remove(s);
        if (claimmenuhandler$mergeinvite != null && serverplayer.getUUID().equals(claimmenuhandler$mergeinvite.targetId())) {
            ClaimManager claimmanager = ClaimManager.getInstance();
            ClaimGroup claimgroup = claimmanager.getGroup(claimmenuhandler$mergeinvite.groupId());
            if (claimgroup == null) {
                serverplayer.displayClientMessage((Component)Component.literal((String)"[x] El grupo ya no existe.").withStyle(ChatFormatting.RED), false);
            } else {
                ServerPlayer serverplayer1;
                claimmanager.registerPlayer(claimgroup.getGroupId(), serverplayer.getUUID());
                serverplayer.displayClientMessage((Component)Component.literal((String)("\u2714 Te uniste al grupo \"" + claimgroup.getName() + "\". Ahora tus piedras colocadas dentro de esa zona se uniran.")).withStyle(ChatFormatting.GREEN), false);
                MutableComponent mutablecomponent = Component.literal((String)(serverplayer.getName().getString() + " acepto unirse al grupo \"" + claimgroup.getName() + "\".")).withStyle(ChatFormatting.GREEN);
                ServerPlayer serverPlayer = serverplayer1 = claimgroup.getMotherOwnerId() == null ? null : serverplayer.server.getPlayerList().getPlayer(claimgroup.getMotherOwnerId());
                if (serverplayer1 != null) {
                    serverplayer1.displayClientMessage((Component)mutablecomponent, false);
                } else if (claimgroup.getMotherOwnerId() != null) {
                    claimmanager.queueMessage(claimgroup.getMotherOwnerId(), (Component)mutablecomponent);
                }
            }
        } else {
            serverplayer.displayClientMessage((Component)Component.literal((String)"[x] Invitacion no valida o expirada.").withStyle(ChatFormatting.RED), false);
        }
    }

    public static void rejectMerge(ServerPlayer serverplayer, String s) {
        MergeInvite claimmenuhandler$mergeinvite = invites.remove(s);
        if (claimmenuhandler$mergeinvite != null && serverplayer.getUUID().equals(claimmenuhandler$mergeinvite.targetId())) {
            serverplayer.displayClientMessage((Component)Component.literal((String)"[i] Rechazaste la invitacion de union.").withStyle(ChatFormatting.GRAY), false);
            ClaimGroup claimgroup = ClaimManager.getInstance().getGroup(claimmenuhandler$mergeinvite.groupId());
            if (claimgroup != null && claimgroup.getMotherOwnerId() != null) {
                MutableComponent mutablecomponent = Component.literal((String)(serverplayer.getName().getString() + " rechazo unirse al grupo \"" + claimgroup.getName() + "\".")).withStyle(ChatFormatting.YELLOW);
                ServerPlayer serverplayer1 = serverplayer.server.getPlayerList().getPlayer(claimgroup.getMotherOwnerId());
                if (serverplayer1 != null) {
                    serverplayer1.displayClientMessage((Component)mutablecomponent, false);
                } else {
                    ClaimManager.getInstance().queueMessage(claimgroup.getMotherOwnerId(), (Component)mutablecomponent);
                }
            }
        } else {
            serverplayer.displayClientMessage((Component)Component.literal((String)"[x] Invitacion no valida o expirada.").withStyle(ChatFormatting.RED), false);
        }
    }

    public static void leaveMerge(ServerPlayer serverplayer) {
        ClaimManager claimmanager = ClaimManager.getInstance();
        ClaimGroup claimgroup = claimmanager.getGroupByRegistered(serverplayer.getUUID());
        if (claimgroup == null) {
            serverplayer.displayClientMessage((Component)Component.literal((String)"[!] No estas en ningun grupo.").withStyle(ChatFormatting.YELLOW), false);
        } else {
            boolean flag = serverplayer.getUUID().equals(claimgroup.getMotherOwnerId());
            String s = claimgroup.getName();
            claimmanager.leaveGroupBreaking(claimgroup.getGroupId(), serverplayer.getUUID());
            serverplayer.displayClientMessage((Component)Component.literal((String)(flag ? "\u2714 Disolviste el grupo \"" + s + "\"." : "\u2714 Saliste del grupo \"" + s + "\". Tus piedras vuelven a ser independientes.")).withStyle(ChatFormatting.GREEN), false);
        }
    }

    private static String genCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Claim findClaimById(UUID uuid) {
        for (Claim claimx : ClaimManager.getInstance().getAllClaims()) {
            if (!claimx.getClaimId().equals(uuid)) continue;
            return claimx;
        }
        return null;
    }

    public record PendingChat(PendingType type, UUID claimId, int returnPage, long createdAtMillis) {
        public PendingChat(PendingType claimmenuhandler$pendingtype, UUID uuid, int i) {
            this(claimmenuhandler$pendingtype, uuid, i, System.currentTimeMillis());
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - this.createdAtMillis > ClaimConfig.get().chatPromptMillis();
        }
    }

    public static enum PendingType {
        ADD_MEMBER,
        EDIT_WELCOME,
        EDIT_LEAVE,
        BAN_PLAYER,
        UNBAN_PLAYER,
        REMOVE_MEMBER,
        MERGE_NAME,
        MERGE_USERS;

    }

    public record MergeInvite(String code, UUID groupId, UUID targetId, String inviterName, String groupName) {
    }
}

