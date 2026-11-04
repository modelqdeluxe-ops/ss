/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * (1.3.174, el dueño: «que la web con el puente se conecte a la data de nLogin y saque esas gráficas sola») Lee los
 * registros del plugin nLogin ({@code plugins/nLogin/logs/AAAA-MM-DD-N.log}, uno por arranque) y saca lo que necesitan
 * las métricas de la web: quién entra y sale y cuándo, quién se registra, cuándo arranca y se apaga el servidor, quién
 * intenta entrar con otra versión de Minecraft y cuándo Mojang no contesta.
 *
 * <p>De cada línea solo se toman el nombre, la fecha de creación de la cuenta y la hora: las líneas de nLogin llevan
 * también la IP y la huella de la contraseña, que <b>nunca</b> salen de aquí.
 *
 * <p>Solo Java (sin Minecraft): se prueba suelto con los registros de verdad ({@code tools/nlogin/}).
 */
public final class NLoginLog {
    private NLoginLog() {}

    /** Un evento: id = archivo:byte de la línea (único), t = hora (ms), kind = in/out/reg/start/stop/verr/mojang. */
    public record Event(String id, long t, String kind, String name, Long created) {}

    /** Por dónde va la lectura: el archivo y el byte siguiente a la última línea leída. */
    public record Cursor(String file, long offset) {}

    /** Lo leído de from a to (to = from si no había nada nuevo). */
    public record Batch(Cursor from, Cursor to, List<Event> events) {}

    private static final Pattern FILE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(\\d{1,6})\\.log");
    private static final Pattern LINE = Pattern.compile("^\\[(\\d{4})/(\\d{2})/(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2}) [A-Z]+\\]: (.*)$");
    private static final Pattern NAME = Pattern.compile("`last_name` = '([A-Za-z0-9_.*-]{1,32})'");
    private static final Pattern CREATED = Pattern.compile("`creation_date` = (\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?");
    private static final Pattern APPLIED = Pattern.compile("\\(applied to \\[([^\\]]*)\\] columns\\)\\s*$");
    private static final Pattern REG = Pattern.compile("^The user ([A-Za-z0-9_.*-]{1,32}) has successfully registered");
    private static final Pattern MOJANG = Pattern.compile("^Unable to contact Mojang servers for ([A-Za-z0-9_.*-]{1,32}):");
    private static final Pattern VERSION = Pattern.compile("client = (V_[0-9_]{1,12})");

    /** Orden de los archivos: por fecha y número de arranque (2026-11-04-10 va después de 2026-11-04-9). */
    static final Comparator<String> ORDER = Comparator.comparing((String n) -> {
        Matcher m = FILE.matcher(n);
        return m.matches() ? m.group(1) : n;
    }).thenComparingInt(n -> {
        Matcher m = FILE.matcher(n);
        return m.matches() ? Integer.parseInt(m.group(2)) : 0;
    });

