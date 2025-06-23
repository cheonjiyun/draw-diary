package com.jduenv.drawdiary.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.jduenv.drawdiary.customView.StrokeData
import com.jduenv.drawdiary.customView.ToolMode
import com.jduenv.drawdiary.repository.DrawingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.hypot

private const val TAG = "DrawingViewModel"

class DrawingViewModel(
) : ViewModel() {

    private val repo = DrawingRepository()

    private val _strokes = MutableLiveData<List<StrokeData>>(emptyList())
    val strokes: LiveData<List<StrokeData>> = _strokes

    private val undoStack = ArrayDeque<List<StrokeData>>()
    private val redoStack = ArrayDeque<List<StrokeData>>()

    private val _strokeWidth = MutableLiveData(10)
    val strokeWidth: LiveData<Int> = _strokeWidth

    private val _currentColor = MutableLiveData(Color.BLACK)
    val currentColor: LiveData<Int> = _currentColor

    private val _currentMode = MutableLiveData<ToolMode>(ToolMode.DRAW)
    val currentMode: LiveData<ToolMode> = _currentMode

    private var _lastEraserMode = ToolMode.ERASE_VECTOR
    val lastEraserMode = _lastEraserMode

    val canUndo: LiveData<Boolean> = _strokes.map { undoStack.isNotEmpty() }
    val canRedo: LiveData<Boolean> = _strokes.map { redoStack.isNotEmpty() }


    fun setCurrentColor(newColor: Int) {
        _currentColor.value = newColor
    }

    /** JSON → LiveData 로 초기 로드 */
    fun loadEntry(filesDir: File, entryName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repo.loadStrokes(filesDir, entryName) ?: emptyList()
            _strokes.postValue(list)
        }
    }


    /**
     * 그리기 모드를 변경합니다.
     * MutableLiveData의 value를 업데이트하면 LiveData 관찰자에게 알림이 전달됩니다.
     */
    fun selectMode(newMode: ToolMode) {
        _currentMode.value = newMode

        if (newMode == ToolMode.ERASE_VECTOR || newMode == ToolMode.ERASE_AREA) {
            _lastEraserMode = newMode
        }
    }

    fun setStrokeWidth(width: Int) {
        _strokeWidth.value = width.coerceIn(1, 100)
    }

    fun minusStrokeWidth() {
        // _strokeWidth.value가 null인 경우(드물지만) 기본값 10으로
        setStrokeWidth((_strokeWidth.value ?: 10) - 1)
    }

    fun plusStrokeWidth() {
        setStrokeWidth((_strokeWidth.value ?: 10) + 1)
    }


    fun undo() {
        Log.d(TAG, "undo: ${undoStack}")
        if (undoStack.isEmpty()) return
        // ① 마지막 요소 꺼내기
        val prev = undoStack.removeLast()
        // ② 현재 상태를 redoStack에 저장
        redoStack.addLast(_strokes.value ?: emptyList())
        if (redoStack.size > MAX_HISTORY) redoStack.removeFirst()
        // ③ LiveData 업데이트
        _strokes.value = prev
    }

    fun redo() {

        Log.d(TAG, "redo: $redoStack")
        if (redoStack.isEmpty()) return
        // ① 마지막 요소 꺼내기
        val next = redoStack.removeLast()
        // ② 현재 상태를 undoStack에 저장
        undoStack.addLast(_strokes.value ?: emptyList())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        // ③ LiveData 업데이트
        _strokes.value = next
    }

    /**
     * 새로운 그리기 획을 추가합니다.
     */
    fun addStroke(stroke: StrokeData) {
        // 1) undo 스택에 현재 상태 저장
        undoStack.addLast(_strokes.value ?: emptyList())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()

        // 2) redo 스택 초기화
        redoStack.clear()

        // 3) 획 목록 업데이트
        _strokes.value = (_strokes.value ?: emptyList()) + stroke
    }

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> = _saveResult

    /**
     * JSON + PNG 저장
     *
     * @param filesDir  Activity.filesDir
     * @param entryName 파일명(확장자 제외)
     * @param bitmap    CustomDrawView.captureBitmap() 으로 받은 비트맵
     */
    fun saveAll(filesDir: File, entryName: String, bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val okJson = repo.saveStrokes(filesDir, entryName, _strokes.value ?: emptyList())
            val okPng = repo.saveImage(filesDir, entryName, bitmap)
            _saveResult.postValue(okJson && okPng)
        }
    }

    private val eraserRadius = 30f

    private fun snapshotForUndo() {
        undoStack.addLast(_strokes.value ?: emptyList())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()

        Log.d(TAG, "snapshotForUndo: $undoStack")
    }

    fun beginErase() {
        snapshotForUndo()
    }

    /** 벡터 지우기: performVectorErase와 동일하게 동작 */
    fun eraseVector(x: Float, y: Float) {
        val next = (_strokes.value ?: emptyList()).filter { sd ->
            sd.points.none { pt -> hypot(pt.x - x, pt.y - y) <= eraserRadius }
        }

        _strokes.value = next
    }

    /** 영역 지우기 */
    fun eraseArea(x: Float, y: Float) {
        val newList = mutableListOf<StrokeData>()

        (_strokes.value ?: emptyList()).forEach { sd ->
            val pts = sd.points
            val flags = pts.map { pt -> hypot(pt.x - x, pt.y - y) <= eraserRadius }

            var segment = mutableListOf<PointF>()
            for ((i, inside) in flags.withIndex()) {
                if (!inside) {
                    // 지우개 반경 밖: 남길 부분
                    segment.add(pts[i])
                } else {
                    // 지우개 반경 안: 기존 segment가 2점 이상이면 저장
                    if (segment.size > 1) {
                        newList += StrokeData(
                            points = segment.toList(),
                            strokeWidth = sd.strokeWidth,
                            color = sd.color
                        )
                    }
                    segment = mutableListOf()
                }
            }

            if (segment.size > 1) {
                newList += StrokeData(
                    points = segment.toList(),
                    strokeWidth = sd.strokeWidth,
                    color = sd.color
                )
            }
        }

        _strokes.value = newList
    }

    companion object {
        private const val MAX_HISTORY = 50
    }

}