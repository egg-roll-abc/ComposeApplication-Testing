package com.shx.composeapplication.data.model

enum class RecordType(val label: String) {
    EXPENSE("支出"),
    INCOME("收入");

    companion object {
        fun fromDb(value: String): RecordType =
            entries.firstOrNull { it.name == value } ?: EXPENSE
    }
}
