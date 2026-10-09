package net.tierrasfantasticas.tfclient.pad.music;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Baja una canción de un link directo y la mete en la biblioteca. Acepta links de compartir de Google Drive (los
 * convierte solo; el archivo tiene que estar como «Cualquier persona con el enlace»), Dropbox, GitHub y cualquier
 * link directo a un MP3, OGG o WAV. También baja portadas (PNG o JPG) para una canción. Una descarga a la vez, en su
 * propio hilo; la app lee {@link #job()} para pintar el progreso.
 */
public final class MusicDownloader {
    public static final long MAX_AUDIO = 80L << 20, MAX_IMAGE = 8L << 20;

    /** Lo que se está bajando. Lo escribe el hilo de la descarga y lo lee la app. */
    public static final class Job {
        public final String url;
        public final boolean cover;
        public volatile long done, total = -1;
        public volatile String status = "Conectando…";
        public volatile String error;
        public volatile MusicLibrary.Track result;
        public volatile boolean finished;
        public final long started = System.currentTimeMillis();

        Job(String url, boolean cover) {
            this.url = url;
            this.cover = cover;
        }

        public float progress() {
            return total > 0 ? Math.min(1F, done / (float) total) : -1F;
        }
    }

    private static volatile Job job;

    private MusicDownloader() {}

    public static Job job() {
        return job;
    }

    public static boolean busy() {
        Job j = job;
        return j != null && !j.finished;
    }

    /** La app ya enseñó el resultado: se olvida. */
    public static void clear() {
        if (!busy()) job = null;
    }

    /** Empieza a bajar una canción. false si ya hay una descarga en marcha o el link no vale. */
    public static boolean download(String link) {
        String url = direct(link);
        if (busy() || url == null) return false;
        Job j = new Job(url, false);
        job = j;
        start(j, () -> downloadSong(j));
        return true;
    }

    /** Pone la portada de una canción desde un link a una imagen. */
    public static boolean downloadCover(MusicLibrary.Track track, String link) {
        String url = direct(link);
        if (busy() || url == null || track == null) return false;
        Job j = new Job(url, true);
        job = j;
        start(j, () -> downloadCoverFor(j, track));
        return true;
    }

    private interface Work {
        void run() throws Exception;
    }

    private static void start(Job j, Work work) {
        Thread t = new Thread(() -> {
            try {
                work.run();
            } catch (Exception e) {
                if (j.error == null) j.error = e.getMessage() == null ? "No se pudo bajar." : e.getMessage();
                TFClient.LOGGER.info("Música: descarga fallida de {}: {}", j.url, e.toString());
            } finally {
                j.finished = true;
            }
        }, "TF Música (descarga)");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Links
    // ---------------------------------------------------------------------------------------------------------------

    private static final Pattern DRIVE_FILE = Pattern.compile("drive\\.google\\.com/file/d/([A-Za-z0-9_-]{10,})");
    private static final Pattern DRIVE_ID = Pattern.compile("[?&]id=([A-Za-z0-9_-]{10,})");
    private static final Pattern GITHUB_BLOB = Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$");

    /** Pasa un link de compartir a uno de descarga directa. null si no parece un link. */
    public static String direct(String link) {
        if (link == null) return null;
        String url = link.trim().replace(" ", "%20");
        if (url.isEmpty()) return null;
        if (!url.matches("(?i)^https?://.*")) url = "https://" + url;
        String lower = url.toLowerCase(Locale.ROOT);
        Matcher m = DRIVE_FILE.matcher(url);
        if (m.find()) return driveUrl(m.group(1));
        if (lower.contains("drive.google.com") || lower.contains("docs.google.com") || lower.contains("drive.usercontent.google.com")) {
            m = DRIVE_ID.matcher(url);
            if (m.find()) return driveUrl(m.group(1));
        }
        if (lower.contains("dropbox.com")) {
            if (url.matches(".*[?&]dl=0.*")) return url.replaceFirst("([?&])dl=0", "$1dl=1");
            if (!url.matches(".*[?&](dl|raw)=1.*")) return url + (url.contains("?") ? "&" : "?") + "dl=1";
            return url;
        }
        m = GITHUB_BLOB.matcher(url);
        if (m.find()) return "https://raw.githubusercontent.com/" + m.group(1) + "/" + m.group(2) + "/" + m.group(3);
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null || !uri.getHost().contains(".")) return null;
        } catch (Exception e) {
            return null;
        }
        return url;
    }

    private static String driveUrl(String id) {
        return "https://drive.usercontent.google.com/download?id=" + id + "&export=download&confirm=t";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Descargas
    // ---------------------------------------------------------------------------------------------------------------

    private static void downloadSong(Job j) throws Exception {
        Path dir = MusicLibrary.dir();
        Files.createDirectories(dir);
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36);
        Path part = dir.resolve(".bajando-" + id + ".part");
        try {
            String name = fetch(j, j.url, part, MAX_AUDIO, true);
            j.status = "Comprobando…";
            AudioDecoder.Kind kind = AudioDecoder.AudioFormatSniffer.sniff(part);
            if (kind == AudioDecoder.Kind.UNKNOWN)
                throw new IOException("Eso no es una canción: tiene que ser un MP3, OGG o WAV.");
            long duration = 0;
            if (kind == AudioDecoder.Kind.MP3) {
                Mp3Frames.Scan scan = Mp3Frames.scan(part, -1);
                if (!scan.mpeg1()) throw new IOException("Este MP3 es de baja calidad (MPEG-2): conviértelo a 128 kbps o más.");
                duration = scan.durationMs();
            } else if (kind == AudioDecoder.Kind.WAV) {
                duration = wavDuration(part);
            }
            // que se pueda reproducir de verdad: unos cuantos trozos
            try (AudioDecoder d = AudioDecoder.open(part, 0)) {
                for (int i = 0; i < 8; i++) if (d.next() == null) break;
                if (kind == AudioDecoder.Kind.OGG) duration = OggDecoder.durationMs(part);
            }
            j.status = "Leyendo portada…";
            MusicTags.Tags tags = MusicTags.read(part);
            String ext = kind == AudioDecoder.Kind.MP3 ? ".mp3" : kind == AudioDecoder.Kind.OGG ? ".ogg" : ".wav";
            boolean cover = tags.cover() != null && saveCover(tags.cover(), dir.resolve(id + ".png"));
            String title = !tags.title().isBlank() ? tags.title() : name;
            Files.move(part, dir.resolve(id + ext), StandardCopyOption.REPLACE_EXISTING);
            MusicLibrary.Track t = new MusicLibrary.Track(id, title, tags.artist(), id + ext, cover, duration, j.url, System.currentTimeMillis());
            MusicLibrary.add(t);
            j.result = t;
            j.status = "Guardada";
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private static void downloadCoverFor(Job j, MusicLibrary.Track track) throws Exception {
        Path dir = MusicLibrary.dir();
        Files.createDirectories(dir);
        Path part = dir.resolve(".portada-" + track.id() + ".part");
        try {
            fetch(j, j.url, part, MAX_IMAGE, false);
            j.status = "Comprobando…";
            if (!saveCover(Files.readAllBytes(part), dir.resolve(track.id() + ".png")))
                throw new IOException("Eso no es una imagen PNG o JPG.");
            MusicLibrary.Track t = new MusicLibrary.Track(track.id(), track.title(), track.artist(), track.file(), true,
                    track.durationMs(), track.url(), track.added());
            MusicLibrary.releaseCover(track.id());
            MusicLibrary.update(t);
            j.result = t;
            j.status = "Portada puesta";
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /**
     * Baja url a file siguiendo redirecciones (también de http a https). Si Google Drive contesta con su página de
     * «no se puede analizar en busca de virus», sigue el botón de descargar. Devuelve el nombre del archivo (sin
     * extensión) para usarlo de título si la canción no trae uno.
     */
    static String fetch(Job j, String url, Path file, long max, boolean audio) throws IOException {
        String current = url;
        for (int hop = 0; hop < 12; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (TF Client; Minecraft) Java");
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null) throw new IOException("El servidor redirigió sin decir a dónde.");
                current = new URL(new URL(current), location).toString();
                continue;
            }
            if (code == 404) throw new IOException("Ese link no existe (404).");
            if (code == 401 || code == 403)
                throw new IOException("No hay permiso para bajarlo: en Drive, compártelo como «Cualquier persona con el enlace».");
            if (code != 200) throw new IOException("El servidor contestó " + code + ".");
            String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.ROOT);
            if (type.startsWith("text/html")) {
                String html = new String(c.getInputStream().readNBytes(512 * 1024), StandardCharsets.UTF_8);
                c.disconnect();
                String next = driveConfirm(html);
                if (next != null && hop < 11) {
                    current = next;
                    continue;
                }
                throw new IOException(audio ? "Ese link abre una página, no la canción: usa el link directo de descarga."
                        : "Ese link abre una página, no la imagen.");
            }
            long length = c.getContentLengthLong();
            if (length > max) throw new IOException("Demasiado grande (" + (length >> 20) + " MB; máximo " + (max >> 20) + " MB).");
            j.total = length;
            j.status = "Descargando…";
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    done += n;
                    if (done > max) throw new IOException("Demasiado grande (máximo " + (max >> 20) + " MB).");
                    out.write(buf, 0, n);
                    j.done = done;
                }
            }
            return fileName(c.getHeaderField("Content-Disposition"), current);
        }
        throw new IOException("Demasiadas redirecciones.");
    }

    /** El enlace del botón «Descargar de todos modos» de Google Drive, si la página es esa. */
    private static String driveConfirm(String html) {
        Matcher form = Pattern.compile("<form[^>]+action=\"([^\"]+)\"[^>]*>(.*?)</form>", Pattern.DOTALL).matcher(html);
        while (form.find()) {
            String action = form.group(1).replace("&amp;", "&");
            if (!action.contains("google")) continue;
            StringBuilder q = new StringBuilder();
            Matcher input = Pattern.compile("<input[^>]+name=\"([^\"]+)\"[^>]+value=\"([^\"]*)\"").matcher(form.group(2));
            while (input.find()) q.append(q.length() == 0 ? "" : "&").append(input.group(1)).append('=').append(input.group(2));
            return q.length() == 0 ? action : action + (action.contains("?") ? "&" : "?") + q;
        }
        Matcher href = Pattern.compile("href=\"(/uc\\?export=download[^\"]+)\"").matcher(html);
        if (href.find()) return "https://drive.google.com" + href.group(1).replace("&amp;", "&");
        return null;
    }

    private static String fileName(String disposition, String url) {
        String name = null;
        if (disposition != null) {
            Matcher m = Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE).matcher(disposition);
            if (m.find()) name = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
            else {
                m = Pattern.compile("filename=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE).matcher(disposition);
                if (m.find()) name = m.group(1);
            }
        }
        if (name == null) {
            String path = url.replaceAll("[?#].*$", "");
            name = URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
        }
        name = name.replaceAll("\\.(?i)(mp3|ogg|oga|wav)$", "").replace('_', ' ').trim();
        if (name.isEmpty() || name.equalsIgnoreCase("download") || name.equalsIgnoreCase("uc")) name = "Canción";
        return name.length() > 60 ? name.substring(0, 60) : name;
    }

    private static long wavDuration(Path file) {
        try (WavDecoder d = new WavDecoder(file, 0)) {
            long frames = 0;
            byte[] b;
            while ((b = d.next()) != null) frames += b.length / (2L * d.channels());
            return frames * 1000 / Math.max(1, d.sampleRate());
        } catch (IOException e) {
            return 0;
        }
    }

    /** Recorta al cuadrado del centro, lo deja en 256x256 y lo guarda como PNG. false si no es una imagen. */
    static boolean saveCover(byte[] bytes, Path png) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) return false;
            int side = Math.min(src.getWidth(), src.getHeight());
            int sx = (src.getWidth() - side) / 2, sy = (src.getHeight() - side) / 2;
            BufferedImage out = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, 256, 256, sx, sy, sx + side, sy + side, null);
            g.dispose();
            return ImageIO.write(out, "png", png.toFile());
        } catch (Exception e) {
            TFClient.LOGGER.info("Música: portada no válida ({})", e.toString());
            return false;
        }
    }
}
