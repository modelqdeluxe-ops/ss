package net.tierrasfantasticas.tfclient.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los hogares de cada rango en el config.yml de EssentialsX: el bloque «sethome-multiple» (grupo → número de hogares,
 * que EssentialsX da a quien tenga el permiso essentials.sethome.multiple.&lt;grupo&gt;). Solo cambia o añade las líneas
 * de los rangos; lo demás del archivo (comentarios, otros grupos como «default», el resto de ajustes) queda igual.
 */
public final class EssentialsHomes {
    private static final Pattern HEADER = Pattern.compile("^sethome-multiple:\\s*(#.*)?$");
    private static final Pattern INLINE = Pattern.compile("^sethome-multiple:\\s*\\{(.*)}\\s*(#.*)?$");
    private static final Pattern ENTRY = Pattern.compile("^([ \\t]+)(['\"]?)([^\\s:'\"#]+)\\2:[ \\t]*([^#\\s]*)([ \\t]*#.*)?$");

    private EssentialsHomes() {}

    /** El archivo con los hogares de los rangos puestos (igual que antes si ya estaban así). */
    public static String update(String yaml, Map<String, Integer> homes) {
        String nl = yaml.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(List.of(yaml.split("\r?\n", -1)));
        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (HEADER.matcher(line).matches()) {
                header = i;
                break;
            }
            Matcher inline = INLINE.matcher(line);
            if (inline.matches()) {
                // «sethome-multiple: {default: 3, vip: 5}» → en bloque, con los mismos valores
                List<String> block = new ArrayList<>();
                block.add("sethome-multiple:" + (inline.group(2) == null ? "" : " " + inline.group(2)));
                for (String part : inline.group(1).split(",")) {
                    String[] kv = part.split(":", 2);
                    if (kv.length == 2 && !kv[0].isBlank()) block.add("  " + kv[0].trim() + ": " + kv[1].trim());
                }
                lines.remove(i);
                lines.addAll(i, block);
                header = i;
                break;
            }
        }
        if (header < 0) {
            // No está: se añade al final
            int end = lines.size();
            while (end > 0 && lines.get(end - 1).isBlank()) end--;
            List<String> block = new ArrayList<>();
            if (end > 0) block.add("");
            block.add("sethome-multiple:");
            for (Map.Entry<String, Integer> e : homes.entrySet()) block.add("  " + e.getKey() + ": " + e.getValue());
            lines.addAll(end, block);
            return String.join(nl, lines);
        }

        // Las líneas del bloque: sangradas (o en blanco) hasta la siguiente clave o comentario sin sangría
        Map<String, Integer> found = new LinkedHashMap<>();
        String indent = "  ";
        int last = header;
        for (int i = header + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            if (!Character.isWhitespace(line.charAt(0))) break;
            Matcher m = ENTRY.matcher(line);
            if (m.matches()) {
                if (found.isEmpty()) indent = m.group(1);
                found.put(m.group(3), i);
            }
            last = i;
        }
        int insertAt = last + 1;
        for (Map.Entry<String, Integer> e : homes.entrySet()) {
            Integer at = found.get(e.getKey());
            if (at != null) {
                Matcher m = ENTRY.matcher(lines.get(at));
                if (!m.matches()) continue;
                String value = Integer.toString(e.getValue());
                if (value.equals(m.group(4))) continue;
                lines.set(at, m.group(1) + m.group(2) + m.group(3) + m.group(2) + ": " + value + (m.group(5) == null ? "" : m.group(5)));
            } else {
                lines.add(insertAt++, indent + e.getKey() + ": " + e.getValue());
            }
        }
        return String.join(nl, lines);
    }
}
