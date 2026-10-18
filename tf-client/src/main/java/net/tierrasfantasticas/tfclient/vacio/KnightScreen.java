package net.tierrasfantasticas.tfclient.vacio;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;
import org.lwjgl.glfw.GLFW;

/**
 * El diálogo con Aurelion, el caballero de luz (lo pidió el dueño: «al seleccionarlo hazle un diseño bonito, que salude,
 * su nombre, qué es él, un breve lore del Void, las grietas... y que le pida si quiere viajar al Void; la segunda vez,
 * solo le saluda por su nombre»).
 *
 * <p>Una placa abajo, maciza (morado casi negro con marco de oro y adornos en las esquinas): a la izquierda su retrato
 * (el modelo de verdad, de tres cuartos, sobre un fondo de luz), a la derecha su nombre en Cinzel dorada, su
 * título, el texto que se va escribiendo (clic o espacio para verlo entero) y las respuestas (con el ratón o con 1, 2,
 * 3). Motas de luz suben por la placa. Hay páginas de lore (qué es el Void, qué son las grietas, qué hace él). El del
 * santuario del vacío, en vez de llevarte, te devuelve a donde estabas.
 */
@OnlyIn(Dist.CLIENT)
public class KnightScreen extends Screen {
    private static final ResourceLocation CINZEL = new ResourceLocation(TFClient.MOD_ID, "cinzel");
    private static final int BG = 0xFF140B22, BG2 = 0xFF1E1132, GOLD = 0xFFD9B45A, GOLD_HI = 0xFFFFE7A0, INNER = 0xFF3A2457,
            TEXT = 0xFFEDE6FF, SUB = 0xFFB9A6E8, OPT = 0xFF24133A, OPT_HI = 0xFF3B1F60;

    private enum Page { GREET, LORE_VOID, LORE_RIFTS, LORE_KNIGHT, WHERE, LEAVING }

    private final VoidNet.Talk talk;
    private final int entityId;
    private Page page = Page.GREET;
    private String text = "";
    private final List<Option> options = new ArrayList<>();
    private float shown;
    private int age, closeAt = -1;
    private final float[][] motes = new float[26][4];

    private record Option(String label, Runnable action) {}

