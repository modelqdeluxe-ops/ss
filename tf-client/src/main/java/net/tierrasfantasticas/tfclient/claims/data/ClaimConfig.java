package net.tierrasfantasticas.tfclient.claims.data;

import net.tierrasfantasticas.tfclient.claims.TFClaims;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class ClaimConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "claimblocks_config.json";
    private static final ClaimConfig INSTANCE = new ClaimConfig();
    public int maxClaimsPerPlayer = 0;
    public int maxMembersPerClaim = 0;
    public boolean protectHoppers = true;
    public boolean protectFluids = true;
    public boolean protectDecoration = true;
    public boolean protectDecorationFromExplosions = true;
    public boolean banTeleportOut = true;
    public float banDamage = 0.0f;
    public int banNoticeSeconds = 2;
    public int trespasserAlertSeconds = 30;
    public int chatPromptSeconds = 90;
    public int maxWelcomeLength = 60;
    public int particleIntervalTicks = 4;
    public int borderIntervalTicks = 20;
    public int particleRenderDistance = 24;
    public int fireSweepIntervalTicks = 40;
    public int fireSweepRadius = 6;
    public int passiveEffectIntervalTicks = 40;
    public int hostileBurnSeconds = 3;
    public float hostileDamage = 3.0f;
    public int effectDurationTicks = 60;
    public String defaultParticle = "minecraft:happy_villager";
    public int defaultParticleDensity = 10;
    public final Map<ClaimFlags.FlagId, Boolean> defaultFlags = new EnumMap<ClaimFlags.FlagId, Boolean>(ClaimFlags.FlagId.class);
    private Path file;

    private ClaimConfig() {
        this.resetDefaultFlags();
    }

    public static ClaimConfig get() {
        return INSTANCE;
    }

    private void resetDefaultFlags() {
        ClaimFlags claimflags = new ClaimFlags();
        this.defaultFlags.clear();
        for (ClaimFlags.FlagId claimflags$flagid : ClaimFlags.FlagId.values()) {
            this.defaultFlags.put(claimflags$flagid, claimflags.get(claimflags$flagid));
        }
    }

    public void load(MinecraftServer minecraftserver) {
        if (minecraftserver != null) {
            this.file = net.tierrasfantasticas.tfclient.util.TFConfigDir.fileFrom("protecciones.json", minecraftserver.getWorldPath(LevelResource.ROOT).resolve(FILE));
            this.reload();
        }
    }

    public boolean reload() {
        if (this.file == null) {
            return false;
        }
        JsonObject jsonobject = new JsonObject();
        try {
            JsonElement jsonelement;
            String s;
            if (Files.exists(this.file, new LinkOption[0]) && !(s = Files.readString(this.file, StandardCharsets.UTF_8)).isBlank() && (jsonelement = JsonParser.parseString((String)s)).isJsonObject()) {
                jsonobject = jsonelement.getAsJsonObject();
            }
        }
        catch (Exception exception) {
            TFClaims.LOGGER.error("[TF Claims] No se pudo leer " + this.file + "; se usan los valores por defecto", (Throwable)exception);
        }
        this.readFrom(jsonobject);
        this.write();
        return true;
    }

    private void readFrom(JsonObject jsonobject) {
        JsonObject jsonobject1 = ClaimConfig.section(jsonobject, "limites");
        int i = jsonobject.has("maxClaimsPerPlayer") ? jsonobject.get("maxClaimsPerPlayer").getAsInt() : this.maxClaimsPerPlayer;
        this.maxClaimsPerPlayer = Math.max(0, ClaimConfig.readInt(jsonobject1, "maxZonasPorJugador", i));
        this.maxMembersPerClaim = Math.max(0, ClaimConfig.readInt(jsonobject1, "maxMiembrosPorZona", this.maxMembersPerClaim));
        JsonObject jsonobject2 = ClaimConfig.section(jsonobject, "protecciones");
        this.protectHoppers = ClaimConfig.readBool(jsonobject2, "tolvasNoSacanItemsDeLaZona", this.protectHoppers);
        this.protectFluids = ClaimConfig.readBool(jsonobject2, "aguaYLavaNoEntranDesdeFuera", this.protectFluids);
        this.protectDecoration = ClaimConfig.readBool(jsonobject2, "cuadrosMarcosYSoportes", this.protectDecoration);
        this.protectDecorationFromExplosions = ClaimConfig.readBool(jsonobject2, "cuadrosResistenExplosiones", this.protectDecorationFromExplosions);
        JsonObject jsonobject3 = ClaimConfig.section(jsonobject, "baneados");
        this.banTeleportOut = ClaimConfig.readBool(jsonobject3, "expulsarPorTeletransporte", this.banTeleportOut);
        this.banDamage = Math.max(0.0f, ClaimConfig.readFloat(jsonobject3, "danoAlEntrar", this.banDamage));
        this.banNoticeSeconds = Math.max(0, ClaimConfig.readInt(jsonobject3, "segundosEntreAvisos", this.banNoticeSeconds));
        JsonObject jsonobject4 = ClaimConfig.section(jsonobject, "avisos");
        this.trespasserAlertSeconds = Math.max(0, ClaimConfig.readInt(jsonobject4, "segundosEntreAvisosDeIntruso", this.trespasserAlertSeconds));
        this.chatPromptSeconds = Math.max(5, ClaimConfig.readInt(jsonobject4, "segundosParaResponderEnChat", this.chatPromptSeconds));
        this.maxWelcomeLength = Math.max(10, ClaimConfig.readInt(jsonobject4, "maxCaracteresDeLosMensajes", this.maxWelcomeLength));
        JsonObject jsonobject5 = ClaimConfig.section(jsonobject, "rendimiento");
        this.particleIntervalTicks = Math.max(1, ClaimConfig.readInt(jsonobject5, "ticksEntreParticulas", this.particleIntervalTicks));
        this.borderIntervalTicks = Math.max(1, ClaimConfig.readInt(jsonobject5, "ticksEntreActualizacionDeBordes", this.borderIntervalTicks));
        this.particleRenderDistance = Math.max(1, ClaimConfig.readInt(jsonobject5, "distanciaParaVerParticulas", this.particleRenderDistance));
        this.fireSweepIntervalTicks = Math.max(1, ClaimConfig.readInt(jsonobject5, "ticksEntreBarridoDeFuego", this.fireSweepIntervalTicks));
        this.fireSweepRadius = Math.max(0, ClaimConfig.readInt(jsonobject5, "radioDeBarridoDeFuego", this.fireSweepRadius));
        this.passiveEffectIntervalTicks = Math.max(1, ClaimConfig.readInt(jsonobject5, "ticksEntreEfectosPasivos", this.passiveEffectIntervalTicks));
        JsonObject jsonobject6 = ClaimConfig.section(jsonobject, "barreraDeHostiles");
        this.hostileBurnSeconds = Math.max(0, ClaimConfig.readInt(jsonobject6, "segundosDeFuego", this.hostileBurnSeconds));
        this.hostileDamage = Math.max(0.0f, ClaimConfig.readFloat(jsonobject6, "dano", this.hostileDamage));
        JsonObject jsonobject7 = ClaimConfig.section(jsonobject, "efectosPasivos");
        this.effectDurationTicks = Math.max(20, ClaimConfig.readInt(jsonobject7, "duracionEnTicks", this.effectDurationTicks));
        JsonObject jsonobject8 = ClaimConfig.section(jsonobject, "zonasNuevas");
        this.defaultParticle = ClaimConfig.readString(jsonobject8, "particula", this.defaultParticle);
        this.defaultParticleDensity = Math.max(1, ClaimConfig.readInt(jsonobject8, "densidadDeParticulas", this.defaultParticleDensity));
        JsonObject jsonobject9 = ClaimConfig.section(jsonobject8, "flags");
        for (ClaimFlags.FlagId claimflags$flagid : ClaimFlags.FlagId.values()) {
            boolean flag = this.defaultFlags.getOrDefault((Object)claimflags$flagid, Boolean.FALSE);
            this.defaultFlags.put(claimflags$flagid, ClaimConfig.readBool(jsonobject9, claimflags$flagid.name(), flag));
        }
    }

    private void write() {
        JsonObject jsonobject = new JsonObject();
        jsonobject.add("_ayuda", (JsonElement)ClaimConfig.doc("TF Claims - configuracion del servidor.", "Recarga en caliente con /tf web claims reload (no hace falta reiniciar).", "Si borras una clave se rellena con su valor por defecto al recargar.", "Los cambios de 'zonasNuevas' solo afectan a las zonas que se creen a partir de ahora."));
        JsonObject jsonobject1 = new JsonObject();
        jsonobject1.add("_doc", (JsonElement)ClaimConfig.doc("maxZonasPorJugador: 0 = sin limite. No se aplica a operadores.", "maxMiembrosPorZona: 0 = sin limite."));
        jsonobject1.addProperty("maxZonasPorJugador", (Number)this.maxClaimsPerPlayer);
        jsonobject1.addProperty("maxMiembrosPorZona", (Number)this.maxMembersPerClaim);
        jsonobject.add("limites", (JsonElement)jsonobject1);
        JsonObject jsonobject2 = new JsonObject();
        jsonobject2.add("_doc", (JsonElement)ClaimConfig.doc("Protecciones que actuan sin que nadie pise la zona. Apagalas solo si te chocan con otro mod.", "tolvasNoSacanItemsDeLaZona: impide que una tolva o vagoneta-tolva vacie cofres desde fuera del borde.", "aguaYLavaNoEntranDesdeFuera: bloquea el flujo que cruza hacia dentro (el flujo interno no se toca).", "cuadrosMarcosYSoportes: protege cuadros, marcos y soportes de armadura de flechas, mobs y golpes.", "cuadrosResistenExplosiones: saca la decoracion de la lista de afectados por TNT y creepers."));
        jsonobject2.addProperty("tolvasNoSacanItemsDeLaZona", Boolean.valueOf(this.protectHoppers));
        jsonobject2.addProperty("aguaYLavaNoEntranDesdeFuera", Boolean.valueOf(this.protectFluids));
        jsonobject2.addProperty("cuadrosMarcosYSoportes", Boolean.valueOf(this.protectDecoration));
        jsonobject2.addProperty("cuadrosResistenExplosiones", Boolean.valueOf(this.protectDecorationFromExplosions));
        jsonobject.add("protecciones", (JsonElement)jsonobject2);
        JsonObject jsonobject3 = new JsonObject();
        jsonobject3.add("_doc", (JsonElement)ClaimConfig.doc("Que le pasa a un jugador baneado de una zona cuando entra.", "expulsarPorTeletransporte: true lo saca al borde mas cercano; false solo lo empuja.", "danoAlEntrar: 0 = sin dano. Ponlo alto solo si quieres que sea letal.", "segundosEntreAvisos: cada cuanto se le repite el mensaje."));
        jsonobject3.addProperty("expulsarPorTeletransporte", Boolean.valueOf(this.banTeleportOut));
        jsonobject3.addProperty("danoAlEntrar", (Number)Float.valueOf(this.banDamage));
        jsonobject3.addProperty("segundosEntreAvisos", (Number)this.banNoticeSeconds);
        jsonobject.add("baneados", (JsonElement)jsonobject3);
        JsonObject jsonobject4 = new JsonObject();
        jsonobject4.add("_doc", (JsonElement)ClaimConfig.doc("segundosEntreAvisosDeIntruso: antiespam del aviso al dueno cuando entra alguien.", "segundosParaResponderEnChat: tiempo para escribir un nombre cuando el menu lo pide.", "maxCaracteresDeLosMensajes: limite del mensaje de bienvenida y de salida."));
        jsonobject4.addProperty("segundosEntreAvisosDeIntruso", (Number)this.trespasserAlertSeconds);
        jsonobject4.addProperty("segundosParaResponderEnChat", (Number)this.chatPromptSeconds);
        jsonobject4.addProperty("maxCaracteresDeLosMensajes", (Number)this.maxWelcomeLength);
        jsonobject.add("avisos", (JsonElement)jsonobject4);
        JsonObject jsonobject5 = new JsonObject();
        jsonobject5.add("_doc", (JsonElement)ClaimConfig.doc("Sube los intervalos para gastar menos CPU y ancho de banda (20 ticks = 1 segundo).", "ticksEntreParticulas: cada cuanto se dibujan las particulas del area.", "ticksEntreActualizacionDeBordes: cada cuanto se envia el borde a los clientes.", "distanciaParaVerParticulas: a cuantos bloques del borde se empiezan a ver.", "ticksEntreBarridoDeFuego y radioDeBarridoDeFuego: apagado de fuego dentro de la zona.", "ticksEntreEfectosPasivos: cada cuanto se reaplican regeneracion, resistencia y velocidad."));
        jsonobject5.addProperty("ticksEntreParticulas", (Number)this.particleIntervalTicks);
        jsonobject5.addProperty("ticksEntreActualizacionDeBordes", (Number)this.borderIntervalTicks);
        jsonobject5.addProperty("distanciaParaVerParticulas", (Number)this.particleRenderDistance);
        jsonobject5.addProperty("ticksEntreBarridoDeFuego", (Number)this.fireSweepIntervalTicks);
        jsonobject5.addProperty("radioDeBarridoDeFuego", (Number)this.fireSweepRadius);
        jsonobject5.addProperty("ticksEntreEfectosPasivos", (Number)this.passiveEffectIntervalTicks);
        jsonobject.add("rendimiento", (JsonElement)jsonobject5);
        JsonObject jsonobject6 = new JsonObject();
        jsonobject6.add("_doc", (JsonElement)ClaimConfig.doc("Flag BURN_HOSTILES: que le pasa a un mob hostil que entra en la zona.", "segundosDeFuego: 0 para no quemarlos. dano: 0 para solo empujarlos."));
        jsonobject6.addProperty("segundosDeFuego", (Number)this.hostileBurnSeconds);
        jsonobject6.addProperty("dano", (Number)Float.valueOf(this.hostileDamage));
        jsonobject.add("barreraDeHostiles", (JsonElement)jsonobject6);
        JsonObject jsonobject7 = new JsonObject();
        jsonobject7.add("_doc", (JsonElement)ClaimConfig.doc("duracionEnTicks: cuanto dura cada aplicacion de los efectos de las zonas grandes.", "Debe ser mayor que ticksEntreEfectosPasivos o el efecto parpadeara."));
        jsonobject7.addProperty("duracionEnTicks", (Number)this.effectDurationTicks);
        jsonobject.add("efectosPasivos", (JsonElement)jsonobject7);
        JsonObject jsonobject8 = new JsonObject();
        jsonobject8.add("_doc", (JsonElement)ClaimConfig.doc("Con que valores nace una zona nueva. No cambia las zonas ya creadas.", "particula: id de particula para el borde, por ejemplo minecraft:happy_villager.", "flags: true = la proteccion viene activada de fabrica. Son los mismos botones del menu."));
        jsonobject8.addProperty("particula", this.defaultParticle);
        jsonobject8.addProperty("densidadDeParticulas", (Number)this.defaultParticleDensity);
        JsonObject jsonobject9 = new JsonObject();
        for (ClaimFlags.FlagId claimflags$flagid : ClaimFlags.FlagId.values()) {
            jsonobject9.addProperty(claimflags$flagid.name(), this.defaultFlags.getOrDefault((Object)claimflags$flagid, Boolean.FALSE));
        }
        jsonobject8.add("flags", (JsonElement)jsonobject9);
        jsonobject.add("zonasNuevas", (JsonElement)jsonobject8);
        try {
            Files.createDirectories(this.file.getParent(), new FileAttribute[0]);
            Path path = this.file.resolveSibling(this.file.getFileName().toString() + ".tmp");
            Files.writeString(path, (CharSequence)GSON.toJson((JsonElement)jsonobject), StandardCharsets.UTF_8, new OpenOption[0]);
            Files.move(path, this.file, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ioexception) {
            TFClaims.LOGGER.error("[TF Claims] No se pudo escribir " + this.file, (Throwable)ioexception);
        }
    }

    public void applyDefaultsTo(Claim claim) {
        if (claim != null) {
            ClaimFlags claimflags = claim.getOwnFlags();
            for (ClaimFlags.FlagId claimflags$flagid : ClaimFlags.FlagId.values()) {
                claimflags.set(claimflags$flagid, this.defaultFlags.getOrDefault((Object)claimflags$flagid, Boolean.FALSE));
            }
            claimflags.borderParticle = this.defaultParticle;
            claimflags.particleDensity = this.defaultParticleDensity;
        }
    }

    public int trespasserAlertTicks() {
        return this.trespasserAlertSeconds * 20;
    }

    public long chatPromptMillis() {
        return (long)this.chatPromptSeconds * 1000L;
    }

    public long banNoticeTicks() {
        return (long)this.banNoticeSeconds * 20L;
    }

    private static JsonArray doc(String ... astring) {
        JsonArray jsonarray = new JsonArray();
        for (String s : astring) {
            jsonarray.add(s);
        }
        return jsonarray;
    }

    private static JsonObject section(JsonObject jsonobject, String s) {
        return jsonobject.has(s) && jsonobject.get(s).isJsonObject() ? jsonobject.getAsJsonObject(s) : new JsonObject();
    }

    private static int readInt(JsonObject jsonobject, String s, int i) {
        try {
            return jsonobject.has(s) ? jsonobject.get(s).getAsInt() : i;
        }
        catch (Exception exception) {
            return i;
        }
    }

    private static float readFloat(JsonObject jsonobject, String s, float f) {
        try {
            return jsonobject.has(s) ? jsonobject.get(s).getAsFloat() : f;
        }
        catch (Exception exception) {
            return f;
        }
    }

    private static boolean readBool(JsonObject jsonobject, String s, boolean flag) {
        try {
            return jsonobject.has(s) ? jsonobject.get(s).getAsBoolean() : flag;
        }
        catch (Exception exception) {
            return flag;
        }
    }

    private static String readString(JsonObject jsonobject, String s, String s1) {
        try {
            return jsonobject.has(s) && !jsonobject.get(s).getAsString().isBlank() ? jsonobject.get(s).getAsString() : s1;
        }
        catch (Exception exception) {
            return s1;
        }
    }
}

