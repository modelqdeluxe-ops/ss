/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;

/** Mensajes del Void: el diálogo del caballero, lo que eliges y la animación del viaje. */
public final class VoidNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "vacio"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    /** Lo que se elige en el diálogo. */
    public static final int GO = 0, BACK = 1, BYE = 2, ARMOR = 3;
    /**
     * Fases de la animación: sale hacia el Void (agujero de gusano Overworld → Nether → End), llega, se cancela y sale de
     * vuelta (End → Nether → Overworld; 1.3.91). Una llegada sin viaje (el rescate al caer al vacío) es solo un fundido.
     */
    public static final int FX_LEAVE = 0, FX_ARRIVE = 1, FX_CANCEL = 2, FX_LEAVE_BACK = 3;

    private VoidNet() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Talk.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Talk::write).decoder(Talk::read).consumerMainThread(Talk::handle).add();
        CHANNEL.messageBuilder(Choose.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Choose::write).decoder(Choose::read).consumerMainThread(Choose::handle).add();
        CHANNEL.messageBuilder(Fx.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Fx::write).decoder(Fx::read).consumerMainThread(Fx::handle).add();
        CHANNEL.messageBuilder(Sky.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sky::write).decoder(Sky::read).consumerMainThread(Sky::handle).add();
        CHANNEL.messageBuilder(Light.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Light::write).decoder(Light::read).consumerMainThread(Light::handle).add();
    }

    static void fx(ServerPlayer p, int phase) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Fx(phase, phase == FX_LEAVE || phase == FX_LEAVE_BACK ? VoidTravel.LEAVE_TICKS : 50));
    }

    /**
     * Abre el diálogo: con quién (entidad), tu nombre, si es la primera vez que hablas con él, si es el del santuario
     * (devuelve) y, entonces, a dónde volverías; la grieta del cielo y (1.3.96) los jefes que llevas derrotados (para la
     * armadura del Vacío).
     */
    public record Talk(int entity, String name, boolean first, boolean home, String back, int sky, int kills) {
        static void write(Talk m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.name, 64);
            buf.writeBoolean(m.first);
            buf.writeBoolean(m.home);
            buf.writeUtf(m.back, 128);
            buf.writeVarInt(m.sky);
            buf.writeVarInt(m.kills);
        }

        static Talk read(FriendlyByteBuf buf) {
            return new Talk(buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(), buf.readUtf(128), buf.readVarInt(),
                    buf.readVarInt());
        }

        static void handle(Talk m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VoidClient.talk(m));
        }
    }

    public record Choose(int entity, int what) {
        static void write(Choose m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeVarInt(m.what);
        }

        static Choose read(FriendlyByteBuf buf) {
            return new Choose(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(Choose m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) VoidTravel.choose(p, m.entity, m.what);
        }
    }

    public record Fx(int phase, int ticks) {
        static void write(Fx m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.phase);
            buf.writeVarInt(m.ticks);
        }

        static Fx read(FriendlyByteBuf buf) {
            return new Fx(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(Fx m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TravelFx.start(m.phase, m.ticks));
        }
    }

    /**
     * La grieta del cielo (1.3.93, {@link SkyRift}): su estado, qué ver ahora (preludio, se rompe, se sella...) y si el
     * mundo de ese jugador es de los que la ven (cielo de Overworld).
     */
    public record Sky(int state, int fx, boolean here) {
        static void write(Sky m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.state);
            buf.writeVarInt(m.fx);
            buf.writeBoolean(m.here);
        }

        static Sky read(FriendlyByteBuf buf) {
            return new Sky(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        static void handle(Sky m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkyRiftClient.receive(m));
        }
    }

    /**
     * (1.3.96) Tu luz en el Void ({@link VoidLight}): cuánta (0..100), si estás dentro del Velo del Alba, los ecos que has
     * oído de cuántos hay y lo que acaba de pasar (VoidLight.EV_*: fragmento, entrar o salir del Velo, sin luz).
     */
    /** (1.3.96) La luz del jugador en el Void (100 = sin corrosión), si está en un sitio de luz y lo que acaba de pasar. */
    public record Light(int light, boolean safe, int event) {
        static void write(Light m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.light);
            buf.writeBoolean(m.safe);
            buf.writeVarInt(m.event);
        }

        static Light read(FriendlyByteBuf buf) {
            return new Light(buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
        }

        static void handle(Light m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VoidLightClient.receive(m));
        }
    }
}
