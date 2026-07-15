package com.lighttodo.android.utils

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.ZoneId

object LunarUtils {
    private val chineseLocale = ULocale("zh_CN@calendar=chinese")

    fun getLunarInfo(date: LocalDate): String {
        val chineseCalendar = ChineseCalendar(chineseLocale).apply {
            timeInMillis = date
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        val lunarMonth = chineseCalendar.get(Calendar.MONTH) + 1
        val lunarDay = chineseCalendar.get(Calendar.DAY_OF_MONTH)
        val isLeapMonth = chineseCalendar.get(Calendar.IS_LEAP_MONTH) == 1

        val lunarText = buildString {
            if (isLeapMonth) append("闰")
            append(monthName(lunarMonth))
            append(dayName(lunarDay))
        }

        val festival = getFestival(date, lunarMonth, lunarDay, isLeapMonth)
        return if (festival != null) "$lunarText · $festival" else lunarText
    }

    private fun getFestival(
        date: LocalDate,
        lunarMonth: Int,
        lunarDay: Int,
        isLeapMonth: Boolean,
    ): String? {
        val solarFestival = solarFestivals[date.monthValue to date.dayOfMonth]
        if (solarFestival != null) return solarFestival

        if (isLeapMonth) {
            return null
        }

        return lunarFestivals[lunarMonth to lunarDay]
    }

    private fun monthName(month: Int): String = when (month) {
        1 -> "正月"
        2 -> "二月"
        3 -> "三月"
        4 -> "四月"
        5 -> "五月"
        6 -> "六月"
        7 -> "七月"
        8 -> "八月"
        9 -> "九月"
        10 -> "十月"
        11 -> "冬月"
        12 -> "腊月"
        else -> ""
    }

    private fun dayName(day: Int): String = when (day) {
        1 -> "初一"
        2 -> "初二"
        3 -> "初三"
        4 -> "初四"
        5 -> "初五"
        6 -> "初六"
        7 -> "初七"
        8 -> "初八"
        9 -> "初九"
        10 -> "初十"
        11 -> "十一"
        12 -> "十二"
        13 -> "十三"
        14 -> "十四"
        15 -> "十五"
        16 -> "十六"
        17 -> "十七"
        18 -> "十八"
        19 -> "十九"
        20 -> "二十"
        21 -> "廿一"
        22 -> "廿二"
        23 -> "廿三"
        24 -> "廿四"
        25 -> "廿五"
        26 -> "廿六"
        27 -> "廿七"
        28 -> "廿八"
        29 -> "廿九"
        30 -> "三十"
        else -> ""
    }

    private val solarFestivals = mapOf(
        (1 to 1) to "元旦",
        (2 to 14) to "情人节",
        (5 to 1) to "劳动节",
        (6 to 1) to "儿童节",
        (10 to 1) to "国庆节",
        (12 to 25) to "圣诞节",
    )

    private val lunarFestivals = mapOf(
        (1 to 1) to "春节",
        (1 to 15) to "元宵节",
        (5 to 5) to "端午节",
        (7 to 7) to "七夕",
        (8 to 15) to "中秋节",
        (9 to 9) to "重阳节",
        (12 to 8) to "腊八节",
        (12 to 23) to "小年",
    )
}
