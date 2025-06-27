package com.jduenv.drawdiary.Activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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


        initBinind()
        initGetIntent()
        initEvent()
        initViewModel()
        initObserve()
    }

    private fun initBinind() {
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
    }


    private fun initGetIntent() {
        // Intent 에서 ENTRY_NAME 받기
        intent.getStringExtra("ENTRY_NAME")?.let {
            viewModel.setEntryName(it)
        }
    }

    private fun initViewModel() {
        viewModel.loadImage(filesDir)
        viewModel.loadInfo(filesDir)
    }


    private fun initEvent() {
        val popup = PopupMenu(this, binding.more)
        popup.menuInflater.inflate(R.menu.menu_read_diary_detail_options, popup.menu)

        val deleteItem = popup.menu.findItem(R.id.menu_delete)
        val s = SpannableString(deleteItem.title)
        s.setSpan(ForegroundColorSpan(Color.RED), 0, s.length, 0)
        deleteItem.title = s

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_download -> {
                    download()
                    true
                }

                R.id.menu_edit -> {
                    modify()
                    true
                }

                R.id.menu_delete -> {
                    delete()
                    true
                }

                else -> false
            }
        }
        binding.more.setOnClickListener {
            popup.show()
        }
    }

    private fun download() {

    }

    private fun modify() {
        startActivity(
            Intent(this@ReadDiaryDetailActivity, DrawingActivity::class.java)
                .putExtra("ENTRY_NAME", viewModel.entryName.value)
        )
    }

    private fun delete() {
        AlertDialog.Builder(this)
            .setTitle("삭제")
            .setMessage("정말 이 그림을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteEntry(filesDir)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun initObserve() {
        viewModel.bitmap.observe(this) { bitmap ->
            binding.imageView.setImageBitmap(bitmap)
        }

        viewModel.deleteResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                finish() // 또는 리스트 갱신
            } else {
                Toast.makeText(this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.loadImage(filesDir)
        viewModel.loadInfo(filesDir)
    }


}