package net.tierrasfantasticas.tfclient.menu;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.ItemStack;

/**
 * Menú de cofre del servidor que sirve de botonera: los objetos no se pueden coger ni mover, y cada hueco puede tener
 * una acción al hacer clic. Es el cofre normal de Minecraft (la tienda de monedas); los oficios usan {@link TFPanelMenu}.
 */
public final class TFMenu extends ChestMenu {
    /** Lo que pasa al hacer clic en un hueco. */
    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, ClickType type, int button);
    }

    private final int size;
    private final Map<Integer, Action> actions = new HashMap<>();
    private Runnable onClose;

    private TFMenu(int id, Inventory inventory, int rows) {
        super(type(rows), id, inventory, new SimpleContainer(rows * 9), rows);
        this.size = rows * 9;
    }

    private static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /** Abre un menú nuevo: fill pone los objetos y las acciones. Se abre en el siguiente tick (seguro desde un clic). */
    public static void open(ServerPlayer player, int rows, Component title, java.util.function.Consumer<TFMenu> fill) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (player.hasDisconnected()) return;
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
                TFMenu menu = new TFMenu(id, inventory, rows);
                fill.accept(menu);
                return menu;
            }, title));
        }));
    }

    public int size() {
        return size;
    }

    /** Pone un objeto en un hueco (sin acción). */
    public void set(int slot, ItemStack stack) {
        set(slot, stack, null);
    }

    public void set(int slot, ItemStack stack, Action action) {
        if (slot < 0 || slot >= size) return;
        getContainer().setItem(slot, stack);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    /** Vacía el menú para volver a llenarlo (cambiar de página sin cerrar la ventana). */
    public void clear() {
        for (int i = 0; i < size; i++) getContainer().setItem(i, ItemStack.EMPTY);
        actions.clear();
    }

    /** Manda los cambios al jugador. */
    public void update() {
        broadcastChanges();
    }

    public void onClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        // Nada se mueve: ni en el menú ni en el inventario mientras está abierto.
        if (slot >= 0 && slot < size && player instanceof ServerPlayer sp && type != ClickType.QUICK_CRAFT) {
            Action action = actions.get(slot);
            if (action != null) {
                sp.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4F, 1.0F);
                action.run(sp, type, button);
            }
        }
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, net.minecraft.world.inventory.Slot slot) {
        return false;
    }

    @Override
    public boolean canDragTo(net.minecraft.world.inventory.Slot slot) {
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (onClose != null) onClose.run();
    }
}
