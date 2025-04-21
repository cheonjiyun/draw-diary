package com.ssafy.drawdiary.CustomView

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.ssafy.drawdiary.Activity.DrawingActivity

private const val TAG = "CustomDrawView_싸피"

class CustomDrawView(context: Context, attrs: AttributeSet?) :
    View(context, attrs) {

    data class Stroke(val path: Path, val paint: Paint)

    var currentStroke: Int = 10
        set(value) {
            field = when {
                value < 1 -> 1
                value > 100 -> 100
                else -> value
            }
        }

    val strokes = mutableListOf<Stroke>() // 굵기를 바꾸었을 때 기존 굵기는 바뀌면 안되므로 선을 구분해서 list에 담음
    val redoStack = mutableListOf<Stroke>()
    private var currentPath: Path? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in strokes) {
            canvas.drawPath(stroke.path, stroke.paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {

                // 그릴위치
                currentPath = Path().apply {
                    moveTo(event.x, event.y)
                }

                // 굵기
                val newPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND

                    strokeWidth = currentStroke.toFloat() // 굵기
                }

                // 선 정보에 저장
                strokes.add(Stroke(currentPath!!, newPaint))
            }

            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(x, y)
            }

            MotionEvent.ACTION_UP -> {

            }

        }
        invalidate()
        (context as DrawingActivity)?.updateUI()

        return true
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            val lastStroke = strokes.removeAt(strokes.lastIndex) // 마지막 하나 빼기
            redoStack.add(lastStroke)
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val lastRedo = redoStack.removeAt(redoStack.lastIndex) // 마지막 하나 빼서
            strokes.add(lastRedo)
            invalidate()
        }
    }
}