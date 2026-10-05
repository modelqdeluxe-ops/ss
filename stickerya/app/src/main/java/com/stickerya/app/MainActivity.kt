package com.stickerya.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stickerya.app.ui.EditorScreen
import com.stickerya.app.ui.HomeScreen
import com.stickerya.app.ui.PackScreen
import com.stickerya.app.ui.SourceSheet
import com.stickerya.app.ui.StickerYaTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            StickerYaTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.resumeTick++
    }

    /** Imagen compartida desde otra app (galería, WhatsApp...): directo al editor. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) vm.openImage(uri, null)
    }
}

/** De dónde sale el sticker nuevo y a qué paquete irá. */
class NewStickerRequest(val packId: String?)

@Composable
fun App(vm: AppViewModel) {
    val snackHost = remember { SnackbarHostState() }
    var request by remember { mutableStateOf<NewStickerRequest?>(null) }
    var pendingPack by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.openImage(uri, pendingPack)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) vm.openImage(Uri.parse(u), pendingPack)
    }

    val startGallery = { packId: String? ->
        pendingPack = packId
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val startCamera = { packId: String? ->
        pendingPack = packId
        val file = vm.cameraFile()
        val uri = FileProvider.getUriForFile(context, "com.stickerya.app.files", file)
        cameraUri = uri.toString()
        runCatching { camera.launch(uri) }.onFailure { vm.snackbar = "No hay app de cámara" }
        Unit
    }

    LaunchedEffect(vm.snackbar) {
        vm.snackbar?.let {
            snackHost.showSnackbar(it)
            vm.snackbar = null
        }
    }

    BackHandler(enabled = vm.stack.size > 1 && vm.screen !is Screen.Editor) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        when (val s = vm.screen) {
            Screen.Home -> HomeScreen(
                vm,
                onNewSticker = { request = NewStickerRequest(null) },
                onGallery = { startGallery(null) },
                onCamera = { startCamera(null) },
            )
            is Screen.Pack -> PackScreen(vm, s.id, onNewSticker = { request = NewStickerRequest(s.id) })
            is Screen.Editor -> EditorScreen(vm, s.session)
        }

        if (vm.loading) {
            Box(
                Modifier.fillMaxSize().background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        SnackbarHost(snackHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
    }

    request?.let { req ->
        SourceSheet(
            onDismiss = { request = null },
            onGallery = {
                request = null
                startGallery(req.packId)
            },
            onCamera = {
                request = null
                startCamera(req.packId)
            },
            onText = {
                request = null
                vm.openBlank(req.packId)
            },
        )
    }
}
