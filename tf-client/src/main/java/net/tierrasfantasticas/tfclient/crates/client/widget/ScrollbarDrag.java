package net.tierrasfantasticas.tfclient.crates.client.widget;

public final class ScrollbarDrag {
   public static final int MIN_THUMB = 12;
   private boolean dragging;
   private double anchorMouseY;
   private int anchorScroll;

   public static boolean isDragButton(int button) {
      return button == 0 || button == 1;
   }

   public static int thumbHeight(int trackHeight, int visibleRows, int totalRows) {
      return totalRows <= 0 ? trackHeight : Math.max(12, Math.min(trackHeight, trackHeight * visibleRows / Math.max(1, totalRows)));
   }

   public static int thumbTop(int trackTop, int trackHeight, int thumbHeight, int scroll, int maxScroll) {
      return maxScroll <= 0 ? trackTop : trackTop + (trackHeight - thumbHeight) * Math.max(0, Math.min(maxScroll, scroll)) / maxScroll;
   }

   public static boolean overThumb(double mouseX, double mouseY, int barX, int barWidth, int thumbTop, int thumbHeight) {
      return mouseX >= (double)barX && mouseX <= (double)(barX + barWidth) && mouseY >= (double)thumbTop && mouseY <= (double)(thumbTop + thumbHeight);
   }

   public static boolean overTrack(double mouseX, double mouseY, int barX, int barWidth, int trackTop, int trackHeight) {
      return mouseX >= (double)(barX - 2)
         && mouseX <= (double)(barX + barWidth + 2)
         && mouseY >= (double)trackTop
         && mouseY <= (double)(trackTop + trackHeight);
   }

   public int beginOnTrack(double mouseY, int currentScroll, int trackTop, int trackHeight, int thumbHeight, int maxScroll) {
      int scroll = currentScroll;
      int thumb = thumbTop(trackTop, trackHeight, thumbHeight, currentScroll, maxScroll);
      boolean onThumb = mouseY >= (double)thumb && mouseY <= (double)(thumb + thumbHeight);
      if (!onThumb) {
         int usable = trackHeight - thumbHeight;
         if (usable > 0 && maxScroll > 0) {
            double target = mouseY - (double)trackTop - (double)thumbHeight / 2.0;
            scroll = (int)Math.round(target * (double)maxScroll / (double)usable);
            scroll = Math.max(0, Math.min(maxScroll, scroll));
         }
      }

      this.dragging = true;
      this.anchorMouseY = mouseY;
      this.anchorScroll = scroll;
      return scroll;
   }

   public int drag(double mouseY, int trackHeight, int thumbHeight, int maxScroll) {
      int usable = trackHeight - thumbHeight;
      if (usable > 0 && maxScroll > 0) {
         int delta = (int)Math.round((mouseY - this.anchorMouseY) * (double)maxScroll / (double)usable);
         return Math.max(0, Math.min(maxScroll, this.anchorScroll + delta));
      } else {
         return this.anchorScroll;
      }
   }

   public void end() {
      this.dragging = false;
   }

   public boolean isDragging() {
      return this.dragging;
   }
}
