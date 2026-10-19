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

    /** (1.3.96) La luz del Alba de ese jugador (en el servidor y lo que enseña el cliente). */
    public static String light(net.minecraft.server.level.ServerPlayer p) {
        return String.format("luz %.0f%s, ecos %d/%d, golpes de corrosión %d, oscuridad %s", VoidLight.testLight(p),
                VoidLight.testInside(p) ? " (dentro del Velo)" : " (fuera del Velo)", VoidLight.heard(p), VoidLight.ECHOES.length,
                VoidLight.hurts, p.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS) ? "sí" : "no")
                + "; el cliente: " + VoidLightClient.describe();
    }

    public static void setLight(net.minecraft.server.level.ServerPlayer p, float light) {
        VoidLight.testSet(p, light);
    }

    /** Los fragmentos de luz del mundo. */
    public static String shards(net.minecraft.server.level.ServerLevel level) {
        return LightShards.describe(level);
    }

    /** El Velo del Alba y el Corazón (el cliente). */
    public static String sanctuary() {
        return (SanctuaryFx.veil == null || SanctuaryFx.heart == null ? "SIN SHADERS del santuario" : "shaders del santuario cargados")
                + ", dibujado en " + SanctuaryFx.frames + " fotogramas";
    }

    /** Shaders (Oculus): si hay paquete puesto y qué se hizo con el Void. */
    public static String shaders() {
        return (ShaderPacks.inUse() ? "paquete de shaders puesto" : "sin paquete de shaders") + " (" + ShaderPacks.status + ")"
                + (AfterShaders.frames > 0 ? "; grieta y cielo después del paquete en " + AfterShaders.frames + " fotogramas" : "");
    }
}
