package com.jduenv.drawdiary.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

}