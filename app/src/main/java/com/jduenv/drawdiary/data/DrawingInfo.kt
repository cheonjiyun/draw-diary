package com.jduenv.drawdiary.data

import com.jduenv.drawdiary.util.DateUtil

data class DrawingInfo(
    var date: String,
    var title: String,
    var content: String
) {
    constructor() : this(DEFAULT_DATE, "", "")

    companion object {
        val DEFAULT_DATE = DateUtil.getTodayFormatted()
    }
}