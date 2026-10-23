/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.color;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class FSText {
   private static final float RAINBOW_SPEED = 0.35F;
   private static final float RAINBOW_SPREAD = 0.6F;

   private FSText() {
   }

   public static String legacy(String raw) {
      if (raw == null) {
         return "";
      } else {
         char[] chars = raw.toCharArray();

         for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(chars[i + 1]) >= 0) {
               chars[i] = 167;
            }
         }

         return new String(chars);
      }
   }

   public static boolean hasEffects(String raw) {
      if (raw == null) {
         return false;
      } else {
         String lower = raw.toLowerCase(Locale.ROOT);
         return lower.contains("<rainbow") || lower.contains("<gradient:") || lower.contains("&#");
      }
   }

   public static int rainbowStyleOf(String raw) {
      if (raw == null) {
         return -1;
      } else {
         String lower = raw.toLowerCase(Locale.ROOT);
         int at = lower.indexOf("<rainbow");
         if (at < 0) {
            return -1;
         } else {
            int close = lower.indexOf(62, at);
            if (close < 0) {
               return -1;
            } else {
               String tag = lower.substring(at + 1, close);
               if (tag.startsWith("rainbow:")) {
                  try {
                     return Integer.parseInt(tag.substring("rainbow:".length()).trim());
                  } catch (NumberFormatException var6) {
                     return 0;
                  }
               } else {
                  return 0;
               }
            }
         }
      }
   }

   public static String toLegacy(String raw) {
      if (raw == null) {
         return "";
      } else {
         String out = raw.replaceAll("(?i)<rainbow(:\\d+)?>", "&d").replaceAll("(?i)</rainbow>", "").replaceAll("(?i)</gradient>", "");
         Matcher gradient = Pattern.compile("(?i)<gradient:#?([0-9a-f]{6}),\\s*#?([0-9a-f]{6})>").matcher(out);
         StringBuilder sb = new StringBuilder();

         while (gradient.find()) {
            gradient.appendReplacement(sb, "&" + nearestLegacyChar(Integer.parseInt(gradient.group(1), 16)));
         }

         gradient.appendTail(sb);
         out = sb.toString();
         Matcher hex = Pattern.compile("[&§]#([0-9a-fA-F]{6})").matcher(out);
         StringBuilder sb2 = new StringBuilder();

         while (hex.find()) {
            hex.appendReplacement(sb2, "&" + nearestLegacyChar(Integer.parseInt(hex.group(1), 16)));
         }

         hex.appendTail(sb2);
         return sb2.toString();
      }
   }

   public static int legacyColorOf(char code) {
      int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
      return index < 0 ? 16777215 : legacyColor(index);
   }

   public static char nearestLegacyChar(int rgb) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      char best = 'f';
      int bestDistance = Integer.MAX_VALUE;
      String codes = "0123456789abcdef";

      for (int i = 0; i < 16; i++) {
         int c = legacyColor(i);
         int dr = (c >> 16 & 0xFF) - r;
         int dg = (c >> 8 & 0xFF) - g;
         int db = (c & 0xFF) - b;
         int distance = dr * dr + dg * dg + db * db;
         if (distance < bestDistance) {
            bestDistance = distance;
            best = codes.charAt(i);
         }
      }

      return best;
   }

   public static String plain(String raw) {
      return raw == null
         ? ""
         : raw.replaceAll("(?i)<\\/?rainbow(:\\d+)?>", "")
            .replaceAll("(?i)<\\/?gradient(:[^>]*)?>", "")
            .replaceAll("[&§]#[0-9a-fA-F]{6}", "")
            .replaceAll("[&§][0-9A-Fa-fK-Ok-orR]", "");
   }

   public static String withRainbow(String raw, int style) {
      String plain = plain(raw);
      return "<rainbow:" + Math.max(0, style) + ">" + plain;
   }

   public static String withoutRainbow(String raw) {
      return raw == null ? "" : raw.replaceAll("(?i)<\\/?rainbow(:\\d+)?>", "");
   }

   public static MutableComponent parse(String raw, long timeMs) {
      if (raw != null && !raw.isEmpty()) {
         MutableComponent out = Component.empty();
         Integer fixedColor = null;
         boolean bold = false;
         boolean italic = false;
         boolean underline = false;
         boolean strike = false;
         boolean obfuscated = false;
         int effect = 0;
         int gradientFrom = 16777215;
         int gradientTo = 16777215;
         int rainbowStyle = 0;
         int effectIndex = 0;
         int effectLength = 1;
         int i = 0;
         int length = raw.length();

         while (i < length) {
            char c = raw.charAt(i);
            if (c == '<') {
               int close = raw.indexOf(62, i);
               if (close > i) {
                  String tag = raw.substring(i + 1, close).toLowerCase(Locale.ROOT);
                  if (tag.equals("rainbow") || tag.startsWith("rainbow:")) {
                     effect = 1;
                     rainbowStyle = 0;
                     if (tag.startsWith("rainbow:")) {
                        try {
                           rainbowStyle = Integer.parseInt(tag.substring("rainbow:".length()).trim());
                        } catch (NumberFormatException var22) {
                           rainbowStyle = 0;
                        }
                     }

                     effectIndex = 0;
                     effectLength = Math.max(1, visibleLength(raw, close + 1));
                     i = close + 1;
                     continue;
                  }

                  if (tag.startsWith("gradient:")) {
                     String[] parts = tag.substring("gradient:".length()).split(",");
                     if (parts.length >= 2) {
                        gradientFrom = FSColors.parse(parts[0].trim(), 16777215);
                        gradientTo = FSColors.parse(parts[1].trim(), 16777215);
                        effect = 2;
                        effectIndex = 0;
                        effectLength = Math.max(1, visibleLength(raw, close + 1));
                        i = close + 1;
                        continue;
                     }
                  }

                  if (tag.equals("/rainbow") || tag.equals("/gradient")) {
                     effect = 0;
                     i = close + 1;
                     continue;
                  }
               }
            }

            if ((c == '&' || c == 167) && i + 7 < length && raw.charAt(i + 1) == '#') {
               String hex = raw.substring(i + 2, i + 8);
               if (hex.matches("[0-9a-fA-F]{6}")) {
                  fixedColor = Integer.parseInt(hex, 16);
                  effect = 0;
                  i += 8;
                  continue;
               }
            }

            if ((c == '&' || c == 167) && i + 1 < length) {
               char code = Character.toLowerCase(raw.charAt(i + 1));
               int paletteIndex = "0123456789abcdef".indexOf(code);
               if (paletteIndex >= 0) {
                  fixedColor = legacyColor(paletteIndex);
                  effect = 0;
                  i += 2;
                  continue;
               }

               switch (code) {
                  case 'k':
                     obfuscated = true;
                     i += 2;
                     continue;
                  case 'l':
                     bold = true;
                     i += 2;
                     continue;
                  case 'm':
                     strike = true;
                     i += 2;
                     continue;
                  case 'n':
                     underline = true;
                     i += 2;
                     continue;
                  case 'o':
                     italic = true;
                     i += 2;
                     continue;
                  case 'p':
                  case 'q':
                  default:
                     break;
                  case 'r':
                     fixedColor = null;
                     obfuscated = false;
                     strike = false;
                     underline = false;
                     italic = false;
                     bold = false;
                     effect = 0;
                     i += 2;
                     continue;
               }
            }

            int color;
            if (effect == 1) {
               float pos = (float)effectIndex / (float)effectLength * 0.6F;
               float time = (float)(timeMs % 3000L) / 3000.0F;
               color = FSRainbow.color(rainbowStyle, pos, time);
               effectIndex++;
            } else if (effect == 2) {
               float t = effectLength <= 1 ? 0.0F : (float)effectIndex / (float)(effectLength - 1);
               color = lerpColor(gradientFrom, gradientTo, t);
               effectIndex++;
            } else {
               color = fixedColor == null ? 16777215 : fixedColor;
            }

            Style style = Style.EMPTY
               .withColor(TextColor.fromRgb(color))
               .withBold(bold)
               .withItalic(italic)
               .withUnderlined(underline)
               .withStrikethrough(strike)
               .withObfuscated(obfuscated);
            out.append(Component.literal(String.valueOf(c)).withStyle(style));
            i++;
         }

         return out;
      } else {
         return Component.empty();
      }
   }

   private static int visibleLength(String raw, int from) {
      int count = 0;
      int i = from;

      while (i < raw.length()) {
         char c = raw.charAt(i);
         if (c == '<') {
            int close = raw.indexOf(62, i);
            if (close > i) {
               i = close + 1;
               continue;
            }
         }

         if ((c == '&' || c == 167) && i + 7 < raw.length() && raw.charAt(i + 1) == '#' && raw.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
            i += 8;
         } else if ((c == '&' || c == 167) && i + 1 < raw.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(raw.charAt(i + 1)) >= 0) {
            i += 2;
         } else {
            count++;
            i++;
         }
      }

      return count;
   }

   private static int legacyColor(int index) {
      return switch (index) {
         case 0 -> 0;
         case 1 -> 170;
         case 2 -> 43520;
         case 3 -> 43690;
         case 4 -> 11141120;
         case 5 -> 11141290;
         case 6 -> 16755200;
         case 7 -> 11184810;
         case 8 -> 5592405;
         case 9 -> 5592575;
         case 10 -> 5635925;
         case 11 -> 5636095;
         case 12 -> 16733525;
         case 13 -> 16733695;
         case 14 -> 16777045;
         default -> 16777215;
      };
   }

   private static int lerpColor(int from, int to, float t) {
      int r = (int)((float)(from >> 16 & 0xFF) + (float)((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
      int g = (int)((float)(from >> 8 & 0xFF) + (float)((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
      int b = (int)((float)(from & 0xFF) + (float)((to & 0xFF) - (from & 0xFF)) * t);
      return r << 16 | g << 8 | b;
   }

   private static int hsvToRgb(float h, float s, float v) {
      int i = (int)(h * 6.0F);
      float f = h * 6.0F - (float)i;
      float p = v * (1.0F - s);
      float q = v * (1.0F - f * s);
      float t = v * (1.0F - (1.0F - f) * s);
      float r;
      float g;
      float b;
      switch (i % 6) {
         case 0:
            r = v;
            g = t;
            b = p;
            break;
         case 1:
            r = q;
            g = v;
            b = p;
            break;
         case 2:
            r = p;
            g = v;
            b = t;
            break;
         case 3:
            r = p;
            g = q;
            b = v;
            break;
         case 4:
            r = t;
            g = p;
            b = v;
            break;
         default:
            r = v;
            g = p;
            b = q;
      }

      return (int)(r * 255.0F) << 16 | (int)(g * 255.0F) << 8 | (int)(b * 255.0F);
   }
}
