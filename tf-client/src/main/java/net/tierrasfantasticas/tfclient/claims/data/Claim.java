package net.tierrasfantasticas.tfclient.claims.data;

import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags;
import net.tierrasfantasticas.tfclient.claims.data.ClaimGroup;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class Claim {
    private final UUID claimId;
    private UUID ownerUUID;
    private String ownerName;
    private String tierId;
    private final int radius;
    private final int height;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private long createdAt;
    private UUID groupId;
    private final List<UUID> members = new ArrayList<UUID>();
    private final List<String> memberNames = new ArrayList<String>();
    private final Set<UUID> bannedPlayers = new HashSet<UUID>();
    private final ClaimFlags flags = new ClaimFlags();

    public Claim(UUID uuid, UUID uuid1, String s, String s1, int i, int j, String s2, int k, int l, int i1) {
        this.claimId = uuid;
        this.ownerUUID = uuid1;
        this.ownerName = s;
        this.tierId = s1;
        this.radius = i;
        this.height = j;
        this.world = s2;
        this.x = k;
        this.y = l;
        this.z = i1;
        this.createdAt = System.currentTimeMillis();
    }

    public static Claim create(UUID uuid, String s, ClaimTier claimtier, String s1, BlockPos blockpos) {
        return new Claim(UUID.randomUUID(), uuid, s, claimtier.id, claimtier.radius, claimtier.height, s1, blockpos.getX(), blockpos.getY(), blockpos.getZ());
    }

    public UUID getClaimId() {
        return this.claimId;
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public String getTierId() {
        return this.tierId;
    }

    public int getRadius() {
        return this.radius;
    }

    public int getHeight() {
        return this.effectiveHeight();
    }

    public int getOwnHeight() {
        return this.height;
    }

    public UUID getGroupId() {
        return this.groupId;
    }

    public void setGroupId(UUID uuid) {
        this.groupId = uuid;
    }

    public boolean isGrouped() {
        return this.groupId != null;
    }

    public Claim getMother() {
        return this.groupId == null ? null : ClaimManager.getInstance().getMotherClaim(this.groupId);
    }

    public boolean isGroupMother() {
        if (this.groupId == null) {
            return false;
        }
        ClaimGroup claimgroup = ClaimManager.getInstance().getGroup(this.groupId);
        return claimgroup != null && this.claimId.equals(claimgroup.getMotherClaimId());
    }

    public int effectiveHeight() {
        Claim claim1;
        return this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null && claim1 != this ? claim1.height : this.height;
    }

    public String getWorld() {
        return this.world;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public BlockPos getCenter() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public List<UUID> getMembers() {
        return this.members;
    }

    public List<String> getMemberNames() {
        return this.memberNames;
    }

    private Claim banHolder() {
        Claim claim1;
        return this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null ? claim1 : this;
    }

    public Set<UUID> getBannedPlayers() {
        return this.banHolder().bannedPlayers;
    }

    public Set<UUID> getOwnBannedPlayers() {
        return this.bannedPlayers;
    }

    public ClaimFlags getFlags() {
        Claim claim1;
        return this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null && claim1 != this ? claim1.flags : this.flags;
    }

    public ClaimFlags getOwnFlags() {
        return this.flags;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(long i) {
        this.createdAt = i;
    }

    public ClaimTier getTier() {
        ClaimTier claimtier;
        return this.tierId != null && (claimtier = ClaimTier.byId(this.tierId)) != null ? claimtier : ClaimTier.closestMatch(this.radius, this.height);
    }

    public String sizeLabel() {
        if (this.tierId != null && this.tierId.startsWith("claimstone_")) {
            return this.tierId.substring("claimstone_".length());
        }
        ClaimTier claimtier = this.getTier();
        return claimtier == null ? this.radius + "x" + this.radius : claimtier.label();
    }

    public boolean contains(BlockPos blockpos) {
        if (Math.abs(blockpos.getX() - this.x) <= this.radius && Math.abs(blockpos.getZ() - this.z) <= this.radius) {
            Claim claim1;
            int i = this.y;
            int j = this.height;
            if (this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null) {
                i = claim1.y;
                j = claim1.height;
            }
            // Hacia abajo, su altura; hacia arriba, hasta el techo del mundo (sin límite)
            return i - blockpos.getY() <= j;
        }
        return false;
    }

    public boolean overlapsWith(BlockPos blockpos, int i, int j) {
        // Las zonas suben hasta el cielo: dos zonas que se pisan en planta siempre se solapan
        return Math.abs(blockpos.getX() - this.x) < this.radius + i && Math.abs(blockpos.getZ() - this.z) < this.radius + i;
    }

    public AABB getBoundingBox() {
        return new AABB((double)(this.x - this.radius), (double)this.getBottom(), (double)(this.z - this.radius), (double)(this.x + this.radius + 1), (double)this.getTop(), (double)(this.z + this.radius + 1));
    }

    /** Primer bloque protegido por abajo (el de la zona madre si está unida a otras). */
    public int getBottom() {
        Claim claim1;
        if (this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null) {
            return claim1.y - claim1.height;
        }
        return this.y - this.height;
    }

    /** Techo de la zona: el del mundo (la protección no tiene límite hacia arriba). */
    public int getTop() {
        return ClaimManager.getInstance().topOf(this.world);
    }

    public boolean isOwner(UUID uuid) {
        return this.ownerUUID != null && this.ownerUUID.equals(uuid);
    }

    public boolean isOwner(Player player) {
        return this.isOwner(player.getUUID());
    }

    public boolean isMember(UUID uuid) {
        return this.members.contains(uuid);
    }

    public boolean isMember(Player player) {
        return this.isMember(player.getUUID());
    }

    public boolean isBanned(UUID uuid) {
        return this.banHolder().bannedPlayers.contains(uuid);
    }

    public boolean canModify(Player player) {
        ClaimGroup claimgroup;
        return !(this.isOwner(player) || this.isMember(player) || player.hasPermissions(2)) ? this.groupId != null && (claimgroup = ClaimManager.getInstance().getGroup(this.groupId)) != null && claimgroup.isRegistered(player.getUUID()) : true;
    }

    public void addMember(UUID uuid, String s) {
        if (!this.members.contains(uuid)) {
            this.members.add(uuid);
            this.memberNames.add(s == null ? "" : s);
        }
    }

    public void removeMember(UUID uuid) {
        int i = this.members.indexOf(uuid);
        if (i >= 0) {
            this.members.remove(i);
            if (i < this.memberNames.size()) {
                this.memberNames.remove(i);
            }
        }
    }

    public void banPlayer(UUID uuid) {
        this.banHolder().bannedPlayers.add(uuid);
        this.removeMember(uuid);
        if (this.groupId != null) {
            for (Claim claim1 : ClaimManager.getInstance().getGroupClaims(this.groupId)) {
                claim1.removeMember(uuid);
            }
        }
    }

    public void unbanPlayer(UUID uuid) {
        this.banHolder().bannedPlayers.remove(uuid);
        this.bannedPlayers.remove(uuid);
        if (this.groupId != null) {
            for (Claim claim1 : ClaimManager.getInstance().getGroupClaims(this.groupId)) {
                claim1.bannedPlayers.remove(uuid);
            }
        }
    }

    public void setOwner(UUID uuid, String s) {
        this.ownerUUID = uuid;
        this.ownerName = s;
    }

    public void setTierId(String s) {
        this.tierId = s;
    }

    public JsonObject toJson() {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("claimId", this.claimId.toString());
        jsonobject.addProperty("ownerUUID", this.ownerUUID == null ? "" : this.ownerUUID.toString());
        jsonobject.addProperty("ownerName", this.ownerName == null ? "" : this.ownerName);
        if (this.tierId != null) {
            jsonobject.addProperty("tierId", this.tierId);
        }
        jsonobject.addProperty("radius", (Number)this.radius);
        jsonobject.addProperty("height", (Number)this.height);
        jsonobject.addProperty("world", this.world);
        jsonobject.addProperty("x", (Number)this.x);
        jsonobject.addProperty("y", (Number)this.y);
        jsonobject.addProperty("z", (Number)this.z);
        jsonobject.addProperty("createdAt", (Number)this.createdAt);
        if (this.groupId != null) {
            jsonobject.addProperty("groupId", this.groupId.toString());
        }
        JsonArray jsonarray = new JsonArray();
        for (UUID uUID : this.members) {
            jsonarray.add(uUID.toString());
        }
        jsonobject.add("members", (JsonElement)jsonarray);
        JsonArray jsonarray1 = new JsonArray();
        for (String string : this.memberNames) {
            jsonarray1.add(string == null ? "" : string);
        }
        jsonobject.add("memberNames", (JsonElement)jsonarray1);
        JsonArray jsonArray = new JsonArray();
        for (UUID uuid1 : this.bannedPlayers) {
            jsonArray.add(uuid1.toString());
        }
        jsonobject.add("bannedPlayers", (JsonElement)jsonArray);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("blockBuilding", Boolean.valueOf(this.flags.blockBuilding));
        jsonObject.addProperty("blockBreaking", Boolean.valueOf(this.flags.blockBreaking));
        jsonObject.addProperty("blockExplosions", Boolean.valueOf(this.flags.blockExplosions));
        jsonObject.addProperty("blockFire", Boolean.valueOf(this.flags.blockFire));
        jsonObject.addProperty("blockMobSpawn", Boolean.valueOf(this.flags.blockMobSpawn));
        jsonObject.addProperty("blockPVP", Boolean.valueOf(this.flags.blockPVP));
        jsonObject.addProperty("blockMobDamage", Boolean.valueOf(this.flags.blockMobDamage));
        jsonObject.addProperty("trespasserAlerts", Boolean.valueOf(this.flags.trespasserAlerts));
        jsonObject.addProperty("blockItemUse", Boolean.valueOf(this.flags.blockItemUse));
        jsonObject.addProperty("blockEntityInteract", Boolean.valueOf(this.flags.blockEntityInteract));
        jsonObject.addProperty("blockTrampling", Boolean.valueOf(this.flags.blockTrampling));
        jsonObject.addProperty("blockFluids", Boolean.valueOf(this.flags.blockFluids));
        jsonObject.addProperty("pvpAll", Boolean.valueOf(this.flags.pvpAll));
        jsonObject.addProperty("blockTreeChopping", Boolean.valueOf(this.flags.blockTreeChopping));
        jsonObject.addProperty("publicMode", Boolean.valueOf(this.flags.publicMode));
        jsonObject.addProperty("showWelcome", Boolean.valueOf(this.flags.showWelcome));
        jsonObject.addProperty("welcomeMessage", this.flags.welcomeMessage == null ? "" : this.flags.welcomeMessage);
        jsonObject.addProperty("showLeave", Boolean.valueOf(this.flags.showLeave));
        jsonObject.addProperty("leaveMessage", this.flags.leaveMessage == null ? "" : this.flags.leaveMessage);
        jsonObject.addProperty("showBorder", Boolean.valueOf(this.flags.showBorder));
        jsonObject.addProperty("showParticles", Boolean.valueOf(this.flags.showParticles));
        jsonObject.addProperty("borderParticle", this.flags.borderParticle == null ? "happy" : this.flags.borderParticle);
        jsonObject.addProperty("particleDensity", (Number)this.flags.particleDensity);
        jsonObject.addProperty("burnHostiles", Boolean.valueOf(this.flags.burnHostiles));
        jsonObject.addProperty("effectRegeneration", Boolean.valueOf(this.flags.effectRegeneration));
        jsonObject.addProperty("effectResistance", Boolean.valueOf(this.flags.effectResistance));
        jsonObject.addProperty("effectSpeed", Boolean.valueOf(this.flags.effectSpeed));
        jsonObject.addProperty("blockAnimalKilling", Boolean.valueOf(this.flags.blockAnimalKilling));
        jsonObject.addProperty("blockChestAccess", Boolean.valueOf(this.flags.blockChestAccess));
        jsonObject.addProperty("blockCropHarvest", Boolean.valueOf(this.flags.blockCropHarvest));
        jsonObject.addProperty("blockAnvilUse", Boolean.valueOf(this.flags.blockAnvilUse));
        jsonObject.addProperty("blockEnderPearl", Boolean.valueOf(this.flags.blockEnderPearl));
        jsonObject.addProperty("blockSignEditing", Boolean.valueOf(this.flags.blockSignEditing));
        jsonObject.addProperty("allowFlight", Boolean.valueOf(this.flags.allowFlight));
        jsonObject.addProperty("blockDoorsAccess", Boolean.valueOf(this.flags.blockDoorsAccess));
        jsonObject.addProperty("blockAllInteractions", Boolean.valueOf(this.flags.blockAllInteractions));
        jsonObject.addProperty("blockAllMobSpawn", Boolean.valueOf(this.flags.blockAllMobSpawn));
        jsonObject.addProperty("blockPassiveMobSpawn", Boolean.valueOf(this.flags.blockPassiveMobSpawn));
        jsonobject.add("flags", (JsonElement)jsonObject);
        return jsonobject;
    }

    public static Claim fromJson(JsonObject jsonobject) {
        String s;
        int i;
        int j;
        UUID uuid = jsonobject.has("claimId") ? UUID.fromString(jsonobject.get("claimId").getAsString()) : UUID.randomUUID();
        UUID uuid1 = jsonobject.has("ownerUUID") && !jsonobject.get("ownerUUID").getAsString().isEmpty() ? UUID.fromString(jsonobject.get("ownerUUID").getAsString()) : null;
        String s1 = jsonobject.has("ownerName") ? jsonobject.get("ownerName").getAsString() : "";
        String s2 = jsonobject.has("world") ? jsonobject.get("world").getAsString() : "minecraft:overworld";
        int k = jsonobject.get("x").getAsInt();
        int l = jsonobject.get("y").getAsInt();
        int i1 = jsonobject.get("z").getAsInt();
        if (jsonobject.has("radius") && jsonobject.has("height")) {
            j = jsonobject.get("radius").getAsInt();
            i = jsonobject.get("height").getAsInt();
            s = jsonobject.has("tierId") ? jsonobject.get("tierId").getAsString() : null;
        } else if (jsonobject.has("tier")) {
            int j1 = jsonobject.get("tier").getAsInt();
            ClaimTier claimtier = ClaimTier.byLegacyTier(j1);
            if (claimtier == null) {
                claimtier = ClaimTier.VALUES[0];
            }
            j = claimtier.radius;
            i = claimtier.height;
            s = claimtier.id;
        } else {
            j = 10;
            i = 15;
            s = "claimstone_10x10";
        }
        Claim claim = new Claim(uuid, uuid1, s1, s, j, i, s2, k, l, i1);
        long l1 = claim.createdAt = jsonobject.has("createdAt") ? jsonobject.get("createdAt").getAsLong() : 0L;
        if (jsonobject.has("groupId") && !jsonobject.get("groupId").getAsString().isEmpty()) {
            claim.groupId = UUID.fromString(jsonobject.get("groupId").getAsString());
        }
        if (jsonobject.has("members")) {
            JsonArray jsonarray = jsonobject.getAsJsonArray("members");
            JsonArray jsonarray1 = jsonobject.has("memberNames") ? jsonobject.getAsJsonArray("memberNames") : new JsonArray();
            for (int k1 = 0; k1 < jsonarray.size(); ++k1) {
                UUID uuid2 = UUID.fromString(jsonarray.get(k1).getAsString());
                String s3 = k1 < jsonarray1.size() ? jsonarray1.get(k1).getAsString() : "";
                claim.addMember(uuid2, s3);
            }
        }
        if (jsonobject.has("bannedPlayers")) {
            JsonArray jsonarray2 = jsonobject.getAsJsonArray("bannedPlayers");
            for (int i2 = 0; i2 < jsonarray2.size(); ++i2) {
                claim.bannedPlayers.add(UUID.fromString(jsonarray2.get(i2).getAsString()));
            }
        }
        if (jsonobject.has("flags")) {
            JsonObject jsonobject1 = jsonobject.getAsJsonObject("flags");
            Claim.applyBool(jsonobject1, "blockBuilding", flag -> {
                claim.flags.blockBuilding = flag;
            });
            Claim.applyBool(jsonobject1, "blockBreaking", flag -> {
                claim.flags.blockBreaking = flag;
            });
            Claim.applyBool(jsonobject1, "blockExplosions", flag -> {
                claim.flags.blockExplosions = flag;
            });
            Claim.applyBool(jsonobject1, "blockFire", flag -> {
                claim.flags.blockFire = flag;
            });
            Claim.applyBool(jsonobject1, "blockMobSpawn", flag -> {
                claim.flags.blockMobSpawn = flag;
            });
            Claim.applyBool(jsonobject1, "blockPVP", flag -> {
                claim.flags.blockPVP = flag;
            });
            Claim.applyBool(jsonobject1, "blockMobDamage", flag -> {
                claim.flags.blockMobDamage = flag;
            });
            Claim.applyBool(jsonobject1, "trespasserAlerts", flag -> {
                claim.flags.trespasserAlerts = flag;
            });
            Claim.applyBool(jsonobject1, "blockItemUse", flag -> {
                claim.flags.blockItemUse = flag;
            });
            Claim.applyBool(jsonobject1, "blockEntityInteract", flag -> {
                claim.flags.blockEntityInteract = flag;
            });
            Claim.applyBool(jsonobject1, "blockTrampling", flag -> {
                claim.flags.blockTrampling = flag;
            });
            Claim.applyBool(jsonobject1, "blockFluids", flag -> {
                claim.flags.blockFluids = flag;
            });
            Claim.applyBool(jsonobject1, "pvpAll", flag -> {
                claim.flags.pvpAll = flag;
            });
            Claim.applyBool(jsonobject1, "blockTreeChopping", flag -> {
                claim.flags.blockTreeChopping = flag;
            });
            Claim.applyBool(jsonobject1, "publicMode", flag -> {
                claim.flags.publicMode = flag;
            });
            Claim.applyBool(jsonobject1, "showWelcome", flag -> {
                claim.flags.showWelcome = flag;
            });
            if (jsonobject1.has("welcomeMessage")) {
                claim.flags.welcomeMessage = jsonobject1.get("welcomeMessage").getAsString();
            }
            Claim.applyBool(jsonobject1, "showLeave", flag -> {
                claim.flags.showLeave = flag;
            });
            if (jsonobject1.has("leaveMessage")) {
                claim.flags.leaveMessage = jsonobject1.get("leaveMessage").getAsString();
            }
            Claim.applyBool(jsonobject1, "showBorder", flag -> {
                claim.flags.showBorder = flag;
            });
            Claim.applyBool(jsonobject1, "showParticles", flag -> {
                claim.flags.showParticles = flag;
            });
            if (jsonobject1.has("borderParticle")) {
                claim.flags.borderParticle = jsonobject1.get("borderParticle").getAsString();
            }
            if (jsonobject1.has("particleDensity")) {
                claim.flags.particleDensity = jsonobject1.get("particleDensity").getAsInt();
            }
            Claim.applyBool(jsonobject1, "burnHostiles", flag -> {
                claim.flags.burnHostiles = flag;
            });
            Claim.applyBool(jsonobject1, "effectRegeneration", flag -> {
                claim.flags.effectRegeneration = flag;
            });
            Claim.applyBool(jsonobject1, "effectResistance", flag -> {
                claim.flags.effectResistance = flag;
            });
            Claim.applyBool(jsonobject1, "effectSpeed", flag -> {
                claim.flags.effectSpeed = flag;
            });
            Claim.applyBool(jsonobject1, "blockAnimalKilling", flag -> {
                claim.flags.blockAnimalKilling = flag;
            });
            Claim.applyBool(jsonobject1, "blockChestAccess", flag -> {
                claim.flags.blockChestAccess = flag;
            });
            Claim.applyBool(jsonobject1, "blockCropHarvest", flag -> {
                claim.flags.blockCropHarvest = flag;
            });
            Claim.applyBool(jsonobject1, "blockAnvilUse", flag -> {
                claim.flags.blockAnvilUse = flag;
            });
            Claim.applyBool(jsonobject1, "blockEnderPearl", flag -> {
                claim.flags.blockEnderPearl = flag;
            });
            Claim.applyBool(jsonobject1, "blockSignEditing", flag -> {
                claim.flags.blockSignEditing = flag;
            });
            Claim.applyBool(jsonobject1, "allowFlight", flag -> {
                claim.flags.allowFlight = flag;
            });
            Claim.applyBool(jsonobject1, "blockDoorsAccess", flag -> {
                claim.flags.blockDoorsAccess = flag;
            });
            Claim.applyBool(jsonobject1, "blockAllInteractions", flag -> {
                claim.flags.blockAllInteractions = flag;
            });
            Claim.applyBool(jsonobject1, "blockAllMobSpawn", flag -> {
                claim.flags.blockAllMobSpawn = flag;
            });
            Claim.applyBool(jsonobject1, "blockPassiveMobSpawn", flag -> {
                claim.flags.blockPassiveMobSpawn = flag;
            });
        }
        return claim;
    }

    private static void applyBool(JsonObject jsonobject, String s, BoolSetter claim$boolsetter) {
        if (jsonobject.has(s)) {
            claim$boolsetter.set(jsonobject.get(s).getAsBoolean());
        }
    }

    private static interface BoolSetter {
        public void set(boolean var1);
    }
}

