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
