package net.tierrasfantasticas.tfclient.claims;

import net.tierrasfantasticas.tfclient.claims.chat.ChatPromptRouter;
import net.tierrasfantasticas.tfclient.claims.command.ClaimAdminCommands;
import net.tierrasfantasticas.tfclient.claims.command.ClaimCommands;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.GlobalFlags;
import net.tierrasfantasticas.tfclient.claims.event.BlockProtectionEvents;
import net.tierrasfantasticas.tfclient.claims.event.EntityProtectionEvents;
import net.tierrasfantasticas.tfclient.claims.event.PassiveEffectsManager;
import net.tierrasfantasticas.tfclient.claims.event.PlayerTracker;
import net.tierrasfantasticas.tfclient.claims.gui.AdminClaimSubMenuHandler;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimMenuHandler;
import net.tierrasfantasticas.tfclient.claims.item.ClaimItems;
import net.tierrasfantasticas.tfclient.claims.net.ClaimBordersPacket;
import net.tierrasfantasticas.tfclient.claims.net.ClaimNetwork;
import net.tierrasfantasticas.tfclient.claims.render.ParticleBorder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * TF Claims: protecciones de zona con piedras de 10 tamaños (antes el mod Fantastic Claims 7.9.2, ahora dentro de
 * TF Client). La zona va de la altura de la piedra menos su altura hacia abajo hasta el techo del mundo (hacia arriba
 * no tiene límite). Datos y configuración en el mundo (claimblocks_data.json, claimblocks_config.json,
 * global_flags.json: los mismos ficheros de Fantastic Claims, así las zonas que ya había siguen igual).
 * Comandos: /tf claims ... (jugadores) y /tf web claims ... (staff).
 */
public class TFClaims {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static int particleCounter = 0;
    private static final Set<UUID> CHECKED_STONES = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> lastBorderHash = new ConcurrentHashMap<UUID, Integer>();
    private static final Map<UUID, Long> lastBorderSend = new ConcurrentHashMap<UUID, Long>();
    private static final long BORDER_KEEPALIVE_MS = 2000L;

    private static int borderHash(List<double[]> list) {
        int i = 1;
        for (double[] adouble : list) {
            i = 31 * i + Arrays.hashCode(adouble);
        }
        return i;
    }

    private TFClaims() {
    }

    /** Lo llama TFClient al arrancar: bloques y objetos, red y eventos. */
    public static void init(IEventBus ieventbus) {
        ClaimItems.register(ieventbus);
        ClaimNetwork.init();
        MinecraftForge.EVENT_BUS.register((Object)new TFClaims());
        MinecraftForge.EVENT_BUS.register((Object)new BlockProtectionEvents());
        MinecraftForge.EVENT_BUS.register((Object)new EntityProtectionEvents());
        MinecraftForge.EVENT_BUS.register((Object)new PlayerTracker());
        MinecraftForge.EVENT_BUS.addListener(ClaimItems::onMissingMappings);
        LOGGER.info("[TF Claims] Eventos, bloques y red registrados.");
    }

