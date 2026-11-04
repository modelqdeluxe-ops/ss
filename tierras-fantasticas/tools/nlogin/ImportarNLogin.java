/*
 * Tierras Fantásticas — carga a mano en /metricas los registros de nLogin (cuando el servidor no tiene el TF Client
 * 1.3.174, que los manda solo). Lee los registros con el mismo NLoginLog del mod y los manda a POST /api/metricas/importar
 * de 400 en 400, empezando donde va la web (así, si luego el mod los manda, sigue justo después y no repite nada).
 * Uso: importar.sh (lo compila y lo lanza). La contraseña va en la variable METRICAS_PASSWORD, nunca en la orden.
 *   java -cp <clases> ImportarNLogin <carpeta nLogin> <web> [cuentas.json] [zona, UTC]
 */
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.tierrasfantasticas.tfclient.server.NLoginLog;

public class ImportarNLogin {
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    static String web;
    static String cookie;

    static HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(web + path)).header("Content-Type", "application/json");
        if (cookie != null) b.header("Cookie", cookie);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static String q(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String cursor(NLoginLog.Cursor c) {
        return c == null ? "null" : "{\"file\":" + q(c.file()) + ",\"offset\":" + c.offset() + "}";
    }

    static NLoginLog.Cursor parseCursor(String json) {
        Matcher m = Pattern.compile("\"cursor\":\\{\"file\":\"([^\"]+)\",\"offset\":(\\d+)\\}").matcher(json);
        return m.find() ? new NLoginLog.Cursor(m.group(1), Long.parseLong(m.group(2))) : null;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]).resolve("logs");
        web = args[1].replaceAll("/+$", "");
        String password = System.getenv("METRICAS_PASSWORD");
        ZoneId zone = ZoneId.of(args.length > 3 ? args[3] : "UTC");
        if (password == null || password.isBlank()) throw new IllegalStateException("Falta METRICAS_PASSWORD");
        HttpResponse<String> r = send("POST", "/api/metricas/entrar", "{\"password\":" + q(password) + "}");
        if (r.statusCode() != 200) throw new IllegalStateException("No entra: " + r.statusCode() + " " + r.body());
        cookie = r.headers().firstValue("set-cookie").orElseThrow().split(";")[0];

        if (args.length > 2 && !args[2].isEmpty()) {
            String accounts = Files.readString(Path.of(args[2]), StandardCharsets.UTF_8).trim();
            r = send("POST", "/api/metricas/importar", "{\"accounts\":" + accounts + "}");
            System.out.println("cuentas: " + r.statusCode() + " " + r.body());
        }

        r = send("GET", "/api/metricas", null);
        String info = r.body();
        Matcher d = Pattern.compile("\"inicio\":\"(\\d{4}-\\d{2}-\\d{2})\"").matcher(info);
        String desde = d.find() ? d.group(1) : "2026-10-26";
        NLoginLog.Cursor c = parseCursor(info);
        System.out.println("la web va por " + cursor(c) + ", desde el " + desde);
        int total = 0;
        while (true) {
            NLoginLog.Batch b = NLoginLog.read(dir, desde, c, 400, 2L << 20, zone);
            if (b.to() == null || b.to().equals(c)) break;
            StringBuilder ev = new StringBuilder("[");
            for (int i = 0; i < b.events().size(); i++) {
                NLoginLog.Event e = b.events().get(i);
                if (i > 0) ev.append(',');
                ev.append('[').append(q(e.id())).append(',').append(e.t()).append(',').append(q(e.kind())).append(',').append(q(e.name())).append(',')
                        .append(e.created() == null ? "null" : e.created()).append(']');
            }
            ev.append(']');
            r = send("POST", "/api/metricas/importar", "{\"nlogin\":{\"from\":" + cursor(c) + ",\"to\":" + cursor(b.to()) + ",\"events\":" + ev + "}}");
            NLoginLog.Cursor now = parseCursor(r.body());
            if (r.statusCode() != 200 || now == null || !now.equals(b.to())) throw new IllegalStateException("La web no lo guardó: " + r.statusCode() + " " + r.body());
            total += b.events().size();
            System.out.println("  " + b.events().size() + " eventos hasta " + cursor(b.to()));
            c = b.to();
        }
        System.out.println("listo: " + total + " eventos cargados");
    }
}
