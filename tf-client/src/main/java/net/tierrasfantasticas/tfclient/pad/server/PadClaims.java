package net.tierrasfantasticas.tfclient.pad.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.ChatFormatting;
import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags;
import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags.FlagId;
import net.tierrasfantasticas.tfclient.claims.data.ClaimGroup;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimMenuHandler;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimParticleMenuHandler;
import net.tierrasfantasticas.tfclient.claims.render.ParticleBorder;
import net.tierrasfantasticas.tfclient.claims.util.PlayerLookup;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;

/**
 * Protección (TF Claims) dentro del pad. La lista de tus zonas y, en cada una: AJUSTES (lo que se protege, con un
 * interruptor por fila y las ventajas que piden zona grande), MIEMBROS, BANEOS, PARTÍCULAS (rejilla para elegir y
 * densidad) y MÁS (mensajes de entrada y salida, contorno, grupo y eliminar la zona). Todo sin chat. Lo administra el
 * dueño (y el staff). Pestañas: "" la lista; "z:&lt;id&gt;:a|m|b|p|x" una zona.
 */
public final class PadClaims {
    public static final PadServer.App APP = new App();

    private static final int TEXT = 0x18265C, MUTED = 0x7E8CA8, GOLD = 0xC27A10;

    /**
     * Una opción de la zona. Para que nunca haya dudas, la fila dice QUÉ controla (name) y el botón CÓMO está ahora
     * (on/off: «BLOQUEADO», «PERMITIDO», «ACTIVADO»…), y debajo una frase con lo que pasa ahora mismo (onDesc/offDesc).
     * En el código, la opción encendida es la que protege o la ventaja puesta.
     */
    private record Flag(FlagId id, String name, String on, String off, String onDesc, String offDesc, Item icon) {}

    private static Flag block(FlagId id, String name, String fem, String onDesc, String offDesc, Item icon) {
        return new Flag(id, name, "BLOQUEAD" + fem, "PERMITID" + fem, onDesc, offDesc, icon);
    }

    private static Flag feature(FlagId id, String name, String fem, String onDesc, String offDesc, Item icon) {
        return new Flag(id, name, "ACTIVAD" + fem, "DESACTIVAD" + fem, onDesc, offDesc, icon);
    }

    private record Section(String title, List<Flag> flags) {}

