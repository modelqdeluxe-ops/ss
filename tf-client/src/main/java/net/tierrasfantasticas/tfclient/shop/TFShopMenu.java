package net.tierrasfantasticas.tfclient.shop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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
import net.tierrasfantasticas.tfclient.menu.TFIcon;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig.Category;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig.Entry;

/**
 * La ventana de /tf shop: un cofre de 6 filas que solo tiene botones (nada se coge ni se mueve; lo decide el servidor
 * en cada clic). Abajo se ve el inventario del jugador: con venderDesdeInventario, al pulsar un objeto suyo que la
 * tienda compra, lo vende. Tres vistas: categorías, objetos de una categoría (por páginas) y cantidad a comprar.
 */
public final class TFShopMenu extends ChestMenu {
    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    /** Huecos de las categorías cuando la configuración no dice cuál: las filas 2-4 sin los bordes. */
    private static final int[] AUTO = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    @FunctionalInterface
    private interface Action {
        void run(ServerPlayer player, ClickType type, int button);
    }

    private final SimpleContainer box;
    private final Map<Integer, Action> actions = new HashMap<>();
    /** Qué hacer al pulsar un objeto del inventario del jugador (vender), y cómo volver a pintar la vista actual. */
    private Runnable refresh = () -> {};

    private TFShopMenu(int id, Inventory inventory, SimpleContainer box) {
        super(MenuType.GENERIC_9x6, id, inventory, box, 6);
        this.box = box;
    }

