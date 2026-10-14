package net.tierrasfantasticas.tfclient.bosses;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * La dificultad de un jefe (lo que se elige en /tf boss): un preset de Fácil a Demencial y sus valores, que se pueden
 * retocar. Vida, daño, velocidad y ritmo de ataque son multiplicadores sobre los valores base del jefe (los de Normal
 * para un jugador); la armadura son puntos; «por jugador» es la vida que suma cada jugador más que haya al abrir la
 * grieta (incursión en grupo); la recarga es lo que tarda la grieta en volver a estar lista; las monedas, lo que se
 * lleva cada jugador que le haya hecho daño.
 */
public record Difficulty(String preset, double health, double damage, double speed, double rhythm, double armor,
                         double perPlayer, int respawnMin, int coins) {

    /** Los presets, de menos a más difícil: id, nombre y color en la ventana. */
    public record Preset(String id, String name, int color, Difficulty values) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset("facil", "Fácil", 0x5BD46B, new Difficulty("facil", 0.6, 0.55, 0.9, 0.8, 0, 0.35, 10, 150)),
            new Preset("normal", "Normal", 0x4FB3F2, new Difficulty("normal", 1.0, 1.0, 1.0, 1.0, 4, 0.5, 15, 300)),
            new Preset("dificil", "Difícil", 0xF2C14F, new Difficulty("dificil", 1.6, 1.45, 1.08, 1.15, 8, 0.6, 20, 600)),
            new Preset("pesadilla", "Pesadilla", 0xF2784F, new Difficulty("pesadilla", 2.5, 2.1, 1.16, 1.3, 12, 0.75, 30, 1100)),
            new Preset("demencial", "Demencial", 0xE0447A, new Difficulty("demencial", 4.0, 3.0, 1.25, 1.5, 18, 1.0, 45, 2000)));

    public static final Difficulty NORMAL = PRESETS.get(1).values();

    /** Límites de cada valor (lo que deja poner la ventana). */
    public static final double MIN_MUL = 0.1, MAX_MUL = 20.0, MAX_ARMOR = 30, MAX_PER_PLAYER = 3.0;
    public static final int MAX_RESPAWN = 24 * 60, MAX_COINS = 1_000_000;

    public static Preset preset(String id) {
        for (Preset p : PRESETS) if (p.id().equals(id)) return p;
        return null;
    }

    /** El nombre que se enseña: el del preset o «Personalizada» si se ha retocado. */
    public String label() {
        Preset p = preset(preset);
        if (p != null && p.values().sameValues(this)) return p.name();
        return p != null ? p.name() + "*" : "Personalizada";
    }

    public int color() {
        Preset p = preset(preset);
        return p != null ? p.color() : 0xC8C8D2;
    }

    public boolean sameValues(Difficulty o) {
        return o != null && Math.abs(health - o.health) < 1e-6 && Math.abs(damage - o.damage) < 1e-6
                && Math.abs(speed - o.speed) < 1e-6 && Math.abs(rhythm - o.rhythm) < 1e-6 && Math.abs(armor - o.armor) < 1e-6
                && Math.abs(perPlayer - o.perPlayer) < 1e-6 && respawnMin == o.respawnMin && coins == o.coins;
    }

    /** Lo mismo dentro de los límites (lo que llega de la ventana o del archivo). */
    public Difficulty clamped() {
        return new Difficulty(preset == null ? "normal" : preset.toLowerCase(Locale.ROOT), clamp(health, MIN_MUL, MAX_MUL),
                clamp(damage, MIN_MUL, MAX_MUL), clamp(speed, 0.3, 3.0), clamp(rhythm, 0.3, 3.0), clamp(armor, 0, MAX_ARMOR),
                clamp(perPlayer, 0, MAX_PER_PLAYER), (int) clamp(respawnMin, 1, MAX_RESPAWN), (int) clamp(coins, 0, MAX_COINS));
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
    }

    public Difficulty withPreset(String id) {
        Preset p = preset(id);
        return p == null ? this : p.values();
    }

    /** Vida que tendrá con «players» jugadores en la incursión. */
    public double healthFor(double base, int players) {
        return base * health * (1.0 + perPlayer * Math.max(0, players - 1));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("preset", preset);
        o.addProperty("vida", health);
        o.addProperty("dano", damage);
        o.addProperty("velocidad", speed);
        o.addProperty("ritmo", rhythm);
        o.addProperty("armadura", armor);
        o.addProperty("porJugador", perPlayer);
        o.addProperty("recargaMin", respawnMin);
        o.addProperty("monedas", coins);
        return o;
    }

    public static Difficulty fromJson(JsonObject o) {
        if (o == null) return NORMAL;
        Difficulty d = NORMAL;
        Preset p = preset(TFJson.str(o, "preset", "normal"));
        if (p != null) d = p.values();
        return new Difficulty(TFJson.str(o, "preset", d.preset), num(o, "vida", d.health), num(o, "dano", d.damage),
                num(o, "velocidad", d.speed), num(o, "ritmo", d.rhythm), num(o, "armadura", d.armor),
                num(o, "porJugador", d.perPlayer), (int) num(o, "recargaMin", d.respawnMin), (int) num(o, "monedas", d.coins)).clamped();
    }

    private static double num(JsonObject o, String k, double def) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(preset, 32);
        buf.writeDouble(health);
        buf.writeDouble(damage);
        buf.writeDouble(speed);
        buf.writeDouble(rhythm);
        buf.writeDouble(armor);
        buf.writeDouble(perPlayer);
        buf.writeVarInt(respawnMin);
        buf.writeVarInt(coins);
    }

    public static Difficulty read(FriendlyByteBuf buf) {
        return new Difficulty(buf.readUtf(32), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt()).clamped();
    }
}
