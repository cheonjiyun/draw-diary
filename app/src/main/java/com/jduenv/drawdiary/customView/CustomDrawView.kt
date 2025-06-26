package com.jduenv.drawdiary.customView

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

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

data class UndoState(
    val bitmap: Bitmap,           // 현재 눈에 보이는 이미지
    val strokes: List<Stroke>     // 벡터 정보
)

enum class ToolMode {
    DRAW,        // 그리기
    ERASE_VECTOR,// 벡터 단위로 획 지우기
    ERASE_AREA, // 비트맵(pixel) 단위로 지우기
    FILL // 채우기 통
}

class CustomDrawView(context: Context, attrs: AttributeSet?) :
    View(context, attrs) {

    init {
        // PorterDuff.Mode.CLEAR 가 동작하게끔 소프트웨어 레이어로 설정
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private var onCanvasInitializedListener: ((width: Int, height: Int) -> Unit)? = null

    fun setOnCanvasInitializedListener(listener: (Int, Int) -> Unit) {
        onCanvasInitializedListener = listener
    }

    private lateinit var strokeCompleteListener: (StrokeData) -> Unit
    fun setOnStrokeCompleteListener(listener: (StrokeData) -> Unit) {
        strokeCompleteListener = listener
    }

    // (1) 지우기 시작 콜백
    private var onSnapshotForUndo: (() -> Unit)? = null
    fun setOnSnapshotForUndo(listener: () -> Unit) {
        onSnapshotForUndo = listener
    }

    // (2) 지우기 동작 콜백 (mode, x, y)
    private lateinit var eraseListener: (ToolMode, Float, Float) -> Unit
    fun setOnEraseListener(listener: (ToolMode, Float, Float) -> Unit) {
        eraseListener = listener
    }

    private var onFillCompleteListener: ((Bitmap) -> Unit)? = null
    fun setOnFillCompleteListener(listener: (Bitmap) -> Unit) {
        onFillCompleteListener = listener
    }


    /** 그리기 · 벡터 지우개 · 비트맵 지우개 모드를 구분 */
    var currentMode: ToolMode = ToolMode.DRAW

    var currentStroke: Int = 10
    var currentColor: Int = Color.BLACK

    private lateinit var bitmapBuffer: Bitmap
    private lateinit var canvasBuffer: Canvas

    // 별도로 보관할 레이어 하나 추가
    private lateinit var bitmapFillLayer: Bitmap
    private lateinit var canvasFillLayer: Canvas


    private val strokes = mutableListOf<Stroke>() // 굵기를 바꾸었을 때 기존 굵기는 바뀌면 안되므로 선을 구분해서 list에 담음

    /** 이미 열어둔 ENTRY_NAME이 있으면 여기에 저장 */
    private var entryName: String? = null

    /** Activity에서 호출해서 ENTRY_NAME을 세팅해 둡니다 */
    fun setEntryName(name: String) {
        entryName = name
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 채우기 레이어
        canvas.drawBitmap(bitmapFillLayer, 0f, 0f, null)

        // 2. stroke 레이어
        for (stroke in strokes) {
            val path = Path().apply {
                stroke.points.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y)
                    else lineTo(pt.x, pt.y)
                }
            }
            canvas.drawPath(path, stroke.paint)
        }

        // 3. 현재 드로잉 중인 것
        currentDrawPath?.let { canvas.drawPath(it, currentDrawPaint) }
    }


    private var isDrawingGesture = false
    private var isErasingAreaGesture = false
    private var isErasingVectorGesture = false
    private var prevEraseX: Float? = null
    private var prevEraseY: Float? = null

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
                            onSnapshotForUndo?.invoke()
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
//                        strokes.add(Stroke(points, Paint(currentDrawPaint)))

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
                            onSnapshotForUndo?.invoke()
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
                            onSnapshotForUndo?.invoke()
                            isErasingAreaGesture = true
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        prevEraseX?.let { lastX ->
                            prevEraseY?.let { lastY ->
                                val dx = x - lastX
                                val dy = y - lastY
                                val distance = hypot(dx, dy)
                                val steps = (distance / 5).toInt()

                                for (i in 0..steps) {
                                    val t = i / steps.toFloat()
                                    val cx = lastX + t * dx
                                    val cy = lastY + t * dy

                                    canvasFillLayer.drawCircle(cx, cy, 30f, Paint().apply {
                                        isAntiAlias = true
                                        style = Paint.Style.FILL
                                        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                    })
                                }
                            }
                        }

                        // 다음 비교를 위해 저장
                        prevEraseX = x
                        prevEraseY = y

                        eraseListener(ToolMode.ERASE_AREA, x, y)
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        prevEraseX = null
                        prevEraseY = null
                    }
                }
            }

            ToolMode.FILL -> {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onSnapshotForUndo?.invoke()

                    val px = event.x.toInt()
                    val py = event.y.toInt()

                    // 이미 stroke + fill 이 다 반영된 비트맵을 기준으로 사용
                    val referenceBitmap = bitmapBuffer  // or canvasBuffer.bitmap if 캡처 가능

                    val targetColor = referenceBitmap.getPixel(px, py)

                    if (targetColor != currentColor) {
                        floodFill(
                            bitmapFillLayer,
                            px,
                            py,
                            targetColor,
                            currentColor,
                            referenceBitmap
                        )
                        onFillCompleteListener?.invoke(bitmapFillLayer)
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
        bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvasBuffer = Canvas(bitmapBuffer)

        bitmapFillLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvasFillLayer = Canvas(bitmapFillLayer)

        canvasBuffer.drawColor(Color.WHITE)
        canvasFillLayer.drawColor(Color.TRANSPARENT) // 투명으로

        redrawBitmapBuffer()
        invalidate()

        onCanvasInitializedListener?.invoke(width, height)
    }

    fun getMergedBitmap(): Bitmap {


        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. fill 포함된 버퍼 먼저 그리기 (배경 + 페인트통)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmapFillLayer, 0f, 0f, null)

        // 2. strokes(벡터) 다시 그리기
        for (stroke in strokes) {
            val path = Path().apply {
                stroke.points.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y)
                    else lineTo(pt.x, pt.y)
                }
            }
            canvas.drawPath(path, stroke.paint)
        }

        return bmp
    }


    fun getCurrentBitmap(): Bitmap {
        return bitmapFillLayer.copy(Bitmap.Config.ARGB_8888, true)
    }

    fun update(bitmap: Bitmap, strokes: List<StrokeData>) {
        bitmapFillLayer = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        canvasFillLayer = Canvas(bitmapFillLayer)
        setStrokes(strokes)
        invalidate()
    }


    private fun redrawBitmapBuffer() {
        if (!::canvasBuffer.isInitialized) return


        canvasBuffer.drawColor(Color.WHITE) // 초기화

//        // ② 이전 buffer 내용을 복사 (floodFill된 비트맵 반영)
        canvasFillLayer.drawBitmap(bitmapFillLayer, 0f, 0f, null)


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

    fun isSimilarColor(c1: Int, c2: Int, threshold: Int = 10): Boolean {
        val r1 = Color.red(c1)
        val g1 = Color.green(c1)
        val b1 = Color.blue(c1)

        val r2 = Color.red(c2)
        val g2 = Color.green(c2)
        val b2 = Color.blue(c2)

        return (Math.abs(r1 - r2) < threshold
                && Math.abs(g1 - g2) < threshold
                && Math.abs(b1 - b2) < threshold)
    }

    private fun floodFill(
        targetBitmap: Bitmap,
        x: Int, y: Int,
        targetColor: Int,
        replacementColor: Int,
        referenceBitmap: Bitmap
    ) {
        val width = targetBitmap.width
        val height = targetBitmap.height

        if (x !in 0 until width || y !in 0 until height) return
        if (isSimilarColor(targetColor, replacementColor)) return

        // referenceBitmap → 배열로 미리 뽑기
        val referencePixels = IntArray(width * height)
        referenceBitmap.getPixels(referencePixels, 0, width, 0, 0, width, height)

        val targetPixels = IntArray(width * height)
        targetBitmap.getPixels(targetPixels, 0, width, 0, 0, width, height)

        val visited = BooleanArray(width * height)

        fun getColor(px: Int, py: Int): Int = referencePixels[py * width + px]
        fun setColor(px: Int, py: Int, color: Int) {
            targetPixels[py * width + px] = color
        }

        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(x, y))

        while (queue.isNotEmpty()) {
            val (startX, startY) = queue.removeFirst()
            var left = startX
            var right = startX

            // 왼쪽으로 확장
            while (left > 0 &&
                !visited[startY * width + (left - 1)] &&
                isSimilarColor(getColor(left - 1, startY), targetColor)
            ) {
                left--
            }

            // 오른쪽으로 확장
            while (right < width - 1 &&
                !visited[startY * width + (right + 1)] &&
                isSimilarColor(getColor(right + 1, startY), targetColor)
            ) {
                right++
            }

            // 현재 라인 채우기
            for (i in left..right) {
                val index = startY * width + i
                if (!visited[index]) {
                    setColor(i, startY, replacementColor)
                    visited[index] = true

                    // 위 라인 체크
                    if (startY > 0 && !visited[(startY - 1) * width + i] &&
                        isSimilarColor(getColor(i, startY - 1), targetColor)
                    ) {
                        queue.add(Pair(i, startY - 1))
                    }

                    // 아래 라인 체크
                    if (startY < height - 1 && !visited[(startY + 1) * width + i] &&
                        isSimilarColor(getColor(i, startY + 1), targetColor)
                    ) {
                        queue.add(Pair(i, startY + 1))
                    }
                }
            }
        }

        // 최종 픽셀 반영
        targetBitmap.setPixels(targetPixels, 0, width, 0, 0, width, height)
    }

}