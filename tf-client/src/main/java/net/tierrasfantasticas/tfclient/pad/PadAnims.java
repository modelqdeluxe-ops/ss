package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.anim.AnimSettings;
import net.tierrasfantasticas.tfclient.anim.PunchyBridge;

/**
 * Efectos › ANIMACIONES (1.3.69): las animaciones en primera persona de Punchy (de Punchy Guys Studios, dentro de TF
 * Client con su permiso), cada una con su interruptor. Como COMBATE, es del cliente: no pasa por el servidor. Las de
 * movimiento, efectos y modelos son los ajustes del propio Punchy (PunchyBridge); las de «con qué objetos» son de TF
 * Client (AnimSettings: un tipo apagado se ve como en Minecraft normal). Arriba, el crédito con el enlace a Modrinth.
 */
final class PadAnims {
    static final String TAB = "animaciones";
    private static final int TEXT = 0x18265C;

    /** Un ajuste de Punchy: su campo, si el SÍ del pad es el campo apagado (los «disable...»), icono y textos. */
    private record Opt(String field, boolean inverted, Item icon, String title, String help) {}

    private static final List<Opt> MOVE = List.of(
            new Opt("enableCustomWalk", false, Items.LEATHER_BOOTS, "Al andar", "Balanceo propio de los brazos (quita el de Minecraft)."),
            new Opt("enableSprintArmSwing", false, Items.GOLDEN_BOOTS, "Al correr", "Los brazos se mueven al correr."),
            new Opt("enableFallArmAnimation", false, Items.FEATHER, "Al caer", "Los brazos se levantan al caer."),
            new Opt("enableSwimAnimation", false, Items.HEART_OF_THE_SEA, "Al nadar", ""),
            new Opt("enableClimbAnimation", false, Items.LADDER, "Al trepar", "Escaleras de mano y enredaderas."),
            new Opt("enableCrawlAnimation", false, Items.SCAFFOLDING, "Al gatear", "Por huecos de un bloque."),
            new Opt("enableElytraFlightAnimation", false, Items.ELYTRA, "Al planear", "Con élitros."),
            new Opt("disableBoatFirstPersonAnimations", true, Items.OAK_BOAT, "En barca o balsa", "Remar."),
            new Opt("disableNewKeyframeMoves", true, Items.RABBIT_FOOT, "Movimientos nuevos",
                    "Andar, correr, saltar, caer y aterrizar con las animaciones nuevas de Punchy."));

    private static final List<Opt> ARMS = List.of(
            new Opt("disableEnhancedFireArmEffects", true, Items.BLAZE_POWDER, "Fuego", "Los brazos arden si te quemas."),
            new Opt("enableFreezeArmOverlay", false, Items.POWDER_SNOW_BUCKET, "Escarcha", "Nieve en los brazos si te congelas."),
            new Opt("enableFreezeShake", false, Items.SNOWBALL, "Tiritar", "Temblor de frío."),
            new Opt("enableMud", false, Items.MUD, "Barro", "Barro en los brazos."),
            new Opt("enableSweat", false, Items.WATER_BUCKET, "Sudor", "Gotas de sudor."),
            new Opt("enableBurn", false, Items.MAGMA_BLOCK, "Quemaduras", "Al coger cosas calientes."),
            new Opt("enableParticles", false, Items.GLOWSTONE_DUST, "Partículas", "Las de las animaciones."),
            new Opt("enableModelPartsGlow", false, Items.GLOW_INK_SAC, "Brillo de las piezas", "Lo que brilla en la oscuridad."),
            new Opt("enableBlurGlowAtDark", false, Items.ENDER_EYE, "Resplandor a oscuras", "Halo suave de algunas partículas."));

    private static final List<Opt> PHYSICS = List.of(
            new Opt("disableArmPhysics", true, Items.STRING, "Física de los brazos", "Se balancean con inercia."),
            new Opt("disableNativeItemPhysics", true, Items.LEAD, "Física de los objetos", "Lo que llevas en la mano se mece."));

