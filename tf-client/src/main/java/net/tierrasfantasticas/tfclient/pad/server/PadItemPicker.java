package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;

/**
 * Elegir objetos en el pad de administrador (para kits y recompensas): de TU INVENTARIO (tal cual, con su cantidad,
 * encantamientos y nombre) o de TODOS los objetos del juego, también los de los mods, con buscador por nombre o id y
 * páginas. Cada clic añade el objeto; la cantidad para los de TODOS se elige con el botón CANTIDAD.
 * Lo que tiene a medias cada admin va en su sesión: &lt;app&gt;.pk.src (inv/todos), .pk.q (búsqueda), .pk.page, .pk.n.
 */
final class PadItemPicker {
    private static final int PAGE = 96;
    private static final int[] AMOUNTS = {1, 4, 8, 16, 32, 64};
    private static List<Item> all;

    private PadItemPicker() {}

    /** Todos los objetos registrados (Minecraft y mods), en el orden del registro; sin el aire. */
    private static List<Item> all() {
        if (all == null) {
            List<Item> list = new ArrayList<>();
            for (Item i : ForgeRegistries.ITEMS.getValues()) if (i != Items.AIR) list.add(i);
            all = list;
        }
        return all;
    }

    private static String key(ServerPlayer p, String app, String k, String def) {
        return PadServer.get(p, app + ".pk." + k, def);
    }

