package com.jduenv.drawdiary.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object FileNameUtils {
    fun createEntryFolderName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val shortId = UUID.randomUUID().toString().substring(0, 6)
        return "${timestamp}_$shortId"
    }
}
