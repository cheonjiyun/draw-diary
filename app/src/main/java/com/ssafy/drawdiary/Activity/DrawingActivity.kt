package com.ssafy.drawdiary.Activity

import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.ssafy.drawdiary.databinding.ActivityDrawingBinding
import com.ssafy.drawdiary.databinding.PopupPenBinding


private const val TAG = "DrawingActivity_싸피"

class DrawingActivity : AppCompatActivity() {

    val binding: ActivityDrawingBinding by lazy {
        ActivityDrawingBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        // 펜 굵기 설정 xml
        val popupPenBinding = PopupPenBinding.inflate(layoutInflater, LinearLayout(this), false)

        // 펜 굵기 data -> seekbar view에 반영
        fun settingSeekBarByStoke() {
            popupPenBinding.seekBarPenStroke.progress =
                binding.customDrawView.currentStroke // 기존 굵기 반영
        }

        settingSeekBarByStoke()

        // 값이 바뀔 때
        popupPenBinding.seekBarPenStroke.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                binding.customDrawView.currentStroke = progress // 펜 굵기 바꾸기
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        popupPenBinding.btnMinus.setOnClickListener {
            binding.customDrawView.currentStroke = binding.customDrawView.currentStroke - 1
            settingSeekBarByStoke()
            Log.d(TAG, "onCreate: ${binding.customDrawView.currentStroke}")
        }

        popupPenBinding.btnPlus.setOnClickListener {
            binding.customDrawView.currentStroke = binding.customDrawView.currentStroke + 1
            settingSeekBarByStoke()
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
    }


}