package com.jduenv.drawdiary.Activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jduenv.drawdiary.R
import com.jduenv.drawdiary.databinding.ActivityReadDiaryDetailBinding
import com.jduenv.drawdiary.viewmodel.ReadDiaryDetailViewModel

private const val TAG = "ReadDiaryDetailActivity"

class ReadDiaryDetailActivity : AppCompatActivity() {

    private val viewModel: ReadDiaryDetailViewModel by viewModels()
    private val binding by lazy {
        ActivityReadDiaryDetailBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initGetIntent()
        initEvent()
        initViewModel()
        initObserve()
    }


    private fun initGetIntent() {
        // Intent 에서 ENTRY_NAME 받기
        intent.getStringExtra("ENTRY_NAME")?.let {
            viewModel.setEntryName(it)
        }

    }

    private fun initViewModel() {
        viewModel.loadImage(filesDir)
    }

    private fun initEvent() {
        binding.modify.setOnClickListener {
            // DrawingActivity 로 이동, ENTRY_NAME 전달
            startActivity(
                Intent(this@ReadDiaryDetailActivity, DrawingActivity::class.java)
                    .putExtra("ENTRY_NAME", viewModel.entryName.value)
            )
        }
    }

    private fun initObserve() {
        viewModel.bitmap.observe(this) { bitmap ->
            binding.imageView.setImageBitmap(bitmap)
        }
    }

    override fun onResume() {
        super.onResume()
        
        viewModel.loadImage(filesDir)
    }


}