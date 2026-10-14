package com.pingpaisa.app.recognition

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pingpaisa.app.catalog.ImageLabelHit
import kotlinx.coroutines.tasks.await

fun Rect?.toBox(): Box? = this?.let { Box(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }

fun Text.toOcrLines(): List<OcrLine> =
    textBlocks.flatMap { it.lines }.map { OcrLine(it.text, it.boundingBox.toBox(), it.confidence) }

/** ML Kit wrappers. Both models are bundled, so OCR and labelling work with no internet. */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())

    fun recognizeRaw(image: InputImage): Task<Text> = recognizer.process(image)

    suspend fun readText(bitmap: Bitmap): List<OcrLine> =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().toOcrLines()

    suspend fun labels(bitmap: Bitmap): List<ImageLabelHit> =
        labeler.process(InputImage.fromBitmap(bitmap, 0)).await().map { ImageLabelHit(it.text, it.confidence) }
}
