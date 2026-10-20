package net.tierrasfantasticas.tfclient.vacio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los caballeros sacros del santuario (1.3.97; el dueño: «busca skins o modelos de caballeros sacros para que pongas en
 * ese santuario, que tenga vida, con diálogos»; y «debes explicar lo que es el santuario, porque no es congruente esa
 * zona bonita en una dimensión de caos»). Cinco miembros de la Orden del Alba que siguen en pie junto a Caelthar, cada uno
 * con la armadura de otro metal (tools/void/caballeros.py), su sitio, por donde pasea, y lo que cuenta: por qué el
 * santuario es lo único vivo del Void, qué son los Fragmentos y las Anclas que los sostienen, la armadura del Coloso,
 * el jardín, los Once. Te saludan en el chat al acercarte (una vez cada 10 minutos) y con clic derecho hablan
 * ({@link KnightScreen} en su modo de caballero sacro).
 *
 * <p>El servidor los pone (y los vuelve a poner si faltan) junto al santuario: su sitio es relativo al de Caelthar
 * ({@link VoidIsland#knightPos}), así que se mudan con él.
 */
public final class SacredKnights {
    /** Una pregunta y su respuesta. */
    public record Topic(String question, String answer) {}

    /**
     * Un caballero: nombre, título, metal (su modelo: vacio.caballero_&lt;metal&gt;), lo que lleva en la mano, su sitio respecto
     * a Caelthar (dx, dy, dz: dy −1 es el suelo del santuario), cuánto pasea, hacia dónde mira, el saludo, de qué habla y lo
     * que dice al verte llegar.
     */
    public record Def(int id, String name, String title, String metal, String item, int dx, int dy, int dz, float radius, float yaw,
            String greet, List<Topic> topics, String ambient) {
        public String model() {
            return "vacio.caballero_" + metal;
        }
    }

    public static final List<Def> ALL = List.of(
            new Def(1, "Ser Bastian", "Guardián de la Puerta del Alba", "plata", "tfclient:fairy_sword", 0, -1, 26, 3F, 0F,
                    "Alto, viajero... Ah, la luz te reconoce. Soy Bastian, guardián de la Puerta del Alba. Mientras yo esté aquí, nada del Vacío cruzará este umbral.",
                    List.of(new Topic("¿Por qué este lugar es tan bonito?",
                                    "Porque es lo último. Cuando el ojo del Vacío abrió sus fauces se tragó nuestra tierra entera: los bosques, los ríos, las ciudades de la Orden. Antes de que cayera lo último, Caelthar alzó el Velo sobre el jardín del templo. Los cerezos, el estanque, las flores... es lo único vivo que queda en todo el Void. Fuera del Velo solo hay piedra y ceniza."),
                            new Topic("¿Qué guardas aquí?",
                                    "La puerta y el puente. Las criaturas del Vacío rondan los Fragmentos y algunas huelen la luz. No pueden entrar en el Velo, pero lo intentan. Yo me aseguro de que nadie que no sea de la luz lo cruce."),
                            new Topic("¿Cómo vuelvo aquí desde la grieta?",
                                    "Cruza los Fragmentos por el Sendero del Alba hasta el Puente de Piedra. Donde el puente toca la isla de la grieta estaba nuestro viejo santuario; ahora hay un portal. Quédate un momento en su luz y te traerá de vuelta a esta puerta.")),
                    "La luz te guarde, viajero."),
            new Def(2, "Ser Varen", "El Vigía", "acero", "tfclient:fairy_spear", 18, -1, 21, 4F, 0F,
                    "¿Ves aquello, al sur? Llevo siglos mirándolo. Soy Varen, el Vigía de la Orden.",
                    List.of(new Topic("¿Qué son esas islas que flotan?",
                                    "Restos. El agujero negro del cielo se tragó esta dimensión entera: lo que flota es lo que no alcanzó a devorar. Sin nada que lo sujete, todo caería hacia él. Mira bien: en cada fragmento hay un Ancla del Alba, un pozo de luz. Ahí un hermano mío se hizo luz para sostenerlo. Su magia es lo único que impide que estas tierras se derrumben."),
                            new Topic("¿Y si un Ancla se apaga?",
                                    "El fragmento cae al ojo del Vacío, y con él todo lo que tenga encima. Por eso velamos. Y por eso su luz te cura: es la de un caballero que juró proteger a quien camine por aquí. Cuando el Vacío te corroa, quédate en una de sus burbujas."),
                            new Topic("¿Qué hay más allá?",
                                    "Los Fragmentos del Alba Rota: los Llanos de Ceniza, el Bosque de Cristal al este, las Fauces de Obsidiana al oeste, las Ruinas de nuestra orden al norte y, en medio, la Herida, por donde se ve el ojo. Más allá, la isla de la grieta, donde duermen las criaturas que encerramos.")),
                    "No te alejes de las Anclas, viajero."),
            new Def(3, "Hermano Osric", "Custodio del Altar del Vacío", "bronce", "minecraft:book", 3, 0, -21, 1.5F, 0F,
                    "Paz, viajero. Soy Osric, y custodio el Altar del Vacío y lo que descansa en él.",
                    List.of(new Topic("¿Qué es esa armadura?",
                                    "La piel del Coloso del Vacío, la primera criatura que cayó cuando el ojo se abrió. La Orden la forjó en armadura: el Vacío no se come lo que es suyo, y quien la viste camina por él sin que lo corroa."),
                            new Topic("¿Cómo la consigo?",
                                    "Demuestra que puedes con lo que el Vacío esconde: derrota a cinco criaturas de las grietas. Entonces será tuya. Pero escucha: el Coloso solo dio una piel, y la Orden forja una armadura por guerrero. No hay segunda."),
                            new Topic("¿Por qué la guardáis aquí?",
                                    "Porque fuera del Velo nada dura. Aquí la luz la conserva... y nos recuerda que hasta lo que nace del Vacío puede servir a la luz.")),
                    "El Coloso aún espera a su portador."),
            new Def(4, "Dama Liora", "La Jardinera", "rosa", "minecraft:cherry_sapling", 22, -1, 2, 6F, 270F,
                    "¡Oh! Cuidado con los pétalos... Soy Liora. Cuido lo último que florece.",
                    List.of(new Topic("¿Cómo crecen flores en el Void?",
                                    "Solo aquí dentro. El Corazón del Alba late sobre el templo y su calor llega a cada raíz; el Velo no deja pasar el hambre del Vacío. Traje las semillas del último jardín de nuestra tierra antes de que cayera. Fuera del Velo nada brota: la tierra se volvió piedra y ceniza."),
                            new Topic("¿No es triste?",
                                    "A veces. Pero cada cerezo que florece es una promesa: mientras algo viva aquí, el Vacío no ha ganado. Algún día, cuando las grietas se cierren, plantaré estos árboles en los Fragmentos.")),
                    "¿Hueles los cerezos? Es lo único que huele a casa."),
            new Def(5, "Aela", "Novicia de la Orden", "marfil", "minecraft:lantern", -12, -1, 12, 3F, 225F,
                    "¡Hola! Perdona, estaba escuchando a los Once. Soy Aela, novicia de la Orden.",
                    List.of(new Topic("¿Quiénes son los Once?",
                                    "Los once caballeros que se hicieron luz para sellar la grieta. Cada faro del borde del santuario es uno de ellos, y sus ecos aún suenan en las Anclas del Alba. Si te paras en una, a veces se oye lo que dijeron."),
                            new Topic("¿Y Caelthar?",
                                    "Era el más joven, como yo. Cuando los demás se hicieron luz, él se quedó en pie para custodiar el umbral. Dice que no es valiente... pero nunca se ha ido."),
                            new Topic("¿Tú también serás luz?",
                                    "Si hace falta, sí. Pero Caelthar dice que la Orden también necesita a alguien que viva para recordarla. Por eso escribo cada eco que oigo.")),
                    "Los Once nos escuchan."));

    /** Cuándo saludó cada uno a cada jugador (para no repetirlo: una vez cada 10 minutos). */
    private static final Map<String, Long> GREETED = new HashMap<>();

    private SacredKnights() {}

    public static Def def(int id) {
        for (Def d : ALL) if (d.id() == id) return d;
        return null;
    }

    /** Su sitio (pies) en el mundo. */
    public static BlockPos home(Def d) {
        BlockPos k = VoidIsland.knightPos();
        return new BlockPos(k.getX() + d.dx(), k.getY() + d.dy(), k.getZ() + d.dz());
    }

    /** Cada 5 s (TFVoid): que estén todos (uno de cada) y en su sitio. */
    static void ensure(ServerLevel level) {
        if (VoidIsland.get() == null) return;
        for (SacredKnight k : level.getEntities(EntityTypeTest.forClass(SacredKnight.class), e -> true)) {
            Def d = def(k.variant());
            // (los de un santuario de antes, o de un caballero que ya no existe, fuera)
            if (d == null || k.blockPosition().distSqr(home(d)) > 40 * 40) k.discard();
        }
        for (Def d : ALL) {
            BlockPos at = home(d);
            if (!level.hasChunkAt(at) || !level.areEntitiesLoaded(ChunkPos.asLong(at))) continue;
            List<SacredKnight> here = level.getEntitiesOfClass(SacredKnight.class, new AABB(at).inflate(40), e -> e.variant() == d.id());
            for (int i = 1; i < here.size(); i++) here.get(i).discard();
            if (!here.isEmpty()) continue;
            SacredKnight k = TFVoid.SACRED.get().create(level);
            if (k == null) continue;
            k.setup(d, at);
            level.addFreshEntity(k);
            TFClient.LOGGER.info("TF Void: {} aparece en el santuario ({})", d.name(), at);
        }
    }

    /** Al acercarse un jugador (a menos de 6 bloques): su saludo en el chat, una vez cada 10 minutos. */
    static void greet(SacredKnight k, ServerPlayer p) {
        Def d = def(k.variant());
        if (d == null) return;
        String key = d.id() + ":" + p.getUUID();
        long now = p.level().getGameTime();
        Long at = GREETED.get(key);
        if (at != null && now - at < 12000) return;
        GREETED.put(key, now);
        p.sendSystemMessage(Component.literal("✦ " + d.name() + ": ").withStyle(s -> s.withColor(TextColor.fromRgb(0xFFD66B)))
                .append(Component.literal(d.ambient()).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
    }

    static void forget(UUID player) {
        GREETED.keySet().removeIf(k -> k.endsWith(":" + player));
    }
}
