/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * La IA de MythicMobs de los mobs de los jefes: AIGoalSelectors (qué hace: perseguir, pegar, pasear, mirar...) y
 * AITargetSelectors (a quién busca), sobre la de su tipo de Minecraft. Sin la lista, el mob tiene la IA de su tipo (un
 * zombi persigue y pega a los jugadores; un cerdo pasea y no ataca a nadie); «clear» (alias «reset») la quita entera y
 * lo de después se añade; con un número delante («1 meleeattack») es la prioridad, que aquí no hace falta porque cada
 * objetivo es una cosa distinta. runaigoalselector{goal} y runaitargetselector{target} añaden uno a mitad de pelea
 * (clear borra los de antes), y resetai vuelve a los de su tipo.
 *
 * Lo que la entidad (BossEntity) ya hace con ellos está en AI_OK del auditor de los jefes (tools/bosses/audit.py, que
 * tiene esta misma tabla): lo demás lo marca como problema en el pack que lo use, para copiarlo entonces. Los «type:x»
 * son lo que hace solo su tipo de Minecraft sin un objetivo de MythicMobs que lo nombre (las bolas de fuego del ghast,
 * la carga del vex, el slime que se parte al morir...).
 */
public final class SkillAI {
    private SkillAI() {}

    /** Alias de MythicMobs de los objetivos de IA (wiki: Mobs/Custom-AI). */
    private static final Map<String, String> GOAL_ALIAS = Map.ofEntries(
            Map.entry("reset", "clear"), Map.entry("swim", "float"), Map.entry("opendoors", "opendoor"),
            Map.entry("lookaround", "randomlookaround"), Map.entry("gotospawn", "gotospawnlocation"),
            Map.entry("meleeattackcond", "meleeattackconditional"), Map.entry("runfromplayers", "fleeplayers"),
            Map.entry("runfromgolems", "fleegolems"), Map.entry("runfromvillagers", "fleevillagers"),
            Map.entry("runfromwolves", "fleewolf"), Map.entry("runfromfaction", "fleefaction"),
            Map.entry("fleeif", "fleeconditional"), Map.entry("movewithinconditional", "movewithindistanceoftargetconditional"),
            Map.entry("movewithincond", "movewithindistanceoftargetconditional"), Map.entry("followpath", "followroute"),
            Map.entry("patrolroute", "patrol"), Map.entry("goto", "gotolocation"),
            Map.entry("gotoconditional", "gotolocationconditional"), Map.entry("gotolocationcond", "gotolocationconditional"),
            Map.entry("gotocond", "gotolocationconditional"), Map.entry("panicwhenonfire", "panic"),
            Map.entry("creeperexplode", "creeperswell"), Map.entry("arrowattack", "rangedattack"),
            Map.entry("bowshoot", "bowattack"), Map.entry("bowmaster", "bowattack"),
            Map.entry("rangedattackconditional", "arrowattackconditional"), Map.entry("arrowattackcond", "arrowattackconditional"),
            Map.entry("bowattackcond", "bowattackconditional"), Map.entry("crossbowattackcond", "crossbowattackconditional"));

    /** Los objetivos de IA de MythicMobs (para saber si uno del pack existe; los que no, MythicMobs los ignora). */
    public static final Set<String> GOALS = Set.of("clear", "breakdoors", "eatgrass", "float", "lookatplayers", "lookattarget",
            "opendoor", "randomlookaround", "gotospawnlocation", "donothing", "meleeattack", "meleeattackconditional",
            "movetowardstarget", "randomstroll", "restrictsun", "fleeplayers", "fleegolems", "fleevillagers", "fleewolf",
            "fleefaction", "fleesun", "fleeconditional", "spiderattack", "zombieattack", "leapattarget", "movethroughvillage",
            "movetoblock", "movetolava", "movetowater", "movetowardsrestriction", "movewithindistanceoftarget",
            "movewithindistanceoftargetconditional", "movetowardstargetconditional", "followroute", "patrol", "gotolocation",
            "gotolocationconditional", "gotoowner", "gotoparent", "panic", "randomfly", "randomnod", "horrified", "breed",
            "creeperswell", "rangedattack", "bowattack", "arrowattackconditional", "bowattackconditional", "crossbowattack",
            "crossbowattackconditional");

