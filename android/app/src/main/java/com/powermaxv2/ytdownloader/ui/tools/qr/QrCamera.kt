package com.powermaxv2.ytdownloader.ui.tools.qr

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powermaxv2.ytdownloader.core.qr.QrCodec
import com.powermaxv2.ytdownloader.core.qr.ScanResult
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live back camera (CameraX) that reads every frame with ZXing until a code is found, then calls [onResult] once
 * (on the main thread) and stops reading. [torch] turns the phone's lamp on. The CAMERA permission must already be
 * granted; [onError] gets a message when the camera cannot start (no camera, used by another app…).
 */
@Composable
internal fun QrCameraPreview(torch: Boolean, onResult: (ScanResult) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val result by rememberUpdatedState(onResult)
    val fail by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val found = AtomicBoolean(false)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val main = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            try {
                val cameras = future.get()
                provider = cameras
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            )
                            .build(),
                    )
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        if (!found.get()) {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val read = QrCodec.decodeFrame(bytes, plane.rowStride, image.width, image.height)
                            if (read != null && found.compareAndSet(false, true)) main.execute { if (!disposed) result(read) }
                        }
                    } catch (e: RuntimeException) {
                        // A frame that cannot be read is skipped; the next one is tried.
                    } finally {
                        image.close()
                    }
                }
                cameras.unbindAll()
                camera = cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                fail("L'appareil photo ne s'ouvre pas. Il est peut-être utilisé par une autre app : réessayez, ou lisez le code depuis une image.")
            }
        }, main)
        onDispose {
            disposed = true
            provider?.unbindAll()
            camera = null
            executor.shutdown()
        }
    }

    LaunchedEffect(camera, torch) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
