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
 * Protección (TF Claims) dentro del pad. La lista de tus zonas y, en cada una: AJUSTES (cada opción es una pregunta
 * con SÍ y NO; el que vale ahora, en verde; y las ventajas que piden zona grande), MIEMBROS, BANEOS, PARTÍCULAS (rejilla para elegir y
 * densidad) y MÁS (mensajes de entrada y salida, contorno, grupo y eliminar la zona). Todo sin chat. Lo administra el
 * dueño (y el staff). Pestañas: "" la lista; "z:&lt;id&gt;:a|m|b|p|x" una zona.
 */
public final class PadClaims {
    public static final PadServer.App APP = new App();

    private static final int TEXT = 0x18265C, MUTED = 0x7E8CA8, GOLD = 0xC27A10;

    /**
     * Una opción de la zona, como PREGUNTA con dos botones, SÍ y NO: el que vale ahora se enciende (verde) y el otro
     * queda gris; se pulsa el otro para cambiarla. yesIsOn: si «SÍ» es la opción encendida en ClaimFlags (en las que
     * protegen, encendida = los de fuera NO pueden, así que «SÍ» es apagada).
     */
    private record Flag(FlagId id, String question, String note, boolean yesIsOn, Item icon) {}

    private static Flag can(FlagId id, String question, Item icon) {
        return new Flag(id, question, "", false, icon);
    }

    private static Flag can(FlagId id, String question, String note, Item icon) {
        return new Flag(id, question, note, false, icon);
    }

    private static Flag want(FlagId id, String question, Item icon) {
        return new Flag(id, question, "", true, icon);
    }

    private static Flag want(FlagId id, String question, String note, Item icon) {
        return new Flag(id, question, note, true, icon);
    }

    private record Section(String title, List<Flag> flags) {}

    private static final List<Section> SECTIONS = List.of(
            new Section("Los de fuera de tu zona (tú y tus miembros pueden todo)", List.of(
                    can(FlagId.BUILDING, "¿Pueden construir?", Items.BRICKS),
                    can(FlagId.BREAKING, "¿Pueden romper bloques?", Items.IRON_PICKAXE),
                    can(FlagId.FLUIDS, "¿Pueden poner agua o lava?", Items.WATER_BUCKET),
                    can(FlagId.TREE_CHOPPING, "¿Pueden talar árboles?", Items.OAK_SAPLING),
                    can(FlagId.CROP_HARVEST, "¿Pueden cosechar?", Items.WHEAT),
                    can(FlagId.ANIMAL_KILLING, "¿Pueden matar tus animales?", Items.LEAD),
                    can(FlagId.CHEST_ACCESS, "¿Pueden abrir cofres?", "Y barriles.", Items.CHEST),
                    can(FlagId.DOORS_ACCESS, "¿Pueden usar puertas?", "Y botones, palancas y placas.", Items.OAK_DOOR),
                    can(FlagId.ANVIL_USE, "¿Pueden usar yunques?", Items.ANVIL),
                    can(FlagId.SIGN_EDITING, "¿Pueden editar letreros?", Items.OAK_SIGN),
                    can(FlagId.ITEM_USE, "¿Pueden usar objetos?", Items.BUCKET),
                    can(FlagId.ENTITY_INTERACT, "¿Pueden usar aldeanos?", "Y soportes de armadura y marcos.", Items.ARMOR_STAND),
                    can(FlagId.ENDER_PEARL, "¿Pueden entrar con perlas?", "Lanzando perlas de ender.", Items.ENDER_PEARL),
                    can(FlagId.BLOCK_ALL_INTERACT, "¿Pueden tocar algo?", "Si dices NO, no tocan NADA de la zona.", Items.BARRIER))),
            new Section("La zona", List.of(
                    can(FlagId.EXPLOSIONS, "¿Las explosiones rompen?", "TNT y creepers.", Items.TNT),
                    can(FlagId.FIRE, "¿El fuego quema bloques?", "Y se propaga.", Items.FLINT_AND_STEEL),
                    can(FlagId.TRAMPLING, "¿Pisar rompe los cultivos?", Items.WHEAT_SEEDS),
                    want(FlagId.PUBLIC_MODE, "¿Modo visita?", "Todos pueden entrar a mirar; nadie de fuera toca nada.", Items.SPYGLASS))),
            new Section("Mobs y PvP", List.of(
                    can(FlagId.MOB_SPAWN, "¿Salen monstruos?", "Zombis, esqueletos, creepers…", Items.ZOMBIE_HEAD),
                    can(FlagId.ALL_MOB_SPAWN, "¿Sale algún mob?", "Cualquiera, también de otros mods.", Items.SPAWNER),
                    can(FlagId.PASSIVE_MOB_SPAWN, "¿Salen animales?", "Y peces (los aldeanos sí salen).", Items.EGG),
                    can(FlagId.MOB_DAMAGE, "¿Los mobs hacen daño?", Items.SHIELD),
                    want(FlagId.BURN_HOSTILES, "¿Arden los monstruos?", "Los que entran en la zona.", Items.BLAZE_POWDER),
                    want(FlagId.PVP, "¿Prohibir el PvP?", "Nadie se puede atacar aquí.", Items.IRON_SWORD),
                    want(FlagId.PVP_ALL, "¿PvP libre?", "Todos se pueden atacar aquí.", Items.DIAMOND_SWORD),
                    want(FlagId.ALERTS, "¿Avisarte si entra alguien?", Items.BELL))),
            new Section("Ventajas de zona grande (para ti y tus miembros)", List.of(
                    want(FlagId.EFFECT_REGEN, "¿Regeneración?", "Os curáis solos en la zona.", Items.GOLDEN_APPLE),
                    want(FlagId.EFFECT_RESIST, "¿Resistencia?", "Recibís menos daño en la zona.", Items.IRON_CHESTPLATE),
                    want(FlagId.EFFECT_SPEED, "¿Velocidad?", "Vais más rápido en la zona.", Items.SUGAR),
                    want(FlagId.ALLOW_FLIGHT, "¿Volar?", "Voláis dentro de la zona.", Items.FEATHER))));

