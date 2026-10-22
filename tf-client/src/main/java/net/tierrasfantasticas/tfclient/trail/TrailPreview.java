package net.tierrasfantasticas.tfclient.trail;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * La vista previa del pad (Efectos › COMBATE): una espada que da un tajo cada poco sobre un escenario de noche, con la
 * estela de los ajustes de ahora, dibujada con el mismo código que en el juego (TrailClient.draw).
 */
public final class TrailPreview {
    private static final TrailClient.Trail TRAIL = new TrailClient.Trail();
    private static final long PERIOD = 1_350_000_000L, SWING = 300_000_000L;
    private static long start = -1, lastFrame;
    private static long frame;

    private TrailPreview() {}

    /** El arma que se ve: la de tu mano si deja estela; si no, una espada de diamante. */
    public static ItemStack weapon() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack main = mc.player.getMainHandItem();
            if (!main.isEmpty() && TrailClient.allowed(main)) return main;
        }
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, ItemStack stack) {
        TrailSettings.load();
        long now = Util.getNanos();
        if (start < 0 || now - lastFrame > 500_000_000L) start = now; // al volver a la página, el tajo empieza de cero
        lastFrame = now;
        // el escenario: cielo de noche, un brillo detrás y el suelo
        g.fillGradient(x, y, x + w, y + h, 0xFF141C3C, 0xFF07091A);
        g.fill(x, y + h - 9, x + w, y + h, 0xFF0B1028);
        g.fill(x, y + h - 9, x + w, y + h - 8, 0x3378A0FF);
        for (int i = 0; i < 7; i++) {
            int sx = x + 6 + (i * 37) % Math.max(1, w - 12), sy = y + 4 + (i * 23) % Math.max(1, h / 2);
            g.fill(sx, sy, sx + 1, sy + 1, i % 3 == 0 ? 0x99FFFFFF : 0x55FFFFFF);
        }
        g.flush();
        // el tajo: de atrás arriba a delante abajo, rápido, con un rebote al final; luego vuelve despacio
        long t = (now - start) % PERIOD;
        float a0 = -125F, a1 = 40F, angle;
        boolean swinging = t < SWING;
        if (swinging) {
            float u = t / (float) SWING;
            float e = 1 - (float) Math.pow(1 - u, 3);
            angle = a0 + (a1 - a0) * e;
        } else if (t < PERIOD - 520_000_000L) {
            float u = (t - SWING) / 220_000_000F;
            angle = a1 + (u < 1 ? (float) Math.sin(u * Math.PI) * 4 : 0);
        } else {
            float u = (t - (PERIOD - 520_000_000L)) / 520_000_000F;
            float e = u * u * (3 - 2 * u);
            angle = a1 + (a0 - a1) * e;
        }
        float k = h * 0.6F / 16F; // la hoja llega casi al borde de arriba
        float px = x + w * 0.5F, py = y + h * 0.74F;
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        TrailClient.Blade blade = TrailClient.blade(model);
        // el modelo, en los 16 px del objeto tal como se pinta aquí (y hacia abajo; las armas 3D, con su posición «gui»)
        Matrix4f gui = TrailClient.guiSpace(model);
        // la empuñadura en la mano (el pivote), como en el juego
        Vector3f grip = gui.transformPosition(blade.along(1.25F), new Vector3f());
        float hx = grip.x, hy = grip.y;
        // (1.3.109, el dueño: «la espada Valentine está invertida en la vista previa») las espadas de Minecraft se pintan
        // con la punta arriba a la derecha (−45°), pero muchas armas 3D, en su posición «gui», apuntan arriba a la
        // izquierda (la Valentine, −137°: salía boca abajo y pegada al suelo, y su estela, fuera de la vista). Se giran
        // lo que haga falta para que la hoja vaya a −45° y se ponen del largo de una espada normal (18 px de la punta a
        // la mano)
        Vector3f tipG = gui.transformPosition(blade.along(0), new Vector3f());
        float dx = tipG.x - hx, dy = tipG.y - hy, len = (float) Math.sqrt(dx * dx + dy * dy);
        float fix = len > 1e-3F ? -45F - (float) Math.toDegrees(Math.atan2(dy, dx)) : 0F;
        if (len > 1e-3F) k *= Math.max(0.5F, Math.min(1.8F, 18.1F / len));
        angle += fix;
        Matrix4f m = new Matrix4f().translate(px, py, 0).rotateZ((float) Math.toRadians(angle)).scale(k, k, 1).translate(-hx, -hy, 0);
        if (swinging) {
            TrailProfile profile = TrailProfile.current();
            float wd = Math.min(1.25F, TrailSettings.width / 100F * profile.widthScale());
            Vector3f t16 = gui.transformPosition(blade.along(0), new Vector3f()), b16 = gui.transformPosition(blade.along(wd), new Vector3f());
            Vector3f tip = m.transformPosition(t16.x, t16.y, 0, new Vector3f());
            Vector3f base = m.transformPosition(b16.x, b16.y, 0, new Vector3f());
            TRAIL.add(tip.x, tip.y, tip.z, base.x, base.y, base.z, now, ++frame);
        } else {
            frame += 2; // el siguiente tajo, tramo nuevo
        }
        TRAIL.color = TrailClient.colorOf(stack, blade);
        TRAIL.strength = 1;
        if (TrailSettings.enabled) {
            MultiBufferSource.BufferSource buffers = g.bufferSource();
            // (1.3.112, el dueño: «la estela del efecto no se muestra en el apartado de efectos de combate del pad»; las
            // siete capturas de la prueba, la espada sin estela en ningún momento del tajo) la estela va con prueba de
            // profundidad (LEQUAL, como en el mundo) y quedaba a la misma altura que el fondo de la vista previa: la
            // tapaba. Un poco por delante del fondo y por detrás del arma (que se pinta a +150)
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            TrailClient.draw(TRAIL, buffers, g.pose().last().pose(), 0, 0, 0, new Vector3f(1, 0, 0), new Vector3f(0, -1, 0), now, 1F);
            buffers.endBatch();
            g.pose().popPose();
        } else {
            TRAIL.samples.clear();
        }
        // el arma encima de su estela
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.pose().scale(k, k, 1);
        g.pose().translate(-hx, -hy, 0);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        g.flush();
    }
}
