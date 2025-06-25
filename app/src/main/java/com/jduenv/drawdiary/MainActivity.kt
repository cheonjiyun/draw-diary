package com.jduenv.drawdiary

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jduenv.drawdiary.Activity.DrawingActivity
import com.jduenv.drawdiary.Adapter.ThumbAdapter
import com.jduenv.drawdiary.data.DrawThumb
import com.jduenv.drawdiary.databinding.ActivityMainBinding
import java.io.File
import java.util.Date

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val thumbAdapter by lazy {
        ThumbAdapter(mutableListOf()) { thumb ->
            // 클릭된 썸네일의 파일명(확장자 없이)
            val entryName = File(thumb.path).nameWithoutExtension
                .removeSuffix("_final")

            // DrawingActivity 로 이동, ENTRY_NAME 전달
            startActivity(
                Intent(this@MainActivity, DrawingActivity::class.java)
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
    }

    fun initEvent() {
        // + 버튼
        binding.floatingActionButton.setOnClickListener {
            val intent = Intent(this, DrawingActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadThumbnails(): List<DrawThumb> {
        val dir = filesDir
        return dir.listFiles { f -> f.name.endsWith("_final.png", ignoreCase = true) }
            ?.map { file ->
                // 파일명: yyyyMMdd_HHmmss_제목_final.png
                val name = file.name.removeSuffix("_final.png")
                val parts = name.split("_", limit = 2)
                val title = parts.getOrNull(1)?.replace('_', ' ') ?: name
                val date = Date(file.lastModified())
                DrawThumb(path = file.absolutePath, title = title, date = date)
            } ?: emptyList()
    }

    override fun onResume() {
        super.onResume()
        // 포그라운드로 돌아올 때마다 갱신
        val thumbs = loadThumbnails()
        thumbAdapter.updateData(thumbs)
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