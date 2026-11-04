/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimGroup;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.clans.ClanTagFx;
import net.tierrasfantasticas.tfclient.items.TFItemTypes;
import net.tierrasfantasticas.tfclient.items.TFMace;
import net.tierrasfantasticas.tfclient.items.TFThrownTrident;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;
import net.tierrasfantasticas.tfclient.pad.server.PadZoneMusic;
import net.tierrasfantasticas.tfclient.server.TFBotPlayer;

/**
 * (1.3.171) Prueba «v171»: lo que pidió el dueño tras la 1.3.170.
 * <ol>
 *   <li>La etiqueta del clan: ONDA de varios colores (un arcoíris) solo con los efectos arcoíris comprados; sin ellos,
 *   ONDA de un color (su brillo, sin cambiar de tono). Capturas del editor.</li>
 *   <li>Los martillos y mazas de los sets como la maza: velocidad, golpe aplastante (con el daño medido), sin daño de
 *   caída, empujón, Densidad, Brecha, Ráfaga de viento, yunque y piedra de afilar.</li>
 *   <li>La lanza lanzada con su modelo (capturas: en el aire y clavada).</li>
 *   <li>Las protecciones dentro de un grupo: el pad con la elección (unida e independiente), disolver el grupo y quitar
 *   la grande.</li>
 *   <li>El reproductor de la música de una protección: capturas y sus botones (pausa, repetir, volumen, saltar).</li>
 * </ol>
 */
final class BossTestV171 {
    private BossTestV171() {}

    private static final String A = "prueba01";
    private static final String SONG = "https://raw.githubusercontent.com/modelqdeluxe-ops/ss/aef616efe956510fe8fe1785ebdabe41d221ae72/"
            + "tf-client/src/main/resources/assets/tfclient/sounds/clanes/emote.ogg";
    private static Mob golem, bystander, armored;
    private static float before, plainHit;
    private static Vec3 bystanderAt;
    private static TFThrownTrident shown, thrown;
    private static ServerPlayer rival;
    private static Claim big, mine, zone;
    private static BlockPos minePos;

