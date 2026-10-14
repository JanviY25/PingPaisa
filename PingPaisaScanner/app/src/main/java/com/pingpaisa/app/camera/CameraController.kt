package com.pingpaisa.app.camera

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.pingpaisa.app.recognition.FocusSpec
import com.pingpaisa.app.recognition.OcrEngine
import com.pingpaisa.app.recognition.PriceExtractor
import com.pingpaisa.app.recognition.PriceResult
import com.pingpaisa.app.recognition.ViewportMapper
import com.pingpaisa.app.recognition.toOcrLines
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A price seen on two consecutive camera frames - shown in the "PRICE DETECTED" chip before capture. */
data class LivePrice(val value: Double, val confidence: Float)

suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({
        try { cont.resume(future.get()) } catch (e: Exception) { cont.resumeWithException(e) }
    }, ContextCompat.getMainExecutor(this))
}

/** Binds preview + still capture + a throttled OCR analyzer to a lifecycle. Portrait only. */
class CameraController(
    private val context: Context,
    ocr: OcrEngine,
    onLive: (LivePrice?) -> Unit
) {
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val executor = Executors.newSingleThreadExecutor()
    /** Set by the UI: where the scan brackets are, in preview-view pixels. */
    val focus = AtomicReference<FocusSpec?>(null)
    private val analyzer = LivePriceAnalyzer(ocr, { focus.get() }, onLive)

    fun bind(provider: ProcessCameraProvider, owner: LifecycleOwner, previewView: PreviewView) {
        this.provider = provider
        val ratio = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val selector = ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build()
        val analysisSelector = ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
            .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val preview = Preview.Builder().setResolutionSelector(selector).build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(selector).build()
        val analysis = ImageAnalysis.Builder().setResolutionSelector(analysisSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            .also { it.setAnalyzer(executor, analyzer) }
        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
        imageCapture = capture
    }

    suspend fun capture(): File = suspendCancellableCoroutine { cont ->
        val ic = imageCapture
        if (ic == null) { cont.resumeWithException(IllegalStateException("Camera not ready")); return@suspendCancellableCoroutine }
        val file = File.createTempFile("cap_", ".jpg", context.cacheDir)
        ic.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { cont.resume(file) }
                override fun onError(exception: ImageCaptureException) { file.delete(); cont.resumeWithException(exception) }
            }
        )
    }

    fun unbind() {
        provider?.unbindAll()
        imageCapture = null
    }
}

private class LivePriceAnalyzer(
    private val ocr: OcrEngine,
    private val focusProvider: () -> FocusSpec?,
    private val onResult: (LivePrice?) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastRun = 0L
    private var lastValue: Double? = null
    private var misses = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        val now = SystemClock.elapsedRealtime()
        if (media == null || now - lastRun < 700) { proxy.close(); return }
        lastRun = now
        val rotation = proxy.imageInfo.rotationDegrees
        val upW = if (rotation % 180 == 0) proxy.width else proxy.height
        val upH = if (rotation % 180 == 0) proxy.height else proxy.width
        ocr.recognizeRaw(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { text -> handle(PriceExtractorBridge.run(text.toOcrLines(), focusProvider(), upW, upH)) }
            .addOnFailureListener { handle(null) }
            .addOnCompleteListener { proxy.close() }
    }

    private fun handle(result: PriceResult?) {
        val best = result?.best
        if (result != null && best != null && best.hasCurrencyMarker && result.confidence >= PriceResult.LOW_CONFIDENCE) {
            misses = 0
            if (best.value == lastValue) onResult(LivePrice(best.value, result.confidence)) else lastValue = best.value
        } else if (++misses >= 3) {
            lastValue = null
            onResult(null)
        }
    }
}

private object PriceExtractorBridge {
    /** Only text inside the scan brackets counts for the live chip. */
    fun run(lines: List<com.pingpaisa.app.recognition.OcrLine>, spec: FocusSpec?, w: Int, h: Int): PriceResult {
        val box = spec?.let { ViewportMapper.focusInImage(it, w, h) }
        val inside = if (box == null) lines else lines.filter { it.box != null && box.containsCenterOf(it.box) }
        return PriceExtractor.extract(inside, box)
    }
}
