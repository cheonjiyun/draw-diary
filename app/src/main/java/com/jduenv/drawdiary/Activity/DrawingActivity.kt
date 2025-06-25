package com.jduenv.drawdiary.Activity

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jduenv.drawdiary.R
import com.jduenv.drawdiary.customDrawable.SeekbarThumbNumberDrawable
import com.jduenv.drawdiary.customView.StrokeData
import com.jduenv.drawdiary.customView.ToolMode
import com.jduenv.drawdiary.databinding.ActivityDrawingBinding
import com.jduenv.drawdiary.databinding.PopupEraserBinding
import com.jduenv.drawdiary.databinding.PopupPenBinding
import com.jduenv.drawdiary.viewmodel.DrawingViewModel
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import java.io.File


private const val TAG = "DrawingActivity_싸피"

class DrawingActivity : AppCompatActivity() {

    // viewmodel
    private val viewModel: DrawingViewModel by viewModels()

    // 펜 굵기 설정 xml
    private val popupPenBinding by lazy {
        PopupPenBinding.inflate(layoutInflater, LinearLayout(this), false)
    }

    // 지우개 모드 설정 xml
    private val popupEraserBinding by lazy {
        PopupEraserBinding.inflate(layoutInflater, LinearLayout(this), false)
    }

    private var entryName: String? = null

    private val customDrawable = SeekbarThumbNumberDrawable()

    val binding: ActivityDrawingBinding by lazy {
        ActivityDrawingBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initGetIntent()
        initViewModel()
        initSetSeekBarThumb()
        initEvent()
        initEventByPopupEraser()
        initObserve()

        // 리스너 등록
        binding.customDrawView.apply {
            setOnCanvasInitializedListener { width, height ->
                viewModel.initSnapshot(width, height)
            }
            setOnStrokeCompleteListener { sd ->
                val current = getCurrentBitmap() // 또는 getCurrentBitmap()
                viewModel.addStroke(sd, current)
            }
            // “지우기 시작”에만 snapshotForUndo 호출
            setOnSnapshotForUndo {
                viewModel.snapshot(getCurrentBitmap())
            }
            // 실제 지우기 동작은 기존 eraseVector / eraseArea
            setOnEraseListener { mode, x, y ->
                when (mode) {
                    ToolMode.ERASE_VECTOR -> viewModel.eraseVector(x, y, getCurrentBitmap())
                    ToolMode.ERASE_AREA -> viewModel.eraseArea(x, y, getCurrentBitmap())
                    else -> {}
                }
            }
            setOnFillCompleteListener { updatedBitmap ->
                viewModel.applyFilledBitmap(updatedBitmap)
            }
        }

        // 값이 바뀔 때
        popupPenBinding.seekBarPenStroke.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                viewModel.setStrokeWidth(progress)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        binding.customDrawView.post {
            intent.getStringExtra("ENTRY_NAME")?.let { name ->
                Log.d(TAG, "name: ${name}")
                viewModel.loadEntry(filesDir, name)
            }
        }
    }

    private fun initGetIntent() {
        // Intent 에서 ENTRY_NAME 받기
        entryName = intent.getStringExtra("ENTRY_NAME")
        entryName?.let {
            binding.customDrawView.setEntryName(it)
            loadEntry(it)    // stroke 데이터 로드
        }
    }

    private fun initViewModel() {
        // viewModel
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        binding.lifecycleOwner = this
        popupEraserBinding.viewModel = viewModel
    }


    private fun initSetSeekBarThumb() {
        popupPenBinding.seekBarPenStroke.thumb = customDrawable
    }


