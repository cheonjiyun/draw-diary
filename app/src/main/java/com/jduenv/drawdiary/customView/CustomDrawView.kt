package com.jduenv.drawdiary.customView

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

private const val TAG = "CustomDrawView_싸피"

private var currentDrawPath: Path? = null
private lateinit var currentDrawPaint: Paint

// JSON 직렬화용 데이터 클래스
data class StrokeData(
    val points: List<PointF>,
    val strokeWidth: Float,
    val color: Int
)

data class Stroke(
    val points: MutableList<PointF>,
    val paint: Paint
)

enum class ToolMode {
    DRAW,        // 그리기
    ERASE_VECTOR,// 벡터 단위로 획 지우기
    ERASE_AREA // 비트맵(pixel) 단위로 지우기
}

class CustomDrawView(context: Context, attrs: AttributeSet?) :
    View(context, attrs) {

    init {
        // PorterDuff.Mode.CLEAR 가 동작하게끔 소프트웨어 레이어로 설정
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private lateinit var strokeCompleteListener: (StrokeData) -> Unit
    fun setOnStrokeCompleteListener(listener: (StrokeData) -> Unit) {
        strokeCompleteListener = listener
    }

    // (1) 지우기 시작 콜백
    private var onEraseStartListener: (() -> Unit)? = null
    fun setOnEraseStartListener(listener: () -> Unit) {
        onEraseStartListener = listener
    }

    // (2) 지우기 동작 콜백 (mode, x, y)
    private lateinit var eraseListener: (ToolMode, Float, Float) -> Unit
    fun setOnEraseListener(listener: (ToolMode, Float, Float) -> Unit) {
        eraseListener = listener
    }

    /** 그리기 · 벡터 지우개 · 비트맵 지우개 모드를 구분 */
    var currentMode: ToolMode = ToolMode.DRAW

    var currentStroke: Int = 10
    var currentColor: Int = Color.BLACK

    private lateinit var bitmapBuffer: Bitmap
    private lateinit var canvasBuffer: Canvas

    private val strokes = mutableListOf<Stroke>() // 굵기를 바꾸었을 때 기존 굵기는 바뀌면 안되므로 선을 구분해서 list에 담음

    /** 이미 열어둔 ENTRY_NAME이 있으면 여기에 저장 */
    private var entryName: String? = null

    /** Activity에서 호출해서 ENTRY_NAME을 세팅해 둡니다 */
    fun setEntryName(name: String) {
        entryName = name
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 비트맵그리기
        canvas.drawBitmap(bitmapBuffer, 0f, 0f, null)

        for (stroke in strokes) {
            val path = Path().apply {
                stroke.points.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y)
                    else lineTo(pt.x, pt.y)
                }
            }
            canvas.drawPath(path, stroke.paint)
        }


        currentDrawPath?.let { canvas.drawPath(it, currentDrawPaint) }

    }


    private var isDrawingGesture = false
    private var isErasingAreaGesture = false
    private var isErasingVectorGesture = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (currentMode) {
            ToolMode.DRAW -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // — 그리기 제스처 시작 시점에만 스냅샷
                        if (!isDrawingGesture) {
                            isDrawingGesture = true
                        }

                        // 1) 새 Path와 Paint 생성해서 저장
                        currentDrawPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeWidth = currentStroke.toFloat()
                            color = currentColor
                        }
                        currentDrawPath = Path().apply { moveTo(x, y) }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // 2) 경로 이어 붙이기
                        currentDrawPath?.lineTo(x, y)
                    }

                    MotionEvent.ACTION_UP -> {
                        // 1) 버퍼에 커밋
                        currentDrawPath?.let { canvasBuffer.drawPath(it, currentDrawPaint) }

                        // 2) strokes 리스트에도 보관 (JSON 저장용!)
                        val points = mutableListOf<PointF>()
                        currentDrawPath?.let { path ->
                            // Path → PointF 리스트로 변환
                            val pm = PathMeasure(path, false)
                            val coords = FloatArray(2)
                            var distance = 0f
                            while (distance < pm.length) {
                                pm.getPosTan(distance, coords, null)
                                points.add(PointF(coords[0], coords[1]))
                                distance += 5f  // 샘플링 간격 (필요에 따라 조절)
                            }
                        }

                        val data = StrokeData(
                            points = points,
                            strokeWidth = currentDrawPaint.strokeWidth,
                            color = currentDrawPaint.color
                        )
                        strokeCompleteListener(data)

                        // Paint 복사
                        strokes.add(Stroke(points, Paint(currentDrawPaint)))

                        // 3) 임시 경로 초기화
                        currentDrawPath = null

                        // 제스처 종료
                        isDrawingGesture = false
                    }
                }
            }

            ToolMode.ERASE_VECTOR -> {
                // 다운이든 무브이든, 터치 위치에 근접한 stroke 모두 지우기
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {

                        // 한 번만 스냅샷 남기기
                        if (!isErasingVectorGesture) {
                            onEraseStartListener?.invoke()
                            isErasingVectorGesture = true
                        }

                        eraseListener(ToolMode.ERASE_VECTOR, x, y)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        eraseListener(ToolMode.ERASE_VECTOR, x, y)
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 제스처 종료 시 플래그 리셋
                        isErasingVectorGesture = false
                    }
                }
            }

            ToolMode.ERASE_AREA -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!isErasingAreaGesture) {
                            onEraseStartListener?.invoke()
                            isErasingAreaGesture = true
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        eraseListener(ToolMode.ERASE_AREA, x, y)
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isErasingAreaGesture = false
                    }
                }
            }
        }

        invalidate()

        return true
    }

    fun setStrokes(dataList: List<StrokeData>) {
        strokes.clear()
        dataList.forEach { d ->
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = d.strokeWidth
                color = d.color
            }
            strokes.add(Stroke(d.points.toMutableList(), paint))
        }
        redrawBitmapBuffer()
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        // 뷰 크기에 맞춘 빈 버퍼 비트맵 생성
        bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // 위 비트맵을 그릴 수 있는 캔버스 생성
        canvasBuffer = Canvas(bitmapBuffer)

        redrawBitmapBuffer()
        invalidate()
    }

    /**
     * JSON → StrokeData 리스트를 받아서 internal strokes 로 복원하고 화면을 다시 그림
     */
    fun setStrokesFromData(dataList: List<StrokeData>) {
        strokes.clear()
        for (d in dataList) {
            // Paint 복원
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = d.strokeWidth
                color = d.color
            }
            // PointF 리스트 → Path 로 변환 없이 points 만 보관
            strokes.add(Stroke(d.points.toMutableList(), paint))
        }

        redrawBitmapBuffer()
        invalidate()
    }

    fun captureBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        draw(c)
        return bmp
    }

    private fun redrawBitmapBuffer() {
        if (!::canvasBuffer.isInitialized) return

        // ① 배경 채우기
        (background as? ColorDrawable)?.let {
            canvasBuffer.drawColor(it.color)
        } ?: canvasBuffer.drawColor(Color.WHITE)

        Log.d(TAG, "redrawBitmapBuffer: ${strokes}")
        // ② strokes 리스트에 담긴 획 모두 그리기
        for (stroke in strokes) {
            val path = Path().apply {
                stroke.points.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y)
                    else lineTo(pt.x, pt.y)
                }
            }
            canvasBuffer.drawPath(path, stroke.paint)
        }
    }
}