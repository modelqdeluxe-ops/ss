package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac;

import java.io.IOException;

public class AACException extends IOException {
   private boolean eos;

   public static AACException endOfStream() {
      AACException ex = new AACException("end of stream");
      ex.eos = true;
      return ex;
   }

   public AACException(String message) {
      super(message);
   }

   public boolean isEndOfStream() {
      return this.eos;
   }

   public static AACException wrap(Exception e) {
      if (e != null && e instanceof AACException) {
         return (AACException)e;
      } else {
         return e != null && e.getMessage() != null ? new AACException(e.getMessage()) : new AACException("" + e);
      }
   }
}
