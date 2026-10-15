package hn.hato.ganadero;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Size;
import android.view.Surface;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/* Puente "Ar" de Rumentis Beta: ARCore corriendo por debajo de la cámara en vivo de la página (camvivo.js). No hay
   pantalla aparte: la sesión de ARCore tiene la cámara y le pasa a la página los cuadros (JPEG chico, derecho como en
   la pantalla) con lo que ARCore sabe de cada uno; la página dibuja la silueta en tiempo real como siempre y, cuando
   la silueta está verde, pide las fotos a resolución completa. Lo registra Enlace si la clase está (la beta se arma con
   Gradle y com.google.ar:core: scripts/pagos.sh con DONANTE=ar).
   JS -> estado(): "listo" | "instalar" | "revisando" | "no"
         iniciar(json {lado}): arranca (la primera vez puede pedir instalar "Servicios de Google Play para RA");
                               responde window.arIniciado({ok, profundidad} | {instalar} | {error})
         otro(): pide el cuadro siguiente -> window.arCuadro(jpegBase64, meta) (el JPEG sin girar, de iw×ih;
                 la página lo gira meta.rot grados)
         foto(): pide una foto completa -> window.arFoto(json): la foto en files/fotos/<id>.jpg, la profundidad (mm)
                 con el encuadre de la foto, los intrínsecos, la altura del teléfono sobre el suelo y su inclinación
         detener(): suelta la cámara.
   meta de cada cuadro: W, H, fx, fy, cx, cy (del cuadro ya girado), rot, iw, ih, elev y ladeo (grados), alto (m sobre
   el suelo, si ARCore ya lo encontró), sigue (ARCore está ubicado) y pd (ARCore ya tiene profundidad). La foto lleva
   además prof y praw {w, h, mm}: la profundidad (suavizada, y la cruda solo con los puntos de confianza) con el encuadre
   de la foto.
   La sesión corre en un GLSurfaceView de 2×2 px detrás de la WebView (ARCore necesita un contexto GL). */
public class ArPuente implements GLSurfaceView.Renderer {
    private static WeakReference<WebView> web = new WeakReference<WebView>(null);
    private final Activity act;
    private GLSurfaceView gl;
    private Session session;
    private volatile boolean activo = false, pedirCuadro = false;
    private volatile int pedirFotos = 0;
    private boolean pausado = false, instalarPedido = false, texListo = false, geometria = true, profundidad = false;
    private int tex = -1, lado = 640, vw = 2, vh = 2, nFoto = 0;
    private String camId = null;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