    private static final List<Opt> MODELS = List.of(
            new Opt("disableBoatMinecartRaftModels", true, Items.MINECART, "Barcas, vagonetas y balsas", "En 3D en la mano."),
            new Opt("disablePistonModels", true, Items.PISTON, "Pistones", "Efectos al llevarlos."),
            new Opt("disableChestModels", true, Items.CHEST, "Cofres", "Efectos al llevarlos."),
            new Opt("disableEnchantingTableModels", true, Items.ENCHANTING_TABLE, "Mesa de encantamientos", "Efectos al llevarla."),
            new Opt("disableResourcePackModelParts", true, Items.ITEM_FRAME, "Piezas de los packs", "Las que añaden los paquetes de recursos."),
            new Opt("renderArmorArmsFP", false, Items.IRON_CHESTPLATE, "Mangas de la armadura", "Tu armadura en los brazos."));

    private static final Item[] KIND_ICONS = {Items.NETHERITE_SWORD, Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.BOW, Items.CROSSBOW, Items.SHIELD, Items.TRIDENT, Items.FISHING_ROD, Items.COOKED_BEEF, Items.GRASS_BLOCK,
            Items.PLAYER_HEAD, Items.SHEARS};

    private PadAnims() {}

    /** ¿Esta acción es de la pestaña ANIMACIONES (se hace aquí y no en el servidor)? */
    static boolean local(PadView view, String action) {
        if (action.equals("tab:" + TAB)) return true;
        return view != null && view.tab().equals(TAB) && !action.startsWith("tab:");
    }

    /** Hace la acción y devuelve la vista nueva. */
    static PadView act(PadView view, String action) {
        if (!action.startsWith("tab:")) apply(action);
        return view(view == null ? List.of() : view.tabs());
    }

    private static void apply(String action) {
        String[] p = action.split(":");
        int dir = p.length > 2 && p[2].equals("-") ? -1 : 1;
        switch (p[0]) {
            case "on" -> {
                // apagar de verdad: Punchy para lo que hacía y todo vuelve a Minecraft normal (PunchyBridge.turnOff)
                if (PunchyBridge.vanilla()) PunchyBridge.turnOn();
                else PunchyBridge.turnOff();
            }
            case "p" -> {
                if (p.length < 2) return;
                Object s = PunchyBridge.read();
                boolean v = PunchyBridge.bool(s, p[1], false);
                PunchyBridge.write(d -> PunchyBridge.set(d, p[1], !v));
            }
            case "k" -> {
                if (p.length < 2) return;
                for (AnimSettings.Kind k : AnimSettings.Kind.values()) if (k.key.equals(p[1])) AnimSettings.toggle(k);
            }
            case "n" -> {
                if (p.length < 2) return;
                Object s = PunchyBridge.read();
                switch (p[1]) {
                    case "velocidad" -> {
                        float v = step(PunchyBridge.num(s, "animationSpeed", 5.5F), 0.5F * dir, 0.5F, 7F);
                        PunchyBridge.write(d -> PunchyBridge.set(d, "animationSpeed", v));
                    }
                    case "fuerza" -> {
                        float v = step(PunchyBridge.num(s, "sprintArmSwingIntensity", 1F), 0.25F * dir, -1F, 1F);
                        PunchyBridge.write(d -> PunchyBridge.set(d, "sprintArmSwingIntensity", v));
                    }
                    case "ritmo" -> {
                        float v = step(PunchyBridge.num(s, "sprintArmSwingSpeed", 0F), 0.25F * dir, -1F, 1F);
                        PunchyBridge.write(d -> PunchyBridge.set(d, "sprintArmSwingSpeed", v));
                    }
                    default -> {
                    }
                }
            }
            case "inspeccionar" -> PunchyBridge.toggleInspect();
            case "mas" -> {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = PunchyBridge.configScreen(mc.screen);
                if (screen != null) mc.setScreen(screen);
            }
            case "reset" -> {
                PunchyBridge.cancelStop();
                AnimSettings.reset();
                Object def = PunchyBridge.defaults();
                if (def != null) {
                    PunchyBridge.write(d -> {
                        for (String f : resettable()) {
                            if (PunchyBridge.has(f)) {
                                Object v = f.equals("animationSpeed") || f.startsWith("sprintArmSwing")
                                        ? (Object) PunchyBridge.num(def, f, 0F) : (Object) PunchyBridge.bool(def, f, false);
                                PunchyBridge.set(d, f, v);
                            }
                        }
                    });
                }
                KeyMapping k = PunchyBridge.inspectKey();
                if (k != null && k.isUnbound()) PunchyBridge.toggleInspect();
            }
            default -> {
            }
        }
    }

