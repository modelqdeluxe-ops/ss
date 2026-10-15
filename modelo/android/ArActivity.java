package hn.hato.ganadero;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.GradientDrawable;
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/* Rumentis Beta: la medición con ARCore. Muestra la cámara con una mira; arriba el paso (costado, por detrás…) y en vivo
   la distancia a lo que está en la mira y la altura del teléfono sobre el suelo que reconoce ARCore. "Tomar foto" guarda
   por cada paso:
   - la foto de la cámara (la imagen de ARCore, la más grande que ofrece el teléfono) en files/fotos/<id>.jpg, derecha
     como se ve en la pantalla;
   - la profundidad de toda la imagen (Depth API, milímetros, si el teléfono la tiene), en la misma orientación;
   - los intrínsecos de la cámara (distancia focal y centro, en píxeles de esa foto);
   - la distancia a la mira (hit test), la altura del teléfono sobre el suelo y su inclinación (pose de ARCore).
   Con eso la página mide en centímetros sin nadie junto al animal. Al terminar (o cancelar) entrega el JSON con
   ArPuente.entregar. Las vistas se arman en código (sin recursos: el código smali usa los números de los recursos). */
public class ArActivity extends Activity implements GLSurfaceView.Renderer {
    private GLSurfaceView gl;
    private Session session;
    private boolean pedidoInstalar = false, texListo = false, geometria = true, entregado = false, terminando = false;
    private int vw = 1, vh = 1, tex = -1, prog = -1, aPos, aTex;
    private FloatBuffer quad, quadTex;
    private TextView tPaso, tInstr, tInfo;
    private Button bTomar, bTerminar;
    private JSONObject opc = new JSONObject();
    private JSONArray pasos = new JSONArray();
    private final JSONArray capturas = new JSONArray();
    private int ip = 0;
    private volatile boolean pedir = false;
    private boolean profundidad = false;
    private long ultInfo = 0;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private String camId = null;
    private static final float[] NDC = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};

    private String txt(String k, String def) { return opc.optJSONObject("textos") != null ? opc.optJSONObject("textos").optString(k, def) : def; }
    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { opc = new JSONObject(getIntent().getStringExtra("o")); } catch (Throwable e) { opc = new JSONObject(); }
        pasos = opc.optJSONArray("pasos");
        if (pasos == null || pasos.length() == 0) { pasos = new JSONArray(); pasos.put(new JSONObject()); }
        setRequestedOrientation("vertical".equals(opc.optString("orient")) ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);
        gl = new GLSurfaceView(this);
        gl.setPreserveEGLContextOnPause(true);
        gl.setEGLContextClientVersion(2);
        gl.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        gl.setRenderer(this);
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        raiz.addView(gl, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        raiz.addView(new Mira(this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout arriba = new LinearLayout(this);
        arriba.setOrientation(LinearLayout.VERTICAL);
        arriba.setPadding(dp(14), dp(10), dp(14), dp(10));
        arriba.setBackgroundColor(0x99000000);
        tPaso = texto(18, true); tInstr = texto(14, false); tInfo = texto(16, true);
        tInfo.setTextColor(0xFFFFE08A);
        arriba.addView(tPaso); arriba.addView(tInstr); arriba.addView(tInfo);
        raiz.addView(arriba, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout abajo = new LinearLayout(this);
        abajo.setOrientation(LinearLayout.HORIZONTAL);
        abajo.setGravity(Gravity.CENTER);
        abajo.setPadding(dp(12), dp(10), dp(12), dp(14));
        Button bCancelar = boton(txt("cancelar", "Cancelar"), 0xFF3A3F46);
        bTerminar = boton(txt("terminar", "Terminar"), 0xFF3A3F46);
        bTomar = boton(txt("tomar", "Tomar foto"), 0xFF2E9E5B);
        bTerminar.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(6), 0, dp(6), 0);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(52), 1.6f);
        lp2.setMargins(dp(6), 0, dp(6), 0);
        abajo.addView(bCancelar, lp); abajo.addView(bTerminar, lp); abajo.addView(bTomar, lp2);
        raiz.addView(abajo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        setContentView(raiz);

        bCancelar.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { cancelar(); } });
        bTerminar.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { terminar(); } });
        bTomar.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            if (pedir || terminando) return;
            pedir = true; bTomar.setEnabled(false); bTomar.setText(txt("tomando", "Tomando…"));
        } });
        pintarPaso();
        tInfo.setText(txt("buscando", "Mueve el teléfono despacio para que reconozca el lugar"));
    }

    private TextView texto(int sp, boolean negrita) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (negrita) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setShadowLayer(4, 0, 1, Color.BLACK);
        return t;
    }

    private Button boton(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        return b;
    }

    private void pintarPaso() {
        JSONObject p = pasos.optJSONObject(Math.min(ip, pasos.length() - 1));
        String n = p == null ? "" : p.optString("nombre", "");
        // (la página manda el título ya traducido, con "Paso 1 de 3 · …")
        tPaso.setText(p != null && p.has("titulo") ? p.optString("titulo") : n);
        tInstr.setText(p == null ? "" : p.optString("instr", ""));
        bTerminar.setVisibility(capturas.length() > 0 ? View.VISIBLE : View.GONE);
        bTomar.setEnabled(true);
        bTomar.setText(txt("tomar", "Tomar foto"));
    }

    /* ---------- ciclo de vida: ARCore se instala si hace falta, la sesión con profundidad y la cámara más grande ---------- */
    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 7);
            return;
        }
        if (session == null) {
            try {
                if (ArCoreApk.getInstance().requestInstall(this, !pedidoInstalar) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    pedidoInstalar = true;
                    return;
                }
                session = new Session(this);
                configurar();
            } catch (UnavailableUserDeclinedInstallationException e) {
                fallar("no-instalado");
                return;
            } catch (Throwable e) {
                fallar("no-compatible: " + e.getClass().getSimpleName());
                return;
            }
        }
        try {
            session.resume();
        } catch (Throwable e) {
            fallar("camara: " + e.getClass().getSimpleName());
            return;
        }
        gl.onResume();
        geometria = true;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (res.length == 0 || res[0] != PackageManager.PERMISSION_GRANTED) fallar("sin-permiso");
    }

    private void configurar() {
        // la cámara trasera con la imagen (CPU) más grande hasta 1920 de ancho
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
        Config c = new Config(session);
        profundidad = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
        if (profundidad) c.setDepthMode(Config.DepthMode.AUTOMATIC);
        c.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
        c.setFocusMode(Config.FocusMode.AUTO);
        c.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        session.configure(c);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            gl.onPause();
            session.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (!entregado) ArPuente.entregar("{\"cancelado\":true}");
        entregado = true;
        hilo.shutdown();
        if (session != null) { try { session.close(); } catch (Throwable e) { } session = null; }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() { cancelar(); }

    private void cancelar() {
        if (!entregado) { entregado = true; ArPuente.entregar("{\"cancelado\":true}"); }
        finish();
    }

    private void fallar(final String que) {
        runOnUiThread(new Runnable() { @Override public void run() {
            if (!entregado) {
                entregado = true;
                try { ArPuente.entregar(new JSONObject().put("error", que).toString()); } catch (Throwable e) { ArPuente.entregar("{\"error\":\"?\"}"); }
            }
            finish();
        } });
    }

    private void terminar() {
        if (terminando) return;
        terminando = true;
        bTomar.setEnabled(false);
        bTerminar.setEnabled(false);
        // después de las fotos que todavía se están guardando
        hilo.execute(new Runnable() { @Override public void run() {
            final String json;
            try {
                json = new JSONObject().put("capturas", capturas).put("profundidad", profundidad).toString();
            } catch (Throwable e) {
                fallar("json");
                return;
            }
            runOnUiThread(new Runnable() { @Override public void run() {
                if (!entregado) { entregado = true; ArPuente.entregar(json); }
                finish();
            } });
        } });
    }

    /* ---------- GL: el fondo con la imagen de la cámara ---------- */
    @Override
    public void onSurfaceCreated(GL10 g, EGLConfig cfg) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        tex = t[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        String vs = "attribute vec4 a_Pos;attribute vec2 a_Tex;varying vec2 v_Tex;void main(){gl_Position=a_Pos;v_Tex=a_Tex;}";
        String fs = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;varying vec2 v_Tex;uniform samplerExternalOES s_Tex;void main(){gl_FragColor=texture2D(s_Tex,v_Tex);}";
        prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, vs));
        GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, fs));
        GLES20.glLinkProgram(prog);
        aPos = GLES20.glGetAttribLocation(prog, "a_Pos");
        aTex = GLES20.glGetAttribLocation(prog, "a_Tex");
        quad = ByteBuffer.allocateDirect(NDC.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(NDC).position(0);
        quadTex = ByteBuffer.allocateDirect(NDC.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texListo = false;
    }

    private static int shader(int tipo, String src) {
        int s = GLES20.glCreateShader(tipo);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    @Override
    public void onSurfaceChanged(GL10 g, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        vw = w; vh = h; geometria = true;
    }

    @Override
    public void onDrawFrame(GL10 g) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (session == null) return;
        try {
            if (!texListo) { session.setCameraTextureName(tex); texListo = true; }
            if (geometria) { session.setDisplayGeometry(rotacionPantalla(), vw, vh); geometria = false; }
            Frame f = session.update();
            if (f.hasDisplayGeometryChanged()) {
                quad.position(0); quadTex.position(0);
                f.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, quadTex);
                quad.position(0); quadTex.position(0);
            }
            if (f.getTimestamp() == 0) return;
            dibujar();
            Camera cam = f.getCamera();
            boolean sigue = cam.getTrackingState() == TrackingState.TRACKING;
            float centro = sigue ? distanciaCentro(f) : -1f;
            float alto = sigue ? alturaSuelo(cam) : -1f;
            long ahora = System.currentTimeMillis();
            if (ahora - ultInfo > 200) { ultInfo = ahora; info(sigue, centro, alto); }
            if (pedir) {
                pedir = false;
                capturar(f, cam, sigue, centro, alto);
            }
        } catch (Throwable e) {
            // un cuadro que falla no detiene la sesión
        }
    }

    private void dibujar() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
        GLES20.glUseProgram(prog);
        quad.position(0); quadTex.position(0);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, quadTex);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aTex);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aTex);
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private int rotacionPantalla() {
        try { return getWindowManager().getDefaultDisplay().getRotation(); } catch (Throwable e) { return Surface.ROTATION_0; }
    }

    /* la distancia (m) a lo que está en la mira: el primer punto de profundidad, plano o punto que toca el rayo */
    private float distanciaCentro(Frame f) {
        try {
            List<HitResult> L = f.hitTest(vw / 2f, vh / 2f);
            for (HitResult h : L) {
                Trackable t = h.getTrackable();
                if (t instanceof DepthPoint || t instanceof Point || (t instanceof Plane && ((Plane) t).isPoseInPolygon(h.getHitPose()))) return h.getDistance();
            }
        } catch (Throwable e) {
        }
        return -1f;
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

    private void info(final boolean sigue, final float centro, final float alto) {
        runOnUiThread(new Runnable() { @Override public void run() {
            if (!sigue) { tInfo.setText(txt("buscando", "Mueve el teléfono despacio para que reconozca el lugar")); return; }
            String a = centro > 0 ? txt("distancia", "Distancia a la mira") + ": " + String.format(Locale.getDefault(), "%.2f m", centro) : txt("sinMira", "Apunta la mira al animal");
            String b = alto > 0 ? txt("altura", "Teléfono sobre el suelo") + ": " + String.format(Locale.getDefault(), "%.2f m", alto) : txt("sinSuelo", "Apunta un momento al suelo para encontrarlo");
            tInfo.setText(a + " · " + b);
        } });
    }

    /* ---------- la captura: foto, profundidad, intrínsecos y pose; la foto se comprime en otro hilo ---------- */
    private void capturar(Frame f, Camera cam, boolean sigue, float centro, float alto) {
        final JSONObject o = new JSONObject();
        Image img = null, dep = null;
        try {
            JSONObject p = pasos.optJSONObject(Math.min(ip, pasos.length() - 1));
            o.put("paso", p == null ? "" : p.optString("id", ""));
            o.put("ts", System.currentTimeMillis());
            o.put("sigue", sigue);
            if (centro > 0) o.put("centro", centro);
            if (alto > 0) o.put("alto", alto);
            final int rot = rotacionImagen();
            o.put("rot", rot);
            Pose ps = cam.getDisplayOrientedPose();
            float[] z = ps.getZAxis(), x = ps.getXAxis();
            o.put("elev", Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -z[1])))));
            o.put("ladeo", Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, x[1])))));
            CameraIntrinsics ci = cam.getImageIntrinsics();
            float[] fl = ci.getFocalLength(), pp = ci.getPrincipalPoint();
            int[] dim = ci.getImageDimensions();
            img = f.acquireCameraImage();
            final int W = img.getWidth(), H = img.getHeight();
            // intrínsecos en la foto ya girada (y escalados si la imagen no mide lo mismo que los intrínsecos)
            float sx = dim[0] > 0 ? (float) W / dim[0] : 1f, sy = dim[1] > 0 ? (float) H / dim[1] : 1f;
            float fx = fl[0] * sx, fy = fl[1] * sy, cx = pp[0] * sx, cy = pp[1] * sy;
            float[] r = girarIntr(rot, W, H, fx, fy, cx, cy);
            o.put("fx", r[0]); o.put("fy", r[1]); o.put("cx", r[2]); o.put("cy", r[3]);
            o.put("W", rot % 180 == 0 ? W : H); o.put("H", rot % 180 == 0 ? H : W);
            final byte[] nv = nv21(img);
            img.close(); img = null;
            if (profundidad) {
                try {
                    dep = f.acquireDepthImage16Bits();
                    int dw = dep.getWidth(), dh = dep.getHeight();
                    Image.Plane pl = dep.getPlanes()[0];
                    ByteBuffer bb = pl.getBuffer().order(ByteOrder.LITTLE_ENDIAN);
                    int rs = pl.getRowStride(), px = pl.getPixelStride();
                    short[] d = new short[dw * dh];
                    for (int yy = 0; yy < dh; yy++) for (int xx = 0; xx < dw; xx++) d[yy * dw + xx] = bb.getShort(yy * rs + xx * px);
                    dep.close(); dep = null;
                    /* la profundidad no tiene el mismo encuadre que la foto (CPU): va con la textura de la cámara. Se arma
                       una rejilla con el encuadre de la foto: cada celda (en píxeles de la foto) se lleva a coordenadas de
                       la textura con ARCore y ahí se lee la profundidad */
                    int GW = 160, GH = Math.max(1, Math.round(160f * H / W));
                    FloatBuffer pin = ByteBuffer.allocateDirect(GW * GH * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    FloatBuffer pout = ByteBuffer.allocateDirect(GW * GH * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    for (int j = 0; j < GH; j++) for (int i = 0; i < GW; i++) { pin.put((i + .5f) * W / GW); pin.put((j + .5f) * H / GH); }
                    pin.position(0);
                    f.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS, pin, Coordinates2d.TEXTURE_NORMALIZED, pout);
                    short[] al = new short[GW * GH];
                    for (int k = 0; k < GW * GH; k++) {
                        float u = pout.get(2 * k), v = pout.get(2 * k + 1);
                        int xx = (int) (u * dw), yy = (int) (v * dh);
                        al[k] = xx >= 0 && yy >= 0 && xx < dw && yy < dh ? d[yy * dw + xx] : 0;
                    }
                    // la profundidad en la mira (el centro de la pantalla): la mediana de 5×5
                    try {
                        FloatBuffer cin = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asFloatBuffer(), cout = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asFloatBuffer();
                        cin.put(vw / 2f); cin.put(vh / 2f); cin.position(0);
                        f.transformCoordinates2d(Coordinates2d.VIEW, cin, Coordinates2d.TEXTURE_NORMALIZED, cout);
                        int cx0 = (int) (cout.get(0) * dw), cy0 = (int) (cout.get(1) * dh), n = 0;
                        int[] m = new int[25];
                        for (int yy = cy0 - 2; yy <= cy0 + 2; yy++) for (int xx = cx0 - 2; xx <= cx0 + 2; xx++)
                            if (xx >= 0 && yy >= 0 && xx < dw && yy < dh) { int v = d[yy * dw + xx] & 0xFFFF; if (v > 0) m[n++] = v; }
                        if (n > 0) { java.util.Arrays.sort(m, 0, n); o.put("centroProf", m[n / 2] / 1000.0); }
                    } catch (Throwable e) {
                    }
                    o.put("profOrig", dw + "x" + dh);
                    int[] nd = new int[2];
                    short[] g = girar(al, GW, GH, rot, nd);
                    ByteBuffer out = ByteBuffer.allocate(g.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                    for (short v : g) out.putShort(v);
                    JSONObject jd = new JSONObject();
                    jd.put("w", nd[0]); jd.put("h", nd[1]); jd.put("mm", Base64.encodeToString(out.array(), Base64.NO_WRAP));
                    o.put("prof", jd);
                } catch (NotYetAvailableException e) {
                    o.put("profNo", "todavia");
                }
            }
            final String id = "ar-" + System.currentTimeMillis() + "-" + ip;
            o.put("foto", id);
            capturas.put(o);
            ip++;
            final boolean fin = ip >= pasos.length();
            hilo.execute(new Runnable() { @Override public void run() {
                try {
                    YuvImage y = new YuvImage(nv, ImageFormat.NV21, W, H, null);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    y.compressToJpeg(new Rect(0, 0, W, H), 92, bo);
                    byte[] jpg = bo.toByteArray();
                    if (rot != 0) {
                        Bitmap bm = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
                        Matrix m = new Matrix();
                        m.postRotate(rot);
                        Bitmap r2 = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
                        r2.compress(Bitmap.CompressFormat.JPEG, 92, b2);
                        jpg = b2.toByteArray();
                        bm.recycle(); r2.recycle();
                    }
                    File dir = new File(getFilesDir(), "fotos");
                    if (!dir.exists()) dir.mkdirs();
                    FileOutputStream fo = new FileOutputStream(new File(dir, id + ".jpg"));
                    fo.write(jpg);
                    fo.close();
                } catch (Throwable e) {
                    try { o.put("errorFoto", e.getClass().getSimpleName()); } catch (Throwable e2) { }
                }
            } });
            runOnUiThread(new Runnable() { @Override public void run() {
                if (fin) terminar(); else pintarPaso();
            } });
        } catch (NotYetAvailableException e) {
            // la imagen todavía no está: se intenta en el cuadro siguiente
            pedir = true;
        } catch (Throwable e) {
            runOnUiThread(new Runnable() { @Override public void run() { pintarPaso(); } });
        } finally {
            if (img != null) try { img.close(); } catch (Throwable e) { }
            if (dep != null) try { dep.close(); } catch (Throwable e) { }
        }
    }

    /* cuántos grados (en el sentido del reloj) hay que girar la imagen de la cámara para verla como en la pantalla */
    private int rotacionImagen() {
        int sensor = 90;
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (camId != null) {
                Integer s = cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (s != null) sensor = s;
            }
        } catch (Throwable e) {
        }
        int pant = 0;
        switch (rotacionPantalla()) {
            case Surface.ROTATION_90: pant = 90; break;
            case Surface.ROTATION_180: pant = 180; break;
            case Surface.ROTATION_270: pant = 270; break;
        }
        return ((sensor - pant) % 360 + 360) % 360;
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

    private static byte[] nv21(Image img) {
        int w = img.getWidth(), h = img.getHeight();
        byte[] out = new byte[w * h * 3 / 2];
        Image.Plane[] p = img.getPlanes();
        ByteBuffer y = p[0].getBuffer(), u = p[1].getBuffer(), v = p[2].getBuffer();
        int yr = p[0].getRowStride(), yp = p[0].getPixelStride();
        int pos = 0;
        if (yp == 1 && yr == w) { y.position(0); y.get(out, 0, w * h); pos = w * h; }
        else for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) out[pos++] = y.get(r * yr + c * yp);
        int ur = p[1].getRowStride(), up = p[1].getPixelStride(), vr = p[2].getRowStride(), vp = p[2].getPixelStride();
        for (int r = 0; r < h / 2; r++) for (int c = 0; c < w / 2; c++) {
            out[pos++] = v.get(r * vr + c * vp);
            out[pos++] = u.get(r * ur + c * up);
        }
        return out;
    }

    /* la mira en el centro */
    private static class Mira extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), s = new Paint(Paint.ANTI_ALIAS_FLAG);
        Mira(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * d);
            s.setColor(0x88000000); s.setStyle(Paint.Style.STROKE); s.setStrokeWidth(5f * d);
        }
        @Override protected void onDraw(Canvas c) {
            float x = getWidth() / 2f, y = getHeight() / 2f, d = getResources().getDisplayMetrics().density, r = 16 * d, l = 30 * d;
            for (Paint q : new Paint[]{s, p}) {
                c.drawCircle(x, y, r, q);
                c.drawLine(x - l, y, x - r * .5f, y, q); c.drawLine(x + r * .5f, y, x + l, y, q);
                c.drawLine(x, y - l, x, y - r * .5f, q); c.drawLine(x, y + r * .5f, x, y + l, q);
            }
        }
    }
}
