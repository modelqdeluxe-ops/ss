/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

/** Para la prueba en el juego (BossTest): lo que se mira del cliente del Void. */
public final class VoidTestHooks {
    private VoidTestHooks() {}

    /** ¿Se cargó el shader del cielo? (solo en el cliente del juego de la prueba) */
    public static boolean skyShaderLoaded() {
        return VoidSky.shader != null;
    }

    /** La prueba: la isla de este mundo pasa a ser «la de antes» (ver {@link IslandUpdate#pretendOld}). */
    public static void pretendOldIsland(net.minecraft.server.level.ServerLevel level) {
        IslandUpdate.pretendOld(level);
    }

    public static boolean islandUpdated() {
        return IslandUpdate.finished();
    }

    public static String islandUpdate() {
        return IslandUpdate.status();
    }

    /** ¿El bloque de ese sitio es el de la isla? */
    public static boolean islandMatches(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos p) {
        VoidIsland island = VoidIsland.get();
        return island != null && level.getBlockState(p) == island.stateAt(p.getX(), p.getY(), p.getZ());
    }

    /** El último viaje por el agujero de gusano: fotogramas por segundo y cuándo llegó. */
    public static String travel() {
        return TravelFx.lastTrip();
    }

    /** La grieta del cielo (1.3.93): su estado en el servidor y lo que ve el cliente. */
    public static String sky() {
        return SkyRift.describe() + "; el cliente: " + SkyRiftClient.describe();
    }

    /** (1.3.96) La corrosión de ese jugador (en el servidor y lo que enseña el cliente). */
    public static String light(net.minecraft.server.level.ServerPlayer p) {
        return String.format("luz %.0f (%s), ecos oídos %d, piezas del Vacío %d, golpes de corrosión %d", VoidLight.testLight(p),
                VoidLight.testWhere(p), VoidLight.heard(p), VoidArmor.pieces(p), VoidLight.hurts) + "; el cliente: " + VoidLightClient.describe();
    }

    public static void setLight(net.minecraft.server.level.ServerPlayer p, float light) {
        VoidLight.testSet(p, light);
    }

    /** Los Pozos de Luz del mundo. */
    public static String shards(net.minecraft.server.level.ServerLevel level) {
        return LightWells.describe(level);
    }

    /** El Velo del Alba, el Corazón, los pozos y el portal (el cliente). */
    public static String sanctuary() {
        return (SanctuaryFx.veil == null || SanctuaryFx.heart == null ? "SIN SHADERS del santuario" : "shaders del santuario cargados")
                + ", dibujado en " + SanctuaryFx.frames + " fotogramas";
    }

    /** (1.3.96) La armadura del Vacío con esos jefes derrotados: lo que da al pedirla. */
    public static String armor(net.minecraft.server.level.ServerPlayer p, int kills) {
        VoidArmor.testSetKills(p, kills);
        int n = VoidArmor.claim(p);
        return n < 0 ? "aún no (" + VoidArmor.kills(p) + " de " + VoidArmor.NEED + ")" : "da " + n + " objetos";
    }

