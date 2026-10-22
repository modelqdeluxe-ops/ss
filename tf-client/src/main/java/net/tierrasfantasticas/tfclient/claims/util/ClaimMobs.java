package net.tierrasfantasticas.tfclient.claims.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.tierrasfantasticas.tfclient.bosses.BossEntity;

/**
 * (1.3.109, el dueño: «los NPC se mueren dentro de la prote, se queman: los considera mobs hostiles») qué es un monstruo
 * para las protecciones. Los NPC de escena y la fauna de los packs son BossEntity, que por dentro es Enemy (como los
 * jefes): «¿Arden los monstruos?» los empujaba, los quemaba y les quitaba vida hasta matarlos, y «¿Salen monstruos?: NO»
 * no dejaba ponerlos.
 */
public final class ClaimMobs {
    private ClaimMobs() {}

    public static boolean hostile(Entity e) {
        return e instanceof Enemy && !(e instanceof BossEntity b && (b.isNpc() || b.isFauna()));
    }
}
