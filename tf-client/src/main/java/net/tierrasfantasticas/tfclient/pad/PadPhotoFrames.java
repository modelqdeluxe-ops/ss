/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.155, el dueño: «agrega en el modo foto más marcos para publicar la foto, una extensa de marcos») Los marcos de
 * las fotos: al publicar se elige uno (PadCameraPage) y se pega encima de la foto antes de subirla
 * (PadCommunityClient.encode), así sale igual para todos en Comunidad. Los dibuja tools/pad/marcos.py (1600x900 con el
 * centro transparente) con su miniatura para el selector; la lista está en assets/tfclient/pad/marcos.json.
 */
final class PadPhotoFrames {
    record Frame(String id, String name, String group) {
        ResourceLocation full() {
            return new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/marcos/" + id + ".png");
        }

        /** 640x360 con transparencia: la vista previa encima de la foto. */
        ResourceLocation preview() {
            return new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/marcos/medio/" + id + ".png");
        }

        ResourceLocation mini() {
            return new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/marcos/mini/" + id + ".png");
        }
    }

    private static List<Frame> frames;

    private PadPhotoFrames() {}

    /** Todos, en el orden del selector (el primero, «Sin marco», no tiene id). */
    static List<Frame> all() {
        if (frames != null) return frames;
        List<Frame> out = new ArrayList<>();
        out.add(new Frame("", "Sin marco", ""));
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(TFClient.MOD_ID, "pad/marcos.json"));
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                JsonArray a = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement e : a) {
                    JsonObject o = e.getAsJsonObject();
                    out.add(new Frame(o.get("id").getAsString(), o.get("nombre").getAsString(), o.get("grupo").getAsString()));
                }
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Pad: no se pudo leer la lista de marcos", e);
            }
        }
        frames = out;
        return out;
    }

    static Frame byId(String id) {
        for (Frame f : all()) if (f.id().equals(id)) return f;
        return all().get(0);
    }

    /** Los bytes del PNG del marco (en el hilo del juego: el gestor de recursos no es para otros hilos); null sin marco. */
    static byte[] bytes(String id) {
        if (id == null || id.isEmpty()) return null;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(byId(id).full());
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            return in.readAllBytes();
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo leer el marco {}", id, e);
            return null;
        }
    }

    /** Pega el marco (con su transparencia) encima de la foto, al tamaño de la foto. */
    static void apply(NativeImage photo, NativeImage frame) {
        int w = photo.getWidth(), h = photo.getHeight();
        try (NativeImage scaled = new NativeImage(w, h, true)) {
            frame.resizeSubRectTo(0, 0, frame.getWidth(), frame.getHeight(), scaled);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = scaled.getPixelRGBA(x, y);
                    if ((c >>> 24) != 0) photo.blendPixel(x, y, c);
                }
            }
        }
    }
}
