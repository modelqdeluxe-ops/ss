/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.media;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryUtil;

public final class VideoTexture extends AbstractTexture {
   private int width;
   private int height;
   private boolean pixelPerfect;
   private boolean filterApplied;

   public void setSharpFiltering(boolean pixelPerfect) {
      if (this.pixelPerfect != pixelPerfect || !this.filterApplied) {
         this.pixelPerfect = pixelPerfect;
         this.filterApplied = true;
         this.setFilter(!pixelPerfect, false);
      }
   }

   public void upload(ByteBuffer rgba, int frameWidth, int frameHeight) {
      RenderSystem.assertOnRenderThread();
      if (this.width != frameWidth || this.height != frameHeight) {
         TextureUtil.prepareImage(this.getId(), frameWidth, frameHeight);
         this.width = frameWidth;
         this.height = frameHeight;
         this.filterApplied = false;
      }

      this.bind();
      GlStateManager._pixelStore(3314, 0);
      GlStateManager._pixelStore(3316, 0);
      GlStateManager._pixelStore(3315, 0);
      GlStateManager._pixelStore(3317, 4);
      GlStateManager._texSubImage2D(3553, 0, 0, 0, frameWidth, frameHeight, 6408, 5121, MemoryUtil.memAddress(rgba));
   }

   public int frameWidth() {
      return this.width;
   }

   public int frameHeight() {
      return this.height;
   }

   public boolean hasFrame() {
      return this.width > 0 && this.height > 0;
   }

   public void load(ResourceManager resourceManager) {
   }
}
