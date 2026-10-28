/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.bosses.BossTest;
import net.tierrasfantasticas.tfclient.util.TFChat;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.149, el dueño: «añade contraseña al comando tf… cuando la ponga me desbloquea el comando solo a mí o al que tenga
 * la pass») El candado de /tf: además de ser staff, hay que poner la contraseña. Al escribir /tf (o /tf lo-que-sea) sin
 * haberla puesto, el juego abre una ventana que la pide (se escribe con puntos y viaja por un paquete propio, así que no
 * sale en el chat ni en los logs); bien puesta, /tf queda abierto para ese jugador hasta que sale del servidor y se
 * ejecuta lo que había escrito. Cada cosa de /tf lleva {@link #allowed} (en {@code TFCommands}), así que tampoco se cuela
 * con /execute, bloques de comandos ni funciones; la consola (y la web por el puente) siempre puede.
 * <p>
 * La contraseña no está en el código: solo su huella (PBKDF2 con sal y el SHA-256 de eso, como {@code TFLock}). Se
 * cambia desde la consola con {@code tf clave cambiar <nueva>} (se guarda la huella en config/tfclient/clave-tf.json) y
 * se apaga con {@code "activa": false} en ese archivo. 5 fallos seguidos: 15 minutos sin poder probar.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFStaffLock {
    private static final String SALT = "190af52d61b8437e9379dbe3aa0446a6";
    private static final String CHECK = "ae1d779451e69ed854d7897af3d71d937e032bd6293cee608d88647f9274be81";
    private static final int ITERATIONS = 200_000;
    private static final int MAX_FAILS = 5;
    private static final long BLOCK_MS = 15 * 60_000L;
    /** /tf (con o sin namespace), sola o tras un «run» de /execute. */
    private static final Pattern TF = Pattern.compile("(?:^|\\brun\\s+)/?(?:[a-z0-9_]+:)?tf(?:\\s|$)");

    private static final Set<UUID> UNLOCKED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ASKED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> PENDING = new ConcurrentHashMap<>();
    /** {fallos seguidos, bloqueado hasta (ms)} */
    private static final Map<UUID, long[]> FAILS = new ConcurrentHashMap<>();

    private static volatile boolean active = true;
    private static volatile String salt = SALT, check = CHECK;
    /** Las pruebas en el juego ({@link BossTest}) lo encienden solo cuando prueban el candado. */
    public static volatile boolean testOn;

    private TFStaffLock() {}

    // ------------------------------------------------------------------------------------------------- config

    private static Path file() {
        return TFConfigDir.dir().resolve("clave-tf.json");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        UNLOCKED.clear();
        ASKED.clear();
        PENDING.clear();
        FAILS.clear();
        JsonObject json = TFJson.read(file());
        if (json == null) {
            save(true, SALT, CHECK);
            return;
        }
        active = TFJson.bool(json, "activa", true);
        String s = TFJson.str(json, "sal", ""), c = TFJson.str(json, "huella", "");
        boolean ok = s.matches("[0-9a-f]{32}") && c.matches("[0-9a-f]{64}");
        salt = ok ? s : SALT;
        check = ok ? c : CHECK;
        if (!active) TFClient.LOGGER.warn("TF Clave: /tf SIN contraseña (\"activa\": false en {})", file().getFileName());
    }

    private static void save(boolean on, String s, String c) {
        active = on;
        salt = s;
        check = c;
        JsonObject json = new JsonObject();
        json.addProperty("_", "La contraseña de /tf. Se cambia desde la consola: tf clave cambiar <nueva>. \"activa\": false la quita.");
        json.addProperty("activa", on);
        json.addProperty("sal", s);
        json.addProperty("huella", c);
        TFJson.write(file(), json);
    }

    private static boolean active() {
        return BossTest.ON ? testOn : active;
    }

    private static String key(String password, String saltHex) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.trim().toCharArray(), HexFormat.of().parseHex(saltHex), ITERATIONS, 256);
        return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
    }

    private static String hash(String keyHex) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(HexFormat.of().parseHex(keyHex)));
    }

    static boolean matches(String password) {
        try {
            return MessageDigest.isEqual(hash(key(password, salt)).getBytes(StandardCharsets.US_ASCII), check.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Clave: no se pudo comprobar la contraseña", e);
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------- el candado

    /** ¿Puede usar /tf? La consola sí; un jugador, si puso la contraseña; nada más (bloques de comandos, /execute as mob…). */
    public static boolean allowed(CommandSourceStack source) {
        if (!active()) return true;
        Entity e = source.getEntity();
        if (e == null) return source.hasPermission(4);
        return e instanceof ServerPlayer p && UNLOCKED.contains(p.getUUID());
    }

    /** Pone el candado a una rama de /tf (sin quitarle el permiso que ya pedía). */
    public static <T extends ArgumentBuilder<CommandSourceStack, T>> T guard(T node) {
        return node.requires(node.getRequirement().and(TFStaffLock::allowed));
    }

    public static boolean unlocked(UUID uuid) {
        return UNLOCKED.contains(uuid);
    }

    /** Un staff escribe /tf … sin la contraseña: no se ejecuta, se le pide (y luego se ejecuta). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!active()) return;
        String input = event.getParseResults().getReader().getString();
        if (!TF.matcher(input.trim()).find()) return;
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        Entity e = source.getEntity();
        if (e instanceof ServerPlayer p) {
            if (UNLOCKED.contains(p.getUUID()) || !p.hasPermissions(2)) return; // a los demás, el error de siempre
            event.setCanceled(true);
            ask(p, input.trim());
        } else if (e != null || !source.hasPermission(4)) {
            event.setCanceled(true);
        }
    }

    /** /tf sola. */
    public static int bare(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (source.getEntity() instanceof ServerPlayer p && active() && !UNLOCKED.contains(p.getUUID())) {
            if (!p.hasPermissions(2)) {
                source.sendFailure(Component.literal("Comando solo para el staff."));
                return 0;
            }
            ask(p, "");
            return 1;
        }
        source.sendSuccess(() -> Component.literal("/tf abierto. Escribe /tf y pulsa Tab para ver los comandos.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static void ask(ServerPlayer p, String command) {
        long[] f = FAILS.get(p.getUUID());
        long left = f == null ? 0 : f[1] - System.currentTimeMillis();
        if (left > 0) {
            TFChat.send(p, "Staff", ChatFormatting.RED, "Demasiados intentos. Vuelve a probar en ", TFChat.hl((left / 60_000 + 1) + " min"), ".");
            return;
        }
        ASKED.add(p.getUUID());
        PENDING.put(p.getUUID(), command.startsWith("/") ? command.substring(1) : command);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Ask());
    }

    private static void answer(ServerPlayer p, String password) {
        UUID id = p.getUUID();
        if (!ASKED.remove(id)) return; // solo se contesta a lo que se pidió (nada de probar a ciegas)
        String command = PENDING.remove(id);
        if (password == null || password.isBlank()) return;
        long now = System.currentTimeMillis();
        long[] f = FAILS.computeIfAbsent(id, k -> new long[2]);
        if (f[1] > now) return;
        if (!matches(password)) {
            f[0]++;
            TFClient.LOGGER.warn("TF Clave: {} falló la contraseña de /tf ({} de {})", p.getGameProfile().getName(), f[0], MAX_FAILS);
            if (f[0] >= MAX_FAILS) {
                f[0] = 0;
                f[1] = now + BLOCK_MS;
                TFChat.send(p, "Staff", ChatFormatting.RED, "Contraseña incorrecta. Demasiados intentos: espera ", TFChat.hl("15 min"), ".");
            } else {
                TFChat.send(p, "Staff", ChatFormatting.RED, "Contraseña incorrecta. Te quedan ", TFChat.hl(String.valueOf(MAX_FAILS - f[0])), " intentos.");
            }
            return;
        }
        FAILS.remove(id);
        UNLOCKED.add(id);
        TFClient.LOGGER.info("TF Clave: {} desbloqueó /tf", p.getGameProfile().getName());
        TFChat.send(p, "Staff", ChatFormatting.GREEN, "/tf desbloqueado hasta que salgas del servidor.");
        p.server.getCommands().sendCommands(p);
        if (command != null && !command.isBlank()) p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), command);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        UNLOCKED.remove(id);
        ASKED.remove(id);
        PENDING.remove(id);
    }

    // ------------------------------------------------------------------------------------------------- /tf clave

    /** /tf clave cerrar (vuelve a cerrar tu /tf) y, solo desde la consola, /tf clave cambiar <nueva>. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("clave")
                .then(Commands.literal("cerrar").executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                    UNLOCKED.remove(p.getUUID());
                    p.server.getCommands().sendCommands(p);
                    TFChat.send(p, "Staff", ChatFormatting.GRAY, "/tf cerrado. Para volver a usarlo, la contraseña.");
                    return 1;
                }))
                .then(Commands.literal("cambiar").requires(s -> s.getEntity() == null && s.hasPermission(4))
                        .then(Commands.argument("nueva", StringArgumentType.greedyString()).executes(ctx -> {
                            String pw = StringArgumentType.getString(ctx, "nueva").trim();
                            if (pw.length() < 12) {
                                ctx.getSource().sendFailure(Component.literal("Muy corta: al menos 12 caracteres."));
                                return 0;
                            }
                            try {
                                byte[] s = new byte[16];
                                new SecureRandom().nextBytes(s);
                                String sHex = HexFormat.of().formatHex(s);
                                save(true, sHex, hash(key(pw, sHex)));
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(Component.literal("No se pudo guardar: " + e));
                                return 0;
                            }
                            UNLOCKED.clear();
                            ctx.getSource().sendSuccess(() -> Component.literal("Contraseña de /tf cambiada. Todos tienen que volver a ponerla."), false);
                            return 1;
                        })));
    }

    // ------------------------------------------------------------------------------------------------- red

    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "clave"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Ask.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Ask::write).decoder(Ask::read).consumerMainThread(Ask::handle).add();
        CHANNEL.messageBuilder(Answer.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Answer::write).decoder(Answer::read).consumerMainThread(Answer::handle).add();
    }

    /** Servidor → cliente: abre la ventana de la contraseña. */
    public record Ask() {
        static void write(Ask m, FriendlyByteBuf buf) {}

        static Ask read(FriendlyByteBuf buf) {
            return new Ask();
        }

        static void handle(Ask m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> net.tierrasfantasticas.tfclient.client.TFStaffLockScreen::open);
        }
    }

    /** Cliente → servidor: la contraseña escrita (vacía si se cerró la ventana). */
    public record Answer(String password) {
        static void write(Answer m, FriendlyByteBuf buf) {
            buf.writeUtf(m.password, 128);
        }

        static Answer read(FriendlyByteBuf buf) {
            return new Answer(buf.readUtf(128));
        }

        static void handle(Answer m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) answer(p, m.password);
        }
    }

    /** Para las pruebas en el juego: como si el jugador hubiera escrito esto en la ventana. */
    public static void testAnswer(ServerPlayer p, String password) {
        answer(p, password);
    }

    public static boolean testAsked(UUID uuid) {
        return ASKED.contains(uuid);
    }
}