    public KnightScreen(VoidNet.Talk talk) {
        super(Component.literal("Aurelion"));
        this.talk = talk;
        this.entityId = talk.entity();
        java.util.Random r = new java.util.Random();
        for (float[] m : motes) {
            m[0] = r.nextFloat();
            m[1] = r.nextFloat();
            m[2] = 0.002F + r.nextFloat() * 0.004F;
            m[3] = r.nextFloat() * 6.28F;
        }
        go(Page.GREET);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** «Volver a Tierras Fantásticas · 120, 64, -30» (a donde estabas antes de venir). */
    private String backLabel() {
        String b = talk.back();
        return b == null || b.isEmpty() ? "Volver a Tierras Fantásticas" : "Volver a " + b;
    }

    private void go(Page p) {
        page = p;
        shown = 0;
        options.clear();
        String n = talk.name();
        if (talk.home()) {
            switch (p) {
                case GREET -> text = talk.first()
                        ? "Bienvenido al santuario, " + n + ". Has cruzado el umbral: esto es el corazón del Void. Aquí la luz es escasa... no te alejes mucho de ella."
                        : "Has vuelto al santuario, " + n + ". El Void te observa. ¿Regresas a casa, o te quedas un poco más?";
                case WHERE -> text = "Esta isla es lo último que queda de una tierra que el Void se tragó. Las grietas brotan en sus orillas, y el agujero negro de ahí arriba es su corazón. Si caes al vacío, mi luz te traerá de vuelta a este santuario.";
                case LEAVING -> text = "Que la luz te guíe de regreso, " + n + ". Aquí estaré cuando el Void vuelva a llamarte.";
                default -> text = "";
            }
            if (p == Page.LEAVING) return;
            options.add(new Option(backLabel(), () -> leave(VoidNet.BACK)));
            if (p != Page.WHERE) options.add(new Option("¿Dónde estoy?", () -> go(Page.WHERE)));
            options.add(new Option("Me quedo un rato", this::onClose));
            return;
        }
        switch (p) {
            case GREET -> text = talk.first()
                    ? "¡Saludos, " + n + "! Que la luz guíe tus pasos. Soy Aurelion, el último de los Caballeros de Luz, y custodio el umbral entre Tierras Fantásticas y el Void."
                    : "¡" + n + "! Me alegra verte de nuevo, viajero. La luz aún te reconoce. ¿Volvemos al Void?";
            case LORE_VOID -> text = "El Void es lo que hay bajo las costuras de nuestro mundo: un océano de oscuridad donde flotan islas arrancadas de otras tierras. En su cielo, un agujero negro devora la luz de mil estrellas.";
            case LORE_RIFTS -> text = "Las grietas son heridas que el Void abre en la realidad. Dentro duermen criaturas antiguas: golpea su cristal y despertarán. Quien las derrota se gana su gloria... y sus tesoros.";
            case LORE_KNIGHT -> text = "Mi orden juró vigilar el umbral. Mi luz puede abrirte camino hasta el santuario del Void, y mi hermano de armas te traerá de vuelta cuando se lo pidas. ¿Te atreves?";
            case LEAVING -> text = "Cierra los ojos y no temas, " + n + ". ¡Que la luz te lleve!";
            default -> text = "";
        }
        if (p == Page.LEAVING) return;
        options.add(new Option("Llévame al Void", () -> leave(VoidNet.GO)));
        switch (p) {
            case GREET -> options.add(new Option("¿Qué es el Void?", () -> go(Page.LORE_VOID)));
            case LORE_VOID -> options.add(new Option("¿Y las grietas?", () -> go(Page.LORE_RIFTS)));
            case LORE_RIFTS -> options.add(new Option("¿Y tú qué haces aquí?", () -> go(Page.LORE_KNIGHT)));
            default -> {
            }
        }
        options.add(new Option(p == Page.LORE_KNIGHT ? "Ahora no" : "Adiós", this::onClose));
    }

    private void leave(int what) {
        VoidNet.CHANNEL.sendToServer(new VoidNet.Choose(entityId, what));
        go(Page.LEAVING);
        closeAt = age + 34;
        click(1.0F);
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.9F));
    }

    @Override
    public void tick() {
        age++;
        shown = Math.min(text.length(), shown + 1.7F);
        if (closeAt >= 0 && age >= closeAt) onClose();
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (e == null || mc.player == null || e.distanceToSqr(mc.player) > 14 * 14) {
            if (closeAt < 0) onClose();
        }
    }

    // ------------------------------------------------------------------------------------------- dibujo

    private int panelW() {
        return Math.min(470, width - 24);
    }

    private int panelH() {
        return 64 + lines().size() * 10 + Math.max(1, options.size()) * 17 + 8;
    }

    private int textW() {
        return panelW() - portraitW() - 30;
    }

    private int portraitW() {
        return panelW() < 360 ? 0 : 104;
    }

    private List<FormattedCharSequence> lines() {
        return font.split(Component.literal(text), Math.max(80, textW()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Oscurece arriba y abajo (se sigue viendo el mundo y al caballero en medio)
        g.fillGradient(0, 0, width, height / 3, 0xA0000000, 0x00000000);
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0xC8050010);
        int w = panelW(), h = panelH();
        int x0 = (width - w) / 2, y0 = height - h - 14;
        float t = age + partial;
        // Placa maciza con doble marco y adornos
        g.fill(x0 - 3, y0 - 3, x0 + w + 3, y0 + h + 3, 0xFF07030D);
        int pulse = lerpColor(GOLD, GOLD_HI, 0.5F + 0.5F * Mth.sin(t * 0.08F));
        g.fill(x0 - 2, y0 - 2, x0 + w + 2, y0 + h + 2, pulse);
        g.fill(x0, y0, x0 + w, y0 + h, BG);
        g.fillGradient(x0, y0, x0 + w, y0 + h, BG2, BG);
        g.fill(x0 + 3, y0 + 3, x0 + w - 3, y0 + 4, INNER);
        g.fill(x0 + 3, y0 + h - 4, x0 + w - 3, y0 + h - 3, INNER);
        corner(g, x0 - 2, y0 - 2, pulse);
        corner(g, x0 + w + 1, y0 - 2, pulse);
        corner(g, x0 - 2, y0 + h + 1, pulse);
        corner(g, x0 + w + 1, y0 + h + 1, pulse);
        // Motas de luz que suben por la placa
        for (float[] m : motes) {
            float yy = (m[1] - t * m[2]) % 1F;
            if (yy < 0) yy += 1F;
            int mx = x0 + 6 + (int) ((m[0] + 0.02F * Mth.sin(t * 0.05F + m[3])) * (w - 12));
            int my = y0 + 6 + (int) (yy * (h - 12));
            int a = (int) (110 + 100 * Mth.sin(t * 0.1F + m[3]));
            g.fill(mx, my, mx + 1, my + 1, (Mth.clamp(a, 0, 255) << 24) | 0xFFE7A0);
        }
        // Retrato
        int pw = portraitW();
        if (pw > 0) {
            int px0 = x0 + 10, py0 = y0 + 10, px1 = px0 + pw, py1 = y0 + h - 10;
            g.fill(px0 - 1, py0 - 1, px1 + 1, py1 + 1, GOLD);
            g.fillGradient(px0, py0, px1, py1, 0xFF3B2160, 0xFF120922);
            // halo de luz detrás (anillos macizos de más claro a más oscuro)
            int cx = (px0 + px1) / 2, cy = py0 + (py1 - py0) * 2 / 5;
            for (int r = 34; r > 0; r -= 4) {
                float k = r / 34F;
                int col = lerpColor(0xFFFFE6A8, 0xFF3B2160, k);
                ring(g, cx, cy, r, col, px0, py0, px1, py1);
            }
            Minecraft mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
            if (e instanceof KnightEntity k) {
                g.enableScissor(px0, py0, px1, py1);
                KnightRenderer.inGui = true;
                try {
                    int scale = (int) ((py1 - py0) * 0.92F / 2.9F);
                    // De tres cuartos y quieto (siguiendo al ratón, con el ratón en una esquina se veía tumbado)
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, py1 - 4, scale, 14F + 4F * Mth.sin(t * 0.03F), -6F, k);
                } finally {
                    KnightRenderer.inGui = false;
                }
                g.disableScissor();
            }
        }
        // Nombre, título y línea de oro
        int tx = x0 + 10 + (pw > 0 ? pw + 12 : 4);
        int tw = textW();
        g.pose().pushPose();
        g.pose().translate(tx, y0 + 10, 0);
        g.pose().scale(1.7F, 1.7F, 1F);
        g.drawString(font, Component.literal("AURELION").withStyle(Style.EMPTY.withFont(CINZEL).withBold(true)), 1, 1, 0xFF5A3A08, false);
        g.drawString(font, Component.literal("AURELION").withStyle(Style.EMPTY.withFont(CINZEL).withBold(true)), 0, 0, GOLD_HI, false);
        g.pose().popPose();
        String sub = talk.home() ? "Guardián del santuario · The Void" : "Caballero de Luz · Custodio del umbral";
        g.drawString(font, sub, tx, y0 + 30, SUB, false);
        g.fill(tx, y0 + 42, tx + tw, y0 + 43, GOLD);
        g.fill(tx, y0 + 43, tx + tw / 2, y0 + 44, 0xFF6E5420);
        // El texto que se va escribiendo
        String vis = text.substring(0, Math.min(text.length(), (int) shown));
        List<FormattedCharSequence> ls = font.split(Component.literal(vis), Math.max(80, tw));
        int ty = y0 + 50;
        for (FormattedCharSequence l : ls) {
            g.drawString(font, l, tx, ty, TEXT, false);
            ty += 10;
        }
        // Respuestas (cuando acaba de escribir)
        int oy = y0 + 54 + lines().size() * 10;
        boolean done = shown >= text.length();
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            int bx0 = tx, by0 = oy + i * 17, bx1 = tx + tw, by1 = by0 + 15;
            boolean hover = done && mouseX >= bx0 && mouseX < bx1 && mouseY >= by0 && mouseY < by1;
            int bg = !done ? 0xFF1A0F2A : hover ? OPT_HI : OPT;
            g.fill(bx0, by0, bx1, by1, bg);
            g.fill(bx0, by0, bx0 + 2, by1, hover ? GOLD_HI : GOLD);
            String label = (i + 1) + "  " + o.label();
            String cut = font.plainSubstrByWidth(label, tw - 14);
            if (!cut.equals(label)) cut = cut.substring(0, Math.max(0, cut.length() - 1)) + "…";
            g.drawString(font, cut, bx0 + 8, by0 + 4, !done ? 0xFF6A5A8A : hover ? GOLD_HI : TEXT, false);
            if (hover) g.drawString(font, "›", bx1 - 9, by0 + 4, GOLD_HI, false);
        }
        if (!done && (age / 10) % 2 == 0) g.drawString(font, "▼", x0 + w - 14, y0 + h - 13, GOLD, false);
        super.render(g, mouseX, mouseY, partial);
    }

    private static void corner(GuiGraphics g, int x, int y, int col) {
        g.fill(x - 3, y, x + 4, y + 1, col);
        g.fill(x, y - 3, x + 1, y + 4, col);
        g.fill(x - 1, y - 1, x + 2, y + 2, col);
    }

    /** Un anillo macizo (relleno) de radio r, recortado al retrato. */
    private static void ring(GuiGraphics g, int cx, int cy, int r, int col, int x0, int y0, int x1, int y1) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            int yy = cy + dy;
            if (yy < y0 || yy >= y1) continue;
            g.fill(Math.max(x0, cx - dx), yy, Math.min(x1, cx + dx), yy + 1, col);
        }
    }

    private static int lerpColor(int a, int b, float k) {
        k = Mth.clamp(k, 0F, 1F);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * k) << 16) | ((int) (ag + (bg - ag) * k) << 8) | (int) (ab + (bb - ab) * k);
    }

    // ------------------------------------------------------------------------------------------- entrada

    private boolean pick(int i) {
        if (i < 0 || i >= options.size()) return false;
        if (shown < text.length()) {
            shown = text.length();
            return true;
        }
        Option o = options.get(i);
        click(1.4F + i * 0.15F);
        o.action().run();
        return true;
    }

    /** La prueba en el juego (BossTestClient): pulsa la respuesta i (desde 0) aunque no haya acabado de escribir. */
    public void testPress(int i) {
        shown = text.length();
        pick(i);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (shown < text.length()) {
            shown = text.length();
            return true;
        }
        int w = panelW(), h = panelH();
        int x0 = (width - w) / 2, y0 = height - h - 14;
        int tx = x0 + 10 + (portraitW() > 0 ? portraitW() + 12 : 4);
        int oy = y0 + 54 + lines().size() * 10;
        for (int i = 0; i < options.size(); i++) {
            int by0 = oy + i * 17;
            if (mx >= tx && mx < tx + textW() && my >= by0 && my < by0 + 15) return pick(i);
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) return pick(key - GLFW.GLFW_KEY_1);
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            if (shown < text.length()) {
                shown = text.length();
                return true;
            }
            return pick(0);
        }
        return super.keyPressed(key, scan, mods);
    }
}
