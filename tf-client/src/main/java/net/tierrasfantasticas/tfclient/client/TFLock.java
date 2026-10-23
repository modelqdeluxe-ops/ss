/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;
import net.tierrasfantasticas.tfclient.bosses.BossTest;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;

/**
 * TF Client, solo para Tierras Fantásticas (1.3.117, el dueño: «este TF Client debe ser únicamente exclusivo de nuestro
 * server, nadie puede usar este mod en otro server; la IP súper vinculada para que lo redirija siempre a nuestro server
 * sin importar qué haga; una contraseña única para acceder a los mundos locales»).
 * <ul>
 *   <li><b>Servidores:</b> toda conexión (el botón del menú, la lista de servidores, la conexión directa, un servidor LAN
 *   o un enlace de juego rápido) pasa por {@code ConnectScreen.startConnecting} ({@code ConnectScreenMixin}): si no es
 *   el servidor oficial ({@link #OFFICIAL}), se conecta al oficial. La dirección de cliente.properties ya no puede ser
 *   otra ({@link TFConfig}).</li>
 *   <li><b>Mundos locales</b> (un jugador y abrirlo en LAN): solo con la contraseña. Al abrir la lista de mundos o crear
 *   uno se pide ({@link TFLocalLockScreen}); y si se entra a un mundo local por otro camino, se sale. Aquí solo está su
 *   huella (PBKDF2 con sal y el SHA-256 de eso): la contraseña no está en el código ni en el repositorio. Bien puesta,
 *   se guarda en config/tfclient/mundos-locales.key (la clave derivada, no la contraseña) y no se vuelve a pedir en
 *   ese ordenador.</li>
 * </ul>
 * La prueba en el juego (GitHub Actions, {@link BossTest#ON}) usa su mundo local sin contraseña.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFLock {
    /**
     * Los servidores oficiales (host:puerto). Ahora el de pruebas; cuando el dueño pase la IP final, se añade aquí (y en
     * TFConfig.DEFAULT_ADDRESS) y se publica una versión.
     */
    public static final List<String> OFFICIAL = List.of("216.163.187.40:19229");

    private static final byte[] SALT = HexFormat.of().parseHex("08dc3e7dfe669487930e5ab7eddff2ed");
    private static final int ITERATIONS = 200_000;
    private static final String CHECK = "bede0c2669ab0349169220a01de7ff91e6894522fb4f1f9043d7386aa75c4813";
    private static final String KEY_FILE = "mundos-locales.key";

    private static Boolean unlocked;

    private TFLock() {}

    // ------------------------------------------------------------------------------------------------- servidores

    /** ¿Es uno de los servidores oficiales? (sin mayúsculas; sin puerto, el 25565) */
    public static boolean official(ServerAddress address) {
        String a = address.getHost().toLowerCase(Locale.ROOT) + ":" + address.getPort();
        for (String o : OFFICIAL) {
            ServerAddress p = ServerAddress.parseString(o);
            if (a.equals(p.getHost().toLowerCase(Locale.ROOT) + ":" + p.getPort())) return true;
        }
        return false;
    }

    public static boolean official(String address) {
        return address != null && ServerAddress.isValidAddress(address) && official(ServerAddress.parseString(address));
    }

    /** Lo que se conecta en lugar de otro servidor: el oficial, con su paquete de recursos aceptado. */
    public static void redirect(Screen parent, Minecraft mc, ServerAddress wanted, boolean quickPlay) {
        String address = OFFICIAL.get(0);
        TFClient.LOGGER.info("TF Client: solo se juega en {}: {} → {}", TFConfig.serverName(), wanted.getHost() + ":" + wanted.getPort(), address);
        ServerData data = new ServerData(TFConfig.serverName(), address, false);
        data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(address), data, quickPlay);
    }

    // ------------------------------------------------------------------------------------------------ mundos locales

    /** ¿Puede jugar mundos locales? (la contraseña ya se puso en este ordenador, o es la prueba en el juego) */
    public static boolean unlocked() {
        if (BossTest.ON) return true;
        if (unlocked == null) {
            unlocked = false;
            try {
                Path f = TFConfigDir.dir().resolve(KEY_FILE);
                if (Files.exists(f)) unlocked = matches(Files.readString(f, StandardCharsets.UTF_8).trim());
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Client: no se pudo leer {}: {}", KEY_FILE, e.getMessage());
            }
        }
        return unlocked;
    }

    /** Comprueba la contraseña; si es la buena, la recuerda en este ordenador. */
    public static boolean tryUnlock(String password) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.trim().toCharArray(), SALT, ITERATIONS, 256);
            String key = HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
            if (!matches(key)) return false;
            unlocked = true;
            Files.writeString(TFConfigDir.dir().resolve(KEY_FILE), key, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudo comprobar la contraseña de los mundos locales: {}", e.getMessage());
            return false;
        }
    }

    private static boolean matches(String keyHex) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(HexFormat.of().parseHex(keyHex));
        return MessageDigest.isEqual(h, HexFormat.of().parseHex(CHECK));
    }

    /** La lista de mundos o crear uno: antes, la contraseña. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if ((next instanceof SelectWorldScreen || next instanceof CreateWorldScreen) && !unlocked()) {
            Screen back = event.getCurrentScreen() instanceof TFLocalLockScreen ? new TitleScreen() : event.getCurrentScreen();
            event.setNewScreen(new TFLocalLockScreen(back == null ? new TitleScreen() : back, next));
        }
    }

    /** Si entra a un mundo local por otro camino (un enlace de juego rápido…), sale y se le pide la contraseña. */
    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasSingleplayerServer() || unlocked()) return;
        mc.execute(() -> {
            if (mc.level == null) return;
            mc.level.disconnect();
            mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
            mc.setScreen(new TFLocalLockScreen(new TitleScreen(), null));
        });
    }
}
