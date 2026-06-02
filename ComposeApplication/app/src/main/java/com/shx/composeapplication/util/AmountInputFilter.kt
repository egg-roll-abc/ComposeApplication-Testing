package com.shx.composeapplication.util

/**
 * 金额输入过滤：仅数字与一个小数点，数字位最多 [MAX_DIGITS] 位，小数最多 2 位。
 */
object AmountInputFilter {
    const val MAX_DIGITS = 10
    private const val MAX_DECIMAL_PLACES = 2

    fun filter(input: String): String {
        val result = StringBuilder()
        var digitCount = 0
        var hasDot = false
        var decimalPlaces = 0

        for (ch in input) {
            when {
                ch.isDigit() -> {
                    if (digitCount >= MAX_DIGITS) continue
                    if (hasDot) {
                        if (decimalPlaces >= MAX_DECIMAL_PLACES) continue
                        decimalPlaces++
                    }
                    result.append(ch)
                    digitCount++
                }
                ch == '.' -> {
                    if (hasDot) continue
                    hasDot = true
                    if (result.isEmpty()) {
                        result.append('0')
                    }
                    result.append('.')
                }
            }
        }
        return result.toString()
    }
}
