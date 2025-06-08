package com.jduenv.drawdiary.CustomView

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
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

private const val TAG = "CustomDrawView_싸피"

// JSON 직렬화용 데이터 클래스
data class StrokeData(
    val points: List<PointF>,
    val strokeWidth: Float,
    val color: Int
)

class CustomDrawView(context: Context, attrs: AttributeSet?) :
    View(context, attrs) {
    // 내부적으로 Path 대신 포인트 리스트를 저장
    private data class Stroke(
        val points: MutableList<PointF>,
        val paint: Paint
    )

    private val strokes = mutableListOf<Stroke>() // 굵기를 바꾸었을 때 기존 굵기는 바뀌면 안되므로 선을 구분해서 list에 담음
    private val redoStack = mutableListOf<Stroke>()

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {

                // 1) 새 Stroke 생성
                val paint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = currentStrokeWidth.toFloat()
                    color = Color.BLACK   // 필요하면 변경 가능
                }
                val pts = mutableListOf(PointF(x, y))
                strokes.add(Stroke(pts, paint))
            }

            MotionEvent.ACTION_MOVE -> {
                // 2) 마지막 Stroke 의 points 에 추가
                strokes.lastOrNull()?.points?.add(PointF(x, y))
            }

            MotionEvent.ACTION_UP -> {

            }

        }
        invalidate()
        (context as DrawingActivity)?.updateUI()

        return true
    }

    // CustomDrawView.kt 에 추가
    val canUndo: Boolean
        get() = strokes.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

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
        invalidate()
    }
}