package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;

public interface PixelStore {
   PixelStore.LoanerPicture getPicture(int var1, int var2, ColorSpace var3);

   void putBack(PixelStore.LoanerPicture var1);

   void retake(PixelStore.LoanerPicture var1);

   public static class LoanerPicture {
      private Picture picture;
      private int refCnt;

      public LoanerPicture(Picture picture, int refCnt) {
         this.picture = picture;
         this.refCnt = refCnt;
      }

      public Picture getPicture() {
         return this.picture;
      }

      public int getRefCnt() {
         return this.refCnt;
      }

      public void decRefCnt() {
         this.refCnt--;
      }

      public boolean unused() {
         return this.refCnt <= 0;
      }

      public void incRefCnt() {
         this.refCnt++;
      }
   }
}
