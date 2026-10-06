package net.tierrasfantasticas.tfclient.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Ventana de los oficios (/tf jobs), con la forma de un cofre de 5 filas: arriba el marco del pack Medieval Jobs con
 * el dibujo del oficio y su rejilla de 5×2 (los huecos 29-33 y 38-42 del cofre); abajo, donde iría el inventario del
 * jugador, 36 huecos que son solo botones (volver, monedas, abandonar, aceptar…). Los objetos del jugador no salen:
 * esta ventana no tiene su inventario, y nada se coge ni se mueve. La dibuja {@code TFPanelScreen}; el fondo y los
 * textos (cinta y cartel) llegan al abrirla.
 */
public final class TFPanelMenu extends AbstractContainerMenu {
    public static final int GRID = 10;
    /** Huecos de abajo: 3 filas y la barra (como el inventario y la barra rápida del jugador). */
    public static final int BOTTOM = 36;
    public static final int SIZE = GRID + BOTTOM;
    /** Tamaño de un cofre de 5 filas, y dónde empieza su parte de abajo (el inventario) en la textura del cofre. */
    public static final int WIDTH = 176;
    public static final int HEIGHT = 114 + 5 * 18;
    public static final int BOTTOM_TOP = 5 * 18 + 17;

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, TFClient.MOD_ID);
    public static final RegistryObject<MenuType<TFPanelMenu>> TYPE = MENUS.register("panel",
            () -> IForgeMenuType.create((id, inventory, buf) -> new TFPanelMenu(id, buf.readUtf(64), buf.readUtf(128), buf.readUtf(128))));

    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, ClickType type, int button);
    }

    private final SimpleContainer box = new SimpleContainer(SIZE);
    private final Map<Integer, Action> actions = new HashMap<>();
    public final String background;
    public final String ribbon;
    public final String sign;

    private TFPanelMenu(int id, String background, String ribbon, String sign) {
        super(TYPE.get(), id);
        this.background = background;
        this.ribbon = ribbon;
        this.sign = sign;
        // La rejilla del marco, justo sobre los huecos 29-33 y 38-42 del cofre
        for (int i = 0; i < GRID; i++) addSlot(new Button(box, i, 8 + (2 + i % 5) * 18, 18 + (3 + i / 5) * 18));
        // Abajo, en el sitio del inventario (3 filas) y de la barra rápida
        for (int i = 0; i < BOTTOM; i++) {
            int row = i / 9;
            addSlot(new Button(box, GRID + i, 8 + (i % 9) * 18, row < 3 ? 121 + row * 18 : 179));
        }
    }

    /** Abre la ventana en el siguiente tick (seguro desde un clic en otra). */
    public static void open(ServerPlayer player, String background, String ribbon, String sign, Consumer<TFPanelMenu> fill) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (player.hasDisconnected()) return;
            NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> {
                TFPanelMenu menu = new TFPanelMenu(id, background, ribbon, sign);
                fill.accept(menu);
                return menu;
            }, Component.literal(ribbon)), (FriendlyByteBuf buf) -> {
                buf.writeUtf(background, 64);
                buf.writeUtf(ribbon, 128);
                buf.writeUtf(sign, 128);
            });
        }));
    }

    /** Hueco i de la rejilla (0-9: fila de arriba 0-4, de abajo 5-9). */
    public void grid(int i, ItemStack stack, Action action) {
        set(i, stack, action);
    }

    /** Botón i de la barra de abajo del todo (0-8, de izquierda a derecha; donde va la barra rápida). */
    public void nav(int i, ItemStack stack, Action action) {
        set(GRID + 27 + i, stack, action);
    }

    /** Botón de las 3 filas de abajo (fila 0-2, columna 0-8; donde va el inventario). */
    public void bottom(int row, int col, ItemStack stack, Action action) {
        set(GRID + row * 9 + col, stack, action);
    }

    private void set(int slot, ItemStack stack, Action action) {
        box.setItem(slot, stack);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    public void clear() {
        for (int i = 0; i < SIZE; i++) box.setItem(i, ItemStack.EMPTY);
        actions.clear();
    }

    public void update() {
        broadcastChanges();
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= 0 && slot < SIZE && player instanceof ServerPlayer sp && type != ClickType.QUICK_CRAFT) {
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
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    /** Hueco que solo enseña: no se puede coger ni poner nada. */
    private static final class Button extends Slot {
        Button(SimpleContainer box, int index, int x, int y) {
            super(box, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
