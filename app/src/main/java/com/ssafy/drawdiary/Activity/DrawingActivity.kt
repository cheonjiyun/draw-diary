package com.ssafy.drawdiary.Activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ssafy.drawdiary.R
import com.ssafy.drawdiary.databinding.ActivityDrawingBinding

class DrawingActivity : AppCompatActivity() {

    val binding : ActivityDrawingBinding by lazy {
        ActivityDrawingBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_drawing)


    }
}