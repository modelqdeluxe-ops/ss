package net.tierrasfantasticas.tfclient.mixin;

import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Los pasos de un filtro de pantalla, para cambiar sus valores (cuánta saturación en Colores vivos). */
@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> tfclient$passes();
}