    private static final Map<String, String> TARGET_ALIAS = Map.ofEntries(
            Map.entry("attacker", "hurtbytarget"), Map.entry("damager", "hurtbytarget"), Map.entry("monster", "monsters"),
            Map.entry("player", "players"), Map.entry("villager", "villagers"), Map.entry("iron_golem", "irongolem"),
            Map.entry("iron_golems", "irongolem"), Map.entry("golems", "irongolem"), Map.entry("golem", "irongolem"),
            Map.entry("nearestconditional", "nearestconditionaltarget"), Map.entry("nearestif", "nearestconditionaltarget"),
            Map.entry("ownerhurtby", "ownerattacker"), Map.entry("ownerhurtbytarget", "ownerattacker"),
            Map.entry("ownerdamager", "ownerattacker"), Map.entry("ownerattack", "ownertarget"), Map.entry("ownerhurt", "ownertarget"),
            Map.entry("parenthurtbytarget", "parenthurtby"), Map.entry("parentdamager", "parenthurtby"),
            Map.entry("parentattacker", "parenthurtby"), Map.entry("parenthurt", "parenttarget"), Map.entry("parentattack", "parenttarget"),
            Map.entry("otherfaction", "nearestotherfaction"), Map.entry("otherfactionmonsters", "nearestotherfactionmonsters"));

    /** A quién buscan (los de MythicMobs; los demás, MythicMobs los ignora). */
    public static final Set<String> TARGETS = Set.of("clear", "hurtbytarget", "monsters", "players", "villagers", "irongolem",
            "nearestconditionaltarget", "ownerattacker", "ownertarget", "parenthurtby", "parenttarget", "nearestotherfaction",
            "nearestotherfactionmonsters", "specificfaction", "specificfactionmonsters");

    /** «1 meleeattack», «lookattarget{r=25}», «MeleeAttack» → «meleeattack» (con sus alias). */
    public static String goalName(String raw) {
        String n = bare(raw);
        return GOAL_ALIAS.getOrDefault(n, n);
    }

    /** «1 players», «specificfaction zombie» → «players», «specificfaction». */
    public static String targetName(String raw) {
        String n = bare(raw);
        return TARGET_ALIAS.getOrDefault(n, n);
    }

    private static String bare(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int brace = s.indexOf('{');
        if (brace >= 0) s = s.substring(0, brace).trim();
        String[] p = s.split("\\s+");
        // «1 meleeattack»: la prioridad delante; «specificfaction zombie»: el argumento detrás
        int i = p.length > 1 && p[0].matches("-?\\d+") ? 1 : 0;
        return i < p.length ? p[i] : "";
    }

    /** Un número de las opciones de ese objetivo («lookattarget{r=25}» → r). */
    public static double arg(String raw, double def, String... keys) {
        if (raw == null) return def;
        int a = raw.indexOf('{'), b = raw.lastIndexOf('}');
        if (a < 0 || b < a) return def;
        for (String part : raw.substring(a + 1, b).split("[;,]")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String k = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            for (String key : keys) {
                if (k.equals(key)) {
                    try {
                        return Double.parseDouble(part.substring(eq + 1).trim());
                    } catch (NumberFormatException e) {
                        return def;
                    }
                }
            }
        }
        return def;
    }

    /** El de la lista con ese nombre (null si no está). */
    public static String find(List<String> list, String name) {
        for (String g : list) if (goalName(g).equals(name)) return g;
        return null;
    }

    public static boolean has(List<String> list, String... names) {
        for (String g : list) {
            String n = goalName(g);
            for (String name : names) if (n.equals(name)) return true;
        }
        return false;
    }

    public static boolean hasTarget(List<String> list, String name) {
        for (String g : list) if (targetName(g).equals(name)) return true;
        return false;
    }

