package com.jduenv.drawdiary

import android.content.Intent
import android.os.Bundle
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
        ThumbAdapter(R.layout.list_item, mutableListOf()) { thumb ->
            // 클릭된 썸네일의 파일명(확장자 없이)
            val entryName = File(thumb.path).nameWithoutExtension
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
        return dir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.map { file ->
                // 파일명: yyyyMMdd_HHmmss_제목.png
                val name = file.nameWithoutExtension
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

}