    /** Lo que RESTABLECER deja de fábrica: lo que se ve en esta pestaña (la lista negra y los packs de Punchy, no). */
    private static List<String> resettable() {
        List<String> out = new ArrayList<>(List.of("enableMod", "animationSpeed", "sprintArmSwingIntensity", "sprintArmSwingSpeed"));
        for (List<Opt> group : List.of(MOVE, ARMS, PHYSICS, MODELS)) for (Opt o : group) out.add(o.field());
        return out;
    }

    private static float step(float v, float d, float min, float max) {
        float r = Math.round((v + d) * 4F) / 4F; // en cuartos: nada de 5,4999
        return Math.max(min, Math.min(max, r));
    }

    // ---------------------------------------------------------------------------------------------------------------

    static PadView view(List<PadView.Tab> serverTabs) {
        PadView.Builder b = PadView.of("efectos");
        PadCombat.tabs(b, serverTabs);
        b.selected(TAB).compact();

        if (!PunchyBridge.available()) {
            b.hero(new PadView.Row(new ItemStack(Items.IRON_SWORD), "Animaciones de Punchy", TEXT,
                    List.of("Punchy no está cargado: vuelve a instalar TF Client.", "Son de Punchy Guys Studios, con su permiso."),
                    -1, "", PadView.Btn.of("MODRINTH", "§link:" + PunchyBridge.MODRINTH, PadView.BLUE), null));
            return b.build();
        }

        Object s = PunchyBridge.read();
        boolean on = !PunchyBridge.vanilla();
        List<String> hero = new ArrayList<>();
        hero.add(on ? "Golpes, minar, comer, arco, nadar... en primera persona." : "Apagadas: todo como en Minecraft normal.");
        hero.add("De Punchy Guys Studios, con su permiso. Se guarda solo.");
        b.hero(new PadView.Row(new ItemStack(Items.IRON_SWORD), "Animaciones de Punchy", TEXT, hero, -1, "",
                PadView.Btn.of(on ? "ACTIVADAS" : "APAGADAS", "on", on ? PadView.GREEN : PadView.RED),
                PadView.Btn.of("MODRINTH", "§link:" + PunchyBridge.MODRINTH, PadView.BLUE)).image("punchy:icon.png"));

        b.row(section("GENERAL"));
        b.row(stepper(Items.CLOCK, "Velocidad", "Cuánto tardan los golpes y gestos (más = más rápido).",
                number(PunchyBridge.num(s, "animationSpeed", 5.5F)), "n:velocidad"));
        KeyMapping key = PunchyBridge.inspectKey();
        if (key != null) {
            boolean bound = !key.isUnbound();
            b.row(toggle(Items.SPYGLASS, "Inspeccionar", bound ? "Mira lo que llevas con la tecla " + key.getTranslatedKeyMessage().getString() + "."
                    : "Sin tecla: nunca se inspecciona.", bound, "inspeccionar"));
        }

        b.row(section("CON QUÉ OBJETOS"));
        b.row(note("NO: ese objeto se ve y se mueve como en Minecraft normal."));
        AnimSettings.Kind[] kinds = AnimSettings.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            AnimSettings.Kind k = kinds[i];
            b.row(toggle(KIND_ICONS[Math.min(i, KIND_ICONS.length - 1)], k.title, k.help, AnimSettings.on(k), "k:" + k.key));
        }