    /**
     * La lista del pack sobre la de su tipo: sin lista, la de su tipo; «clear» quita todo lo de antes; lo demás se
     * añade (los que no existen en MythicMobs se ignoran, como hace él).
     */
    public static List<String> apply(List<String> base, List<String> entries, boolean targets) {
        List<String> out = new ArrayList<>(base);
        if (entries == null) return out;
        for (String e : entries) {
            if (e == null || e.isBlank()) continue;
            String n = targets ? targetName(e) : goalName(e);
            if (n.equals("clear")) {
                out.clear();
                continue;
            }
            if (!(targets ? TARGETS : GOALS).contains(n)) continue;
            out.add(e.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** Lo que trae su tipo de Minecraft (1.20.1) de lo que importa en una pelea, con los nombres de MythicMobs. */
    public static List<String> vanillaGoals(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            // Zombie.registerGoals + addBehaviourGoals (no flotan: se hunden)
            case "ZOMBIE", "HUSK", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN", "PIG_ZOMBIE" ->
                    List.of("zombieattack", "movethroughvillage", "randomstroll", "lookatplayers", "randomlookaround");
            // Drowned: con tridente dispara (DrownedTridentAttackGoal); si no, como un zombi
            case "DROWNED" -> List.of("type:drowned", "zombieattack", "randomstroll", "lookatplayers", "randomlookaround");
            // AbstractSkeleton: con el arco que les da Minecraft al aparecer, RangedBowAttackGoal; el esqueleto del
            // Wither lleva espada de piedra (MeleeAttackGoal)
            case "SKELETON", "STRAY" -> List.of("restrictsun", "fleesun", "fleewolf", "bowattack", "randomstroll", "lookatplayers",
                    "randomlookaround");
            case "WITHER_SKELETON" -> List.of("restrictsun", "fleesun", "fleewolf", "meleeattack", "randomstroll", "lookatplayers",
                    "randomlookaround");
            case "IRON_GOLEM" -> List.of("meleeattack", "movetowardstarget", "movetowardsrestriction", "randomstroll",
                    "lookatplayers", "randomlookaround");
            case "VINDICATOR" -> List.of("float", "meleeattack", "randomstroll", "lookatplayers");
            case "RAVAGER" -> List.of("float", "meleeattack", "type:ravager", "randomstroll", "lookatplayers");
            case "PILLAGER" -> List.of("float", "crossbowattack", "randomstroll", "lookatplayers");
            case "EVOKER", "ILLUSIONER" -> List.of("float", "type:spellcaster", "fleeplayers", "randomstroll", "lookatplayers");
            case "WITCH" -> List.of("float", "type:witch", "randomstroll", "lookatplayers", "randomlookaround");
            case "SPIDER", "CAVE_SPIDER" -> List.of("float", "leapattarget", "spiderattack", "randomstroll", "lookatplayers",
                    "randomlookaround");
            case "SILVERFISH" -> List.of("float", "meleeattack", "type:silverfish");
            case "ENDERMITE" -> List.of("float", "meleeattack", "randomstroll", "lookatplayers", "randomlookaround");
            // El enderman además se teletransporta solo (al recibir flechas, en el agua)
            case "ENDERMAN" -> List.of("float", "meleeattack", "type:enderman", "randomstroll", "lookatplayers", "randomlookaround");
            case "POLAR_BEAR" -> List.of("float", "meleeattack", "panic", "randomstroll", "lookatplayers", "randomlookaround");
            case "WOLF" -> List.of("float", "leapattarget", "meleeattack", "randomstroll", "lookatplayers", "randomlookaround");
            case "CREEPER" -> List.of("float", "creeperswell", "meleeattack", "randomstroll", "lookatplayers", "randomlookaround");
            // Con cerebro (Brain) en vez de objetivos: pegan cuerpo a cuerpo (el piglin, con ballesta si la lleva)
            case "ZOGLIN" -> List.of("meleeattack", "randomstroll", "lookatplayers");
            // ...y fuera del Nether se convierten en zombis a los 15 s
            case "PIGLIN_BRUTE", "HOGLIN" -> List.of("meleeattack", "type:zombify", "randomstroll", "lookatplayers");
            case "PIGLIN" -> List.of("type:piglin", "type:zombify", "randomstroll", "lookatplayers");
            case "BLAZE" -> List.of("type:blaze", "movetowardsrestriction", "randomstroll", "lookatplayers", "randomlookaround");
            case "GHAST" -> List.of("type:ghast");
            case "VEX" -> List.of("float", "type:vex", "lookatplayers");
            case "PHANTOM" -> List.of("type:phantom");
            case "SLIME", "MAGMA_CUBE" -> List.of("type:slime");
            case "BEE" -> List.of("type:bee", "randomstroll");
            case "GOAT" -> List.of("type:goat", "randomstroll", "lookatplayers");
            case "LLAMA", "TRADER_LLAMA" -> List.of("type:llama", "panic", "randomstroll", "lookatplayers");
            case "SNOW_GOLEM", "SNOWMAN" -> List.of("type:snowgolem", "randomstroll", "lookatplayers", "randomlookaround");
            case "SHULKER" -> List.of("type:shulker");
            case "GUARDIAN", "ELDER_GUARDIAN" -> List.of("type:guardian");
            case "WARDEN" -> List.of("type:warden");
            case "WITHER" -> List.of("type:wither");
            case "ENDER_DRAGON" -> List.of("type:enderdragon");
            case "BAT", "ALLAY", "PARROT" -> List.of("type:flying");
            case "GIANT" -> List.of(); // Giant no trae objetivos: se queda quieto
            // Animales y aldeanos: pasean, miran y huyen si les pegan; no atacan a los jugadores
            default -> List.of("float", "panic", "randomstroll", "lookatplayers", "randomlookaround");
        };
    }

    /** A quién busca su tipo de Minecraft (de lo que importa aquí: los jugadores y quien le pega). */
    public static List<String> vanillaTargets(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "ZOMBIE", "HUSK", "ZOMBIE_VILLAGER", "DROWNED", "SKELETON", "STRAY", "WITHER_SKELETON", "VINDICATOR", "RAVAGER",
                 "PILLAGER", "EVOKER", "ILLUSIONER", "WITCH", "SILVERFISH", "ENDERMITE", "CREEPER", "PIGLIN_BRUTE", "HOGLIN",
                 "ZOGLIN", "BLAZE", "VEX", "SLIME", "MAGMA_CUBE", "GUARDIAN", "ELDER_GUARDIAN", "SHULKER", "WARDEN",
                 "WITHER", "PIGLIN" -> List.of("hurtbytarget", "players");
            // A los jugadores solo de noche o a oscuras (SpiderTargetGoal): de día, solo a quien le pega
            case "SPIDER", "CAVE_SPIDER" -> List.of("hurtbytarget", "type:spider");
            case "GHAST", "PHANTOM" -> List.of("players");
            // Neutrales: solo a quien les pega (el gólem, además, a los monstruos)
            case "IRON_GOLEM" -> List.of("hurtbytarget", "monsters");
            case "SNOW_GOLEM", "SNOWMAN" -> List.of("monsters");
            case "WOLF", "POLAR_BEAR", "ENDERMAN", "ZOMBIFIED_PIGLIN", "PIG_ZOMBIE", "BEE", "LLAMA", "TRADER_LLAMA", "PANDA",
                 "GOAT", "DOLPHIN" -> List.of("hurtbytarget");
            default -> List.of();
        };
    }

    /** La resistencia al empuje de su tipo de Minecraft (atributo knockback_resistance). */
    public static double knockbackResistance(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "IRON_GOLEM", "WARDEN", "SHULKER", "ENDER_DRAGON", "WITHER" -> 1.0;
            case "RAVAGER" -> 0.75;
            case "HOGLIN", "ZOGLIN" -> 0.6;
            case "GOAT", "CAMEL", "SNIFFER", "PIGLIN_BRUTE" -> 0.0;
            default -> 0.0;
        };
    }

    /** Sin arco (PreventRandomEquipment): el esqueleto pega cuerpo a cuerpo en vez de disparar. */
    public static List<String> withoutBow(List<String> goals) {
        List<String> out = new ArrayList<>();
        for (String g : goals) out.add(goalName(g).equals("bowattack") && !g.contains("{") ? "meleeattack" : g);
        return out;
    }

    /** Persigue a su objetivo (algún objetivo de los que andan hasta él). */
    public static boolean chases(List<String> goals) {
        return has(goals, "meleeattack", "zombieattack", "spiderattack", "movetowardstarget", "movewithindistanceoftarget");
    }

    /** Le pega con el golpe de Minecraft (MeleeAttackGoal y los que lo heredan). */
    public static boolean melee(List<String> goals) {
        return has(goals, "meleeattack", "zombieattack", "spiderattack");
    }
}
