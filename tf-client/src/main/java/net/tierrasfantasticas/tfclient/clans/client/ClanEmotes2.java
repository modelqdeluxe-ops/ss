/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans.client;

import static net.tierrasfantasticas.tfclient.clans.client.ClanEmotes.clamp01;
import static net.tierrasfantasticas.tfclient.clans.client.ClanEmotes.easeIn;
import static net.tierrasfantasticas.tfclient.clans.client.ClanEmotes.easeOut;
import static net.tierrasfantasticas.tfclient.clans.client.ClanEmotes.easeOutBack;
import static net.tierrasfantasticas.tfclient.clans.client.ClanEmotes.lerp;
import static net.tierrasfantasticas.tfclient.clans.client.ClanEmotes.rnd;
import static net.tierrasfantasticas.tfclient.clans.client.ClanEmotes.smooth;
import static net.tierrasfantasticas.tfclient.clans.client.ClanEmotes.spring;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;

/**
 * (1.3.160, el dueño: «mete muchos más emotes, que sean comprables en la tienda del clan») Los diez emotes de pago (5 a
 * 14 en ClanEmote.IDS), con la misma maquinaria que los cinco primeros (ClanEmotes: postura, cuerpo entero y efectos;
 * ClanEmoteFx: lo que se dibuja) y su música (tools/pad/emotes_sonido.py).
 * <ol start="5">
 *   <li><b>Fénix</b>: se encoge con los brazos cruzados y los abre: le salen alas de fuego que aletean, sube un poco, sube
 *   la ceniza encendida; la insignia arriba en un anillo de llamas.</li>
 *   <li><b>Tornado</b>: gira cada vez más deprisa dentro de un torbellino de viento y hojas; la insignia sube por el
 *   centro y, al deshacerse, se queda arriba.</li>
 *   <li><b>Ventisca</b>: lanza el frío con las manos: crecen cristales de hielo en círculo, nieva, y la insignia sale en un
 *   marco de hielo hexagonal; al final, el hielo se rompe.</li>
 *   <li><b>Meteoro</b>: señala el cielo, cae un meteoro detrás, destello, onda y escombros; la insignia grande sobre el
 *   cráter y el puño en alto.</li>
 *   <li><b>Fuegos artificiales</b>: enciende la mecha y suben seis cohetes que estallan en los colores del clan; el último
 *   dibuja la insignia en el cielo.</li>
 *   <li><b>Espadas</b>: invoca ocho espadas de luz que caen en círculo y se clavan; brillan una tras otra y la insignia
 *   sale como un escudo; al final suben y se van.</li>
 *   <li><b>Aura</b>: carga su poder: aura de llamas de colores, el suelo se agrieta, flotan piedras y suelta la energía.</li>
 *   <li><b>Corazones</b>: saluda, lanza un beso y hace un corazón con los brazos; suben corazones de colores y la insignia
 *   dentro de un corazón grande.</li>
 *   <li><b>Concierto</b>: toca una guitarra con altavoces detrás (con la insignia), notas musicales y focos.</li>
 *   <li><b>Portal</b>: abre un portal mágico detrás; la insignia sale de él por encima de su cabeza y el portal se cierra.</li>
 * </ol>
 */
final class ClanEmotes2 {
    private ClanEmotes2() {}

    // ---------------------------------------------------------------------------------------------------------------
    // La postura
    // ---------------------------------------------------------------------------------------------------------------

