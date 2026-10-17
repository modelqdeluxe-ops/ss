package net.tierrasfantasticas.tfclient.bosses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;

/**
 * Eventos del servidor de los jefes: quien empieza a ver a un jefe lo ve como está, de un agarre no se sale solo, y los
 * disparadores que vienen de Minecraft (~onDismounted, ~onHear).
 */
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

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDismount(EntityMountEvent event) {
        // ~onDismounted: el jefe (o un esbirro) se baja de lo que montaba
        if (event.isDismounting() && !event.isCanceled() && !event.getLevel().isClientSide
                && event.getEntityMounting() instanceof BossEntity b && b.def() != null) {
            SkillMobs.dismounted(b, event.getEntityBeingMounted());
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        // Los jefes no sueltan objetos (orden del dueño): tampoco los zombis, esqueletos... que sacan en la pelea
        if (event.getEntity().getTags().contains(SkillMobs.SUMMONED)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(SkillMobs.SUMMONED)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onVibration(VanillaGameEvent event) {
        // ~onHear de los mobs con Hearing: Enabled (como el Warden: lo que hace vibrar, salvo lo que se calla agachado o
        // lo amortiguado)
        if (!(event.getLevel() instanceof ServerLevel level) || !SkillMobs.anyHearing()) return;
        if (!event.getVanillaEvent().is(GameEventTags.VIBRATIONS)) return;
        Entity src = event.getCause();
        if (src != null) {
            if (src.isSpectator() || src.dampensVibrations()) return;
            if (src.isSteppingCarefully() && event.getVanillaEvent().is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) return;
        }
        String type = net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.getKey(event.getVanillaEvent()).getPath();
        SkillMobs.heard(level, event.getEventPosition(), type.toUpperCase(java.util.Locale.ROOT), src);
    }
}
