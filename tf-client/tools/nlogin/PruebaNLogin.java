/*
 * TF Client — Tierras Fantásticas. Prueba suelta de NLoginLog (la lectura de los registros de nLogin para /metricas)
 * con registros de verdad, sin Minecraft ni Gradle:
 *
 *   javac -d /tmp/nl tf-client/src/main/java/net/tierrasfantasticas/tfclient/server/NLoginLog.java tf-client/tools/nlogin/PruebaNLogin.java
 *   java -cp /tmp/nl PruebaNLogin <carpeta logs de nLogin> <desde AAAA-MM-DD> <eventos.json>
 *
 * Lee como el puente (de 400 en 400 eventos, siguiendo el cursor), escribe los eventos en eventos.json (lo que le llega
 * a la web; para tierras-fantasticas/test) y comprueba:
 *  - leer por partes da lo mismo que de una vez;
 *  - con el último archivo a medias (una línea cortada), la línea cortada no se lee hasta que termina y luego sí, una vez;
 *  - en lo mandado no hay IP ni contraseñas.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.tierrasfantasticas.tfclient.server.NLoginLog;

public class PruebaNLogin {
    static int fallos = 0;

    static void comprueba(boolean ok, String que) {
        System.out.println((ok ? "bien: " : "MAL: ") + que);
        if (!ok) fallos++;
    }

    static List<NLoginLog.Event> todo(Path dir, String desde, int max, long bytes) throws Exception {
        List<NLoginLog.Event> out = new ArrayList<>();
        NLoginLog.Cursor c = null;
        for (int vueltas = 0; vueltas < 100000; vueltas++) {
            NLoginLog.Batch b = NLoginLog.read(dir, desde, c, max, bytes, ZoneId.of("UTC"));
            out.addAll(b.events());
            if (b.to() == null || b.to().equals(c)) break;
            c = b.to();
        }
        return out;
    }

    static String json(NLoginLog.Event e) {
        return "[\"" + e.id() + "\"," + e.t() + ",\"" + e.kind() + "\"," + (e.name() == null ? "null" : "\"" + e.name() + "\"") + ","
                + (e.created() == null ? "null" : e.created()) + "]";
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String desde = args[1];
        List<NLoginLog.Event> porPartes = todo(dir, desde, 400, 2 << 20);
        List<NLoginLog.Event> deUnaVez = todo(dir, desde, Integer.MAX_VALUE, Long.MAX_VALUE / 4);
        comprueba(porPartes.equals(deUnaVez), "por partes (" + porPartes.size() + ") = de una vez (" + deUnaVez.size() + ")");
        List<NLoginLog.Event> pocosBytes = todo(dir, desde, 400, 4096);
        comprueba(pocosBytes.equals(deUnaVez), "leyendo de 4 KB en 4 KB, igual (" + pocosBytes.size() + ")");

        Map<String, Integer> tipos = new TreeMap<>();
        for (NLoginLog.Event e : deUnaVez) tipos.merge(e.kind(), 1, Integer::sum);
        System.out.println("tipos: " + tipos);
        comprueba(tipos.getOrDefault("in", 0) > 0 && tipos.getOrDefault("out", 0) > 0 && tipos.getOrDefault("start", 0) > 0, "hay entradas, salidas y arranques");
        long conCreacion = deUnaVez.stream().filter(e -> e.kind().equals("in") && e.created() != null).count();
        comprueba(conCreacion == tipos.getOrDefault("in", 0), "todas las entradas traen la creación de la cuenta (" + conCreacion + ")");

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < deUnaVez.size(); i++) sb.append(i == 0 ? "" : ",\n").append(json(deUnaVez.get(i)));
        sb.append("]\n");
        String texto = sb.toString();
        comprueba(!texto.matches("(?s).*\\d+\\.\\d+\\.\\d+\\.\\d+.*") && !texto.contains("argon") && !texto.contains("password") && !texto.contains("$"),
                "en lo mandado no hay IP, contraseñas ni huellas");
        Files.writeString(Path.of(args[2]), texto, StandardCharsets.UTF_8);

        // El último archivo a medias: se copia la carpeta y se corta el último a mitad de una línea de entrada
        Path tmp = Files.createTempDirectory("nlogin");
        List<String> nombres = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.forEach(p -> nombres.add(p.getFileName().toString()));
        }
        for (String n : nombres) {
            if (n.endsWith(".log")) Files.copy(dir.resolve(n), tmp.resolve(n));
        }
        List<String> orden = new ArrayList<>(nombres.stream().filter(n -> n.endsWith(".log") && n.compareTo(desde) >= 0).toList());
        orden.sort(Comparator());
        String ultimo = orden.get(orden.size() - 1);
        byte[] entero = Files.readAllBytes(dir.resolve(ultimo));
        String txt = new String(entero, StandardCharsets.UTF_8);
        int corte = txt.lastIndexOf("(applied to [");
        comprueba(corte > 0, "el último registro tiene una línea de base de datos para cortar");
        int bytesCorte = txt.substring(0, corte).getBytes(StandardCharsets.UTF_8).length;
        Files.write(tmp.resolve(ultimo), java.util.Arrays.copyOf(entero, bytesCorte));
        List<NLoginLog.Event> antes = new ArrayList<>();
        NLoginLog.Cursor c = null;
        while (true) {
            NLoginLog.Batch b = NLoginLog.read(tmp, desde, c, 400, 2 << 20, ZoneId.of("UTC"));
            antes.addAll(b.events());
            if (b.to() == null || b.to().equals(c)) break;
            c = b.to();
        }
        Files.write(tmp.resolve(ultimo), java.util.Arrays.copyOfRange(entero, bytesCorte, entero.length), StandardOpenOption.APPEND);
        List<NLoginLog.Event> despues = new ArrayList<>(antes);
        while (true) {
            NLoginLog.Batch b = NLoginLog.read(tmp, desde, c, 400, 2 << 20, ZoneId.of("UTC"));
            despues.addAll(b.events());
            if (b.to() == null || b.to().equals(c)) break;
            c = b.to();
        }
        comprueba(antes.size() == deUnaVez.size() - 1 || antes.size() < deUnaVez.size(), "con la línea cortada falta esa línea (" + antes.size() + " de " + deUnaVez.size() + ")");
        comprueba(despues.equals(deUnaVez), "al terminar de escribirse, se lee una vez y queda todo igual (" + despues.size() + ")");

        System.out.println(fallos == 0 ? "NLOGIN: TODO BIEN" : "NLOGIN: " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }

    static java.util.Comparator<String> Comparator() {
        return java.util.Comparator.comparing((String n) -> n.substring(0, 10)).thenComparingInt(n -> Integer.parseInt(n.substring(11, n.length() - 4)));
    }
}
