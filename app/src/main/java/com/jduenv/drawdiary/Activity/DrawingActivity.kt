package com.jduenv.drawdiary.Activity

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.jduenv.drawdiary.R
import com.jduenv.drawdiary.customDrawable.SeekbarThumbNumberDrawable
import com.jduenv.drawdiary.customView.ToolMode
import com.jduenv.drawdiary.databinding.ActivityDrawingBinding
import com.jduenv.drawdiary.databinding.PopupEraserBinding
import com.jduenv.drawdiary.databinding.PopupPenBinding
import com.jduenv.drawdiary.util.DateUtil.formatDate
import com.jduenv.drawdiary.viewmodel.DrawingViewModel
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import java.util.Calendar


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
                viewModel.loadEntry(filesDir, name)
            }
        }
    }

    private fun initGetIntent() {
        // Intent 에서 ENTRY_NAME 받기
        entryName = intent.getStringExtra("ENTRY_NAME")
        entryName?.let {
            binding.customDrawView.setEntryName(it)
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


    private fun initEvent() {
        binding.save.setOnClickListener {
            val fillBitmap = binding.customDrawView.getCurrentBitmap()
            val mergedBitmap = binding.customDrawView.getMergedBitmap()

            viewModel.saveAll(filesDir, entryName ?: "untitled", fillBitmap, mergedBitmap)
        }

        binding.date.setOnClickListener {
            showDatePickerDialog()
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


        binding.fill.setOnClickListener {
            viewModel.selectMode(ToolMode.FILL)
        }

        binding.undo.setOnClickListener {
            viewModel.undo()
        }

        binding.redo.setOnClickListener {
            val current = binding.customDrawView.getCurrentBitmap()
            viewModel.redo(current)
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

        // 색
        viewModel.currentColor.observe(this) { color ->
            binding.customDrawView.currentColor = color
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
        // 앞으로가기
        viewModel.canRedo.observe(this) { enabled ->
            binding.redo.isEnabled = enabled
        }

        // 그림 정보 변경
        viewModel.currentSnapshot.observe(this) { snapshot ->
            binding.customDrawView.update(snapshot.fillBitmap, snapshot.strokes)
        }


        // 저장 끝
        viewModel.saveResult.observe(this) { success ->
            Toast.makeText(this, if (success) "저장 완료" else "저장 실패", Toast.LENGTH_LONG).show()
            if (success) finish()
        }
    }


    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                // 선택된 날짜 처리
                val selectedDate = formatDate(selectedYear, selectedMonth + 1, selectedDayOfMonth)
                viewModel.setInfoDate(selectedDate)
            },
            year, month, day
        )

        datePickerDialog.show()
    }

}