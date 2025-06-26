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
import com.jduenv.drawdiary.data.DrawingInfo
import com.jduenv.drawdiary.data.DrawingSnapshot
import com.jduenv.drawdiary.repository.DrawingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.hypot

private const val TAG = "DrawingViewModel"

class DrawingViewModel(
) : ViewModel() {

    private val repo = DrawingRepository()

    private val _currentSnapshot = MutableLiveData<DrawingSnapshot>()
    val currentSnapshot: LiveData<DrawingSnapshot> = _currentSnapshot

    private val _currentInfo = MutableLiveData<DrawingInfo>(DrawingInfo())
    val currentInfo: LiveData<DrawingInfo> = _currentInfo

    private val undoStack = ArrayDeque<DrawingSnapshot>()
    private val redoStack = ArrayDeque<DrawingSnapshot>()

    private val _strokeWidth = MutableLiveData(10)
    val strokeWidth: LiveData<Int> = _strokeWidth

    private val _currentColor = MutableLiveData(Color.BLACK)
    val currentColor: LiveData<Int> = _currentColor

    private val _currentMode = MutableLiveData<ToolMode>(ToolMode.DRAW)
    val currentMode: LiveData<ToolMode> = _currentMode

    private var _lastEraserMode = ToolMode.ERASE_VECTOR
    val lastEraserMode = _lastEraserMode

    private val eraserRadius = 30f

    val canUndo: LiveData<Boolean> = currentSnapshot.map { undoStack.isNotEmpty() }
    val canRedo: LiveData<Boolean> = currentSnapshot.map { redoStack.isNotEmpty() }

    fun initSnapshot(width: Int, height: Int) {
        if (_currentSnapshot.value == null) {
            _currentSnapshot.value = DrawingSnapshot(
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
                emptyList()
            )
            Log.d(TAG, "initSnapshot: 초기 스냅샷 생성 ($width x $height)")
        }
    }

    fun setInfoDate(newDate: String) {
        val current = currentInfo.value ?: DrawingInfo()
        _currentInfo.value = current.copy(date = newDate)
    }


    fun setCurrentColor(newColor: Int) {
        _currentColor.value = newColor
    }

    /** JSON → LiveData 로 초기 로드 */
    fun loadEntry(filesDir: File, entryName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 그림
            val strokes = repo.loadStrokes(filesDir, entryName) ?: emptyList()
            val fillBitmap = repo.loadFillBitmap(filesDir, entryName)

            _currentSnapshot.postValue(
                DrawingSnapshot(
                    fillBitmap = fillBitmap,
                    strokes = strokes
                )
            )

            // 날짜, 글 등 이외 정보
            val info = entryName.let { repo.loadInfo(filesDir, it) }
            _currentInfo.postValue(info)
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

    fun snapshot(currentBitmap: Bitmap) {
        _currentSnapshot.value?.let { current ->
            // 비트맵 깊은 복사
            val bitmapCopy = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
            // strokes는 immutable이라 복사 안 해도 됨 (불변 객체로 간주)
            val snapshot = DrawingSnapshot(bitmapCopy, current.strokes)

            undoStack.addLast(snapshot)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()

            redoStack.clear()
        }
    }

    fun undo() {
        Log.d(TAG, "undo.size: ${undoStack.size}")
        if (undoStack.isEmpty()) return
        Log.d(TAG, "undo: ")
        redoStack.addLast(_currentSnapshot.value!!) // 현재 상태 push
        val snapshot = undoStack.removeLast()
        _currentSnapshot.value = snapshot
    }

    fun redo(currentBitmap: Bitmap) {
        if (redoStack.isEmpty()) return

        _currentSnapshot.value?.let { current ->
            // 현재 상태 → undo 스택에 저장
            val bitmapCopy = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val snapshot = DrawingSnapshot(bitmapCopy, current.strokes)
            undoStack.addLast(snapshot)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        }

        // 복원할 상태
        val next = redoStack.removeLast()
        _currentSnapshot.value = next
    }


    /**
     * 새로운 그리기 획을 추가합니다.
     */
    fun addStroke(stroke: StrokeData, currentBitmap: Bitmap) {
        Log.d(TAG, "snapshotForUndo: ${_currentSnapshot.value == null}")

        val current = _currentSnapshot.value
        if (current != null) {
            val newStrokes = current.strokes + stroke
            val newSnapshot =
                DrawingSnapshot(currentBitmap.copy(Bitmap.Config.ARGB_8888, true), newStrokes)
            _currentSnapshot.value = newSnapshot
        }

        redoStack.clear()
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
    fun saveAll(
        filesDir: File,
        entryName: String,
        fillBitmap: Bitmap,
        mergedBitmap: Bitmap,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val strokes = _currentSnapshot.value?.strokes ?: emptyList()

            val okJson = repo.saveStrokes(filesDir, entryName, strokes)
            val okFill = repo.saveImage(filesDir, "${entryName}_fill", fillBitmap)
            val okMerged = repo.saveImage(filesDir, "${entryName}_final", mergedBitmap)
            val okText = currentInfo.value?.let { repo.saveText(filesDir, "${entryName}_info", it) }

            _saveResult.postValue(okJson && okFill && okMerged && okText == true)
        }
    }


    /** 벡터 지우기: performVectorErase와 동일하게 동작 */
    fun eraseVector(x: Float, y: Float, currentBitmap: Bitmap) {
        val current = _currentSnapshot.value ?: return

        Log.d(TAG, "지우기 전 strokes: ${current.strokes.size}")
        val nextStrokes = current.strokes.filter { sd ->
            sd.points.none { pt -> hypot(pt.x - x, pt.y - y) <= eraserRadius }
        }

        _currentSnapshot.value =
            DrawingSnapshot(currentBitmap.copy(Bitmap.Config.ARGB_8888, true), nextStrokes)
        Log.d(TAG, "지우고 남은 strokes: ${nextStrokes.size}")

    }


    /** 영역 지우기 */
    fun eraseArea(x: Float, y: Float, currentBitmap: Bitmap) {
        val current = _currentSnapshot.value ?: return
        val newList = mutableListOf<StrokeData>()

        current.strokes.forEach { sd ->
            val pts = sd.points
            val flags = pts.map { pt -> hypot(pt.x - x, pt.y - y) <= eraserRadius }

            var segment = mutableListOf<PointF>()
            for ((i, inside) in flags.withIndex()) {
                if (!inside) {
                    segment.add(pts[i])
                } else {
                    if (segment.size > 1) {
                        newList += StrokeData(segment.toList(), sd.strokeWidth, sd.color)
                    }
                    segment = mutableListOf()
                }
            }

            if (segment.size > 1) {
                newList += StrokeData(segment.toList(), sd.strokeWidth, sd.color)
            }
        }

        _currentSnapshot.value =
            DrawingSnapshot(currentBitmap.copy(Bitmap.Config.ARGB_8888, true), newList)
    }

    fun applyFilledBitmap(filledBitmap: Bitmap) {
        Log.d(TAG, "applyFilledBitmap: $filledBitmap")
        val current = _currentSnapshot.value ?: return
        val newSnapshot = DrawingSnapshot(filledBitmap, current.strokes)
        _currentSnapshot.value = newSnapshot
    }

    companion object {
        private const val MAX_HISTORY = 50
    }

}