    /** Los dos botones de una pregunta: el que vale ahora, verde; el otro, gris (pulsarlo la cambia). */
    private static PadView.Btn answer(String label, boolean chosen, String action) {
        return PadView.Btn.of(label, action, chosen ? PadView.GREEN : PadView.GRAY);
    }

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
                        id = FlagId.valueOf(arg.split(":")[0]);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    int need = required(id);
                    if (need > 0 && paidLevel(c.getTier()) < need) {
                        TFPadNet.notice(player, "Necesita una zona de " + requiredLabel(need).toLowerCase(Locale.ROOT) + " o más grande.");
                        return null;
                    }
                    // «flag:<zona>:<opción>:1|0» pone la opción encendida o apagada (no la alterna: pulsar el botón que
                    // ya está elegido no cambia nada)
                    String[] v = arg.split(":");
                    if (v.length > 1) f.set(id, v[1].equals("1"));
                    else f.toggle(id);
                    manager.save();
                }
                case "contorno" -> {
                    f.showBorder = arg.isEmpty() ? !f.showBorder : arg.equals("1");
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
                case "msg" -> { // msg:<zona>:entrar|salir:1|0
                    String[] v = arg.split(":");
                    boolean welcome = v[0].equals("entrar");
                    boolean on = v.length > 1 ? v[1].equals("1") : !(welcome ? f.showWelcome : f.showLeave);
                    if (welcome) f.showWelcome = on;
                    else f.showLeave = on;
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
                if (locked) {
                    b.row(new PadView.Row(new ItemStack(flag.icon), flag.question, MUTED,
                            List.of("Necesita una zona de " + requiredLabel(need).toLowerCase(Locale.ROOT) + " o más grande."), -1, "",
                            PadView.Btn.off(requiredLabel(need)), null));
                    continue;
                }
                boolean yes = f.get(flag.id) == flag.yesIsOn;
                String act = "flag:" + id + ":" + flag.id.name() + ":";
                b.row(new PadView.Row(new ItemStack(flag.icon), flag.question, TEXT, flag.note.isEmpty() ? List.of() : List.of(flag.note), -1, "",
                        answer("SÍ", yes, act + (flag.yesIsOn ? "1" : "0")), answer("NO", !yes, act + (flag.yesIsOn ? "0" : "1"))));
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
        b.row(new PadView.Row(new ItemStack(Items.MAP), "¿Se ve el contorno de la zona?", TEXT, List.of(), -1, "",
                answer("SÍ", f.showBorder, "contorno:" + id + ":1"), answer("NO", !f.showBorder, "contorno:" + id + ":0")));
        b.row(new PadView.Row(new ItemStack(Items.OAK_HANGING_SIGN), "¿Mensaje al entrar?", TEXT,
                List.of(f.welcomeMessage.isBlank() ? "Sin mensaje escrito." : "«" + f.welcomeMessage + "»"), -1, "",
                answer("SÍ", f.showWelcome, "msg:" + id + ":entrar:1"), answer("NO", !f.showWelcome, "msg:" + id + ":entrar:0")));
        b.row(new PadView.Row(new ItemStack(Items.WRITABLE_BOOK), "Escribir el mensaje al entrar", TEXT, List.of(), -1, "",
                PadView.Btn.of("EDITAR", "editar:" + id + ":entrar", PadView.BLUE), null).selected(editing.equals("entrar")));
        b.row(new PadView.Row(new ItemStack(Items.DARK_OAK_HANGING_SIGN), "¿Mensaje al salir?", TEXT,
                List.of(f.leaveMessage.isBlank() ? "Sin mensaje escrito." : "«" + f.leaveMessage + "»"), -1, "",
                answer("SÍ", f.showLeave, "msg:" + id + ":salir:1"), answer("NO", !f.showLeave, "msg:" + id + ":salir:0")));
        b.row(new PadView.Row(new ItemStack(Items.WRITABLE_BOOK), "Escribir el mensaje al salir", TEXT, List.of(), -1, "",
                PadView.Btn.of("EDITAR", "editar:" + id + ":salir", PadView.BLUE), null).selected(editing.equals("salir")));
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
