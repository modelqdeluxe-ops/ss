package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;

public interface Filter {
   PixelStore.LoanerPicture filter(Picture var1, PixelStore var2);

   ColorSpace getInputColor();

   ColorSpace getOutputColor();
}
