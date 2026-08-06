package com.exammate.mcq.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface OcrService {
    suspend fun recognize(bitmap: Bitmap): String

    fun close()
}

class MlKitOcrService : OcrService {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result -> continuation.resume(result.text) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }

    override fun close() = recognizer.close()
}
