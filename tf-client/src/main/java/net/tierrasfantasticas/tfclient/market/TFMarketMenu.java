package net.tierrasfantasticas.tfclient.market;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.items.TFItems;
import net.tierrasfantasticas.tfclient.market.TFMarket.Listing;
import net.tierrasfantasticas.tfclient.menu.TFIcon;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;

/**
 * La ventana del GTS (se abre desde el TF Pad): un cofre de 6 filas que solo tiene botones, como la tienda.
 * Vistas: el mercado (por páginas), comprar (confirmar), mis publicaciones y recoger lo que volvió.
 */
public final class TFMarketMenu extends ChestMenu {
    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final String TITLE = "GTS · Mercado de jugadores";

    @FunctionalInterface
    private interface Action {
        void run(ServerPlayer player, ClickType type, int button);
    }

    private final SimpleContainer box;
    private final Map<Integer, Action> actions = new HashMap<>();

    private TFMarketMenu(int id, Inventory inventory, SimpleContainer box) {
        super(MenuType.GENERIC_9x6, id, inventory, box, 6);
        this.box = box;
    }

    private static void open(ServerPlayer player, String title, Consumer<TFMarketMenu> fill) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (player.hasDisconnected()) return;
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
                TFMarketMenu menu = new TFMarketMenu(id, inventory, new SimpleContainer(SIZE));
                fill.accept(menu);
                return menu;
            }, Component.literal(title)));
        }));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vistas
    // ---------------------------------------------------------------------------------------------------------------

    public static void openMain(ServerPlayer player) {
        open(player, TITLE, menu -> menu.fillMain(player, 0));
    }

    public static void openMine(ServerPlayer player) {
        open(player, "GTS · Lo que vendes", menu -> menu.fillMine(player));
    }

    private void fillMain(ServerPlayer player, int page) {
        clear();
        List<Listing> all = TFMarket.listings();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= all.size()) break;
            Listing l = all.get(index);
            boolean mine = l.seller().equals(player.getUUID());
            set(i, listingIcon(l, mine ? "Es tuya · clic para retirarla" : "Clic para comprar"), (pl, t, b) -> {
                if (mine) {
                    TFMarket.withdraw(pl, l.id());
                    fillMain(pl, current);
                } else {
                    fillBuy(pl, l, current);
                }
            });
        }
        if (all.isEmpty()) {
            set(22, TFIcon.of(Items.PAPER).name("Aún no hay nada a la venta", ChatFormatting.YELLOW)
                    .text("Sé el primero: ten en la mano lo que quieres vender y pulsa «Vender lo de tu mano».").build(), null);
        }
        bottomBar(player);
        if (current > 0) set(48, arrow("◀ Página " + current), (pl, t, b) -> fillMain(pl, current - 1));
        if (current < pages - 1) set(50, arrow("Página " + (current + 2) + " ▶"), (pl, t, b) -> fillMain(pl, current + 1));
        broadcastChanges();
    }

    private void bottomBar(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        String why = TFMarket.whyNot(hand);
        TFIcon sell = TFIcon.of(Items.EMERALD).name("Vender lo de tu mano", ChatFormatting.GREEN);
        if (why == null) {
            sell.line(Component.literal("En la mano: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(TFMarket.describe(hand)).withStyle(ChatFormatting.WHITE)));
            sell.blank().text("Después escribes el precio en el chat.", ChatFormatting.GRAY);
            sell.blank().line("Clic para ponerlo a la venta", ChatFormatting.YELLOW);
        } else {
            sell.text(why, ChatFormatting.RED);
        }
        set(45, sell.build(), (pl, t, b) -> TFMarket.requestPrice(pl));
        int mine = TFMarket.listingsOf(player.getUUID()).size();
        set(46, TFIcon.of(Items.CHEST).name("Lo que vendes (" + mine + "/" + TFMarket.MAX_LISTINGS + ")", ChatFormatting.GOLD)
                .text("Mira tus publicaciones y retira las que quieras.").blank()
                .line("Clic para verlas", ChatFormatting.YELLOW).build(), (pl, t, b) -> fillMine(pl));
        int waiting = TFMarket.returnsOf(player.getUUID()).size();
        if (waiting > 0) {
            set(47, TFIcon.of(Items.ENDER_CHEST).name("Recoger (" + waiting + ")", ChatFormatting.AQUA)
                    .text("Lo que no se vendió a tiempo vuelve aquí.").blank()
                    .line("Clic para recogerlo", ChatFormatting.YELLOW).glow(true).build(), (pl, t, b) -> fillReturns(pl));
        }
        set(49, coins(player), null);
        set(52, TFIcon.of(Items.BOOK).name("¿Cómo funciona?", ChatFormatting.GOLD)
                .text("Compra y vende con otros jugadores usando tus " + TFServerConfig.currency() + ".")
                .blank()
                .text("Para vender: ten el objeto en la mano, pulsa «Vender lo de tu mano» y escribe el precio. Estará "
                        + TFMarket.DAYS + " días; si nadie lo compra, vuelve a «Recoger».", ChatFormatting.GRAY)
                .blank()
                .text("Cobras al momento cuando alguien compra lo tuyo, aunque no estés conectado.", ChatFormatting.GREEN)
                .blank()
                .text("No se venden piezas de sets, lo comprado en la web ni contenedores llenos.", ChatFormatting.DARK_GRAY).build(), null);
        set(53, close(), (pl, t, b) -> pl.closeContainer());
    }

    private void fillBuy(ServerPlayer player, Listing l, int page) {
        clear();
        set(22, listingIcon(l, null), null);
        set(30, TFIcon.of(Items.LIME_CONCRETE).name(Component.literal("Comprar por " + TFEconomy.format(l.price()))
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true).withItalic(false)))
                .line("Clic para pagar", ChatFormatting.YELLOW).build(), (pl, t, b) -> {
                    if (TFMarket.buy(pl, l.id())) fillMain(pl, page);
                    else fillBuy(pl, l, page);
                });
        set(32, TFIcon.of(Items.RED_CONCRETE).name("Mejor no", ChatFormatting.RED).build(), (pl, t, b) -> fillMain(pl, page));
        set(45, arrow("◀ Volver"), (pl, t, b) -> fillMain(pl, page));
        set(49, coins(player), null);
        set(53, close(), (pl, t, b) -> pl.closeContainer());
        broadcastChanges();
    }

    private void fillMine(ServerPlayer player) {
        clear();
        List<Listing> mine = TFMarket.listingsOf(player.getUUID());
        for (int i = 0; i < mine.size() && i < PER_PAGE; i++) {
            Listing l = mine.get(i);
            set(i, listingIcon(l, "Clic para retirarla"), (pl, t, b) -> {
                TFMarket.withdraw(pl, l.id());
                fillMine(pl);
            });
        }
        if (mine.isEmpty()) {
            set(22, TFIcon.of(Items.PAPER).name("No tienes nada a la venta", ChatFormatting.YELLOW)
                    .text("Ten en la mano lo que quieres vender y pulsa «Vender lo de tu mano».").build(), null);
        }
        bottomBar(player);
        set(46, arrow("◀ Al mercado"), (pl, t, b) -> fillMain(pl, 0));
        broadcastChanges();
    }

    private void fillReturns(ServerPlayer player) {
        clear();
        List<ItemStack> items = TFMarket.returnsOf(player.getUUID());
        for (int i = 0; i < items.size() && i < PER_PAGE; i++) {
            int index = i;
            set(i, TFIcon.of(items.get(i)).keepOriginal().blank().line("Clic para recogerlo", ChatFormatting.YELLOW).build(), (pl, t, b) -> {
                TFMarket.collect(pl, index);
                fillReturns(pl);
            });
        }
        if (items.isEmpty()) set(22, TFIcon.of(Items.PAPER).name("No hay nada que recoger", ChatFormatting.GRAY).build(), null);
        set(45, arrow("◀ Al mercado"), (pl, t, b) -> fillMain(pl, 0));
        set(49, coins(player), null);
        set(53, close(), (pl, t, b) -> pl.closeContainer());
        broadcastChanges();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Botones comunes
    // ---------------------------------------------------------------------------------------------------------------

    private static ItemStack listingIcon(Listing l, String action) {
        long days = Math.max(0, (l.expiresAt() - System.currentTimeMillis()) / (24L * 60 * 60 * 1000));
        TFIcon icon = TFIcon.of(l.item()).keepOriginal().blank();
        icon.line(Component.literal("Precio: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(TFEconomy.format(l.price())).withStyle(ChatFormatting.GOLD)));
        icon.line(Component.literal("Lo vende: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(l.sellerName()).withStyle(ChatFormatting.WHITE)));
        icon.line(days >= 1 ? "Quedan " + days + " días" : "Quedan menos de 24 h", ChatFormatting.DARK_GRAY);
        if (action != null) icon.blank().line(action, ChatFormatting.YELLOW);
        return icon.build();
    }

    private static ItemStack coins(ServerPlayer player) {
        OptionalLong balance = TFEconomy.balance(player.getServer(), player.getUUID());
        return TFIcon.of(TFItems.COIN.get()).name("Tus " + TFServerConfig.currency(), ChatFormatting.GOLD)
                .line(balance.isPresent() ? TFEconomy.format(balance.getAsLong()) : "—", ChatFormatting.YELLOW).build();
    }

    private static ItemStack arrow(String text) {
        return TFIcon.of(Items.ARROW).name(text, ChatFormatting.YELLOW).build();
    }

    private static ItemStack close() {
        return TFIcon.of(Items.BARRIER).name("Cerrar", ChatFormatting.RED).build();
    }

    private void clear() {
        actions.clear();
        ItemStack fill = TFIcon.of(Items.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) box.setItem(i, i >= 45 ? fill.copy() : ItemStack.EMPTY);
    }

    private void set(int slot, ItemStack stack, Action action) {
        box.setItem(slot, stack);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Nada se mueve: cada clic lo resuelve el servidor
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (type != ClickType.QUICK_CRAFT && type != ClickType.PICKUP_ALL && slot >= 0 && slot < SIZE) {
            Action action = actions.get(slot);
            if (action != null) {
                sp.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4F, 1.0F);
                action.run(sp, type, button);
            }
        }
        setCarried(ItemStack.EMPTY);
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
