package com.ssafy.drawdiary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.helper.widget.Grid
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.ssafy.drawdiary.Adapter.ThumbAdapter
import com.ssafy.drawdiary.data.DrawThumb
import com.ssafy.drawdiary.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        val thumbs = listOf(
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
            DrawThumb("이미지", "제목", java.util.Date()),
        )
        val thumbAdapter = ThumbAdapter(R.layout.list_item, thumbs)


        binding.recyclerView.adapter = thumbAdapter


    }
}