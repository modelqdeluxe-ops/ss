/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.clans.ClanTagFx;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * (1.3.160, el dueño: «el emote no me gusta, necesito mínimo 5 distintos, ninguno igual ni música igual») Los cinco
 * emotes del clan. Cada uno: la postura (brazos, piernas, cabeza), lo que hace el cuerpo entero (agacharse, saltar, girar,
 * flotar) y sus efectos, con la insignia del clan de ahora y su etiqueta (solo las letras, con estrellas a los lados), al
 * ritmo de su música (tools/pad/emotes_sonido.py).
 * <ol start="0">
 *   <li><b>Estandarte</b> (5 s): levanta los brazos y golpea el suelo; detrás sale de la tierra un estandarte enorme del
 *   clan que se despliega y ondea al viento, con rayos de luz detrás; saludo con el puño en el pecho y luego en alto.</li>
 *   <li><b>Corona</b> (5 s): baja un haz de luz del cielo; se arrodilla; una corona de oro con la insignia baja girando y,
 *   al levantarse con los brazos abiertos, se le posa en la cabeza (destello, aureola y la etiqueta encima).</li>
 *   <li><b>Trueno</b> (4,6 s): se agacha y junta energía entre las manos; salta con el puño arriba y le cae un rayo del
 *   color del clan; onda en el suelo con la insignia grabada, chispas eléctricas alrededor y un golpe final al suelo.</li>
 *   <li><b>Fiesta</b> (5,2 s): cañones de confeti, pista de baile que se ilumina al ritmo, la insignia arriba como bola
 *   de discoteca con focos; baila (brazos, señalar arriba, dos vueltas) y acaba con los brazos en V y más confeti.</li>
 *   <li><b>Órbita</b> (5 s): levita con los brazos abiertos sobre un círculo mágico; seis insignias pequeñas giran a su
 *   alrededor dejando estela, con un remolino de brillos; se juntan arriba en una insignia grande que destella.</li>
 * </ol>
 * Las posiciones «locales» son respecto a hacia dónde mira el cuerpo: x a su izquierda, z hacia delante.
 */
final class ClanEmotes {
    static final double[] DURATIONS = {5.0, 5.0, 4.6, 5.2, 5.0};

    private ClanEmotes() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Curvas y azar fijo
    // ---------------------------------------------------------------------------------------------------------------