    private static String act(ServerPlayer p, String tab, String action) {
        return PadClans.testAction(p, tab, action, "");
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfclient", id));
        return it == null || it == Items.AIR ? null : it;
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static Mob still(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z) {
        Mob m = type.create(level);
        if (m == null) return null;
        m.moveTo(x, y, z, 0F, 0F);
        m.setNoAi(true);
        m.setPersistenceRequired();
        level.addFreshEntity(m);
        return m;
    }

    /** Un golpe del jugador a target: lo que le quitó de vida. */
    private static float hit(ServerPlayer p, Mob target) {
        target.invulnerableTime = 0;
        target.setHealth(target.getMaxHealth());
        float h0 = target.getHealth();
        p.attack(target);
        return h0 - target.getHealth();
    }

    /** El jugador encima del blanco, cayendo desde fall bloques (sin tocar el suelo). */
    private static void falling(ServerPlayer p, ServerLevel level, Mob target, float fall) {
        p.teleportTo(level, target.getX() + 0.6, target.getY() + target.getBbHeight() + 0.4, target.getZ(), 0F, 60F);
        p.setOnGround(false);
        p.fallDistance = fall;
        p.setDeltaMovement(0, -0.6, 0);
    }

    private static void place(ServerPlayer p, ServerLevel level, BlockPos at, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos below = at.below();
        p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        int ax = (int) arena.x, az = (int) arena.z;
        switch (t) {
            // ---------------------------------------------------------------------------------------------------------
            // 1. La etiqueta: ONDA de varios colores solo con los efectos arcoíris
            // ---------------------------------------------------------------------------------------------------------
            case 1 -> {
                player.teleportTo(level, ax + 0.5, ground(level, ax, az).getY(), az + 0.5, 0F, 0F);
                BossTest.command(server, "time set 6000");
                PadClans.testRanks(player);
                act(player, "estilo", "tagfx:onda|FF0000,00FF00,0000FF|3");
                String multi = PadClans.testFx(A);
                act(player, "estilo", "tagfx:onda|FF8800|3");
                String one = PadClans.testFx(A);
                r.event("v171 1a (sin comprar los efectos arcoíris): guardar ONDA roja-verde-azul deja «" + multi + "»; ONDA naranja deja «" + one + "»"
                        + (!multi.contains(",") && one.startsWith("onda|FF8800") ? " (bien)" : " (MAL)"));
                // la onda de un color: todas las letras, en todos los instantes, con el tono del naranja (solo cambia el brillo)
                ClanTagFx.Fx fx = ClanTagFx.decode(one, 0xFF8800);
                float base = java.awt.Color.RGBtoHSB(0xFF, 0x88, 0x00, null)[0];
                double worst = 0;
                boolean changes = false;
                int first = ClanTagFx.colorAt(fx, 0, 5, 0);
                for (int i = 0; i < 5; i++) {
                    for (double s = 0; s < 4; s += 0.25) {
                        int c = ClanTagFx.colorAt(fx, i, 5, s);
                        float[] hsb = java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
                        if (hsb[1] > 0.15F) worst = Math.max(worst, Math.min(Math.abs(hsb[0] - base), 1 - Math.abs(hsb[0] - base)));
                        changes |= c != first;
                    }
                }
                r.event("v171 1b (ONDA de un color): el tono se aleja como mucho " + f1(worst * 360) + "° del naranja; se anima (brillo) " + changes
                        + (worst < 0.03 && changes ? " (bien: brillo, no arcoíris)" : " (MAL)"));
                // datos de antes: un clan con ONDA de 3 colores guardada y sin comprar → de un color
                JsonObject old = new JsonObject();
                old.addProperty("tagEfecto", "onda");
                JsonArray cols = new JsonArray();
                for (String c : new String[] {"#FF0000", "#00FF00", "#0000FF"}) cols.add(c);
                old.add("tagColores", cols);
                old.addProperty("tagVelocidad", 3);
                int oldNo = ClanTagFx.of(old, 0xFF0000).colors().length;
                old.addProperty("arcoiris", true);
                int oldYes = ClanTagFx.of(old, 0xFF0000).colors().length;
                r.event("v171 1c (ONDA de 3 colores guardada antes): sin comprar se ve con " + oldNo + " color(es); comprada, con " + oldYes
                        + (oldNo == 1 && oldYes == 3 ? " (bien)" : " (MAL)"));
                BossTest.openPad = "clanes/estilo";
            }
            case 30 -> BossTest.actPad = "§color:clanes:estilo:FF8800:PRB:onda|FF8800|3:0";
            case 60 -> BossTest.shot(r, "v171_01_editor_onda_sin_comprar");
            case 62 -> {
                BossTest.closeScreen = true;
                act(player, "tienda", "tienda.comprar:efectos_arcoiris");
                act(player, "tienda", "tienda.comprar:efectos_arcoiris");
                act(player, "estilo", "tagfx:onda|FF0000,00FF00,0000FF|3");
                String fx = PadClans.testFx(A);
                r.event("v171 1d (comprados los efectos arcoíris): ONDA de 3 colores guardada «" + fx + "»" + (fx.startsWith("onda|FF0000,00FF00,0000FF") ? " (bien)" : " (MAL)"));
                BossTest.openPad = "clanes/estilo";
            }
            case 90 -> BossTest.actPad = "§color:clanes:estilo:FF0000:PRB:onda|FF0000,00FF00,0000FF|3:1";
            case 120 -> BossTest.shot(r, "v171_02_editor_onda_comprada");
            // ---------------------------------------------------------------------------------------------------------
            // 2. La maza
            // ---------------------------------------------------------------------------------------------------------
            case 122 -> {
                BossTest.closeScreen = true;
                BlockPos g = ground(level, ax + 12, az);
                player.teleportTo(level, g.getX() - 1.5, g.getY(), g.getZ() + 0.5, -90F, 0F);
                golem = still(level, EntityType.IRON_GOLEM, g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
                bystander = still(level, EntityType.ZOMBIE, g.getX() + 2.5, g.getY(), g.getZ() + 0.5);
                armored = still(level, EntityType.ZOMBIE, g.getX() + 0.5, g.getY(), g.getZ() + 6.5);
                if (armored != null) {
                    armored.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    armored.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                    armored.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                    armored.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
                }
                if (golem != null) golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
                // todos los martillos y mazas de los sets son la maza; los garrotes y guanteletes, no
                int maces = 0, others = 0, wrong = 0;
                for (var e : net.tierrasfantasticas.tfclient.items.TFItems.BY_SET.values()) {
                    for (var o : e) {
                        String id = ForgeRegistries.ITEMS.getKey(o.get()).getPath();
                        boolean mace = o.get() instanceof TFItemTypes.Mace;
                        boolean should = (id.contains("hammer") || id.contains("mace")) && !id.contains("gauntlet");
                        if (mace) maces++;
                        if (mace != should && (id.contains("hammer") || id.contains("mace") || mace)) wrong++;
                        if (!mace && (id.contains("club") || id.contains("gauntlet"))) others++;
                    }
                }
                r.event("v171 2a (los martillos y mazas son la maza): " + maces + " mazas, " + wrong + " que no cuadran; garrotes y guanteletes siguen siendo espada: "
                        + others + (maces > 60 && wrong == 0 ? " (bien)" : " (MAL)"));
                Item hammer = item("valentine_hammer");
                player.setItemInHand(InteractionHand.MAIN_HAND, hammer == null ? ItemStack.EMPTY : new ItemStack(hammer));
                // los encantamientos de la maza
                ItemStack m = new ItemStack(hammer);
                var tm = m.getItem();
                boolean table = tm.canApplyAtEnchantingTable(m, TFMace.DENSITY.get()) && tm.canApplyAtEnchantingTable(m, TFMace.BREACH.get())
                        && !tm.canApplyAtEnchantingTable(m, Enchantments.SHARPNESS) && !tm.canApplyAtEnchantingTable(m, TFMace.WIND_BURST.get());
                boolean anvil = TFMace.anvilAccepts(Enchantments.SMITE) && TFMace.anvilAccepts(Enchantments.BANE_OF_ARTHROPODS) && TFMace.anvilAccepts(Enchantments.FIRE_ASPECT)
                        && TFMace.anvilAccepts(TFMace.WIND_BURST.get()) && !TFMace.anvilAccepts(Enchantments.SHARPNESS) && !TFMace.anvilAccepts(Enchantments.MOB_LOOTING)
                        && !TFMace.anvilAccepts(Enchantments.SWEEPING_EDGE) && !TFMace.anvilAccepts(Enchantments.KNOCKBACK);
                boolean excl = !TFMace.DENSITY.get().isCompatibleWith(TFMace.BREACH.get()) && !TFMace.DENSITY.get().isCompatibleWith(Enchantments.SMITE)
                        && !TFMace.BREACH.get().isCompatibleWith(Enchantments.BANE_OF_ARTHROPODS) && TFMace.WIND_BURST.get().isCompatibleWith(TFMace.DENSITY.get())
                        && TFMace.WIND_BURST.get().isTreasureOnly();
                r.event("v171 2b (encantamientos de la maza): mesa (Densidad y Brecha, no Filo ni Ráfaga) " + table + "; yunque (Castigo, Perdición, Aspecto ígneo, Ráfaga; no Filo, Saqueo, Barrido ni Empuje) "
                        + anvil + "; Densidad/Brecha/Castigo/Perdición se excluyen y Ráfaga va con todos (de tesoro) " + excl + (table && anvil && excl ? " (bien)" : " (MAL)"));
            }
            case 180 -> {
                double speed = player.getAttributeValue(Attributes.ATTACK_SPEED), dmg = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                float normal = golem == null ? -1 : hit(player, golem);
                r.event("v171 2c (golpe normal, en el suelo): velocidad de ataque " + f1(speed) + " (la maza: 0,6), daño del arma " + f1(dmg) + ", le quitó " + f1(normal)
                        + (Math.abs(speed - 0.6) < 0.05 && normal > 0 && Math.abs(normal - dmg) < 0.6 ? " (bien)" : " (MAL)"));
                before = normal;
            }
            case 230 -> {
                if (golem == null) return;
                bystanderAt = bystander == null ? null : bystander.position();
                falling(player, level, golem, 4.0F);
                TFMace.testLast = "";
                float smash = hit(player, golem);
                float fallAfter = player.fallDistance;
                float expect = TFMace.smashBonus(4.0F, 0);
                r.event("v171 2d (golpe aplastante, cayendo 4 bloques): le quitó " + f1(smash) + " (el normal " + f1(before) + "; el aplastante suma " + f1(expect)
                        + ", ×1,5 si es crítico) · " + TFMace.testLast + " · caída tras el golpe " + f1(fallAfter)
                        + (smash >= before + expect - 0.6 && fallAfter == 0F ? " (bien)" : " (MAL)"));
            }
            case 236 -> {
                if (bystander == null) return;
                double moved = bystanderAt == null ? -1 : bystander.position().distanceTo(bystanderAt);
                r.event("v171 2e (el empujón del golpe aplastante): el zombi de al lado se movió " + f1(moved) + " bloques" + (moved > 0.8 ? " (bien)" : " (MAL)"));
            }
            case 290 -> {
                if (golem == null) return;
                ItemStack h = player.getMainHandItem().copy();
                h.enchant(TFMace.DENSITY.get(), 5);
                player.setItemInHand(InteractionHand.MAIN_HAND, h);
            }
            case 340 -> {
                if (golem == null) return;
                falling(player, level, golem, 4.0F);
                TFMace.testLast = "";
                float smash = hit(player, golem);
                r.event("v171 2f (Densidad V, cayendo 4 bloques): le quitó " + f1(smash) + " · " + TFMace.testLast + " (Densidad suma 0,5 × 5 × 4 = 10 más)"
                        + (TFMace.testLast.contains("+" + f1(TFMace.smashBonus(4F, 5))) || TFMace.testLast.contains("+" + f1(TFMace.smashBonus(4F, 5) * 1.5F)) ? " (bien)" : " (MAL)"));
            }
            case 345 -> {
                Item hammer = item("valentine_hammer");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hammer));
                if (armored != null) player.teleportTo(level, armored.getX() - 1.5, armored.getY(), armored.getZ(), -90F, 0F);
            }
            case 395 -> {
                if (armored == null) return;
                float plain = hit(player, armored);
                ItemStack h = player.getMainHandItem().copy();
                h.enchant(TFMace.BREACH.get(), 4);
                player.setItemInHand(InteractionHand.MAIN_HAND, h);
                plainHit = plain;
            }
            case 445 -> {
                if (armored == null) return;
                float breach = hit(player, armored);
                double plain = plainHit;
                r.event("v171 2g (Brecha IV contra armadura de diamante): sin Brecha le quitó " + f1(plain) + ", con Brecha " + f1(breach)
                        + (breach > plain + 1 ? " (bien: la armadura protege menos)" : " (MAL)"));
                ItemStack h = new ItemStack(item("valentine_hammer"));
                h.enchant(TFMace.WIND_BURST.get(), 3);
                player.setItemInHand(InteractionHand.MAIN_HAND, h);
            }
            case 495 -> {
                if (golem == null) return;
                falling(player, level, golem, 3.0F);
                hit(player, golem);
                double vy = player.getDeltaMovement().y;
                r.event("v171 2h (Ráfaga de viento III tras el golpe aplastante): lanzado hacia arriba a " + String.format(Locale.ROOT, "%.2f", vy)
                        + " (24 bloques: 2,22)" + (Math.abs(vy - 2.223) < 0.05 ? " (bien)" : " (MAL)"));
            }
            case 500 -> {
                // el yunque y la piedra de afilar con una maza de una crate y una de un rango
                int[] cost = new int[1];
                Item crate = BossTest.setPiece(false, i -> i instanceof TFItemTypes.Mace), rank = BossTest.setPiece(true, i -> i instanceof TFItemTypes.Mace);
                ItemStack mace = new ItemStack(crate);
                ItemStack withSmite = BossTest.anvil(level, player, mace, BossTest.book(Enchantments.SMITE, 5), cost);
                ItemStack withSharp = BossTest.anvil(level, player, mace, BossTest.book(Enchantments.SHARPNESS, 5), cost);
                ItemStack dens = mace.copy();
                dens.enchant(TFMace.DENSITY.get(), 3);
                ItemStack densBreach = BossTest.anvil(level, player, dens, BossTest.book(TFMace.BREACH.get(), 2), cost);
                ItemStack densWind = BossTest.anvil(level, player, dens, BossTest.book(TFMace.WIND_BURST.get(), 2), cost);
                boolean anvilOk = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SMITE, withSmite) == 5 && withSharp.isEmpty() && densBreach.isEmpty()
                        && EnchantmentHelper.getItemEnchantmentLevel(TFMace.WIND_BURST.get(), densWind) == 2 && EnchantmentHelper.getItemEnchantmentLevel(TFMace.DENSITY.get(), densWind) == 3;
                r.event("v171 2i (yunque con una maza de crate): Castigo V " + !withSmite.isEmpty() + ", Filo V " + (withSharp.isEmpty() ? "no deja" : "DEJA") + ", Densidad+Brecha "
                        + (densBreach.isEmpty() ? "no deja" : "DEJA") + ", Densidad+Ráfaga II " + !densWind.isEmpty() + (anvilOk ? " (bien)" : " (MAL)"));
                ItemStack ground1 = BossTest.grind(level, player, densWind, ItemStack.EMPTY);
                boolean keepsAttr = !ground1.isEmpty() && ground1.getItem() == crate && EnchantmentHelper.getEnchantments(ground1).isEmpty();
                ItemStack rankMace = new ItemStack(rank);
                rankMace.enchant(TFMace.DENSITY.get(), 2);
                ItemStack ground2 = BossTest.grind(level, player, rankMace, ItemStack.EMPTY);
                ItemStack cursed = densWind.copy();
                cursed.enchant(Enchantments.VANISHING_CURSE, 1);
                ItemStack ground3 = BossTest.grind(level, player, cursed, ItemStack.EMPTY);
                boolean curseKept = !ground3.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.VANISHING_CURSE, ground3) == 1
                        && EnchantmentHelper.getEnchantments(ground3).size() == 1;
                r.event("v171 2j (piedra de afilar): maza de crate desencantada (misma maza, sin encantamientos) " + keepsAttr + "; maza de rango "
                        + (ground2.isEmpty() ? "no se desencanta" : "SE DESENCANTA") + "; la maldición se queda " + curseKept + (keepsAttr && ground2.isEmpty() && curseKept ? " (bien)" : " (MAL)"));
                if (golem != null) golem.discard();
                if (bystander != null) bystander.discard();
                if (armored != null) armored.discard();
            }
            // ---------------------------------------------------------------------------------------------------------
            // 3. La lanza lanzada, con su modelo
            // ---------------------------------------------------------------------------------------------------------
            case 502 -> {
                BlockPos g = ground(level, ax - 12, az);
                player.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, -90F, 0F);
                BossTest.hideHud = true;
                Item spear = item("valentine_trident");
                if (spear == null) {
                    r.event("v171 3: no está valentine_trident (MAL)");
                    return;
                }
                // una quieta en el aire, de lado, delante de la cámara
                shown = new TFThrownTrident(level, player, new ItemStack(spear));
                shown.setNoGravity(true);
                shown.setDeltaMovement(Vec3.ZERO);
                shown.moveTo(g.getX() + 3.5, g.getY() + 1.4, g.getZ() + 0.5, 0F, 0F);
                level.addFreshEntity(shown);
                // y otra lanzada de verdad (como al soltar el clic derecho), hacia delante y un poco abajo
                ItemStack s = new ItemStack(spear);
                player.setItemInHand(InteractionHand.MAIN_HAND, s);
                player.setXRot(25F);
                s.getItem().releaseUsing(s, level, player, s.getUseDuration() - 20);
                thrown = level.getEntitiesOfClass(TFThrownTrident.class, player.getBoundingBox().inflate(4), e -> e != shown).stream().findFirst().orElse(null);
                r.event("v171 3a (lanzar la lanza): sale " + (thrown == null ? "NADA (MAL)" : ForgeRegistries.ENTITY_TYPES.getKey(thrown.getType()) + " con "
                        + ForgeRegistries.ITEMS.getKey(thrown.shownItem().getItem()) + (thrown.shownItem().getItem() == spear ? " (bien)" : " (MAL)")));
                player.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, -90F, 8F);
            }
            case 540 -> BossTest.shot(r, "v171_03_lanza_en_el_aire");
            case 542 -> {
                if (thrown != null && thrown.isAlive()) {
                    Vec3 at = thrown.position();
                    player.teleportTo(level, at.x - 3.5, at.y + 0.6, at.z - 0.5, -70F, 20F);
                    r.event("v171 3b (la lanza lanzada): quieta (clavada) " + (thrown.getDeltaMovement().lengthSqr() < 0.01) + " en " + f1(at.x) + ", " + f1(at.y) + ", " + f1(at.z));
                }
            }
            case 580 -> BossTest.shot(r, "v171_04_lanza_clavada");
            // ---------------------------------------------------------------------------------------------------------
            // 4. Protecciones dentro de un grupo
            // ---------------------------------------------------------------------------------------------------------
            case 582 -> {
                BossTest.hideHud = false;
                if (shown != null) shown.discard();
                if (thrown != null) thrown.discard();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                BlockPos c = ground(level, ax, az + 140);
                rival = TFBotPlayer.spawnTarget(server, level, "Rival_Prote", c.getX() + 0.5, c.getY(), c.getZ() + 2.5);
                if (rival == null) {
                    r.event("v171 4: no salió el jugador de prueba (MAL)");
                    return;
                }
                ClaimManager cm = ClaimManager.getInstance();
                big = cm.createClaim(level, c, rival, ClaimTier.byId("claimstone_100x100"));
                ClaimGroup g = cm.createGroup(big, "Reino de Rival");
                cm.registerPlayer(g.getGroupId(), player.getUUID());
                minePos = ground(level, c.getX() + 30, c.getZ() + 30);
                player.teleportTo(level, minePos.getX() - 1.5, minePos.getY(), minePos.getZ() + 0.5, -90F, 0F);
                place(player, level, minePos, ClaimBlocks.createTierItem(ClaimTier.byId("claimstone_25x25"), 1));
                mine = cm.getClaimByCenter(level, minePos);
                r.event("v171 4a (la piedra del invitado dentro de la grande): " + (mine == null ? "NO SE PUSO (MAL)" : "unida " + (mine.getGroupId() != null)
                        + (mine.getGroupId() != null ? " (bien)" : " (MAL)")) + "; el pad se abre en ella con UNIDA / INDEPENDIENTE");
            }
            case 620 -> BossTest.shot(r, "v171_05_protes_piedra_unida");
            case 622 -> {
                if (mine != null) BossTest.actPad = "modo:" + mine.getClaimId() + ":indep";
            }
            case 660 -> {
                BossTest.shot(r, "v171_06_protes_independiente");
                if (mine == null || rival == null) return;
                ClaimManager cm = ClaimManager.getInstance();
                Claim at = cm.getClaimAt(level, minePos.east(3));
                boolean rivalBuilds = at != null && at.canModify(rival);
                r.event("v171 4b (independiente, pulsado en el pad): independiente " + mine.isNestedIndependent() + "; en su cuadrado manda ella " + (at == mine)
                        + "; el dueño de la grande construye ahí " + rivalBuilds + (mine.isNestedIndependent() && at == mine && !rivalBuilds ? " (bien)" : " (MAL)"));
            }
            case 662 -> {
                BossTest.closeScreen = true;
                if (mine == null || big == null) return;
                ClaimManager cm = ClaimManager.getInstance();
                // disolver el grupo con la grande puesta: la independiente vuelve a su dueño
                cm.dissolveGroupBreaking(big.getGroupId());
                boolean gone = cm.getClaimByCenter(level, minePos) == null;
                // otra vez: grupo, piedra independiente, y quitar la grande: la pequeña se queda como zona normal
                ClaimGroup g2 = cm.createGroup(big, "Reino de Rival");
                cm.registerPlayer(g2.getGroupId(), player.getUUID());
                place(player, level, minePos, ClaimBlocks.createTierItem(ClaimTier.byId("claimstone_25x25"), 1));
                Claim again = cm.getClaimByCenter(level, minePos);
                if (again != null) cm.makeIndependent(again);
                cm.removeClaim(level, big.getCenter());
                Claim after = cm.getClaimByCenter(level, minePos);
                boolean stays = after != null && after.getInsideGroup() == null && after.getGroupId() == null;
                r.event("v171 4c (disolver el grupo con la grande puesta): la independiente vuelve a su dueño " + gone + "; (quitar la grande) se queda como zona normal "
                        + stays + (gone && stays ? " (bien)" : " (MAL)"));
                if (after != null) cm.removeClaim(level, after.getCenter());
                BossTest.closeScreen = true;
            }
            // ---------------------------------------------------------------------------------------------------------
            // 5. El reproductor de la música de una protección
            // ---------------------------------------------------------------------------------------------------------
            case 670 -> {
                BlockPos c = ground(level, ax + 60, az - 60);
                player.teleportTo(level, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0F, 0F);
                zone = ClaimManager.getInstance().createClaim(level, c, player, ClaimTier.byId("claimstone_64x64"));
                if (zone == null) {
                    r.event("v171 5: no se pudo crear la zona (MAL)");
                    return;
                }
                PadZoneMusic.testSet(player, zone, SONG, "Canción de prueba", 4650);
                BossTest.openPad = "protecciones/z:" + zone.getClaimId() + ":a";
            }
            case 700 -> {
                if (zone != null) BossTest.actPad = "§zonaplayer:" + zone.getClaimId() + ":Zona de prueba";
            }
            case 740 -> BossTest.shot(r, "v171_07_reproductor");
            case 742 -> BossTest.actPad = "pausa";
            case 760 -> {
                PadZoneMusic.Song s = zone == null ? null : PadZoneMusic.of(zone);
                r.event("v171 5a (reproductor: PAUSA): en pausa " + (s != null && s.paused()) + " en " + (s == null ? "-" : s.position(System.currentTimeMillis())) + " ms"
                        + (s != null && s.paused() ? " (bien)" : " (MAL)"));
                BossTest.actPad = "repetir";
            }
            case 770 -> BossTest.actPad = "vol:35";
            case 780 -> BossTest.actPad = "ir:2000";
            case 800 -> {
                PadZoneMusic.Song s = zone == null ? null : PadZoneMusic.of(zone);
                boolean ok = s != null && !s.repeat() && s.volume() == 35 && s.paused() && s.position(System.currentTimeMillis()) == 2000;
                r.event("v171 5b (reproductor: REPETIR, volumen 35 y saltar a 0:02): repetir " + (s != null && s.repeat()) + ", volumen " + (s == null ? -1 : s.volume())
                        + ", en " + (s == null ? -1 : s.position(System.currentTimeMillis())) + " ms" + (ok ? " (bien)" : " (MAL)"));
                BossTest.actPad = "pausa";
            }
            case 840 -> {
                BossTest.shot(r, "v171_08_reproductor_una_vez");
                PadZoneMusic.Song s = zone == null ? null : PadZoneMusic.of(zone);
                r.event("v171 5c (sigue sonando una vez desde 0:02): suena " + (s != null && s.playing(System.currentTimeMillis())) + (s != null && !s.paused() ? " (bien)" : " (MAL)"));
            }
            case 900 -> {
                PadZoneMusic.Song s = zone == null ? null : PadZoneMusic.of(zone);
                r.event("v171 5d (una vez: 4,6 s de canción, pasados 3 s desde 0:02): terminó " + (s != null && s.ended(System.currentTimeMillis()))
                        + (s != null && s.ended(System.currentTimeMillis()) ? " (bien)" : " (MAL)"));
            }
            case 940 -> BossTest.shot(r, "v171_09_reproductor_termino");
            case 942 -> {
                BossTest.closeScreen = true;
                if (zone != null) {
                    PadZoneMusic.testClear(zone);
                    ClaimManager.getInstance().removeClaim(level, zone.getCenter());
                }
            }
            case 960 -> BossTest.next(level);
            default -> {
            }
        }
    }
}
