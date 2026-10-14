package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;
import net.tierrasfantasticas.tfclient.vfx.VfxLayers;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;
import org.joml.Matrix4f;

/**
 * /tf boss: la ventana del staff para los jefes. A la izquierda los jefes; en el centro, el elegido en 3D (anda en su
 * animación de reposo; se gira arrastrando) con su nombre y los botones (poner su grieta donde estás, probarlo delante y
 * guardar); a la derecha, DIFICULTAD (presets de Fácil a Demencial y cada valor con − y +; mayúsculas = pasos grandes)
 * y GRIETAS (las puestas: ir, editar su jefe y dificultad, reiniciar, quitar). Todo con su sitio calculado en
 * {@link #layout} para que nada se monte encima de nada a ningún tamaño (se comprueba con
 * tools/bosses/sim_gui.py). Colores macizos, sin transparencias.
 */
@OnlyIn(Dist.CLIENT)
public class BossScreen extends Screen {
    // Colores (opacos)
    static final int BG = 0xFF14161C, PANEL = 0xFF191C23, HEAD = 0xFF1F232C, LINE = 0xFF2C313D, CARD = 0xFF1F232B,
            CARD_HOVER = 0xFF282D37, TEXT = 0xFFEDEFF4, MUTED = 0xFF9AA0AE, DIM = 0xFF6B7180, STAGE_TOP = 0xFF0D0F13,
            STAGE_BOT = 0xFF1A1E26, GOLD = 0xFFF2C14F, RED = 0xFFE0565B;
    private static final ResourceLocation[] TITLE_FONT = new ResourceLocation[12];

    static {
        for (int i = 0; i < 12; i++) TITLE_FONT[i] = new ResourceLocation(TFClient.MOD_ID, "titanone_" + (i + 1));
    }

    private final List<String> bosses = new ArrayList<>();
    private final Map<String, Difficulty> chosen = new HashMap<>();
    private List<BossNet.RiftInfo> rifts = List.of();
    private String selected;
    private Difficulty editing = Difficulty.NORMAL;
    /** Grieta que se está editando (null = la dificultad del jefe). */
    private BossNet.RiftInfo editRift;
    private int tab;
    private int riftScroll, listScroll;
    private String notice = "";
    private long noticeAt;
    private UUID confirmRemove;
    private long confirmAt;
    // Vista previa
    private final VfxLayers previewLayers = new VfxLayers();
    private float spin = 20F;
    private boolean dragging;
    private double dragX;
    private int ticks;
    /** La ayuda a enseñar este fotograma (al pasar por encima de un valor). */
    private String tip;

    public BossScreen(BossNet.Open m) {
        super(Component.literal("Jefes"));
        update(m);
    }

    void update(BossNet.Open m) {
        bosses.clear();
        bosses.addAll(m.bosses());
        for (int i = 0; i < m.bosses().size(); i++) chosen.put(m.bosses().get(i), m.chosen().get(i));
        rifts = m.rifts();
        if (selected == null || !bosses.contains(selected)) select(bosses.isEmpty() ? null : bosses.get(0));
        if (editRift != null) {
            BossNet.RiftInfo again = null;
            for (BossNet.RiftInfo r : rifts) if (r.id().equals(editRift.id())) again = r;
            editRift = again;
        }
        if (!m.notice().isEmpty()) {
            notice = m.notice();
            noticeAt = System.currentTimeMillis();
        }
    }

    private void select(String id) {
        selected = id;
        editing = id == null ? Difficulty.NORMAL : chosen.getOrDefault(id, Difficulty.NORMAL);
        previewLayers.clear();
        spin = 20F;
    }

    private SkillDefs.BossInfo info() {
        SkillDefs.ClassDef c = selected == null ? null : SkillDefs.bosses().get(selected);
        return c == null ? null : c.bossInfo;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
        if (!dragging) spin += 0.6F;
    }

    // ------------------------------------------------------------------------------------------- disposición

    /** Dónde va cada cosa (en píxeles de interfaz). Igual que tools/bosses/sim_gui.py. */
    static final class Layout {
        int px, py, pw, ph;
        int headH = 28;
        int bx, by, bw, bh;
        int listX, listW, midX, midW, rightX, rightW;
        int stageY, stageH, nameY, btnY;
        int footY;
        // Derecha
        int tabsY, chipsY, chipRows, chipsPerRow, rowsY, rowH, bannerY;
        static final int GAP = 8, PAD = 8, FOOT = 14, BTN = 16, CHIP = 14, TAB = 16, ROWS = 9, CARD_H = 32, RIFT_H = 32;
    }

