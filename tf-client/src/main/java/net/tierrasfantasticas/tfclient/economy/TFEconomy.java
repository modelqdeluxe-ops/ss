/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.economy;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;

/**
 * Las monedas del servidor, para la tienda de monedas y los oficios. Según economy.mode (config/tfclient/servidor.properties):
 * <ul>
 *   <li>vault: la economía del servidor (EssentialsX, CMI…) a través de Vault, en servidores Mohist/Arclight.</li>
 *   <li>tf: monedas propias del TF Client, guardadas en el mundo ({@link TFWallet}); /tf web monedas ver para verlas.</li>
 *   <li>comandos: dar y quitar con economy.give / economy.take. No se puede ver el saldo: el comando de quitar
 *       tiene que fallar si no hay bastantes.</li>
 *   <li>auto (por defecto): Vault si lo hay; si no, las del TF Client.</li>
 * </ul>
 */
public final class TFEconomy {
    public enum Mode { VAULT, TF, COMMANDS }

    private static Vault vault;
    private static long vaultCheckedAt = -1;
    private static boolean warned;

    private TFEconomy() {}

    public static Mode mode() {
        String configured = TFServerConfig.economyMode();
        switch (configured) {
            case "tf":
                return Mode.TF;
            case "comandos":
            case "commands":
                return Mode.COMMANDS;
            case "vault":
                if (vault() != null) return Mode.VAULT;
                if (!warned) {
                    warned = true;
                    TFClient.LOGGER.warn("TF Economía: economy.mode=vault pero no hay Vault con una economía; uso las monedas del TF Client");
                }
                return Mode.TF;
            default:
                return vault() != null ? Mode.VAULT : Mode.TF;
        }
    }

    /**
     * Saldo del jugador (vacío si la economía no deja verlo). (1.3.125) Menos lo que deba por un reembolso
     * ({@link TFDebt}): puede ser negativo.
     */
    public static OptionalLong balance(MinecraftServer server, UUID uuid) {
        OptionalLong raw = rawBalance(server, uuid);
        if (raw.isEmpty()) return raw;
        return OptionalLong.of(raw.getAsLong() - TFDebt.get(server, uuid, TFDebt.COINS));
    }

    /** Lo que tiene de verdad en la economía (sin restar la deuda). */
    private static OptionalLong rawBalance(MinecraftServer server, UUID uuid) {
        return switch (mode()) {
            case TF -> OptionalLong.of(TFWallet.get(server).balance(uuid));
            case VAULT -> {
                Double value = vault().balance(uuid);
                yield value == null ? OptionalLong.empty() : OptionalLong.of((long) Math.floor(value));
            }
            case COMMANDS -> OptionalLong.empty();
        };
    }

    /** Cobra al jugador. Devuelve false (y no cobra nada) si no tiene bastantes. */
    public static boolean take(ServerPlayer player, long amount) {
        if (amount <= 0) return true;
        MinecraftServer server = player.getServer();
        return switch (mode()) {
            case TF -> TFWallet.get(server).take(player.getUUID(), amount);
            case VAULT -> vault().withdraw(player.getUUID(), amount);
            case COMMANDS -> run(server, TFServerConfig.economyTake(), player.getGameProfile().getName(), amount);
        };
    }

    /** Paga al jugador. (1.3.125) Si debe monedas de un reembolso, primero se paga la deuda. */
    public static boolean give(MinecraftServer server, UUID uuid, String name, long amount) {
        if (amount <= 0) return true;
        amount = TFDebt.payFrom(server, uuid, TFDebt.COINS, amount);
        if (amount <= 0) return true;
        return deposit(server, uuid, name, amount);
    }

    private static boolean deposit(MinecraftServer server, UUID uuid, String name, long amount) {
        return switch (mode()) {
            case TF -> {
                TFWallet.get(server).add(uuid, amount);
                yield true;
            }
            case VAULT -> vault().deposit(uuid, amount);
            case COMMANDS -> run(server, TFServerConfig.economyGive(), name, amount);
        };
    }

    /**
     * (1.3.125) Reembolso: quita amount; lo que no tenga (ya lo gastó) queda como deuda. Funciona aunque no esté
     * conectado (con la economía del TF Client o Vault). Devuelve lo que se le quitó ya.
     */
    public static long revoke(MinecraftServer server, UUID uuid, String name, long amount) {
        if (amount <= 0) return 0;
        long now = 0;
        switch (mode()) {
            case TF -> {
                now = Math.min(amount, Math.max(0, TFWallet.get(server).balance(uuid)));
                if (now > 0) TFWallet.get(server).take(uuid, now);
            }
            case VAULT -> {
                Double have = vault().balance(uuid);
                now = have == null ? 0 : Math.min(amount, Math.max(0, (long) Math.floor(have)));
                if (now > 0 && !vault().withdraw(uuid, now)) now = 0;
            }
            case COMMANDS -> {
                // sin ver el saldo: o lo tiene entero o va todo a la deuda
                if (name != null && run(server, TFServerConfig.economyTake(), name, amount)) now = amount;
            }
        }
        TFDebt.add(server, uuid, TFDebt.COINS, amount - now);
        return now;
    }

