package net.tierrasfantasticas.tfclient.vacio;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.client.TFMenuPaint;
import org.lwjgl.glfw.GLFW;

/**
 * El diálogo con Caelthar, el Caballero Sacro (hasta la 1.3.88, Aurelion; lo pidió el dueño: «al seleccionarlo hazle un
 * diseño bonito, que salude, su nombre, qué es él, un breve lore del Void, las grietas... y que le pida si quiere viajar
 * al Void; la segunda vez, solo le saluda por su nombre»). Hay páginas de lore (qué es el Void, qué son las grietas, qué
 * hace él); el del santuario del vacío, en vez de llevarte, te devuelve a donde estabas.
 *
 * <p>1.3.92, rehecho (el dueño: «su cuadro de diálogo es una mierda, va lento, hay texto mal dibujado como su nombre, y
 * tiene sonidos de amatista: quita ese sonido, hazle rework»). Ahora, como una escena de cine:
 * <ul>
 *   <li>El mundo se oscurece abajo (y un poco arriba) y se sigue viendo al caballero, con su espada, en medio.</li>
 *   <li>Una placa de cristal oscuro con las esquinas redondeadas y un hilo de oro (en píxeles reales de pantalla:
 *   {@link TFMenuPaint}, nítida con cualquier escala de interfaz), que entra subiendo y encendiéndose.</li>
 *   <li>Su nombre en Cinzel dorada con contorno, rasterizada al tamaño real de la pantalla (font/cinzel_N: un píxel
 *   de letra por píxel de pantalla). Antes iba en negrita a ×1,7: la negrita de una letra TTF se dibuja dos veces
 *   desplazada y, escalada, se veía corrida y borrosa. A los lados, dos hilos de oro que se apagan.</li>
 *   <li>El texto se escribe a {@link #CPS} letras por segundo (antes 34, por ticks: a saltos; 55 hasta la 1.3.97: el
 *   dueño, «las letras de los diálogos van lentas aún») y entra con un fundido; clic, espacio o intro lo muestran
 *   entero.</li>
 *   <li>Las respuestas, en pastillas redondeadas con su número, que se encienden con el ratón (o 1, 2, 3).</li>
 *   <li>Sin el retrato (el caballero ya está delante) y sin sonidos de amatista: los suaves del TF Pad (al abrir, al
 *   pasar por una respuesta y al elegirla).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class KnightScreen extends Screen {
    /** Letras por segundo del texto que se escribe. */
    private static final float CPS = 120F;
    private static final int GOLD = 0xFFE9C46E, GOLD_HI = 0xFFFFE7A0, GOLD_DARK = 0xFF3A2306, TEXT = 0xFFF1ECFF, SUB = 0xFFB9A6E8,
            HINT = 0xFF8C7BB0;

    private enum Page { GREET, LORE_VOID, LORE_RIFTS, SKY, SEAL, LORE_KNIGHT, WHERE, VEIL, DRAIN, SHARDS, ARMOR, ARMOR_GIVEN, LEAVING }

    private final VoidNet.Talk talk;
    /** (1.3.97) Un caballero sacro del santuario (SacredKnights): su saludo y sus temas, sin pasar por el servidor. */
    private final SacredKnights.Def sacred;
    private final String playerName;
    private final int entityId;
    private String text = "";
    private final List<Option> options = new ArrayList<>();
    private int age, closeAt = -1;
    /** Cuándo se abrió y cuándo empezó la página (ms). */
    private long openMs, pageMs;
    /** Si ya se ve todo el texto (clic para verlo entero). */
    private boolean full;
    /** Lo encendida que está cada respuesta (0..1, se anima) y la que tiene el ratón encima. */
    private final float[] glow = new float[8];
    private int hovered = -1;
    private long lastFrame;

    private record Option(String label, Runnable action) {}

    /** Dónde va cada cosa (en píxeles de interfaz), calculado en cada fotograma. */
    private float panelX, panelY, panelW, panelH, textX, textY, textW;
    private final List<float[]> optRects = new ArrayList<>();
    private List<FormattedCharSequence> lines = List.of();

    public KnightScreen(VoidNet.Talk talk) {
        super(Component.literal(KnightEntity.NAME));
        this.talk = talk;
        this.sacred = null;
        this.playerName = talk.name();
        this.entityId = talk.entity();
        openMs = System.currentTimeMillis();
        go(Page.GREET);
        ui("page", 0.45F, 1.0F);
    }

    /** (1.3.97) Con un caballero sacro del santuario. */
    public KnightScreen(SacredKnights.Def def, int entityId, String playerName) {
        super(Component.literal(def.name()));
        this.talk = null;
        this.sacred = def;
        this.playerName = playerName;
        this.entityId = entityId;
        openMs = System.currentTimeMillis();
        goSacred(-1);
        ui("page", 0.45F, 1.0F);
    }

    /** El saludo (−1) o la respuesta a un tema; las respuestas: los demás temas y «adiós». */
    private void goSacred(int t) {
        pageMs = System.currentTimeMillis();
        full = false;
        options.clear();
        text = t < 0 ? sacred.greet() : sacred.topics().get(t).answer();
        for (int i = 0; i < sacred.topics().size(); i++) {
            if (i == t) continue;
            int k = i;
            options.add(new Option(sacred.topics().get(i).question(), () -> goSacred(k)));
        }
        options.add(new Option("Que la luz te guarde, " + sacred.name().replaceAll("^(Ser|Dama|Hermano) ", ""), this::onClose));
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
        pageMs = System.currentTimeMillis();
        full = false;
        options.clear();
        String n = talk.name();
        if (talk.home()) {
            // (1.3.96: el Velo del Alba, el Páramo, la corrosión y los Pozos de Luz, la Orden del Alba y la armadura del Vacío)
            int kills = talk.kills(), need = VoidArmor.NEED;
            boolean earned = kills >= need;
            switch (p) {
                case GREET -> text = talk.first()
                        ? "Bienvenido al santuario, " + n + ". Has cruzado el umbral: esto es el Void, o lo que queda de él. Estás dentro del Velo del Alba, y aquí nada del Vacío puede tocarte. Al sur, tras el Puente de Luz, empiezan los Fragmentos del Alba Rota... y más allá, la isla de la grieta."
                        : "Has vuelto al santuario, " + n + ". El Velo aún resiste. ¿Regresas a casa, o te quedas un poco más?";
                case WHERE -> text = "Esta dimensión era una tierra viva, hasta que el ojo del cielo se la tragó. Lo que flota son sus restos, y mis hermanos los sostienen con su luz para que no caigan. Este jardín era el del templo de mi orden: lo único vivo que queda, porque el Velo lo guarda. Al sur, los Fragmentos del Alba Rota; más allá, la isla de la grieta: golpea su cristal y despertará lo que duerme dentro. Donde el Puente de Piedra toca esa isla, el Portal del Alba te traerá de vuelta: quédate un momento en su luz."
                        + (skyWound() ? " Es esa grieta la que desgarró el cielo de Tierras Fantásticas: derrota lo que salga de ella y la del cielo se sellará." : "");
                case VEIL -> text = "Es el Velo del Alba. Cuando el Void se tragó nuestra tierra, el Alba, la luz que guardaba mi orden, se quebró para cerrar sus fauces. Con lo último que quedó levantamos este velo. Arriba late su Corazón: mientras brille, nada del Vacío entra aquí.";
                case DRAIN -> text = "Fuera, el Vacío te corroe despacio: lo notarás en los bordes de los ojos, venas violetas que se te meten dentro. Cuando te llenen, te herirán poco a poco. Pero donde mis hermanos se hicieron luz hay Pozos de Luz: entra en su burbuja y la corrosión se irá.";
                case SHARDS -> text = "Éramos doce, la Orden del Alba. Once se hicieron luz para sellar la grieta y sostener lo que quedó de nuestra tierra: cada Pozo de Luz es un Ancla del Alba, uno de ellos sujetando un fragmento para que no caiga al ojo del Vacío. Verás su columna desde lejos. Si te detienes en uno, quizá oigas el eco de quien fue.";
                case ARMOR -> text = "La armadura del Coloso del Vacío. El Coloso fue la primera criatura que cayó cuando el Vacío abrió sus fauces, y de su piel la forjamos: el Vacío no se come lo que es suyo. Con el yelmo, el peto, las grebas y las botas caminarás por él como uno de los suyos. "
                        + (earned ? "Has derrotado a " + kills + " criaturas de las grietas: es tuya. Está en el altar, detrás de mí. Solo hay una para cada guerrero: cuídala."
                        : "La guardo en el altar para quien haya derrotado a " + need + " criaturas de las grietas. Llevas " + kills + ".");
                case ARMOR_GIVEN -> text = "Que te guarde, " + n + ". Ya no eres presa del Vacío.";
                case LEAVING -> text = "Que la luz te guíe de regreso, " + n + ". Aquí estaré cuando el Void vuelva a llamarte.";
                default -> text = "";
            }
            if (p == Page.LEAVING) return;
            options.add(new Option(backLabel(), () -> leave(VoidNet.BACK)));
            switch (p) {
                case GREET -> {
                    options.add(new Option("¿Qué es esta burbuja?", () -> go(Page.VEIL)));
                    options.add(new Option("¿Hay algo que me proteja del Vacío?", () -> go(Page.ARMOR)));
                }
                case VEIL -> options.add(new Option("¿Y si salgo del Velo?", () -> go(Page.DRAIN)));
                case DRAIN -> options.add(new Option("¿Qué son los Pozos de Luz?", () -> go(Page.SHARDS)));
                case SHARDS -> options.add(new Option("¿Dónde estoy?", () -> go(Page.WHERE)));
                case WHERE -> options.add(new Option("¿Hay algo que me proteja del Vacío?", () -> go(Page.ARMOR)));
                case ARMOR -> {
                    if (earned) {
                        options.add(new Option("Dame la armadura del Vacío", () -> {
                            VoidNet.CHANNEL.sendToServer(new VoidNet.Choose(entityId, VoidNet.ARMOR));
                            go(Page.ARMOR_GIVEN);
                        }));
                    } else {
                        options.add(new Option("¿Qué es esta burbuja?", () -> go(Page.VEIL)));
                    }
                }
                case ARMOR_GIVEN -> options.add(new Option("¿Dónde estoy?", () -> go(Page.WHERE)));
                default -> {
                }
            }
            options.add(new Option("Me quedo un rato", this::onClose));
            return;
        }
        // (con la grieta del cielo abierta o sellada por ahora, 1.3.93, cuenta lo de la grieta del cielo y cómo se cierra)
        boolean wound = skyWound();
        switch (p) {
            case GREET -> text = greeting(n);
            case LORE_VOID -> text = "El Void es lo que hay bajo las costuras de nuestro mundo: un océano de oscuridad donde flotan islas arrancadas de otras tierras. En su cielo, un agujero negro devora la luz de mil estrellas.";
            case LORE_RIFTS -> text = "Las grietas son heridas que el Void abre en la realidad. Dentro duermen criaturas antiguas: golpea su cristal y despertarán. Quien las derrota se gana su gloria... y sus tesoros.";
            case SKY -> text = "Esa grieta nació en el Void. En el centro de su isla late otra, tan profunda que rompió el espacio-tiempo... y su herida se abrió aquí, en el cielo de nuestro Overworld. Para cerrar la del cielo, hay que cerrar la del Void.";
            case SEAL -> text = "Se cierra rompiendo su cristal. Pero cuidado: al romperlo puede traer consigo a las criaturas y entidades malvadas que mi orden encerró en el Void hace eras. Hay que derrotarlas a todas. Cada vez que una cae, la grieta del cielo se sella... hasta que despierta la siguiente.";
            case LORE_KNIGHT -> text = wound
                    ? "Soy lo que queda de la Orden del Alba. Encerramos a esas criaturas en el Void y juramos vigilar el umbral; mis once hermanos se hicieron luz para sellarlo. La mía puede abrirte camino hasta su santuario, y allí te esperaré para traerte de vuelta. ¿Te atreves?"
                    : "Soy lo que queda de la Orden del Alba. Juramos vigilar el umbral, y mis once hermanos se hicieron luz para sellarlo. La mía puede abrirte camino hasta el santuario del Void, y allí te esperaré para traerte de vuelta. ¿Te atreves?";
            case LEAVING -> text = "Cierra los ojos y no temas, " + n + ". ¡Que la luz te lleve!";
            default -> text = "";
        }
        if (p == Page.LEAVING) return;
        options.add(new Option("Llévame al Void", () -> leave(VoidNet.GO)));
        switch (p) {
            case GREET -> {
                if (wound) options.add(new Option("¿Qué es esa grieta del cielo?", () -> go(Page.SKY)));
                options.add(new Option("¿Qué es el Void?", () -> go(Page.LORE_VOID)));
            }
            case LORE_VOID -> options.add(wound ? new Option("¿Y la grieta del cielo?", () -> go(Page.SKY))
                    : new Option("¿Y las grietas?", () -> go(Page.LORE_RIFTS)));
            case LORE_RIFTS -> options.add(new Option("¿Y tú qué haces aquí?", () -> go(Page.LORE_KNIGHT)));
            case SKY -> options.add(new Option("¿Cómo la cerramos?", () -> go(Page.SEAL)));
            case SEAL -> options.add(new Option("¿Y tú qué haces aquí?", () -> go(Page.LORE_KNIGHT)));
            default -> {
            }
        }
        options.add(new Option(p == Page.LORE_KNIGHT ? "Ahora no" : "Adiós", this::onClose));
    }

    /** ¿Está la herida del cielo (abierta o sellada por ahora)? */
    private boolean skyWound() {
        return talk.sky() == SkyRift.OPEN || talk.sky() == SkyRift.CLOSED;
    }

    private String greeting(String n) {
        String me = "Soy " + KnightEntity.NAME + ", el último de los Caballeros Sacros, y custodio el umbral entre Tierras Fantásticas y el Void.";
        return switch (talk.sky()) {
            case SkyRift.OPEN -> talk.first()
                    ? "¡Por fin llegas, " + n + "! " + me + " ¿Has visto el cielo? Esa herida no es una tormenta: es el Void, que se abre paso hasta nuestro mundo."
                    : "¡" + n + "! La grieta del cielo sigue abierta... y el Void no descansa. ¿Estás listo para volver a él?";
            case SkyRift.CLOSED -> talk.first()
                    ? "¡Saludos, " + n + "! " + me + " Mira el cielo: esa cicatriz es una herida del Void, sellada solo por ahora. Otra de sus criaturas ya está despertando."
                    : "¡" + n + "! Lo has visto: la grieta del cielo se ha sellado... pero solo por ahora. El Void ya está despertando a otra de sus criaturas.";
            case SkyRift.ENDED -> talk.first()
                    ? "¡Saludos, " + n + "! Que la luz guíe tus pasos. " + me + " Gracias a los héroes de esta tierra, la herida del cielo se cerró para siempre."
                    : "¡" + n + "! Lo lograron: la herida del cielo se cerró para siempre. Pero el Void sigue ahí abajo, y sus grietas aún laten. ¿Volvemos?";
            default -> talk.first()
                    ? "¡Saludos, " + n + "! Que la luz guíe tus pasos. " + me
                    : "¡" + n + "! Me alegra verte de nuevo, viajero. La luz aún te reconoce. ¿Volvemos al Void?";
        };
    }

    private void leave(int what) {
        VoidNet.CHANNEL.sendToServer(new VoidNet.Choose(entityId, what));
        go(Page.LEAVING);
        closeAt = age + 34;
    }

    /** Un sonido suave del TF Pad (sin posición: suena igual mires a donde mires). */
    private static void ui(String name, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(new ResourceLocation(TFClient.MOD_ID, "pad." + name),
                SoundSource.MASTER, volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    @Override
    public void tick() {
        age++;
        if (closeAt >= 0 && age >= closeAt) onClose();
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        // (si el cliente aún no tiene al caballero, p. ej. recién llegado de lejos, se le esperan 3 s)
        if (mc.player == null || (e == null ? age > 60 : e.distanceToSqr(mc.player) > 14 * 14)) {
            if (closeAt < 0) onClose();
        }
    }

    /** Cuántas letras se ven ya. */
    private int shown() {
        if (full) return text.length();
        int n = (int) ((System.currentTimeMillis() - pageMs) / 1000F * CPS);
        if (n >= text.length()) full = true;
        return Math.min(text.length(), n);
    }

    private boolean done() {
        return shown() >= text.length();
    }

    // ------------------------------------------------------------------------------------------- dibujo

    private static float ease(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return 1F - (1F - x) * (1F - x) * (1F - x);
    }

    /** La letra Cinzel rasterizada a n píxeles reales por píxel de interfaz (font/cinzel_N.json; 4 = cinzel.json). */
    private static ResourceLocation cinzel(int n) {
        n = Mth.clamp(n, 1, 12);
        return new ResourceLocation(TFClient.MOD_ID, n == 4 ? "cinzel" : "cinzel_" + n);
    }

    private void layout() {
        textW = Math.min(380, width - 64);
        lines = font.split(Component.literal(text), (int) textW);
        float optH = 18F, optGap = 5F;
        float optsH = options.isEmpty() ? 0F : options.size() * (optH + optGap) - optGap;
        float nameH = 30F;
        float bodyH = Math.max(3, lines.size()) * 11F;
        panelW = textW + 48F;
        panelH = nameH + 8F + bodyH + (optsH > 0 ? 14F + optsH : 6F) + 16F;
        panelX = (width - panelW) / 2F;
        panelY = height - panelH - 12F;
        textX = panelX + 24F;
        textY = panelY + nameH + 8F;
        optRects.clear();
        float oy = textY + bodyH + 14F;
        for (int i = 0; i < options.size(); i++) {
            String label = options.get(i).label();
            float w = Math.min(textW, font.width(label) + 44F);
            float x = (width - w) / 2F;
            optRects.add(new float[] {x, oy + i * (optH + optGap), w, optH});
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long nowMs = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0F : Math.min(0.1F, (nowMs - lastFrame) / 1000F);
        lastFrame = nowMs;
        layout();
        float in = ease((nowMs - openMs) / 260F);
        float slide = (1F - in) * 14F;
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
        // El mundo se oscurece abajo (y un poco arriba): se sigue viendo al caballero en medio
        g.fillGradient(0, 0, width, height / 4, TFMenuPaint.argb(0.55F * in, 0x000000), 0x00000000);
        g.fillGradient(0, height / 3, width, height, 0x00000000, TFMenuPaint.argb(0.82F * in, 0x07030F));
        // La placa (en píxeles reales: esquinas suaves y un hilo de oro nítidos)
        int rx = Math.round(panelX * s), ry = Math.round((panelY + slide) * s), rw = Math.round(panelW * s), rh = Math.round(panelH * s);
        int rad = Math.round(10 * s);
        g.pose().pushPose();
        g.pose().scale(1F / s, 1F / s, 1F);
        g.drawManaged(() -> {
            TFMenuPaint.roundFill(g, rx, ry, rw, rh, rad, 0, 0, rh, TFMenuPaint.argb(0.86F * in, 0x1A0F2C), TFMenuPaint.argb(0.92F * in, 0x0B0616));
            TFMenuPaint.roundRing(g, rx, ry, rw, rh, rad, Math.max(1, Math.round(s * 0.5F)), TFMenuPaint.argb(0.75F * in, 0xE9C46E),
                    TFMenuPaint.argb(0.25F * in, 0x8A6A2A));
            // las respuestas
            for (int i = 0; i < optRects.size(); i++) {
                float[] o = optRects.get(i);
                float k = glow[i];
                float vis = in * (done() ? 1F : 0.35F);
                int ox = Math.round(o[0] * s), oy = Math.round((o[1] + slide) * s), ow = Math.round(o[2] * s), oh = Math.round(o[3] * s);
                int orad = oh / 2;
                int top = TFMenuPaint.lerp(0xFF261640, 0xFF4A2A78, k), bot = TFMenuPaint.lerp(0xFF170C28, 0xFF2E1850, k);
                TFMenuPaint.roundFill(g, ox, oy, ow, oh, orad, 0, 0, oh, TFMenuPaint.withAlpha(top, 0.95F * vis), TFMenuPaint.withAlpha(bot, 0.95F * vis));
                TFMenuPaint.roundRing(g, ox, oy, ow, oh, orad, Math.max(1, Math.round(s * 0.5F)),
                        TFMenuPaint.withAlpha(TFMenuPaint.lerp(0xFF8A6A2A, GOLD_HI, k), vis), TFMenuPaint.withAlpha(TFMenuPaint.lerp(0xFF5A4420, GOLD, k), vis));
            }
        });
        g.pose().popPose();
        // Su nombre: Cinzel dorada con contorno, a un píxel de letra por píxel de pantalla
        int n = Math.round(s * 1.7F);
        float k = Mth.clamp(n, 1, 12) / s;
        String nm = sacred != null ? sacred.name() : KnightEntity.NAME;
        Component name = Component.literal(nm.toUpperCase(java.util.Locale.ROOT)).withStyle(Style.EMPTY.withFont(cinzel(n)));
        float nw = font.width(name) * k;
        float nx = Math.round((width - nw) / 2F * s) / s, ny = Math.round((panelY + 7F + slide) * s) / s;
        for (int[] d : RING) drawScaled(g, name, nx + d[0] / s, ny + d[1] / s, k, TFMenuPaint.withAlpha(GOLD_DARK, in));
        drawScaled(g, name, nx, ny, k, TFMenuPaint.withAlpha(GOLD_HI, in));
        // los hilos de oro a los lados, que se apagan hacia fuera
        float ly = ny + 5.5F * k;
        float lw = Math.min(90F, (panelW - nw) / 2F - 22F);
        if (lw > 8) {
            fade(g, nx - 10F - lw, ly, lw, true, in);
            fade(g, nx + nw + 10F, ly, lw, false, in);
        }
        String sub = sacred != null ? "Orden del Alba · " + sacred.title()
                : talk.home() ? KnightEntity.TITLE + " · Guardián del santuario" : KnightEntity.TITLE + " · Custodio del umbral";
        g.drawString(font, sub, Math.round((width - font.width(sub)) / 2F), Math.round(panelY + 21F + slide), TFMenuPaint.withAlpha(SUB, in), false);
        // El texto que se va escribiendo (entra con un fundido)
        float tin = ease((nowMs - pageMs) / 180F) * in;
        int vis = shown();
        List<FormattedCharSequence> ls = font.split(Component.literal(text.substring(0, vis)), (int) textW);
        float ty = textY + slide;
        for (FormattedCharSequence l : ls) {
            int lw2 = font.width(l);
            g.drawString(font, l, Math.round((width - lw2) / 2F), Math.round(ty), TFMenuPaint.withAlpha(TEXT, tin), true);
            ty += 11F;
        }
        // Las respuestas (se encienden con el ratón; hasta que acaba de escribir, apagadas)
        boolean ready = done();
        hovered = -1;
        for (int i = 0; i < optRects.size(); i++) {
            float[] o = optRects.get(i);
            boolean over = ready && mouseX >= o[0] && mouseX < o[0] + o[2] && mouseY >= o[1] + slide && mouseY < o[1] + slide + o[3];
            if (over) hovered = i;
            glow[i] += ((over ? 1F : 0F) - glow[i]) * Math.min(1F, dt * 14F);
            float a = in * (ready ? 1F : 0.35F);
            float cy = o[1] + slide + (o[3] - 8F) / 2F;
            String num = String.valueOf(i + 1);
            g.drawString(font, num, Math.round(o[0] + 10F), Math.round(cy), TFMenuPaint.withAlpha(TFMenuPaint.lerp(GOLD, GOLD_HI, glow[i]), a), false);
            String label = options.get(i).label();
            String cut = font.plainSubstrByWidth(label, (int) (o[2] - 36F));
            if (!cut.equals(label)) cut = cut.substring(0, Math.max(0, cut.length() - 1)) + "…";
            g.drawString(font, cut, Math.round(o[0] + 24F), Math.round(cy), TFMenuPaint.withAlpha(TFMenuPaint.lerp(TEXT, GOLD_HI, glow[i]), a), false);
        }
        if (hovered != lastHover) {
            if (hovered >= 0) ui("hover", 0.3F, 1.0F);
            lastHover = hovered;
        }
        // (mientras escribe: «clic para ver todo», que late)
        if (!ready && options.size() > 0) {
            String hint = "Clic para ver todo";
            float pulse = 0.55F + 0.45F * Mth.sin(nowMs / 260F);
            g.drawString(font, hint, Math.round((width - font.width(hint)) / 2F), Math.round(panelY + panelH - 13F + slide),
                    TFMenuPaint.withAlpha(HINT, in * pulse), false);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private int lastHover = -1;

    /** Desplazamientos del contorno del nombre, en píxeles reales. */
    private static final int[][] RING = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    private void drawScaled(GuiGraphics g, Component c, float x, float y, float scale, int argb) {
        if ((argb >>> 24) < 4) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0F);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    // Un hilo de oro que se apaga hacia fuera (una textura de 128×1 con la transparencia en rampa, suave)
    private static ResourceLocation fadeTex;

    private static void fade(GuiGraphics g, float x, float y, float w, boolean toLeft, float alpha) {
        if (fadeTex == null) {
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 128, 1, false);
            for (int i = 0; i < 128; i++) {
                float a = i / 127F;
                a = a * a * (3 - 2 * a);
                int r = 0xE9, gg = 0xC4, b = 0x6E;
                img.setPixelRGBA(i, 0, ((int) (a * 255) << 24) | (b << 16) | (gg << 8) | r);
            }
            DynamicTexture t = new DynamicTexture(img);
            t.setFilter(true, false);
            fadeTex = Minecraft.getInstance().getTextureManager().register("tfclient_hilo_caballero", t);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, alpha);
        g.pose().pushPose();
        g.pose().translate(x, y, 0F);
        g.pose().scale(w / 128F, 1F, 1F);
        // (al revés, con la textura leída de derecha a izquierda: un cuadro girado en espejo no se vería, por la cara)
        if (toLeft) g.blit(fadeTex, 0, 0, 128, 1, 0F, 0F, 128, 1, 128, 1);
        else g.blit(fadeTex, 0, 0, 128, 1, 128F, 0F, -128, 1, 128, 1);
        g.pose().popPose();
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------------------------------- entrada

    private boolean pick(int i) {
        if (i < 0 || i >= options.size()) return false;
        if (!done()) {
            full = true;
            return true;
        }
        Option o = options.get(i);
        ui("select", 0.55F, 1.0F + i * 0.06F);
        o.action().run();
        return true;
    }

    /** La prueba en el juego (BossTestClient): pulsa la respuesta i (desde 0) aunque no haya acabado de escribir. */
    public void testPress(int i) {
        full = true;
        pick(i);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (!done()) {
            full = true;
            return true;
        }
        if (hovered >= 0) return pick(hovered);
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) return pick(key - GLFW.GLFW_KEY_1);
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            if (!done()) {
                full = true;
                return true;
            }
            return pick(0);
        }
        return super.keyPressed(key, scan, mods);
    }
}
