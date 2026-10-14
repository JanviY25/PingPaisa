package com.pingpaisa.app.recognition

import kotlin.math.max

/** Aiming box (in preview-view pixels) plus the size of the view it was measured in. */
data class FocusSpec(val viewWidth: Int, val viewHeight: Int, val box: Box)

/**
 * PreviewView (FILL_CENTER) scales the camera image to cover the view and crops the overflow.
 * This maps the on-screen bracket rectangle back into camera-image pixels so OCR can prefer text
 * the user actually aimed at.
 */
object ViewportMapper {
    fun focusInImage(spec: FocusSpec, imageWidth: Int, imageHeight: Int): Box? {
        if (spec.viewWidth <= 0 || spec.viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
        val scale = max(spec.viewWidth.toFloat() / imageWidth, spec.viewHeight.toFloat() / imageHeight)
        val visibleW = spec.viewWidth / scale
        val visibleH = spec.viewHeight / scale
        val x0 = (imageWidth - visibleW) / 2f
        val y0 = (imageHeight - visibleH) / 2f
        val b = spec.box
        return Box(
            x0 + b.left / scale, y0 + b.top / scale,
            x0 + b.right / scale, y0 + b.bottom / scale
        )
    }
}
