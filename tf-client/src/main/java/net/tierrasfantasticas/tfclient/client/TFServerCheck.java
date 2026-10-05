package net.tierrasfantasticas.tfclient.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.fml.ModList;

/**
 * Antes de conectar, pregunta al servidor (con el ping de la lista de servidores) qué mods usa y los compara con los
 * instalados. Así se avisa de los que faltan en vez de quedarse esperando en "Conectando".
 *
 * Solo cuentan los mods que el cliente necesita de verdad: los que tienen algún canal de red obligatorio (es lo mismo
 * que comprueba Forge al entrar). Los del servidor (Mohist, plugins, mods solo de servidor) no se piden.
 */
public final class TFServerCheck {
    /** Mods que no cuentan: los trae cualquier cliente de Forge. */
    private static final Set<String> IGNORED = Set.of("minecraft", "forge", "mcp");
    /** Partes del servidor híbrido (Mohist y similares): nunca van en el cliente. */
    private static final Set<String> SERVER_PLATFORMS = Set.of("mohist", "bukkit", "craftbukkit", "spigot", "paper",
            "arclight", "magma", "ketting", "youer", "banner", "catserver", "crucible", "spongeforge", "sponge");

    public record MissingMod(String id, String version) {}

    public record Result(boolean reachable, boolean forge, List<MissingMod> missing, String error) {}

    private TFServerCheck() {}

    public static Result check(String address) {
        try {
            ServerAddress server = ServerAddress.parseString(address);
            JsonObject status = ping(server.getHost(), server.getPort());
            JsonElement forgeData = status.get("forgeData");
            if (forgeData == null || !forgeData.isJsonObject()) return new Result(true, false, List.of(), null);
            List<String[]> serverMods = readMods(forgeData.getAsJsonObject());
            List<MissingMod> missing = new ArrayList<>();
            for (String[] mod : serverMods) {
                String id = mod[0];
                boolean serverOnly = mod[2] != null;
                boolean clientNeeds = mod[3] != null;
                if (serverOnly || !clientNeeds || IGNORED.contains(id) || isServerPlatform(id)
                        || ModList.get().isLoaded(id)) continue;
                missing.add(new MissingMod(id, mod[1]));
            }
            missing.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
            return new Result(true, true, missing, null);
        } catch (Exception e) {
            return new Result(false, false, List.of(), e.getMessage());
        }
    }

    private static boolean isServerPlatform(String id) {
        return SERVER_PLATFORMS.contains(id.toLowerCase(Locale.ROOT));
    }

    private static JsonObject ping(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 6000);
            socket.setSoTimeout(8000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(handshakeBytes);
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, 763); // 1.20.1
            byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(handshake, hostBytes.length);
            handshake.write(hostBytes);
            handshake.writeShort(port);
            writeVarInt(handshake, 1); // estado
            writeVarInt(out, handshakeBytes.size());
            out.write(handshakeBytes.toByteArray());
            out.write(new byte[] {0x01, 0x00}); // petición de estado
            out.flush();

            DataInputStream in = new DataInputStream(socket.getInputStream());
            readVarInt(in); // longitud del paquete
            readVarInt(in); // id del paquete
            byte[] json = new byte[readVarInt(in)];
            in.readFully(json);
            return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /**
     * Lista de mods del servidor: {id, versión, marcador de "solo servidor" o null, marcador de "el cliente lo
     * necesita" (tiene un canal obligatorio) o null}.
     */
    private static List<String[]> readMods(JsonObject forgeData) {
        List<String[]> mods = new ArrayList<>();
        if (forgeData.has("d")) {
            decodeOptimized(forgeData.get("d").getAsString(), mods);
        } else if (forgeData.has("mods")) {
            Set<String> required = new HashSet<>();
            if (forgeData.has("channels")) {
                for (JsonElement element : forgeData.getAsJsonArray("channels")) {
                    JsonObject channel = element.getAsJsonObject();
                    if (!channel.has("res") || !channel.has("required") || !channel.get("required").getAsBoolean()) continue;
                    String res = channel.get("res").getAsString();
                    required.add(res.contains(":") ? res.substring(0, res.indexOf(':')) : "minecraft");
                }
            }
            JsonArray array = forgeData.getAsJsonArray("mods");
            for (JsonElement element : array) {
                JsonObject mod = element.getAsJsonObject();
                String id = mod.get("modId").getAsString();
                String marker = mod.has("modmarker") ? mod.get("modmarker").getAsString() : "";
                mods.add(new String[] {id, marker, marker.toUpperCase(Locale.ROOT).contains("IGNORE") ? marker : null,
                        required.contains(id) ? "required" : null});
            }
        }
        return mods;
    }

    /** Formato comprimido de Forge (ServerStatusPing): 15 bits de datos por carácter. */
    private static void decodeOptimized(String s, List<String[]> mods) {
        int size = s.charAt(0) | (s.charAt(1) << 15);
        byte[] data = new byte[size];
        int written = 0;
        long buffer = 0;
        int bits = 0;
        for (int i = 2; i < s.length() && written < size; i++) {
            while (bits >= 8 && written < size) {
                data[written++] = (byte) buffer;
                buffer >>>= 8;
                bits -= 8;
            }
            buffer |= (long) (s.charAt(i) & 0x7FFF) << bits;
            bits += 15;
        }
        while (written < size) {
            data[written++] = (byte) buffer;
            buffer >>>= 8;
        }

        Reader reader = new Reader(data);
        reader.readByte(); // lista truncada
        int count = reader.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int flag = reader.readVarInt();
            int channels = flag >>> 1;
            boolean ignoreServerOnly = (flag & 1) != 0;
            String id = reader.readUtf();
            String version = ignoreServerOnly ? "" : reader.readUtf();
            boolean required = false;
            for (int c = 0; c < channels; c++) {
                reader.readUtf(); // canal
                reader.readUtf(); // versión del canal
                required |= reader.readByte() != 0; // el cliente debe tenerlo
            }
            mods.add(new String[] {id, version, ignoreServerOnly ? "server-only" : null, required ? "required" : null});
        }
    }

    private static final class Reader {
        private final byte[] data;
        private int pos;

        Reader(byte[] data) {
            this.data = data;
        }

        int readByte() {
            return data[pos++] & 0xFF;
        }

        int readUnsignedShort() {
            return (readByte() << 8) | readByte();
        }

        int readVarInt() {
            int value = 0;
            for (int i = 0; i < 5; i++) {
                int b = readByte();
                value |= (b & 0x7F) << (7 * i);
                if ((b & 0x80) == 0) return value;
            }
            return value;
        }

        String readUtf() {
            int length = readVarInt();
            String value = new String(data, pos, length, StandardCharsets.UTF_8);
            pos += length;
            return value;
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarInt demasiado largo");
    }
}
