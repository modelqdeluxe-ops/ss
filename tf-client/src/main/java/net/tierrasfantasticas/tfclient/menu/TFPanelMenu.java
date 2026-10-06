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
 * Ventana de los oficios (/tf jobs): el marco con el dibujo del oficio y su rejilla de 5×2, y debajo una barra de 9
 * botones. No tiene el inventario del jugador: solo estos 19 huecos, que son botones (nada se coge ni se mueve).
 * El TF Client la dibuja con {@code TFPanelScreen}; el fondo y los textos (cinta y cartel) llegan al abrirla.
 */
public final class TFPanelMenu extends AbstractContainerMenu {
    public static final int GRID = 10;
    public static final int NAV = 9;
    public static final int SIZE = GRID + NAV;
    /** Posiciones (píxeles de la ventana, como la dibuja TFPanelScreen) */
    public static final int WIDTH = 192;
    public static final int ART_HEIGHT = 144;
    public static final int NAV_Y = 145;
    public static final int HEIGHT = NAV_Y + 30;

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
        for (int i = 0; i < GRID; i++) addSlot(new Button(box, i, 51 + (i % 5) * 18, 96 + (i / 5) * 18));
        for (int i = 0; i < NAV; i++) addSlot(new Button(box, GRID + i, 15 + i * 18, NAV_Y + 6));
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

    /** Botón i de la barra de abajo (0-8, de izquierda a derecha). */
    public void nav(int i, ItemStack stack, Action action) {
        set(GRID + i, stack, action);
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
