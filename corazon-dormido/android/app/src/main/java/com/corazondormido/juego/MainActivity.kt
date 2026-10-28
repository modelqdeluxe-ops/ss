package com.corazondormido.juego

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

// El juego entero va dentro del APK (assets/juego) y se sirve desde un origen https propio, así que funciona sin
// internet y guarda la partida (IndexedDB) en el móvil. La actividad solo pone la pantalla completa, el botón
// «atrás» (cierra paneles o pausa), la vibración y avisa al juego al salir y al volver para que guarde.
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val cargador = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isHapticFeedbackEnabled = false
        }
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
        }
        web.addJavascriptInterface(Puente(), "CorazonApp")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                cargador.shouldInterceptRequest(request.url)

            // nada de salir a otras páginas
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != ORIGEN

            // si el sistema mata el proceso que dibuja, se vuelve a abrir (la partida está guardada)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                recreate()
                return true
            }
        }
        web.webChromeClient = WebChromeClient()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContentView(web)
        ocultarBarras()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.atrasAndroid ? atrasAndroid() : false") { r ->
                    if (r != "true") moveTaskToBack(true)
                }
            }
        })

        web.loadUrl("https://$ORIGEN/assets/juego/index.html")
    }

    private fun ocultarBarras() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ocultarBarras()
    }

    override fun onPause() {
        web.evaluateJavascript("window.appEnPausa && appEnPausa()", null)
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.evaluateJavascript("window.appVuelve && appVuelve()", null)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    // Lo que el juego puede pedirle al móvil (window.CorazonApp en js/app.js).
    inner class Puente {
        @JavascriptInterface
        fun vibrar(ms: Int) {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(ms.toLong().coerceIn(1, 400), VibrationEffect.DEFAULT_AMPLITUDE))
        }

        @JavascriptInterface
        fun salir() {
            runOnUiThread { finishAndRemoveTask() }
        }

        @JavascriptInterface
        fun version(): String = BuildConfig.VERSION_NAME
    }

    companion object {
        private const val ORIGEN = "appassets.androidplatform.net"
    }
}