    /** (1.3.97) Los caballeros sacros del santuario: quiénes están y a cuánto de su sitio (pasean). */
    public static String sacred(net.minecraft.server.level.ServerLevel level) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (SacredKnights.Def d : SacredKnights.ALL) {
            net.minecraft.core.BlockPos home = SacredKnights.home(d);
            var list = level.getEntitiesOfClass(SacredKnight.class, new net.minecraft.world.phys.AABB(home).inflate(40), e -> e.variant() == d.id());
            if (b.length() > 0) b.append(", ");
            if (list.isEmpty()) {
                b.append(d.name()).append(" NO ESTÁ");
            } else {
                n++;
                b.append(String.format("%s a %.1f de su sitio", d.name(), Math.sqrt(list.get(0).blockPosition().distSqr(home))));
            }
        }
        return n + " de " + SacredKnights.ALL.size() + " caballeros sacros: " + b;
    }

    /** (1.3.98) La prueba corta: al Void como el staff (/tf void ir), con su viaje. */
    public static void sendToVoid(net.minecraft.server.level.ServerPlayer p) {
        VoidTravel.adminSend(p.createCommandSourceStack().withSuppressedOutput(), java.util.List.of(p), true);
    }

    /** (1.3.98) El caballero sacro n.º id (o null). */
    public static net.minecraft.world.entity.Entity sacredKnight(net.minecraft.server.level.ServerLevel level, int id) {
        SacredKnights.Def d = SacredKnights.def(id);
        if (d == null) return null;
        var list = level.getEntitiesOfClass(SacredKnight.class, new net.minecraft.world.phys.AABB(SacredKnights.home(d)).inflate(40),
                e -> e.variant() == id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** (1.3.98) Cada caballero sacro: si pisa el suelo y sobre qué (el dueño: «están rotos porque vuelan»). */
    public static String sacredGround(net.minecraft.server.level.ServerLevel level) {
        StringBuilder b = new StringBuilder();
        for (SacredKnights.Def d : SacredKnights.ALL) {
            net.minecraft.world.entity.Entity e = sacredKnight(level, d.id());
            if (b.length() > 0) b.append("; ");
            if (e == null) {
                b.append(d.name()).append(" NO ESTÁ");
                continue;
            }
            net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(e.getX(), e.getY() - 0.05, e.getZ());
            b.append(String.format("%s en el suelo %s (sobre %s, a %.2f de él), lleva %s", d.name(), e.onGround() ? "sí" : "NO",
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(below).getBlock()).getPath(),
                    e.getY() - (below.getY() + 1), d.item()));
        }
        return b.toString();
    }

    /** (1.3.98) El soporte de la armadura del altar (o null). */
    public static net.minecraft.world.entity.Entity altarStand(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.core.BlockPos a = VoidIsland.altarPos();
        if (a == null) return null;
        var list = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, new net.minecraft.world.phys.AABB(a).inflate(3),
                e -> e.getTags().contains(VoidArmor.TAG));
        return list.isEmpty() ? null : list.get(0);
    }

    /** (1.3.114, el dueño: «la armadura del Void está volando 1 bloque del suelo») qué tiene el soporte debajo y a cuánto. */
    public static String standGround(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.world.entity.Entity s = altarStand(level);
        if (s == null) return "sin soporte";
        StringBuilder b = new StringBuilder();
        for (int dy = 0; dy <= 3; dy++) {
            net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(s.getX(), s.getY() - 0.05 - dy, s.getZ());
            var st = level.getBlockState(p);
            if (!st.getCollisionShape(level, p).isEmpty()) {
                double top = p.getY() + st.getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y);
                return String.format("sobre %s, a %.2f de él", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath(), s.getY() - top);
            }
            b.append(dy == 0 ? "" : ",").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());
        }
        return "en el aire (debajo: " + b + ")";
    }

    /** (1.3.97) Pedirla otra vez: no da nada (una sola vez por jugador). */
    public static String armorAgain(net.minecraft.server.level.ServerPlayer p) {
        int n = VoidArmor.claim(p);
        return n == -2 ? "no da nada (bien: una sola vez)" : "da " + n + " (MAL)";
    }

    /** La armadura y la dureza que tiene puestas (la de netherita, 20 y 12; los sets de las crates, +1 por pieza). */
    public static String armorValue(net.minecraft.server.level.ServerPlayer p) {
        return String.format("armadura %.1f, dureza %.1f", p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR),
                p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS));
    }

    /** Le pone (o le quita) el yelmo, el peto, las grebas y las botas del Vacío. */
    public static void wearArmor(net.minecraft.server.level.ServerPlayer p, boolean on) {
        String[] ids = {"void_helmet", "void_armor_chestplate", "void_armor_leggings", "void_armor_boots"};
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            net.minecraft.world.item.Item it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation(net.tierrasfantasticas.tfclient.TFClient.MOD_ID, ids[i]));
            p.setItemSlot(slots[i], on && it != null ? new net.minecraft.world.item.ItemStack(it) : net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    /** El soporte del Altar del Vacío: si está y lo que lleva puesto. */
    public static String altar(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.core.BlockPos a = VoidIsland.altarPos();
        if (a == null) return "LA ISLA NO TIENE ALTAR";
        var stands = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, new net.minecraft.world.phys.AABB(a).inflate(2),
                e -> e.getTags().contains(VoidArmor.TAG));
        if (stands.isEmpty()) return "SIN SOPORTE";
        StringBuilder b = new StringBuilder("soporte con");
        for (net.minecraft.world.item.ItemStack s : stands.get(0).getArmorSlots()) b.append(' ').append(s.isEmpty() ? "-" : s.getItem().toString());
        return b.toString();
    }

    /** El Portal del Alba: viajes hechos. */
    public static int portalTrips() {
        return VoidPortal.trips;
    }

    public static String portal() {
        return VoidPortal.trips + " viajes al santuario";
    }

    /** Un sitio del Páramo para ver su fauna (de donde parte la zona por donde anda). */
    public static net.minecraft.world.phys.Vec3 faunaSpot() {
        VoidIsland i = VoidIsland.get();
        return i == null || i.faunaSeed == null ? new net.minecraft.world.phys.Vec3(-64.5, 75, -320.5)
                : new net.minecraft.world.phys.Vec3(i.faunaSeed.getX() + 0.5, i.faunaSeed.getY(), i.faunaSeed.getZ() + 0.5);
    }

    /** La fauna del Páramo: sus reglas y cuántas hay. */
    public static String fauna(net.minecraft.server.level.ServerLevel level) {
        return VoidFauna.describe(level);
    }

    /** Shaders (Oculus): si hay paquete puesto y qué se hizo con el Void. */
    public static String shaders() {
        return (ShaderPacks.inUse() ? "paquete de shaders puesto" : "sin paquete de shaders") + " (" + ShaderPacks.status + ")"
                + (AfterShaders.frames > 0 ? "; grieta y cielo después del paquete en " + AfterShaders.frames + " fotogramas" : "");
    }
}
