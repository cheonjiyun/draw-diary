package com.jduenv.drawdiary

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jduenv.drawdiary.Activity.DrawingActivity
import com.jduenv.drawdiary.Activity.ReadDiaryDetailActivity
import com.jduenv.drawdiary.Adapter.ThumbAdapter
import com.jduenv.drawdiary.databinding.ActivityMainBinding
import com.jduenv.drawdiary.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val thumbAdapter by lazy {
        ThumbAdapter(mutableListOf()) { thumb ->
            // 클릭된 썸네일의 파일명(확장자 없이)
            val entryName = thumb.entryName

            // DrawingActivity 로 이동, ENTRY_NAME 전달
            startActivity(
                Intent(this@MainActivity, ReadDiaryDetailActivity::class.java)
                    .putExtra("ENTRY_NAME", entryName)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Recyclerview
        binding.recyclerView.adapter = thumbAdapter

        initEvent()
        initObserve()
    }


    fun initEvent() {
        // + 버튼
        binding.floatingActionButton.setOnClickListener {
            val intent = Intent(this, DrawingActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initObserve() {
        viewModel.thumbnails.observe(this) { thumbs ->
            thumbAdapter.updateData(thumbs)
        }
    }

    override fun onResume() {
        super.onResume()
        // 포그라운드로 돌아올 때마다 갱신
        viewModel.loadThumbnails(filesDir)
    }

    // 마지막으로 뒤로 가기 버튼을 눌렀던 시간(밀리초)
    private var lastBackPressedTime = 0L

    override fun onBackPressed() {
        val currentTime = System.currentTimeMillis()
        // 2초(2000ms) 이내에 다시 눌렀으면
        if (currentTime - lastBackPressedTime < 2000) {
            super.onBackPressed()  // 앱 종료
        } else {
            lastBackPressedTime = currentTime
            Toast.makeText(
                this,
                "뒤로 버튼을 한 번 더 누르면 앱이 종료됩니다",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}