    public ArPuente(Activity a, WebView w) {
        act = a;
        web = new WeakReference<WebView>(w);
        try {
            a.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityPaused(Activity x) { if (x == act) pausar(); }
                @Override public void onActivityResumed(Activity x) { if (x == act) reanudar(); }
                @Override public void onActivityCreated(Activity x, Bundle b) { }
                @Override public void onActivityStarted(Activity x) { }
                @Override public void onActivityStopped(Activity x) { }
                @Override public void onActivitySaveInstanceState(Activity x, Bundle b) { }
                @Override public void onActivityDestroyed(Activity x) { }
            });
        } catch (Throwable e) {
        }
    }

    @JavascriptInterface
    public String estado() {
        try {
            ArCoreApk.Availability av = ArCoreApk.getInstance().checkAvailability(act);
            if (av.isTransient()) return "revisando";
            if (av == ArCoreApk.Availability.SUPPORTED_INSTALLED) return "listo";
            if (av == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD || av == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED) return "instalar";
            return "no";
        } catch (Throwable e) {
            return "no";
        }
    }

    @JavascriptInterface
    public void iniciar(String opc) {
        try { lado = Math.max(240, Math.min(960, new JSONObject(opc == null ? "{}" : opc).optInt("lado", 640))); } catch (Throwable e) { lado = 640; }
        act.runOnUiThread(new Runnable() { @Override public void run() { arrancar(); } });
    }

    @JavascriptInterface
    public void otro() { pedirCuadro = true; }

    @JavascriptInterface
    public void foto() { pedirFotos++; }

    @JavascriptInterface
    public void detener() {
        act.runOnUiThread(new Runnable() { @Override public void run() { parar(); } });
    }

    private void arrancar() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && act.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                act.requestPermissions(new String[]{Manifest.permission.CAMERA}, 71);
                evento("arIniciado", "{\"error\":\"permiso\"}");
                return;
            }
            if (session == null) {
                if (ArCoreApk.getInstance().requestInstall(act, !instalarPedido) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    instalarPedido = true;
                    evento("arIniciado", "{\"instalar\":true}");
                    return;
                }
                session = new Session(act);
                configurar();
            }
            if (gl == null) {
                gl = new GLSurfaceView(act);
                gl.setPreserveEGLContextOnPause(true);
                gl.setEGLContextClientVersion(2);
                gl.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
                gl.setRenderer(this);
                gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
                ViewGroup raiz = (ViewGroup) act.findViewById(android.R.id.content);
                raiz.addView(gl, 0, new FrameLayout.LayoutParams(2, 2));
                texListo = false;
            }
            session.resume();
            gl.onResume();
            activo = true; pausado = false; geometria = true; pedirFotos = 0;
            evento("arIniciado", "{\"ok\":true,\"profundidad\":" + profundidad + "}");
        } catch (UnavailableUserDeclinedInstallationException e) {
            evento("arIniciado", "{\"error\":\"no-instalado\"}");
        } catch (Throwable e) {
            evento("arIniciado", "{\"error\":" + JSONObject.quote("no-compatible: " + e.getClass().getSimpleName()) + "}");
        }
    }

    private void parar() {
        activo = false; pedirCuadro = false; pedirFotos = 0;
        try { if (session != null) session.pause(); } catch (Throwable e) { }
        try {
            if (gl != null) {
                gl.onPause();
                ViewGroup p = (ViewGroup) gl.getParent();
                if (p != null) p.removeView(gl);
            }
        } catch (Throwable e) { }
        gl = null; texListo = false;
    }

    private void pausar() {
        if (!activo || pausado) return;
        pausado = true;
        try { if (gl != null) gl.onPause(); } catch (Throwable e) { }
        try { if (session != null) session.pause(); } catch (Throwable e) { }
    }

    private void reanudar() {
        if (!activo || !pausado) return;
        try { session.resume(); if (gl != null) gl.onResume(); pausado = false; geometria = true; } catch (Throwable e) { }
    }

    private void configurar() {
        // la cámara trasera con la imagen (CPU) más grande hasta 1920 de ancho: las fotos salen de ahí
        try {
            CameraConfigFilter f = new CameraConfigFilter(session);
            f.setFacingDirection(CameraConfig.FacingDirection.BACK);
            List<CameraConfig> L = session.getSupportedCameraConfigs(f);
            CameraConfig mejor = null;
            for (CameraConfig c : L) {
                Size s = c.getImageSize();
                if (s.getWidth() > 1920) continue;
                if (mejor == null || s.getWidth() * s.getHeight() > mejor.getImageSize().getWidth() * mejor.getImageSize().getHeight()) mejor = c;
            }
            if (mejor != null) session.setCameraConfig(mejor);
        } catch (Throwable e) {
        }
        try { camId = session.getCameraConfig().getCameraId(); } catch (Throwable e) { camId = null; }
        sensorRot = -1;
        Config c = new Config(session);
        profundidad = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
        if (profundidad) c.setDepthMode(Config.DepthMode.AUTOMATIC);
        c.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
        c.setFocusMode(Config.FocusMode.AUTO);
        c.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        session.configure(c);
    }

    /* ---------- GL: solo para que ARCore tenga su textura; no se dibuja nada ---------- */
    @Override
    public void onSurfaceCreated(GL10 g, EGLConfig cfg) {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        tex = t[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        texListo = false;
    }

    @Override
    public void onSurfaceChanged(GL10 g, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        vw = Math.max(1, w); vh = Math.max(1, h); geometria = true;
    }

    @Override
    public void onDrawFrame(GL10 g) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (!activo || session == null) return;
        try {
            if (!texListo) { session.setCameraTextureName(tex); texListo = true; }
            if (geometria) { session.setDisplayGeometry(rotacionPantalla(), vw, vh); geometria = false; }
            Frame f = session.update();
            if (f.getTimestamp() == 0) return;
            Camera cam = f.getCamera();
            if (pedirFotos > 0) {
                if (foto(f, cam)) pedirFotos--;
            } else if (pedirCuadro) {
                if (cuadro(f, cam)) pedirCuadro = false;
            }
        } catch (Throwable e) {
            // un cuadro que falla no detiene la sesión
        }
    }

    private int rotacionPantalla() {
        try { return act.getWindowManager().getDefaultDisplay().getRotation(); } catch (Throwable e) { return Surface.ROTATION_0; }
    }

    /* cuántos grados (sentido del reloj) hay que girar la imagen de la cámara para verla como en la pantalla */
    private int sensorRot = -1;
    private int rotacionImagen() {
        // la orientación del sensor se lee una vez (pedirla en cada cuadro es lento)
        if (sensorRot < 0) {
            sensorRot = 90;
            try {
                CameraManager cm = (CameraManager) act.getSystemService(Context.CAMERA_SERVICE);
                if (camId != null) {
                    Integer s = cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SENSOR_ORIENTATION);
                    if (s != null) sensorRot = s;
                }
            } catch (Throwable e) {
            }
        }
        int sensor = sensorRot;
        int pant = 0;
        switch (rotacionPantalla()) {
            case Surface.ROTATION_90: pant = 90; break;
            case Surface.ROTATION_180: pant = 180; break;
            case Surface.ROTATION_270: pant = 270; break;
        }
        return ((sensor - pant) % 360 + 360) % 360;
    }

    /* la altura del teléfono (m) sobre el suelo: el plano horizontal más bajo que ARCore sigue, debajo del teléfono */
    private float alturaSuelo(Camera cam) {
        try {
            float y = cam.getPose().ty(), suelo = Float.NaN;
            for (Plane p : session.getAllTrackables(Plane.class)) {
                if (p.getTrackingState() != TrackingState.TRACKING || p.getSubsumedBy() != null || p.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING) continue;
                float py = p.getCenterPose().ty();
                if (y - py < .3f || y - py > 3f) continue;
                if (Float.isNaN(suelo) || py < suelo) suelo = py;
            }
            return Float.isNaN(suelo) ? -1f : y - suelo;
        } catch (Throwable e) {
            return -1f;
        }
    }

    /* lo común de un cuadro y de una foto: inclinación, suelo, intrínsecos (para una imagen de W×H escalada por k y
       girada rot grados) y la profundidad con ese encuadre (GW de ancho) */
    private void datos(JSONObject o, Frame f, Camera cam, int W, int H, float k, int rot, int GW) throws Exception {
        boolean sigue = cam.getTrackingState() == TrackingState.TRACKING;
        o.put("sigue", sigue);
        Pose ps = cam.getDisplayOrientedPose();
        float[] z = ps.getZAxis(), x = ps.getXAxis();
        o.put("elev", Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -z[1])))));
        o.put("ladeo", Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, x[1])))));
        if (sigue) { float a = alturaSuelo(cam); if (a > 0) o.put("alto", a); }
        CameraIntrinsics ci = cam.getImageIntrinsics();
        float[] fl = ci.getFocalLength(), pp = ci.getPrincipalPoint();
        int[] dim = ci.getImageDimensions();
        float sx = dim[0] > 0 ? (float) W / dim[0] : 1f, sy = dim[1] > 0 ? (float) H / dim[1] : 1f;
        int w2 = Math.round(W * k), h2 = Math.round(H * k);
        float[] r = girarIntr(rot, w2, h2, fl[0] * sx * k, fl[1] * sy * k, pp[0] * sx * k, pp[1] * sy * k);
        o.put("fx", r[0]); o.put("fy", r[1]); o.put("cx", r[2]); o.put("cy", r[3]);
        o.put("W", rot % 180 == 0 ? w2 : h2); o.put("H", rot % 180 == 0 ? h2 : w2);
        o.put("rot", rot);
        if (!profundidad || GW <= 0) return;
        Image dep = null, raw = null, conf = null;
        try {
            dep = f.acquireDepthImage16Bits();
            int dw = dep.getWidth(), dh = dep.getHeight();
            short[] d = leer16(dep);
            dep.close(); dep = null;
            // la cruda (sin rellenar ni suavizar) con su confianza: solo los puntos que ARCore midió de verdad
            short[] dr = null;
            try {
                raw = f.acquireRawDepthImage16Bits(); conf = f.acquireRawDepthConfidenceImage();
                if (raw.getWidth() == dw && raw.getHeight() == dh && conf.getWidth() == dw && conf.getHeight() == dh) {
                    dr = leer16(raw);
                    Image.Plane pc = conf.getPlanes()[0];
                    ByteBuffer cb = pc.getBuffer();
                    int crs = pc.getRowStride(), cps = pc.getPixelStride();
                    for (int yy = 0; yy < dh; yy++) for (int xx = 0; xx < dw; xx++)
                        if ((cb.get(yy * crs + xx * cps) & 0xff) < CONF_MIN) dr[yy * dw + xx] = 0;
                }
            } catch (Throwable e) {
                dr = null;
            } finally {
                if (raw != null) try { raw.close(); } catch (Throwable e) { }
                if (conf != null) try { conf.close(); } catch (Throwable e) { }
                raw = null; conf = null;
            }
            /* la profundidad va con la textura de la cámara: cada celda de la rejilla (en píxeles de la imagen CPU) se
               lleva a coordenadas normalizadas de la textura con ARCore y ahí se lee (como en la guía de Depth) */
            int GH = Math.max(1, Math.round((float) GW * H / W));
            FloatBuffer pin = ByteBuffer.allocateDirect(GW * GH * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
            FloatBuffer pout = ByteBuffer.allocateDirect(GW * GH * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
            for (int j = 0; j < GH; j++) for (int i = 0; i < GW; i++) { pin.put((i + .5f) * W / GW); pin.put((j + .5f) * H / GH); }
            pin.position(0);
            f.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS, pin, Coordinates2d.TEXTURE_NORMALIZED, pout);
            int[] idx = new int[GW * GH];
            for (int q = 0; q < GW * GH; q++) {
                float tx = pout.get(2 * q), ty = pout.get(2 * q + 1);
                int xx = (int) (tx * dw), yy = (int) (ty * dh);
                idx[q] = tx >= 0 && ty >= 0 && xx >= 0 && yy >= 0 && xx < dw && yy < dh ? yy * dw + xx : -1;
            }
            o.put("prof", rejilla(d, idx, GW, GH, rot, dw + "x" + dh));
            if (dr != null) o.put("praw", rejilla(dr, idx, GW, GH, rot, dw + "x" + dh));
        } catch (NotYetAvailableException e) {
            // ARCore todavía no tiene la profundidad (necesita que el teléfono se mueva un poco)
        } finally {
            if (dep != null) try { dep.close(); } catch (Throwable e) { }
        }
    }

    private static final int CONF_MIN = 128;

    private static short[] leer16(Image im) {
        int w = im.getWidth(), h = im.getHeight();
        Image.Plane pl = im.getPlanes()[0];
        ByteBuffer bb = pl.getBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int rs = pl.getRowStride(), px = pl.getPixelStride();
        short[] d = new short[w * h];
        for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++) d[yy * w + xx] = bb.getShort(yy * rs + xx * px);
        return d;
    }

    private static JSONObject rejilla(short[] d, int[] idx, int GW, int GH, int rot, String orig) throws Exception {
        short[] al = new short[GW * GH];
        for (int q = 0; q < GW * GH; q++) al[q] = idx[q] >= 0 ? d[idx[q]] : 0;
        int[] nd = new int[2];
        short[] gg = girar(al, GW, GH, rot, nd);
        ByteBuffer out = ByteBuffer.allocate(gg.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short v : gg) out.putShort(v);
        JSONObject jd = new JSONObject();
        jd.put("w", nd[0]); jd.put("h", nd[1]); jd.put("mm", Base64.encodeToString(out.array(), Base64.NO_WRAP));
        jd.put("orig", orig);
        return jd;
    }

    /* si ARCore ya tiene profundidad (para la página: listo para medir) */
    private boolean hayProf(Frame f) {
        if (!profundidad) return false;
        Image im = null;
        try { im = f.acquireDepthImage16Bits(); return true; } catch (Throwable e) { return false; }
        finally { if (im != null) try { im.close(); } catch (Throwable e) { } }
    }

    /* un cuadro chico para la silueta en vivo. En el hilo de GL solo se copian los planos de la imagen (rápido) y lo
       que sabe ARCore; el JPEG se arma en otro hilo, sin girar (lo gira la página al dibujarlo: meta.rot). Mientras se
       arma uno no se pide otro. */
    private volatile boolean armando = false;
    private int nCuadro = 0;
    private boolean ultProf = false;
    private boolean cuadro(Frame f, Camera cam) {
        if (armando) return false;
        Image img = null;
        try {
            img = f.acquireCameraImage();
            final int W = img.getWidth(), H = img.getHeight();
            final float k = Math.min(1f, (float) lado / Math.max(W, H));
            final int w2 = Math.round(W * k) & ~1, h2 = Math.round(H * k) & ~1;
            final Planos pl = new Planos(img);
            img.close(); img = null;
            final int rot = rotacionImagen();
            final JSONObject o = new JSONObject();
            datos(o, f, cam, W, H, (float) w2 / W, rot, 0);
            if ((nCuadro++ % 10) == 0) ultProf = hayProf(f);
            o.put("pd", ultProf);
            o.put("iw", w2); o.put("ih", h2);
            armando = true;
            hilo.execute(new Runnable() { @Override public void run() {
                try {
                    byte[] nv = pl.nv21(w2, h2);
                    YuvImage y = new YuvImage(nv, ImageFormat.NV21, w2, h2, null);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream(64 * 1024);
                    y.compressToJpeg(new Rect(0, 0, w2, h2), 70, bo);
                    js("window.arCuadro&&window.arCuadro('" + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP) + "'," + o.toString() + ")");
                } catch (Throwable e) {
                    pedirCuadro = true;
                } finally {
                    armando = false;
                }
            } });
            return true;
        } catch (NotYetAvailableException e) {
            return false;
        } catch (Throwable e) {
            return false;
        } finally {
            if (img != null) try { img.close(); } catch (Throwable e) { }
        }
    }

    /* los planos YUV_420_888 copiados de una vez (la imagen se suelta enseguida) y pasados a NV21 de w2×h2 */
    private static final class Planos {
        final int W, H, yr, yp, ur, up, vr, vp;
        final byte[] y, u, v;
        Planos(Image img) {
            W = img.getWidth(); H = img.getHeight();
            Image.Plane[] p = img.getPlanes();
            yr = p[0].getRowStride(); yp = p[0].getPixelStride();
            ur = p[1].getRowStride(); up = p[1].getPixelStride();
            vr = p[2].getRowStride(); vp = p[2].getPixelStride();
            y = copiar(p[0].getBuffer()); u = copiar(p[1].getBuffer()); v = copiar(p[2].getBuffer());
        }
        private static byte[] copiar(ByteBuffer b) { b.rewind(); byte[] a = new byte[b.remaining()]; b.get(a); return a; }
        byte[] nv21(int w2, int h2) {
            byte[] out = new byte[w2 * h2 * 3 / 2];
            int pos = 0;
            int[] sx = new int[w2];
            for (int c = 0; c < w2; c++) sx[c] = (int) ((long) c * W / w2) * yp;
            for (int r = 0; r < h2; r++) { int base = (int) ((long) r * H / h2) * yr; for (int c = 0; c < w2; c++) out[pos++] = y[base + sx[c]]; }
            for (int r = 0; r < h2 / 2; r++) {
                int cy = (int) ((long) r * 2 * H / h2) / 2;
                for (int c = 0; c < w2 / 2; c++) {
                    int cx = (int) ((long) c * 2 * W / w2) / 2;
                    int iv = cy * vr + cx * vp, iu = cy * ur + cx * up;
                    out[pos++] = iv < v.length ? v[iv] : (byte) 128;
                    out[pos++] = iu < u.length ? u[iu] : (byte) 128;
                }
            }
            return out;
        }
    }

    /* una foto completa para medir: se guarda en files/fotos y se avisa con su id */
    private boolean foto(Frame f, Camera cam) {
        Image img = null;
        try {
            img = f.acquireCameraImage();
            final int W = img.getWidth(), H = img.getHeight();
            final byte[] nv = nv21(img, W, H);
            img.close(); img = null;
            final int rot = rotacionImagen();
            final JSONObject o = new JSONObject();
            o.put("ts", System.currentTimeMillis());
            datos(o, f, cam, W, H, 1f, rot, 160);
            final String id = "ar-" + System.currentTimeMillis() + "-" + (nFoto++);
            o.put("foto", id);
            hilo.execute(new Runnable() { @Override public void run() {
                try {
                    byte[] jpg = jpeg(nv, W, H, rot, 92);
                    File dir = new File(act.getFilesDir(), "fotos");
                    if (!dir.exists()) dir.mkdirs();
                    FileOutputStream fo = new FileOutputStream(new File(dir, id + ".jpg"));
                    fo.write(jpg);
                    fo.close();
                } catch (Throwable e) {
                    try { o.put("error", e.getClass().getSimpleName()); } catch (Throwable e2) { }
                }
                js("window.arFoto&&window.arFoto(" + JSONObject.quote(o.toString()) + ")");
            } });
            return true;
        } catch (NotYetAvailableException e) {
            return false;
        } catch (Throwable e) {
            return false;
        } finally {
            if (img != null) try { img.close(); } catch (Throwable e) { }
        }
    }

    private static byte[] jpeg(byte[] nv, int w, int h, int rot, int q) {
        YuvImage y = new YuvImage(nv, ImageFormat.NV21, w, h, null);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        y.compressToJpeg(new Rect(0, 0, w, h), q, bo);
        byte[] jpg = bo.toByteArray();
        if (rot == 0) return jpg;
        Bitmap bm = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
        Matrix m = new Matrix();
        m.postRotate(rot);
        Bitmap r = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
        r.compress(Bitmap.CompressFormat.JPEG, q, b2);
        bm.recycle(); r.recycle();
        return b2.toByteArray();
    }

    private static float[] girarIntr(int rot, int W, int H, float fx, float fy, float cx, float cy) {
        if (rot == 90) return new float[]{fy, fx, H - cy, cx};
        if (rot == 180) return new float[]{fx, fy, W - cx, H - cy};
        if (rot == 270) return new float[]{fy, fx, cy, W - cx};
        return new float[]{fx, fy, cx, cy};
    }

    private static short[] girar(short[] d, int w, int h, int rot, int[] nd) {
        if (rot == 0) { nd[0] = w; nd[1] = h; return d; }
        short[] o = new short[w * h];
        if (rot == 180) {
            nd[0] = w; nd[1] = h;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o[(h - 1 - y) * w + (w - 1 - x)] = d[y * w + x];
            return o;
        }
        nd[0] = h; nd[1] = w;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int nx, ny;
            if (rot == 90) { nx = h - 1 - y; ny = x; } else { nx = y; ny = w - 1 - x; }
            o[ny * h + nx] = d[y * w + x];
        }
        return o;
    }

    /* YUV_420_888 a NV21 de w2×h2 (el más cercano: para el cuadro chico; con w2×h2 = W×H, la imagen completa) */
    private static byte[] nv21(Image img, int w2, int h2) {
        int W = img.getWidth(), H = img.getHeight();
        byte[] out = new byte[w2 * h2 * 3 / 2];
        Image.Plane[] p = img.getPlanes();
        ByteBuffer y = p[0].getBuffer(), u = p[1].getBuffer(), v = p[2].getBuffer();
        int yr = p[0].getRowStride(), yp = p[0].getPixelStride();
        int ur = p[1].getRowStride(), up = p[1].getPixelStride(), vr = p[2].getRowStride(), vp = p[2].getPixelStride();
        int pos = 0;
        if (w2 == W && h2 == H && yp == 1 && yr == W) { y.position(0); y.get(out, 0, W * H); pos = W * H; }
        else for (int r = 0; r < h2; r++) { int sy = (int) ((long) r * H / h2); for (int c = 0; c < w2; c++) out[pos++] = y.get(sy * yr + (int) ((long) c * W / w2) * yp); }
        for (int r = 0; r < h2 / 2; r++) {
            int sy = (int) ((long) r * 2 * H / h2) / 2;
            for (int c = 0; c < w2 / 2; c++) {
                int sx = (int) ((long) c * 2 * W / w2) / 2;
                out[pos++] = v.get(sy * vr + sx * vp);
                out[pos++] = u.get(sy * ur + sx * up);
            }
        }
        return out;
    }

    private void evento(String fn, String json) { js("window." + fn + "&&window." + fn + "(" + JSONObject.quote(json) + ")"); }

    private static void js(final String codigo) {
        final WebView w = web.get();
        if (w == null) return;
        w.post(new Runnable() { @Override public void run() {
            try { w.evaluateJavascript(codigo, null); } catch (Throwable e) { }
        } });
    }
}
