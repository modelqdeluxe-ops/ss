package net.tierrasfantasticas.tfclient.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Objetos para los menús: nombre, descripción (sin cursiva), brillo y cantidad. */
public final class TFIcon {
    /** Ancho máximo de una línea de descripción, para que nada se salga de la ventana. */
    public static final int LINE = 34;

    private final ItemStack stack;
    private final List<Component> lore = new ArrayList<>();
    private boolean keep;

    private TFIcon(ItemStack stack) {
        this.stack = stack;
    }

    public static TFIcon of(Item item) {
        return new TFIcon(new ItemStack(item));
    }

    public static TFIcon of(ItemStack stack) {
        return new TFIcon(stack.copy());
    }

    /** Para objetos de verdad (tienda): se queda su descripción y sus encantamientos, y se añade lo nuestro debajo. */
    public TFIcon keepOriginal() {
        keep = true;
        return this;
    }

    public TFIcon name(Component name) {
        stack.setHoverName(plain(name));
        return this;
    }

    public TFIcon name(String text, ChatFormatting... style) {
        return name(Component.literal(text).withStyle(style));
    }

    public TFIcon line(Component line) {
        lore.add(plain(line));
        return this;
    }

    public TFIcon line(String text, ChatFormatting... style) {
        return line(Component.literal(text).withStyle(style.length == 0 ? new ChatFormatting[] {ChatFormatting.GRAY} : style));
    }

    /** Texto largo partido en líneas cortas. */
    public TFIcon text(String text, ChatFormatting... style) {
        for (String part : wrap(text, LINE)) line(part, style);
        return this;
    }

    public TFIcon blank() {
        lore.add(Component.empty());
        return this;
    }

    public TFIcon glow(boolean glow) {
        if (glow) {
            ListTag list = new ListTag();
            list.add(new CompoundTag());
            stack.getOrCreateTag().put("Enchantments", list);
        }
        return this;
    }

    public TFIcon count(int count) {
        stack.setCount(Math.max(1, Math.min(64, count)));
        return this;
    }

    public ItemStack build() {
        CompoundTag tag = stack.getOrCreateTag();
        if (!lore.isEmpty()) {
            ListTag list = keep && tag.getCompound("display").contains("Lore", 9) ? tag.getCompound("display").getList("Lore", 8) : new ListTag();
            for (Component line : lore) list.add(StringTag.valueOf(Component.Serializer.toJson(line)));
            if (!tag.contains("display")) {
                CompoundTag display = new CompoundTag();
                display.put("Lore", list);
                tag.put("display", display);
            } else {
                tag.getCompound("display").put("Lore", list);
            }
        }
        if (!keep) tag.putInt("HideFlags", 127);
        return stack;
    }

    private static MutableComponent plain(Component c) {
        return Component.empty().withStyle(Style.EMPTY.withItalic(false)).append(c);
    }

    /** ▮▮▮▮▮▯▯▯▯▯ */
    public static MutableComponent bar(double fraction, int length, ChatFormatting full) {
        int done = (int) Math.round(Math.max(0, Math.min(1, fraction)) * length);
        return Component.literal("■".repeat(done)).withStyle(full)
                .append(Component.literal("■".repeat(length - done)).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            if (line.length() > 0) out.add(line.toString());
        }
        return out;
    }
}
