package com.exammate.ui.mcq

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraPreview"
private const val MIN_FRAME_INTERVAL_MILLIS = 500L

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrame: ((Bitmap) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val frameCallback = remember { FrameCallbackHolder() }
    frameCallback.callback = onFrame
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        onDispose {
            providerFuture.addListener({
                providerFuture.get().unbindAll()
            }, ContextCompat.getMainExecutor(context))
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        provider.unbindAll()
                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(surfaceProvider) }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        imageAnalysis.setAnalyzer(analysisExecutor, ImageAnalyzer(frameCallback))
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind camera preview", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}

private class FrameCallbackHolder {
    var callback: ((Bitmap) -> Unit)? = null
}

private class ImageAnalyzer(
    private val frameCallback: FrameCallbackHolder,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var lastAnalyzedAtMillis: Long = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyzedAtMillis < MIN_FRAME_INTERVAL_MILLIS) {
            imageProxy.close()
            return
        }
        lastAnalyzedAtMillis = now
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()
        val rotated = if (rotationDegrees == 0) bitmap else rotate(bitmap, rotationDegrees)
        frameCallback.callback?.invoke(rotated)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
