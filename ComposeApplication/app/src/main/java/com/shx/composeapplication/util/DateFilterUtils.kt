package com.shx.composeapplication.util

import java.util.Calendar

data class DateFilterState(
    val year: Int,
    val month: Int,
    val day: Int
) {
    fun isToday(): Boolean = this == today()

    companion object {
        fun today(): DateFilterState {
            val calendar = Calendar.getInstance()
            return DateFilterState(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}

object DateFilterUtils {

    fun dayRangeMillis(year: Int, month: Int, day: Int): Pair<Long, Long> {
        val startCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCalendar = (startCalendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return startCalendar.timeInMillis to endCalendar.timeInMillis
    }

    fun maxDayInMonth(year: Int, month: Int): Int {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun clampDay(year: Int, month: Int, day: Int): Int =
        day.coerceIn(1, maxDayInMonth(year, month))

    fun defaultCreatedAt(date: DateFilterState): Long {
        if (date.isToday()) return System.currentTimeMillis()
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.month - 1)
            set(Calendar.DAY_OF_MONTH, date.day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun formatDayLabel(date: DateFilterState): String =
        "${date.year}年${date.month}月${date.day}日"

    /** 筛选栏展示：选中今天时标注（今天），否则仅显示具体日期 */
    fun formatDisplayLabel(date: DateFilterState): String =
        if (date.isToday()) "今天 · ${date.month}月${date.day}日" else formatDayLabel(date)

    /** 与本地时区日历一致，避免 UTC 转换导致日期偏差 */
    fun toPickerMillis(date: DateFilterState): Long =
        localCalendar(date.year, date.month, date.day, hour = 12).timeInMillis

    fun fromPickerMillis(millis: Long): DateFilterState {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return DateFilterState(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun localCalendar(year: Int, month: Int, day: Int, hour: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    fun pickerYearRange(): IntRange {
        val todayYear = DateFilterState.today().year
        return IntRange(2020, todayYear)
    }

    fun currentYearMonth(): Pair<Int, Int> {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) to calendar.get(Calendar.MONTH) + 1
    }

    fun monthRangeMillis(year: Int, month: Int): Pair<Long, Long> {
        val startCalendar = localCalendar(year, month, day = 1)
        val endCalendar = (startCalendar.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
        }
        return startCalendar.timeInMillis to endCalendar.timeInMillis
    }

    fun formatMonthLabel(year: Int, month: Int): String = "${year}年${month}月"
}
