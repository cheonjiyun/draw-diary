package com.ssafy.drawdiary.Activity

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.ssafy.drawdiary.customDrawable.SeekbarThumbNumberDrawable
import com.ssafy.drawdiary.databinding.ActivityDrawingBinding
import com.ssafy.drawdiary.databinding.PopupPenBinding


private const val TAG = "DrawingActivity_싸피"

class DrawingActivity : AppCompatActivity() {

    // 펜 굵기 설정 xml
    private val popupPenBinding by lazy {
        PopupPenBinding.inflate(layoutInflater, LinearLayout(this), false)
    }

    private val customDrawable = SeekbarThumbNumberDrawable()

    val binding: ActivityDrawingBinding by lazy {
        ActivityDrawingBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)


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


        popupPenBinding.btnMinus.setOnClickListener {
            binding.customDrawView.currentStroke = binding.customDrawView.currentStroke - 1
            updateUI()
        }

        popupPenBinding.btnPlus.setOnClickListener {
            binding.customDrawView.currentStroke = binding.customDrawView.currentStroke + 1
            updateUI()
        }

        binding.pen.setOnClickListener {
            // 팝업 윈도우 띄우기
            val popupPenWindow = PopupWindow(
                popupPenBinding.root,
                popupPenBinding.root.layoutParams.width,
                popupPenBinding.root.layoutParams.height,
                true
            )
            popupPenWindow.showAsDropDown(binding.pen)
        }


        binding.undo.setOnClickListener {
            binding.customDrawView.undo()
            updateUI()
        }

        binding.redo.setOnClickListener {
            binding.customDrawView.redo()
            updateUI()
        }

    }

    fun updateUI() {
        popupPenBinding.seekBarPenStroke.progress =
            binding.customDrawView.currentStroke // 기존 굵기 반영

        customDrawable.progress = binding.customDrawView.currentStroke // 프로그레스바 숫자 반영

        // 뒤로가기
        binding.undo.isEnabled = binding.customDrawView.strokes.isNotEmpty()

        // 앞으로 가기
        binding.redo.isEnabled = binding.customDrawView.redoStack.isNotEmpty()
    }

}