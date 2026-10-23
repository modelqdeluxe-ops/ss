/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
    /** Lo más que puede traer una entrada de un kit o una recompensa (se da en montones normales). */
    static final int MAX_AMOUNT = 6400;
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

    /** Lo que no se ofrece de tu inventario: el propio pad de administrador. */
    static boolean hidden(ItemStack s) {
        return s.isEmpty() || s.getItem() instanceof net.tierrasfantasticas.tfclient.pad.AdminPadItem;
    }

    /**
     * El selector, sin sus botones de volver: pestañas TU INVENTARIO / TODOS LOS OBJETOS, una línea de cabecera (what:
     * «Paso 2 de 7 · Pulsa el objeto que hará de icono»), la rejilla y, en TODOS, el buscador y las páginas (« »). amounts:
     * si se elige de cuántos en cuántos se añaden los de TODOS (botón ×N; no para elegir un icono). marked: el id del
     * objeto que sale marcado (el icono actual), o "". Quien lo llama añade sus botones (como mucho 4 en total).
     */
    static PadView.Builder builder(ServerPlayer p, String app, String tab, String what, boolean amounts, String marked) {
        String src = key(p, app, "src", "inv");
        int n = amounts ? Integer.parseInt(key(p, app, "n", "1")) : 1;
        PadView.Builder b = PadView.of(app).tab(tab + "|inv", "TU INVENTARIO").tab(tab + "|todos", "TODOS LOS OBJETOS")
                .selected(tab + "|" + src);
        if (src.equals("inv")) {
            b.header(what);
            List<ItemStack> inv = p.getInventory().items;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.get(i);
                if (!hidden(s)) b.cell(s.copy(), "", 0x18265C, "pk.add:#" + i, marked.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem()))));
            }
            b.empty("Tu inventario está vacío. Mira en TODOS LOS OBJETOS (arriba).");
        } else {
            String q = key(p, app, "q", "");
            List<Item> found = search(q);
            int pages = Math.max(1, (found.size() + PAGE - 1) / PAGE);
            int page = Math.max(0, Math.min(pages - 1, Integer.parseInt(key(p, app, "page", "0"))));
            b.header(what + (amounts ? " Se añaden de " + n + " en " + n + "." : "") + " Página " + (page + 1) + " de " + pages
                    + (q.isEmpty() ? "" : " · «" + q + "»") + ".");
            for (int i = page * PAGE; i < Math.min(found.size(), (page + 1) * PAGE); i++) {
                Item it = found.get(i);
                String id = String.valueOf(ForgeRegistries.ITEMS.getKey(it));
                b.cell(new ItemStack(it), "", 0x18265C, "pk.add:" + id, marked.equals(id));
            }
            b.empty("Nada con ese nombre. Prueba con el id en inglés (diamond, iron_sword…).");
            b.input("pk.q", q.isEmpty() ? "BUSCAR OBJETO" : "BUSCAR: " + q.toUpperCase(Locale.ROOT), 40, "BUSCAR");
            if (page > 0) b.footer(PadView.Btn.of("«", "pk.prev", PadView.BLUE));
            if (page < pages - 1) b.footer(PadView.Btn.of("»", "pk.next", PadView.BLUE));
            if (amounts) b.footer(PadView.Btn.of("×" + n, "pk.n", PadView.GOLD));
        }
        return b;
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
                    return hidden(s) ? ItemStack.EMPTY : s.copy();
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

    /**
     * El objeto como entrada de «objetos» en kits.json / recompensas.json (con todo su NBT). El NBT guarda la cantidad en
     * un byte (hasta 127): si es más, va también en «cantidad».
     */
    static JsonObject json(ItemStack s) {
        JsonObject o = new JsonObject();
        ItemStack c = s.copy();
        c.setCount(Math.max(1, Math.min(64, s.getCount())));
        o.addProperty("nbt", c.save(new CompoundTag()).toString());
        if (s.getCount() > 64) o.addProperty("cantidad", s.getCount());
        return o;
    }

    /** Lee una entrada de «objetos»: "minecraft:bread 16" o {"nbt": "..."}. */
    static ItemStack stack(JsonElement e) {
        try {
            if (e.isJsonObject() && e.getAsJsonObject().has("nbt")) {
                ItemStack s = ItemStack.of(TagParser.parseTag(e.getAsJsonObject().get("nbt").getAsString()));
                long n = net.tierrasfantasticas.tfclient.util.TFJson.num(e.getAsJsonObject(), "cantidad", 0);
                if (!s.isEmpty() && n > 0) s.setCount((int) Math.min(MAX_AMOUNT, n));
                return s;
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

    /**
     * Saca las monedas (de oro y verdes) de una lista «objetos»: las quita del array y devuelve {oro, verdes}. Los kits y
     * recompensas de antes las llevaban como objeto (y se daban como objeto); ahora van en «monedas» y «verdes».
     */
    static long[] takeCoins(JsonArray a) {
        long[] out = {0, 0};
        if (a == null) return out;
        for (int i = a.size() - 1; i >= 0; i--) {
            ItemStack s = stack(a.get(i));
            net.tierrasfantasticas.tfclient.economy.TFCoins.Kind k = net.tierrasfantasticas.tfclient.economy.TFCoins.kind(s);
            if (k == net.tierrasfantasticas.tfclient.economy.TFCoins.Kind.NONE) continue;
            out[k == net.tierrasfantasticas.tfclient.economy.TFCoins.Kind.GOLD ? 0 : 1] += s.getCount();
            a.remove(i);
        }
        return out;
    }
}
