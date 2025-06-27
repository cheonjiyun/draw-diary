package com.jduenv.drawdiary.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jduenv.drawdiary.data.DrawingInfo
import com.jduenv.drawdiary.repository.DrawingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ReadDiaryDetailViewModel : ViewModel() {

    private val repo = DrawingRepository()

    private var _bitmap = MutableLiveData<Bitmap>()
    val bitmap: LiveData<Bitmap> = _bitmap

    private var _entryName = MutableLiveData<String>()
    val entryName: LiveData<String> = _entryName

    private var _info = MutableLiveData<DrawingInfo>(DrawingInfo())
    val info: LiveData<DrawingInfo> = _info

    private val _deleteResult = MutableLiveData<Boolean>()
    val deleteResult: LiveData<Boolean> = _deleteResult

    private val _exportResult = MutableLiveData<Boolean>()
    val exportResult: LiveData<Boolean> = _exportResult


    fun setEntryName(newEntryName: String) {
        _entryName.value = newEntryName
    }

    fun loadImage(filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _bitmap.postValue(entryName.value?.let {
                repo.loadFinalBitmap(filesDir, it)
            })
        }
    }

    fun loadInfo(filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = entryName.value?.let { repo.loadInfo(filesDir, it) }

            _info.postValue(
                info
            )
        }
    }

    fun deleteEntry(baseDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = entryName.value?.let { repo.deleteDirectory(baseDir, it) }
            _deleteResult.postValue(success)
        }
    }

    fun exportImage(baseDir: File, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = entryName.value
            val info = _info.value

            val success = if (name != null && info != null) {
                repo.downLoad(context, baseDir, name, info) // 스마트 캐스트로 name, info는 non-null
            } else {
                null
            }

            _exportResult.postValue(success)
        }
    }


}