    static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }

    static double smooth(double a, double b, double x) {
        double t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    static double easeOut(double x) {
        x = clamp01(x);
        return 1 - Math.pow(1 - x, 3);
    }

    static double easeIn(double x) {
        x = clamp01(x);
        return x * x * x;
    }

    /** Sale pasándose un poco y vuelve. */
    static double easeOutBack(double x) {
        x = clamp01(x);
        double c = 1.9;
        return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
    }

    /** Un muelle (de 0 a 1, pasándose y volviendo). */
    static double spring(double t) {
        if (t <= 0) return 0;
        return 1 - Math.exp(-5.2 * t) * Math.cos(11.0 * t);
    }

    static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    /** Un número fijo entre 0 y 1 para (semilla, i, k): el mismo en todos los TF Client y en todos los fotogramas. */
    static double rnd(long seed, int i, int k) {
        long z = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + k * 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La postura: ángulos (radianes) de brazos, piernas, cabeza y cuerpo
    // ---------------------------------------------------------------------------------------------------------------

    static final class P {
        double ra, ry, rz, la, ly, lz, rl, rlz, ll, llz, hx, hy, bx;

        void arms(double ra, double rz, double la, double lz) {
            this.ra = ra;
            this.rz = rz;
            this.la = la;
            this.lz = lz;
        }
    }

    static void pose(int id, double e, P p) {
        switch (id) {
            case 0 -> poseBanner(e, p);
            case 1 -> poseCrown(e, p);
            case 2 -> poseThunder(e, p);
            case 3 -> poseParty(e, p);
            default -> poseOrbit(e, p);
        }
    }

    /** Cuánto se baja, sube o flota el cuerpo entero (bloques). */
    static double lift(int id, double e) {
        switch (id) {
            case 0 -> {
                if (e < 0.32) return 0.05 * smooth(0, 0.3, e);
                if (e < 0.5) return lerp(0.05, -0.18, smooth(0.32, 0.48, e));
                return -0.18 * (1 - smooth(0.75, 1.05, e));
            }
            case 1 -> {
                return -0.38 * smooth(0.4, 0.95, e) * (1 - smooth(2.0, 2.5, e));
            }
            case 2 -> {
                if (e < 1.0) return -0.2 * smooth(0, 0.5, e);
                if (e < 1.15) return lerp(-0.2, 0.35, easeOut((e - 1.0) / 0.15));
                if (e < 1.4) return lerp(0.35, 0.12, smooth(1.15, 1.4, e));
                if (e < 3.75) return 0.12 + 0.03 * Math.sin(e * 7);
                if (e < 3.85) return lerp(0.12, -0.22, smooth(3.75, 3.85, e));
                return -0.22 * (1 - smooth(3.95, 4.4, e));
            }
            case 3 -> {
                double beat = 60 / 128.0, b = (e - 0.3) / beat;
                if (e < 0.3) return -0.05 * smooth(0, 0.25, e);
                if (b < 4) return 0.06 * Math.abs(Math.sin(Math.PI * b));
                if (b < 7) return 0.03 * Math.abs(Math.sin(Math.PI * b));
                if (e < 4.75) return 0;
                double f = clamp01((e - 4.75) / 0.32);
                return 0.25 * 4 * f * (1 - f);
            }
            default -> {
                return orbitLift(e);
            }
        }
    }

    static double orbitLift(double e) {
        if (e < 0.8) return 0.55 * easeOut(e / 0.8);
        if (e < 4.2) return 0.55 + 0.05 * Math.sin((e - 0.8) * 2.4);
        return (0.55 + 0.05 * Math.sin((4.2 - 0.8) * 2.4)) * (1 - smooth(4.2, 4.85, e));
    }

    private static void poseBanner(double e, P p) {
        if (e < 0.32) {
            double a = smooth(0, 0.3, e);
            p.arms(-2.95 * a, 0.05 * a, -2.95 * a, -0.05 * a);
            p.bx = -0.18 * a;
            p.hx = -0.3 * a;
        } else if (e < 0.75) {
            double a = smooth(0.32, 0.48, e);
            p.arms(lerp(-2.95, -0.55, a), 0, lerp(-2.95, -0.55, a), 0);
            p.bx = lerp(-0.18, 0.45, a);
            p.hx = lerp(-0.3, 0.35, a);
            p.rl = -0.25 * a;
            p.ll = 0.15 * a;
        } else if (e < 2.6) {
            double a = smooth(0.75, 1.05, e);
            p.ra = lerp(-0.55, -1.25, a);
            p.ry = lerp(0, -0.55, a);
            p.la = lerp(-0.55, 0.05, a);
            p.lz = -0.08 * a;
            p.bx = lerp(0.45, 0, a);
            p.hx = lerp(0.35, -0.12, a) + 0.02 * Math.sin(e * 3);
            p.rl = lerp(-0.25, 0, a);
            p.ll = lerp(0.15, 0, a);
        } else {
            double a = smooth(2.6, 2.85, e);
            double pump = Math.sin((e - 2.9) / (60 / 112.0) * Math.PI * 2) * smooth(2.85, 3.0, e) * (1 - smooth(3.9, 4.2, e));
            p.ra = lerp(-1.25, -3.0, a) + 0.2 * pump;
            p.ry = lerp(-0.55, 0, a);
            p.rz = 0.18 * a;
            p.la = lerp(0.05, -0.3, a);
            p.lz = lerp(-0.08, -0.4, a);
            p.hx = lerp(-0.12, -0.3, a) - 0.05 * pump;
        }
    }

    private static void poseCrown(double e, P p) {
        if (e < 0.4) {
            double a = smooth(0, 0.3, e);
            p.hx = -0.5 * a;
            p.arms(-0.2 * a, 0.25 * a, -0.2 * a, -0.25 * a);
        } else if (e < 2.0) {
            double a = smooth(0.4, 0.95, e);
            p.hx = lerp(-0.5, 0.55, a) + 0.03 * Math.sin(e * 2.5) * a;
            p.arms(lerp(-0.2, -0.75, a), lerp(0.25, 0, a), lerp(-0.2, -1.1, a), lerp(-0.25, -0.1, a));
            p.rl = 1.0 * a;
            p.ll = -1.45 * a;
            p.bx = 0.15 * a;
        } else if (e < 2.6) {
            double a = smooth(2.0, 2.5, e);
            p.hx = lerp(0.55, -0.25, a);
            p.arms(lerp(-0.75, -0.15, a), lerp(0, 1.4, a), lerp(-1.1, -0.15, a), lerp(-0.1, -1.4, a));
            p.rl = lerp(1.0, 0, a);
            p.ll = lerp(-1.45, 0, a);
            p.bx = lerp(0.15, -0.12, a);
        } else {
            double f = smooth(2.6, 4.2, e);
            double breath = 0.03 * Math.sin(e * 2);
            p.hx = -0.25;
            p.arms(-0.15, 1.4 + 0.12 * f + breath, -0.15, -1.4 - 0.12 * f - breath);
            p.bx = -0.12;
        }
    }

    private static void poseThunder(double e, P p) {
        if (e < 0.6) {
            double a = smooth(0, 0.5, e);
            p.arms(0.6 * a, 0.2 * a, 0.6 * a, -0.2 * a);
            p.rlz = 0.25 * a;
            p.llz = -0.25 * a;
            p.bx = 0.25 * a;
            p.hx = 0.1 * a;
        } else if (e < 1.0) {
            double a = smooth(0.6, 0.95, e);
            p.ra = lerp(0.6, -1.25, a);
            p.ry = -0.45 * a;
            p.la = lerp(0.6, -1.25, a);
            p.ly = 0.45 * a;
            p.rz = 0.2 * (1 - a);
            p.lz = -0.2 * (1 - a);
            p.rlz = 0.25;
            p.llz = -0.25;
            p.bx = lerp(0.25, 0.1, a);
            p.hx = 0.1 * (1 - a);
        } else if (e < 3.75) {
            double a = smooth(1.0, 1.15, e);
            double shake = (e < 1.7 ? 1 : 0.35) * 0.04 * Math.sin(e * 60) * a;
            p.ra = lerp(-1.25, -3.05, a) + shake;
            p.ry = -0.45 * (1 - a);
            p.rz = 0.15 * a;
            p.la = lerp(-1.25, 0.5, a);
            p.ly = 0.45 * (1 - a);
            p.lz = -0.35 * a;
            p.bx = lerp(0.1, -0.15, a);
            p.hx = lerp(0, -0.45, a);
            p.rlz = lerp(0.25, 0.1, a);
            p.llz = lerp(-0.25, -0.1, a);
        } else {
            double a = smooth(3.75, 3.85, e);
            p.arms(lerp(-3.05, -0.5, a), 0.15 * (1 - a), lerp(0.5, -0.3, a), -0.35 * (1 - a));
            p.bx = lerp(-0.15, 0.5, a);
            p.hx = lerp(-0.45, 0.4, a);
            p.rlz = lerp(0.1, 0.3, a);
            p.llz = lerp(-0.1, -0.3, a);
        }
    }

    private static void poseParty(double e, P p) {
        double beat = 60 / 128.0, b = (e - 0.3) / beat;
        if (e < 0.3) {
            double a = smooth(0, 0.25, e);
            p.arms(-0.3 * a, 0.2 * a, -0.3 * a, -0.2 * a);
            p.bx = 0.1 * a;
        } else if (b < 4) {
            double c = Math.cos(Math.PI * b);
            double in = smooth(0.3, 0.45, e);
            p.arms((-1.5 - 1.2 * c) * in, 0.3, (-1.5 + 1.2 * c) * in, -0.3);
            p.hx = 0.1 * Math.sin(2 * Math.PI * b);
            p.rl = 0.15 * Math.sin(Math.PI * b);
            p.ll = -0.15 * Math.sin(Math.PI * b);
        } else if (b < 7) {
            int k = (int) Math.floor(b);
            double s = smooth(0, 0.14, (b - k) * beat);
            boolean right = k % 2 == 0;
            // de la postura del pulso anterior a la de este (señalar arriba y la otra mano en la cadera)
            // {ra, rz, ry, la, lz, ly}: señala arriba con una mano; la otra, en la cadera (hacia dentro: ry < 0, ly > 0)
            double[] pointRight = {-2.45, 0.6, 0, -0.35, -0.9, 0.35}, pointLeft = {-0.35, 0.9, -0.35, -2.45, -0.6, 0};
            double[] from = right ? pointLeft : pointRight;
            double[] to = right ? pointRight : pointLeft;
            if (k == 4) from = new double[] {-1.5 - 1.2 * Math.cos(Math.PI * 4), 0.3, 0, -1.5 + 1.2 * Math.cos(Math.PI * 4), -0.3, 0};
            p.ra = lerp(from[0], to[0], s);
            p.rz = lerp(from[1], to[1], s);
            p.ry = lerp(from[2], to[2], s);
            p.la = lerp(from[3], to[3], s);
            p.lz = lerp(from[4], to[4], s);
            p.ly = lerp(from[5], to[5], s);
            double hyTo = right ? -0.35 : 0.35, hyFrom = k == 4 ? 0 : -hyTo;
            p.hy = lerp(hyFrom, hyTo, s);
            p.hx = -0.15;
        } else if (e < 4.75) {
            double a = smooth(3.58, 3.75, e);
            p.arms(-0.1, 1.35 * a, -0.1, -1.35 * a);
            p.hx = -0.1;
        } else {
            double a = smooth(4.75, 4.9, e);
            p.arms(lerp(-0.1, -2.75, a), lerp(1.35, 0.45, a), lerp(-0.1, -2.75, a), lerp(-1.35, -0.45, a));
            p.bx = -0.1 * a;
            p.hx = -0.3 * a;
        }
    }

    private static void poseOrbit(double e, P p) {
        double a = smooth(0, 0.7, e);
        double wing = e > 0.8 ? 0.15 * Math.sin((e - 0.8) * 2.2) : 0;
        double up = smooth(3.6, 4.1, e);
        p.arms(lerp(-0.35 * a, -2.9, up), lerp(0.75 * a + wing, 0.25, up), lerp(-0.35 * a, -2.9, up), lerp(-0.75 * a - wing, -0.25, up));
        p.rl = 0.12 * a;
        p.ll = 0.12 * a;
        p.hx = lerp(-0.2 * a + (e > 0.8 ? 0.05 * Math.sin(e * 1.7) : 0), -0.55, up);
        p.bx = -0.05 * a;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // El cuerpo entero (desde RenderPlayerEvent.Pre): subir/bajar, girar, balancearse
    // ---------------------------------------------------------------------------------------------------------------

    static void body(int id, double e, PoseStack pose, float yaw) {
        pose.translate(0, lift(id, e), 0);
        switch (id) {
            case 0 -> {
                // el golpe al suelo: se aplasta un poco
                double sq = e > 0.48 && e < 0.9 ? 0.06 * Math.exp(-12 * (e - 0.48)) : 0;
                pose.scale((float) (1 + sq * 0.6), (float) (1 - sq), (float) (1 + sq * 0.6));
            }
            case 2 -> {
                double sq = e > 3.82 && e < 4.3 ? 0.08 * Math.exp(-10 * (e - 3.82)) * Math.cos(16 * (e - 3.82)) : 0;
                pose.scale((float) (1 + sq * 0.6), (float) (1 - sq), (float) (1 + sq * 0.6));
            }
            case 3 -> {
                double beat = 60 / 128.0, b = (e - 0.3) / beat;
                double sway = e > 0.3 && b < 7 ? 5 * Math.sin(Math.PI * b) : 0;
                double spin = 720 * smooth(3.58, 4.7, e);
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                pose.mulPose(Axis.ZP.rotationDegrees((float) sway));
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                if (spin > 0 && spin < 720) pose.mulPose(Axis.YP.rotationDegrees((float) spin));
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Los efectos (desde RenderPlayerEvent.Post, con el sistema en los pies del jugador, sin girar)
    // ---------------------------------------------------------------------------------------------------------------

    /** Hacia dónde mira el cuerpo: pasar de local (x a su izquierda, z delante) al mundo. */
    record Frame(double cos, double sin, float yaw) {
        static Frame of(float yawDeg) {
            double r = Math.toRadians(yawDeg);
            return new Frame(Math.cos(r), Math.sin(r), yawDeg);
        }

        double x(double lx, double lz) {
            return lx * cos - lz * sin;
        }

        double z(double lx, double lz) {
            return lx * sin + lz * cos;
        }

        /** Gira el sistema para dibujar en local (hay que hacer popPose después). */
        void push(PoseStack pose) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        }
    }

    static void fx(int id, double e, PoseStack pose, MultiBufferSource b, Player p, ClanLooks.Look look, float partial) {
        Frame f = Frame.of(Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot));
        long seed = p.getUUID().getLeastSignificantBits() ^ id * 7919L;
        int[] pal = ClanEmoteFx.palette(look);
        double now = ClanClient.now();
        switch (id) {
            case 0 -> fxBanner(e, pose, b, look, f, seed, pal, now);
            case 1 -> fxCrown(e, pose, b, look, f, seed, pal, now);
            case 2 -> fxThunder(e, pose, b, look, f, seed, pal, now);
            case 3 -> fxParty(e, pose, b, look, f, seed, pal, now);
            default -> fxOrbit(e, pose, b, look, f, seed, pal, now);
        }
    }

    /** Una lluvia de brillos que sale de (x, y, z) en t0 (con gravedad y frenado), de los colores del clan. */
    static void burst(PoseStack pose, MultiBufferSource b, long seed, int[] pal, double e, double t0, int n, double x, double y, double z,
                      double speed, double g, double life, float size) {
        double age0 = e - t0;
        if (age0 < 0 || age0 > life + 0.2) return;
        VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
        for (int i = 0; i < n; i++) {
            double th = rnd(seed, i, 1) * Math.PI * 2, ph = Math.acos(1 - rnd(seed, i, 2) * 1.7);
            double sp = speed * (0.5 + rnd(seed, i, 3) * 0.7);
            double vx = Math.sin(ph) * Math.cos(th) * sp, vy = Math.cos(ph) * sp, vz = Math.sin(ph) * Math.sin(th) * sp;
            double l = life * (0.55 + rnd(seed, i, 4) * 0.45);
            if (age0 > l) continue;
            double drag = (1 - Math.exp(-1.8 * age0)) / 1.8;
            double px = x + vx * drag, py = y + vy * drag - 0.5 * g * age0 * age0 * 0.45, pz = z + vz * drag;
            float fade = (float) ((1 - age0 / l) * (0.65 + 0.35 * Math.sin(e * 20 + i * 1.3)));
            ClanEmoteFx.billboard(pose, vc, px, py, pz, size * (float) (0.6 + rnd(seed, i, 5) * 0.8), ClanEmoteFx.scale(pal[i % pal.length], fade));
        }
    }

    // ----- 0. Estandarte --------------------------------------------------------------------------------------------

    private static void fxBanner(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, Frame f, long seed, int[] pal, double now) {
        int main = ClanEmoteFx.main(look);
        // sube de la tierra (0,5–0,95 s), se despliega (0,95–1,6 s) y al final se hunde otra vez (4,35–4,9 s)
        double rise = e < 0.5 ? -3.9 : e < 0.95 ? lerp(-3.9, 0, easeOutBack((e - 0.5) / 0.45)) : e < 4.35 ? 0 : lerp(0, -3.9, easeIn((e - 4.35) / 0.55));
        double len = e < 0.95 ? 0.12 : lerp(0.12, 2.2, easeOut((e - 0.95) / 0.65));
        double wind = 0.55 + 0.45 * smooth(1.6, 2.3, e) + 0.12 * Math.sin(e * 1.3);
        // los rayos de luz detrás (1,4–4,4 s)
        double rays = smooth(1.4, 1.9, e) * (1 - smooth(4.0, 4.4, e));
        if (rays > 0.01) {
            f.push(pose);
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            pose.translate(0, 2.55 + rise, -0.85);
            for (int i = 0; i < 12; i++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees((float) (i * 30 + e * 14)));
                float l = (float) (2.0 + 0.45 * Math.sin(e * 2.2 + i * 1.7));
                ClanEmoteFx.quad(pose, vc, 0, l * 0.55F, 0, 0.38F, l * 1.1F, ClanEmoteFx.scale(ClanEmoteFx.mix(main, 0xFFE08A, 0.55F), (float) (0.6 * rays)));
                pose.popPose();
            }
            pose.popPose();
        }
        if (rise > -3.85) {
            f.push(pose);
            banner(pose, b, look, e, rise, len, wind, now);
            pose.popPose();
        }
        // el golpe (0,5 s): polvo, piedras y chispas en el pie del asta
        double bx = f.x(0, -0.55), bz = f.z(0, -0.55);
        if (e > 0.5 && e < 1.2) {
            double k = (e - 0.5) / 0.7;
            ClanEmoteFx.ring(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0.04, 0.3 + 2.4 * easeOut(k), 0.4 * (1 - k) + 0.05,
                    ClanEmoteFx.scale(0xC9A060, (float) (1 - k)), 40);
            VertexConsumer rock = ClanEmoteFx.solid(b);
            for (int i = 0; i < 16; i++) {
                double a = e - 0.5, th = rnd(seed, i, 7) * Math.PI * 2, sp = 1.6 + rnd(seed, i, 8) * 2.2;
                double px = bx + Math.cos(th) * sp * a * 0.7, pz = bz + Math.sin(th) * sp * a * 0.7, py = 0.05 + (2.5 + rnd(seed, i, 9) * 2.5) * a - 4.9 * a * a;
                if (py < 0) continue;
                float s = (float) (0.05 + rnd(seed, i, 10) * 0.06);
                pose.pushPose();
                pose.translate(px, py, pz);
                pose.mulPose(Axis.YP.rotationDegrees((float) (a * 400 + i * 40)));
                pose.mulPose(Axis.XP.rotationDegrees((float) (a * 300)));
                ClanEmoteFx.box(pose, rock, -s, -s, -s, s, s, s, i % 3 == 0 ? 0x6B5A48 : 0x8A7A66);
                pose.popPose();
            }
        }
        burst(pose, b, seed, new int[] {0xFFD24A, 0xFFFFFF, main}, e, 0.5, 30, bx, 0.2, bz, 3.6, 5.0, 1.1, 0.12F);
        // el viento: brillos dorados que cruzan de un lado a otro (1,6–4,4 s)
        double windFx = smooth(1.6, 2.0, e) * (1 - smooth(4.1, 4.4, e));
        if (windFx > 0.01) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 34; i++) {
                double ph = (e * (0.45 + rnd(seed, i, 11) * 0.35) + rnd(seed, i, 12)) % 1.0;
                double lx = 2.4 - ph * 4.8, ly = 0.4 + rnd(seed, i, 13) * 2.9 + 0.15 * Math.sin(e * 2 + i), lz = -0.9 + rnd(seed, i, 14) * 1.6;
                float tw = (float) (0.55 + 0.45 * Math.sin(e * 9 + i * 2.1)) * (float) Math.sin(Math.PI * ph);
                ClanEmoteFx.billboard(pose, vc, f.x(lx, lz), ly, f.z(lx, lz), 0.09F, ClanEmoteFx.scale(i % 3 == 0 ? pal[i % pal.length] : 0xFFD878, (float) (tw * windFx)));
            }
        }
        // el puño arriba (2,6 s): estrellas
        burst(pose, b, seed + 3, pal, e, 2.72, 22, f.x(-0.38, 0.05), 2.2, f.z(-0.38, 0.05), 2.4, 3.0, 1.0, 0.09F);
        if (e > 2.68 && e < 4.2) {
            double k = smooth(2.68, 2.85, e) * (1 - smooth(3.9, 4.2, e));
            ClanEmoteFx.star(pose, b, f.x(-0.38, 0.08), 2.28, f.z(-0.38, 0.08), (float) (0.55 * k * (0.85 + 0.15 * Math.sin(e * 8))), (float) (e * 60), 0xFFF0B0);
        }
        // al hundirse, chispas
        burst(pose, b, seed + 5, pal, e, 4.4, 26, bx, 0.3, bz, 2.6, 2.0, 0.9, 0.1F);
    }

    /** La onda de la tela del estandarte (solo hacia delante: no se mete detrás del asta). */
    private static float clothWave(double t, double u, double v, double wind) {
        double amp = (0.03 + 0.17 * v) * wind;
        return (float) (amp * (0.5 + 0.5 * Math.sin(t * 3.1 + v * 4.2 + u * 2.6)) + 0.05 * v * wind * (0.5 + 0.5 * Math.sin(t * 1.7 + u * 5.3)));
    }

    /** El estandarte grande (en local): asta y travesaño, la tela de len de largo, la insignia y la etiqueta. */
    private static void banner(PoseStack pose, MultiBufferSource b, ClanLooks.Look look, double t, double rise, double len, double wind, double now) {
        final float z0 = -0.55F, top = (float) (3.45 + rise), hw = 1.0F, full = 2.2F;
        int rgb = ClanEmoteFx.main(look);
        VertexConsumer vc = ClanEmoteFx.solid(b);
        ClanEmoteFx.box(pose, vc, -0.05F, (float) Math.min(0, rise) - 0.2F, -0.72F, 0.05F, top + 0.18F, -0.62F, 0x6B4423);
        ClanEmoteFx.box(pose, vc, -hw - 0.17F, top + 0.02F, -0.64F, hw + 0.17F, top + 0.11F, -0.53F, 0xC9962E);
        ClanEmoteFx.box(pose, vc, -hw - 0.24F, top - 0.01F, -0.66F, -hw - 0.13F, top + 0.14F, -0.51F, 0xF2C14E);
        ClanEmoteFx.box(pose, vc, hw + 0.13F, top - 0.01F, -0.66F, hw + 0.24F, top + 0.14F, -0.51F, 0xF2C14E);
        ClanEmoteFx.box(pose, vc, -0.09F, top + 0.18F, -0.76F, 0.09F, top + 0.36F, -0.58F, 0xF2C14E);
        // la tela: el ribete y encima el paño del color del clan (la onda, según el sitio de verdad en la tela)
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int trim = ClanEmoteFx.mix(ClanEmoteFx.scale(rgb, 0.5F), 0xC9962E, 0.3F);
        float bottom = (float) (top - len);
        for (int layer = 0; layer < 2; layer++) {
            float inset = layer == 0 ? 0 : 0.07F;
            int col = layer == 0 ? trim : rgb;
            float dz = layer == 0 ? 0 : 0.008F;
            float x0 = -hw + inset, x1 = hw - inset, y0 = top - inset * 0.6F, y1 = bottom + inset;
            int nx = 12, ny = 14;
            for (int j = 0; j < ny; j++) {
                for (int i = 0; i < nx; i++) {
                    float ua = i / (float) nx, ub = (i + 1) / (float) nx, va = j / (float) ny, vb = (j + 1) / (float) ny;
                    float[] xs = {Mth.lerp(ua, x0, x1), Mth.lerp(ub, x0, x1), Mth.lerp(ub, x0, x1), Mth.lerp(ua, x0, x1)};
                    float[] vs = {va, va, vb, vb};
                    float[] us = {ua, ub, ub, ua};
                    for (int pass = 0; pass < 2; pass++) {
                        for (int q = 0; q < 4; q++) {
                            int k = pass == 0 ? q : 3 - q;
                            float y = Mth.lerp(vs[k], y0, y1);
                            // abajo, dos puntas (la V en medio), solo si está desplegada
                            if (vs[k] > 0.999F && len > 1.0) y += 0.24F * (float) smooth(1.0, 2.0, len) * (1 - Math.abs(us[k] - 0.5F) * 2);
                            float u = (xs[k] + hw) / (2 * hw), v = (top - y) / full;
                            float z = z0 + clothWave(t, u, v, wind) + dz;
                            int c = ClanEmoteFx.scale(col, 1.06F - 0.25F * v);
                            vc.vertex(m, xs[k], y, z).color(c >> 16 & 255, c >> 8 & 255, c & 255, 255).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                                    .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, pass == 0 ? 1 : -1).endVertex();
                        }
                    }
                }
            }
        }
        if (look == null) return;
        // la insignia (cuando la tela ya llega), con un muelle
        double show = len < 1.45 ? 0 : spring((len - 1.45) / 0.5 * 0.6);
        if (show > 0.01) {
            float size = (float) (0.95 * show), cy = top - 0.98F;
            VertexConsumer bv = b.getBuffer(RenderType.entityCutoutNoCull(look.badgeTexture()));
            int steps = 6;
            for (int j = 0; j < steps; j++) {
                for (int i = 0; i < steps; i++) {
                    float[] fu = {i / (float) steps, (i + 1) / (float) steps, (i + 1) / (float) steps, i / (float) steps};
                    float[] fv = {(j + 1) / (float) steps, (j + 1) / (float) steps, j / (float) steps, j / (float) steps};
                    for (int q = 0; q < 4; q++) {
                        float x = -size / 2 + fu[q] * size, y = cy + size / 2 - fv[q] * size;
                        float z = z0 + clothWave(t, (x + hw) / (2 * hw), (top - y) / full, wind) + 0.02F;
                        bv.vertex(m, x, y, z).color(255, 255, 255, 255).uv(fu[q], fv[q]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                                .normal(n, 0, 0, 1).endVertex();
                    }
                }
            }
        }
        // la etiqueta arriba (solo las letras, con estrellas a los lados), letra a letra sobre la onda
        if (len > 0.45) clothTag(pose, b, look, t, top - 0.27F, z0, hw, full, wind, now);
    }

    private static void clothTag(PoseStack pose, MultiBufferSource b, ClanLooks.Look look, double t, float ty, float z0, float hw, float full, double wind, double now) {
        Font font = Minecraft.getInstance().font;
        String tag = look.tag();
        int[] cps = tag.codePoints().toArray();
        float k = 0.022F;
        float total = font.width(Component.literal(tag).withStyle(s -> s.withBold(true))) * k;
        int outline = 0xFF000000 | ClanEmoteFx.scale(look.rgb(), 0.18F);
        // estrellas a los lados
        for (int end = 0; end < 2; end++) {
            float sx = (end == 0 ? -1 : 1) * (total / 2F + 0.11F);
            float z = z0 + clothWave(t, (sx + hw) / (2 * hw), Math.max(0, (top(ty) - ty) / full), wind) + 0.03F;
            int col = ClanEmoteFx.mix(ClanTagFx.colorAt(look.fx(), end == 0 ? 0 : cps.length - 1, cps.length, now), 0xFFFFFF, 0.3F);
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.STAR);
            pose.pushPose();
            pose.translate(sx, ty, z);
            pose.mulPose(Axis.ZP.rotationDegrees((float) (now * 40 * (end == 0 ? 1 : -1))));
            float tw = (float) (0.75 + 0.25 * Math.sin(now * 5 + end * 2.3));
            ClanEmoteFx.quad(pose, vc, 0, 0, 0, 0.34F * tw, 0.34F * tw, col);
            ClanEmoteFx.quad(pose, vc, 0, 0, 0.002F, 0.14F * tw, 0.14F * tw, 0xFFFFFF);
            pose.popPose();
        }
        float x = -total / 2F;
        for (int i = 0; i < cps.length; i++) {
            String ch = new String(Character.toChars(cps[i]));
            Component one = Component.literal(ch).withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(ClanTagFx.colorAt(look.fx(), i, cps.length, now))));
            float cw = font.width(one) * k;
            float cx = x + cw / 2F;
            double u = (cx + hw) / (2 * hw), vv = (top(ty) - ty) / full;
            float dz = clothWave(t, u, vv, wind);
            float slope = (clothWave(t, u + 0.01, vv, wind) - clothWave(t, u - 0.01, vv, wind)) / (0.02F * 2 * hw);
            float slopeV = (clothWave(t, u, vv + 0.01, wind) - clothWave(t, u, vv - 0.01, wind)) / (0.02F * full);
            pose.pushPose();
            pose.translate(cx, ty, z0 + dz + 0.025F);
            pose.mulPose(Axis.YP.rotation((float) -Math.atan(slope)));
            pose.mulPose(Axis.XP.rotation((float) Math.atan(-slopeV)));
            pose.scale(-k, -k, k);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            font.drawInBatch8xOutline(one.getVisualOrderText(), -cw / k / 2F, -4F, 0xFFFFFFFF, outline, pose.last().pose(), b, LightTexture.FULL_BRIGHT);
            pose.popPose();
            x += cw;
        }
    }

    /** El borde de arriba de la tela para una etiqueta en ty (está 0,27 por debajo). */
    private static float top(float ty) {
        return ty + 0.27F;
    }

    // ----- 1. Corona ------------------------------------------------------------------------------------------------

    private static void fxCrown(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, Frame f, long seed, int[] pal, double now) {
        int warm = 0xFFE6A0;
        double light = e < 3.0 ? 1 : 1 - smooth(3.0, 4.0, e);
        double low = e < 0.6 ? 16 - 16 * easeOut(e / 0.6) : 0;
        if (light > 0.01) {
            // el haz de luz del cielo (4 caras cruzadas) y la luz en el suelo
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.BEAM);
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(i * 45 + (float) (e * 8)));
                ClanEmoteFx.quad(pose, vc, 0, (float) ((low + 16) / 2), 0, 1.9F, (float) (16 - low), ClanEmoteFx.scale(warm, (float) (0.55 * light)));
                ClanEmoteFx.quad(pose, vc, 0, (float) ((low + 16) / 2), 0, 0.7F, (float) (16 - low), ClanEmoteFx.scale(0xFFFFFF, (float) (0.5 * light)));
                pose.popPose();
            }
            if (low < 0.5) {
                ClanEmoteFx.flat(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0, 0.03, 0, 3.2F, 0, ClanEmoteFx.scale(warm, (float) (0.7 * light)));
            }
            // motas doradas que caen despacio dentro del haz
            VertexConsumer motes = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 46; i++) {
                double ph = (e * 0.32 + rnd(seed, i, 1)) % 1.0;
                double y = 4.4 - ph * 4.4;
                if (y > 16 - (16 - low)) {
                    double ang = rnd(seed, i, 2) * Math.PI * 2 + e * 0.6, rad = 0.2 + rnd(seed, i, 3) * 0.75;
                    float tw = (float) (0.5 + 0.5 * Math.sin(e * 7 + i * 1.9));
                    ClanEmoteFx.billboard(pose, motes, Math.cos(ang) * rad, y, Math.sin(ang) * rad, 0.07F, ClanEmoteFx.scale(i % 4 == 0 ? 0xFFFFFF : 0xFFD24A,
                            (float) (tw * light)));
                }
            }
        }
        // la corona: baja girando (0,8–2,2), espera, y al levantarse se posa en la cabeza (2,6); al final se deshace
        if (e > 0.8 && e < 4.95) {
            double y, spin, scale = 1;
            if (e < 2.2) {
                double k = easeOut((e - 0.8) / 1.4);
                y = lerp(4.6, 2.35, k);
                spin = 720 * k;
            } else if (e < 2.6) {
                y = 2.35 + 0.03 * Math.sin(e * 6);
                spin = 720 + 40 * Math.sin((e - 2.2) / 0.4 * Math.PI) * (1 - (e - 2.2) / 0.4);
            } else {
                double k = clamp01((e - 2.6) / 0.25);
                y = lerp(2.35, 1.86, k) - (k >= 1 ? 0.025 * Math.exp(-8 * (e - 2.85)) * Math.sin((e - 2.85) * 30) : 0);
                spin = 720;
            }
            if (e > 4.4) {
                scale = 1 - smooth(4.4, 4.9, e);
                y += 0.4 * smooth(4.4, 4.9, e);
            }
            f.push(pose);
            pose.translate(0, y, 0);
            pose.mulPose(Axis.YP.rotationDegrees((float) spin));
            pose.scale((float) scale, (float) scale, (float) scale);
            crown(pose, b, look, pal, e);
            pose.popPose();
        }
        // al posarse (2,6–3,0): destello, estrella y un aro de brillos
        if (e > 2.6 && e < 3.1) {
            double k = (e - 2.6) / 0.5;
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0, 1.98, 0, (float) (2.6 * (1 - k * 0.6)), ClanEmoteFx.scale(warm, (float) (1 - k)));
            ClanEmoteFx.star(pose, b, 0, 2.0, 0, (float) (1.7 * (1 - k * 0.4)), (float) (k * 90), ClanEmoteFx.scale(0xFFFFFF, (float) (1 - k)));
            ClanEmoteFx.ring(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 1.92, 0.3 + 1.5 * easeOut(k), 0.18 * (1 - k) + 0.03,
                    ClanEmoteFx.scale(0xFFD24A, (float) (1 - k)), 40);
        }
        burst(pose, b, seed + 1, new int[] {0xFFD24A, 0xFFFFFF, 0xFFE6A0}, e, 2.6, 30, 0, 1.95, 0, 2.2, 1.2, 1.3, 0.09F);
        // la aureola detrás de la cabeza y la etiqueta encima (2,7–4,4)
        double halo = smooth(2.7, 3.0, e) * (1 - smooth(4.2, 4.5, e));
        if (halo > 0.01) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 26; i++) {
                double a = i * Math.PI * 2 / 26 + e * 0.8;
                double lx = Math.cos(a) * 0.62, ly = 1.74 + Math.sin(a) * 0.62, lz = -0.32;
                ClanEmoteFx.billboard(pose, vc, f.x(lx, lz), ly, f.z(lx, lz), 0.12F, ClanEmoteFx.scale(i % 2 == 0 ? 0xFFD24A : 0xFFF2C0, (float) (0.8 * halo)));
            }
            ClanEmoteFx.tag(pose, b, look, 0, 2.48, 0, (float) (0.024 * Math.min(1.1, spring((e - 2.75) * 0.9)) * (1 - smooth(4.2, 4.5, e))), now);
        }
        burst(pose, b, seed + 2, new int[] {0xFFD24A, 0xFFFFFF}, e, 4.42, 34, 0, 2.05, 0, 1.6, 1.0, 1.0, 0.08F);
    }

    /** La corona de oro (en local, centrada, de cara hacia delante): aro, seis picos con su perla, gemas y la insignia. */
    private static void crown(PoseStack pose, MultiBufferSource b, ClanLooks.Look look, int[] pal, double e) {
        VertexConsumer vc = ClanEmoteFx.solid(b);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = 0.27F, h = 0.11F;
        int seg = 24;
        int gold = 0xF2C14E, dark = 0xA8781E;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float shade = (float) (0.8 + 0.2 * Math.sin((a0 + a1) / 2));
            int out = ClanEmoteFx.scale(gold, shade), in = ClanEmoteFx.scale(dark, 0.8F);
            float[][] band = {{c0 * r, 0, s0 * r}, {c1 * r, 0, s1 * r}, {c1 * r, h, s1 * r}, {c0 * r, h, s0 * r}};
            for (float[] p : band) vc.vertex(m, p[0], p[1], p[2]).color(out >> 16 & 255, out >> 8 & 255, out & 255, 255).uv(0.5F, 0.5F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, c0, 0, s0).endVertex();
            for (int k = 3; k >= 0; k--) vc.vertex(m, band[k][0] * 0.94F, band[k][1], band[k][2] * 0.94F).color(in >> 16 & 255, in >> 8 & 255, in & 255, 255)
                    .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, -c0, 0, -s0).endVertex();
        }
        // los picos (triángulos) y sus perlas
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.PI / 2, d = 0.22;
            float cx = (float) Math.cos(a) * r, cz = (float) Math.sin(a) * r;
            float lx = (float) Math.cos(a - d) * r, lz = (float) Math.sin(a - d) * r, rx = (float) Math.cos(a + d) * r, rz = (float) Math.sin(a + d) * r;
            float tip = h + 0.15F;
            for (int pass = 0; pass < 2; pass++) {
                float[][] tri = {{lx, h, lz}, {rx, h, rz}, {cx, tip, cz}, {cx, tip, cz}};
                int c = pass == 0 ? gold : dark;
                for (int k = 0; k < 4; k++) {
                    float[] p = tri[pass == 0 ? k : 3 - k];
                    vc.vertex(m, p[0], p[1], p[2]).color(c >> 16 & 255, c >> 8 & 255, c & 255, 255).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(LightTexture.FULL_BRIGHT).normal(n, (float) Math.cos(a), 0, (float) Math.sin(a)).endVertex();
                }
            }
            float s = 0.026F;
            ClanEmoteFx.box(pose, vc, cx - s, tip - s, cz - s, cx + s, tip + s, cz + s, 0xFFF4C8);
        }
        // las gemas (luz de los colores del clan) entre los picos
        VertexConsumer glow = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.PI / 2 + Math.PI / 6;
            float tw = (float) (0.75 + 0.25 * Math.sin(e * 6 + i));
            pose.pushPose();
            pose.translate(Math.cos(a) * (r + 0.012), h * 0.5, Math.sin(a) * (r + 0.012));
            pose.mulPose(Axis.YP.rotation((float) (-a + Math.PI / 2)));
            ClanEmoteFx.quad(pose, glow, 0, 0, 0, 0.09F, 0.09F, ClanEmoteFx.scale(pal[i % pal.length], tw));
            pose.popPose();
        }
        // la insignia delante (local +z = hacia delante)
        pose.pushPose();
        pose.translate(0, h * 0.5 + 0.02, r + 0.02);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        ClanEmoteFx.badge(pose, b, look, 0.2F);
        pose.popPose();
    }

    // ----- 2. Trueno ------------------------------------------------------------------------------------------------

    private static void fxThunder(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, Frame f, long seed, int[] pal, double now) {
        int main = ClanEmoteFx.main(look);
        double body = lift(2, e);
        // la carga (0,1–1,1): chispas que se juntan en una bola de energía delante del pecho
        double ox = f.x(0, 0.45), oy = 1.12 + body, oz = f.z(0, 0.45);
        if (e < 1.15) {
            double charge = smooth(0.1, 1.05, e);
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 34; i++) {
                double t0 = 0.1 + rnd(seed, i, 1) * 0.55, k = clamp01((e - t0) / 0.45);
                if (k <= 0 || k >= 1) continue;
                double ang = rnd(seed, i, 2) * Math.PI * 2 + k * 4, rad = 2.2 * (1 - easeIn(k) * 0.98), h = (rnd(seed, i, 3) - 0.5) * 1.6 * (1 - k);
                ClanEmoteFx.billboard(pose, vc, ox + Math.cos(ang) * rad, oy + h, oz + Math.sin(ang) * rad, 0.1F, ClanEmoteFx.scale(pal[i % pal.length], (float) k));
            }
            float pulse = (float) (0.85 + 0.15 * Math.sin(e * 30));
            ClanEmoteFx.billboard(pose, vc, ox, oy, oz, (float) (0.25 + 0.6 * charge) * pulse, ClanEmoteFx.scale(main, (float) charge));
            ClanEmoteFx.billboard(pose, vc, ox, oy, oz, (float) (0.12 + 0.28 * charge) * pulse, ClanEmoteFx.scale(0xFFFFFF, (float) charge));
            if (e > 0.5) ClanEmoteFx.bolt(pose, b, ox - 0.15, oy, oz, ox + 0.15, oy + 0.05, oz, 4, seed + (long) (e * 25), 0.015F, main, (float) charge);
        }
        // el rayo (1,15 s): del cielo al puño, y luego parpadea hasta 1,9
        double fx0 = f.x(-0.38, 0.06), fy = 2.22 + body, fz = f.z(-0.38, 0.06);
        if (e > 1.13 && e < 1.9) {
            long step = (long) (e / 0.05);
            boolean on = e < 1.55 || rnd(seed, (int) step, 77) < 0.45;
            if (on) {
                float k = (float) (e < 1.55 ? 1 : 0.7);
                ClanEmoteFx.bolt(pose, b, fx0 + 0.8, 14, fz - 0.4, fx0, fy, fz, 16, seed + step, 0.07F, main, k);
                ClanEmoteFx.bolt(pose, b, fx0 + 0.5, 8.5, fz - 0.2, fx0 + 1.6, 6.0, fz + 0.5, 6, seed + step * 3, 0.035F, main, k * 0.8F);
                ClanEmoteFx.bolt(pose, b, fx0 + 0.3, 5.0, fz, fx0 - 0.9, 3.4, fz - 0.3, 5, seed + step * 7, 0.03F, main, k * 0.7F);
            }
        }
        if (e > 1.15 && e < 1.6) {
            double k = (e - 1.15) / 0.45;
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), fx0, fy, fz, (float) (3.4 * (1 - k * 0.5)), ClanEmoteFx.scale(0xFFFFFF, (float) (1 - k)));
            ClanEmoteFx.star(pose, b, fx0, fy, fz, (float) (2.4 * (1 - k * 0.3)), (float) (k * 45), ClanEmoteFx.scale(main, (float) (1 - k)));
        }
        // la onda en el suelo
        shock(pose, b, e, 1.15, main);
        shock(pose, b, e, 3.82, main);
        // la insignia grabada en el suelo (1,15–3,9)
        double emb = (e < 1.15 ? 0 : Math.min(1.1, spring((e - 1.15) * 0.8))) * (1 - smooth(3.4, 3.9, e));
        if (emb > 0.01 && look != null) {
            f.push(pose);
            pose.translate(0, 0.035, 0);
            pose.mulPose(Axis.YP.rotationDegrees((float) (e * 18)));
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(-1, 1, 1); // badge() está pensada para mirar a la cámara (sistema al revés): aquí, del derecho
            ClanEmoteFx.badge(pose, b, look, (float) (2.0 * emb));
            pose.popPose();
            ClanEmoteFx.ring(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0.04, 1.15 * emb, 0.08, ClanEmoteFx.scale(main, (float) (0.9 * Math.min(1, emb))), 48);
        }
        // las chispas eléctricas alrededor (1,3–3,75)
        double arcs = smooth(1.3, 1.5, e) * (1 - smooth(3.5, 3.75, e));
        if (arcs > 0.01) {
            long step = (long) (e / 0.07);
            for (int i = 0; i < 4; i++) {
                double ax = (rnd(seed + step, i, 1) - 0.5) * 0.6, ay = 0.4 + rnd(seed + step, i, 2) * 1.3 + body, az = (rnd(seed + step, i, 3) - 0.5) * 0.3;
                double th = rnd(seed + step, i, 4) * Math.PI * 2, rad = 0.9 + rnd(seed + step, i, 5) * 0.6;
                double bx = Math.cos(th) * rad, by = 0.2 + rnd(seed + step, i, 6) * 2.0, bz = Math.sin(th) * rad;
                ClanEmoteFx.bolt(pose, b, f.x(ax, az), ay, f.z(ax, az), bx, by, bz, 5, seed + step * 31 + i, 0.022F, pal[i % pal.length], (float) arcs);
            }
            // del puño a la insignia
            ClanEmoteFx.bolt(pose, b, fx0, fy + 0.1, fz, 0, 2.62 + body, 0, 4, seed + step * 13, 0.03F, main, (float) arcs);
            // la insignia arriba, con su luz
            double s = Math.min(1.08, spring((e - 1.3) * 0.9));
            float pulse = (float) (0.8 + 0.2 * Math.sin(e * 14));
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0, 2.95 + body, 0, (float) (1.9 * s) * pulse, ClanEmoteFx.scale(main, (float) (0.8 * arcs)));
            ClanEmoteFx.badgeFacing(pose, b, look, 0, 2.95 + body, 0, (float) (0.9 * s * arcs), 0);
            ClanEmoteFx.tag(pose, b, look, 0, 3.58 + body, 0, (float) (0.024 * Math.min(1, s) * arcs), now);
        }
        // el golpe final (3,82): la insignia estalla en chispas
        burst(pose, b, seed + 4, pal, e, 3.8, 36, 0, 2.95, 0, 3.0, 4.0, 0.9, 0.11F);
        burst(pose, b, seed + 6, new int[] {0xFFFFFF, main}, e, 3.84, 24, f.x(0, 0.4), 0.1, f.z(0, 0.4), 2.4, 4.0, 0.7, 0.09F);
    }

    /** Una onda de luz que corre por el suelo desde los pies (t0), con otra blanca detrás. */
    private static void shock(PoseStack pose, MultiBufferSource b, double e, double t0, int rgb) {
        for (int w = 0; w < 2; w++) {
            double k = (e - t0 - w * 0.08) / 0.7;
            if (k <= 0 || k >= 1) continue;
            ClanEmoteFx.ring(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0.05, 0.3 + 4.0 * easeOut(k), 0.4 * (1 - k) + 0.04,
                    ClanEmoteFx.scale(w == 0 ? rgb : 0xFFFFFF, (float) (1 - k)), 56);
        }
    }

    // ----- 3. Fiesta ------------------------------------------------------------------------------------------------

    private static void fxParty(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, Frame f, long seed, int[] pal, double now) {
        double beat = 60 / 128.0, bt = (e - 0.3) / beat;
        int[] bright = look != null && look.fx().mode().equals(ClanTagFx.RAINBOW) ? pal
                : new int[] {pal[0], 0xFF4FA0, 0x4FD8FF, 0xFFE04F, 0x7CFF6B, pal[pal.length > 2 ? 1 : 0]};
        // la pista de baile: 5 x 5 baldosas que se encienden al ritmo
        double floor = smooth(0.25, 0.5, e) * (1 - smooth(4.9, 5.2, e));
        if (floor > 0.01) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.WHITE);
            int k = (int) Math.floor(Math.max(0, bt));
            double frac = Math.max(0, bt) - k;
            f.push(pose);
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    boolean lit = ((i + j + k) & 1) == 0;
                    double in = lit ? 0.22 + 0.6 * Math.exp(-5 * frac) : 0.1;
                    int col = bright[Math.floorMod(i * 3 + j * 5 + k, bright.length)];
                    ClanEmoteFx.flat(pose, vc, i * 0.8, 0.025, j * 0.8, 0.74F, 0, ClanEmoteFx.scale(col, (float) (in * floor)));
                }
            }
            pose.popPose();
        }
        // la bola de discoteca: la insignia arriba (se mece), con su luz y los focos que barren
        double disco = smooth(0.5, 0.9, e) * (1 - smooth(4.85, 5.15, e));
        double dy = 2.85;
        if (disco > 0.01) {
            float hit = (float) Math.pow(Math.max(0, Math.cos(Math.PI * 2 * Math.max(0, bt))), 6);
            VertexConsumer beams = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            for (int i = 0; i < 8; i++) {
                double th = i * Math.PI / 4 + e * 1.6, ph = Math.toRadians(38 + 10 * Math.sin(e * 1.3 + i));
                double ex = Math.cos(th) * Math.cos(ph) * 3.4, ey = dy - Math.sin(ph) * 3.4, ez = Math.sin(th) * Math.cos(ph) * 3.4;
                ClanEmoteFx.segment(pose, beams, 0, dy, 0, ex, ey, ez, 0.22F, ClanEmoteFx.scale(bright[(i + (int) Math.max(0, bt)) % bright.length],
                        (float) ((0.35 + 0.45 * hit) * disco)));
            }
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0, dy, 0, (float) (1.4 + 0.5 * hit), ClanEmoteFx.scale(0xFFFFFF, (float) (0.6 * disco)));
            ClanEmoteFx.badgeFacing(pose, b, look, 0, dy + 0.04 * Math.sin(e * 3), 0, (float) (0.78 * Math.min(1.1, spring((e - 0.5) * 0.9)) * disco),
                    (float) (30 * Math.sin(e * 4)));
            ClanEmoteFx.tag(pose, b, look, 0, dy + 0.62, 0, (float) (0.023 * disco), now);
            // destellos en la bola
            for (int i = 0; i < 4; i++) {
                double a = e * 2.5 + i * 1.57;
                ClanEmoteFx.star(pose, b, Math.cos(a) * 0.3, dy + Math.sin(a * 1.3) * 0.25, Math.sin(a) * 0.3, (float) (0.28 * (0.5 + 0.5 * Math.sin(e * 11 + i))),
                        (float) (e * 90), 0xFFFFFF);
            }
        }
        // el confeti: dos cañonazos (0,2 s y 4,8 s) y la lluvia de arriba (2,0–4,9 s)
        VertexConsumer paper = ClanEmoteFx.solid(b);
        for (int shot = 0; shot < 2; shot++) {
            double t0 = shot == 0 ? 0.2 : 4.8;
            double a = e - t0;
            if (a < 0 || a > 3.6) continue;
            for (int side = 0; side < 2; side++) {
                double sx = side == 0 ? -0.85 : 0.85;
                for (int i = 0; i < 60; i++) {
                    int id = shot * 1000 + side * 100 + i;
                    double vx = -Math.signum(sx) * (0.5 + rnd(seed, id, 1) * 1.1) + (rnd(seed, id, 2) - 0.5) * 0.6;
                    double vy = 4.2 + rnd(seed, id, 3) * 2.4, vz = 0.1 + rnd(seed, id, 4) * 1.1;
                    double drag = (1 - Math.exp(-2.0 * a)) / 2.0;
                    double lx = sx + vx * drag + 0.12 * Math.sin(a * 6 + i) * clamp01(a - 0.6);
                    double ly = 0.2 + vy * drag - 0.5 * 3.2 * a * a * 0.55;
                    double lz = 0.35 + vz * drag;
                    if (ly < 0.03) continue;
                    confetti(pose, paper, f.x(lx, lz), ly, f.z(lx, lz), a * (6 + rnd(seed, id, 5) * 8), bright[i % bright.length], seed + id);
                }
            }
        }
        double rain = smooth(1.9, 2.2, e);
        if (rain > 0 && e < 5.2) {
            for (int i = 0; i < 90; i++) {
                double t0 = 1.9 + rnd(seed, i, 21) * 2.6, a = e - t0;
                if (a < 0) continue;
                double y = 3.6 - a * (0.9 + rnd(seed, i, 22) * 0.5);
                if (y < 0.03) continue;
                double ang = rnd(seed, i, 23) * Math.PI * 2, rad = 0.3 + rnd(seed, i, 24) * 1.5;
                double lx = Math.cos(ang) * rad + 0.15 * Math.sin(a * 3 + i), lz = Math.sin(ang) * rad;
                confetti(pose, paper, f.x(lx, lz), y, f.z(lx, lz), a * 7, bright[i % bright.length], seed + 5000 + i);
            }
        }
        // las estrellas del final
        burst(pose, b, seed + 9, bright, e, 4.8, 30, 0, 2.2, 0, 2.6, 2.0, 0.9, 0.1F);
    }

    /** Un papelito de confeti que da vueltas (se ve de canto y de cara). */
    private static void confetti(PoseStack pose, VertexConsumer vc, double x, double y, double z, double turn, int rgb, long seed) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees((float) (rnd(seed, 0, 1) * 360 + turn * 40)));
        pose.mulPose(Axis.XP.rotationDegrees((float) (turn * 57)));
        pose.mulPose(Axis.ZP.rotationDegrees((float) (rnd(seed, 0, 2) * 360)));
        ClanEmoteFx.quad(pose, vc, 0, 0, 0, 0.075F, 0.04F, rgb);
        pose.popPose();
    }

    // ----- 4. Órbita ------------------------------------------------------------------------------------------------

    private static void fxOrbit(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, Frame f, long seed, int[] pal, double now) {
        int main = ClanEmoteFx.main(look);
        double lev = orbitLift(e);
        // el círculo mágico en el suelo
        double circle = smooth(0.15, 0.6, e) * (1 - smooth(4.4, 4.9, e));
        if (circle > 0.01) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            ClanEmoteFx.ring(pose, vc, 0.03, 1.38, 0.07, ClanEmoteFx.scale(main, (float) (0.9 * circle)), 64);
            ClanEmoteFx.ring(pose, vc, 0.032, 1.06, 0.045, ClanEmoteFx.scale(0xFFFFFF, (float) (0.6 * circle)), 64);
            ClanEmoteFx.flat(pose, vc, 0, 0.028, 0, 2.6F, 0, ClanEmoteFx.scale(main, (float) (0.35 * circle)));
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8 + e * 0.5;
                pose.pushPose();
                pose.translate(Math.cos(a) * 1.22, 0.035, Math.sin(a) * 1.22);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.mulPose(Axis.ZP.rotationDegrees((float) (e * 80 + i * 22)));
                ClanEmoteFx.quad(pose, ClanEmoteFx.light(b, ClanEmoteFx.STAR), 0, 0, 0, 0.22F, 0.22F, ClanEmoteFx.scale(pal[i % pal.length], (float) circle));
                pose.popPose();
            }
        }
        // el remolino de brillos (0,4–3,9)
        if (e > 0.4 && e < 4.2) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            double life = 1.6;
            for (int i = 0; i < 72; i++) {
                double born = 0.4 + i * 0.045;
                double age = e - born;
                if (age < 0) continue;
                age %= life;
                if (e - age > 3.9) continue;
                double ang = i * 2.39996 + age * 3.5, rad = 1.25 - 0.45 * age / life, y = 0.05 + age * 1.9;
                float fade = (float) ((1 - age / life) * (0.6 + 0.4 * Math.sin(e * 12 + i)));
                ClanEmoteFx.billboard(pose, vc, Math.cos(ang) * rad, y, Math.sin(ang) * rad, 0.07F, ClanEmoteFx.scale(pal[i % pal.length], fade));
            }
        }
        // las seis insignias que giran (0,6–4,15) y se juntan arriba (3,6–4,15)
        double conv = smooth(3.6, 4.15, e);
        if (e > 0.6 && e < 4.16) {
            double grow = smooth(0.6, 1.0, e);
            double baseAng = 1.6 * (e - 0.6) + 1.6 * Math.pow(Math.max(0, e - 0.6), 2) / 3.0;
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3 + baseAng;
                double rad = 1.15 * grow * (1 - conv);
                double y = lerp(1.0 + 0.45 * Math.sin(ang * 0.5 + i), 2.75, conv) + lev;
                float size = (float) (0.34 * grow * (1 - 0.6 * conv));
                int col = pal[i % pal.length];
                // la estela
                VertexConsumer trail = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
                for (int k = 1; k <= 10; k++) {
                    double a2 = ang - k * 0.09;
                    double y2 = lerp(1.0 + 0.45 * Math.sin(a2 * 0.5 + i), 2.75, conv) + lev;
                    ClanEmoteFx.billboard(pose, trail, Math.cos(a2) * rad, y2, Math.sin(a2) * rad, size * (float) (1.1 - k * 0.08),
                            ClanEmoteFx.scale(col, (float) (0.75 * (1 - k / 10.0) * grow)));
                }
                ClanEmoteFx.billboard(pose, trail, Math.cos(ang) * rad, y, Math.sin(ang) * rad, size * 1.9F, ClanEmoteFx.scale(col, (float) (0.6 * grow)));
                ClanEmoteFx.badgeFacing(pose, b, look, Math.cos(ang) * rad, y, Math.sin(ang) * rad, size, 0);
            }
        }
        // el destello al juntarse (4,15) y la insignia grande con la etiqueta
        double top = 2.75 + lev;
        if (e > 4.12 && e < 4.7) {
            double k = (e - 4.12) / 0.58;
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0, top, 0, (float) (3.2 * (1 - k * 0.5)), ClanEmoteFx.scale(0xFFFFFF, (float) (1 - k)));
            ClanEmoteFx.star(pose, b, 0, top, 0, (float) (2.4 * (1 - k * 0.3)), (float) (k * 120), ClanEmoteFx.scale(main, (float) (1 - k)));
            VertexConsumer rays = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            pose.pushPose();
            pose.translate(0, top, 0);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            for (int i = 0; i < 14; i++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(i * 360F / 14 + (float) (e * 40)));
                float l = (float) (0.6 + 1.8 * easeOut(k));
                ClanEmoteFx.quad(pose, rays, 0, l * 0.55F, 0, 0.2F, l * 1.1F, ClanEmoteFx.scale(pal[i % pal.length], (float) (1 - k)));
                pose.popPose();
            }
            pose.popPose();
        }
        double big = e < 4.15 ? 0 : Math.min(1.1, spring((e - 4.15) * 0.9)) * (1 - smooth(4.65, 5.0, e));
        if (big > 0.01) {
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0, top, 0, (float) (1.6 * big), ClanEmoteFx.scale(main, (float) (0.7 * big)));
            ClanEmoteFx.badgeFacing(pose, b, look, 0, top, 0, (float) (0.95 * big), 0);
            ClanEmoteFx.tag(pose, b, look, 0, top + 0.66, 0, (float) (0.024 * Math.min(1, big)), now);
        }
        burst(pose, b, seed + 2, pal, e, 4.2, 40, 0, top, 0, 2.2, 1.4, 1.0, 0.09F);
    }
}