    static void pose(int id, double e, ClanEmotes.P p) {
        switch (id) {
            case 5 -> { // Fénix: brazos cruzados → alas abiertas que aletean
                if (e < 0.5) {
                    double a = smooth(0, 0.4, e);
                    p.ra = -1.4 * a;
                    p.ry = -0.9 * a;
                    p.la = -1.4 * a;
                    p.ly = 0.9 * a;
                    p.bx = 0.25 * a;
                    p.hx = 0.3 * a;
                } else {
                    double a = smooth(0.5, 0.75, e), flap = 0.18 * Math.sin((e - 0.5) * 5.0);
                    p.ra = lerp(-1.4, -2.3, a);
                    p.ry = lerp(-0.9, 0, a);
                    p.rz = lerp(0, 1.15 + flap, a);
                    p.la = lerp(-1.4, -2.3, a);
                    p.ly = lerp(0.9, 0, a);
                    p.lz = lerp(0, -1.15 - flap, a);
                    p.bx = lerp(0.25, -0.12, a);
                    p.hx = lerp(0.3, -0.4, a);
                    p.rl = 0.1 * a;
                    p.ll = 0.1 * a;
                }
            }
            case 6 -> { // Tornado: brazos abiertos, algo arriba
                double a = smooth(0.2, 0.6, e);
                p.arms(-0.4 * a, 1.25 * a, -0.4 * a, -1.25 * a);
                p.hx = -0.2 * a;
            }
            case 7 -> { // Ventisca: lanza el frío con las manos y luego brazos en V
                if (e < 2.5) {
                    double a = smooth(0.2, 0.6, e), push = 0.12 * Math.sin(e * 9) * smooth(0.6, 0.8, e);
                    p.arms(-1.5 * a + push, 0.3 * a, -1.5 * a - push, -0.3 * a);
                    p.bx = 0.1 * a;
                } else {
                    double a = smooth(2.5, 2.75, e);
                    p.arms(lerp(-1.5, -2.8, a), lerp(0.3, 0.5, a), lerp(-1.5, -2.8, a), lerp(-0.3, -0.5, a));
                    p.hx = -0.35 * a;
                }
            }
            case 8 -> { // Meteoro: señala el cielo, se protege del golpe y puño en alto
                if (e < 1.4) {
                    double a = smooth(0.1, 0.5, e);
                    p.ra = -2.45 * a;
                    p.rz = 0.15 * a;
                    p.la = -0.35 * a;
                    p.lz = -0.9 * a;
                    p.ly = 0.35 * a;
                    p.hx = -0.5 * a;
                } else if (e < 2.4) {
                    double a = smooth(1.4, 1.7, e);
                    p.ra = lerp(-2.45, -1.7, a);
                    p.ry = -0.7 * a;
                    p.la = lerp(-0.35, -1.7, a);
                    p.ly = 0.7 * a;
                    p.lz = lerp(-0.9, 0, a);
                    p.bx = 0.3 * a;
                    p.hx = lerp(-0.5, 0.35, a);
                } else {
                    double a = smooth(2.4, 2.65, e);
                    p.ra = lerp(-1.7, -3.0, a);
                    p.ry = lerp(-0.7, 0, a);
                    p.rz = 0.15 * a;
                    p.la = lerp(-1.7, -0.35, a);
                    p.ly = lerp(0.7, 0.35, a);
                    p.lz = -0.9 * a;
                    p.bx = lerp(0.3, -0.05, a);
                    p.hx = lerp(0.35, -0.3, a);
                }
            }
            case 9 -> { // Fuegos: enciende la mecha y celebra señalando el cielo
                if (e < 0.5) {
                    double a = smooth(0, 0.3, e);
                    p.arms(-0.8 * a, 0, -0.3 * a, -0.2 * a);
                    p.bx = 0.45 * a;
                    p.hx = 0.4 * a;
                } else {
                    double a = smooth(0.5, 0.7, e), alt = Math.sin((e - 0.7) * 4.0);
                    p.ra = lerp(-0.8, -2.6 - 0.25 * alt, a);
                    p.rz = 0.35 * a;
                    p.la = lerp(-0.3, -2.6 + 0.25 * alt, a);
                    p.lz = -0.35 * a;
                    p.bx = lerp(0.45, -0.1, a);
                    p.hx = lerp(0.4, -0.55, a);
                }
            }
            case 10 -> { // Espadas: invoca con el brazo arriba, lo baja y queda en guardia
                if (e < 2.2) {
                    double a = smooth(0.1, 0.6, e);
                    p.ra = -3.0 * a;
                    p.rz = 0.1 * a;
                    p.la = -0.2 * a;
                    p.lz = -0.3 * a;
                    p.hx = -0.45 * a;
                } else {
                    double a = smooth(2.2, 2.35, e);
                    p.ra = lerp(-3.0, -1.55, a);
                    p.rz = lerp(0.1, 0.25, a);
                    p.la = lerp(-0.2, -0.9, a);
                    p.ly = 0.6 * a;
                    p.lz = -0.3;
                    p.hx = lerp(-0.45, 0, a);
                    p.rl = -0.35 * a;
                    p.ll = 0.25 * a;
                }
            }
            case 11 -> { // Aura: se agacha, carga con los puños abajo y grita; suelta la energía
                double a = smooth(0, 0.6, e), shake = e > 0.8 && e < 3.6 ? 0.05 * Math.sin(e * 55) : 0;
                if (e < 3.6) {
                    p.arms(-0.15 * a + shake, 0.55 * a, -0.15 * a - shake, -0.55 * a);
                    p.bx = 0.2 * a * (1 - smooth(1.4, 1.7, e));
                    p.hx = lerp(0.4 * a, -0.55, smooth(1.4, 1.7, e)) + shake;
                    p.rlz = 0.2 * a;
                    p.llz = -0.2 * a;
                } else {
                    double b = smooth(3.6, 3.75, e), o = smooth(3.85, 4.0, e);
                    p.arms(lerp(-1.5, -2.6, o), lerp(-0.5, 0.9, o) * b + 0.55 * (1 - b), lerp(-1.5, -2.6, o), lerp(0.5, -0.9, o) * b - 0.55 * (1 - b));
                    p.ry = -0.6 * b * (1 - o);
                    p.ly = 0.6 * b * (1 - o);
                    p.hx = -0.4;
                }
            }
            case 12 -> { // Corazones: saluda, beso y corazón con los brazos
                if (e < 0.9) {
                    double a = smooth(0, 0.25, e);
                    p.ra = -2.6 * a;
                    p.rz = (0.3 + 0.35 * Math.sin(e * 14)) * a;
                } else if (e < 1.5) {
                    double a = smooth(0.9, 1.05, e), out = smooth(1.25, 1.45, e);
                    p.ra = lerp(lerp(-2.6, -1.95, a), -1.35, out);
                    p.ry = lerp(-0.6 * a, 0, out);
                    p.rz = lerp(0.3, 0, a);
                    p.hx = -0.1 * out;
                } else {
                    double a = smooth(1.5, 1.8, e), sway = 0.06 * Math.sin(e * 4);
                    p.ra = lerp(-1.35, -2.9, a);
                    p.rz = lerp(0, -0.25, a) + sway;
                    p.la = -2.9 * a;
                    p.lz = 0.25 * a + sway;
                    p.hx = -0.15 * a;
                }
            }
            case 13 -> { // Concierto: guitarra en el aire, cabeza al ritmo y salto final
                double a = smooth(0, 0.3, e);
                if (e < 3.65) {
                    double strum = (e % 0.5 < 0.25 ? 0.28 : -0.1) * smooth(0.3, 0.45, e);
                    p.ra = -0.75 * a + strum;
                    p.ry = -0.6 * a;
                    p.la = -1.15 * a;
                    p.ly = 0.55 * a;
                    p.lz = -0.15 * a;
                    p.hx = 0.25 * Math.abs(Math.sin(e * Math.PI * 2)) * smooth(0.5, 0.7, e);
                    p.bx = -0.12 * a;
                } else {
                    double b = smooth(3.65, 3.8, e);
                    p.arms(lerp(-0.75, -3.0, b), 0.2 * b, lerp(-1.15, -3.0, b), -0.2 * b);
                    p.hx = -0.4 * b;
                }
            }
            default -> { // Portal: brazos arriba abiertos invocando; mira la insignia que sale
                double a = smooth(0.3, 0.9, e), down = smooth(3.4, 3.7, e);
                double wig = 0.05 * Math.sin(e * 12) * (1 - down);
                p.arms(lerp(-2.2 * a, -0.2, down) + wig, lerp(0.9 * a, 0.2, down), lerp(-2.2 * a, -0.2, down) - wig, lerp(-0.9 * a, -0.2, down));
                p.hx = lerp(-0.25 * a, -0.6, smooth(2.0, 2.4, e)) * (1 - down) + 0.35 * down * (1 - smooth(4.2, 4.5, e));
                p.bx = 0.25 * down * (1 - smooth(4.2, 4.5, e));
            }
        }
    }

    /** Cuánto sube, baja o flota el cuerpo entero. */
    static double lift(int id, double e) {
        return switch (id) {
            case 5 -> e < 0.5 ? -0.12 * smooth(0, 0.4, e) : lerp(-0.12, 0.4 + 0.06 * Math.sin((e - 0.5) * 5.0), smooth(0.5, 0.9, e)) * (1 - smooth(4.4, 4.9, e));
            case 6 -> 0.3 * smooth(0.6, 1.4, e) * (1 - smooth(3.6, 4.2, e));
            case 8 -> e > 1.9 && e < 2.4 ? -0.1 * Math.sin((e - 1.9) / 0.5 * Math.PI) : 0;
            case 9 -> e < 0.5 ? -0.08 * smooth(0, 0.3, e) : e < 0.9 ? 0.3 * Math.sin((e - 0.5) / 0.4 * Math.PI) : 0;
            case 11 -> e < 0.8 ? -0.15 * smooth(0, 0.6, e) : e < 3.6 ? lerp(-0.15, 0.22, smooth(1.4, 1.8, e)) + 0.02 * Math.sin(e * 40)
                    : lerp(0.22, 0, smooth(3.9, 4.5, e));
            case 13 -> e > 3.65 && e < 4.05 ? 0.35 * Math.sin((e - 3.65) / 0.4 * Math.PI) : 0;
            default -> 0;
        };
    }

