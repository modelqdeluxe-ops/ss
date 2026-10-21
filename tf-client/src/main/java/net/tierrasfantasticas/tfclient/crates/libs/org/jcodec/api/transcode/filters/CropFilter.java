package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode.filters;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode.Filter;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode.PixelStore;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;

public class CropFilter implements Filter {
   @Override
   public PixelStore.LoanerPicture filter(Picture picture, PixelStore store) {
      return null;
   }

   @Override
   public ColorSpace getInputColor() {
      return null;
   }

   @Override
   public ColorSpace getOutputColor() {
      return null;
   }
}
