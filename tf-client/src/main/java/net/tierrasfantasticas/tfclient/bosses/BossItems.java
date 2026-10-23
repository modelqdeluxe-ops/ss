/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;

/**
 * Los objetos que sueltan los jefes (dropitem del pack: el cofre del Esqueleto de la Torre deja su guja, su égida y sus
 * cuernos), con las skills que traen en el pack: el arma lanza las suyas al golpear (~onSwing), el escudo mientras te
 * cubres (~onTimer) y lo de la cabeza se lleva puesto.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class BossItems {
    private BossItems() {}

    static Item create(SkillDefs.ClassDef cls, SkillDefs.Drop d) {
        Item.Properties props = new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
        String mat = d.material().toLowerCase(Locale.ROOT);
        if (mat.equals("shield")) return new Shield(props.durability(1008), cls, d);
        if (d.type().equalsIgnoreCase("HAT")) return new Hat(props, cls, d);
        return new Weapon(props, cls, d);
    }

    /** El objeto de jefe (y su definición) de ese stack, o null. */
    static Owned owned(ItemStack s) {
        return s.getItem() instanceof Owned o ? o : null;
    }

    interface Owned {
        SkillDefs.ClassDef cls();

        SkillDefs.Drop drop();

        default void lore(List<Component> tip) {
            SkillDefs.BossInfo bi = cls().bossInfo;
            if (bi != null) {
                tip.add(Component.literal("De " + bi.name).withStyle(s -> s.withColor(TextColor.fromRgb(bi.color))));
            }
        }
    }

    static final class Weapon extends Item implements Owned {
        private final SkillDefs.ClassDef cls;
        private final SkillDefs.Drop drop;

        Weapon(Properties p, SkillDefs.ClassDef cls, SkillDefs.Drop drop) {
            super(p);
            this.cls = cls;
            this.drop = drop;
        }

        @Override
        public SkillDefs.ClassDef cls() {
            return cls;
        }

        @Override
        public SkillDefs.Drop drop() {
            return drop;
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.literal(drop.name());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
            lore(tip);
            tip.add(Component.literal("Al golpear: daño de alma y un tajo que avanza").withStyle(ChatFormatting.GRAY));
        }
    }

    static final class Shield extends ShieldItem implements Owned {
        private final SkillDefs.ClassDef cls;
        private final SkillDefs.Drop drop;

        Shield(Properties p, SkillDefs.ClassDef cls, SkillDefs.Drop drop) {
            super(p);
            this.cls = cls;
            this.drop = drop;
        }

        @Override
        public SkillDefs.ClassDef cls() {
            return cls;
        }

        @Override
        public SkillDefs.Drop drop() {
            return drop;
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.literal(drop.name());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
            lore(tip);
            tip.add(Component.literal("Al cubrirte: escupe fuego hacia delante").withStyle(ChatFormatting.GRAY));
        }
    }

    static final class Hat extends Item implements Owned, Equipable {
        private final SkillDefs.ClassDef cls;
        private final SkillDefs.Drop drop;

        Hat(Properties p, SkillDefs.ClassDef cls, SkillDefs.Drop drop) {
            super(p);
            this.cls = cls;
            this.drop = drop;
        }

        @Override
        public SkillDefs.ClassDef cls() {
            return cls;
        }

        @Override
        public SkillDefs.Drop drop() {
            return drop;
        }

        @Override
        public EquipmentSlot getEquipmentSlot() {
            return EquipmentSlot.HEAD;
        }

        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                                         net.minecraft.world.InteractionHand hand) {
            return swapWithEquipmentSlot(this, level, player, hand);
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.literal(drop.name());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
            lore(tip);
            tip.add(Component.literal("Se lleva en la cabeza").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Ha golpeado (clic izquierdo, también al aire) con un arma de jefe: sus skills ~onSwing (con el golpe cargado). */
    static void swing(ServerPlayer p) {
        Owned o = owned(p.getMainHandItem());
        if (!(o instanceof Weapon) || p.getAttackStrengthScale(0.5F) < 0.9F) return;
        for (net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech m : o.drop().mechs()) {
            if ("onswing".equals(m.tr)) SkillMobs.playerMech(p, o.cls(), m, 4.5);
        }
    }

    /** Mientras se cubre con un escudo de jefe: sus skills ~onTimer. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !p.isBlocking()) return;
        Owned o = owned(p.getUseItem());
        if (!(o instanceof Shield)) return;
        for (net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech m : o.drop().mechs()) {
            if (!"ontimer".equals(m.tr)) continue;
            int every = Math.max(1, (int) parse(m.trv, 20));
            if (p.tickCount % every == 0) SkillMobs.playerMech(p, o.cls(), m, 16);
        }
    }

    private static double parse(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return def;
        }
    }
}
