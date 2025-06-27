package com.jduenv.drawdiary.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtil {

    fun formatDate(year: Int, month: Int, day: Int): String {
        return "${year}년 ${month}월 ${day}일"
    }

    fun getTodayFormatted(): String {
        val today = Date()
        val formatter = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)
        return formatter.format(today)
    }
    
    fun parseTimestampFromEntryName(entryName: String): Long {
        return try {
            val dateString = entryName.substring(0, 15) // "yyyyMMdd_HHmmss"
            val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            format.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

}
