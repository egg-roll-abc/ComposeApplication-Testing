package com.shx.composeapplication.data.model

object AccountCategories {
    val expense = listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗", "教育", "其他")
    val income = listOf("工资", "奖金", "理财", "兼职", "其他")

    fun forType(type: RecordType): List<String> =
        when (type) {
            RecordType.EXPENSE -> expense
            RecordType.INCOME -> income
        }
}
