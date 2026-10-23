/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.server.TFRanks;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * KITS en el pad de administrador (config/tfclient/kits.json). 1.3.67, el dueño: «la creación de kits es un dolor de
 * cabeza: ¿por qué no lo haces sencillo, meter todo de un solo?». Sin asistente de pasos: todo en UNA pantalla.
 * <ul>
 *   <li>La lista: cada kit con lo que es y si lo ven los jugadores; EDITAR y COPIAR. Abajo se escribe el nombre de uno
 *       nuevo y CREAR lo hace al momento <b>con todo lo que llevas en el inventario</b> (las monedas que lleves pasan a
 *       monedas) y abre su ficha.</li>
 *   <li>La ficha («k:&lt;id&gt;»): arriba cómo es y PUBLICAR/OCULTAR; sus objetos en casillas (pulsa uno para cambiar
 *       cuántos o quitarlo), MI INVENTARIO y ELEGIR para añadir; y cada ajuste en su fila, que se cambia ahí mismo:
 *       monedas, monedas verdes y precio con − y + (saltos redondos; pulsando la fila se escribe el número exacto), cada
 *       cuánto y quién puede con « y », nombre, descripción e icono. PROBAR te lo da a ti, COPIAR, BORRAR.</li>
 *   <li>Los selectores: «p:&lt;id&gt;» (añadir objetos, de tu inventario o de todos los del juego) e «i:&lt;id&gt;» (el icono).</li>
 * </ul>
 */
final class PadAdminKits {
    static final PadServer.App APP = new App();
    private static final String APP_ID = "a_kits";

    private PadAdminKits() {}

    /** Las opciones de «cada cuánto», en el orden de « y ». */
    private record Freq(String type, long seconds) {}

    private static final List<Freq> FREQS = List.of(new Freq("unico", 0), new Freq("horas", 3600), new Freq("horas", 12 * 3600),
            new Freq("horas", 86400), new Freq("horas", 3 * 86400), new Freq("semanal", 604800));

    /** La opción de «cada cuánto» del kit (la más cercana si se escribió a mano otra cosa). */
    private static int freqIndex(PadKits.Kit k) {
        if (k.type().equals("unico")) return 0;
        if (k.type().equals("semanal")) return FREQS.size() - 1;
        int best = 1;
        long diff = Long.MAX_VALUE;
        for (int i = 1; i < FREQS.size(); i++) {
            long d = Math.abs(FREQS.get(i).seconds - k.cooldownSeconds());
            if (d < diff) {
                diff = d;
                best = i;
            }
        }
        return best;
    }

    /** Quién puede, en el orden de « y »: todos, cada rango (de menos a más) y solo el staff. */
    private static List<String> ranks() {
        List<String> out = new ArrayList<>();
        out.add("");
        for (TFRanks.Rank r : TFRanks.list()) out.add(r.key().toLowerCase(Locale.ROOT));
        out.add("staff");
        return out;
    }

    /** «Una sola vez · 8 objetos · +25 monedas · Todos los jugadores · gratis». */
    private static String summary(PadKits.Kit k) {
        List<String> parts = new ArrayList<>();
        parts.add(PadKits.typeText(k));
        parts.add(k.items().size() + (k.items().size() == 1 ? " objeto" : " objetos"));
        String coins = PadKits.coinsText(k.coins(), k.greens());
        if (!coins.isEmpty()) parts.add(coins);
        parts.add(PadKits.rankText(k.rank()));
        parts.add(k.price() > 0 ? "cuesta " + TFEconomy.format(k.price()) : "gratis");
        return String.join(" · ", parts);
    }

    private static boolean empty(PadKits.Kit k) {
        return k.items().isEmpty() && k.coins() <= 0 && k.greens() <= 0;
    }

    private static String target(PadKits.Kit k) {
        return "k:" + k.id();
    }

