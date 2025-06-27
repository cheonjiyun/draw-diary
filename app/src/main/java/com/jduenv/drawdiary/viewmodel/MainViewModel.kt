package com.jduenv.drawdiary.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jduenv.drawdiary.data.DrawThumb
import com.jduenv.drawdiary.repository.DrawingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {

    val repository = DrawingRepository()

    private val _thumbnails = MutableLiveData<List<DrawThumb>>()
    val thumbnails: LiveData<List<DrawThumb>> get() = _thumbnails

    fun loadThumbnails(filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val thumbs = repository.loadAllFinalThumbs(filesDir)
            _thumbnails.postValue(thumbs)
        }
    }
}