    static void body(int id, double e, PoseStack pose, float yaw) {
        pose.translate(0, lift(id, e), 0);
        if (id == 6) {
            // el giro del tornado: acelera y frena (3 vueltas)
            double k = smooth(0.5, 3.9, e);
            double spin = 1080 * k * k * (3 - 2 * k) / 1.0;
            if (spin > 0 && spin < 1080) pose.mulPose(Axis.YP.rotationDegrees((float) spin));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Los efectos
    // ---------------------------------------------------------------------------------------------------------------

    static void fx(int id, double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now) {
        int main = ClanEmoteFx.main(look);
        switch (id) {
            case 5 -> phoenix(e, pose, b, look, f, seed, pal, now, main);
            case 6 -> tornado(e, pose, b, look, f, seed, pal, now, main);
            case 7 -> blizzard(e, pose, b, look, f, seed, pal, now, main);
            case 8 -> meteor(e, pose, b, look, f, seed, pal, now, main);
            case 9 -> fireworks(e, pose, b, look, f, seed, pal, now, main);
            case 10 -> swords(e, pose, b, look, f, seed, pal, now, main);
            case 11 -> aura(e, pose, b, look, f, seed, pal, now, main);
            case 12 -> hearts(e, pose, b, look, f, seed, pal, now, main);
            case 13 -> concert(e, pose, b, look, f, seed, pal, now, main);
            default -> portal(e, pose, b, look, f, seed, pal, now, main);
        }
    }

    /** La insignia con su luz y la etiqueta encima (de cara a la cámara), con un muelle de entrada desde t0. */
    private static void crest(PoseStack pose, MultiBufferSource b, ClanLooks.Look look, double e, double t0, double t1, double x, double y, double z,
                              float size, int glowRgb, double now) {
        double s = e < t0 ? 0 : Math.min(1.1, spring((e - t0) * 0.9)) * (1 - smooth(t1 - 0.35, t1, e));
        if (s < 0.01) return;
        ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), x, y, z, (float) (size * 1.8 * s), ClanEmoteFx.scale(glowRgb, (float) (0.75 * Math.min(1, s))));
        ClanEmoteFx.badgeFacing(pose, b, look, x, y, z, (float) (size * s), 0);
        ClanEmoteFx.tag(pose, b, look, x, y + size * 0.72, z, (float) (0.024 * Math.min(1, s)), now);
    }

    // ----- 5. Fénix -------------------------------------------------------------------------------------------------

