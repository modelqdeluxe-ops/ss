package net.tierrasfantasticas.tfclient.bosses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;

/** Eventos del servidor de los jefes: quien empieza a ver a un jefe lo ve como está, y de un agarre no se sale solo. */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class BossEvents {
    private BossEvents() {}

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof BossEntity b && event.getEntity() instanceof ServerPlayer p) SkillMobs.replay(b, p);
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        // Agarrado por un jefe: hasta que te suelte (o se vaya el jefe) no te bajas con mayúsculas
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof SeatEntity s && !s.releasing && !s.isRemoved()) {
            event.setCanceled(true);
        }
    }
}
