package net.tierrasfantasticas.tfclient.util;

import java.time.LocalDate;
import java.util.Random;

/**
 * Rotación de misiones y cazas por temporadas de 90 días (el «colchón de 3 meses»): de una lista de n familias (qué
 * hacer: tipo y objetivo) se sacan k por periodo (día, semana o 12 h) recorriendo un orden barajado distinto en cada
 * temporada. Cada familia sale una vez por vuelta y, en cada vuelta, con más cantidad y más premio (de x1 al empezar la
 * temporada a x2,5 al acabarla). Así, en toda la temporada no se repite ninguna misión igual (misma familia y misma
 * cantidad) y se van poniendo más difíciles. Todo sale de la fecha: no hay que guardar nada y todos ven lo mismo.
 */
public final class TFRotation {
    /** Día 0 de las temporadas (1 de octubre de 2026). */
    public static final long EPOCH_DAY = LocalDate.of(2026, 10, 1).toEpochDay();
    public static final int SEASON_DAYS = 90;
    /** Al final de la temporada, la cantidad y el premio van multiplicados por esto. */
    private static final double MAX_FACTOR = 2.5;

    private TFRotation() {}

    /** Días desde el día 0 (hoy, en la hora del servidor). */
    public static long day() {
        return LocalDate.now().toEpochDay() - EPOCH_DAY;
    }

    /** Lo que toca: el índice de la familia y la vuelta (0, 1, 2…) en la que va. */
    public record Pick(int index, int round, double factor) {}

    /**
     * Las k familias del periodo. period: el número de periodo desde el día 0; perSeason: cuántos periodos tiene una
     * temporada (90 días, 13 semanas, 180 medios días); salt distingue listas (oficio, diarias, cazas); offset desplaza
     * el recorrido (por ejemplo, por jugador, para que cada uno tenga las suyas).
     */
    public static Pick[] pick(int n, int k, long period, long perSeason, long salt, long offset) {
        if (n <= 0 || k <= 0) return new Pick[0];
        k = Math.min(k, n);
        long season = Math.floorDiv(period, perSeason);
        long p = Math.floorMod(period, perSeason);
        int[] order = order(n, season * 1_000_003L + salt * 7_919L);
        long rounds = Math.max(1, (perSeason * k + n - 1) / n);
        long start = p * k + Math.floorMod(offset, n);
        Pick[] out = new Pick[k];
        for (int j = 0; j < k; j++) {
            long s = start + j;
            int round = (int) (s / n);
            double factor = rounds <= 1 ? 1 : 1 + (MAX_FACTOR - 1) * Math.min(1.0, round / (double) (rounds - 1));
            out[j] = new Pick(order[(int) (s % n)], round, factor);
        }
        return out;
    }

    /** Un orden barajado de 0..n-1, siempre el mismo para la misma semilla. */
    private static int[] order(int n, long seed) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        Random r = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    /** Una cantidad con el factor, redondeada «bonita» (de 5 en 5 si pasa de 20). */
    public static int amount(int base, double factor) {
        double v = Math.max(1, base * factor);
        if (v > 20) return (int) (Math.round(v / 5) * 5);
        return (int) Math.round(v);
    }

    /** Un premio con el factor, redondeado a entero. */
    public static long coins(double base, double factor) {
        return Math.round(Math.max(0, base * factor));
    }
}