    static Layout layout(int w, int h, boolean banner) {
        Layout L = new Layout();
        L.pw = Math.min(w - 12, 620);
        L.ph = Math.min(h - 12, 340);
        L.px = (w - L.pw) / 2;
        L.py = (h - L.ph) / 2;
        L.bx = L.px + Layout.PAD;
        L.by = L.py + L.headH + 4;
        L.bw = L.pw - 2 * Layout.PAD;
        L.bh = L.ph - L.headH - 4 - Layout.FOOT - Layout.PAD;
        L.footY = L.py + L.ph - Layout.FOOT - 2;
        L.listW = clamp((int) (L.bw * 0.22), 100, 140);
        L.midW = clamp((int) (L.bw * 0.32), 120, 210);
        L.rightW = L.bw - L.listW - L.midW - 2 * Layout.GAP;
        if (L.rightW < 170) {
            L.midW = Math.max(110, L.midW - (170 - L.rightW));
            L.rightW = L.bw - L.listW - L.midW - 2 * Layout.GAP;
        }
        L.listX = L.bx;
        L.midX = L.listX + L.listW + Layout.GAP;
        L.rightX = L.midX + L.midW + Layout.GAP;
        // Centro: escenario, nombre y tres botones
        int buttons = 3 * Layout.BTN + 2 * 3;
        L.btnY = L.by + L.bh - buttons;
        L.nameY = L.btnY - 26;
        L.stageY = L.by;
        L.stageH = L.nameY - 4 - L.stageY;
        // Derecha: pestañas, (aviso de edición), presets y filas
        L.tabsY = L.by;
        int y = L.tabsY + Layout.TAB + 5;
        if (banner) {
            L.bannerY = y;
            y += 13;
        }
        L.chipsY = y;
        L.chipsPerRow = L.rightW >= 5 * 56 + 4 * 3 ? 5 : 3;
        L.chipRows = L.chipsPerRow == 5 ? 1 : 2;
        y += L.chipRows * Layout.CHIP + (L.chipRows - 1) * 3 + 6;
        L.rowsY = y;
        L.rowH = Math.max(11, Math.min(20, (L.by + L.bh - y) / Layout.ROWS));
        return L;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------------------------------- dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        tip = null;
        Layout L = layout(width, height, editRift != null && tab == 0);
        // Panel
        g.fill(L.px - 1, L.py - 1, L.px + L.pw + 1, L.py + L.ph + 1, LINE);
        g.fill(L.px, L.py, L.px + L.pw, L.py + L.ph, PANEL);
        g.fill(L.px, L.py, L.px + L.pw, L.py + L.headH, HEAD);
        g.fill(L.px, L.py + L.headH, L.px + L.pw, L.py + L.headH + 1, LINE);
        SkillDefs.BossInfo bi = info();
        int accent = 0xFF000000 | (bi != null ? bi.color : 0xB45CF0);
        g.fill(L.px, L.py, L.px + L.pw, L.py + 2, accent);
        title(g, "JEFES", L.px + 10, L.py + 8, 1.4F, 0xFFFFFFFF);
        int tw = titleWidth("JEFES", 1.4F);
        g.drawString(font, fit("Incursiones: elige el jefe, su dificultad y pon su grieta", L.pw - tw - 50), L.px + 16 + tw,
                L.py + 11, MUTED, false);
        drawButton(g, L.px + L.pw - 20, L.py + 7, 14, 14, "✕", mx, my, 0xFF3A3F4B, true);

        drawList(g, L, mx, my);
        drawStage(g, L, mx, my, partial, bi);
        drawRight(g, L, mx, my);

        // Pie: lo último que ha pasado (unos segundos)
        if (!notice.isEmpty() && System.currentTimeMillis() - noticeAt < 6000) {
            g.drawString(font, fit(notice, L.pw - 20), L.px + 10, L.footY + 3, GOLD, false);
        } else {
            g.drawString(font, fit("Golpear la grieta despierta al jefe · La vida crece con cada jugador más", L.pw - 20),
                    L.px + 10, L.footY + 3, DIM, false);
        }
        super.render(g, mx, my, partial);
        if (tip != null) g.renderTooltip(font, font.split(Component.literal(tip), 190), mx, my);
    }

