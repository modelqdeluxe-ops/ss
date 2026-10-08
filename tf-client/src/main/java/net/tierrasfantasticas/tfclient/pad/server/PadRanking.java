package net.tierrasfantasticas.tfclient.pad.server;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.economy.TFWallet;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;

/** Ranking: los 10 primeros en misiones, cazas, likes, exploraciones y (con las monedas del TF Client) monedas. */
public final class PadRanking {
    public static final PadServer.App APP = new App();

    private PadRanking() {}

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            boolean coins = TFEconomy.mode() == TFEconomy.Mode.TF;
            String t = tab.isEmpty() ? "misiones" : tab;
            PadView.Builder b = PadView.of("ranking").tab("misiones", "MISIONES").tab("cazas", "CAZAS").tab("likes", "LIKES")
                    .tab("exploraciones", "EXPLORAR");
            if (coins) b.tab("monedas", "MONEDAS");
            b.selected(t).empty("Aún no hay nadie en este ranking. ¡Sé el primero!");
            List<Map.Entry<UUID, Long>> top;
            String unit;
            switch (t) {
                case "cazas" -> {
                    top = PadStats.top(PadStats.HUNTS, 10);
                    unit = " cazas";
                }
                case "likes" -> {
                    top = PadStats.top(PadStats.LIKES, 10);
                    unit = " likes";
                }
                case "exploraciones" -> {
                    top = PadStats.top(PadStats.EXPLORES, 10);
                    unit = " exploraciones";
                }
                case "monedas" -> {
                    top = coins ? TFWallet.get(player.getServer()).top(10) : List.of();
                    unit = "";
                }
                default -> {
                    top = PadStats.top(PadStats.MISSIONS, 10);
                    unit = " misiones";
                }
            }
            for (int i = 0; i < top.size(); i++) {
                UUID u = top.get(i).getKey();
                long v = top.get(i).getValue();
                ItemStack medal = new ItemStack(i == 0 ? Items.GOLD_INGOT : i == 1 ? Items.IRON_INGOT : i == 2 ? Items.COPPER_INGOT : Items.PAPER);
                String value = t.equals("monedas") ? TFEconomy.format(v) : v + unit;
                boolean me = u.equals(player.getUUID());
                b.row(new PadView.Row(medal, (i + 1) + ". " + PadStats.nameOf(u) + (me ? " (tú)" : ""), me ? 0xC27A10 : 0x18265C,
                        List.of(value), -1, "", null, null));
            }
            return b.build();
        }
    }
}
