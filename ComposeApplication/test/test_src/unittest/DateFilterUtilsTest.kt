package com.shx.composeapplication.util

import org.junit.Assert
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs

/**
 * DateFilter单元测试
 */
class DateFilterUtilsTest {

    // ============================================
    // 测试1：dayRangeMillis - 当天范围正确
    // ============================================
    @Test
    fun testDayRangeMillis_returnsCorrectRange() {
        // 测试日期：2026年6月3日
        val year = 2026
        val month = 6
        val day = 3

        val (startMillis, endMillis) = DateFilterUtils.dayRangeMillis(year, month, day)

        // 验证开始时间：2026-06-03 00:00:00
        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = startMillis
        }
        Assert.assertEquals(year, startCalendar.get(Calendar.YEAR))
        Assert.assertEquals(5, startCalendar.get(Calendar.MONTH)) // 月份从0开始，5 = 6月
        Assert.assertEquals(day, startCalendar.get(Calendar.DAY_OF_MONTH))
        Assert.assertEquals(0, startCalendar.get(Calendar.HOUR_OF_DAY))
        Assert.assertEquals(0, startCalendar.get(Calendar.MINUTE))
        Assert.assertEquals(0, startCalendar.get(Calendar.SECOND))

        // 验证结束时间：2026-06-04 00:00:00（第二天）
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = endMillis
        }
        Assert.assertEquals(year, endCalendar.get(Calendar.YEAR))
        Assert.assertEquals(5, endCalendar.get(Calendar.MONTH))
        Assert.assertEquals(day + 1, endCalendar.get(Calendar.DAY_OF_MONTH))
    }

    // ============================================
    // 测试2：dayRangeMillis - 跨月边界正确
    // ============================================
    @Test
    fun testDayRangeMillis_crossMonthBoundary() {
        // 测试日期：2026年1月31日（1月最后一天）
        val year = 2026
        val month = 1
        val day = 31

        val (startMillis, endMillis) = DateFilterUtils.dayRangeMillis(year, month, day)

        // 验证结束时间应该是 2026-02-01 00:00:00
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = endMillis
        }
        Assert.assertEquals(year, endCalendar.get(Calendar.YEAR))
        Assert.assertEquals(1, endCalendar.get(Calendar.MONTH)) // 1 = 2月
        Assert.assertEquals(1, endCalendar.get(Calendar.DAY_OF_MONTH))
    }

    // ============================================
    // 测试3：maxDayInMonth - 返回正确的当月最大天数
    // ============================================
    @Test
    fun testMaxDayInMonth_returnsCorrectMaxDay() {
        // 1月 = 31天
        Assert.assertEquals(31, DateFilterUtils.maxDayInMonth(2026, 1))
        // 4月 = 30天
        Assert.assertEquals(30, DateFilterUtils.maxDayInMonth(2026, 4))
        // 2月非闰年 = 28天
        Assert.assertEquals(28, DateFilterUtils.maxDayInMonth(2025, 2))
        // 2月闰年 = 29天
        Assert.assertEquals(29, DateFilterUtils.maxDayInMonth(2024, 2))
    }

    // ============================================
    // 测试4：clampDay - 将日期限制在有效范围内
    // ============================================
    @Test
    fun testClampDay_restrictsDayToValidRange() {
        // 1月有31天，输入35应该变成31
        Assert.assertEquals(31, DateFilterUtils.clampDay(2026, 1, 35))

        // 1月有31天，输入0应该变成1
        Assert.assertEquals(1, DateFilterUtils.clampDay(2026, 1, 0))

        // 2月非闰年有28天，输入30应该变成28
        Assert.assertEquals(28, DateFilterUtils.clampDay(2025, 2, 30))

        // 有效输入不变
        Assert.assertEquals(15, DateFilterUtils.clampDay(2026, 6, 15))
    }

    // ============================================
    // 测试5：formatDayLabel - 格式化正确
    // ============================================
    @Test
    fun testFormatDayLabel_returnsCorrectFormat() {
        val date = DateFilterState(year = 2026, month = 6, day = 3)
        Assert.assertEquals("2026年6月3日", DateFilterUtils.formatDayLabel(date))
    }

    // ============================================
    // 测试6：formatDisplayLabel - 今天显示特殊格式
    // ============================================
    @Test
    fun testFormatDisplayLabel_todayShowsSpecialFormat() {
        // 获取今天的实际日期
        val today = DateFilterState.Companion.today()

        val result = DateFilterUtils.formatDisplayLabel(today)

        // 今天的格式应该是 "今天 · X月X日"
        Assert.assertTrue(result.startsWith("今天 · "))
        Assert.assertTrue(result.contains("${today.month}月"))
        Assert.assertTrue(result.contains("${today.day}日"))
    }

    @Test
    fun testFormatDisplayLabel_nonTodayShowsNormalFormat() {
        val date = DateFilterState(year = 2025, month = 6, day = 3)
        Assert.assertEquals("2025年6月3日", DateFilterUtils.formatDisplayLabel(date))
    }

    // ============================================
    // 测试7：DateFilterState.today() - 返回今天的日期
    // ============================================
    @Test
    fun testToday_returnsCurrentDate() {
        val today = DateFilterState.Companion.today()
        val calendar = Calendar.getInstance()

        Assert.assertEquals(calendar.get(Calendar.YEAR), today.year)
        Assert.assertEquals(calendar.get(Calendar.MONTH) + 1, today.month)
        Assert.assertEquals(calendar.get(Calendar.DAY_OF_MONTH), today.day)
    }

    @Test
    fun testIsToday_returnsTrueForToday() {
        val today = DateFilterState.Companion.today()
        Assert.assertTrue(today.isToday())

        val yesterday = DateFilterState(
            year = 2026,
            month = 6,
            day = 2
        )
        // 如果不是今天，isToday应该返回false（注意：如果今天恰好是2026-06-02，这个测试会失败）
        // 所以这里用另一种方式验证：创建一个明显不是今天的日期
        val farDate = DateFilterState(year = 2000, month = 1, day = 1)
        Assert.assertFalse(farDate.isToday())
    }

    // ============================================
    // 测试8：toPickerMillis 和 fromPickerMillis 互逆
    // ============================================
    @Test
    fun testPickerMillisConversion_isReversible() {
        val original = DateFilterState(year = 2026, month = 6, day = 3)
        val millis = DateFilterUtils.toPickerMillis(original)
        val converted = DateFilterUtils.fromPickerMillis(millis)

        Assert.assertEquals(original.year, converted.year)
        Assert.assertEquals(original.month, converted.month)
        Assert.assertEquals(original.day, converted.day)
    }

    // ============================================
    // 测试9：monthRangeMillis - 返回正确的月份范围
    // ============================================
    @Test
    fun testMonthRangeMillis_returnsCorrectMonthRange() {
        val year = 2026
        val month = 6

        val (startMillis, endMillis) = DateFilterUtils.monthRangeMillis(year, month)

        // 验证开始时间：2026-06-01 00:00:00
        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = startMillis
        }
        Assert.assertEquals(year, startCalendar.get(Calendar.YEAR))
        Assert.assertEquals(5, startCalendar.get(Calendar.MONTH))
        Assert.assertEquals(1, startCalendar.get(Calendar.DAY_OF_MONTH))

        // 验证结束时间：2026-07-01 00:00:00
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = endMillis
        }
        Assert.assertEquals(year, endCalendar.get(Calendar.YEAR))
        Assert.assertEquals(6, endCalendar.get(Calendar.MONTH)) // 6 = 7月
        Assert.assertEquals(1, endCalendar.get(Calendar.DAY_OF_MONTH))
    }

    // ============================================
    // 测试10：monthRangeMillis - 跨年边界正确
    // ============================================
    @Test
    fun testMonthRangeMillis_crossYearBoundary() {
        val year = 2026
        val month = 12  // 12月

        val (startMillis, endMillis) = DateFilterUtils.monthRangeMillis(year, month)

        // 验证开始时间：2026-12-01 00:00:00
        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = startMillis
        }
        Assert.assertEquals(year, startCalendar.get(Calendar.YEAR))
        Assert.assertEquals(11, startCalendar.get(Calendar.MONTH))
        Assert.assertEquals(1, startCalendar.get(Calendar.DAY_OF_MONTH))

        // 验证结束时间：2027-01-01 00:00:00
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = endMillis
        }
        Assert.assertEquals(year + 1, endCalendar.get(Calendar.YEAR))
        Assert.assertEquals(0, endCalendar.get(Calendar.MONTH)) // 0 = 1月
        Assert.assertEquals(1, endCalendar.get(Calendar.DAY_OF_MONTH))
    }

    // ============================================
    // 测试11：formatMonthLabel - 格式化正确
    // ============================================
    @Test
    fun testFormatMonthLabel_returnsCorrectFormat() {
        Assert.assertEquals("2026年6月", DateFilterUtils.formatMonthLabel(2026, 6))
        Assert.assertEquals("2025年12月", DateFilterUtils.formatMonthLabel(2025, 12))
    }

    // ============================================
    // 测试12：pickerYearRange - 返回正确的年份范围
    // ============================================
    @Test
    fun testPickerYearRange_returnsCorrectRange() {
        val range = DateFilterUtils.pickerYearRange()
        val currentYear = DateFilterState.Companion.today().year

        Assert.assertEquals(2020, range.first)   // 起始年份
        Assert.assertEquals(currentYear, range.last)  // 结束年份 = 当前年份
        Assert.assertTrue(range.contains(2025))
        Assert.assertTrue(range.contains(currentYear))
    }

    // ============================================
    // 测试13：currentYearMonth - 返回正确的当前年月
    // ============================================
    @Test
    fun testCurrentYearMonth_returnsCurrentYearAndMonth() {
        val calendar = Calendar.getInstance()
        val expectedYear = calendar.get(Calendar.YEAR)
        val expectedMonth = calendar.get(Calendar.MONTH) + 1

        val (year, month) = DateFilterUtils.currentYearMonth()

        Assert.assertEquals(expectedYear, year)
        Assert.assertEquals(expectedMonth, month)
    }

    // ============================================
    // 测试14：defaultCreatedAt - 今天的默认时间戳是当前时间
    // ============================================
    @Test
    fun testDefaultCreatedAt_today_returnsCurrentTime() {
        val today = DateFilterState.Companion.today()
        val createdAt = DateFilterUtils.defaultCreatedAt(today)

        // 应该在当前时间的前后1秒内
        val now = System.currentTimeMillis()
        val diff = abs(now - createdAt)
        Assert.assertTrue("时间差应在1000ms内，实际差${diff}ms", diff < 1000)
    }

    @Test
    fun testDefaultCreatedAt_nonToday_returnsNoonOfThatDay() {
        val date = DateFilterState(year = 2025, month = 6, day = 3)
        val createdAt = DateFilterUtils.defaultCreatedAt(date)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = createdAt
        }

        Assert.assertEquals(2025, calendar.get(Calendar.YEAR))
        Assert.assertEquals(5, calendar.get(Calendar.MONTH))
        Assert.assertEquals(3, calendar.get(Calendar.DAY_OF_MONTH))
        Assert.assertEquals(12, calendar.get(Calendar.HOUR_OF_DAY)) // 中午12点
        Assert.assertEquals(0, calendar.get(Calendar.MINUTE))
    }
}