    private static final List<Section> SECTIONS = List.of(
            new Section("Bloques y terreno (para los de fuera de la zona)", List.of(
                    block(FlagId.BUILDING, "Construir", "O", "Los de fuera no pueden colocar bloques.", "Cualquiera puede colocar bloques.", Items.BRICKS),
                    block(FlagId.BREAKING, "Romper bloques", "O", "Los de fuera no pueden romper nada.", "Cualquiera puede romper bloques.", Items.IRON_PICKAXE),
                    block(FlagId.EXPLOSIONS, "Explosiones", "AS", "La TNT y los creepers no rompen nada.", "La TNT y los creepers rompen bloques.", Items.TNT),
                    block(FlagId.FIRE, "Fuego", "O", "El fuego no se propaga ni quema bloques.", "El fuego se propaga y quema bloques.", Items.FLINT_AND_STEEL),
                    block(FlagId.FLUIDS, "Poner agua y lava", "O", "Los de fuera no pueden poner agua ni lava.", "Cualquiera puede poner agua y lava.", Items.WATER_BUCKET),
                    block(FlagId.TREE_CHOPPING, "Talar árboles", "O", "Los de fuera no pueden talar.", "Cualquiera puede talar.", Items.OAK_SAPLING),
                    block(FlagId.TRAMPLING, "Pisar los cultivos", "O", "La tierra de cultivo no se rompe al pisarla.", "La tierra de cultivo se rompe al pisarla.", Items.WHEAT_SEEDS),
                    block(FlagId.CROP_HARVEST, "Cosechar", "O", "Los de fuera no pueden cosechar.", "Cualquiera puede cosechar.", Items.WHEAT),
                    block(FlagId.ANIMAL_KILLING, "Matar tus animales", "O", "Los de fuera no pueden matar tus animales.", "Cualquiera puede matar tus animales.", Items.LEAD))),
            new Section("Acceso (para los de fuera de la zona)", List.of(
                    block(FlagId.CHEST_ACCESS, "Abrir cofres", "O", "Los de fuera no abren cofres ni barriles.", "Cualquiera abre cofres y barriles.", Items.CHEST),
                    block(FlagId.DOORS_ACCESS, "Puertas y botones", "OS", "Los de fuera no usan puertas, botones ni placas.", "Cualquiera usa puertas, botones y placas.", Items.OAK_DOOR),
                    block(FlagId.ANVIL_USE, "Usar yunques", "O", "Los de fuera no pueden usar yunques.", "Cualquiera puede usar yunques.", Items.ANVIL),
                    block(FlagId.SIGN_EDITING, "Editar letreros", "O", "Los de fuera no pueden editarlos.", "Cualquiera puede editarlos.", Items.OAK_SIGN),
                    block(FlagId.ITEM_USE, "Usar objetos", "O", "Los de fuera no pueden usar objetos.", "Cualquiera puede usar objetos.", Items.BUCKET),
                    block(FlagId.ENTITY_INTERACT, "Aldeanos, soportes y marcos", "OS", "Los de fuera no pueden tocarlos.", "Cualquiera puede usarlos.", Items.ARMOR_STAND),
                    block(FlagId.ENDER_PEARL, "Entrar con perlas de ender", "O", "Nadie entra lanzando perlas.", "Se puede entrar lanzando perlas.", Items.ENDER_PEARL),
                    block(FlagId.BLOCK_ALL_INTERACT, "Tocar cualquier cosa", "O", "Los de fuera no pueden tocar NADA.", "Vale lo que digan las demás opciones.", Items.BARRIER),
                    feature(FlagId.PUBLIC_MODE, "Modo visita", "O", "Todos pueden entrar a mirar; nadie de fuera modifica nada.", "Apagado.", Items.SPYGLASS))),
            new Section("Mobs y PvP", List.of(
                    block(FlagId.MOB_SPAWN, "Que salgan monstruos", "O", "No salen zombis, esqueletos ni otros monstruos.", "Los monstruos salen como en cualquier sitio.", Items.ZOMBIE_HEAD),
                    block(FlagId.ALL_MOB_SPAWN, "Que salga cualquier mob", "O", "No sale nada (tampoco mobs de otros mods).", "Los mobs salen como en cualquier sitio.", Items.SPAWNER),
                    block(FlagId.PASSIVE_MOB_SPAWN, "Que salgan animales", "O", "No salen animales ni peces (aldeanos sí).", "Los animales salen como en cualquier sitio.", Items.EGG),
                    block(FlagId.MOB_DAMAGE, "Daño de los mobs", "O", "Los mobs no hacen daño en la zona.", "Los mobs hacen daño como en cualquier sitio.", Items.SHIELD),
                    feature(FlagId.BURN_HOSTILES, "Quemar a los monstruos", "O", "Los monstruos que entran arden.", "Los monstruos que entran no arden.", Items.BLAZE_POWDER),
                    new Flag(FlagId.PVP, "Pelear entre jugadores", "BLOQUEADO", "NORMAL", "Nadie se puede atacar en la zona.", "Como en el resto del servidor.", Items.IRON_SWORD),
                    feature(FlagId.PVP_ALL, "PvP libre", "O", "Todos se pueden atacar en la zona.", "Apagado: como en el resto del servidor.", Items.DIAMOND_SWORD),
                    feature(FlagId.ALERTS, "Avisos de intrusos", "OS", "Te avisa cuando entra alguien.", "No te avisa cuando entra alguien.", Items.BELL))),
            new Section("Ventajas de zona grande (para ti y tus miembros)", List.of(
                    feature(FlagId.EFFECT_REGEN, "Regeneración", "A", "Os curáis solos dentro de la zona.", "Sin regeneración.", Items.GOLDEN_APPLE),
                    feature(FlagId.EFFECT_RESIST, "Resistencia", "A", "Recibís menos daño dentro de la zona.", "Sin resistencia.", Items.IRON_CHESTPLATE),
                    feature(FlagId.EFFECT_SPEED, "Velocidad", "A", "Vais más rápido dentro de la zona.", "Sin velocidad extra.", Items.SUGAR),
                    feature(FlagId.ALLOW_FLIGHT, "Volar", "O", "Voláis dentro de la zona.", "Nadie vuela en la zona.", Items.FEATHER))));

