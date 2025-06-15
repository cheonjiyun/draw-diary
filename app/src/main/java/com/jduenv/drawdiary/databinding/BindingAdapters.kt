package com.jduenv.drawdiary.databinding

import android.view.View
import androidx.databinding.BindingAdapter
import com.jduenv.drawdiary.customView.ToolMode

object BindingAdapters {
    /**
     * modeValue: 이 View 가 나타내는 모드
     * currentMode: 지금 선택된 전체 모드
     * 두 값이 같으면 View.isSelected = true
     */
    @JvmStatic
    @BindingAdapter("modeValue", "currentMode")
    fun setSelectedByMode(view: View, modeValue: ToolMode?, currentMode: ToolMode?) {
        view.isSelected = (modeValue != null && modeValue == currentMode)
    }

    /** 복수 모드용: List 중 하나라도 currentMode 와 같으면 selected=true */
    @JvmStatic
    @BindingAdapter("modeValues", "currentMode")
    fun setSelectedByModes(
        view: View,
        modeValues: List<ToolMode>?,
        currentMode: ToolMode?
    ) {
        view.isSelected = modeValues?.any { it == currentMode } == true
    }
}