package com.jduenv.drawdiary.CustomView

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
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.google.gson.Gson
import com.jduenv.drawdiary.Activity.DrawingActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /** 그리기 · 벡터 지우개 · 비트맵 지우개 모드를 구분 */
    var currentMode: ToolMode = ToolMode.DRAW

    // 지우개
    private var eraserPath: Path? = null
    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeWidth = eraserRadius * 2
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }


    private lateinit var bitmapBuffer: Bitmap
    private lateinit var canvasBuffer: Canvas

    // 내부적으로 Path 대신 포인트 리스트를 저장
    private data class Stroke(
        val points: MutableList<PointF>,
        val paint: Paint
    )

    // 이전상태
    private val undoHistory = mutableListOf<List<Stroke>>()
    private val redoHistory = mutableListOf<List<Stroke>>()

    // — performAreaErase 직전에 호출
    private fun snapshotForUndo() {
        // strokes 깊은 복사
        val copy = strokes.map { s ->
            Stroke(s.points.toMutableList(), Paint(s.paint))
        }
        undoHistory.add(copy)
        // 3) 최대 개수 초과 시, 가장 오래된 것부터 제거
        if (undoHistory.size > MAX_HISTORY) {
            undoHistory.removeAt(0)
        }
        // undo 이후에 redo 가능하도록 초기화
        redoHistory.clear()
    }


    private val strokes = mutableListOf<Stroke>() // 굵기를 바꾸었을 때 기존 굵기는 바뀌면 안되므로 선을 구분해서 list에 담음

    /** 이미 열어둔 ENTRY_NAME이 있으면 여기에 저장 */
    private var entryName: String? = null

    /** Activity에서 호출해서 ENTRY_NAME을 세팅해 둡니다 */
    fun setEntryName(name: String) {
        entryName = name
    }

    var currentStroke: Int
        get() = currentStrokeWidth
        set(v) = setStrokeWidth(v)

    private var currentStrokeWidth = 10
    fun setStrokeWidth(w: Int) {
        currentStrokeWidth = w.coerceIn(1, 100)
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 비트맵그리기
        canvas.drawBitmap(bitmapBuffer, 0f, 0f, null)

        currentDrawPath?.let { canvas.drawPath(it, currentDrawPaint) }

        for (stroke in strokes) {
            val path = Path().apply {
                stroke.points.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y)
                    else lineTo(pt.x, pt.y)
                }
            }
            canvas.drawPath(path, stroke.paint)
        }
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
                            snapshotForUndo()
                            isDrawingGesture = true
                        }

                        // 1) 새 Path와 Paint 생성해서 저장
                        currentDrawPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeWidth = currentStrokeWidth.toFloat()
                            color = Color.BLACK
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
                        currentDrawPath?.let { path ->
                            // Path → PointF 리스트로 변환
                            val points = mutableListOf<PointF>()
                            val pm = PathMeasure(path, false)
                            val coords = FloatArray(2)
                            var distance = 0f
                            while (distance < pm.length) {
                                pm.getPosTan(distance, coords, null)
                                points.add(PointF(coords[0], coords[1]))
                                distance += 5f  // 샘플링 간격 (필요에 따라 조절)
                            }
                            // Paint 복사
                            val paintCopy = Paint(currentDrawPaint)
                            strokes.add(Stroke(points, paintCopy))
                        }

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
                            snapshotForUndo()
                            isErasingVectorGesture = true
                        }
                        performVectorErase(x, y)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        performVectorErase(x, y)
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
                            snapshotForUndo()
                            isErasingAreaGesture = true
                        }
                        performAreaErase(x, y)
                    }

                    MotionEvent.ACTION_MOVE -> performAreaErase(x, y)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isErasingAreaGesture = false
                    }
                }
            }
        }


        invalidate()
        (context as? DrawingActivity)?.updateUI()

        return true
    }

    // CustomDrawView.kt 에 추가
    val canUndo: Boolean
        get() = undoHistory.isNotEmpty()

    val canRedo: Boolean
        get() = redoHistory.isNotEmpty()

    fun undo() {
        Log.d(TAG, "undo: ")
        Log.d(TAG, "undo: ${undoHistory}")
        if (undoHistory.isEmpty()) return
        // 1) 현재 상태를 redo 스택에 저장
        val currentCopy = strokes.map { s ->
            Stroke(s.points.toMutableList(), Paint(s.paint))
        }
        redoHistory.add(currentCopy)
        if (redoHistory.size > MAX_HISTORY) {
            redoHistory.removeAt(0)
        }

        // 2) undoHistory에서 마지막 스냅샷 복원
        val prev = undoHistory.removeAt(undoHistory.lastIndex)
        Log.d(TAG, "undo: ${undoHistory}")
        strokes.clear()
        strokes.addAll(prev)

        // 3) buffer & 화면 갱신
        redrawBitmapBuffer()
        invalidate()
    }

    /** 다시 앞으로 (redo) */
    fun redo() {
        if (redoHistory.isEmpty()) return
        // 1) 현재 상태를 undo 스택에 저장
        val currentCopy = strokes.map { s ->
            Stroke(s.points.toMutableList(), Paint(s.paint))
        }
        undoHistory.add(currentCopy)
        if (undoHistory.size > MAX_HISTORY) {
            undoHistory.removeAt(0)
        }

        // 2) redoHistory에서 마지막 복원
        val next = redoHistory.removeAt(redoHistory.lastIndex)
        strokes.clear()
        strokes.addAll(next)

        // 3) buffer & 화면 갱신
        redrawBitmapBuffer()
        invalidate()
    }

    /**
     * 두 저장을 한번에 실행하고, 토스트로 결과 알림
     */
    fun save(title: String) {
        val jsonFile = saveDataAsJson(title)
        val pngFile = saveViewAsPng(title)
        val msg = buildString {
            if (jsonFile != null) append("저장되었습니다.") else append("JSON 저장실패\n")
            if (pngFile != null) append("저장되었습니다.") else append("PNG 저장실패")
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        // 뷰 크기에 맞춘 빈 버퍼 비트맵 생성
        bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // 위 비트맵을 그릴 수 있는 캔버스 생성
        canvasBuffer = Canvas(bitmapBuffer)
    }

    /**
     * 뷰를 비트맵으로 캡처해 PNG로 저장합니다.
     * - 동일한 fileBaseName을 사용해 덮어씁니다.
     * @return 저장된 PNG 파일 객체, 실패 시 null
     */
    fun saveViewAsPng(diaryTitle: String): File? {
        // 1) 파일 기본 이름 결정 (JSON과 동일)
        val fileBaseName = entryName ?: run {
            val timeStampString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val sanitizedTitle = diaryTitle.trim()
                .replace(Regex("[^\\w가-힣_-]"), "_")
            "$timeStampString" + "_" + sanitizedTitle
        }

        // 2) 뷰 크기 체크
        val viewWidth = width.takeIf { it > 0 } ?: return null
        val viewHeight = height.takeIf { it > 0 } ?: return null

        // 3) 비트맵과 캔버스 생성
        val bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        val canvasForBitmap = Canvas(bitmap)

        // 4) 배경색 채우기
        val backgroundDrawable = background
        if (backgroundDrawable is ColorDrawable) {
            canvasForBitmap.drawColor(backgroundDrawable.color)
        } else {
            canvasForBitmap.drawColor(Color.WHITE)
        }

        // 5) 뷰 내용을 비트맵에 그리기
        draw(canvasForBitmap)

        // 6) 파일로 저장
        val pngFile = File(context.filesDir, "$fileBaseName.png")
        return try {
            FileOutputStream(pngFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            pngFile
        } catch (exception: IOException) {
            exception.printStackTrace()
            null
        }
    }

    /**
     * 획 데이터를 JSON으로 저장합니다.
     * - entryBaseName이 있으면 덮어쓰고, 없으면 새로 생성합니다.
     * @return 저장된 JSON 파일 객체, 실패 시 null
     */
    fun saveDataAsJson(diaryTitle: String): File? {
        // 1) 파일 기본 이름 결정
        val fileBaseName = entryName ?: run {
            val timeStampString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val sanitizedTitle = diaryTitle.trim()
                .replace(Regex("[^\\w가-힣_-]"), "_")
            "$timeStampString" + "_" + sanitizedTitle
        }

        // 2) 직렬화용 리스트 생성
        val strokeDataList = strokes.map { stroke ->
            StrokeData(
                points = stroke.points.toList(),
                strokeWidth = stroke.paint.strokeWidth,
                color = stroke.paint.color
            )
        }

        // 3) JSON 문자열 생성
        val jsonString = Gson().toJson(strokeDataList)

        // 4) 파일에 쓰기
        val jsonFile = File(context.filesDir, "$fileBaseName.json")
        return try {
            jsonFile.writeText(jsonString)
            jsonFile
        } catch (exception: IOException) {
            exception.printStackTrace()
            null
        }
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

    private val eraserRadius = 30f // 지우개 크기(픽셀 단위)

    private fun performVectorErase(atX: Float, atY: Float) {
        // 지울 획을 모아두고
        val strokesToRemove = strokes.filter { stroke ->
            stroke.points.any { pt ->
                // 거리 계산: 점과 터치 위치 사이가 반경 이내면
                hypot(pt.x - atX, pt.y - atY) <= eraserRadius
            }
        }
        // 삭제
        strokes.removeAll(strokesToRemove)

        // 2) 버퍼 전체를 지우고 남은 strokes만 다시 그리기
        redrawBitmapBuffer()

        invalidate()
    }

    private fun redrawBitmapBuffer() {
        // ① 배경 채우기
        (background as? ColorDrawable)?.let {
            canvasBuffer.drawColor(it.color)
        } ?: canvasBuffer.drawColor(Color.WHITE)

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

    private fun performAreaErase(atX: Float, atY: Float) {
        // 1) 지운 뒤 남길 획(Stroke)을 담을 새 리스트를 만든다
        val newStrokes = mutableListOf<Stroke>()

        // 2) 기존에 그려진 모든 획을 하나씩 검사
        for (stroke in strokes) {
            // 2-1) 각 포인트가 지우개 반경 내에 있는지 Boolean 리스트로 생성
            val flags = stroke.points.map { pt ->
                hypot(pt.x - atX, pt.y - atY) <= eraserRadius
            }

            // 2-2) 하나의 연속된 '남길 구간(점들)'을 모을 임시 리스트
            var segment = mutableListOf<PointF>()

            // 2-3) flags와 points를 인덱스로 같이 순회
            flags.forEachIndexed { idx, isInside ->
                if (!isInside) {
                    // - 반경 밖(지우지 않을 부분)이면 segment에 추가
                    segment.add(stroke.points[idx])
                } else {
                    // - 반경 안(지워질 부분)을 만나면
                    if (segment.size > 1) { // 점이 두개이상일 때만 그릴 수 있으므로
                        //   지금까지 모은 segment가 2개 이상 포인트면 새로운 Stroke로 저장
                        newStrokes += Stroke(segment.toMutableList(), Paint(stroke.paint))
                    }
                    //   segment를 비워서 다음 구간을 준비
                    segment = mutableListOf()
                }
            }

            // 2-4) 루프 종료 후 마지막으로 남은 segment 처리
            if (segment.size > 1) {
                // - 반경 밖 구간이 남아있다면 마찬가지로 새 Stroke로 추가
                newStrokes += Stroke(segment, Paint(stroke.paint))
            }
        }

        // 3) 원래 strokes를 지우고, 새로 만든 newStrokes로 교체
        strokes.clear()
        strokes.addAll(newStrokes)

        // 4) buffer 비트맵을 배경부터 다시 그리고(strokes만큼) 화면에 갱신
        redrawBitmapBuffer()
        invalidate()
    }


    companion object {
        private const val MAX_HISTORY = 50
    }
}