    /** Los registros desde el día since (AAAA-MM-DD), en orden. */
    static List<String> files(Path dir, String since) throws IOException {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> list = Files.list(dir)) {
            list.map(p -> p.getFileName().toString()).forEach(n -> {
                Matcher m = FILE.matcher(n);
                if (m.matches() && m.group(1).compareTo(since) >= 0) out.add(n);
            });
        }
        out.sort(ORDER);
        return out;
    }

    /**
     * Lee desde from (null = el primer registro desde since) hasta max eventos o maxBytes bytes. Un archivo que ya tiene
     * otro detrás está terminado (nLogin abre uno nuevo en cada arranque): se pasa al siguiente. En el último, solo se
     * leen las líneas completas (la que nLogin está escribiendo se lee la próxima vez).
     */
    public static Batch read(Path dir, String since, Cursor from, int max, long maxBytes, ZoneId zone) throws IOException {
        List<String> names = files(dir, since);
        List<Event> events = new ArrayList<>();
        int i = 0;
        long offset = 0;
        if (from != null) {
            i = names.indexOf(from.file());
            if (i >= 0) {
                offset = from.offset();
            } else {
                // ya no está (se borró): el siguiente en orden
                i = 0;
                while (i < names.size() && ORDER.compare(names.get(i), from.file()) <= 0) i++;
            }
        }
        Cursor to = from;
        long budget = maxBytes;
        while (i < names.size() && events.size() < max && budget > 0) {
            String name = names.get(i);
            boolean last = i == names.size() - 1;
            Path file = dir.resolve(name);
            long size = Files.size(file);
            if (offset > size) offset = size;
            long pos = offset;
            if (offset < size) {
                int length = (int) Math.min(size - offset, budget);
                byte[] data = new byte[length];
                try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                    raf.seek(offset);
                    raf.readFully(data);
                }
                budget -= length;
                int start = 0;
                for (int j = 0; j < data.length && events.size() < max; j++) {
                    if (data[j] != '\n') continue;
                    Event e = parse(name + ":" + (offset + start), new String(data, start, j - start, StandardCharsets.UTF_8), zone);
                    if (e != null) events.add(e);
                    start = j + 1;
                }
                // la última línea sin salto: completa solo si el archivo ya terminó (hay otro detrás)
                if (!last && offset + length == size && start < data.length && events.size() < max) {
                    Event e = parse(name + ":" + (offset + start), new String(data, start, data.length - start, StandardCharsets.UTF_8), zone);
                    if (e != null) events.add(e);
                    start = data.length;
                }
                pos = offset + start;
            }
            to = new Cursor(name, pos);
            // a medias (tope de eventos o de bytes) o el último, que nLogin sigue escribiendo: aquí se queda
            if (pos < size || last) break;
            i++;
            offset = 0;
            to = new Cursor(names.get(i), 0);
        }
        return new Batch(from, to == null ? from : to, events);
    }

    /** Una línea del registro → evento, o null si no es de los que cuentan. */
    static Event parse(String id, String raw, ZoneId zone) {
        String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
        Matcher m = LINE.matcher(line);
        if (!m.matches()) return null;
        long t;
        try {
            t = LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)))
                    .atZone(zone).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return null;
        }
        String msg = m.group(7);
        if (msg.contains("[UPDATE]")) {
            Matcher applied = APPLIED.matcher(msg);
            Matcher name = NAME.matcher(msg);
            if (!applied.find() || !name.find()) return null;
            String cols = applied.group(1);
            String kind;
            if (cols.contains("last_ip") && cols.contains("last_seen")) kind = "in";
            else if (cols.contains("last_seen")) kind = "out";
            else return null;
            return new Event(id, t, kind, name.group(1), created(msg, zone));
        }
        if (msg.contains("By: www.nickuc.com")) return new Event(id, t, "start", null, null);
        if (msg.startsWith("Successful plugin core shutdown")) return new Event(id, t, "stop", null, null);
        if (msg.startsWith("Unable to handle incoming packet LOGIN_START")) {
            Matcher v = VERSION.matcher(msg);
            return new Event(id, t, "verr", v.find() ? v.group(1) : null, null);
        }
        Matcher reg = REG.matcher(msg);
        if (reg.find()) return new Event(id, t, "reg", reg.group(1), null);
        Matcher mojang = MOJANG.matcher(msg);
        if (mojang.find()) return new Event(id, t, "mojang", mojang.group(1), null);
        return null;
    }

    private static Long created(String msg, ZoneId zone) {
        Matcher c = CREATED.matcher(msg);
        if (!c.find()) return null;
        try {
            int ms = c.group(7) == null ? 0 : Integer.parseInt((c.group(7) + "00").substring(0, 3));
            return LocalDateTime.of(Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), Integer.parseInt(c.group(3)),
                    Integer.parseInt(c.group(4)), Integer.parseInt(c.group(5)), Integer.parseInt(c.group(6)), ms * 1_000_000)
                    .atZone(zone).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
