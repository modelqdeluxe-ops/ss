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
 * KITS en el pad de administrador (config/tfclient/kits.json), paso a paso.
 * <ul>
 *   <li>La lista: cada kit con lo que es (cada cuánto, cuántos objetos, monedas, quién) y si es borrador o lo ven los
 *       jugadores; EDITAR y COPIAR. Abajo se escribe el nombre de uno nuevo y CREAR empieza el asistente.</li>
 *   <li>El asistente (pestañas «w:&lt;id&gt;:&lt;paso&gt;»): 1 nombre y descripción · 2 icono · 3 objetos (de tu
 *       inventario o de todos los del juego; cantidad y quitar) · 4 monedas y monedas verdes (números: van al monedero) ·
 *       5 cada cuánto · 6 quién puede y precio · 7 revisar y PUBLICAR (o dejarlo como borrador). Cada paso se guarda al
 *       momento: si cierras el pad, el kit sigue ahí como borrador.</li>
 *   <li>La ficha de un kit («k:&lt;id&gt;»): cómo lo ve el jugador, si se ve o no, y CAMBIAR en cada parte (abre ese paso,
 *       «e:&lt;id&gt;:&lt;paso&gt;», con LISTO para volver); COPIAR, BORRAR (con confirmación) y PROBAR (te lo da a ti).</li>
 * </ul>
 */
final class PadAdminKits {
    static final PadServer.App APP = new App();
    private static final String APP_ID = "a_kits";
    private static final int STEPS = 7;
    private static final String[] STEP_NAMES = {"", "Nombre", "Icono", "Objetos", "Monedas", "Cada cuánto", "Quién y precio", "Revisar"};