    private PadClaims() {}

    // ---------------------------------------------------------------------------------------------------------------

    private static int paidLevel(ClaimTier tier) {
        if (tier == null) return 0;
        return switch (tier.id) {
            case "claimstone_250x250" -> 1;
            case "claimstone_300x300" -> 2;
            case "claimstone_500x500" -> 3;
            default -> 0;
        };
    }

    private static int required(FlagId f) {
        return switch (f) {
            case EFFECT_REGEN -> 1;
            case EFFECT_RESIST, EFFECT_SPEED -> 2;
            case ALLOW_FLIGHT -> 3;
            default -> 0;
        };
    }

    private static String requiredLabel(int level) {
        return switch (level) {
            case 1 -> "250X250";
            case 2 -> "300X300";
            default -> "500X500";
        };
    }

    private static Claim claim(String id) {
        try {
            return ClaimManager.getInstance().findClaimById(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean canManage(ServerPlayer player, Claim c) {
        return c != null && (c.isOwner(player.getUUID()) || player.hasPermissions(2));
    }

    private static String dim(String world) {
        return switch (world) {
            case "minecraft:overworld" -> "Mundo normal";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "El End";
            default -> world;
        };
    }

    private static ItemStack icon(Claim c) {
        ClaimTier tier = c.getTier();
        return tier == null ? new ItemStack(Items.STONE) : new ItemStack(ClaimBlocks.itemForTier(tier));
    }

    private static String title(Claim c) {
        ClaimGroup g = ClaimManager.getInstance().getGroupOf(c);
        return g != null ? g.getName() : "Zona " + c.sizeLabel();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            if (tab.startsWith("z:")) {
                String[] t = tab.split(":");
                Claim c = t.length > 1 ? claim(t[1]) : null;
                if (canManage(player, c)) return zone(player, c, t.length > 2 ? t[2] : "a");
            }
            return list(player);
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 3);
            Claim c = a.length > 1 ? claim(a[1]) : null;
            if (a[0].equals("volver")) return "";
            if (a[0].equals("inv_si") || a[0].equals("inv_no")) {
                String code = a.length > 1 ? a[1] : "";
                if (a[0].equals("inv_si")) ClaimMenuHandler.acceptMerge(player, code);
                else ClaimMenuHandler.rejectMerge(player, code);
                return "";
            }
            if (a[0].equals("salirgrupo")) {
                if (PadServer.confirm(player, "protecciones.salirgrupo")) ClaimMenuHandler.leaveMerge(player);
                return "";
            }
            if (!canManage(player, c)) return "";
            ClaimFlags f = c.getFlags();
            String arg = a.length > 2 ? a[2] : "";
            ClaimManager manager = ClaimManager.getInstance();
            switch (a[0]) {
                case "flag" -> {
                    FlagId id;
                    try {
                        id = FlagId.valueOf(arg);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    int need = required(id);
                    if (need > 0 && paidLevel(c.getTier()) < need) {
                        TFPadNet.notice(player, "Necesita una zona de " + requiredLabel(need).toLowerCase(Locale.ROOT) + " o más grande.");
                        return null;
                    }
                    f.toggle(id);
                    manager.save();
                }
                case "contorno" -> {
                    f.showBorder = !f.showBorder;
                    manager.save();
                }
                case "particulas" -> {
                    f.showParticles = !f.showParticles;
                    manager.save();
                }
                case "particula" -> {
                    for (String p : ParticleBorder.availableParticles()) {
                        if (!p.equals(arg)) continue;
                        f.borderParticle = p;
                        f.showParticles = true;
                        manager.save();
                    }
                }
                case "densidad" -> {
                    f.particleDensity = Math.max(1, Math.min(200, f.particleDensity + (arg.equals("+") ? 5 : -5)));
                    manager.save();
                }
                case "msg" -> {
                    boolean welcome = arg.equals("entrar");
                    if (welcome) f.showWelcome = !f.showWelcome;
                    else f.showLeave = !f.showLeave;
                    manager.save();
                }
                case "editar" -> PadServer.put(player, "protecciones.editar", arg);
                case "texto" -> {
                    String what = PadServer.get(player, "protecciones.editar", "");
                    int max = ClaimConfig.get().maxWelcomeLength;
                    String msg = text.length() > max ? text.substring(0, max) : text;
                    if (what.equals("entrar")) {
                        f.welcomeMessage = msg;
                        f.showWelcome = !msg.isBlank();
                    } else if (what.equals("salir")) {
                        f.leaveMessage = msg;
                        f.showLeave = !msg.isBlank();
                    } else if (what.equals("grupo")) {
                        TFPadNet.notice(player, ClaimMenuHandler.inviteToGroup(player, c, msg, ""));
                    } else if (what.equals("invitar")) {
                        TFPadNet.notice(player, ClaimMenuHandler.inviteToGroup(player, c, null, msg));
                    }
                    PadServer.put(player, "protecciones.editar", null);
                    manager.save();
                    if (what.equals("entrar") || what.equals("salir")) TFPadNet.notice(player, "Mensaje guardado.");
                }
                case "noeditar" -> PadServer.put(player, "protecciones.editar", null);
                case "disolver" -> {
                    ClaimGroup g = manager.getGroupOf(c);
                    if (g == null || !c.isGroupMother() || !PadServer.confirm(player, "proteccion.disolver")) return null;
                    manager.dissolveGroupBreaking(g.getGroupId());
                    TFPadNet.notice(player, "Grupo disuelto.");
                }
                case "anadir" -> addMember(player, c, text.trim());
                case "quitar" -> {
                    UUID who = uuid(arg);
                    if (who == null || !c.isMember(who) || !PadServer.confirm(player, "proteccion.quitar:" + arg)) return null;
                    String name = PlayerLookup.nameOf(player.server, who);
                    c.removeMember(who);
                    manager.save();
                    tell(player.server.getPlayerList().getPlayer(who), who, "[Protección] Ya no eres miembro de la zona de " + player.getName().getString(), ChatFormatting.YELLOW);
                    TFPadNet.notice(player, name + " ya no es miembro.");
                }
                case "banear" -> ban(player, c, text.trim());
                case "desbanear" -> {
                    UUID who = uuid(arg);
                    if (who == null || !c.isBanned(who)) return null;
                    c.unbanPlayer(who);
                    manager.save();
                    TFPadNet.notice(player, PlayerLookup.nameOf(player.server, who) + " ya puede entrar.");
                }
                case "eliminar" -> {
                    if (!PadServer.confirm(player, "proteccion.eliminar:" + c.getClaimId())) return null;
                    delete(player, c);
                    return "";
                }
                default -> {
                }
            }
            return null;
        }
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void tell(ServerPlayer online, UUID who, String text, ChatFormatting color) {
        Component msg = Component.literal(text).withStyle(color);
        if (online != null) online.displayClientMessage(msg, false);
        else ClaimManager.getInstance().queueMessage(who, msg);
    }

    private static void addMember(ServerPlayer player, Claim c, String name) {
        if (name.isEmpty()) return;
        UUID id = c.getClaimId();
        PlayerLookup.resolveAsync(player.getServer(), name, r -> {
            if (player.hasDisconnected()) return;
            Claim claim = ClaimManager.getInstance().findClaimById(id);
            if (claim == null) return;
            if (r == null) {
                TFPadNet.notice(player, "No encuentro a «" + name + "». Tiene que haber entrado alguna vez al servidor.");
            } else if (claim.isOwner(r.id())) {
                TFPadNet.notice(player, "Ese jugador es el dueño.");
            } else if (claim.isMember(r.id())) {
                TFPadNet.notice(player, r.name() + " ya es miembro.");
            } else {
                int max = ClaimConfig.get().maxMembersPerClaim;
                if (max > 0 && claim.getMembers().size() >= max) {
                    TFPadNet.notice(player, "Esta zona ya tiene el máximo de miembros (" + max + ").");
                } else {
                    if (claim.isBanned(r.id())) claim.unbanPlayer(r.id());
                    claim.addMember(r.id(), r.name());
                    ClaimManager.getInstance().save();
                    TFPadNet.notice(player, r.name() + " ahora es miembro de la zona.");
                    tell(r.online(), r.id(), "[Protección] Eres miembro de la zona de " + player.getName().getString(), ChatFormatting.AQUA);
                }
            }
            PadServer.refresh(player, "protecciones", "z:" + id + ":m");
        });
    }

    private static void ban(ServerPlayer player, Claim c, String name) {
        if (name.isEmpty()) return;
        UUID id = c.getClaimId();
        PlayerLookup.resolveAsync(player.getServer(), name, r -> {
            if (player.hasDisconnected()) return;
            Claim claim = ClaimManager.getInstance().findClaimById(id);
            if (claim == null) return;
            if (r == null) {
                TFPadNet.notice(player, "No encuentro a «" + name + "».");
            } else if (claim.isOwner(r.id())) {
                TFPadNet.notice(player, "No puedes banear al dueño.");
            } else {
                claim.banPlayer(r.id());
                ClaimManager.getInstance().save();
                TFPadNet.notice(player, r.name() + " ya no puede entrar en la zona.");
                tell(r.online(), r.id(), "[!] Has sido baneado de una zona de " + player.getName().getString(), ChatFormatting.RED);
            }
            PadServer.refresh(player, "protecciones", "z:" + id + ":b");
        });
    }

    /** Quita la zona y devuelve la piedra al inventario (como «Eliminar zona» del menú). */
    private static void delete(ServerPlayer player, Claim c) {
        ServerLevel level = null;
        for (ServerLevel l : player.server.getAllLevels()) if (l.dimension().location().toString().equals(c.getWorld())) level = l;
        if (level == null) {
            TFPadNet.notice(player, "Ese mundo no existe.");
            return;
        }
        ClaimTier tier = c.getTier();
        BlockPos pos = c.getCenter();
        if (tier != null && ClaimBlocks.isClaimConcreteForTier(level.getBlockState(pos).getBlock(), tier)) level.destroyBlock(pos, false);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 1.0F);
        ClaimManager.getInstance().removeClaim(level, pos);
        if (tier != null) {
            ItemStack stone = ClaimBlocks.createTierItem(tier, 1);
            if (!player.getInventory().add(stone)) player.drop(stone, false);
        }
        TFPadNet.notice(player, "Zona eliminada. Tienes la piedra en el inventario.");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static PadView list(ServerPlayer player) {
        PadView.Builder b = PadView.of("protecciones");
        ClaimManager manager = ClaimManager.getInstance();
        List<Claim> mine = manager.getClaimsOf(player.getUUID());
        int max = ClaimConfig.get().maxClaimsPerPlayer;
        Claim here = manager.getClaimAt(player.level(), player.blockPosition());
        if (here != null && !here.isOwner(player.getUUID())) {
            b.header("Estás en la zona de " + here.getOwnerName() + (here.isMember(player.getUUID()) ? " (eres miembro)." : "."));
        } else {
            b.header(mine.isEmpty() ? "" : "Tus zonas: " + mine.size() + (max > 0 ? " de " + max : "") + ".");
        }
        // invitaciones a grupos de otros y el grupo en el que estás
        for (ClaimMenuHandler.MergeInvite inv : ClaimMenuHandler.invitesFor(player.getUUID())) {
            b.row(new PadView.Row(new ItemStack(Items.SLIME_BALL), "Invitación: «" + inv.groupName() + "»", GOLD,
                    List.of(inv.inviterName() + " te invita a unir tus piedras a su grupo."), -1, "",
                    PadView.Btn.of("ACEPTAR", "inv_si:" + inv.code(), PadView.GREEN), PadView.Btn.of("NO", "inv_no:" + inv.code(), PadView.RED)).selected(true));
        }
        ClaimGroup joined = manager.getGroupByRegistered(player.getUUID());
        if (joined != null && !player.getUUID().equals(joined.getMotherOwnerId())) {
            boolean sure = PadServer.confirming(player, "protecciones.salirgrupo");
            b.row(new PadView.Row(new ItemStack(Items.SLIME_BALL), "Grupo «" + joined.getName() + "»", TEXT,
                    List.of("Tus piedras dentro de su zona se unen a ella."), -1, "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "SALIR", "salirgrupo", PadView.RED), null));
        }
        for (Claim c : mine) {
            List<String> lines = new ArrayList<>();
            lines.add(dim(c.getWorld()) + " · " + c.getX() + ", " + c.getY() + ", " + c.getZ());
            int m = c.getMembers().size();
            lines.add(m == 0 ? "Sin miembros" : m + (m == 1 ? " miembro" : " miembros"));
            boolean inside = c.equals(here);
            b.row(new PadView.Row(icon(c), title(c), TEXT, lines, -1, inside ? "ESTÁS AQUÍ" : "", null, null)
                    .clickable("tab:z:" + c.getClaimId() + ":a").selected(inside));
        }
        if (here != null && !here.isOwner(player.getUUID()) && player.hasPermissions(2)) {
            b.row(new PadView.Row(icon(here), "Zona de " + here.getOwnerName(), GOLD, List.of("Staff: puedes administrarla."), -1, "AQUÍ", null, null)
                    .clickable("tab:z:" + here.getClaimId() + ":a"));
        }
        b.empty("Sin zonas. Coloca una piedra de protección.");
        return b.build();
    }

    private static PadView zone(ServerPlayer player, Claim c, String section) {
        String base = "z:" + c.getClaimId() + ":";
        int members = c.getMembers().size(), bans = c.getBannedPlayers().size();
        PadView.Builder b = PadView.of("protecciones").tab(base + "a", "AJUSTES")
                .tab(base + "m", members > 0 ? "MIEMBROS (" + members + ")" : "MIEMBROS")
                .tab(base + "b", bans > 0 ? "BANEOS (" + bans + ")" : "BANEOS")
                .tab(base + "p", "PARTÍCULAS").tab(base + "x", "MÁS").selected(base + section);
        if (c.getGroupId() != null && !c.isGroupMother()) {
            Claim mother = c.getMother();
            b.header("Esta piedra es del grupo de " + (mother != null ? mother.getOwnerName() : "?") + ": se administra desde su piedra principal.");
            b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
            return b.build();
        }
        String id = c.getClaimId().toString();
        switch (section) {
            case "m" -> members(player, b, c, id);
            case "b" -> bans(player, b, c, id);
            case "p" -> particles(b, c, id);
            case "x" -> more(player, b, c, id);
            default -> settings(b, c, id);
        }
        b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
        return b.build();
    }

    private static void settings(PadView.Builder b, Claim c, String id) {
        b.header(title(c) + " · " + dim(c.getWorld()) + " · " + c.getX() + ", " + c.getY() + ", " + c.getZ());
        ClaimFlags f = c.getFlags();
        int level = paidLevel(c.getTier());
        for (Section s : SECTIONS) {
            b.text(s.title, GOLD, List.of());
            for (Flag flag : s.flags) {
                int need = required(flag.id);
                boolean locked = need > 0 && level < need;
                boolean on = f.get(flag.id);
                // el botón dice cómo está (y al pulsarlo cambia); debajo, lo que pasa ahora mismo
                PadView.Btn btn = locked ? PadView.Btn.off(requiredLabel(need))
                        : PadView.Btn.of(on ? flag.on : flag.off, "flag:" + id + ":" + flag.id.name(), on ? PadView.GREEN : PadView.GRAY);
                String now = locked ? "Pide una zona de " + requiredLabel(need).toLowerCase(Locale.ROOT) + ": " + flag.onDesc.toLowerCase(Locale.ROOT)
                        : (on ? flag.onDesc : flag.offDesc);
                b.row(new PadView.Row(new ItemStack(flag.icon), flag.name, locked ? MUTED : TEXT, List.of(now), -1, "", btn, null));
            }
        }
    }

    private static void members(ServerPlayer player, PadView.Builder b, Claim c, String id) {
        int max = ClaimConfig.get().maxMembersPerClaim;
        b.header("Pueden construir y usarlo todo." + (max > 0 ? " Máximo " + max + "." : ""));
        List<UUID> ids = c.getMembers();
        for (int i = 0; i < ids.size(); i++) {
            UUID who = ids.get(i);
            String name = i < c.getMemberNames().size() ? c.getMemberNames().get(i) : PlayerLookup.nameOf(player.server, who);
            boolean online = player.server.getPlayerList().getPlayer(who) != null;
            boolean sure = PadServer.confirming(player, "proteccion.quitar:" + who);
            b.row(new PadView.Row(PadClans.head(who, name), name, TEXT, List.of(online ? "Conectado" : "Desconectado"), -1, "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "QUITAR", "quitar:" + id + ":" + who, PadView.RED), null));
        }
        b.empty("Sin miembros.");
        b.input("anadir:" + id, "NOMBRE DEL JUGADOR", 16, "AÑADIR");
    }

    private static void bans(ServerPlayer player, PadView.Builder b, Claim c, String id) {
        b.header("No pueden entrar: la barrera los saca de la zona.");
        for (UUID who : c.getBannedPlayers()) {
            b.row(new PadView.Row(new ItemStack(Items.IRON_BARS), PlayerLookup.nameOf(player.server, who), TEXT, List.of(), -1, "",
                    PadView.Btn.of("DESBANEAR", "desbanear:" + id + ":" + who, PadView.GREEN), null));
        }
        b.empty("No hay nadie baneado.");
        b.input("banear:" + id, "NOMBRE DEL JUGADOR", 16, "BANEAR");
    }

    private static void particles(PadView.Builder b, Claim c, String id) {
        ClaimFlags f = c.getFlags();
        b.header("Partículas " + (f.showParticles ? "encendidas" : "apagadas") + " · densidad " + f.particleDensity + ".");
        for (String p : ParticleBorder.availableParticles()) {
            boolean sel = p.equals(f.borderParticle);
            b.cell(new ItemStack(ClaimParticleMenuHandler.iconFor(p)), ParticleBorder.particleLabel(p).toUpperCase(Locale.ROOT), sel ? GOLD : TEXT,
                    "particula:" + id + ":" + p, sel);
        }
        b.footer(PadView.Btn.of("MENOS", "densidad:" + id + ":-", PadView.GRAY));
        b.footer(PadView.Btn.of("MÁS", "densidad:" + id + ":+", PadView.GRAY));
        b.footer(PadView.Btn.of(f.showParticles ? "APAGAR" : "ENCENDER", "particulas:" + id, f.showParticles ? PadView.RED : PadView.GREEN));
    }

    private static void more(ServerPlayer player, PadView.Builder b, Claim c, String id) {
        ClaimFlags f = c.getFlags();
        String editing = PadServer.get(player, "protecciones.editar", "");
        b.row(new PadView.Row(new ItemStack(Items.MAP), "Ver el contorno", TEXT,
                List.of(f.showBorder ? "Se ven las líneas del borde de la zona." : "No se ven las líneas del borde."), -1, "",
                PadView.Btn.of(f.showBorder ? "ACTIVADO" : "DESACTIVADO", "contorno:" + id, f.showBorder ? PadView.GREEN : PadView.GRAY), null));
        b.row(new PadView.Row(new ItemStack(Items.OAK_HANGING_SIGN), "Mensaje al entrar", TEXT,
                List.of(f.welcomeMessage.isBlank() ? "Sin mensaje." : "«" + f.welcomeMessage + "»"), -1, "",
                PadView.Btn.of(f.showWelcome ? "ACTIVADO" : "DESACTIVADO", "msg:" + id + ":entrar", f.showWelcome ? PadView.GREEN : PadView.GRAY),
                PadView.Btn.of("EDITAR", "editar:" + id + ":entrar", PadView.BLUE)).selected(editing.equals("entrar")));
        b.row(new PadView.Row(new ItemStack(Items.DARK_OAK_HANGING_SIGN), "Mensaje al salir", TEXT,
                List.of(f.leaveMessage.isBlank() ? "Sin mensaje." : "«" + f.leaveMessage + "»"), -1, "",
                PadView.Btn.of(f.showLeave ? "ACTIVADO" : "DESACTIVADO", "msg:" + id + ":salir", f.showLeave ? PadView.GREEN : PadView.GRAY),
                PadView.Btn.of("EDITAR", "editar:" + id + ":salir", PadView.BLUE)).selected(editing.equals("salir")));
        ClaimGroup g = ClaimManager.getInstance().getGroupOf(c);
        if (g == null) {
            b.row(new PadView.Row(new ItemStack(Items.SLIME_BALL), "Unir zonas en un grupo", TEXT,
                    List.of("Las piedras de los invitados dentro de esta zona se unen a ella."), -1, "",
                    PadView.Btn.of("CREAR", "editar:" + id + ":grupo", PadView.BLUE), null).selected(editing.equals("grupo")));
        } else {
            boolean sure = PadServer.confirming(player, "proteccion.disolver");
            b.row(new PadView.Row(new ItemStack(Items.SLIME_BALL), "Grupo «" + g.getName() + "»", TEXT,
                    List.of(g.getRegisteredPlayers().size() + " jugadores registrados."), -1, "",
                    PadView.Btn.of("INVITAR", "editar:" + id + ":invitar", PadView.BLUE),
                    PadView.Btn.of(sure ? "¿SEGURO?" : "DISOLVER", "disolver:" + id, PadView.RED)).selected(editing.equals("invitar")));
        }
        boolean sure = PadServer.confirming(player, "proteccion.eliminar:" + c.getClaimId());
        b.row(new PadView.Row(new ItemStack(Items.TNT), "Eliminar la zona", 0xC8323C, List.of("Se quita la protección y la piedra vuelve a tu inventario."),
                -1, "", PadView.Btn.of(sure ? "¿SEGURO?" : "ELIMINAR", "eliminar:" + id, PadView.RED), null));
        switch (editing) {
            case "entrar" -> b.input("texto:" + id, "MENSAJE AL ENTRAR", ClaimConfig.get().maxWelcomeLength, "GUARDAR");
            case "salir" -> b.input("texto:" + id, "MENSAJE AL SALIR", ClaimConfig.get().maxWelcomeLength, "GUARDAR");
            case "grupo" -> b.input("texto:" + id, "NOMBRE DEL GRUPO", 32, "CREAR");
            case "invitar" -> b.input("texto:" + id, "JUGADORES (CONECTADOS)", 64, "INVITAR");
            default -> {
            }
        }
        if (!editing.isEmpty()) b.footer(PadView.Btn.of("CANCELAR", "noeditar:" + id, PadView.GRAY));
    }
}
