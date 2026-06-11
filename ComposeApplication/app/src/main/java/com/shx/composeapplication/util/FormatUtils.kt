package com.shx.composeapplication.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val moneyFormat = DecimalFormat("#,##0.00")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatMoney(amount: Double): String = moneyFormat.format(amount)

    fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))
}
