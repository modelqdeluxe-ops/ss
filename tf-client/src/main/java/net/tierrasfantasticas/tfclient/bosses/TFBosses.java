/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;

/**
 * Los jefes (bosses) del TF Client: sus entidades (el jefe, sus esbirros, la grieta y el asiento de los agarres), los
 * objetos que sueltan, el comando /tf boss (la ventana del staff) y lo que conecta el motor de skills con el mundo.
 */
public final class TFBosses {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TFClient.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TFClient.MOD_ID);

    public static final RegistryObject<EntityType<BossEntity>> JEFE = ENTITIES.register("jefe", () ->
            EntityType.Builder.<BossEntity>of((t, l) -> new BossEntity(t, l, true), MobCategory.MONSTER)
                    .sized(1.4F, 2.7F).clientTrackingRange(10).updateInterval(1).build("jefe"));
    public static final RegistryObject<EntityType<BossEntity>> ESBIRRO = ENTITIES.register("esbirro", () ->
            EntityType.Builder.<BossEntity>of((t, l) -> new BossEntity(t, l, false), MobCategory.MONSTER)
                    .sized(0.8F, 1.9F).clientTrackingRange(8).updateInterval(2).build("esbirro"));
    /** (1.3.101) Los NPC de escenas (npcs/): el mismo motor que los esbirros, pero se guardan con el mundo. */
    public static final RegistryObject<EntityType<BossEntity>> NPC = ENTITIES.register("npc", () ->
            EntityType.Builder.<BossEntity>of((t, l) -> new BossEntity(t, l, false), MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).updateInterval(2).noSummon().build("npc"));
    public static final RegistryObject<EntityType<RiftEntity>> GRIETA = ENTITIES.register("grieta", () ->
            EntityType.Builder.<RiftEntity>of(RiftEntity::new, MobCategory.MISC)
                    .sized(3.2F, 8.5F).clientTrackingRange(10).updateInterval(5).noSummon().fireImmune().build("grieta"));
    public static final RegistryObject<EntityType<SeatEntity>> ASIENTO = ENTITIES.register("asiento", () ->
            EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).noSummon().build("asiento"));

    /** Objetos que sueltan los jefes (dropitem del pack): id de MythicMobs en minúsculas → objeto. */
    public static final Map<String, RegistryObject<Item>> DROPS = new LinkedHashMap<>();

    private TFBosses() {}

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        List<SkillDefs.ClassDef> all = new ArrayList<>(SkillDefs.bosses().values());
        all.addAll(SkillDefs.fauna().values());
        for (SkillDefs.ClassDef c : all) {
            if (c.bossInfo == null) continue;
            for (SkillDefs.Drop d : c.bossInfo.drops.values()) {
                String id = itemId(c.id, d.id());
                DROPS.put(c.id + ":" + d.id().toLowerCase(Locale.ROOT), ITEMS.register(id, () -> BossItems.create(c, d)));
            }
        }
        ITEMS.register(modBus);
        modBus.addListener(TFBosses::attributes);
        SkillMobs.world = new BossWorld();
    }

    /** tfclient:jefe_&lt;jefe&gt;_&lt;objeto&gt; (el mismo id que escribe tools/bosses/build_bosses.py para su modelo). */
    public static String itemId(String boss, String drop) {
        return ("jefe_" + boss + "_" + drop).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(JEFE.get(), BossEntity.attributes().build());
        event.put(ESBIRRO.get(), BossEntity.attributes().build());
        event.put(NPC.get(), BossEntity.attributes().build());
    }

    /** /tf boss (staff, nivel 2): la ventana de jefes, dificultades y grietas. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("boss").requires(s -> s.hasPermission(2)).executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            BossNet.open(p);
            return 1;
        });
    }
}
