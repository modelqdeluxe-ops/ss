/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.156) La tienda del clan (pestaña TIENDA): se paga con Fantastic Clan Coins y compran el líder y los colíderes.
 * El dueño (31 oct 2026): «protecciones del clan de 50x50 y 120x120, 2 por clan de cada una; ponlas caras, no
 * regaladas; y no pongas esa moneda inflada: como las del gacha, 1 o 2 en las recompensas». Con ~6 Clan Coins a la
 * semana de las misiones (y 1–2 por nivel o guerra), los precios son de semanas o meses de clan.
 */
final class PadClanShop {
    /** Una cosa de la tienda: su clave, nombre, descripción, icono, precio en Clan Coins, cuántas por clan (0 = sin tope). */
    record Offer(String key, String name, String desc, Supplier<ItemStack> icon, long price, int max) {}

    static final List<Offer> OFFERS = List.of(
            new Offer("proteccion_clan_50x50", "Protección del clan 50x50",
                    "El territorio del clan: todos los del clan construyen, sin PvP entre vosotros y con el aviso «Territorio de [TAG]». La pone el líder o un colíder.",
                    () -> ClaimBlocks.createTierItem(ClaimTier.byId("claimstone_clan_50x50"), 1), 15, 2),
            new Offer("proteccion_clan_120x120", "Protección del clan 120x120",
                    "Un territorio grande del clan (120x120), con todo lo de la de 50x50. La pone el líder o un colíder.",
                    () -> ClaimBlocks.createTierItem(ClaimTier.byId("claimstone_clan_120x120"), 1), 40, 2),
            new Offer("plazas", "+2 plazas en el clan",
                    "Dos miembros más, además de los que da el nivel (hasta 5 veces: +10).",
                    () -> new ItemStack(Items.PLAYER_HEAD), 10, 5),
            new Offer("potenciador", "Potenciador de misiones (24 h)",
                    "Durante 24 horas, todo lo que hagáis cuenta x1,5 en las misiones del clan.",
                    () -> new ItemStack(Items.EXPERIENCE_BOTTLE), 3, 0));

    private PadClanShop() {}

    static Offer offer(String key) {
        for (Offer o : OFFERS) if (o.key().equals(key)) return o;
        return null;
    }

    static int bought(JsonObject c, String key) {
        return (int) TFJson.num(TFJson.obj(c, "compras"), key, 0);
    }

    private static void markBought(JsonObject c, String key) {
        JsonObject b = TFJson.obj(c, "compras");
        b.addProperty(key, bought(c, key) + 1);
        c.add("compras", b);
        PadClans.STORE_IMPL.changed();
    }

    static PadView view(PadView.Builder b, ServerPlayer player, String id, JsonObject c) {
        UUID me = player.getUUID();
        boolean treasurer = PadClanProgress.treasurer(c, me);
        long coins = PadClanProgress.coins(c);
        b.header(treasurer ? "Se paga con las Clan Coins del clan: tenéis " + coins + "." : "La tienda la usan el líder y los colíderes.");
        for (Offer o : OFFERS) {
            int n = bought(c, o.key());
            List<String> lines = new ArrayList<>();
            lines.add(o.desc());
            if (o.key().equals("potenciador") && PadClanProgress.boosted(c)) {
                lines.add("Activo: quedan " + PadClanViews.until(TFJson.num(c, "potenciadorHasta", 0) - System.currentTimeMillis()) + ".");
            } else if (o.max() > 0) {
                lines.add("Compradas: " + n + " de " + o.max() + ".");
            }
            boolean full = o.max() > 0 && n >= o.max();
            boolean sure = PadServer.confirming(player, "tienda.comprar:" + o.key());
            PadView.Btn buy = !treasurer ? null : full ? PadView.Btn.off("AGOTADO")
                    : coins < o.price() ? PadView.Btn.off("FALTAN " + (o.price() - coins)) : PadView.Btn.of(sure ? "¿SEGURO?" : "COMPRAR", "tienda.comprar:" + o.key(), PadView.GOLD);
            b.row(new PadView.Row(o.icon().get(), o.name(), full ? 0x7E8CA8 : 0x18265C, lines, -1, PadView.clanCoins(o.price()), buy, null));
        }
        return b.build();
    }

    /** Las acciones «tienda.…». */
    static String action(ServerPlayer player, String id, JsonObject c, String action) {
        if (!action.startsWith("tienda.comprar:") || !PadClanProgress.treasurer(c, player.getUUID())) return null;
        Offer o = offer(action.substring("tienda.comprar:".length()));
        if (o == null) return "tienda";
        if (o.max() > 0 && bought(c, o.key()) >= o.max()) {
            TFPadNet.notice(player, "Ya tenéis las " + o.max() + " que se pueden comprar.");
            return "tienda";
        }
        if (PadClanProgress.coins(c) < o.price()) {
            TFPadNet.notice(player, "Cuesta " + o.price() + " Clan Coins y el clan tiene " + PadClanProgress.coins(c) + ".");
            return "tienda";
        }
        if (!PadServer.confirm(player, action)) return "tienda";
        if (!PadClanProgress.takeCoins(c, o.price())) return "tienda";
        markBought(c, o.key());
        switch (o.key()) {
            case "plazas" -> c.addProperty("plazasCompradas", TFJson.num(c, "plazasCompradas", 0) + 1);
            case "potenciador" -> c.addProperty("potenciadorHasta",
                    Math.max(System.currentTimeMillis(), TFJson.num(c, "potenciadorHasta", 0)) + 24L * 60 * 60 * 1000);
            default -> {
                ItemStack s = o.icon().get();
                if (!player.getInventory().add(s)) player.drop(s, false);
            }
        }
        PadClans.STORE_IMPL.changed();
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5F, 1.3F);
        PadClanProgress.tell(player.getServer(), c, PadStats.nameOf(player.getUUID()) + " compró en la tienda del clan: " + o.name() + " (" + o.price()
                + " Clan Coins).", ChatFormatting.AQUA);
        return "tienda";
    }

    static String testAct(ServerPlayer player, String action) {
        String id = PadClans.clanIdOf(player.getUUID());
        return id == null ? null : action(player, id, PadClans.clan(id), action);
    }
}
