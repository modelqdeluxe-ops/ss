/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.trail;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los estilos de estela (1.3.67): los perfiles de Afterswing (MIT) con sus mismas capas. Cada capa es una cinta (de la
 * punta hacia la empuñadura: from y to son la parte del ancho que cubre, 0 = el filo de la punta), chispas o píxeles.
 * La cola se apaga de alphaFrom (lo nuevo) a alphaTo (lo viejo) con la curva fadePower y se estrecha con taperPower.
 */
public record TrailProfile(String name, String description, float lengthScale, float widthScale, List<Layer> layers) {
    public enum Type { RIBBON, SPARKS, PIXELS }

    /**
     * Una capa. brightness: cuánto brilla el color (1 tal cual); whiten: cuánto tira a blanco; texture: la textura de la
     * cinta (null = color liso), que se repite textureRepeat veces y corre textureScroll por segundo. Las chispas: cuántas
     * por muestra, su probabilidad, su tamaño (en bloques) y cuánto se apartan del camino.
     */
    public record Layer(Type type, float from, float to, float alphaFrom, float alphaTo, float fadePower, float taperPower, float brightness,
                        float whiten, ResourceLocation texture, float textureRepeat, float textureScroll, int perSample, float density,
                        float size, float drift, int cells, int steps) {
        static Layer ribbon(float from, float to, float alphaFrom, float alphaTo, float fadePower, float taperPower) {
            return new Layer(Type.RIBBON, from, to, alphaFrom, alphaTo, fadePower, taperPower, 1, 0, null, 1, 0, 0, 0, 0, 0, 0, 0);
        }

        Layer bright(float b, float w) {
            return new Layer(type, from, to, alphaFrom, alphaTo, fadePower, taperPower, b, w, texture, textureRepeat, textureScroll, perSample,
                    density, size, drift, cells, steps);
        }

        Layer textured(String tex, float repeat, float scroll) {
            return new Layer(type, from, to, alphaFrom, alphaTo, fadePower, taperPower, brightness, whiten,
                    new ResourceLocation(TFClient.MOD_ID, "textures/trail/" + tex + ".png"), repeat, scroll, perSample, density, size, drift,
                    cells, steps);
        }
    }

    public static final List<TrailProfile> ALL = List.of(
            new TrailProfile("Clásico", "Una cinta de luz suave.", 1, 1, List.of(Layer.ribbon(0, 1, 1, 0, 2, 1))),
            new TrailProfile("Tajo", "Media luna fina con el filo blanco y se apaga rápido.", 0.55F, 0.5F,
                    List.of(Layer.ribbon(0, 1, 0.85F, 0, 3, 2), Layer.ribbon(0, 0.14F, 1, 0.5F, 2.5F, 1.5F).bright(1, 0.85F))),
            new TrailProfile("Neón", "Línea brillante por fuera y un brillo oscuro dentro, como una hoja de energía.", 1, 1,
                    List.of(Layer.ribbon(0.05F, 1, 0.6F, 0.05F, 1.5F, 1).bright(0.45F, 0), Layer.ribbon(0, 0.06F, 1, 1, 1.5F, 0).bright(1.3F, 0.3F))),
            new TrailProfile("Brasas", "Una cinta tenue y chispas que se escapan del camino.", 1, 1,
                    List.of(Layer.ribbon(0, 1, 0.5F, 0, 2, 1),
                            new Layer(Type.SPARKS, 0, 0.8F, 1, 0.6F, 1.2F, 0, 1, 0.45F, null, 1, 0, 2, 0.55F, 0.035F, 0.6F, 0, 0))),
            new TrailProfile("Píxel", "Cuadritos que bajan de tamaño y se apagan.", 1, 1,
                    List.of(new Layer(Type.PIXELS, 0, 0.9F, 1, 0.55F, 1, 0, 1, 0, null, 1, 0, 0, 0, 0, 0, 5, 4))),
            new TrailProfile("Humo", "Humo suave que se deshace por la cinta.", 1, 1.15F,
                    List.of(Layer.ribbon(0, 1, 0.9F, 0.55F, 1.2F, 0.5F).textured("smoke", 1, 0.35F))),
            new TrailProfile("Destellos", "Rayas de luz que corren por la cinta.", 1, 1,
                    List.of(Layer.ribbon(0, 1, 1, 0.5F, 1.5F, 1).textured("streaks", 1.5F, 1.2F))));

    public static TrailProfile current() {
        return ALL.get(Math.max(0, Math.min(ALL.size() - 1, TrailSettings.profile)));
    }
}
