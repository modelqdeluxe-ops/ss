/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La config de revivir (config/tfclient/revivir.json): los mismos campos que config/revivemod.json, con los mismos
 * valores por defecto, y los dos nuevos para rematar ({@link #playersCanFinish}, {@link #mobsCanFinish}).
 */
public class ReviveConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public int downTimeSeconds = 90;
    public double reviveDistance = 3.0;
    public int reviveTimeTicks = 80;
    public float reviveHealth = 6.0F;
    public int reviveFood = 10;
    /** (1.3.157) Otro jugador puede rematar a un noqueado: un golpe suyo que haga daño lo mata de verdad. */
    public boolean playersCanFinish = true;
    /** (1.3.157) Los mobs también pueden rematar a un noqueado (y no pierden de vista al que cae). */
    public boolean mobsCanFinish = false;
    public boolean glowingWhileDown = true;
    public boolean clearMobAggroOnDown = true;
    public int crawlSlowness = 2;
    public boolean allowSelfRevive = true;
    public int selfReviveLevelCost = 10;
    public int knockdownSoundLayers = 5;
    public float knockdownVolume = 1.0F;
    public float deathVolume = 1.0F;
    public int deathSoundLayers = 5;
    public float reviveVolume = 1.0F;
    public int reviveSoundLayers = 5;
    public float countdownTickVolume = 0.35F;
    public float reviveTickVolume = 0.4F;
    public int bloodParticleCount = 2;
    public int bloodParticleInterval = 12;
    public float bloodParticleScale = 0.9F;
    public int whiteParticleCount = 1;
    public float whiteParticleScale = 0.7F;

    /** La lee del archivo (o la de fábrica si no existe) y la guarda (así salen los campos nuevos en el archivo). */
    public static ReviveConfig load(Path configFile) {
        ReviveConfig cfg;
        try {
            cfg = read(configFile);
        } catch (Exception e) {
            // (igual que el revivemod, se arranca con lo de fábrica; pero el archivo con el error no se pisa)
            TFClient.LOGGER.error("TF Revivir: no se pudo leer {}, se usa lo de fábrica: {}", configFile.getFileName(), e.getMessage());
            return new ReviveConfig();
        }
        cfg.save(configFile);
        return cfg;
    }

    /** La lee del archivo; la de fábrica si no existe; una excepción si el JSON tiene un error. */
    public static ReviveConfig read(Path configFile) throws IOException {
        if (!Files.exists(configFile)) return new ReviveConfig();
        ReviveConfig cfg = GSON.fromJson(Files.readString(configFile), ReviveConfig.class);
        return cfg == null ? new ReviveConfig() : cfg;
    }

    public void save(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(this));
        } catch (IOException e) {
            TFClient.LOGGER.error("TF Revivir: no se pudo guardar {}: {}", configFile.getFileName(), e.getMessage());
        }
    }
}