    private static void phoenix(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        double lift = lift(5, e);
        double open = e < 0.5 ? 0 : easeOutBack((e - 0.5) / 0.45) * (1 - smooth(4.35, 4.85, e));
        if (open > 0.01) {
            double flap = 16 * Math.sin((e - 0.5) * 5.0);
            f.push(pose);
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            // (las alas, a la espalda y hacia fuera: cada pluma sale del hombro con su ángulo φ desde la horizontal hacia
            // fuera; de un poco hacia abajo a casi arriba)
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * 0.22, 1.4 + lift, -0.3);
                // (barridas hacia atrás: con side * 14 las dos puntas quedan detrás del cuerpo)
                pose.mulPose(Axis.YP.rotationDegrees(side * 14));
                for (int k = 0; k < 11; k++) {
                    double phi = -25 + k * 10 + flap * (0.6 + k * 0.05);
                    float len = (float) ((1.05 + 0.09 * k) * open * (0.92 + 0.08 * Math.sin(e * 9 + k)));
                    // (de fuego, no blancas: las plumas se suman donde se cruzan, así que cada una va suave)
                    int col = ClanEmoteFx.mix(ClanEmoteFx.mix(0xFFA020, 0xFF3A08, k / 10F), main, k > 7 ? 0.3F : 0F);
                    pose.pushPose();
                    pose.mulPose(Axis.ZP.rotationDegrees((float) (side * -(90 - phi))));
                    ClanEmoteFx.quad(pose, vc, 0, len * 0.5F, 0, 0.3F + 0.025F * k, len, ClanEmoteFx.scale(col, 0.5F));
                    ClanEmoteFx.quad(pose, vc, 0, len * 0.55F, 0.002F, 0.1F, len * 0.7F, ClanEmoteFx.scale(0xFFC860, 0.16F));
                    pose.popPose();
                }
                pose.popPose();
            }
            pose.popPose();
        }
        // ceniza encendida que sube
        if (e > 0.55 && e < 4.9) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 46; i++) {
                double ph = (e * (0.35 + rnd(seed, i, 1) * 0.3) + rnd(seed, i, 2)) % 1.0;
                double lx = (rnd(seed, i, 3) - 0.5) * 3.4 * (0.4 + ph * 0.6), ly = 0.6 + ph * 3.2 + lift, lz = -0.3 + (rnd(seed, i, 4) - 0.5) * 0.8;
                float fade = (float) (Math.sin(Math.PI * ph) * (0.6 + 0.4 * Math.sin(e * 15 + i)) * Math.min(1, open + 0.2));
                ClanEmoteFx.billboard(pose, vc, f.x(lx, lz), ly, f.z(lx, lz), 0.07F, ClanEmoteFx.scale(i % 3 == 0 ? main : i % 3 == 1 ? 0xFFB040 : 0xFF5020, fade));
            }
            // el anillo de fuego en el suelo
            ClanEmoteFx.ring(pose, vc, 0.04, 1.1 + 0.05 * Math.sin(e * 10), 0.18, ClanEmoteFx.scale(0xFF7A20, (float) (0.8 * Math.min(1, open))), 48);
        }
        // la insignia arriba, en un anillo de llamas
        double top = 2.95 + lift;
        double ring = e < 1.0 ? 0 : smooth(1.0, 1.3, e) * (1 - smooth(4.35, 4.85, e));
        if (ring > 0.01) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 18; i++) {
                double a = i * Math.PI * 2 / 18 + e * 2.2;
                double r = 0.62 + 0.05 * Math.sin(e * 12 + i);
                ClanEmoteFx.billboard(pose, vc, Math.cos(a) * r * 0.9, top + Math.sin(a) * r, Math.sin(a) * 0.05, (float) (0.22 + 0.06 * Math.sin(e * 14 + i)),
                        ClanEmoteFx.scale(i % 2 == 0 ? 0xFF8A30 : 0xFFD050, (float) ring));
            }
        }
        crest(pose, b, look, e, 1.0, 4.9, 0, top, 0, 0.82F, 0xFF8A30, now);
        ClanEmotes.burst(pose, b, seed + 1, new int[] {0xFFB040, 0xFF5020, main}, e, 0.5, 30, 0, 1.4, 0, 3.0, 2.0, 1.0, 0.1F);
        ClanEmotes.burst(pose, b, seed + 2, new int[] {0xFFB040, 0xFF5020, main}, e, 4.4, 40, 0, 1.6 + lift, 0, 2.6, 1.5, 0.9, 0.1F);
    }

    // ----- 6. Tornado -----------------------------------------------------------------------------------------------

    private static void tornado(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        double on = smooth(0.2, 0.8, e) * (1 - smooth(3.7, 4.3, e));
        if (on > 0.01) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int ring = 0; ring < 9; ring++) {
                double h = ring * 0.45, r = 0.5 + ring * 0.17;
                double speed = 8.0 - ring * 0.55;
                for (int i = 0; i < 18; i++) {
                    double a = i * Math.PI * 2 / 18 + e * speed + ring * 0.7;
                    double rr = r * (0.9 + 0.1 * Math.sin(e * 6 + i));
                    float tw = (float) (0.6 + 0.4 * Math.sin(e * 11 + i * 1.7 + ring));
                    // (los de delante, más suaves: que se vea al jugador dentro del torbellino)
                    double px = Math.cos(a) * rr, pz = Math.sin(a) * rr;
                    float k = f.front(px, pz) > 0.15 && h < 2.2 ? 0.45F : 1F;
                    ClanEmoteFx.billboard(pose, vc, px, h + 0.08 * Math.sin(a * 3 + e * 4), pz, 0.22F,
                            ClanEmoteFx.scale(i % 3 == 0 ? pal[(i + ring) % pal.length] : 0xE8F0F8, (float) (on * tw * k)));
                }
            }
            // las estelas del viento (trozos de arco que giran)
            VertexConsumer streak = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            for (int i = 0; i < 26; i++) {
                double h = rnd(seed, i, 11) * 3.8, r = 0.5 + h * 0.38;
                double a = rnd(seed, i, 12) * Math.PI * 2 + e * (8 - h * 1.2);
                double a2 = a - 0.5;
                ClanEmoteFx.segment(pose, streak, Math.cos(a) * r, h, Math.sin(a) * r, Math.cos(a2) * r, h - 0.08, Math.sin(a2) * r, 0.07F,
                        ClanEmoteFx.scale(0xF0F6FF, (float) (0.8 * on)));
            }
            // las hojas que vuelan dentro
            VertexConsumer leaf = ClanEmoteFx.solid(b);
            for (int i = 0; i < 30; i++) {
                double ph = (e * 0.5 + rnd(seed, i, 1)) % 1.0;
                double a = rnd(seed, i, 2) * Math.PI * 2 + e * (6 - ph * 3);
                double r = 0.5 + ph * 1.1, y = ph * 3.8;
                ClanEmotes.confetti(pose, leaf, Math.cos(a) * r, y, Math.sin(a) * r, e * 5 + i, i % 3 == 0 ? 0x4F8F2A : i % 3 == 1 ? 0x6FB23A : 0xA88A3A, seed + i);
            }
            // el polvo en el suelo (con su búfer pedido otra vez: el de antes ya se cerró al pedir el de las hojas)
            ClanEmoteFx.ring(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0.04, 1.0 + 0.6 * on, 0.5, ClanEmoteFx.scale(0xB8A890, (float) (0.4 * on)), 48);
        }
        // la insignia sube por el centro (0,8–3,8) y se queda arriba
        if (e > 0.8 && e < 3.9) {
            double k = (e - 0.8) / 3.0;
            double y = 0.6 + 2.6 * easeOut(k);
            ClanEmoteFx.badgeFacing(pose, b, look, 0, y, 0, (float) (0.35 + 0.35 * k), (float) (e * 300));
        }
        crest(pose, b, look, e, 3.85, 4.8, 0, 3.2, 0, 0.9F, main, now);
        ClanEmotes.burst(pose, b, seed + 3, pal, e, 3.85, 34, 0, 3.2, 0, 2.6, 2.0, 0.9, 0.1F);
    }

    // ----- 7. Ventisca ----------------------------------------------------------------------------------------------

    private static void blizzard(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        int ice = 0xA8E8FF;
        // los cristales que crecen en círculo
        double grow = e < 0.4 ? 0 : easeOutBack((e - 0.4) / 0.9) * (1 - smooth(4.4, 4.85, e));
        if (grow > 0.01) {
            VertexConsumer vc = ClanEmoteFx.solid(b);
            VertexConsumer glow = null;
            // (solo a los lados y detrás: los de delante tapaban al jugador)
            f.push(pose);
            for (int i = 0; i < 9; i++) {
                double a = Math.toRadians(-25 + i * (230.0 / 8));
                double r = 1.25 + rnd(seed, i, 1) * 0.25, h = (0.8 + rnd(seed, i, 2) * 0.7) * Math.max(0, Math.min(1.15, grow - i * 0.03));
                if (h < 0.02) continue;
                pose.pushPose();
                pose.translate(Math.cos(a) * r, 0, -Math.sin(a) * r);
                pose.mulPose(Axis.YP.rotation((float) a));
                pose.mulPose(Axis.ZP.rotationDegrees((float) (-12 - rnd(seed, i, 3) * 12)));
                float w = 0.09F + (float) rnd(seed, i, 4) * 0.05F;
                ClanEmoteFx.box(pose, vc, -w, 0, -w, w, (float) h, w, ice);
                ClanEmoteFx.box(pose, vc, -w * 0.6F, (float) h, -w * 0.6F, w * 0.6F, (float) h + 0.12F, w * 0.6F, 0xF0FCFF);
                pose.popPose();
            }
            pose.popPose();
            glow = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 9; i++) {
                double a = Math.toRadians(-25 + i * (230.0 / 8)), r = 1.25 + rnd(seed, i, 1) * 0.25;
                double lx = Math.cos(a) * r, lz = -Math.sin(a) * r;
                ClanEmoteFx.billboard(pose, glow, f.x(lx, lz), 0.6, f.z(lx, lz), (float) (0.9 * Math.min(1, grow)), ClanEmoteFx.scale(ice, 0.35F));
            }
            ClanEmoteFx.ring(pose, glow, 0.04, 1.3, 0.35, ClanEmoteFx.scale(0xD8F4FF, (float) (0.55 * Math.min(1, grow))), 48);
            ClanEmoteFx.flat(pose, glow, 0, 0.03, 0, 3.4F, 0, ClanEmoteFx.scale(0x9AD8F0, (float) (0.3 * Math.min(1, grow))));
        }
        // la nieve
        if (e > 0.3 && e < 5.0) {
            double on = smooth(0.3, 0.8, e) * (1 - smooth(4.5, 5.0, e));
            for (int i = 0; i < 60; i++) {
                double ph = (e * (0.22 + rnd(seed, i, 5) * 0.15) + rnd(seed, i, 6)) % 1.0;
                double x = (rnd(seed, i, 7) - 0.5) * 4.4 + 0.3 * Math.sin(e * 1.5 + i), z = (rnd(seed, i, 8) - 0.5) * 3.0, y = 4.2 - ph * 4.2;
                ClanEmoteFx.star(pose, b, x, y, z, (float) (0.08 + rnd(seed, i, 9) * 0.07), (float) (e * 60 + i * 20), ClanEmoteFx.scale(0xF4FBFF, (float) on));
            }
        }
        // la insignia en un marco de hielo hexagonal (2,5 s)
        double frame = e < 2.5 ? 0 : Math.min(1.1, spring((e - 2.5) * 0.9)) * (1 - smooth(4.4, 4.85, e));
        if (frame > 0.01) {
            double cy = 2.95;
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            pose.pushPose();
            pose.translate(0, cy, 0);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            float r = (float) (0.7 * frame);
            for (int i = 0; i < 6; i++) {
                double a0 = i * Math.PI / 3 + Math.PI / 6, a1 = (i + 1) * Math.PI / 3 + Math.PI / 6;
                float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
                pose.pushPose();
                pose.translate((x0 + x1) / 2, (y0 + y1) / 2, 0);
                pose.mulPose(Axis.ZP.rotation((float) Math.atan2(y1 - y0, x1 - x0) - (float) Math.PI / 2));
                ClanEmoteFx.quad(pose, vc, 0, 0, 0, 0.12F, r * 1.05F, ClanEmoteFx.scale(0xC8F0FF, 0.9F));
                pose.popPose();
            }
            pose.popPose();
        }
        crest(pose, b, look, e, 2.5, 4.85, 0, 2.95, 0, 0.78F, ice, now);
        // al final, el hielo se rompe
        ClanEmotes.burst(pose, b, seed + 4, new int[] {ice, 0xFFFFFF, 0xD8F4FF}, e, 4.4, 50, 0, 0.6, 0, 3.4, 4.0, 0.8, 0.09F);
    }

    // ----- 8. Meteoro -----------------------------------------------------------------------------------------------

    private static void meteor(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        // del cielo (atrás a la derecha de quien mira) al suelo, detrás del jugador
        double sx = 6, sy = 13, sz = -13, ix = -2.8, iz = -7.5;
        double t = (e - 0.6) / 1.4;
        if (t > 0 && t < 1) {
            double k = easeIn(t);
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 18; i >= 0; i--) {
                double kk = Math.max(0, k - i * 0.03);
                double lx = lerp(sx, ix, kk), ly = lerp(sy, 0.5, kk), lz = lerp(sz, iz, kk);
                float s = (float) (2.6 - i * 0.11);
                ClanEmoteFx.billboard(pose, vc, f.x(lx, lz), ly, f.z(lx, lz), s, ClanEmoteFx.scale(i == 0 ? 0xFFF0C0 : ClanEmoteFx.mix(0xFF7A20, main, i / 14F),
                        (float) (1 - i / 15.0)));
            }
            double lx = lerp(sx, ix, k), ly = lerp(sy, 0.5, k), lz = lerp(sz, iz, k);
            ClanEmoteFx.billboard(pose, vc, f.x(lx, lz), ly, f.z(lx, lz), 1.4F, 0xFFFFFF);
            ClanEmoteFx.star(pose, b, f.x(lx, lz), ly, f.z(lx, lz), 2.6F, (float) (e * 200), 0xFFC060);
        }
        double cx = f.x(ix, iz), cz = f.z(ix, iz);
        if (e > 2.0 && e < 2.6) {
            double k = (e - 2.0) / 0.6;
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), cx, 1.0, cz, (float) (9 * (1 - k * 0.5)), ClanEmoteFx.scale(0xFFE0B0, (float) (1 - k)));
            ClanEmoteFx.star(pose, b, cx, 1.2, cz, (float) (5 * (1 - k * 0.3)), (float) (k * 50), ClanEmoteFx.scale(main, (float) (1 - k)));
        }
        // la onda: en el cráter y la que llega a los pies
        if (e > 2.0 && e < 3.2) {
            double k = (e - 2.0) / 1.2;
            pose.pushPose();
            pose.translate(cx, 0, cz);
            ClanEmoteFx.ring(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0.06, 0.5 + 9 * easeOut(k), 0.8 * (1 - k) + 0.1, ClanEmoteFx.scale(0xFFB060, (float) (1 - k)), 64);
            pose.popPose();
        }
        // los escombros
        if (e > 2.0 && e < 3.6) {
            VertexConsumer rock = ClanEmoteFx.solid(b);
            double a = e - 2.0;
            for (int i = 0; i < 22; i++) {
                double th = rnd(seed, i, 1) * Math.PI * 2, sp = 2 + rnd(seed, i, 2) * 3;
                double px = cx + Math.cos(th) * sp * a, pz = cz + Math.sin(th) * sp * a, py = 0.3 + (4 + rnd(seed, i, 3) * 4) * a - 4.9 * a * a;
                if (py < 0) continue;
                float s = (float) (0.08 + rnd(seed, i, 4) * 0.1);
                pose.pushPose();
                pose.translate(px, py, pz);
                pose.mulPose(Axis.XP.rotationDegrees((float) (a * 400 + i * 30)));
                ClanEmoteFx.box(pose, rock, -s, -s, -s, s, s, s, i % 3 == 0 ? 0xFF7A20 : 0x4A3A30);
                pose.popPose();
            }
        }
        // el fuego que queda en el cráter
        if (e > 2.1 && e < 4.9) {
            double on = smooth(2.1, 2.4, e) * (1 - smooth(4.4, 4.9, e));
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 16; i++) {
                double ph = (e * 0.9 + rnd(seed, i, 5)) % 1.0;
                double a = rnd(seed, i, 6) * Math.PI * 2, r = rnd(seed, i, 7) * 1.4;
                ClanEmoteFx.billboard(pose, vc, cx + Math.cos(a) * r, 0.2 + ph * 1.6, cz + Math.sin(a) * r, (float) (0.35 * (1 - ph)),
                        ClanEmoteFx.scale(i % 2 == 0 ? 0xFF7A20 : 0xFFC050, (float) (on * (1 - ph))));
            }
        }
        // la insignia grande sobre el cráter, y la onda que llega al jugador
        crest(pose, b, look, e, 2.35, 5.0, cx, 4.0, cz, 2.6F, main, now);
        ClanEmotes.shock(pose, b, e, 2.25, main);
    }

    // ----- 9. Fuegos artificiales -----------------------------------------------------------------------------------

    private static void fireworks(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        // (detrás y no muy altos: con la cámara de frente, más arriba de 5,5 se salían de la imagen)
        double[][] rockets = {{-2.2, -1.5, 0.5, 4.2}, {2.1, -1.6, 0.9, 4.6}, {-3.2, -3.0, 1.3, 5.2}, {3.1, -2.8, 1.7, 4.8}, {-1.2, -3.6, 2.1, 5.6}, {1.3, -3.4, 2.5, 5.0}};
        for (int r = 0; r < rockets.length; r++) {
            double lx = rockets[r][0], lz = rockets[r][1], t0 = rockets[r][2], top = rockets[r][3];
            double wx = f.x(lx, lz), wz = f.z(lx, lz);
            double up = (e - t0) / 0.9;
            int col = pal[r % pal.length];
            if (up > 0 && up < 1) {
                double y = top * easeOut(up);
                VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
                for (int k = 0; k < 12; k++) {
                    double yy = Math.max(0, y - k * 0.25);
                    ClanEmoteFx.billboard(pose, vc, wx + 0.05 * Math.sin(k + e * 30), yy, wz, (float) (0.38 - k * 0.025), ClanEmoteFx.scale(k == 0 ? 0xFFFFFF : 0xFFC060,
                            (float) (1 - k / 13.0)));
                }
            }
            if (e >= t0 + 0.9 && e < t0 + 0.9 + 0.25) {
                double k = (e - t0 - 0.9) / 0.25;
                ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), wx, top, wz, (float) (3.2 * (1 - k * 0.4)), ClanEmoteFx.scale(col, (float) (1 - k)));
            }
            ClanEmotes.burst(pose, b, seed + r * 101, new int[] {col, ClanEmoteFx.mix(col, 0xFFFFFF, 0.5F), 0xFFFFFF}, e, t0 + 0.9, 70, wx, top, wz, 4.0, 1.6, 1.5, 0.2F);
        }
        // el gran final: la insignia en el cielo
        double t0 = 3.3;
        if (e > t0 - 0.9 && e < t0) {
            double y = 5.4 * easeOut((e - t0 + 0.9) / 0.9);
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), f.x(0, -3.5), y, f.z(0, -3.5), 0.45F, 0xFFFFFF);
        }
        crest(pose, b, look, e, t0, 5.2, f.x(0, -3.5), 5.4, f.z(0, -3.5), 2.4F, main, now);
        ClanEmotes.burst(pose, b, seed + 7, pal, e, t0, 90, f.x(0, -3.5), 5.4, f.z(0, -3.5), 4.0, 1.4, 1.7, 0.22F);
        // la mecha al principio
        if (e < 0.6) {
            ClanEmoteFx.star(pose, b, f.x(0, 0.55), 0.15, f.z(0, 0.55), (float) (0.3 + 0.1 * Math.sin(e * 40)), (float) (e * 300), 0xFFC060);
        }
    }

    // ----- 10. Espadas ----------------------------------------------------------------------------------------------

    private static void swords(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        int n = 8;
        double leave = smooth(4.2, 4.85, e);
        for (int i = 0; i < n; i++) {
            double t0 = 0.8 + i * 0.12;
            if (e < t0) continue;
            double fall = clamp01((e - t0) / 0.35);
            // (a los lados y detrás: delante tapaban al jugador)
            double la = Math.toRadians(-30 + i * (240.0 / (n - 1)));
            double r = 1.7;
            double lx = Math.cos(la) * r, lz = -Math.sin(la) * r;
            double a = Math.atan2(f.z(lx, lz), f.x(lx, lz));
            double y = lerp(9, 0.0, easeIn(fall)) + leave * 9 * leave;
            double glowOn = e > 2.2 + i * 0.05 ? smooth(2.2 + i * 0.05, 2.4 + i * 0.05, e) : 0;
            pose.pushPose();
            pose.translate(Math.cos(a) * r, y, Math.sin(a) * r);
            pose.mulPose(Axis.YP.rotation((float) -a));
            pose.mulPose(Axis.ZP.rotationDegrees(-8));
            VertexConsumer vc = ClanEmoteFx.solid(b);
            // hoja, guarda y mango (la punta en el suelo)
            ClanEmoteFx.box(pose, vc, -0.035F, 0F, -0.012F, 0.035F, 1.05F, 0.012F, ClanEmoteFx.mix(0xD8E8F8, main, 0.15F));
            ClanEmoteFx.box(pose, vc, -0.17F, 1.05F, -0.03F, 0.17F, 1.12F, 0.03F, 0xD9A441);
            ClanEmoteFx.box(pose, vc, -0.03F, 1.12F, -0.03F, 0.03F, 1.38F, 0.03F, 0x5A3A22);
            ClanEmoteFx.box(pose, vc, -0.045F, 1.38F, -0.045F, 0.045F, 1.45F, 0.045F, 0xF2C14E);
            VertexConsumer g = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            ClanEmoteFx.quad(pose, g, 0, 0.55F, 0.02F, 0.22F, 1.2F, ClanEmoteFx.scale(ClanEmoteFx.mix(0x9AD8FF, main, 0.4F), (float) (0.35 + 0.55 * glowOn)));
            pose.popPose();
            // al clavarse: chispas y una onda pequeña
            if (fall >= 1 && e < t0 + 0.7) {
                double k = (e - t0 - 0.35) / 0.35;
                if (k >= 0 && k < 1) {
                    pose.pushPose();
                    pose.translate(Math.cos(a) * r, 0, Math.sin(a) * r);
                    ClanEmoteFx.ring(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0.04, 0.1 + 0.8 * k, 0.15 * (1 - k), ClanEmoteFx.scale(0xC8E8FF, (float) (1 - k)), 24);
                    pose.popPose();
                }
            }
        }
        ClanEmotes.burst(pose, b, seed + 2, new int[] {0xC8E8FF, 0xFFFFFF, main}, e, 1.2, 30, 0, 0.3, 0, 3.0, 4.0, 0.7, 0.08F);
        crest(pose, b, look, e, 2.4, 4.85, 0, 2.95, 0, 0.95F, 0x9AD8FF, now);
        if (e > 2.4 && e < 4.6) {
            double k = smooth(2.4, 2.7, e) * (1 - smooth(4.3, 4.6, e));
            VertexConsumer rays = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            pose.pushPose();
            pose.translate(0, 2.95, 0);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            for (int i = 0; i < 8; i++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(i * 45 + 22.5F));
                ClanEmoteFx.quad(pose, rays, 0, 1.0F, -0.02F, 0.16F, 1.2F, ClanEmoteFx.scale(0xC8E8FF, (float) (0.7 * k)));
                pose.popPose();
            }
            pose.popPose();
        }
    }

    // ----- 11. Aura -------------------------------------------------------------------------------------------------

    private static void aura(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        double lift = lift(11, e);
        double power = smooth(0.6, 1.7, e) * (1 - smooth(3.7, 4.2, e));
        if (power > 0.01) {
            VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            long step = (long) (e / 0.05);
            for (int i = 0; i < 30; i++) {
                double a = i * Math.PI * 2 / 30 + rnd(seed + step, i, 1) * 0.2;
                double r = 0.4 + rnd(seed + step, i, 2) * 0.18;
                float len = (float) ((1.2 + rnd(seed + step, i, 3) * 1.3) * power);
                int col = pal[i % pal.length];
                ClanEmoteFx.segment(pose, vc, Math.cos(a) * r * 1.15, lift, Math.sin(a) * r * 1.15, Math.cos(a) * r, lift + len, Math.sin(a) * r, 0.09F,
                        ClanEmoteFx.scale(col, (float) (0.42 * power)));
            }
            ClanEmoteFx.ring(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0.04, 0.9, 0.5, ClanEmoteFx.scale(main, (float) (0.6 * power)), 40);
        }
        // las grietas del suelo (1,6 s)
        double crack = smooth(1.6, 1.9, e) * (1 - smooth(4.3, 4.8, e));
        if (crack > 0.01) {
            VertexConsumer dark = ClanEmoteFx.solid(b);
            VertexConsumer glow = null;
            for (int c = 0; c < 10; c++) {
                double a = c * Math.PI * 2 / 10 + rnd(seed, c, 4) * 0.4;
                double x = 0, z = 0;
                for (int s = 0; s < 4; s++) {
                    double len = (0.35 + rnd(seed, c * 10 + s, 5) * 0.3) * crack;
                    double aa = a + (rnd(seed, c * 10 + s, 6) - 0.5) * 0.8;
                    double x2 = x + Math.cos(aa) * len, z2 = z + Math.sin(aa) * len;
                    pose.pushPose();
                    pose.translate((x + x2) / 2, 0.02, (z + z2) / 2);
                    pose.mulPose(Axis.YP.rotation((float) -aa));
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    ClanEmoteFx.quad(pose, dark, 0, 0, 0, (float) len, 0.07F - s * 0.012F, 0x140A04);
                    pose.popPose();
                    x = x2;
                    z = z2;
                }
            }
            glow = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            ClanEmoteFx.flat(pose, glow, 0, 0.025, 0, 3.6F, 0, ClanEmoteFx.scale(main, (float) (0.35 * crack)));
        }
        // piedras que flotan (1,4–3,6) y caen
        if (e > 1.4 && e < 4.4) {
            VertexConsumer rock = ClanEmoteFx.solid(b);
            for (int i = 0; i < 12; i++) {
                double a = rnd(seed, i, 7) * Math.PI * 2, r = 1.2 + rnd(seed, i, 8) * 0.9;
                double rise = smooth(1.4 + i * 0.05, 2.4 + i * 0.05, e) * (0.6 + rnd(seed, i, 9) * 1.4);
                double fall = e > 3.6 ? Math.min(rise + 0.1, 4.9 * (e - 3.6) * (e - 3.6)) : 0;
                double y = rise - fall + 0.06 * Math.sin(e * 3 + i);
                if (y < 0.02) continue;
                float s = (float) (0.07 + rnd(seed, i, 10) * 0.08);
                pose.pushPose();
                pose.translate(Math.cos(a) * r, y, Math.sin(a) * r);
                pose.mulPose(Axis.YP.rotationDegrees((float) (e * 60 + i * 30)));
                pose.mulPose(Axis.XP.rotationDegrees((float) (e * 40 + i * 50)));
                ClanEmoteFx.box(pose, rock, -s, -s, -s, s, s, s, i % 2 == 0 ? 0x7A7468 : 0x5E5A52);
                pose.popPose();
            }
        }
        // los pulsos de poder y la descarga final
        for (double t0 : new double[] {1.6, 2.4, 3.2}) ClanEmotes.shock(pose, b, e, t0, main);
        if (e > 3.85 && e < 4.3) {
            double k = (e - 3.85) / 0.45;
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), 0, 1.3 + lift, 0, (float) (4 * (1 - k * 0.4)), ClanEmoteFx.scale(0xFFFFFF, (float) (1 - k)));
        }
        ClanEmotes.burst(pose, b, seed + 5, pal, e, 3.85, 50, 0, 1.4 + lift, 0, 4.0, 2.5, 0.9, 0.1F);
        crest(pose, b, look, e, 3.9, 4.8, 0, 2.95, 0, 0.9F, main, now);
    }

    // ----- 12. Corazones --------------------------------------------------------------------------------------------

    private static void hearts(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        int[] cols = {0xFF4F7A, 0xFF8AB0, main, 0xFF2F55, ClanEmoteFx.mix(main, 0xFFFFFF, 0.4F)};
        // corazones que suben
        if (e > 0.2) {
            double on = 1 - smooth(4.2, 4.6, e);
            for (int i = 0; i < 26; i++) {
                double born = 0.2 + rnd(seed, i, 1) * 3.6, age = e - born;
                if (age < 0 || age > 1.8) continue;
                double a = rnd(seed, i, 2) * Math.PI * 2, r = 0.5 + rnd(seed, i, 3) * 0.9;
                double x = Math.cos(a) * r + 0.15 * Math.sin(age * 4 + i), z = Math.sin(a) * r, y = 0.6 + age * 1.4;
                float s = (float) ((0.16 + rnd(seed, i, 4) * 0.18) * Math.min(1, age * 4));
                heart(pose, b, x, y, z, s, (float) (8 * Math.sin(age * 5 + i)), ClanEmoteFx.scale(cols[i % cols.length], (float) ((1 - age / 1.8) * on)));
            }
        }
        // el beso: un corazón vuela hacia delante (1,4 s)
        double k = (e - 1.4) / 0.8;
        if (k > 0 && k < 1) {
            double lz = 0.4 + 2.2 * easeOut(k);
            heart(pose, b, f.x(-0.2, lz), 1.6 + 0.3 * k, f.z(-0.2, lz), (float) (0.2 + 0.5 * k), 0, ClanEmoteFx.scale(0xFF4F7A, (float) (1 - k * 0.6)));
        }
        // el corazón grande con la insignia dentro (latiendo)
        double big = e < 1.8 ? 0 : Math.min(1.1, spring((e - 1.8) * 0.9)) * (1 - smooth(4.2, 4.6, e));
        if (big > 0.01) {
            double beat = 1 + 0.08 * Math.pow(Math.max(0, Math.sin(e * 6.5)), 8);
            heart(pose, b, 0, 2.85, 0, (float) (1.55 * big * beat), 0, ClanEmoteFx.scale(0xFF3F6A, (float) (0.85 * Math.min(1, big))));
            ClanEmoteFx.badgeFacing(pose, b, look, 0, 2.88, 0, (float) (0.6 * big), 0);
            ClanEmoteFx.tag(pose, b, look, 0, 3.72, 0, (float) (0.023 * Math.min(1, big)), now);
        }
        ClanEmotes.burst(pose, b, seed + 3, cols, e, 1.8, 30, 0, 2.85, 0, 2.2, 1.2, 1.0, 0.08F);
    }

    private static void heart(PoseStack pose, MultiBufferSource b, double x, double y, double z, float size, float ang, int rgb) {
        VertexConsumer vc = ClanEmoteFx.light(b, ClanEmoteFx.HEART);
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(ang));
        pose.scale(-1, 1, 1);
        ClanEmoteFx.quad(pose, vc, 0, 0, 0, size, size, rgb);
        pose.popPose();
    }

    // ----- 13. Concierto --------------------------------------------------------------------------------------------

    private static void concert(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        double show = smooth(0.1, 0.4, e) * (1 - smooth(4.6, 5.0, e));
        double beat = Math.pow(Math.max(0, Math.cos((e - 0.3) * Math.PI * 2 * 2)), 6);
        double lift = lift(13, e);
        // los altavoces detrás (con la insignia delante)
        if (show > 0.01) {
            f.push(pose);
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * 1.45, 0, -0.9);
                float sc = (float) show;
                pose.scale(sc, sc, sc);
                VertexConsumer vc = ClanEmoteFx.solid(b);
                ClanEmoteFx.box(pose, vc, -0.45F, 0F, -0.35F, 0.45F, 1.5F, 0.35F, 0x1A1A20);
                ClanEmoteFx.box(pose, vc, -0.47F, 1.5F, -0.37F, 0.47F, 1.56F, 0.37F, 0x3A3A44);
                // los conos (se hinchan con el ritmo)
                float pump = (float) (1 + 0.12 * beat);
                ClanEmoteFx.box(pose, vc, -0.3F * pump, 0.25F, 0.35F, 0.3F * pump, 0.25F + 0.6F * pump, 0.39F, 0x2A2A30);
                ClanEmoteFx.box(pose, vc, -0.12F, 0.47F, 0.39F, 0.12F, 0.71F, 0.42F, 0x55555F);
                pose.pushPose();
                pose.translate(0, 1.15, 0.37);
                pose.mulPose(Axis.YP.rotationDegrees(180));
                ClanEmoteFx.badge(pose, b, look, 0.5F);
                pose.popPose();
                // las ondas de sonido (aros en el plano del altavoz)
                VertexConsumer g = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
                for (int w = 0; w < 2; w++) {
                    double k = ((e * 2 + w * 0.5) % 1.0);
                    pose.pushPose();
                    pose.translate(0, 0.55, 0.42);
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    ClanEmoteFx.ring(pose, g, 0, 0.25 + 0.9 * k, 0.05, ClanEmoteFx.scale(pal[w % pal.length], (float) ((1 - k) * 0.6)), 32);
                    pose.popPose();
                }
                pose.popPose();
            }
            // la guitarra (en diagonal sobre el cuerpo)
            pose.pushPose();
            pose.translate(-0.05, 0.92 + lift, 0.24);
            pose.mulPose(Axis.ZP.rotationDegrees(-55));
            float sc = (float) Math.min(1, show * 1.5);
            pose.scale(sc, sc, sc);
            VertexConsumer vc = ClanEmoteFx.solid(b);
            ClanEmoteFx.box(pose, vc, -0.2F, -0.24F, -0.05F, 0.2F, 0.14F, 0.05F, main);
            ClanEmoteFx.box(pose, vc, -0.14F, 0.14F, -0.05F, 0.14F, 0.26F, 0.05F, ClanEmoteFx.mix(main, 0x000000, 0.25F));
            ClanEmoteFx.box(pose, vc, -0.035F, 0.26F, -0.03F, 0.035F, 0.95F, 0.03F, 0x6B4423);
            ClanEmoteFx.box(pose, vc, -0.06F, 0.95F, -0.035F, 0.06F, 1.08F, 0.035F, 0x2A1A10);
            ClanEmoteFx.box(pose, vc, -0.05F, -0.06F, 0.05F, 0.05F, 0.04F, 0.06F, 0x1A1A1A);
            pose.popPose();
            pose.popPose();
        }
        // los focos que barren desde arriba
        if (show > 0.01) {
            VertexConsumer rays = ClanEmoteFx.light(b, ClanEmoteFx.RAY);
            for (int i = 0; i < 3; i++) {
                double a = e * (0.9 + i * 0.3) + i * 2.1;
                double tx = f.x(Math.sin(a) * 0.8, 0), tz = f.z(Math.sin(a) * 0.8, 0);
                double sx = f.x(-3 + i * 3, -3.5), sz = f.z(-3 + i * 3, -3.5);
                ClanEmoteFx.segment(pose, rays, sx, 7, sz, tx, 0.2, tz, 0.7F, ClanEmoteFx.scale(pal[(i + 1) % pal.length], (float) (0.35 * show * (0.6 + 0.4 * beat))));
            }
        }
        // las notas musicales que salen de la guitarra
        if (e > 0.4 && e < 4.9) {
            Font font = Minecraft.getInstance().font;
            for (int i = 0; i < 18; i++) {
                double born = 0.4 + i * 0.2, age = e - born;
                if (age < 0 || age > 1.6) continue;
                double lx = -0.1 + (rnd(seed, i, 1) - 0.5) * 1.2 + 0.25 * Math.sin(age * 3 + i), lz = 0.4 + age * 0.4, y = 1.1 + age * 1.5;
                String note = i % 2 == 0 ? "♪" : "♫";
                int col = pal[i % pal.length];
                float k = 0.045F * (float) Math.min(1, age * 5);
                pose.pushPose();
                pose.translate(f.x(lx, lz), y, f.z(lx, lz));
                pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
                pose.mulPose(Axis.ZP.rotationDegrees((float) (15 * Math.sin(age * 4 + i))));
                pose.scale(-k, -k, k);
                Component c = Component.literal(note).withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(col)));
                font.drawInBatch8xOutline(c.getVisualOrderText(), -font.width(c) / 2F, -4, 0xFFFFFFFF, 0xFF000000 | ClanEmoteFx.scale(col, 0.25F),
                        pose.last().pose(), b, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        }
        // el acorde final
        ClanEmotes.burst(pose, b, seed + 4, pal, e, 3.85, 46, 0, 2.2, 0, 3.4, 2.0, 1.0, 0.11F);
        crest(pose, b, look, e, 3.85, 5.0, 0, 3.0, 0, 0.85F, main, now);
    }

    // ----- 14. Portal -----------------------------------------------------------------------------------------------

    private static void portal(double e, PoseStack pose, MultiBufferSource b, ClanLooks.Look look, ClanEmotes.Frame f, long seed, int[] pal, double now, int main) {
        double open = e < 0.4 ? 0 : easeOutBack((e - 0.4) / 0.8) * (1 - easeIn(clamp01((e - 3.9) / 0.5)));
        int purple = ClanEmoteFx.mix(0x9A4FFF, main, 0.35F);
        if (open > 0.01) {
            f.push(pose);
            pose.translate(0, 1.75, -1.15);
            float r = (float) (1.35 * open);
            // el fondo del portal (oscuro) y el remolino
            VertexConsumer dark = ClanEmoteFx.solid(b);
            org.joml.Matrix4f m = pose.last().pose();
            org.joml.Matrix3f nm = pose.last().normal();
            int seg = 36;
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                float[][] tri = {{0, 0}, {(float) Math.cos(a0) * r * 0.97F, (float) Math.sin(a0) * r * 0.97F},
                        {(float) Math.cos(a1) * r * 0.97F, (float) Math.sin(a1) * r * 0.97F}, {0, 0}};
                for (int pass = 0; pass < 2; pass++) {
                    for (int k = 0; k < 4; k++) {
                        float[] q = tri[pass == 0 ? k : 3 - k];
                        int c = 0x12062A;
                        dark.vertex(m, q[0], q[1], -0.02F).color(c >> 16 & 255, c >> 8 & 255, c & 255, 255).uv(0.5F, 0.5F)
                                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, 0, 0, 1).endVertex();
                    }
                }
            }
            VertexConsumer glow = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int arm = 0; arm < 3; arm++) {
                for (int k = 0; k < 16; k++) {
                    double t = k / 16.0;
                    double a = arm * Math.PI * 2 / 3 + t * 4.5 - e * 3.2;
                    float rr = (float) (r * (0.08 + 0.85 * t));
                    pose.pushPose();
                    pose.translate(Math.cos(a) * rr, Math.sin(a) * rr, 0);
                    ClanEmoteFx.quad(pose, glow, 0, 0, 0, (float) (0.12 + 0.18 * t) * (float) open, (float) (0.12 + 0.18 * t) * (float) open,
                            ClanEmoteFx.scale(arm == 0 ? purple : pal[arm % pal.length], (float) (0.8 * (1 - t * 0.4))));
                    pose.popPose();
                }
            }
            // el borde: un aro de luz que gira con runas
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48 + e * 1.4;
                pose.pushPose();
                pose.translate(Math.cos(a) * r, Math.sin(a) * r, 0.01);
                ClanEmoteFx.quad(pose, glow, 0, 0, 0, 0.2F, 0.2F, ClanEmoteFx.scale(i % 4 == 0 ? 0xFFFFFF : purple, 0.9F));
                pose.popPose();
            }
            VertexConsumer runes = ClanEmoteFx.light(b, ClanEmoteFx.STAR);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 - e * 0.8;
                pose.pushPose();
                pose.translate(Math.cos(a) * (r + 0.2), Math.sin(a) * (r + 0.2), 0.02);
                pose.mulPose(Axis.ZP.rotationDegrees((float) (e * 90)));
                ClanEmoteFx.quad(pose, runes, 0, 0, 0, 0.3F, 0.3F, ClanEmoteFx.scale(pal[i % pal.length], 0.9F));
                pose.popPose();
            }
            pose.popPose();
            // chispas que el portal se traga
            VertexConsumer sp = ClanEmoteFx.light(b, ClanEmoteFx.GLOW);
            for (int i = 0; i < 24; i++) {
                double ph = (e * 0.8 + rnd(seed, i, 1)) % 1.0;
                double a = rnd(seed, i, 2) * Math.PI * 2, rr = 2.6 * (1 - ph);
                double lx = Math.cos(a) * rr, ly = 1.75 + Math.sin(a) * rr, lz = -1.15 + (1 - ph) * 0.6;
                ClanEmoteFx.billboard(pose, sp, f.x(lx, lz), ly, f.z(lx, lz), 0.07F, ClanEmoteFx.scale(pal[i % pal.length], (float) (ph * open)));
            }
        }
        // la insignia sale del portal (2,0–2,7) por encima de la cabeza y se queda delante
        if (e > 1.9 && e < 2.75) {
            double k = easeOut((e - 1.9) / 0.85);
            double lz = lerp(-1.15, 0.3, k), y = lerp(1.75, 2.8, k);
            ClanEmoteFx.badgeFacing(pose, b, look, f.x(0, lz), y, f.z(0, lz), (float) (0.35 + 0.55 * k), (float) (360 * (1 - k)));
        }
        crest(pose, b, look, e, 2.7, 5.0, f.x(0, 0.3), 2.8, f.z(0, 0.3), 0.9F, purple, now);
        // al cerrarse: destello hacia dentro
        if (e > 4.25 && e < 4.6) {
            double k = (e - 4.25) / 0.35;
            ClanEmoteFx.billboard(pose, ClanEmoteFx.light(b, ClanEmoteFx.GLOW), f.x(0, -1.15), 1.75, f.z(0, -1.15), (float) (3 * (1 - k)), ClanEmoteFx.scale(purple, (float) (1 - k)));
        }
    }
}