    /**
     * filesDir/ENTRY_NAME.json 에 저장된 획 데이터를 읽어서
     * customDrawView 에 복원합니다.
     */
    private fun loadEntry(entryName: String) {
        val jsonFile = File(filesDir, "$entryName.json")
        if (!jsonFile.exists()) return

        try {
            val json = jsonFile.readText()
            val type = object : TypeToken<List<StrokeData>>() {}.type
            val dataList: List<StrokeData> = Gson().fromJson(json, type)
            binding.customDrawView.setStrokesFromData(dataList)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "불러오기 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initEvent() {
        binding.pen.setOnClickListener {
            // ui
            // 두번 눌렀을 때만 팝업 윈도우 띄우기
            if (viewModel.currentMode.value == ToolMode.DRAW) {
                val popupPenWindow = PopupWindow(
                    popupPenBinding.root,
                    popupPenBinding.root.layoutParams.width,
                    popupPenBinding.root.layoutParams.height,
                    true
                )
                popupPenWindow.showAsDropDown(binding.pen)
            }

            // data
            viewModel.selectMode(ToolMode.DRAW)
        }

        popupPenBinding.btnMinus.setOnClickListener {
            viewModel.minusStrokeWidth()
        }

        popupPenBinding.btnPlus.setOnClickListener {
            viewModel.plusStrokeWidth()
        }

        binding.undo.setOnClickListener {
            viewModel.undo()
        }

        binding.redo.setOnClickListener {
            val current = binding.customDrawView.getCurrentBitmap()
            viewModel.redo(current)
        }


        binding.eraser.setOnClickListener {
            // ui
            if (viewModel.currentMode.value == ToolMode.ERASE_VECTOR || viewModel.currentMode.value == ToolMode.ERASE_AREA) {
                // 팝업 윈도우 띄우기
                val popupEraserWindow = PopupWindow(
                    popupEraserBinding.root,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
                )

                popupEraserWindow.showAsDropDown(binding.eraser)
            }

            // data: 가장 최근 지우개 모드에 따라 미리 설정
            viewModel.selectMode(viewModel.lastEraserMode)
        }

        binding.save.setOnClickListener {
            val fillBitmap = binding.customDrawView.getCurrentBitmap()
            val mergedBitmap = binding.customDrawView.getMergedBitmap()

            viewModel.saveAll(filesDir, entryName ?: "untitled", fillBitmap, mergedBitmap)
        }



        binding.pickColor.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setPreferenceName("색상선택")
                .setPositiveButton(
                    getString(R.string.confirm),
                    ColorEnvelopeListener { envelope, fromUser ->
                        viewModel.setCurrentColor(envelope.color)
                    })
                .setNegativeButton(
                    getString(R.string.cancel)
                ) { dialogInterface, i -> dialogInterface.dismiss() }
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(12)
                .show()
        }
        binding.fill.setOnClickListener {
            viewModel.selectMode(ToolMode.FILL)
        }
    }

    private fun initEventByPopupEraser() {
        popupEraserBinding.eraserLine.setOnClickListener {
            viewModel.selectMode(ToolMode.ERASE_VECTOR)
        }

        popupEraserBinding.eraserArea.setOnClickListener {
            viewModel.selectMode(ToolMode.ERASE_AREA)
        }
    }

    private fun initObserve() {
        // 모드
        viewModel.currentMode.observe(this) { mode ->
            // data
            binding.customDrawView.currentMode = mode

            // ui
            binding.pen.isSelected = mode == ToolMode.DRAW
            binding.eraser.isSelected = mode == ToolMode.ERASE_VECTOR || mode == ToolMode.ERASE_AREA
        }

        // 굵기
        viewModel.strokeWidth.observe(this) { stokeWidth ->
            binding.customDrawView.currentStroke = stokeWidth // data
            popupPenBinding.seekBarPenStroke.progress = stokeWidth // 바
            customDrawable.progress = stokeWidth // 동그라미안에 숫자
        }

        // 뒤로가기
        viewModel.canUndo.observe(this) { enabled ->
            Log.d(TAG, "initObserve: $enabled")
            binding.undo.isEnabled = enabled
        }

        viewModel.canRedo.observe(this) { enabled ->
            binding.redo.isEnabled = enabled
        }

        // 그림 정보 변경
        viewModel.currentSnapshot.observe(this) { snapshot ->
            binding.customDrawView.update(snapshot.fillBitmap, snapshot.strokes)
        }

        // 색
        viewModel.currentColor.observe(this) { color ->
            binding.customDrawView.currentColor = color
        }

        // 저장 끝
        viewModel.saveResult.observe(this) { success ->
            Toast.makeText(this, if (success) "저장 완료" else "저장 실패", Toast.LENGTH_LONG).show()
            if (success) finish()
        }
    }
}