    private void drawList(GuiGraphics g, Layout L, int mx, int my) {
        int y = L.by;
        int visible = L.bh / Layout.CARD_H;
        listScroll = clamp(listScroll, 0, Math.max(0, bosses.size() - visible));
        for (int i = listScroll; i < bosses.size() && i < listScroll + visible; i++) {
            String id = bosses.get(i);
            SkillDefs.ClassDef c = SkillDefs.bosses().get(id);
            if (c == null || c.bossInfo == null) continue;
            int top = y + (i - listScroll) * Layout.CARD_H;
            boolean sel = id.equals(selected);
            boolean hover = in(mx, my, L.listX, top, L.listW, Layout.CARD_H - 3);
            int color = 0xFF000000 | c.bossInfo.color;
            g.fill(L.listX, top, L.listX + L.listW, top + Layout.CARD_H - 3, sel ? mix(CARD, color, 0.22F) : hover ? CARD_HOVER : CARD);
            g.fill(L.listX, top, L.listX + 3, top + Layout.CARD_H - 3, color);
            if (sel) outline(g, L.listX, top, L.listW, Layout.CARD_H - 3, color);
            Difficulty d = chosen.getOrDefault(id, Difficulty.NORMAL);
            g.fill(L.listX + L.listW - 9, top + 5, L.listX + L.listW - 4, top + 10, 0xFF000000 | d.color());
            g.drawString(font, fit(c.bossInfo.name, L.listW - 22), L.listX + 8, top + 6, TEXT, false);
            g.drawString(font, fit(c.bossInfo.title, L.listW - 12), L.listX + 8, top + 17, MUTED, false);
        }
    }

    private void drawStage(GuiGraphics g, Layout L, int mx, int my, float partial, SkillDefs.BossInfo bi) {
        int x = L.midX, y = L.stageY, w = L.midW, h = L.stageH;
        int accent = 0xFF000000 | (bi != null ? bi.color : 0xB45CF0);
        g.fillGradient(x, y, x + w, y + h, STAGE_TOP, STAGE_BOT);
        // Suelo con la luz del jefe
        int floor = y + h - 14;
        for (int i = 0; i < 6; i++) {
            int rw = w / 2 - 6 - i * 8;
            if (rw <= 0) break;
            g.fill(x + w / 2 - rw, floor - 2 + i, x + w / 2 + rw, floor - 1 + i, mix(STAGE_BOT, accent, 0.10F + i * 0.05F));
        }
        outline(g, x, y, w, h, LINE);
        if (bi != null && bi.model != null) {
            VfxModel m = SkillMobs.model(bi.model);
            if (m != null) {
                g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
                drawModel(g, m, x + w / 2F, floor, w - 12, h - 26, partial);
                g.disableScissor();
            }
        }
        g.drawString(font, fit("Arrastra para girar", w - 10), x + 5, y + 4, DIM, false);
        // Nombre y título
        if (bi != null) {
            title(g, fitTitle(bi.name, w - 4, 1.0F), x + 2, L.nameY + 2, 1.0F, accent);
            g.drawString(font, fit(bi.title, w - 4), x + 2, L.nameY + 15, MUTED, false);
        }
        // Botones
        int by = L.btnY;
        if (editRift != null) {
            drawButton(g, x, by, w, Layout.BTN, "GUARDAR GRIETA", mx, my, accent, true);
            drawButton(g, x, by + Layout.BTN + 3, w, Layout.BTN, "REINICIAR GRIETA", mx, my, 0xFF3A3F4B, true);
            drawButton(g, x, by + 2 * (Layout.BTN + 3), w, Layout.BTN, "DEJAR DE EDITAR", mx, my, 0xFF3A3F4B, true);
        } else {
            drawButton(g, x, by, w, Layout.BTN, "PONER GRIETA AQUÍ", mx, my, accent, true);
            drawButton(g, x, by + Layout.BTN + 3, w, Layout.BTN, "PROBAR DELANTE", mx, my, 0xFF3A3F4B, true);
            boolean dirty = selected != null && !editing.equals(chosen.get(selected));
            drawButton(g, x, by + 2 * (Layout.BTN + 3), w, Layout.BTN, dirty ? "GUARDAR CAMBIOS" : "GUARDADO", mx, my,
                    dirty ? GOLD : 0xFF2B3039, dirty);
        }
    }