    /** /tf claims merge accept|reject <codigo> | leave: los botones de las invitaciones a unir zonas. */
    public static LiteralArgumentBuilder<CommandSourceStack> mergeCommand() {
        return Commands.literal("merge").then(Commands.literal("accept").then(Commands.argument("code", StringArgumentType.word()).executes(commandcontext -> {
            ServerPlayer serverplayer = commandcontext.getSource().getPlayerOrException();
            ClaimMenuHandler.acceptMerge(serverplayer, StringArgumentType.getString(commandcontext, "code"));
            return 1;
        }))).then(Commands.literal("reject").then(Commands.argument("code", StringArgumentType.word()).executes(commandcontext -> {
            ServerPlayer serverplayer = commandcontext.getSource().getPlayerOrException();
            ClaimMenuHandler.rejectMerge(serverplayer, StringArgumentType.getString(commandcontext, "code"));
            return 1;
        }))).then(Commands.literal("leave").executes(commandcontext -> {
            ServerPlayer serverplayer = commandcontext.getSource().getPlayerOrException();
            ClaimMenuHandler.leaveMerge(serverplayer);
            return 1;
        }));
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent serverstartedevent) {
        CHECKED_STONES.clear();
        ClaimManager.getInstance().load(serverstartedevent.getServer());
        GlobalFlags.getInstance().load(serverstartedevent.getServer());
        LOGGER.info("[TF Claims] Datos cargados.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent serverstoppingevent) {
        ClaimManager.getInstance().saveNow();
        GlobalFlags.getInstance().save(serverstoppingevent.getServer());
        LOGGER.info("[TF Claims] Datos guardados al apagar.");
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent playerloggedinevent) {
        Player player = playerloggedinevent.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer serverplayer = (ServerPlayer)player;
            ClaimManager.getInstance().flushPendingTo(serverplayer);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent playerloggedoutevent) {
        ClaimMenuHandler.clearPrompt(playerloggedoutevent.getEntity().getUUID());
        lastBorderHash.remove(playerloggedoutevent.getEntity().getUUID());
        lastBorderSend.remove(playerloggedoutevent.getEntity().getUUID());
        AdminClaimSubMenuHandler.clearPendingTransfer(playerloggedoutevent.getEntity().getUUID());
        ChatPromptRouter.onPlayerDisconnect(playerloggedoutevent.getEntity().getUUID());
        PlayerTracker.onDisconnect(playerloggedoutevent.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent servertickevent) {
        MinecraftServer minecraftserver;
        if (servertickevent.phase == TickEvent.Phase.END && (minecraftserver = ServerLifecycleHooks.getCurrentServer()) != null) {
            PlayerTracker.tick(minecraftserver);
            BlockProtectionEvents.tickFireSweep(minecraftserver);
            PassiveEffectsManager.tick(minecraftserver);
            if (++particleCounter % ClaimConfig.get().particleIntervalTicks == 0) {
                TFClaims.renderClaimParticles(minecraftserver);
            }
            if (particleCounter % ClaimConfig.get().borderIntervalTicks == 0) {
                TFClaims.sendBorderPackets(minecraftserver);
            }
            if (particleCounter % 100 == 0) {
                TFClaims.upgradeLegacyStones(minecraftserver);
            }
        }
    }

    /**
     * Las zonas de Fantastic Claims tienen de piedra un concreto: cuando su chunk está cargado se cambia por la piedra
     * de TF Claims de su tamaño (una vez por zona y arranque).
     */
    private static void upgradeLegacyStones(MinecraftServer minecraftserver) {
        for (Claim claim : ClaimManager.getInstance().getAllClaims()) {
            if (CHECKED_STONES.contains(claim.getClaimId()) || claim.getTier() == null) continue;
            ServerLevel serverlevel = null;
            for (ServerLevel level : minecraftserver.getAllLevels()) {
                if (!level.dimension().location().toString().equals(claim.getWorld())) continue;
                serverlevel = level;
                break;
            }
            if (serverlevel == null) {
                CHECKED_STONES.add(claim.getClaimId());
                continue;
            }
            BlockPos blockpos = claim.getCenter();
            if (!serverlevel.hasChunkAt(blockpos)) continue;
            CHECKED_STONES.add(claim.getClaimId());
            Block block = serverlevel.getBlockState(blockpos).getBlock();
            if (block == ClaimBlocks.legacyBlockForTier(claim.getTier()) && block != ClaimBlocks.blockForTier(claim.getTier())) {
                serverlevel.setBlockAndUpdate(blockpos, ClaimBlocks.blockForTier(claim.getTier()).defaultBlockState());
            }
        }
    }

    private static void sendBorderPackets(MinecraftServer minecraftserver) {
        for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
            String s = serverlevel.dimension().location().toString();
            for (ServerPlayer serverplayer : serverlevel.players()) {
                boolean flag1;
                ArrayList<double[]> arraylist = new ArrayList<double[]>();
                HashSet<UUID> hashset = new HashSet<UUID>();
                HashSet<UUID> hashset1 = new HashSet<UUID>();
                Claim claim = ClaimManager.getInstance().getClaimAt((Level)serverlevel, serverplayer.blockPosition());
                if (claim != null && claim.getFlags().showBorder && claim.canModify((Player)serverplayer)) {
                    TFClaims.addBorder(arraylist, claim, serverplayer, s, hashset, hashset1);
                }
                for (Claim claim1 : ClaimManager.getInstance().getClaimsOf(serverplayer.getUUID())) {
                    if (!claim1.getWorld().equals(s) || !claim1.getFlags().showBorder || !ParticleBorder.withinRenderRange(serverplayer, claim1)) continue;
                    TFClaims.addBorder(arraylist, claim1, serverplayer, s, hashset, hashset1);
                }
                int j = TFClaims.borderHash(arraylist);
                Integer integer = lastBorderHash.get(serverplayer.getUUID());
                long i = System.currentTimeMillis();
                Long olong = lastBorderSend.get(serverplayer.getUUID());
                boolean flag = integer == null || integer != j;
                boolean bl = flag1 = olong == null || i - olong >= 2000L;
                if (!flag && !flag1) continue;
                lastBorderHash.put(serverplayer.getUUID(), j);
                lastBorderSend.put(serverplayer.getUUID(), i);
                ClaimNetwork.sendTo(serverplayer, new ClaimBordersPacket(arraylist));
            }
        }
    }

    private static void addBorder(ArrayList<double[]> arraylist, Claim claim, ServerPlayer serverplayer, String s, HashSet<UUID> hashset, HashSet<UUID> hashset1) {
        if (claim.getGroupId() != null) {
            UUID uuid = claim.getGroupId();
            if (hashset1.contains(uuid)) {
                return;
            }
            hashset1.add(uuid);
            TFClaims.addGroupOutline(arraylist, uuid, serverplayer, s);
        } else {
            if (hashset.contains(claim.getClaimId())) {
                return;
            }
            hashset.add(claim.getClaimId());
            arraylist.add(TFClaims.boxOf(claim));
        }
    }

    private static void addGroupOutline(ArrayList<double[]> arraylist, UUID uuid, ServerPlayer serverplayer, String s) {
        ClaimManager claimmanager = ClaimManager.getInstance();
        Claim claim = claimmanager.getMotherClaim(uuid);
        if (claim != null) {
            ArrayList<Claim> arraylist1 = new ArrayList<Claim>();
            for (Claim claim1 : claimmanager.getGroupClaims(uuid)) {
                if (!claim1.getWorld().equals(s)) continue;
                arraylist1.add(claim1);
            }
            if (!arraylist1.isEmpty()) {
                double d3 = claim.getBottom();
                double d0 = claim.getTop();
                float f = 1.0f;
                float f1 = 1.0f;
                float f2 = 1.0f;
                if (claim.getTier() != null) {
                    f = claim.getTier().r;
                    f1 = claim.getTier().g;
                    f2 = claim.getTier().b;
                }
                int i = arraylist1.size();
                int[] aint = new int[i];
                int[] aint1 = new int[i];
                int[] aint2 = new int[i];
                int[] aint3 = new int[i];
                TreeSet<Integer> treeset = new TreeSet<Integer>();
                TreeSet<Integer> treeset1 = new TreeSet<Integer>();
                for (int j = 0; j < i; ++j) {
                    Claim claim2 = (Claim)arraylist1.get(j);
                    int k = claim2.getRadius();
                    aint[j] = claim2.getX() - k;
                    aint1[j] = claim2.getX() + k + 1;
                    aint2[j] = claim2.getZ() - k;
                    aint3[j] = claim2.getZ() + k + 1;
                    treeset.add(aint[j]);
                    treeset.add(aint1[j]);
                    treeset1.add(aint2[j]);
                    treeset1.add(aint3[j]);
                }
                Integer[] ainteger = treeset.toArray(new Integer[0]);
                Integer[] ainteger1 = treeset1.toArray(new Integer[0]);
                int l1 = ainteger.length;
                int l = ainteger1.length;
                if (l1 >= 2 && l >= 2) {
                    boolean[][] aboolean = new boolean[l1 - 1][l - 1];
                    for (int i1 = 0; i1 < l1 - 1; ++i1) {
                        double d1 = (double)(ainteger[i1] + ainteger[i1 + 1]) / 2.0;
                        for (int j1 = 0; j1 < l - 1; ++j1) {
                            double d2 = (double)(ainteger1[j1] + ainteger1[j1 + 1]) / 2.0;
                            boolean flag1 = false;
                            for (int k1 = 0; k1 < i; ++k1) {
                                if (!(d1 >= (double)aint[k1]) || !(d1 < (double)aint1[k1]) || !(d2 >= (double)aint2[k1]) || !(d2 < (double)aint3[k1])) continue;
                                flag1 = true;
                                break;
                            }
                            aboolean[i1][j1] = flag1;
                        }
                    }
                    for (int i2 = 0; i2 < l1; ++i2) {
                        int k2 = 0;
                        while (k2 < l - 1) {
                            boolean flag3;
                            boolean flag = i2 > 0 && aboolean[i2 - 1][k2];
                            boolean bl = flag3 = i2 < l1 - 1 && aboolean[i2][k2];
                            if (flag != flag3) {
                                int i3 = k2;
                                while (k2 < l - 1 && (i2 > 0 && aboolean[i2 - 1][k2]) != (i2 < l1 - 1 && aboolean[i2][k2])) {
                                    ++k2;
                                }
                                arraylist.add(new double[]{(double)ainteger[i2].intValue() - 0.03, d3, ainteger1[i3].intValue(), (double)ainteger[i2].intValue() + 0.03, d0, ainteger1[k2].intValue(), f, f1, f2});
                                continue;
                            }
                            ++k2;
                        }
                    }
                    for (int j2 = 0; j2 < l; ++j2) {
                        int l2 = 0;
                        while (l2 < l1 - 1) {
                            boolean flag4;
                            boolean flag2 = j2 > 0 && aboolean[l2][j2 - 1];
                            boolean bl = flag4 = j2 < l - 1 && aboolean[l2][j2];
                            if (flag2 != flag4) {
                                int j3 = l2;
                                while (l2 < l1 - 1 && (j2 > 0 && aboolean[l2][j2 - 1]) != (j2 < l - 1 && aboolean[l2][j2])) {
                                    ++l2;
                                }
                                arraylist.add(new double[]{ainteger[j3].intValue(), d3, (double)ainteger1[j2].intValue() - 0.03, ainteger[l2].intValue(), d0, (double)ainteger1[j2].intValue() + 0.03, f, f1, f2});
                                continue;
                            }
                            ++l2;
                        }
                    }
                }
            }
        }
    }

    private static boolean covered(List<Claim> list, int i, int j) {
        for (Claim claim : list) {
            if (Math.abs(i - claim.getX()) > claim.getRadius() || Math.abs(j - claim.getZ()) > claim.getRadius()) continue;
            return true;
        }
        return false;
    }

    private static double[] boxOf(Claim claim) {
        int i = claim.getRadius();
        float f = 1.0f;
        float f1 = 1.0f;
        float f2 = 1.0f;
        if (claim.getTier() != null) {
            f = claim.getTier().r;
            f1 = claim.getTier().g;
            f2 = claim.getTier().b;
        }
        return new double[]{claim.getX() - i, claim.getBottom(), claim.getZ() - i, claim.getX() + i + 1, claim.getTop(), claim.getZ() + i + 1, f, f1, f2};
    }

    private static void renderClaimParticles(MinecraftServer minecraftserver) {
        for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
            String s = serverlevel.dimension().location().toString();
            for (ServerPlayer serverplayer : serverlevel.players()) {
                HashSet<UUID> hashset = new HashSet<UUID>();
                Claim claim = ClaimManager.getInstance().getClaimAt((Level)serverlevel, serverplayer.blockPosition());
                if (claim != null && claim.getFlags().showParticles && claim.canModify((Player)serverplayer)) {
                    ParticleBorder.fillClaim(serverlevel, serverplayer, claim);
                    hashset.add(claim.getClaimId());
                }
                for (Claim claim1 : ClaimManager.getInstance().getClaimsOf(serverplayer.getUUID())) {
                    if (hashset.contains(claim1.getClaimId()) || !claim1.getFlags().showParticles || !claim1.getWorld().equals(s) || !ParticleBorder.withinRenderRange(serverplayer, claim1)) continue;
                    ParticleBorder.fillClaim(serverlevel, serverplayer, claim1);
                    hashset.add(claim1.getClaimId());
                }
            }
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent serverchatevent) {
        ClaimMenuHandler.handleChat(serverchatevent);
    }

    @SubscribeEvent
    public void onCommandEvent(CommandEvent commandevent) {
    }
}

