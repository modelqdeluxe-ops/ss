package net.tierrasfantasticas.tfclient.crates.client.media;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaUrls {
   private static final Pattern DRIVE_FILE = Pattern.compile("drive\\.google\\.com/file/d/([a-zA-Z0-9_-]{10,})");
   private static final Pattern DRIVE_ID_PARAM = Pattern.compile("drive(?:usercontent)?\\.google\\.com/[^?]*\\?(?:[^&]*&)*id=([a-zA-Z0-9_-]{10,})");
   private static final Pattern GITHUB_BLOB = Pattern.compile("github\\.com/([^/]+)/([^/]+)/blob/(.+)");

   private MediaUrls() {
   }

   public static String normalize(String url) {
      if (url == null) {
         return null;
      } else {
         String trimmed = url.trim();
         if (trimmed.isEmpty()) {
            return trimmed;
         } else {
            String lower = trimmed.toLowerCase(Locale.ROOT);
            Matcher file = DRIVE_FILE.matcher(trimmed);
            if (file.find()) {
               return driveDownload(file.group(1));
            } else {
               if (lower.contains("drive.google.com") || lower.contains("driveusercontent.google.com")) {
                  Matcher param = DRIVE_ID_PARAM.matcher(trimmed);
                  if (param.find()) {
                     return driveDownload(param.group(1));
                  }
               }

               if (lower.contains("dropbox.com")) {
                  if (lower.contains("dl=0")) {
                     return trimmed.replace("dl=0", "dl=1").replace("DL=0", "dl=1");
                  } else {
                     return !lower.contains("dl=1") && !lower.contains("raw=1") ? trimmed + (trimmed.contains("?") ? "&" : "?") + "dl=1" : trimmed;
                  }
               } else {
                  Matcher blob = GITHUB_BLOB.matcher(trimmed);
                  return blob.find() ? "https://raw.githubusercontent.com/" + blob.group(1) + "/" + blob.group(2) + "/" + blob.group(3) : trimmed;
               }
            }
         }
      }
   }

   private static String driveDownload(String id) {
      return "https://drive.usercontent.google.com/download?id=" + id + "&export=download";
   }

   public static boolean wasRewritten(String original, String normalized) {
      return original != null && normalized != null && !original.trim().equals(normalized);
   }
}
