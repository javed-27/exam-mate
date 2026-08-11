package com.exammate.ui.mcq

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import com.exammate.BuildConfig
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraPreview"
private const val MIN_FRAME_INTERVAL_MILLIS = 500L
private const val MAX_DEBUG_FRAMES = 20
private const val MAX_FRAME_EDGE = 1440

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
                val debugFrameDir = if (BuildConfig.DEBUG) {
                    File(ctx.getExternalFilesDir(null), "debug_frames").apply { mkdirs() }
                } else {
                    null
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        provider.unbindAll()
                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(surfaceProvider) }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1920, 1080))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        imageAnalysis.setAnalyzer(analysisExecutor, ImageAnalyzer(frameCallback, debugFrameDir))
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
    private val debugFrameDir: File? = null,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var lastAnalyzedAtMillis: Long = 0L

    private val debugExecutor = debugFrameDir?.let {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "camera-debug-frame").apply { isDaemon = true }
        }
    }

    @Volatile
    private var debugSaveInFlight: Boolean = false

    override fun analyze(imageProxy: ImageProxy) {
        try {
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
            val scaled = scaleDown(rotated, MAX_FRAME_EDGE)
            if (scaled !== rotated) rotated.recycle()
            Log.d(TAG, "frame ${scaled.width}x${scaled.height} rot=$rotationDegrees")
            saveDebugFrameAsync(scaled)
            frameCallback.callback?.invoke(scaled)
        } catch (e: Throwable) {
            Log.e(TAG, "analyze failed; keeping capture alive", e)
            runCatching { imageProxy.close() }
        }
    }

    private fun saveDebugFrameAsync(bitmap: Bitmap) {
        if (debugExecutor == null || debugSaveInFlight) return
        debugSaveInFlight = true
        debugExecutor.execute {
            try {
                saveDebugFrame(bitmap)
            } catch (e: Throwable) {
                Log.w(TAG, "debug frame save failed", e)
            } finally {
                debugSaveInFlight = false
            }
        }
    }

    private fun saveDebugFrame(bitmap: Bitmap) {
        val dir = debugFrameDir ?: return
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        while (files.size >= MAX_DEBUG_FRAMES) files.first().delete()
        val frameFile = File(dir, "frame_${SystemClock.elapsedRealtime()}.jpg")
        FileOutputStream(frameFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