    /** (1.3.125) Si debe y tiene monedas (le entraron por otro camino), se cobra lo que se pueda. */
    static void collectDebt(MinecraftServer server, ServerPlayer player) {
        long debt = TFDebt.get(server, player.getUUID(), TFDebt.COINS);
        if (debt <= 0) return;
        OptionalLong raw = rawBalance(server, player.getUUID());
        if (raw.isEmpty() || raw.getAsLong() <= 0) return;
        long pay = Math.min(debt, raw.getAsLong());
        boolean ok = switch (mode()) {
            case TF -> TFWallet.get(server).take(player.getUUID(), pay);
            case VAULT -> vault().withdraw(player.getUUID(), pay);
            case COMMANDS -> false;
        };
        if (ok) TFDebt.payFrom(server, player.getUUID(), TFDebt.COINS, pay);
    }

    /** Pone el saldo exacto (solo con las monedas del TF Client). */
    public static boolean set(MinecraftServer server, UUID uuid, long amount) {
        if (mode() != Mode.TF) return false;
        TFWallet.get(server).set(uuid, amount);
        return true;
    }

    private static boolean run(MinecraftServer server, String template, String name, long amount) {
        if (template.isEmpty()) return false;
        String command = template.replace("{player}", name).replace("{amount}", Long.toString(amount));
        List<String> errors = TFBridge.run(server, command);
        if (!errors.isEmpty()) TFClient.LOGGER.warn("TF Economía: «{}» falló: {}", command, errors.get(0));
        return errors.isEmpty();
    }

    /** 1.250 monedas */
    public static String format(long amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).format(amount) + " " + TFServerConfig.currency();
    }

    public static String number(long amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).format(amount);
    }

    private static Vault vault() {
        // Si no está, se vuelve a mirar cada minuto (los plugins pueden cargar después del mod).
        long now = System.currentTimeMillis();
        if (vault == null && (vaultCheckedAt < 0 || now - vaultCheckedAt > 60_000)) {
            vaultCheckedAt = now;
            vault = Vault.find();
        }
        return vault;
    }

    /** Vault por reflexión: solo existe en servidores híbridos (Mohist, Arclight) con el plugin Vault y una economía. */
    private record Vault(Object provider, Method getOfflinePlayer, Method getBalance, Method withdraw, Method deposit,
                         Method success) {
        static Vault find() {
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                Object services = bukkit.getMethod("getServicesManager").invoke(null);
                @SuppressWarnings("unchecked")
                Collection<Class<?>> known = (Collection<Class<?>>) services.getClass().getMethod("getKnownServices").invoke(services);
                Class<?> economy = known.stream().filter(c -> c.getName().equals("net.milkbowl.vault.economy.Economy")).findFirst().orElse(null);
                if (economy == null) return null;
                Object registration = services.getClass().getMethod("getRegistration", Class.class).invoke(services, economy);
                if (registration == null) return null;
                Method getProvider = registration.getClass().getMethod("getProvider");
                getProvider.setAccessible(true);
                Object provider = getProvider.invoke(registration);
                if (provider == null) return null;
                Class<?> offline = Class.forName("org.bukkit.OfflinePlayer", false, bukkit.getClassLoader());
                Method withdraw = economy.getMethod("withdrawPlayer", offline, double.class);
                Method success = withdraw.getReturnType().getMethod("transactionSuccess");
                Vault found = new Vault(provider, bukkit.getMethod("getOfflinePlayer", UUID.class),
                        economy.getMethod("getBalance", offline), withdraw, economy.getMethod("depositPlayer", offline, double.class), success);
                TFClient.LOGGER.info("TF Economía: usando la economía del servidor a través de Vault ({})", provider.getClass().getSimpleName());
                return found;
            } catch (ClassNotFoundException e) {
                return null;
            } catch (Throwable t) {
                TFClient.LOGGER.warn("TF Economía: Vault no disponible: {}", t.toString());
                return null;
            }
        }

        Double balance(UUID uuid) {
            try {
                return ((Number) getBalance.invoke(provider, getOfflinePlayer.invoke(null, uuid))).doubleValue();
            } catch (Throwable t) {
                TFClient.LOGGER.warn("TF Economía: no se pudo leer el saldo: {}", t.toString());
                return null;
            }
        }

        boolean withdraw(UUID uuid, long amount) {
            try {
                Object player = getOfflinePlayer.invoke(null, uuid);
                double have = ((Number) getBalance.invoke(provider, player)).doubleValue();
                if (have < amount) return false;
                return (boolean) success.invoke(withdraw.invoke(provider, player, (double) amount));
            } catch (Throwable t) {
                TFClient.LOGGER.warn("TF Economía: no se pudo cobrar: {}", t.toString());
                return false;
            }
        }

        boolean deposit(UUID uuid, long amount) {
            try {
                return (boolean) success.invoke(deposit.invoke(provider, getOfflinePlayer.invoke(null, uuid), (double) amount));
            } catch (Throwable t) {
                TFClient.LOGGER.warn("TF Economía: no se pudo pagar: {}", t.toString());
                return false;
            }
        }
    }
}
