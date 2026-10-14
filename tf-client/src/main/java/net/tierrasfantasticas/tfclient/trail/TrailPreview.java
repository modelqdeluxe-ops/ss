package net.tierrasfantasticas.tfclient.trail;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
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
        TrailClient.Blade blade = TrailClient.blade(stack, Minecraft.getInstance());
        // la empuñadura en la mano (el pivote), como en el juego: el modelo en píxeles de 16, con y hacia abajo
        float hx = (blade.gx() - (blade.tx() - blade.gx()) * 0.25F) * 16, hy = (1 - (blade.gy() - (blade.ty() - blade.gy()) * 0.25F)) * 16;
        Matrix4f m = new Matrix4f().translate(px, py, 0).rotateZ((float) Math.toRadians(angle)).scale(k, k, 1).translate(-hx, -hy, 0);
        if (swinging) {
            TrailProfile profile = TrailProfile.current();
            float wd = Math.min(1.25F, TrailSettings.width / 100F * profile.widthScale());
            Vector3f tip = m.transformPosition(blade.tx() * 16, (1 - blade.ty()) * 16, 0, new Vector3f());
            float bx = blade.tx() + (blade.gx() - blade.tx()) * wd, by = blade.ty() + (blade.gy() - blade.ty()) * wd;
            Vector3f base = m.transformPosition(bx * 16, (1 - by) * 16, 0, new Vector3f());
            TRAIL.add(tip.x, tip.y, tip.z, base.x, base.y, base.z, now, ++frame);
        } else {
            frame += 2; // el siguiente tajo, tramo nuevo
        }
        TRAIL.color = TrailClient.colorOf(stack, blade);
        TRAIL.strength = 1;
        if (TrailSettings.enabled) {
            MultiBufferSource.BufferSource buffers = g.bufferSource();
            TrailClient.draw(TRAIL, buffers, g.pose().last().pose(), 0, 0, 0, new Vector3f(1, 0, 0), new Vector3f(0, -1, 0), now, 1F);
            buffers.endBatch();
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