    private static void open(ServerPlayer player, String title, Consumer<TFShopMenu> fill) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (player.hasDisconnected()) return;
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
                TFShopMenu menu = new TFShopMenu(id, inventory, new SimpleContainer(SIZE));
                fill.accept(menu);
                return menu;
            }, Component.literal(TFShop.colors(title))));
        }));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vistas
    // ---------------------------------------------------------------------------------------------------------------

    public static void openMain(ServerPlayer player) {
        open(player, TFShopConfig.title, menu -> menu.fillMain(player));
    }

    private void fillMain(ServerPlayer player) {
        clear();
        List<Category> visible = TFShop.visible(player);
        int auto = 0;
        boolean[] used = new boolean[SIZE];
        for (Category c : visible) {
            int slot = c.slot();
            if (slot < 0 || slot >= 45 || used[slot]) {
                while (auto < AUTO.length && used[AUTO[auto]]) auto++;
                if (auto >= AUTO.length) break;
                slot = AUTO[auto++];
            }
            used[slot] = true;
            int buyable = (int) c.entries().stream().filter(Entry::buyable).count();
            int sellable = (int) c.entries().stream().filter(Entry::sellable).count();
            TFIcon icon = TFIcon.of(c.icon()).name(Component.literal(c.name()).withStyle(color(c.color()).withBold(true)));
            for (String line : c.description()) icon.text(line, ChatFormatting.GRAY);
            icon.blank();
            icon.line(c.entries().size() + " objetos", ChatFormatting.GRAY);
            if (buyable > 0) icon.line("● " + buyable + " se pueden comprar", ChatFormatting.GREEN);
            if (sellable > 0) icon.line("● " + sellable + " se pueden vender", ChatFormatting.GOLD);
            icon.blank();
            icon.line("Clic para entrar", ChatFormatting.YELLOW);
            set(slot, icon.build(), (pl, t, b) -> openCategory(pl, c, 0));
        }
        set(45, TFIcon.of(Items.BOOK).name("¿Cómo funciona?", ChatFormatting.GOLD)
                .text("Elige una categoría. Clic izquierdo en un objeto para comprarlo (eliges la cantidad); clic derecho para venderlo.")
                .blank()
                .text("También puedes pulsar los objetos de tu inventario para venderlos.", ChatFormatting.GREEN)
                .blank()
                .text("Solo se compran objetos sin nombre, encantamientos ni daño.", ChatFormatting.DARK_GRAY).build(), null);
        set(49, coins(player), null);
        if (TFShopConfig.sellAllButton) {
            set(51, TFIcon.of(Items.HOPPER).name("Vender todo", ChatFormatting.GOLD)
                    .text("Vende de una vez todo lo de tu inventario que compra la tienda.")
                    .blank().line("Clic para vender", ChatFormatting.YELLOW).build(), (pl, t, b) -> {
                        TFShop.sellAll(pl);
                        fillMain(pl);
                    });
        }
        set(53, close(), (pl, t, b) -> pl.closeContainer());
        refresh = () -> fillMain(player);
        broadcastChanges();
    }

    private static void openCategory(ServerPlayer player, Category c, int page) {
        open(player, TFShopConfig.title + " · " + c.name(), menu -> menu.fillCategory(player, c, page));
    }

    private void fillCategory(ServerPlayer player, Category c, int page) {
        clear();
        List<Entry> entries = c.entries();
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= entries.size()) break;
            Entry e = entries.get(index);
            set(i, entryIcon(player, e), (pl, type, button) -> {
                if (button == 1 && e.sellable()) {
                    TFShop.sell(pl, e, type == ClickType.QUICK_MOVE ? 0 : 1, false);
                    fillCategory(pl, c, current);
                } else if (button == 0 && e.buyable()) {
                    openAmount(pl, c, current, e);
                }
            });
        }
        set(45, arrow("◀ Categorías"), (pl, t, b) -> openMain(pl));
        if (current > 0) set(47, arrow("◀ Página " + current), (pl, t, b) -> fillCategory(pl, c, current - 1));
        set(49, coins(player), null);
        if (current < pages - 1) set(51, arrow("Página " + (current + 2) + " ▶"), (pl, t, b) -> fillCategory(pl, c, current + 1));
        set(53, close(), (pl, t, b) -> pl.closeContainer());
        refresh = () -> fillCategory(player, c, current);
        broadcastChanges();
    }

    private ItemStack entryIcon(ServerPlayer player, Entry e) {
        ItemStack base = e.stack(Math.min(e.amount(), e.item().getMaxStackSize()));
        TFIcon icon = TFIcon.of(base).keepOriginal();
        if (!e.name().isBlank()) icon.name(Component.literal(TFShop.colors(e.name())).withStyle(Style.EMPTY.withItalic(false)));
        for (String line : e.lore()) icon.text(TFShop.colors(line), ChatFormatting.GRAY);
        if (!e.lore().isEmpty()) icon.blank();
        String lot = e.amount() > 1 ? " (" + e.amount() + " u.)" : "";
        if (e.buyable()) {
            icon.line(Component.literal("Comprar: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(TFEconomy.format(e.buy()) + lot).withStyle(ChatFormatting.GREEN)));
        }
        if (e.sellable()) {
            icon.line(Component.literal("Vender: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(TFEconomy.format(e.sell()) + lot).withStyle(ChatFormatting.GOLD)));
            icon.line("Tienes: " + TFShop.count(player.getInventory(), e), ChatFormatting.DARK_GRAY);
        }
        if (e.buyDaily() > 0) {
            icon.line("Compra diaria: te quedan " + TFShop.left(player.getUUID(), "c:" + e.key(), e.buyDaily()) + " de " + e.buyDaily(),
                    ChatFormatting.DARK_AQUA);
        }
        if (e.sellDaily() > 0) {
            icon.line("Venta diaria: te quedan " + TFShop.left(player.getUUID(), "v:" + e.key(), e.sellDaily()) + " de " + e.sellDaily(),
                    ChatFormatting.DARK_AQUA);
        }
        icon.blank();
        if (e.buyable()) icon.line("Clic izquierdo: comprar", ChatFormatting.YELLOW);
        if (e.sellable()) {
            icon.line("Clic derecho: vender " + (e.amount() > 1 ? "un lote" : "uno"), ChatFormatting.YELLOW);
            icon.line("Mayús + clic derecho: vender todo", ChatFormatting.YELLOW);
        }
        return icon.build();
    }

    private static void openAmount(ServerPlayer player, Category c, int page, Entry e) {
        open(player, TFShopConfig.title + " · Comprar", menu -> menu.fillAmount(player, c, page, e, 1));
    }

    private void fillAmount(ServerPlayer player, Category c, int page, Entry e, int lots) {
        clear();
        int max = TFShopConfig.maxLots;
        if (e.buyDaily() > 0) {
            long left = TFShop.left(player.getUUID(), "c:" + e.key(), e.buyDaily()) / e.amount();
            max = (int) Math.max(1, Math.min(max, left));
        }
        int n = Math.max(1, Math.min(lots, max));
        long units = (long) n * e.amount();
        long price = e.buy() * n;
        List<Integer> steps = TFShopConfig.steps;
        // Restar (rojo) a la izquierda y sumar (verde) a la derecha del objeto
        for (int i = 0; i < steps.size(); i++) {
            int step = steps.get(i);
            set(21 - i, TFIcon.of(Items.RED_STAINED_GLASS_PANE).count(Math.min(64, step)).name("−" + step, ChatFormatting.RED).build(),
                    (pl, t, b) -> fillAmount(pl, c, page, e, n - step));
            set(23 + i, TFIcon.of(Items.LIME_STAINED_GLASS_PANE).count(Math.min(64, step)).name("+" + step, ChatFormatting.GREEN).build(),
                    (pl, t, b) -> fillAmount(pl, c, page, e, n + step));
        }
        TFIcon preview = TFIcon.of(e.stack((int) Math.min(64, Math.max(1, units)))).keepOriginal();
        if (!e.name().isBlank()) preview.name(Component.literal(TFShop.colors(e.name())).withStyle(Style.EMPTY.withItalic(false)));
        preview.line(Component.literal("Cantidad: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(units + (e.amount() > 1 ? " (" + n + " lotes)" : "")).withStyle(ChatFormatting.WHITE)));
        preview.line(Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(TFEconomy.format(price)).withStyle(ChatFormatting.GOLD)));
        set(22, preview.build(), null);
        set(40, TFIcon.of(Items.LIME_CONCRETE).name(Component.literal("Comprar " + units + " por " + TFEconomy.format(price))
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true).withItalic(false)))
                .line("Clic para pagar", ChatFormatting.YELLOW).build(), (pl, t, b) -> {
                    if (TFShop.buy(pl, e, n)) fillAmount(pl, c, page, e, n);
                    else fillAmount(pl, c, page, e, n);
                });
        set(45, arrow("◀ Volver"), (pl, t, b) -> openCategory(pl, c, page));
        set(49, coins(player), null);
        set(53, close(), (pl, t, b) -> pl.closeContainer());
        refresh = () -> fillAmount(player, c, page, e, n);
        broadcastChanges();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Botones comunes
    // ---------------------------------------------------------------------------------------------------------------

    private static ItemStack coins(ServerPlayer player) {
        OptionalLong balance = TFEconomy.balance(player.getServer(), player.getUUID());
        TFIcon icon = TFIcon.of(TFItems.COIN.get()).name("Tus " + TFServerConfig.currency(), ChatFormatting.GOLD);
        icon.line(balance.isPresent() ? TFEconomy.format(balance.getAsLong()) : "—", ChatFormatting.YELLOW);
        icon.blank();
        icon.text("Se ganan con los oficios (app Oficios del pad) y vendiendo en la tienda.", ChatFormatting.DARK_GRAY);
        return icon.build();
    }

    private static ItemStack arrow(String text) {
        return TFIcon.of(Items.ARROW).name(text, ChatFormatting.YELLOW).build();
    }

    private static ItemStack close() {
        return TFIcon.of(Items.BARRIER).name("Cerrar", ChatFormatting.RED).build();
    }

    private static Style color(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false);
    }

    private void clear() {
        actions.clear();
        ItemStack fill = TFIcon.of(TFShopConfig.filler).name(" ").build();
        for (int i = 0; i < SIZE; i++) box.setItem(i, i >= 45 ? fill.copy() : ItemStack.EMPTY);
    }

    private void set(int slot, ItemStack stack, Action action) {
        box.setItem(slot, stack);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Nada se mueve: cada clic lo resuelve el servidor y se vuelve a enviar todo
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (type != ClickType.QUICK_CRAFT && type != ClickType.PICKUP_ALL) {
            if (slot >= 0 && slot < SIZE) {
                Action action = actions.get(slot);
                if (action != null) {
                    if (TFShopConfig.sounds) sp.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4F, 1.0F);
                    action.run(sp, type, button);
                }
            } else if (slot >= SIZE && slot < slots.size() && TFShopConfig.sellFromInventory) {
                Slot s = slots.get(slot);
                ItemStack stack = s.getItem();
                Entry e = TFShop.sellableFor(stack);
                if (e != null) {
                    int lots = stack.getCount() / e.amount();
                    if (lots > 0) TFShop.sell(sp, e, lots, false);
                    refresh.run();
                }
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
