package net.tierrasfantasticas.tfclient.claims.data;

import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.TFClaims;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimGroup;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

public class ClaimManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "claimblocks_data.json";
    private static final String CONFIG_FILE = "claimblocks_config.json";
    private static int MAX_CLAIMS_PER_PLAYER = 0;
    private static ClaimManager INSTANCE;
    private final Map<String, List<Claim>> claimsByWorld = new ConcurrentHashMap<String, List<Claim>>();
    private MinecraftServer server;
    private final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<Component>> pendingMessages = new ConcurrentHashMap<UUID, List<Component>>();
    private final Map<UUID, ClaimGroup> groups = new ConcurrentHashMap<UUID, ClaimGroup>();
    private final Map<UUID, Claim> claimIndex = new ConcurrentHashMap<UUID, Claim>();
    private final AtomicReference<String> pendingWrite = new AtomicReference();
    private static final ExecutorService IO;

    private ClaimManager() {
    }

    public static synchronized ClaimManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClaimManager();
        }
        return INSTANCE;
    }

    public static int getMaxClaimsPerPlayer() {
        return ClaimConfig.get().maxClaimsPerPlayer;
    }

    public static void setMaxClaimsPerPlayer(int i) {
        ClaimConfig.get().maxClaimsPerPlayer = Math.max(0, i);
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public Claim createClaim(Level level, BlockPos blockpos, Player player, ClaimTier claimtier) {
        String s = level.dimension().location().toString();
        Claim claim = Claim.create(player.getUUID(), player.getName().getString(), claimtier, s, blockpos);
        ClaimConfig.get().applyDefaultsTo(claim);
        if (claimtier != null) {
            String s3;
            String s2 = claimtier.id;
            String s12 = claimtier.id;
            switch (s3 = claimtier.id) {
                case "claimstone_500x500": {
                    claim.getFlags().effectRegeneration = true;
                    claim.getFlags().effectResistance = true;
                    claim.getFlags().effectSpeed = true;
                    claim.getFlags().allowFlight = true;
                    break;
                }
                case "claimstone_300x300": {
                    claim.getFlags().effectRegeneration = true;
                    claim.getFlags().effectResistance = true;
                    claim.getFlags().effectSpeed = true;
                    break;
                }
                case "claimstone_250x250": {
                    claim.getFlags().effectRegeneration = true;
                }
            }
        }
        this.claimsByWorld.computeIfAbsent(s, s1 -> Collections.synchronizedList(new ArrayList())).add(claim);
        this.claimIndex.put(claim.getClaimId(), claim);
        this.save();
        return claim;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean removeClaim(Level level, BlockPos blockpos) {
        String s = level.dimension().location().toString();
        List<Claim> list = this.claimsByWorld.get(s);
        if (list == null) {
            return false;
        }
        Claim claim = null;
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim claim1 : list) {
                if (claim1.getX() != blockpos.getX() || claim1.getY() != blockpos.getY() || claim1.getZ() != blockpos.getZ()) continue;
                claim = claim1;
                break;
            }
            if (claim != null) {
                list.remove(claim);
            }
        }
        if (claim != null) {
            this.claimIndex.remove(claim.getClaimId());
            this.onClaimRemoved(claim);
            this.save();
            return true;
        }
        return false;
    }

    private void onClaimRemoved(Claim claim) {
        ClaimGroup claimgroup;
        if (claim.getGroupId() != null && (claimgroup = this.groups.get(claim.getGroupId())) != null && claim.getClaimId().equals(claimgroup.getMotherClaimId())) {
            this.dissolveGroupBreaking(claimgroup.getGroupId());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public int clearClaimsOf(UUID uuid) {
        int i = 0;
        for (Map.Entry<String, List<Claim>> entry : this.claimsByWorld.entrySet()) {
            List<Claim> list = entry.getValue();
            ArrayList<Claim> arraylist = new ArrayList<Claim>();
            List<Claim> list2 = list;
            synchronized (list2) {
                for (Claim claim : list) {
                    if (!claim.isOwner(uuid)) continue;
                    arraylist.add(claim);
                }
            }
            for (Claim claim1 : arraylist) {
                BlockPos blockpos;
                ServerLevel serverlevel;
                if (this.server != null && (serverlevel = this.worldFor(entry.getKey())) != null && ClaimBlocks.isClaimConcreteForTier(serverlevel.getBlockState(blockpos = claim1.getCenter()).getBlock(), claim1.getTier())) {
                    serverlevel.setBlockAndUpdate(blockpos, Blocks.AIR.defaultBlockState());
                }
                List<Claim> list3 = list;
                synchronized (list3) {
                    list.remove(claim1);
                }
                this.claimIndex.remove(claim1.getClaimId());
                this.onClaimRemoved(claim1);
                ++i;
            }
        }
        if (i > 0) {
            this.save();
        }
        return i;
    }

    public boolean transferOwnership(Claim claim, UUID uuid, String s) {
        if (claim != null && uuid != null) {
            claim.setOwner(uuid, s);
            this.save();
            return true;
        }
        return false;
    }

    /** Techo del mundo (altura máxima de construcción) de esa dimensión; 320 si no está cargada. */
    public int topOf(String s) {
        ServerLevel serverlevel = this.worldFor(s);
        return serverlevel != null ? serverlevel.getMaxBuildHeight() : 320;
    }

    private ServerLevel worldFor(String s) {
        if (this.server == null) {
            return null;
        }
        for (ServerLevel serverlevel : this.server.getAllLevels()) {
            if (!serverlevel.dimension().location().toString().equals(s)) continue;
            return serverlevel;
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Claim getClaimAt(Level level, BlockPos blockpos) {
        String s = level.dimension().location().toString();
        List<Claim> list = this.claimsByWorld.get(s);
        if (list == null) {
            return null;
        }
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim claim : list) {
                if (!claim.contains(blockpos)) continue;
                return claim;
            }
            return null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Claim getClaimByCenter(Level level, BlockPos blockpos) {
        String s = level.dimension().location().toString();
        List<Claim> list = this.claimsByWorld.get(s);
        if (list == null) {
            return null;
        }
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim claim : list) {
                if (claim.getX() != blockpos.getX() || claim.getY() != blockpos.getY() || claim.getZ() != blockpos.getZ()) continue;
                return claim;
            }
            return null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean wouldOverlap(Level level, BlockPos blockpos, int i, int j) {
        String s = level.dimension().location().toString();
        List<Claim> list = this.claimsByWorld.get(s);
        if (list == null) {
            return false;
        }
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim claim : list) {
                if (!claim.overlapsWith(blockpos, i, j)) continue;
                return true;
            }
            return false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Claim> overlappingClaims(Level level, BlockPos blockpos, int i, int j) {
        ArrayList<Claim> arraylist = new ArrayList<Claim>();
        String s = level.dimension().location().toString();
        List<Claim> list = this.claimsByWorld.get(s);
        if (list == null) {
            return arraylist;
        }
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim claim : list) {
                if (!claim.overlapsWith(blockpos, i, j)) continue;
                arraylist.add(claim);
            }
            return arraylist;
        }
    }

    public ClaimGroup getGroup(UUID uuid) {
        return uuid == null ? null : this.groups.get(uuid);
    }

    public ClaimGroup getGroupOf(Claim claim) {
        return claim == null ? null : this.getGroup(claim.getGroupId());
    }

    public Claim findClaimById(UUID uuid) {
        return uuid == null ? null : this.claimIndex.get(uuid);
    }

    public Claim getMotherClaim(UUID uuid) {
        ClaimGroup claimgroup = this.getGroup(uuid);
        return claimgroup != null && claimgroup.getMotherClaimId() != null ? this.claimIndex.get(claimgroup.getMotherClaimId()) : null;
    }

    public ClaimGroup createGroup(Claim claim, String s) {
        UUID uuid = UUID.randomUUID();
        ClaimGroup claimgroup = new ClaimGroup(uuid, s, claim.getClaimId(), claim.getOwnerUUID());
        this.groups.put(uuid, claimgroup);
        claim.setGroupId(uuid);
        this.save();
        return claimgroup;
    }

    public void registerPlayer(UUID uuid, UUID uuid1) {
        ClaimGroup claimgroup = this.getGroup(uuid);
        if (claimgroup != null) {
            claimgroup.register(uuid1);
            this.save();
        }
    }

    public boolean isRegistered(UUID uuid, UUID uuid1) {
        ClaimGroup claimgroup = this.getGroup(uuid);
        return claimgroup != null && claimgroup.isRegistered(uuid1);
    }

    public ClaimGroup getGroupByRegistered(UUID uuid) {
        for (ClaimGroup claimgroup : this.groups.values()) {
            if (!claimgroup.isRegistered(uuid)) continue;
            return claimgroup;
        }
        return null;
    }

    public void joinClaimToGroup(Claim claim, UUID uuid) {
        if (claim != null && this.groups.containsKey(uuid)) {
            claim.setGroupId(uuid);
            this.save();
        }
    }

    public List<Claim> getGroupClaims(UUID uuid) {
        ArrayList<Claim> arraylist = new ArrayList<Claim>();
        if (uuid == null) {
            return arraylist;
        }
        for (Claim claim : this.getAllClaims()) {
            if (!uuid.equals(claim.getGroupId())) continue;
            arraylist.add(claim);
        }
        return arraylist;
    }

    public void dissolveGroup(UUID uuid) {
        if (this.groups.remove(uuid) != null) {
            for (Claim claim : this.getAllClaims()) {
                if (!uuid.equals(claim.getGroupId())) continue;
                claim.setGroupId(null);
            }
            this.save();
        }
    }

    public void dissolveGroupBreaking(UUID uuid) {
        ClaimGroup claimgroup = this.groups.get(uuid);
        if (claimgroup != null) {
            Claim claim = this.getMotherClaim(uuid);
            UUID uuid1 = claim != null ? claim.getClaimId() : claimgroup.getMotherClaimId();
            for (Claim claim1 : this.getGroupClaims(uuid)) {
                if (uuid1 != null && claim1.getClaimId().equals(uuid1)) continue;
                this.breakAndReturn(claim1);
            }
            this.groups.remove(uuid);
            for (Claim claim2 : this.getAllClaims()) {
                if (!uuid.equals(claim2.getGroupId())) continue;
                claim2.setGroupId(null);
            }
            this.save();
        }
    }

    public void leaveGroupBreaking(UUID uuid, UUID uuid1) {
        ClaimGroup claimgroup = this.getGroup(uuid);
        if (claimgroup != null) {
            if (uuid1 != null && uuid1.equals(claimgroup.getMotherOwnerId())) {
                this.dissolveGroupBreaking(uuid);
            } else {
                claimgroup.unregister(uuid1);
                for (Claim claim : this.getGroupClaims(uuid)) {
                    if (!claim.isOwner(uuid1)) continue;
                    this.breakAndReturn(claim);
                }
                this.save();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void breakAndReturn(Claim claim) {
        List<Claim> list;
        ServerLevel serverlevel = this.worldFor(claim.getWorld());
        BlockPos blockpos = claim.getCenter();
        ClaimTier claimtier = claim.getTier();
        if (serverlevel != null && claimtier != null && ClaimBlocks.isClaimConcreteForTier(serverlevel.getBlockState(blockpos).getBlock(), claimtier)) {
            serverlevel.setBlockAndUpdate(blockpos, Blocks.AIR.defaultBlockState());
        }
        if (serverlevel != null && claimtier != null) {
            ServerPlayer serverplayer;
            ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
            ServerPlayer serverPlayer = serverplayer = this.server != null && claim.getOwnerUUID() != null ? this.server.getPlayerList().getPlayer(claim.getOwnerUUID()) : null;
            if (serverplayer != null) {
                if (!serverplayer.getInventory().add(itemstack)) {
                    serverplayer.drop(itemstack, false);
                }
            } else {
                serverlevel.addFreshEntity((Entity)new ItemEntity((Level)serverlevel, (double)blockpos.getX() + 0.5, (double)blockpos.getY() + 0.5, (double)blockpos.getZ() + 0.5, itemstack));
            }
        }
        if ((list = this.claimsByWorld.get(claim.getWorld())) != null) {
            List<Claim> list2 = list;
            synchronized (list2) {
                list.remove(claim);
            }
        }
        this.claimIndex.remove(claim.getClaimId());
    }

    public void removePlayerFromGroup(UUID uuid, UUID uuid1) {
        ClaimGroup claimgroup = this.getGroup(uuid);
        if (claimgroup != null) {
            if (uuid1 != null && uuid1.equals(claimgroup.getMotherOwnerId())) {
                this.dissolveGroup(uuid);
            } else {
                claimgroup.unregister(uuid1);
                for (Claim claim : this.getGroupClaims(uuid)) {
                    if (!claim.isOwner(uuid1)) continue;
                    claim.setGroupId(null);
                }
                this.save();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Claim> getAllClaims() {
        ArrayList<Claim> arraylist = new ArrayList<Claim>();
        Iterator<List<Claim>> iterator = this.claimsByWorld.values().iterator();
        while (iterator.hasNext()) {
            List<Claim> list;
            List<Claim> list1;
            List<Claim> list2 = list1 = (list = iterator.next());
            synchronized (list2) {
                arraylist.addAll(list);
            }
        }
        return arraylist;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Claim> getClaimsOf(UUID uuid) {
        ArrayList<Claim> arraylist = new ArrayList<Claim>();
        Iterator<List<Claim>> iterator = this.claimsByWorld.values().iterator();
        while (iterator.hasNext()) {
            List<Claim> list;
            List<Claim> list1;
            List<Claim> list2 = list1 = (list = iterator.next());
            synchronized (list2) {
                for (Claim claim : list) {
                    if (!claim.isOwner(uuid)) continue;
                    arraylist.add(claim);
                }
            }
        }
        return arraylist;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Claim> getClaimsInWorld(String s) {
        List list;
        List list1;
        List list2 = list1 = (list = this.claimsByWorld.getOrDefault(s, Collections.emptyList()));
        synchronized (list2) {
            return new ArrayList<Claim>(list);
        }
    }

    private String snapshotJson() {
        JsonObject jsonobject = new JsonObject();
        JsonArray jsonarray = new JsonArray();
        for (Claim claim : this.getAllClaims()) {
            jsonarray.add((JsonElement)claim.toJson());
        }
        jsonobject.add("claims", (JsonElement)jsonarray);
        JsonArray jsonarray1 = new JsonArray();
        for (ClaimGroup claimgroup : this.groups.values()) {
            jsonarray1.add((JsonElement)claimgroup.toJson());
        }
        jsonobject.add("groups", (JsonElement)jsonarray1);
        return GSON.toJson((JsonElement)jsonobject);
    }

    public void save() {
        if (this.server != null) {
            boolean flag;
            Path path = this.dataFile(this.server);
            String s = this.snapshotJson();
            boolean bl = flag = this.pendingWrite.getAndSet(s) == null;
            if (flag) {
                IO.execute(() -> {
                    String s1 = this.pendingWrite.getAndSet(null);
                    if (s1 != null) {
                        ClaimManager.writeAtomic(path, s1);
                    }
                });
            }
        }
    }

    public void saveNow() {
        if (this.server != null) {
            Path path = this.dataFile(this.server);
            String s = this.snapshotJson();
            this.pendingWrite.set(null);
            ClaimManager.writeAtomic(path, s);
        }
    }

    private static void writeAtomic(Path path, String s) {
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            Path path1 = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(path1, (CharSequence)s, StandardCharsets.UTF_8, new OpenOption[0]);
            if (Files.exists(path, new LinkOption[0])) {
                try {
                    Files.copy(path, path.resolveSibling(path.getFileName().toString() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                }
                catch (IOException iOException) {
                    // empty catch block
                }
            }
            try {
                Files.move(path1, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException atomicmovenotsupportedexception) {
                Files.move(path1, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException ioexception1) {
            TFClaims.LOGGER.error("Could not save claims to " + path, (Throwable)ioexception1);
        }
    }

    public void load(MinecraftServer minecraftserver) {
        Path path1;
        this.server = minecraftserver;
        this.claimsByWorld.clear();
        this.claimIndex.clear();
        this.groups.clear();
        this.loadConfig(minecraftserver);
        Path path = this.dataFile(minecraftserver);
        if (!ClaimManager.looksValid(path) && ClaimManager.looksValid(path1 = path.resolveSibling(path.getFileName().toString() + ".bak"))) {
            TFClaims.LOGGER.warn("[TF Claims] {} no se puede leer; restaurando desde {}", (Object)path, (Object)path1);
            path = path1;
        }
        if (!Files.exists(path, new LinkOption[0])) {
            TFClaims.LOGGER.info("No existing claims file at {}, starting fresh.", (Object)path);
        } else {
            try {
                String s = Files.readString(path, StandardCharsets.UTF_8);
                if (s.isBlank()) {
                    return;
                }
                JsonElement jsonelement = JsonParser.parseString((String)s);
                if (!jsonelement.isJsonObject()) {
                    return;
                }
                JsonArray jsonarray = jsonelement.getAsJsonObject().getAsJsonArray("claims");
                if (jsonarray == null) {
                    return;
                }
                int i = 0;
                int j = 0;
                for (JsonElement jsonelement1 : jsonarray) {
                    JsonObject jsonobject = jsonelement1.getAsJsonObject();
                    boolean flag = !jsonobject.has("radius") && jsonobject.has("tier");
                    Claim claim = Claim.fromJson(jsonobject);
                    this.claimsByWorld.computeIfAbsent(claim.getWorld(), s1 -> Collections.synchronizedList(new ArrayList())).add(claim);
                    this.claimIndex.put(claim.getClaimId(), claim);
                    ++i;
                    if (!flag) continue;
                    ++j;
                }
                JsonArray jsonarray1 = jsonelement.getAsJsonObject().getAsJsonArray("groups");
                if (jsonarray1 != null) {
                    for (JsonElement jsonelement2 : jsonarray1) {
                        ClaimGroup claimgroup = ClaimGroup.fromJson(jsonelement2.getAsJsonObject());
                        this.groups.put(claimgroup.getGroupId(), claimgroup);
                    }
                }
                ArrayList<UUID> arraylist = new ArrayList<UUID>();
                for (ClaimGroup claimgroup1 : this.groups.values()) {
                    if (claimgroup1.getMotherClaimId() != null && this.claimIndex.get(claimgroup1.getMotherClaimId()) != null) continue;
                    arraylist.add(claimgroup1.getGroupId());
                }
                for (UUID uuid : arraylist) {
                    this.groups.remove(uuid);
                    for (Claim claim1 : this.getAllClaims()) {
                        if (!uuid.equals(claim1.getGroupId())) continue;
                        claim1.setGroupId(null);
                    }
                }
                TFClaims.LOGGER.info("Loaded {} claims from {} (migrated {} legacy)", new Object[]{i, path, j});
                if (j > 0) {
                    this.save();
                }
            }
            catch (Exception exception) {
                TFClaims.LOGGER.error("Could not load claims from " + path, (Throwable)exception);
            }
        }
    }

    private void loadConfig(MinecraftServer minecraftserver) {
        ClaimConfig.get().load(minecraftserver);
    }

    private static boolean looksValid(Path path) {
        try {
            if (!Files.exists(path, new LinkOption[0])) {
                return false;
            }
            String s = Files.readString(path, StandardCharsets.UTF_8);
            if (s.isBlank()) {
                return false;
            }
            JsonElement jsonelement = JsonParser.parseString((String)s);
            return jsonelement.isJsonObject() && jsonelement.getAsJsonObject().has("claims");
        }
        catch (Exception exception) {
            return false;
        }
    }

    private Path dataFile(MinecraftServer minecraftserver) {
        return minecraftserver.getWorldPath(LevelResource.ROOT).resolve(DATA_FILE);
    }

    public boolean isBypassing(UUID uuid) {
        return this.bypassPlayers.contains(uuid);
    }

    public boolean toggleBypass(UUID uuid) {
        if (this.bypassPlayers.contains(uuid)) {
            this.bypassPlayers.remove(uuid);
            return false;
        }
        this.bypassPlayers.add(uuid);
        return true;
    }

    public Set<UUID> getBypassPlayers() {
        return this.bypassPlayers;
    }

    public void queueMessage(UUID uuid, Component component) {
        this.pendingMessages.computeIfAbsent(uuid, uuid1 -> Collections.synchronizedList(new ArrayList())).add(component);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void flushPendingTo(ServerPlayer serverplayer) {
        List<Component> list = this.pendingMessages.remove(serverplayer.getUUID());
        if (list != null) {
            List<Component> list2 = list;
            synchronized (list2) {
                for (Component component : list) {
                    serverplayer.displayClientMessage(component, false);
                }
            }
        }
    }

    public void onPlayerDisconnect(UUID uuid) {
        this.bypassPlayers.remove(uuid);
    }

    static {
        IO = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ClaimBlocks-IO");
            thread.setDaemon(true);
            return thread;
        });
    }
}