    /** El jefe en 3D, de frente, con los pies en (cx, floorY) y cabiendo entero en w×h (con lo que se inclina). */
    private void drawModel(GuiGraphics g, VfxModel m, float cx, float floorY, float w, float h, float partial) {
        Matrix4f[] mats = new Matrix4f[m.bones.length];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        previewLayers.pose(m, ticks + partial, 0F, 0F, mats);
        boolean[] hidden = previewHidden(m);
        // El tamaño se mide una vez (en la primera pose) para que no «respire» con la animación
        float[] bb = bounds.computeIfAbsent(selected, k -> bounds(m, mats, hidden));
        float r = Math.max(bb[0], 0.25F);
        float sin = (float) Math.sin(Math.toRadians(TILT)), cos = (float) Math.cos(Math.toRadians(TILT));
        float bottom = bb[1] * cos - r * sin, top = bb[2] * cos + r * sin;
        float scale = 0.94F * Math.min(w / (2F * r), h / Math.max(0.5F, top - bottom));
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, floorY + bottom * scale, 220);
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(TILT));
        // El frente de los modelos de ModelEngine es -z: girado 180° mira a la pantalla
        ps.mulPose(Axis.YP.rotationDegrees(180F + spin + (dragging ? 0 : partial * 0.6F)));
        Lighting.setupForEntityInInventory();
        ModelDraw.draw(m, mats, hidden, ps, g.bufferSource(), LightTexture.FULL_BRIGHT, 0xFFFFFF, ticks, 1F);
        g.flush();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    private static final float TILT = 10F;
    private final Map<String, float[]> bounds = new HashMap<>();

    /** {radio horizontal, y más baja, y más alta} (bloques) de lo que se ve del modelo en esa pose. */
    private static float[] bounds(VfxModel m, Matrix4f[] mats, boolean[] hidden) {
        float rad = 0F, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        org.joml.Vector3f v = new org.joml.Vector3f();
        for (int i = 0; i < m.bones.length; i++) {
            if (m.bones[i].hidden || hiddenUp(m, i, hidden)) continue;
            VfxModel.Bone b = m.bones[i];
            for (int q = 0; q < b.quadCount; q++) {
                int o = q * VfxModel.QUAD + 4;
                for (int k = 0; k < 4; k++) {
                    v.set(b.quads[o + k * 5], b.quads[o + k * 5 + 1], b.quads[o + k * 5 + 2]);
                    mats[i].transformPosition(v);
                    rad = Math.max(rad, (float) Math.sqrt(v.x * v.x + v.z * v.z) / 16F);
                    lo = Math.min(lo, v.y / 16F);
                    hi = Math.max(hi, v.y / 16F);
                }
            }
        }
        if (lo > hi) return new float[] {1F, 0F, 2F};
        return new float[] {rad, lo, hi};
    }

    private static boolean hiddenUp(VfxModel m, int b, boolean[] hidden) {
        for (int p = b; p >= 0; p = m.bones[p].parent) if (hidden[p]) return true;
        return false;
    }

    /** Lo que el jefe lleva escondido al luchar (las estelas, las piezas de la escena): la vista previa sin eso. */
    private boolean[] previewHidden(VfxModel m) {
        SkillDefs.ClassDef c = SkillDefs.bosses().get(selected);
        boolean[] h = new boolean[m.bones.length];
        if (c == null || c.bossInfo == null) return h;
        for (String part : c.bossInfo.previewHide) {
            int b = m.bone(part);
            if (b >= 0) h[b] = true;
        }
        return h;
    }

    private void drawRight(GuiGraphics g, Layout L, int mx, int my) {
        int x = L.rightX, w = L.rightW;
        int tw = (w - 3) / 2;
        drawTab(g, x, L.tabsY, tw, "DIFICULTAD", tab == 0, mx, my);
        drawTab(g, x + tw + 3, L.tabsY, w - tw - 3, "GRIETAS (" + rifts.size() + ")", tab == 1, mx, my);
        if (tab == 0) drawDifficulty(g, L, mx, my);
        else drawRifts(g, L, mx, my);
    }

    private void drawDifficulty(GuiGraphics g, Layout L, int mx, int my) {
        int x = L.rightX, w = L.rightW;
        if (editRift != null) {
            g.fill(x, L.bannerY, x + w, L.bannerY + 11, mix(PANEL, GOLD, 0.18F));
            g.drawString(font, fit("Editando la grieta de " + (int) editRift.x() + ", " + (int) editRift.y() + ", " + (int) editRift.z(), w - 6),
                    x + 3, L.bannerY + 2, GOLD, false);
        }
        // Presets
        for (int i = 0; i < Difficulty.PRESETS.size(); i++) {
            int[] r = chipRect(L, i);
            Difficulty.Preset p = Difficulty.PRESETS.get(i);
            boolean on = p.id().equals(editing.preset()) && p.values().sameValues(editing);
            boolean hover = in(mx, my, r[0], r[1], r[2], r[3]);
            int c = 0xFF000000 | p.color();
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? c : hover ? mix(CARD, c, 0.35F) : mix(CARD, c, 0.16F));
            outline(g, r[0], r[1], r[2], r[3], on ? 0xFFFFFFFF : mix(CARD, c, 0.6F));
            String label = fit(p.name(), r[2] - 4);
            g.drawString(font, label, r[0] + (r[2] - font.width(label)) / 2, r[1] + 3, on ? 0xFF101216 : TEXT, false);
        }
        // Valores
        SkillDefs.BossInfo bi = info();
        for (int i = 0; i < Layout.ROWS; i++) {
            int y = L.rowsY + i * L.rowH;
            if (in(mx, my, x, y, w, L.rowH - 1)) g.fill(x, y, x + w, y + L.rowH - 1, CARD_HOVER);
            else if (i % 2 == 0) g.fill(x, y, x + w, y + L.rowH - 1, CARD);
            int ty = y + (L.rowH - 9) / 2 + 1;
            int[] minus = stepRect(L, i, false), plus = stepRect(L, i, true);
            int valueRight = minus[0] - 4;
            String value = value(i, bi);
            int vw = font.width(value);
            g.drawString(font, fit(LABELS[i], valueRight - vw - x - 8), x + 4, ty, MUTED, false);
            g.drawString(font, value, valueRight - vw, ty, TEXT, false);
            drawButton(g, minus[0], minus[1], minus[2], minus[3], "−", mx, my, 0xFF343946, true);
            drawButton(g, plus[0], plus[1], plus[2], plus[3], "+", mx, my, 0xFF343946, true);
        }
        // Lo que cada valor es (al pasar el ratón por encima; se dibuja al final, encima de todo)
        for (int i = 0; i < Layout.ROWS; i++) {
            int y = L.rowsY + i * L.rowH;
            if (in(mx, my, x, y, stepRect(L, i, false)[0] - 2 - x, L.rowH - 1)) tip = HELP[i];
        }
        // Debajo, si queda sitio: cuánta vida tendrá en grupo y qué hace el jefe (solo si cabe entero)
        int iy = L.rowsY + Layout.ROWS * L.rowH + 6, bottom = L.by + L.bh;
        if (bi == null || bottom - iy < 9) return;
        String group = "Con 4 jugadores: " + grouped(Math.round(editing.healthFor(bi.health, 4))) + " de vida";
        g.drawString(font, fit(group, w - 4), x + 2, iy, GOLD, false);
        iy += 12;
        for (String text : new String[] {bi.desc, String.join(" · ", bi.attacks)}) {
            List<FormattedCharSequence> lines = font.split(Component.literal(text), w - 4);
            if (text.isEmpty() || iy + lines.size() * 10 - 1 > bottom) continue;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 2, iy, DIM, false);
                iy += 10;
            }
            break;
        }
    }

    static final String[] LABELS = {"Vida", "Daño", "Velocidad", "Ritmo de ataque", "Inteligencia", "Armadura",
            "Vida por jugador", "Recarga de la grieta", "Monedas por jugador"};
    static final String[] HELP = {
            "Vida del jefe con un jugador (cada jugador más que haya al despertarlo le suma lo de «Vida por jugador»).",
            "Daño de sus golpes.",
            "Lo rápido que anda.",
            "Cada cuánto ataca: más alto, menos espera entre ataques (las animaciones no se aceleran).",
            "Su cerebro táctico: 0 % = como el pack; más alto = elige el mejor ataque para cada momento, castiga si te curas, "
                    + "te cubres o te alejas, te rodea, te sigue al atacar, aprende lo que esquivas y se enfurece al bajar de vida.",
            "Puntos de armadura (como la de un jugador: de 0 a 30).",
            "Cuánta vida suma cada jugador más de la incursión (100 % = el doble con 2 jugadores).",
            "Lo que tarda la grieta en volver a estar lista después de que caiga el jefe.",
            "Lo que se lleva cada jugador que le haya hecho daño cuando cae."};

    private String value(int i, SkillDefs.BossInfo bi) {
        double hp = bi == null ? 0 : bi.health * editing.health();
        double dmg = bi == null ? 0 : bi.damage * editing.damage();
        return switch (i) {
            case 0 -> grouped(Math.round(hp));
            case 1 -> trim(dmg);
            case 2 -> "×" + trim(editing.speed());
            case 3 -> "×" + trim(editing.rhythm());
            case 4 -> Math.round(editing.smart() * 100) + " %";
            case 5 -> String.valueOf((int) Math.round(editing.armor()));
            case 6 -> "+" + Math.round(editing.perPlayer() * 100) + " %";
            case 7 -> editing.respawnMin() >= 60 ? (editing.respawnMin() / 60) + " h " + (editing.respawnMin() % 60) + " min"
                    : editing.respawnMin() + " min";
            default -> grouped(editing.coins());
        };
    }

    private void drawRifts(GuiGraphics g, Layout L, int mx, int my) {
        int x = L.rightX, w = L.rightW;
        int top = L.tabsY + Layout.TAB + 5;
        drawButton(g, x, top, w, Layout.BTN, "QUITAR JEFES DE PRUEBA", mx, my, 0xFF3A3F4B, true);
        int y0 = top + Layout.BTN + 5;
        int visible = Math.max(1, (L.by + L.bh - y0) / Layout.RIFT_H);
        riftScroll = clamp(riftScroll, 0, Math.max(0, rifts.size() - visible));
        if (rifts.isEmpty()) {
            g.drawString(font, fit("Aún no hay grietas. Elige un jefe y pulsa", w - 4), x + 2, y0 + 4, MUTED, false);
            g.drawString(font, fit("«PONER GRIETA AQUÍ».", w - 4), x + 2, y0 + 15, MUTED, false);
            return;
        }
        for (int i = riftScroll; i < rifts.size() && i < riftScroll + visible; i++) {
            BossNet.RiftInfo r = rifts.get(i);
            int y = y0 + (i - riftScroll) * Layout.RIFT_H;
            SkillDefs.ClassDef c = SkillDefs.bosses().get(r.boss());
            int color = 0xFF000000 | (c != null && c.bossInfo != null ? c.bossInfo.color : 0x888888);
            boolean edit = editRift != null && editRift.id().equals(r.id());
            g.fill(x, y, x + w, y + Layout.RIFT_H - 3, edit ? mix(CARD, GOLD, 0.15F) : CARD);
            g.fill(x, y, x + 3, y + Layout.RIFT_H - 3, color);
            int[][] b = riftButtons(L, i);
            int textW = b[0][0] - x - 10;
            String name = c != null && c.bossInfo != null ? c.bossInfo.name : r.boss();
            String diff = r.diff().label();
            String head = fit(name, textW - font.width(" · " + diff));
            g.drawString(font, head, x + 7, y + 4, TEXT, false);
            g.drawString(font, " · " + diff, x + 7 + font.width(head), y + 4, 0xFF000000 | r.diff().color(), false);
            String state = switch (r.state()) {
                case RiftEntity.READY -> "lista";
                case RiftEntity.WAKING -> "despertando";
                case RiftEntity.FIGHT -> "en incursión";
                default -> "vuelve en " + Rifts.time(r.left());
            };
            String where = (int) r.x() + ", " + (int) r.y() + ", " + (int) r.z() + (r.dist() >= 0 ? " · " + Math.round(r.dist()) + " m" : " · " + r.world());
            g.drawString(font, fit(where + " · " + state, textW), x + 7, y + 16, MUTED, false);
            boolean confirm = r.id().equals(confirmRemove) && System.currentTimeMillis() - confirmAt < 3000;
            drawButton(g, b[0][0], b[0][1], b[0][2], b[0][3], "IR", mx, my, 0xFF343946, true);
            drawButton(g, b[1][0], b[1][1], b[1][2], b[1][3], "✎", mx, my, edit ? GOLD : 0xFF343946, true);
            drawButton(g, b[2][0], b[2][1], b[2][2], b[2][3], confirm ? "?" : "✕", mx, my, confirm ? RED : 0xFF4A2F33, true);
        }
        if (rifts.size() > visible) {
            String more = (riftScroll + 1) + "-" + Math.min(rifts.size(), riftScroll + visible) + " de " + rifts.size();
            g.drawString(font, more, x + w - font.width(more) - 2, L.by + L.bh - 9, DIM, false);
        }
    }

    // ------------------------------------------------------------------------------------------- piezas

    /** [x, y, w, h] del preset i. */
    static int[] chipRect(Layout L, int i) {
        int per = L.chipsPerRow;
        int row = i / per, col = i % per;
        int inRow = row == L.chipRows - 1 ? Difficulty.PRESETS.size() - row * per : per;
        int cw = (L.rightW - (inRow - 1) * 3) / inRow;
        int x = L.rightX + col * (cw + 3);
        int w = col == inRow - 1 ? L.rightX + L.rightW - x : cw;
        return new int[] {x, L.chipsY + row * (Layout.CHIP + 3), w, Layout.CHIP};
    }

    /** [x, y, w, h] del − (o del +) de la fila i. */
    static int[] stepRect(Layout L, int i, boolean plus) {
        int s = Math.min(12, L.rowH - 2);
        int y = L.rowsY + i * L.rowH + (L.rowH - 1 - s) / 2;
        int x = L.rightX + L.rightW - (plus ? s + 2 : 2 * s + 5);
        return new int[] {x, y, s, s};
    }

    /** Botones IR, editar y quitar de la grieta i (en la página que se ve). */
    int[][] riftButtons(Layout L, int i) {
        int top = L.tabsY + Layout.TAB + 5 + Layout.BTN + 5;
        int y = top + (i - riftScroll) * Layout.RIFT_H + 7;
        int x3 = L.rightX + L.rightW - 16;
        return new int[][] {{x3 - 2 * 17 - 4, y, 20, 14}, {x3 - 17, y, 14, 14}, {x3, y, 14, 14}};
    }

    private void drawTab(GuiGraphics g, int x, int y, int w, String label, boolean on, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, Layout.TAB);
        g.fill(x, y, x + w, y + Layout.TAB, on ? 0xFF2C3240 : hover ? CARD_HOVER : CARD);
        if (on) g.fill(x, y + Layout.TAB - 2, x + w, y + Layout.TAB, 0xFF000000 | (info() != null ? info().color : 0xB45CF0));
        String t = fit(label, w - 6);
        g.drawString(font, t, x + (w - font.width(t)) / 2, y + 4, on ? TEXT : MUTED, false);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, int color, boolean enabled) {
        boolean hover = enabled && in(mx, my, x, y, w, h);
        int c = enabled ? (hover ? lighten(color, 0.18F) : color) : 0xFF2B3039;
        g.fill(x, y, x + w, y + h, c);
        g.fill(x, y + h - 1, x + w, y + h, lighten(c, -0.25F));
        g.fill(x, y, x + w, y + 1, lighten(c, 0.15F));
        int text = luminance(c) > 0.6F ? 0xFF101216 : 0xFFFFFFFF;
        String t = fit(label, w - 4);
        g.drawString(font, t, x + (w - font.width(t)) / 2, y + (h - 8) / 2, enabled ? text : MUTED, false);
    }

    private void title(GuiGraphics g, String text, float x, float y, float size, int color) {
        int gs = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        int px = clamp(Math.round(gs * size), 1, 12);
        Component c = Component.literal(text).setStyle(Style.EMPTY.withFont(TITLE_FONT[px - 1]));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float k = size * gs / px;
        g.pose().scale(k, k, 1F);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    private int titleWidth(String text, float size) {
        int gs = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        int px = clamp(Math.round(gs * size), 1, 12);
        return Math.round(font.width(Component.literal(text).setStyle(Style.EMPTY.withFont(TITLE_FONT[px - 1]))) * size * gs / px);
    }

    private String fitTitle(String s, int w, float size) {
        if (titleWidth(s, size) <= w) return s;
        while (s.length() > 1 && titleWidth(s + "…", size) > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** El texto recortado con «…» si no cabe en w. */
    private String fit(String s, int w) {
        if (s == null) return "";
        if (w <= 0) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int gg = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bb = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }

    static int lighten(int c, float k) {
        return k >= 0 ? mix(c, 0xFFFFFFFF, k) : mix(c, 0xFF000000, -k);
    }

    static float luminance(int c) {
        return (0.299F * ((c >> 16) & 255) + 0.587F * ((c >> 8) & 255) + 0.114F * (c & 255)) / 255F;
    }

    static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        return s.replace('.', ',');
    }

    static String grouped(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    // ------------------------------------------------------------------------------------------- ratón

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        Layout L = layout(width, height, editRift != null && tab == 0);
        if (in(mx, my, L.px + L.pw - 20, L.py + 7, 14, 14)) {
            onClose();
            return true;
        }
        // Lista de jefes
        int visible = L.bh / Layout.CARD_H;
        for (int i = listScroll; i < bosses.size() && i < listScroll + visible; i++) {
            int top = L.by + (i - listScroll) * Layout.CARD_H;
            if (in(mx, my, L.listX, top, L.listW, Layout.CARD_H - 3)) {
                if (!bosses.get(i).equals(selected)) {
                    editRift = null;
                    select(bosses.get(i));
                    click();
                }
                return true;
            }
        }
        // Escenario: girar
        if (in(mx, my, L.midX, L.stageY, L.midW, L.stageH)) {
            dragging = true;
            dragX = mx;
            return true;
        }
        // Botones del centro
        for (int k = 0; k < 3; k++) {
            if (!in(mx, my, L.midX, L.btnY + k * (Layout.BTN + 3), L.midW, Layout.BTN)) continue;
            click();
            if (editRift != null) {
                if (k == 0) send(BossNet.RIFT_SAVE, editRift.id());
                else if (k == 1) send(BossNet.RIFT_RESET, editRift.id());
                else editRift = null;
            } else if (k == 0) {
                send(BossNet.PLACE, null);
            } else if (k == 1) {
                send(BossNet.TEST, null);
            } else if (selected != null && !editing.equals(chosen.get(selected))) {
                send(BossNet.SAVE, null);
            }
            return true;
        }
        // Pestañas
        int tw = (L.rightW - 3) / 2;
        if (in(mx, my, L.rightX, L.tabsY, tw, Layout.TAB)) {
            tab = 0;
            click();
            return true;
        }
        if (in(mx, my, L.rightX + tw + 3, L.tabsY, L.rightW - tw - 3, Layout.TAB)) {
            tab = 1;
            click();
            send(BossNet.REFRESH, null);
            return true;
        }
        if (tab == 0) return clickDifficulty(L, mx, my) || super.mouseClicked(mx, my, button);
        return clickRifts(L, mx, my) || super.mouseClicked(mx, my, button);
    }

    private boolean clickDifficulty(Layout L, double mx, double my) {
        for (int i = 0; i < Difficulty.PRESETS.size(); i++) {
            int[] r = chipRect(L, i);
            if (in(mx, my, r[0], r[1], r[2], r[3])) {
                editing = Difficulty.PRESETS.get(i).values();
                click();
                return true;
            }
        }
        for (int i = 0; i < Layout.ROWS; i++) {
            int[] minus = stepRect(L, i, false), plus = stepRect(L, i, true);
            boolean big = hasShiftDown();
            if (in(mx, my, minus[0], minus[1], minus[2], minus[3])) {
                step(i, big ? -5 : -1);
                click();
                return true;
            }
            if (in(mx, my, plus[0], plus[1], plus[2], plus[3])) {
                step(i, big ? 5 : 1);
                click();
                return true;
            }
        }
        return false;
    }

    private boolean clickRifts(Layout L, double mx, double my) {
        int top = L.tabsY + Layout.TAB + 5;
        if (in(mx, my, L.rightX, top, L.rightW, Layout.BTN)) {
            click();
            send(BossNet.CLEAR_TESTS, null);
            return true;
        }
        int y0 = top + Layout.BTN + 5;
        int visible = Math.max(1, (L.by + L.bh - y0) / Layout.RIFT_H);
        for (int i = riftScroll; i < rifts.size() && i < riftScroll + visible; i++) {
            BossNet.RiftInfo r = rifts.get(i);
            int[][] b = riftButtons(L, i);
            if (in(mx, my, b[0][0], b[0][1], b[0][2], b[0][3])) {
                click();
                send(BossNet.RIFT_TP, r.id());
                return true;
            }
            if (in(mx, my, b[1][0], b[1][1], b[1][2], b[1][3])) {
                click();
                editRift = r;
                selected = r.boss();
                editing = r.diff();
                previewLayers.clear();
                tab = 0;
                return true;
            }
            if (in(mx, my, b[2][0], b[2][1], b[2][2], b[2][3])) {
                click();
                if (r.id().equals(confirmRemove) && System.currentTimeMillis() - confirmAt < 3000) {
                    if (editRift != null && editRift.id().equals(r.id())) editRift = null;
                    send(BossNet.RIFT_REMOVE, r.id());
                    confirmRemove = null;
                } else {
                    confirmRemove = r.id();
                    confirmAt = System.currentTimeMillis();
                    notice = "Pulsa otra vez la ✕ para quitar esa grieta";
                    noticeAt = System.currentTimeMillis();
                }
                return true;
            }
        }
        return false;
    }

    /** Cambia el valor de la fila i en «n» pasos (uno o cinco). */
    private void step(int i, int n) {
        Difficulty d = editing;
        double h = d.health(), dm = d.damage(), sp = d.speed(), rh = d.rhythm(), ar = d.armor(), pp = d.perPlayer();
        double sm = d.smart();
        int rs = d.respawnMin(), co = d.coins();
        switch (i) {
            case 0 -> h = round(h + 0.05 * n);
            case 1 -> dm = round(dm + 0.05 * n);
            case 2 -> sp = round(sp + 0.02 * n);
            case 3 -> rh = round(rh + 0.05 * n);
            case 4 -> sm = round(sm + 0.05 * n);
            case 5 -> ar += n;
            case 6 -> pp = round(pp + 0.05 * n);
            case 7 -> rs += n;
            default -> co += 50 * n;
        }
        editing = new Difficulty(d.preset(), h, dm, sp, rh, ar, pp, rs, co, sm).clamped();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            spin += (float) (mx - dragX) * 1.5F;
            dragX = mx;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        Layout L = layout(width, height, editRift != null && tab == 0);
        if (in(mx, my, L.listX, L.by, L.listW, L.bh)) {
            listScroll -= (int) Math.signum(delta);
            return true;
        }
        if (tab == 1 && in(mx, my, L.rightX, L.by, L.rightW, L.bh)) {
            riftScroll -= (int) Math.signum(delta);
            return true;
        }
        if (tab == 0) {
            for (int i = 0; i < Layout.ROWS; i++) {
                if (in(mx, my, L.rightX, L.rowsY + i * L.rowH, L.rightW, L.rowH)) {
                    step(i, (int) Math.signum(delta) * (hasShiftDown() ? 5 : 1));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void send(int kind, UUID rift) {
        BossNet.CHANNEL.sendToServer(new BossNet.Action(kind, selected, editing, rift));
        if (kind == BossNet.SAVE || kind == BossNet.PLACE || kind == BossNet.TEST) {
            if (selected != null) chosen.put(selected, editing);
        }
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1F, 0.6F));
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, BG);
    }
}