    /** Mete en el kit todo lo que lleva el admin en el inventario. Devuelve cuántos montones. */
    private static int addInventory(ServerPlayer p, String id) {
        List<ItemStack> inv = new ArrayList<>();
        for (ItemStack s : p.getInventory().items) if (!PadItemPicker.hidden(s)) inv.add(s.copy());
        if (inv.isEmpty()) return 0;
        PadKits.edit(id, o -> {
            for (ItemStack s : inv) PadAdminUi.add(o, s);
            if (TFJson.str(o, "icono", "").isEmpty()) {
                for (ItemStack s : inv) {
                    if (net.tierrasfantasticas.tfclient.economy.TFCoins.kind(s) != net.tierrasfantasticas.tfclient.economy.TFCoins.Kind.NONE) continue;
                    o.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem())));
                    break;
                }
            }
        });
        return inv.size();
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            String[] t = tab.split(":");
            PadKits.Kit k = t.length > 1 ? PadKits.kit(t[1]) : null;
            if (k != null) {
                if (t[0].equals("p")) {
                    PadView.Builder b = PadItemPicker.builder(player, APP_ID, tab, "Añadir a " + k.name() + ": cada clic añade ese objeto (el de tu "
                            + "inventario va tal cual, con su cantidad y encantamientos).", true, "");
                    b.footer(PadView.Btn.of("LISTO", "tab:k:" + k.id(), PadView.GREEN));
                    return b.build();
                }
                if (t[0].equals("i")) {
                    PadView.Builder b = PadItemPicker.builder(player, APP_ID, tab, "Icono de " + k.name() + ": pulsa el objeto que se verá en su "
                            + "tarjeta.", false, k.icon());
                    b.footer(PadView.Btn.of("ATRÁS", "tab:k:" + k.id(), PadView.BLUE));
                    return b.build();
                }
                return sheet(player, k); // «k:» (y los «w:»/«e:» de antes)
            }
            return list();
        }

        private PadView list() {
            List<PadKits.Kit> kits = PadKits.kits();
            PadView.Builder b = PadView.of(APP_ID).selected("").header("Kit nuevo: escribe su nombre abajo y pulsa CREAR. Se hace con todo lo que "
                    + "llevas en el inventario y lo ajustas en una sola pantalla.");
            for (PadKits.Kit k : kits) {
                b.row(new PadView.Row(new ItemStack(k.iconItem()), k.name(), k.on() ? PadAdminUi.TEXT : PadAdminUi.MUTED,
                        List.of(summary(k)), -1, k.on() ? "VISIBLE" : "BORRADOR", PadView.Btn.of("EDITAR", "tab:k:" + k.id(), PadView.BLUE),
                        PadView.Btn.of("COPIAR", "copiar:" + k.id(), PadView.GRAY)).clickable("tab:k:" + k.id()));
            }
            b.empty("Aún no hay kits. Llena tu inventario con lo que quieras dar, escribe un nombre abajo y pulsa CREAR.");
            b.input("nuevo", "NOMBRE DEL KIT NUEVO", 32, "CREAR");
            return b.build();
        }

        /** La ficha: todo el kit en una pantalla. */
        private PadView sheet(ServerPlayer p, PadKits.Kit k) {
            String f = PadAdminUi.field(p, APP_ID, target(k));
            PadView.Builder b = PadView.of(APP_ID).selected(target(k)).compact();
            List<String> lines = new ArrayList<>();
            if (!k.desc().isEmpty()) lines.add(k.desc());
            lines.add(summary(k));
            b.hero(new PadView.Row(new ItemStack(k.iconItem()), k.name(), k.on() ? 0x40C850 : 0xF6B628, lines, -1, k.on() ? "VISIBLE" : "BORRADOR",
                    k.on() ? PadView.Btn.of("OCULTAR", "on", PadView.GRAY) : PadView.Btn.of("PUBLICAR", "on", PadView.GREEN), null));
            // los objetos en casillas (pulsa uno: cuántos trae o quitarlo) y cómo añadir más
            PadAdminUi.items(b, p, APP_ID, target(k), k.items(), PadView.Btn.of("MI INVENTARIO", "c.inv", PadView.GOLD),
                    PadView.Btn.of("ELEGIR", "tab:p:" + k.id(), PadView.GREEN));
            if (empty(k)) b.row(PadAdminUi.note("Ojo: este kit aún no trae nada. Añade objetos o monedas."));
            // lo demás, cada cosa en su fila y se cambia ahí mismo
            PadAdminUi.coins(b, p, APP_ID, target(k), k.coins(), k.greens());
            boolean customFreq = k.type().equals("horas") && FREQS.stream().noneMatch(x -> x.type.equals("horas") && x.seconds == k.cooldownSeconds());
            b.row(new PadView.Row(new ItemStack(Items.CLOCK), "Cada cuánto", PadAdminUi.TEXT,
                    List.of(customFreq ? "Elegido a mano; pulsa la fila para cambiarlo." : "Pulsa la fila para escribir otras horas."), -1,
                    PadKits.typeText(k), PadView.Btn.of("«", "freq:-", PadView.BLUE), PadView.Btn.of("»", "freq:+", PadView.BLUE))
                    .clickable("f:horas").selected(f.equals("horas")));
            b.row(PadAdminUi.cycle(new ItemStack(Items.GOLDEN_HELMET), "Quién puede", "Un rango y los de encima.", PadKits.rankText(k.rank()),
                    "rango:-", "rango:+"));
            b.row(PadAdminUi.stepper(PadKits.coinIcon(false), "Precio", "Al reclamarlo; pulsa la fila para escribirlo.",
                    k.price() > 0 ? PadView.moneyExact(k.price()) : "Gratis", "precio:-", "precio:+", "f:precio", f.equals("precio")));
            b.row(PadAdminUi.value(new ItemStack(Items.NAME_TAG), "Nombre", "", k.name(), "f:nombre", f.equals("nombre")));
            b.row(PadAdminUi.value(new ItemStack(Items.WRITABLE_BOOK), "Descripción", k.desc().isEmpty() ? "Opcional: una frase." : "", k.desc().isEmpty()
                    ? "Ninguna" : cut(k.desc(), 28), "f:descripcion", f.equals("descripcion")));
            b.row(PadAdminUi.button(new ItemStack(k.iconItem()), "Icono", new ItemStack(k.iconItem()).getHoverName().getString() + ".", "",
                    PadView.Btn.of("CAMBIAR", "tab:i:" + k.id(), PadView.BLUE)));
            boolean typing = PadAdminUi.input(b, p, APP_ID, target(k));
            String hint = switch (f) {
                case "nombre" -> "NOMBRE DEL KIT";
                case "descripcion" -> "UNA FRASE (VACÍO = SIN DESCRIPCIÓN)";
                case "horas" -> "CADA CUÁNTAS HORAS (1 A 8.760)";
                case "precio" -> "PRECIO EN MONEDAS (0 = GRATIS)";
                default -> "";
            };
            if (!typing && !hint.isEmpty()) {
                b.input("v", hint, f.equals("descripcion") ? 120 : f.equals("nombre") ? 32 : 12, "GUARDAR");
                typing = true;
            }
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            if (typing) {
                b.footer(PadView.Btn.of("CANCELAR", "cancelar", PadView.GRAY));
            } else {
                boolean sure = PadServer.confirming(p, APP_ID + ".borrar:" + k.id());
                b.footer(PadView.Btn.of("PROBAR", "probar", PadView.GOLD));
                b.footer(PadView.Btn.of("COPIAR", "copiar:" + k.id(), PadView.GRAY));
                b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar", PadView.RED));
            }
            return b.build();
        }

        // -----------------------------------------------------------------------------------------------------------

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            String[] t = tab.split(":");
            PadKits.Kit k = t.length > 1 ? PadKits.kit(t[1]) : null;
            // los selectores: añadir objetos y el icono
            if (k != null && (t[0].equals("p") || t[0].equals("i"))) {
                ItemStack chosen = PadItemPicker.action(player, APP_ID, action, text);
                if (chosen != null) {
                    if (chosen.isEmpty()) return null;
                    if (t[0].equals("i")) {
                        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(chosen.getItem()));
                        PadKits.edit(k.id(), o -> o.addProperty("icono", id));
                        TFPadNet.notice(player, "Icono: " + chosen.getHoverName().getString() + ".");
                        return "k:" + k.id();
                    }
                    String[] said = {""};
                    PadKits.edit(k.id(), o -> said[0] = PadAdminUi.add(o, chosen));
                    TFPadNet.notice(player, said[0]);
                    return null;
                }
            }
            if (k != null && PadAdminUi.action(player, APP_ID, target(k), action, text, f -> PadKits.edit(k.id(), f))) return null;
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "nuevo" -> {
                    String name = text.trim();
                    if (name.isEmpty()) return null;
                    String id = PadKits.create(name.length() > 32 ? name.substring(0, 32) : name);
                    PadKits.edit(id, o -> {
                        o.addProperty("tipo", "unico");
                        o.addProperty("esperaSegundos", 0);
                    });
                    int n = addInventory(player, id);
                    TFPadNet.notice(player, n > 0 ? "Kit «" + name + "» creado con lo de tu inventario (" + n + (n == 1 ? " montón" : " montones")
                            + "). Ajusta lo que quieras aquí y pulsa PUBLICAR." : "Kit «" + name + "» creado vacío (tu inventario no tenía nada). "
                            + "Añade objetos aquí y pulsa PUBLICAR.");
                    return "k:" + id;
                }
                case "copiar" -> {
                    String nid = PadKits.duplicate(arg);
                    if (nid == null) return null;
                    TFPadNet.notice(player, "Copia creada como borrador. Cámbiale lo que quieras.");
                    return "k:" + nid;
                }
                case "borrar" -> {
                    if (k == null || !PadServer.confirm(player, APP_ID + ".borrar:" + k.id())) return null;
                    PadKits.delete(k.id());
                    TFPadNet.notice(player, "Kit «" + k.name() + "» borrado.");
                    return "";
                }
                case "on" -> {
                    if (k == null) return null;
                    if (!k.on() && empty(k)) {
                        TFPadNet.notice(player, "Este kit no trae nada: añade objetos o monedas antes de publicarlo.");
                        return null;
                    }
                    PadKits.edit(k.id(), o -> o.addProperty("activado", !k.on()));
                    TFPadNet.notice(player, k.on() ? "Oculto: nadie lo ve hasta que lo publiques." : "Publicado: ya sale en la app Kits.");
                }
                case "probar" -> {
                    if (k == null) return null;
                    PadKits.Kit test = new PadKits.Kit(k.id(), k.name(), k.desc(), k.icon(), k.cooldownSeconds(), k.coins(), k.greens(),
                            k.items(), k.type(), "", 0, true);
                    // sin cobrar, sin esperas y sin apuntarlo: se le deja como estaba antes de la prueba
                    JsonObject mine = PadKits.STORE_IMPL.playerIfAny(player.getUUID());
                    com.google.gson.JsonElement before = mine == null ? null : mine.get(k.id());
                    PadKits.claim(player, test);
                    JsonObject after = PadKits.STORE_IMPL.player(player.getUUID());
                    if (before == null) after.remove(k.id());
                    else after.add(k.id(), before);
                    PadKits.STORE_IMPL.changed();
                }
                case "freq" -> {
                    if (k == null) return null;
                    int i = Math.floorMod(freqIndex(k) + (arg.equals("-") ? -1 : 1), FREQS.size());
                    Freq fr = FREQS.get(i);
                    PadKits.edit(k.id(), o -> {
                        o.addProperty("tipo", fr.type);
                        o.addProperty("esperaSegundos", fr.seconds);
                    });
                    PadAdminUi.stopEditing(player, APP_ID);
                }
                case "rango" -> {
                    if (k == null) return null;
                    List<String> rs = ranks();
                    int at = Math.max(0, rs.indexOf(k.rank().toLowerCase(Locale.ROOT)));
                    String next = rs.get(Math.floorMod(at + (arg.equals("-") ? -1 : 1), rs.size()));
                    PadKits.edit(k.id(), o -> o.addProperty("rango", next));
                }
                case "precio" -> {
                    if (k == null) return null;
                    long v = PadAdminUi.step(k.price(), PadAdminUi.COIN_STEPS, arg.equals("-") ? -1 : 1);
                    PadKits.edit(k.id(), o -> o.addProperty("precio", v));
                    PadAdminUi.stopEditing(player, APP_ID);
                }
                case "f" -> {
                    if (k == null) return null;
                    String now = PadAdminUi.field(player, APP_ID, target(k));
                    PadAdminUi.editField(player, APP_ID, target(k), now.equals(arg) ? "" : arg);
                }
                case "cancelar" -> PadAdminUi.stopEditing(player, APP_ID);
                case "v" -> {
                    if (k == null) return null;
                    String f = PadAdminUi.field(player, APP_ID, target(k));
                    String value = text.trim();
                    switch (f) {
                        case "nombre" -> {
                            if (value.isEmpty()) return null;
                            PadKits.edit(k.id(), o -> o.addProperty("nombre", value.length() > 32 ? value.substring(0, 32) : value));
                            TFPadNet.notice(player, "Nombre guardado.");
                        }
                        case "descripcion" -> {
                            PadKits.edit(k.id(), o -> o.addProperty("descripcion", value.length() > 120 ? value.substring(0, 120) : value));
                            TFPadNet.notice(player, value.isEmpty() ? "Sin descripción." : "Descripción guardada.");
                        }
                        case "horas" -> {
                            long h = PadAdminUi.number(player, value, 1, 24L * 365);
                            if (h < 0) return null;
                            PadKits.edit(k.id(), o -> {
                                o.addProperty("tipo", h == 168 ? "semanal" : "horas");
                                o.addProperty("esperaSegundos", h * 3600);
                            });
                            TFPadNet.notice(player, "Guardado: " + PadKits.typeText(h == 168 ? "semanal" : "horas", h * 3600).toLowerCase(Locale.ROOT) + ".");
                        }
                        case "precio" -> {
                            long v = PadAdminUi.number(player, value, 0, 1_000_000_000L);
                            if (v < 0) return null;
                            PadKits.edit(k.id(), o -> o.addProperty("precio", v));
                            TFPadNet.notice(player, v == 0 ? "Gratis." : "Precio guardado: " + TFEconomy.format(v) + ".");
                        }
                        default -> {
                            return null;
                        }
                    }
                    PadAdminUi.stopEditing(player, APP_ID);
                }
                default -> {
                }
            }
            return null;
        }
    }

    /** Un texto largo recortado para una etiqueta. */
    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
