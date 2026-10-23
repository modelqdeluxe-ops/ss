/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * (1.3.109, el dueño: «no quiero un paquete de recursos, quiero que lo metas al mod cifrado y que suene en la escena de
 * la crate») la música de la escena va dentro del mod, cifrada: «TFENC1» + vector inicial (16 bytes) + el .ogg en
 * AES-128/CTR (tools/crates/cifrar_musica.py, con la misma SEED). SoundBufferLibraryMixin la descifra al vuelo al
 * sonar; un .ogg normal (el de un paquete de recursos, por ejemplo) sigue igual.
 */
public final class SealedAudio {
    private static final String SEED = "TierrasFantasticas|santuario-de-las-hadas|1.3.109";
    private static final byte[] MAGIC = "TFENC1".getBytes(StandardCharsets.US_ASCII);

    private SealedAudio() {}

    /** ¿Puede ser uno de los nuestros? (solo se miran los sonidos de las crates) */
    public static boolean candidate(ResourceLocation loc) {
        // (1.3.115) también la música del santuario del Void (la del dueño, «La petite fille de la mer», cifrada en el mod)
        return "tfclient".equals(loc.getNamespace()) && (loc.getPath().startsWith("sounds/crates/") || loc.getPath().startsWith("sounds/santuario/"));
    }

    /** Los bytes del .ogg (descifrados si venían cifrados), o null si el recurso no está cifrado. */
    public static byte[] open(ResourceProvider resources, ResourceLocation loc) throws IOException {
        byte[] all;
        try (InputStream in = resources.open(loc)) {
            all = in.readAllBytes();
        }
        if (all.length < MAGIC.length + 16 || !Arrays.equals(Arrays.copyOf(all, MAGIC.length), MAGIC)) return null;
        try {
            byte[] key = Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(SEED.getBytes(StandardCharsets.UTF_8)), 16);
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(all, MAGIC.length, 16));
            return c.doFinal(all, MAGIC.length + 16, all.length - MAGIC.length - 16);
        } catch (Exception e) {
            throw new IOException("no se pudo descifrar " + loc, e);
        }
    }
}
