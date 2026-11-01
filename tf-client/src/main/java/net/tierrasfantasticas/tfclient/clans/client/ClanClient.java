/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.clans.ClanBlocks;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.clans.ClanNet;
import net.tierrasfantasticas.tfclient.clans.ClanTagFx;

/**
 * (1.3.159) Lo de los clanes en el cliente: los dibujantes del cofre (a plena luz) y del estandarte, la tecla de los
 * emotes del clan (B de fábrica; Opciones › Controles › TF Client; 1.3.160: abre la rueda de los 5) y la etiqueta del
 * clan animada (arcoíris u onda) encima de la cabeza, en la lista de jugadores (PlayerTabOverlayClanMixin) y en el chat
 * (ChatComponentClanMixin).
 */
public final class ClanClient {
    public static final KeyMapping EMOTE = new KeyMapping("key.tfclient.emote_clan", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_B, "key.categories.tfclient");

    private ClanClient() {}

    /** Segundos para las animaciones (la prueba en el juego los puede llevar a mano: ClanEmoteClient.clock). */
    public static double now() {
        return ClanEmoteClient.clock();
    }

    /**
     * La etiqueta del clan de ese jugador, animada, dentro de un texto ya hecho (el nombre encima de la cabeza o el de la
     * lista): se busca «[TAG]» y se le pone el color de cada letra en este instante. Si no hay efecto animado, igual.
     */
    public static Component animateTag(Component in, UUID uuid) {
        if (in == null || uuid == null) return in;
        ClanLooks.Look l = ClanLooks.ofPlayer(uuid);
        if (l == null || !l.fx().animated()) return in;
        String target = "[" + l.tag() + "]";
        List<String> texts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        in.visit((style, text) -> {
            texts.add(text);
            styles.add(style);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        String plain = String.join("", texts);
        int at = plain.lastIndexOf(target);
        if (at < 0) return in;
        double t = now();
        MutableComponent out = Component.empty();
        int pos = 0;
        for (int i = 0; i < texts.size(); i++) {
            String s = texts.get(i);
            Style st = styles.get(i);
            int a = pos, b = pos + s.length();
            if (b <= at || a >= at + target.length()) {
                out.append(Component.literal(s).withStyle(st));
            } else {
                for (int k = 0; k < s.length(); k++) {
                    int g = a + k;
                    String ch = String.valueOf(s.charAt(k));
                    if (g >= at && g < at + target.length()) {
                        int c = ClanTagFx.colorAt(l.fx(), g - at, target.length(), t);
                        out.append(Component.literal(ch).withStyle(st.withColor(TextColor.fromRgb(c))));
                    } else {
                        out.append(Component.literal(ch).withStyle(st));
                    }
                }
            }
            pos = b;
        }
        return out;
    }

    /**
     * (1.3.160) Una línea del chat con la etiqueta de un clan animado («[TAG]» que llega con color del servidor: lo que
     * escribe un jugador a mano no viene coloreado y no se toca) con los colores de este instante. Si no hay nada que
     * animar, la misma línea.
     */
    public static net.minecraft.util.FormattedCharSequence animateChat(net.minecraft.util.FormattedCharSequence in) {
        if (in == null) return in;
        List<ClanLooks.Look> anim = ClanLooks.animated();
        if (anim.isEmpty()) return in;
        List<Integer> cps = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        in.accept((i, st, cp) -> {
            cps.add(cp);
            styles.add(st);
            return true;
        });
        int n = cps.size();
        int[] color = null;
        double t = now();
        for (ClanLooks.Look l : anim) {
            int[] target = ("[" + l.tag() + "]").codePoints().toArray();
            for (int at = 0; at + target.length <= n; at++) {
                boolean match = true;
                for (int k = 0; k < target.length && match; k++) match = cps.get(at + k) == target[k];
                if (!match || styles.get(at + 1).getColor() == null) continue;
                if (color == null) {
                    color = new int[n];
                    java.util.Arrays.fill(color, -1);
                }
                for (int k = 0; k < target.length; k++) color[at + k] = ClanTagFx.colorAt(l.fx(), k, target.length, t);
                at += target.length - 1;
            }
        }
        if (color == null) return in;
        int[] c = color;
        return sink -> {
            for (int k = 0; k < n; k++) {
                Style st = c[k] >= 0 ? styles.get(k).withColor(TextColor.fromRgb(c[k])) : styles.get(k);
                if (!sink.accept(k, st, cps.get(k))) return false;
            }
            return true;
        };
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ClanBlocks.BANK_BE.get(), ClanBankRenderer::new);
            event.registerBlockEntityRenderer(ClanBlocks.BANNER_BE.get(), ClanBannerRenderer::new);
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(EMOTE);
        }
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            // (1.3.160) la tecla abre la rueda de los 5 emotes
            while (EMOTE.consumeClick()) {
                if (mc.player != null && mc.screen == null) mc.setScreen(new ClanEmoteWheel());
            }
            ClanEmoteClient.tick();
        }

        @SubscribeEvent
        public static void onNameTag(RenderNameTagEvent event) {
            if (event.getEntity() instanceof Player p) event.setContent(animateTag(event.getContent(), p.getUUID()));
        }
    }
}