    /**
     * La vista del selector. title: a qué se añade («Kit inicial»). Los botones de abajo: ANTERIOR / SIGUIENTE (en
     * TODOS), CANTIDAD y LISTO (vuelve a la ficha, con la acción done).
     */
    static PadView view(ServerPlayer p, String app, String tab, String title, String done) {
        String src = key(p, app, "src", "inv");
        int n = Integer.parseInt(key(p, app, "n", "1"));
        PadView.Builder b = PadView.of(app).tab(tab + "|inv", "TU INVENTARIO").tab(tab + "|todos", "TODOS LOS OBJETOS")
                .selected(tab + "|" + src);
        if (src.equals("inv")) {
            b.header("Añadir a " + title + ": pulsa un objeto (va tal cual, con su cantidad y encantamientos).");
            List<ItemStack> inv = p.getInventory().items;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.get(i);
                if (!s.isEmpty()) b.cell(s.copy(), "", 0x18265C, "pk.add:#" + i, false);
            }
            b.empty("Tu inventario está vacío. Mira en TODOS LOS OBJETOS.");
        } else {
            String q = key(p, app, "q", "");
            List<Item> found = search(q);
            int pages = Math.max(1, (found.size() + PAGE - 1) / PAGE);
            int page = Math.max(0, Math.min(pages - 1, Integer.parseInt(key(p, app, "page", "0"))));
            b.header("Añadir a " + title + " (de " + n + " en " + n + "). " + found.size() + " objetos"
                    + (q.isEmpty() ? "" : " con «" + q + "»") + " · página " + (page + 1) + " de " + pages + ".");
            for (int i = page * PAGE; i < Math.min(found.size(), (page + 1) * PAGE); i++) {
                Item it = found.get(i);
                b.cell(new ItemStack(it), "", 0x18265C, "pk.add:" + ForgeRegistries.ITEMS.getKey(it), false);
            }
            b.empty("Nada con ese nombre.");
            b.input("pk.q", q.isEmpty() ? "BUSCAR (NOMBRE O ID)" : "BUSCAR: " + q.toUpperCase(Locale.ROOT), 40, "BUSCAR");
            if (page > 0) b.footer(PadView.Btn.of("« ANTERIOR", "pk.prev", PadView.BLUE));
            if (page < pages - 1) b.footer(PadView.Btn.of("SIGUIENTE »", "pk.next", PadView.BLUE));
            b.footer(PadView.Btn.of("CANTIDAD " + n, "pk.n", PadView.GOLD));
        }
        b.footer(PadView.Btn.of("LISTO", done, PadView.GREEN));
        return b.build();
    }

    private static List<Item> search(String q) {
        if (q.isBlank()) return all();
        String needle = q.toLowerCase(Locale.ROOT).trim();
        List<Item> out = new ArrayList<>();
        for (Item i : all()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(i);
            String name = Component.translatable(i.getDescriptionId()).getString().toLowerCase(Locale.ROOT);
            if (String.valueOf(id).contains(needle) || name.contains(needle)) out.add(i);
        }
        return out;
    }

    /**
     * Las acciones del selector («pk.…» y las pestañas «tab|inv»). Devuelve el objeto elegido (para añadir), EMPTY si
     * era una acción del selector sin objeto, o null si la acción no es suya.
     */
    static ItemStack action(ServerPlayer p, String app, String action, String text) {
        if (action.startsWith("pk.add:")) {
            String what = action.substring(7);
            if (what.startsWith("#")) {
                try {
                    int slot = Integer.parseInt(what.substring(1));
                    ItemStack s = p.getInventory().items.get(slot);
                    return s.isEmpty() ? ItemStack.EMPTY : s.copy();
                } catch (RuntimeException e) {
                    return ItemStack.EMPTY;
                }
            }
            Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(what));
            if (it == null || it == Items.AIR) return ItemStack.EMPTY;
            int n = Integer.parseInt(key(p, app, "n", "1"));
            return new ItemStack(it, Math.max(1, Math.min(n, it.getMaxStackSize())));
        }
        switch (action) {
            case "pk.q" -> {
                PadServer.put(p, app + ".pk.q", text.trim());
                PadServer.put(p, app + ".pk.page", "0");
            }
            case "pk.prev", "pk.next" -> {
                int page = Integer.parseInt(key(p, app, "page", "0")) + (action.equals("pk.next") ? 1 : -1);
                PadServer.put(p, app + ".pk.page", String.valueOf(Math.max(0, page)));
            }
            case "pk.n" -> {
                int n = Integer.parseInt(key(p, app, "n", "1"));
                int next = AMOUNTS[0];
                for (int i = 0; i < AMOUNTS.length; i++) if (AMOUNTS[i] == n) next = AMOUNTS[(i + 1) % AMOUNTS.length];
                PadServer.put(p, app + ".pk.n", String.valueOf(next));
            }
            default -> {
                return null;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Las pestañas del selector llegan como «tab:&lt;tab&gt;|inv»: guarda la fuente y devuelve la pestaña base. */
    static String tab(ServerPlayer p, String app, String tab) {
        int bar = tab.indexOf('|');
        if (bar < 0) return tab;
        String src = tab.substring(bar + 1);
        PadServer.put(p, app + ".pk.src", src.equals("todos") ? "todos" : "inv");
        return tab.substring(0, bar);
    }

    /** El objeto como entrada de «objetos» en kits.json / recompensas.json (con todo su NBT). */
    static JsonObject json(ItemStack s) {
        JsonObject o = new JsonObject();
        o.addProperty("nbt", s.save(new CompoundTag()).toString());
        return o;
    }

    /** Lee una entrada de «objetos»: "minecraft:bread 16" o {"nbt": "..."}. */
    static ItemStack stack(JsonElement e) {
        try {
            if (e.isJsonObject() && e.getAsJsonObject().has("nbt")) {
                return ItemStack.of(TagParser.parseTag(e.getAsJsonObject().get("nbt").getAsString()));
            }
            String[] parts = e.getAsString().trim().split("\\s+");
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(parts[0]));
            if (item == null || item == Items.AIR) return ItemStack.EMPTY;
            int n = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            return new ItemStack(item, Math.max(1, Math.min(n, item.getMaxStackSize() * 9)));
        } catch (Exception ex) {
            return ItemStack.EMPTY;
        }
    }

    static List<ItemStack> stacks(JsonArray a) {
        List<ItemStack> out = new ArrayList<>();
        if (a != null) for (JsonElement e : a) {
            ItemStack s = stack(e);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
