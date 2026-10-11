package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Un premio del Gachapón o del TF Pass (1.3.38), tal como se escribe en gachapon.json y pase.json:
 * <pre>
 *   {"tipo": "verdes",  "cantidad": 3}                          monedas verdes (las del gacha)
 *   {"tipo": "monedas", "cantidad": 500}                        monedas normales (la economía del servidor)
 *   {"tipo": "objeto",  "id": "minecraft:diamond", "cantidad": 8, "nbt": "{...}"}   un objeto (nbt opcional: el de /data)
 *   {"tipo": "comando", "comando": "lp user {player} permission set x true", "nombre": "Permiso X", "icono": "minecraft:name_tag"}
 * </pre>
 * Todos aceptan «nombre» (lo que se ve en el pad; si no, se pone solo) e «icono» (id de un objeto). En los comandos,
 * {player} es el nombre y {uuid} la UUID; se ejecutan como la consola.
 */
public record PadPrize(String type, long amount, String id, String nbt, String command, String name, String icon) {
    public static final String GREEN_ICON = "tfclient:fantastic_coin_green", COIN_ICON = "tfclient:fantastic_coin";

    /** Lee un premio; null si está mal escrito (se avisa en el registro con where). */
    public static PadPrize read(JsonElement e, String where) {
        try {
            JsonObject o = e.getAsJsonObject();
            String type = TFJson.str(o, "tipo", "objeto").toLowerCase(java.util.Locale.ROOT);
            long amount = Math.max(1, TFJson.num(o, "cantidad", 1));
            String id = TFJson.str(o, "id", "");
            String nbt = TFJson.str(o, "nbt", "");
            String cmd = TFJson.str(o, "comando", "");
            switch (type) {
                case "verdes", "monedas" -> {
                }
                case "objeto" -> {
                    if (!nbt.isEmpty()) TagParser.parseTag(nbt); // que se lea bien antes de aceptarlo
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
                    if (nbt.isEmpty() && (item == null || item == Items.AIR)) throw new IllegalArgumentException("objeto desconocido: " + id);
                }
                case "comando" -> {
                    if (cmd.isBlank()) throw new IllegalArgumentException("falta «comando»");
                }
                default -> throw new IllegalArgumentException("tipo desconocido: " + type);
            }
            return new PadPrize(type, amount, id, nbt, cmd.startsWith("/") ? cmd.substring(1) : cmd, TFJson.str(o, "nombre", ""),
                    TFJson.str(o, "icono", ""));
        } catch (Exception ex) {
            TFClient.LOGGER.warn("TF Pad: {}: un premio no se pudo leer ({})", where, ex.getMessage());
            return null;
        }
    }

    /** El objeto que se da (o null si no es un objeto). */
    public ItemStack stack() {
        if (!type.equals("objeto")) return null;
        try {
            if (!nbt.isEmpty()) {
                ItemStack s = ItemStack.of(TagParser.parseTag(nbt));
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) {
            // nbt roto: se usa el id
        }
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        if (item == null || item == Items.AIR) return null;
        return new ItemStack(item, (int) Math.min(amount, item.getMaxStackSize() * 36L));
    }

    /** El objeto que se ve como icono. */
    public String iconId() {
        if (!icon.isEmpty()) return icon;
        return switch (type) {
            case "verdes" -> GREEN_ICON;
            case "monedas" -> COIN_ICON;
            case "objeto" -> id.isEmpty() ? "minecraft:chest" : id;
            default -> "minecraft:paper";
        };
    }

    /** El nombre en el pad: el de la config o uno corto («3 monedas verdes», «500 monedas»); los objetos los nombra el
     *  cliente en su idioma (aquí van vacíos). */
    public String shownName() {
        if (!name.isEmpty()) return name;
        return switch (type) {
            case "verdes" -> amount + (amount == 1 ? " moneda verde" : " monedas verdes");
            case "monedas" -> TFEconomy.number(amount) + (amount == 1 ? " moneda" : " monedas");
            case "comando" -> "Premio especial";
            default -> "";
        };
    }

    /** Para el cliente: {tipo, n, id, nombre, nbt?}. */
    public JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("tipo", type);
        o.addProperty("n", amount);
        o.addProperty("id", iconId());
        o.addProperty("nombre", shownName());
        if (type.equals("objeto") && !nbt.isEmpty() && nbt.length() < 2000) o.addProperty("nbt", nbt);
        return o;
    }

    /** Lo da. false si no se pudo (la economía falló, el comando dio error…). */
    public boolean give(ServerPlayer player) {
        switch (type) {
            case "verdes" -> {
                PadGreen.add(player, amount);
                return true;
            }
            case "monedas" -> {
                return TFEconomy.give(player.getServer(), player.getUUID(), player.getGameProfile().getName(), amount);
            }
            case "objeto" -> {
                ItemStack s = stack();
                if (s == null) return false;
                PadKits.give(player, s);
                return true;
            }
            case "comando" -> {
                String c = command.replace("{player}", player.getGameProfile().getName()).replace("{uuid}", player.getStringUUID());
                List<String> errors = TFBridge.run(player.getServer(), c);
                if (!errors.isEmpty()) TFClient.LOGGER.warn("TF Pad: el premio «{}» falló: {}", c, errors.get(0));
                return errors.isEmpty();
            }
            default -> {
                return false;
            }
        }
    }

    /** Para escribir la config por defecto. */
    static JsonObject of(String type, long amount, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("tipo", type);
        o.addProperty("cantidad", amount);
        if (id != null) o.addProperty("id", id);
        return o;
    }
}
