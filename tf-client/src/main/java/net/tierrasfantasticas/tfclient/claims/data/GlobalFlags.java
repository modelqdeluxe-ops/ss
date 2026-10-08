package net.tierrasfantasticas.tfclient.claims.data;

import net.tierrasfantasticas.tfclient.claims.TFClaims;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;

public final class GlobalFlags {
    private static final String FILE = "global_flags.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile GlobalFlags INSTANCE;
    public volatile boolean globalPVP = true;
    public volatile boolean globalMobGriefing = true;
    public volatile boolean globalFireSpread = true;
    public volatile boolean globalNoMobSpawn = false;

    private GlobalFlags() {
    }

    public static GlobalFlags getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GlobalFlags();
        }
        return INSTANCE;
    }

    public boolean get(String s) {
        return switch (s) {
            case "globalPVP" -> this.globalPVP;
            case "globalMobGriefing" -> this.globalMobGriefing;
            case "globalFireSpread" -> this.globalFireSpread;
            case "globalNoMobSpawn" -> this.globalNoMobSpawn;
            default -> false;
        };
    }

    public void set(String s, boolean flag, MinecraftServer minecraftserver) {
        switch (s) {
            case "globalPVP": {
                this.globalPVP = flag;
                break;
            }
            case "globalMobGriefing": {
                this.globalMobGriefing = flag;
                break;
            }
            case "globalFireSpread": {
                this.globalFireSpread = flag;
                break;
            }
            case "globalNoMobSpawn": {
                this.globalNoMobSpawn = flag;
            }
        }
        this.applyToServer(minecraftserver);
        this.save(minecraftserver);
    }

    public void applyToServer(MinecraftServer minecraftserver) {
        if (minecraftserver != null) {
            minecraftserver.setPvpAllowed(this.globalPVP);
            GameRules gamerules = minecraftserver.getGameRules();
            ((GameRules.BooleanValue)gamerules.getRule(GameRules.RULE_MOBGRIEFING)).set(this.globalMobGriefing, minecraftserver);
            ((GameRules.BooleanValue)gamerules.getRule(GameRules.RULE_DOFIRETICK)).set(this.globalFireSpread, minecraftserver);
        }
    }

    public void load(MinecraftServer minecraftserver) {
        Path path = this.file(minecraftserver);
        if (!Files.exists(path, new LinkOption[0])) {
            TFClaims.LOGGER.info("No global_flags.json, defaults applied.");
            this.applyToServer(minecraftserver);
        } else {
            try {
                String s = Files.readString(path, StandardCharsets.UTF_8);
                JsonObject jsonobject = JsonParser.parseString((String)s).getAsJsonObject();
                if (jsonobject.has("globalPVP")) {
                    this.globalPVP = jsonobject.get("globalPVP").getAsBoolean();
                }
                if (jsonobject.has("globalMobGriefing")) {
                    this.globalMobGriefing = jsonobject.get("globalMobGriefing").getAsBoolean();
                }
                if (jsonobject.has("globalFireSpread")) {
                    this.globalFireSpread = jsonobject.get("globalFireSpread").getAsBoolean();
                }
                if (jsonobject.has("globalNoMobSpawn")) {
                    this.globalNoMobSpawn = jsonobject.get("globalNoMobSpawn").getAsBoolean();
                }
                this.applyToServer(minecraftserver);
                TFClaims.LOGGER.info("Global flags cargadas: PVP={} MobGrief={} FireSpread={} NoMobSpawn={}", new Object[]{this.globalPVP, this.globalMobGriefing, this.globalFireSpread, this.globalNoMobSpawn});
            }
            catch (Exception exception) {
                TFClaims.LOGGER.error("No se pudo cargar global_flags.json", (Throwable)exception);
            }
        }
    }

    public void save(MinecraftServer minecraftserver) {
        Path path = this.file(minecraftserver);
        try {
            JsonObject jsonobject = new JsonObject();
            jsonobject.addProperty("globalPVP", Boolean.valueOf(this.globalPVP));
            jsonobject.addProperty("globalMobGriefing", Boolean.valueOf(this.globalMobGriefing));
            jsonobject.addProperty("globalFireSpread", Boolean.valueOf(this.globalFireSpread));
            jsonobject.addProperty("globalNoMobSpawn", Boolean.valueOf(this.globalNoMobSpawn));
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            Files.writeString(path, (CharSequence)GSON.toJson((JsonElement)jsonobject), StandardCharsets.UTF_8, new OpenOption[0]);
        }
        catch (IOException ioexception) {
            TFClaims.LOGGER.error("No se pudo guardar global_flags.json", (Throwable)ioexception);
        }
    }

    private Path file(MinecraftServer minecraftserver) {
        return minecraftserver.getWorldPath(LevelResource.ROOT).resolve(FILE);
    }
}