        b.row(section("MOVIMIENTO"));
        for (Opt o : MOVE) {
            if (!PunchyBridge.has(o.field())) continue;
            b.row(opt(s, o));
            if (o.field().equals("enableSprintArmSwing") && value(s, o)) {
                b.row(stepper(Items.SUGAR, "Fuerza al correr", "Cuánto se mueven los brazos.",
                        percent(PunchyBridge.num(s, "sprintArmSwingIntensity", 1F)), "n:fuerza"));
                b.row(stepper(Items.CLOCK, "Ritmo al correr", "0 = el normal.", signed(PunchyBridge.num(s, "sprintArmSwingSpeed", 0F)), "n:ritmo"));
            }
        }

        b.row(section("EFECTOS EN LOS BRAZOS"));
        for (Opt o : ARMS) if (PunchyBridge.has(o.field())) b.row(opt(s, o));

        b.row(section("FÍSICA"));
        for (Opt o : PHYSICS) if (PunchyBridge.has(o.field())) b.row(opt(s, o));

        b.row(section("OBJETOS EN 3D"));
        for (Opt o : MODELS) if (PunchyBridge.has(o.field())) b.row(opt(s, o));

        b.row(section("MÁS"));
        b.row(new PadView.Row(new ItemStack(Items.WRITABLE_BOOK), "Ajustes de Punchy", TEXT,
                List.of("Su lista de objetos sin animar, packs de animaciones y compatibilidad con otros mods."), -1, "",
                PadView.Btn.of("ABRIR", "mas", PadView.BLUE), null).clickable("mas"));
        String ver = PunchyBridge.version();
        b.row(note("Punchy " + (ver.isEmpty() ? "" : ver + " ") + "· Punchy Guys Studios · modrinth.com/mod/punchy-fpa"));

        b.footer(PadView.Btn.of("RESTABLECER", "reset", PadView.GRAY));
        return b.build();
    }

    private static boolean value(Object s, Opt o) {
        return PunchyBridge.bool(s, o.field(), o.inverted()) != o.inverted();
    }

    private static PadView.Row opt(Object s, Opt o) {
        return toggle(o.icon(), o.title(), o.help(), value(s, o), "p:" + o.field());
    }

    private static String number(float v) {
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    private static String percent(float v) {
        return Math.round(v * 100) + " %";
    }

    private static String signed(float v) {
        String n = String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
        return v > 0 ? "+" + n : n;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Filas (las mismas que COMBATE: el SÍ/NO se dibuja como interruptor)
    // ---------------------------------------------------------------------------------------------------------------

    private static PadView.Row toggle(Item icon, String title, String help, boolean on, String action) {
        return new PadView.Row(new ItemStack(icon), title, on ? TEXT : 0x7E8CA8, help.isEmpty() ? List.of() : List.of(help), -1, "",
                PadView.Btn.of(on ? "SÍ" : "NO", action, on ? PadView.GREEN : PadView.RED), null).clickable(action);
    }

    private static PadView.Row stepper(Item icon, String title, String help, String value, String key) {
        return new PadView.Row(new ItemStack(icon), title, TEXT, help.isEmpty() ? List.of() : List.of(help), -1, value,
                PadView.Btn.of("-", key + ":-", PadView.BLUE), PadView.Btn.of("+", key + ":+", PadView.BLUE));
    }

    private static PadView.Row section(String title) {
        return new PadView.Row(ItemStack.EMPTY, title, TEXT, List.of(), -1, "", null, null);
    }

    private static PadView.Row note(String text) {
        return new PadView.Row(ItemStack.EMPTY, "", TEXT, List.of(text), -1, "", null, null);
    }
}