    private PadAdminKits() {}

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

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            String[] t = tab.split(":");
            PadKits.Kit k = t.length > 1 ? PadKits.kit(t[1]) : null;
            if (k != null) {
                if (t[0].equals("k")) return review(player, k, false);
                if ((t[0].equals("w") || t[0].equals("e")) && t.length > 2) {
                    boolean wizard = t[0].equals("w");
                    if (t[2].equals("p")) return itemPicker(player, k, tab, wizard);
                    int step = PadAdminUi.parse(t[2]);
                    switch (step) {
                        case 1 -> {
                            return nameStep(player, k, wizard);
                        }
                        case 2 -> {
                            return iconStep(player, k, tab, wizard);
                        }
                        case 3 -> {
                            return itemsStep(player, k, wizard);
                        }
                        case 4 -> {
                            return coinsStep(player, k, wizard);
                        }
                        case 5 -> {
                            return frequencyStep(player, k, wizard);
                        }
                        case 6 -> {
                            return whoStep(player, k, wizard);
                        }
                        case 7 -> {
                            return review(player, k, true);
                        }
                        default -> {
                        }
                    }
                }
            }
            return list();
        }

        private PadView list() {
            List<PadKits.Kit> kits = PadKits.kits();
            PadView.Builder b = PadView.of(APP_ID).header(kits.isEmpty() ? "Escribe abajo el nombre de un kit y pulsa CREAR: te guío paso a paso."
                    : "Pulsa un kit para verlo y cambiarlo. Para uno nuevo, escribe su nombre abajo y pulsa CREAR.");
            for (PadKits.Kit k : kits) {
                b.row(new PadView.Row(new ItemStack(k.iconItem()), k.name(), k.on() ? PadAdminUi.TEXT : PadAdminUi.MUTED,
                        List.of(summary(k), k.on() ? "Visible: lo ven los jugadores." : "Borrador: los jugadores aún no lo ven."), -1,
                        k.on() ? "" : "BORRADOR", PadView.Btn.of("EDITAR", "tab:k:" + k.id(), PadView.BLUE),
                        PadView.Btn.of("COPIAR", "copiar:" + k.id(), PadView.GRAY)).clickable("tab:k:" + k.id()));
            }
            b.empty("Aún no hay kits. Escribe un nombre abajo y pulsa CREAR: te guío paso a paso.");
            b.input("nuevo", "NOMBRE DEL KIT NUEVO", 32, "CREAR");
            return b.build();
        }

        /** La cabecera de un paso: «Paso 3 de 7 · Objetos · Kit inicial» y qué hacer. */
        private PadView.Builder step(PadKits.Kit k, int n, boolean wizard, String what) {
            return PadView.of(APP_ID).selected((wizard ? "w:" : "e:") + k.id() + ":" + n)
                    .header((wizard ? "Paso " + n + " de " + STEPS + " · " : "") + STEP_NAMES[n] + " · " + k.name() + ". " + what);
        }

        /** ATRÁS y SIGUIENTE (en el asistente) o ATRÁS y LISTO (cambiando una parte desde la ficha). */
        private void nav(PadView.Builder b, PadKits.Kit k, int n, boolean wizard, PadView.Btn... middle) {
            String back = wizard ? (n > 1 ? "tab:w:" + k.id() + ":" + (n - 1) : "tab:") : "tab:k:" + k.id();
            b.footer(PadView.Btn.of("ATRÁS", back, PadView.BLUE));
            for (PadView.Btn m : middle) if (m != null) b.footer(m);
            if (wizard) b.footer(PadView.Btn.of("SIGUIENTE", "tab:w:" + k.id() + ":" + (n + 1), PadView.GREEN));
            else b.footer(PadView.Btn.of("LISTO", "tab:k:" + k.id(), PadView.GREEN));
        }

        // -----------------------------------------------------------------------------------------------------------

        private PadView nameStep(ServerPlayer p, PadKits.Kit k, boolean wizard) {
            String f = PadAdminUi.field(p, APP_ID, target(k));
            if (f.isEmpty()) f = "nombre";
            PadView.Builder b = step(k, 1, wizard, "Escribe abajo y pulsa GUARDAR.");
            b.row(PadAdminUi.value(new ItemStack(Items.NAME_TAG), "Nombre", "El que ven los jugadores en su tarjeta.", k.name(), "f:nombre",
                    f.equals("nombre")));
            b.row(PadAdminUi.value(new ItemStack(Items.WRITABLE_BOOK), "Descripción (opcional)",
                    k.desc().isEmpty() ? "Una frase que sale al abrir el kit. Ahora no tiene." : "«" + k.desc() + "»", "", "f:descripcion",
                    f.equals("descripcion"), k.desc().isEmpty() ? null : PadView.Btn.of("QUITAR", "quitar:descripcion", PadView.GRAY)));
            b.input("v", f.equals("nombre") ? "NOMBRE DEL KIT" : "DESCRIPCIÓN (UNA FRASE)", f.equals("nombre") ? 32 : 120, "GUARDAR");
            nav(b, k, 1, wizard);
            return b.build();
        }

        private PadView iconStep(ServerPlayer p, PadKits.Kit k, String tab, boolean wizard) {
            String now = k.icon().isEmpty() ? (k.items().isEmpty() ? "un saco (por defecto)" : "el primer objeto del kit")
                    : new ItemStack(k.iconItem()).getHoverName().getString();
            PadView.Builder b = PadItemPicker.builder(p, APP_ID, tab, (wizard ? "Paso 2 de " + STEPS + " · " : "") + "Icono de " + k.name()
                    + ": pulsa el objeto que se verá en su tarjeta. Ahora: " + now + ".", false, k.icon());
            nav(b, k, 2, wizard);
            return b.build();
        }

        private PadView itemsStep(ServerPlayer p, PadKits.Kit k, boolean wizard) {
            String m = wizard ? "w:" : "e:";
            PadView.Builder b = step(k, 3, wizard, "Lo que se da como objeto. Las monedas van en el paso siguiente.");
            PadAdminUi.items(b, p, APP_ID, target(k), k.items());
            PadAdminUi.input(b, p, APP_ID, target(k));
            nav(b, k, 3, wizard, PadView.Btn.of("AÑADIR OBJETOS", "tab:" + m + k.id() + ":p", PadView.GREEN),
                    PadView.Btn.of("MI INVENTARIO", "c.inv", PadView.GOLD));
            return b.build();
        }

        private PadView itemPicker(ServerPlayer p, PadKits.Kit k, String tab, boolean wizard) {
            PadView.Builder b = PadItemPicker.builder(p, APP_ID, tab, "Añadir a " + k.name() + ": cada clic añade ese objeto (el de tu inventario, "
                    + "tal cual).", true, "");
            b.footer(PadView.Btn.of("LISTO", "tab:" + (wizard ? "w:" : "e:") + k.id() + ":3", PadView.GREEN));
            return b.build();
        }

        private PadView coinsStep(ServerPlayer p, PadKits.Kit k, boolean wizard) {
            PadView.Builder b = step(k, 4, wizard, "Se ingresan solas en el monedero al reclamarlo.");
            PadAdminUi.coins(b, p, APP_ID, target(k), k.coins(), k.greens());
            b.row(PadAdminUi.note("Escribe el número y pulsa GUARDAR. 0 o QUITAR = ninguna. Nunca se dan monedas como objeto."));
            PadAdminUi.input(b, p, APP_ID, target(k));
            nav(b, k, 4, wizard);
            return b.build();
        }

        private PadView frequencyStep(ServerPlayer p, PadKits.Kit k, boolean wizard) {
            String f = PadAdminUi.field(p, APP_ID, target(k));
            boolean custom = k.type().equals("horas") && k.cooldownSeconds() != 86400;
            PadView.Builder b = step(k, 5, wizard, "Cada cuánto puede reclamarlo cada jugador.");
            b.row(PadAdminUi.choice(new ItemStack(Items.CAKE), "Una sola vez", "Cada jugador lo reclama una vez y ya está (como un kit de inicio).",
                    k.type().equals("unico"), "tipo:unico"));
            b.row(PadAdminUi.choice(new ItemStack(Items.CLOCK), "Cada día", "Puede volver a reclamarlo 24 horas después.",
                    k.type().equals("horas") && k.cooldownSeconds() == 86400, "tipo:dia"));
            b.row(PadAdminUi.choice(new ItemStack(Items.CLOCK), "Cada semana", "Puede volver a reclamarlo 7 días después.",
                    k.type().equals("semanal"), "tipo:semanal"));
            if (custom) {
                long h = Math.max(1, k.cooldownSeconds() / 3600);
                b.row(PadAdminUi.value(new ItemStack(Items.COMPASS), "Cada cierto tiempo (elegido)", "Las horas entre una vez y la siguiente.",
                        h + " h", "f:horas", f.equals("horas")));
                if (f.equals("horas")) b.input("v", "HORAS ENTRE UNA VEZ Y OTRA (1 A 8.760)", 6, "GUARDAR");
            } else {
                b.row(PadAdminUi.choice(new ItemStack(Items.COMPASS), "Cada cierto tiempo", "Tú eliges las horas (por ejemplo 12 o 72).", false,
                        "tipo:horas"));
            }
            nav(b, k, 5, wizard);
            return b.build();
        }

        private PadView whoStep(ServerPlayer p, PadKits.Kit k, boolean wizard) {
            String f = PadAdminUi.field(p, APP_ID, target(k));
            PadView.Builder b = step(k, 6, wizard, "Quién puede reclamarlo y si cuesta algo.");
            b.row(PadAdminUi.section("Quién puede reclamarlo"));
            b.row(PadAdminUi.choice(new ItemStack(Items.PLAYER_HEAD), "Todos los jugadores", "Cualquiera, tenga rango o no.", k.rank().isEmpty(), "rango:"));
            boolean known = k.rank().isEmpty() || k.rank().equals("staff");
            for (TFRanks.Rank r : TFRanks.list()) {
                boolean chosen = r.key().equalsIgnoreCase(k.rank()) || r.id().equalsIgnoreCase(k.rank());
                known |= chosen;
                b.row(PadAdminUi.choice(new ItemStack(Items.GOLDEN_HELMET), "Rango " + (r.name().isEmpty() ? r.key() : r.name()) + " o superior",
                        "Los de este rango y los de rangos más altos.", chosen, "rango:" + r.key()));
            }
            if (!known) b.row(PadAdminUi.choice(new ItemStack(Items.GOLDEN_HELMET), PadKits.rankText(k.rank()), "Ese rango no está en la lista ahora.", true, ""));
            if (TFRanks.list().isEmpty()) b.row(PadAdminUi.note("Los rangos de la tienda salen aquí cuando la web está conectada."));
            b.row(PadAdminUi.choice(new ItemStack(Items.COMMAND_BLOCK), "Solo el staff", "Para probar o para kits de staff.", k.rank().equals("staff"),
                    "rango:staff"));
            b.row(PadAdminUi.section("Precio"));
            b.row(PadAdminUi.value(PadKits.coinIcon(false), "Precio", "Monedas que paga el jugador al reclamarlo. 0 = gratis.",
                    k.price() > 0 ? PadView.moneyExact(k.price()) : "Gratis", "f:precio", f.equals("precio"),
                    k.price() > 0 ? PadView.Btn.of("GRATIS", "gratis", PadView.GRAY) : null));
            if (f.equals("precio")) b.input("v", "PRECIO EN MONEDAS (0 = GRATIS)", 12, "GUARDAR");
            nav(b, k, 6, wizard);
            return b.build();
        }

        /** La ficha: cómo lo ve el jugador, y cada parte con CAMBIAR. wizard: el último paso del asistente (PUBLICAR). */
        private PadView review(ServerPlayer p, PadKits.Kit k, boolean wizard) {
            String m = wizard ? "tab:w:" : "tab:e:";
            PadView.Builder b = PadView.of(APP_ID).selected((wizard ? "w:" : "k:") + k.id() + (wizard ? ":7" : ""));
            if (wizard) b.header("Paso 7 de " + STEPS + " · Revisar: así lo verán los jugadores. PUBLICAR lo pone en su pad.");
            List<String> lines = new ArrayList<>();
            if (!k.desc().isEmpty()) lines.add(k.desc());
            lines.add(PadKits.typeText(k) + " · " + PadKits.rankText(k.rank()) + " · " + (k.price() > 0 ? "cuesta " + TFEconomy.format(k.price()) : "gratis"));
            b.hero(new PadView.Row(new ItemStack(k.iconItem()), k.name(), k.on() ? 0x40C850 : 0xF6B628, lines, -1, k.on() ? "VISIBLE" : "BORRADOR",
                    null, null));
            PadKits.contents(b, k.items(), k.coins(), k.greens());
            if (!wizard) {
                b.row(PadAdminUi.toggle(new ItemStack(k.on() ? Items.LIME_DYE : Items.GRAY_DYE), "Visible para los jugadores", "",
                        k.on(), "Sale en la app Kits de todos.", "Borrador: nadie lo ve hasta que lo pongas en SÍ.", "on:" + k.id()));
            }
            if (empty(k)) b.row(PadAdminUi.note("Ojo: este kit aún no trae nada. Añade objetos (paso 3) o monedas (paso 4)."));
            b.row(PadAdminUi.button(new ItemStack(Items.NAME_TAG), "1 · Nombre", k.desc().isEmpty() ? k.name() : k.name() + " — " + k.desc(), "",
                    PadView.Btn.of("CAMBIAR", m + k.id() + ":1", PadView.BLUE)));
            b.row(PadAdminUi.button(new ItemStack(k.iconItem()), "2 · Icono", new ItemStack(k.iconItem()).getHoverName().getString(), "",
                    PadView.Btn.of("CAMBIAR", m + k.id() + ":2", PadView.BLUE)));
            b.row(PadAdminUi.button(new ItemStack(Items.CHEST), "3 · Objetos", k.items().isEmpty() ? "Ninguno." : k.items().size() + " (arriba).",
                    "", PadView.Btn.of("CAMBIAR", m + k.id() + ":3", PadView.BLUE)));
            String coins = PadKits.coinsText(k.coins(), k.greens());
            b.row(PadAdminUi.button(PadKits.coinIcon(false), "4 · Monedas", coins.isEmpty() ? "Ninguna." : coins + " (al monedero).", "",
                    PadView.Btn.of("CAMBIAR", m + k.id() + ":4", PadView.BLUE)));
            b.row(PadAdminUi.button(new ItemStack(Items.CLOCK), "5 · Cada cuánto", PadKits.typeText(k) + ".", "",
                    PadView.Btn.of("CAMBIAR", m + k.id() + ":5", PadView.BLUE)));
            b.row(PadAdminUi.button(new ItemStack(Items.GOLDEN_HELMET), "6 · Quién y precio",
                    PadKits.rankText(k.rank()) + " · " + (k.price() > 0 ? "cuesta " + TFEconomy.format(k.price()) : "gratis") + ".", "",
                    PadView.Btn.of("CAMBIAR", m + k.id() + ":6", PadView.BLUE)));
            boolean sure = PadServer.confirming(p, APP_ID + ".borrar:" + k.id());
            if (wizard) {
                b.footer(PadView.Btn.of("ATRÁS", "tab:w:" + k.id() + ":6", PadView.BLUE));
                b.footer(PadView.Btn.of("DEJAR EN BORRADOR", "borrador:" + k.id(), PadView.GRAY));
                b.footer(PadView.Btn.of("PUBLICAR", "publicar:" + k.id(), PadView.GREEN));
            } else {
                b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
                b.footer(PadView.Btn.of("PROBAR", "probar:" + k.id(), PadView.GOLD));
                b.footer(PadView.Btn.of("COPIAR", "copiar:" + k.id(), PadView.GRAY));
                b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar:" + k.id(), PadView.RED));
            }
            return b.build();
        }

        // -----------------------------------------------------------------------------------------------------------

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            String[] t = tab.split(":");
            PadKits.Kit k = t.length > 1 ? PadKits.kit(t[1]) : null;
            boolean inStep = k != null && t.length > 2 && (t[0].equals("w") || t[0].equals("e"));
            // los selectores: el icono (paso 2) y añadir objetos (paso 3)
            if (inStep && (t[2].equals("2") || t[2].equals("p"))) {
                ItemStack chosen = PadItemPicker.action(player, APP_ID, action, text);
                if (chosen != null) {
                    if (chosen.isEmpty()) return null;
                    if (t[2].equals("2")) {
                        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(chosen.getItem()));
                        PadKits.edit(k.id(), o -> o.addProperty("icono", id));
                        TFPadNet.notice(player, "Icono: " + chosen.getHoverName().getString() + ".");
                    } else {
                        String[] said = {""};
                        PadKits.edit(k.id(), o -> said[0] = PadAdminUi.add(o, chosen));
                        TFPadNet.notice(player, said[0]);
                    }
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
                    String id = PadKits.create(name);
                    TFPadNet.notice(player, "Kit «" + name + "» creado (borrador: nadie lo ve todavía). Ahora elige su icono.");
                    return "w:" + id + ":2";
                }
                case "copiar" -> {
                    String nid = PadKits.duplicate(arg);
                    if (nid == null) return null;
                    TFPadNet.notice(player, "Copia creada como borrador. Cámbiale lo que quieras.");
                    return "k:" + nid;
                }
                case "borrar" -> {
                    if (!PadServer.confirm(player, APP_ID + ".borrar:" + arg)) return null;
                    PadKits.Kit gone = PadKits.kit(arg);
                    PadKits.delete(arg);
                    TFPadNet.notice(player, "Kit «" + (gone == null ? arg : gone.name()) + "» borrado.");
                    return "";
                }
                case "on" -> PadKits.edit(arg, o -> o.addProperty("activado", !TFJson.bool(o, "activado", true)));
                case "publicar" -> {
                    PadKits.Kit pk = PadKits.kit(arg);
                    if (pk == null) return "";
                    if (empty(pk)) {
                        TFPadNet.notice(player, "Este kit no trae nada: añade objetos o monedas antes de publicarlo.");
                        return "w:" + arg + ":3";
                    }
                    PadKits.edit(arg, o -> o.addProperty("activado", true));
                    TFPadNet.notice(player, "Kit «" + pk.name() + "» publicado: ya sale en la app Kits.");
                    return "";
                }
                case "borrador" -> {
                    PadKits.edit(arg, o -> o.addProperty("activado", false));
                    TFPadNet.notice(player, "Guardado como borrador: nadie lo ve hasta que lo publiques.");
                    return "";
                }
                case "probar" -> {
                    PadKits.Kit pk = PadKits.kit(arg);
                    if (pk == null) return null;
                    PadKits.Kit test = new PadKits.Kit(pk.id(), pk.name(), pk.desc(), pk.icon(), pk.cooldownSeconds(), pk.coins(), pk.greens(),
                            pk.items(), pk.type(), "", 0, true);
                    // sin cobrar, sin esperas y sin apuntarlo: se le da otra vez lo de antes de la prueba
                    JsonObject mine = PadKits.STORE_IMPL.playerIfAny(player.getUUID());
                    com.google.gson.JsonElement before = mine == null ? null : mine.get(pk.id());
                    PadKits.claim(player, test);
                    JsonObject after = PadKits.STORE_IMPL.player(player.getUUID());
                    if (before == null) after.remove(pk.id());
                    else after.add(pk.id(), before);
                    PadKits.STORE_IMPL.changed();
                    return null;
                }
                case "tipo" -> {
                    if (k == null) return null;
                    switch (arg) {
                        case "unico" -> PadKits.edit(k.id(), o -> {
                            o.addProperty("tipo", "unico");
                            o.addProperty("esperaSegundos", 0);
                        });
                        case "dia" -> PadKits.edit(k.id(), o -> {
                            o.addProperty("tipo", "horas");
                            o.addProperty("esperaSegundos", 86400);
                        });
                        case "semanal" -> PadKits.edit(k.id(), o -> {
                            o.addProperty("tipo", "semanal");
                            o.addProperty("esperaSegundos", 604800);
                        });
                        case "horas" -> {
                            PadKits.edit(k.id(), o -> {
                                o.addProperty("tipo", "horas");
                                o.addProperty("esperaSegundos", 12 * 3600);
                            });
                            PadAdminUi.editField(player, APP_ID, target(k), "horas");
                            TFPadNet.notice(player, "Escribe abajo cada cuántas horas (ahora 12).");
                        }
                        default -> {
                        }
                    }
                    if (!arg.equals("horas")) PadAdminUi.stopEditing(player, APP_ID);
                }
                case "rango" -> {
                    if (k != null) PadKits.edit(k.id(), o -> o.addProperty("rango", arg.toLowerCase(Locale.ROOT)));
                }
                case "gratis" -> {
                    if (k != null) PadKits.edit(k.id(), o -> o.addProperty("precio", 0));
                    PadAdminUi.stopEditing(player, APP_ID);
                }
                case "f" -> {
                    if (k == null) return null;
                    String now = PadAdminUi.field(player, APP_ID, target(k));
                    PadAdminUi.editField(player, APP_ID, target(k), now.equals(arg) && !arg.equals("nombre") ? "" : arg);
                }
                case "quitar" -> {
                    if (k != null && arg.equals("descripcion")) PadKits.edit(k.id(), o -> o.addProperty("descripcion", ""));
                }
                case "v" -> {
                    if (k == null) return null;
                    String f = PadAdminUi.field(player, APP_ID, target(k));
                    if (f.isEmpty() && t.length > 2 && t[2].equals("1")) f = "nombre";
                    String value = text.trim();
                    switch (f) {
                        case "nombre" -> {
                            if (value.isEmpty()) return null;
                            PadKits.edit(k.id(), o -> o.addProperty("nombre", value.length() > 32 ? value.substring(0, 32) : value));
                            TFPadNet.notice(player, "Nombre guardado.");
                        }
                        case "descripcion" -> {
                            PadKits.edit(k.id(), o -> o.addProperty("descripcion", value.length() > 120 ? value.substring(0, 120) : value));
                            TFPadNet.notice(player, "Descripción guardada.");
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
                    // en el asistente, tras poner el nombre se pasa solo al paso siguiente
                    if (f.equals("nombre") && t[0].equals("w")) return "w:" + k.id() + ":2";
                }
                default -> {
                }
            }
            return null;
        }
    }
}
