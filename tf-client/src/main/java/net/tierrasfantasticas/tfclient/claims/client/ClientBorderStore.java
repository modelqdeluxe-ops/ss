package net.tierrasfantasticas.tfclient.claims.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Los contornos que manda el servidor (cajas {x0, y0, z0, x1, y1, z1, r, g, b}). Al recibirlos se convierten en
 * «paredes» (los lados de la zona, sin techo) y «esquinas» (las columnas verticales), que es lo que dibuja el polvo
 * mágico. Sin clases del cliente: el paquete lo toca también en el servidor dedicado.
 */
public final class ClientBorderStore {
    private static final long STALE_AFTER_MS = 60000L;
    // Por debajo de este grosor una caja es una pared suelta (los contornos de grupo vienen así, de 0,06 de grueso)
    private static final double THIN = 0.1;
    private static volatile List<double[]> boxes = Collections.emptyList();
    private static volatile List<double[]> walls = Collections.emptyList();
    private static volatile List<double[]> corners = Collections.emptyList();
    private static volatile long lastUpdate = 0L;

    private ClientBorderStore() {
    }

    public static void receive(List<double[]> list) {
        List<double[]> list1 = list == null ? Collections.emptyList() : list;
        ArrayList<double[]> arraylist = new ArrayList<double[]>();
        ArrayList<double[]> arraylist1 = new ArrayList<double[]>();
        ClientBorderStore.toWalls(list1, arraylist, arraylist1);
        boxes = list1;
        walls = arraylist;
        corners = arraylist1;
        lastUpdate = System.currentTimeMillis();
    }

    public static void clear() {
        boxes = Collections.emptyList();
        walls = Collections.emptyList();
        corners = Collections.emptyList();
        lastUpdate = 0L;
    }

    private static boolean stale() {
        return System.currentTimeMillis() - lastUpdate > STALE_AFTER_MS;
    }

    public static List<double[]> current() {
        List<double[]> list = boxes;
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return ClientBorderStore.stale() ? Collections.emptyList() : list;
    }

    /** Paredes {eje (0 = va a lo largo de X con z fija, 1 = a lo largo de Z con x fija), fija, u0, u1, abajo, arriba, r, g, b}. */
    public static List<double[]> walls() {
        List<double[]> list = walls;
        return list.isEmpty() || ClientBorderStore.stale() ? Collections.emptyList() : list;
    }

    /** Esquinas {x, z, abajo, arriba, r, g, b}: las líneas verticales (sin el cuadrado de arriba). */
    public static List<double[]> corners() {
        List<double[]> list = corners;
        return list.isEmpty() || ClientBorderStore.stale() ? Collections.emptyList() : list;
    }

    /**
     * Pasa cajas a paredes y esquinas. Una caja normal da sus 4 lados; una caja muy fina (pared de un grupo) da un solo
     * lado. Las esquinas repetidas (dos paredes que se tocan) se quedan en una.
     */
    public static void toWalls(List<double[]> list, List<double[]> wallsOut, List<double[]> cornersOut) {
        Set<Long> set = new HashSet<Long>();
        for (double[] adouble : list) {
            double d0 = Math.min(adouble[0], adouble[3]);
            double d1 = Math.max(adouble[0], adouble[3]);
            double d2 = Math.min(adouble[2], adouble[5]);
            double d3 = Math.max(adouble[2], adouble[5]);
            double d4 = Math.min(adouble[1], adouble[4]);
            double d5 = Math.max(adouble[1], adouble[4]);
            double r = adouble[6];
            double g = adouble[7];
            double b = adouble[8];
            boolean thinX = d1 - d0 < THIN;
            boolean thinZ = d3 - d2 < THIN;
            if (thinX && thinZ) continue;
            if (thinX) {
                double d6 = (d0 + d1) / 2.0;
                wallsOut.add(new double[]{1, d6, d2, d3, d4, d5, r, g, b});
                ClientBorderStore.addCorner(set, cornersOut, d6, d2, d4, d5, r, g, b);
                ClientBorderStore.addCorner(set, cornersOut, d6, d3, d4, d5, r, g, b);
            } else if (thinZ) {
                double d7 = (d2 + d3) / 2.0;
                wallsOut.add(new double[]{0, d7, d0, d1, d4, d5, r, g, b});
                ClientBorderStore.addCorner(set, cornersOut, d0, d7, d4, d5, r, g, b);
                ClientBorderStore.addCorner(set, cornersOut, d1, d7, d4, d5, r, g, b);
            } else {
                wallsOut.add(new double[]{0, d2, d0, d1, d4, d5, r, g, b});
                wallsOut.add(new double[]{0, d3, d0, d1, d4, d5, r, g, b});
                wallsOut.add(new double[]{1, d0, d2, d3, d4, d5, r, g, b});
                wallsOut.add(new double[]{1, d1, d2, d3, d4, d5, r, g, b});
                ClientBorderStore.addCorner(set, cornersOut, d0, d2, d4, d5, r, g, b);
                ClientBorderStore.addCorner(set, cornersOut, d1, d2, d4, d5, r, g, b);
                ClientBorderStore.addCorner(set, cornersOut, d0, d3, d4, d5, r, g, b);
                ClientBorderStore.addCorner(set, cornersOut, d1, d3, d4, d5, r, g, b);
            }
        }
    }

    private static void addCorner(Set<Long> set, List<double[]> list, double x, double z, double bottom, double top, double r, double g, double b) {
        long i = Math.round(x * 4.0);
        long j = Math.round(z * 4.0);
        if (set.add(i << 32 ^ (j & 0xFFFFFFFFL))) {
            list.add(new double[]{x, z, bottom, top, r, g, b});
        }
    }
}
