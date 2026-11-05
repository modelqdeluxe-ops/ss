/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.luna;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * config/tfclient/luna_sangre.json (se crea la primera vez con esto; /tf reload la vuelve a leer). Lo que no esté en el
 * archivo se queda con el valor de aquí.
 */
public final class BloodMoonConfig {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Si la Luna de Sangre sale sola (los comandos de staff funcionan siempre). */
    public boolean activa = true;
    /** Cada cuántas noches (contando solo las noches con gente conectada si contarNochesVacias es false). */
    public int cadaNoches = 10;
    /** Cuántos jugadores tiene que haber conectados justo al salir la luna; si hay menos, la cuenta vuelve a empezar. */
    public int minJugadores = 3;
    /** Tiempo real mínimo entre una Luna de Sangre y la siguiente (si toca antes, se espera a la noche siguiente). */
    public int minMinutosEntre = 120;
    /** Si las noches con el servidor vacío cuentan para la cuenta atrás. */
    public boolean contarNochesVacias = false;
    /** Desde cuántas noches antes se avisa en el chat al anochecer («faltan N noches»). */
    public int avisarDesde = 3;
    /** La escena del nacimiento de la luna para todos al empezar. */
    public boolean escena = true;
    /** Mobs hostiles cerca de cada jugador (48 bloques) hasta los que el evento añade más. */
    public int mobsPorJugador = 16;
    /** Cuántos mobs como mucho aparecen por jugador en cada oleada. */
    public int porOleada = 3;
    /** Segundos entre oleadas. */
    public int segundosEntreOleadas = 5;
    /** Si los mobs extra pueden aparecer dentro de las protecciones (los normales de Minecraft siguen sus reglas). */
    public boolean aparecerEnProtecciones = false;
    /** Bloques desde el spawn del mundo donde no salen élites ni campeones y los modificadores son raros. */
    public int zonaSegura = 150;
    /** A esta distancia del spawn (o más) los mobs fuertes salen con la probabilidad más alta. */
    public int distanciaMaxima = 3000;
    /** Probabilidad de que un mob normal de la noche lleve un modificador (cerca del spawn la mitad). */
    public double probModificador = 0.35;
    /** Probabilidad de élite (2 modificadores + daño mágico) y de campeón (3 + daño mágico), lejos del spawn sube. */
    public double probElite = 0.10;
    public double probCampeon = 0.025;
    /** Multiplicador de la vida. */
    public double vidaElite = 1.6;
    public double vidaCampeon = 2.5;
    /** Lo que suben el daño y la velocidad de todos los mobs hostiles esa noche (0,2 = +20 %). */
    public double danoExtra = 0.2;
    public double velocidadExtra = 0.12;
    /** Multiplicador de la experiencia que sueltan. */
    public int experienciaNormal = 2;
    public int experienciaElite = 4;
    public int experienciaCampeon = 8;
    /** Los modificadores que pueden salir (los nombres de BloodMobs.Affix: VAMPIRICO, ARDIENTE…). */
    public List<String> modificadores = new ArrayList<>(BloodMobs.Affix.names());

    static BloodMoonConfig load(Path path) {
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    BloodMoonConfig c = GSON.fromJson(r, BloodMoonConfig.class);
                    if (c != null) {
                        c.clamp();
                        c.save(path);
                        return c;
                    }
                }
            }
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Luna de Sangre: no se pudo leer {} (se usa la config por defecto)", path, e);
            return new BloodMoonConfig();
        }
        BloodMoonConfig c = new BloodMoonConfig();
        c.save(path);
        return c;
    }

    /** Para /tf reload: lanza si el archivo está mal (así se queda la de antes). */
    static BloodMoonConfig read(Path path) throws Exception {
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            BloodMoonConfig c = GSON.fromJson(r, BloodMoonConfig.class);
            if (c == null) throw new IllegalStateException("archivo vacío");
            c.clamp();
            return c;
        }
    }

    void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Luna de Sangre: no se pudo guardar {}", path, e);
        }
    }

    private void clamp() {
        cadaNoches = Math.max(1, cadaNoches);
        minJugadores = Math.max(0, minJugadores);
        minMinutosEntre = Math.max(0, minMinutosEntre);
        mobsPorJugador = Math.max(0, Math.min(60, mobsPorJugador));
        porOleada = Math.max(0, Math.min(10, porOleada));
        segundosEntreOleadas = Math.max(1, segundosEntreOleadas);
        distanciaMaxima = Math.max(zonaSegura + 1, distanciaMaxima);
        if (modificadores == null) modificadores = new ArrayList<>(BloodMobs.Affix.names());
    }
}
