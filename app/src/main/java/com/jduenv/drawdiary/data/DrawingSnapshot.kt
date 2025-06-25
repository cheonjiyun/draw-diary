package com.jduenv.drawdiary.data

import android.graphics.Bitmap
import com.jduenv.drawdiary.customView.StrokeData

data class DrawingSnapshot(
    val fillBitmap: Bitmap,
    val strokes: List<StrokeData>
)
