package com.jduenv.drawdiary.Activity

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jduenv.drawdiary.customDrawable.SeekbarThumbNumberDrawable
import com.jduenv.drawdiary.customView.StrokeData
import com.jduenv.drawdiary.customView.ToolMode
import com.jduenv.drawdiary.databinding.ActivityDrawingBinding
import com.jduenv.drawdiary.databinding.PopupEraserBinding
import com.jduenv.drawdiary.databinding.PopupPenBinding
import java.io.File


private const val TAG = "DrawingActivity_싸피"

class DrawingActivity : AppCompatActivity() {

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

        // Intent 에서 ENTRY_NAME 받기
        entryName = intent.getStringExtra("ENTRY_NAME")
        entryName?.let {
            binding.customDrawView.setEntryName(it)
            loadEntry(it)    // stroke 데이터 로드
        }

        popupPenBinding.seekBarPenStroke.thumb = customDrawable

        // 펜 굵기 data -> seekbar view에 반영
        updateUI()

        // 값이 바뀔 때
        popupPenBinding.seekBarPenStroke.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                binding.customDrawView.currentStroke = progress // 펜 굵기 바꾸기
                updateUI()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })


        // 초기설정
        binding.currentMode = binding.customDrawView.currentMode

        binding.pen.setOnClickListener {
            // ui
            binding.pen.isSelected = true
            binding.eraser.isSelected = false
            if (binding.customDrawView.currentMode == ToolMode.DRAW) {
                // 팝업 윈도우 띄우기
                val popupPenWindow = PopupWindow(
                    popupPenBinding.root,
                    popupPenBinding.root.layoutParams.width,
                    popupPenBinding.root.layoutParams.height,
                    true
                )
                popupPenWindow.showAsDropDown(binding.pen)
            }

            // data
            binding.customDrawView.currentMode = ToolMode.DRAW
            binding.currentMode = binding.customDrawView.currentMode
        }

        binding.undo.setOnClickListener {
            binding.customDrawView.undo()
            updateUI()
        }

        binding.redo.setOnClickListener {
            binding.customDrawView.redo()
            updateUI()
        }

        binding.eraser.setOnClickListener {
            // ui
            if (binding.customDrawView.currentMode == ToolMode.ERASE_VECTOR || binding.customDrawView.currentMode == ToolMode.ERASE_AREA) {
                // 팝업 윈도우 띄우기
                val popupEraserWindow = PopupWindow(
                    popupEraserBinding.root,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
                )

                popupEraserWindow.showAsDropDown(binding.eraser)
            }

            // data
            if (binding.customDrawView.lastEraserMode == ToolMode.ERASE_VECTOR) {
                binding.customDrawView.currentMode = ToolMode.ERASE_VECTOR
                binding.currentMode = binding.customDrawView.currentMode
                popupEraserBinding.currentMode = binding.customDrawView.currentMode
            } else if (binding.customDrawView.lastEraserMode == ToolMode.ERASE_AREA) {
                binding.customDrawView.currentMode = ToolMode.ERASE_AREA
                binding.currentMode = binding.customDrawView.currentMode
                popupEraserBinding.currentMode = binding.customDrawView.currentMode
            }

        }

        popupEraserBinding.eraserLine.setOnClickListener {
            binding.customDrawView.currentMode = ToolMode.ERASE_VECTOR
            binding.customDrawView.lastEraserMode = ToolMode.ERASE_VECTOR
        }

        popupEraserBinding.eraserArea.setOnClickListener {
            binding.customDrawView.currentMode = ToolMode.ERASE_AREA
            binding.customDrawView.lastEraserMode = ToolMode.ERASE_AREA
        }

        binding.save.setOnClickListener {
            val title = entryName ?: "임시 제목"
            binding.customDrawView.save(title)
            finish()
        }

        initEvent()
    }

    fun updateUI() {
        popupPenBinding.seekBarPenStroke.progress =
            binding.customDrawView.currentStroke // 기존 굵기 반영

        customDrawable.progress = binding.customDrawView.currentStroke // 프로그레스바 숫자 반영

        // 뒤로가기 버튼 활성화 여부
        binding.undo.isEnabled = binding.customDrawView.canUndo

        // 앞으로가기 버튼 활성화 여부
        binding.redo.isEnabled = binding.customDrawView.canRedo
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

    fun initEvent() {
        popupPenBinding.btnMinus.setOnClickListener {
            binding.customDrawView.currentStroke = binding.customDrawView.currentStroke - 1
            updateUI()
        }

        popupPenBinding.btnPlus.setOnClickListener {
            binding.customDrawView.currentStroke = binding.customDrawView.currentStroke + 1
            updateUI()
        }
    }
}