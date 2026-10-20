package net.tierrasfantasticas.tfclient.crates.client.widget;

public final class ScrollMemory {
   int scroll;
   Object selected;

   public void clear() {
      this.scroll = 0;
      this.selected = null;
   }
}
