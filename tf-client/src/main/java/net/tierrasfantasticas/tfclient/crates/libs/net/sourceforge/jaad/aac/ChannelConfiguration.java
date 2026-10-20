package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac;

public final class ChannelConfiguration {
   public static final ChannelConfiguration CHANNEL_CONFIG_UNSUPPORTED = new ChannelConfiguration(-1, "invalid");
   public static final ChannelConfiguration CHANNEL_CONFIG_NONE = new ChannelConfiguration(0, "No channel");
   public static final ChannelConfiguration CHANNEL_CONFIG_MONO = new ChannelConfiguration(1, "Mono");
   public static final ChannelConfiguration CHANNEL_CONFIG_STEREO = new ChannelConfiguration(2, "Stereo");
   public static final ChannelConfiguration CHANNEL_CONFIG_STEREO_PLUS_CENTER = new ChannelConfiguration(3, "Stereo+Center");
   public static final ChannelConfiguration CHANNEL_CONFIG_STEREO_PLUS_CENTER_PLUS_REAR_MONO = new ChannelConfiguration(4, "Stereo+Center+Rear");
   public static final ChannelConfiguration CHANNEL_CONFIG_FIVE = new ChannelConfiguration(5, "Five channels");
   public static final ChannelConfiguration CHANNEL_CONFIG_FIVE_PLUS_ONE = new ChannelConfiguration(6, "Five channels+LF");
   public static final ChannelConfiguration CHANNEL_CONFIG_SEVEN_PLUS_ONE = new ChannelConfiguration(8, "Seven channels+LF");
   private final int chCount;
   private final String descr;

   public static ChannelConfiguration forInt(int i) {
      ChannelConfiguration c;
      switch (i) {
         case 0:
            c = CHANNEL_CONFIG_NONE;
            break;
         case 1:
            c = CHANNEL_CONFIG_MONO;
            break;
         case 2:
            c = CHANNEL_CONFIG_STEREO;
            break;
         case 3:
            c = CHANNEL_CONFIG_STEREO_PLUS_CENTER;
            break;
         case 4:
            c = CHANNEL_CONFIG_STEREO_PLUS_CENTER_PLUS_REAR_MONO;
            break;
         case 5:
            c = CHANNEL_CONFIG_FIVE;
            break;
         case 6:
            c = CHANNEL_CONFIG_FIVE_PLUS_ONE;
            break;
         case 7:
         case 8:
            c = CHANNEL_CONFIG_SEVEN_PLUS_ONE;
            break;
         default:
            c = CHANNEL_CONFIG_UNSUPPORTED;
      }

      return c;
   }

   private ChannelConfiguration(int chCount, String descr) {
      this.chCount = chCount;
      this.descr = descr;
   }

   public int getChannelCount() {
      return this.chCount;
   }

   public String getDescription() {
      return this.descr;
   }

   @Override
   public String toString() {
      return this.descr;
   }
}
