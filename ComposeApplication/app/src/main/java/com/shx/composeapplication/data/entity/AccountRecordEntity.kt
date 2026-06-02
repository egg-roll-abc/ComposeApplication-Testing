package com.shx.composeapplication.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shx.composeapplication.data.model.RecordType

@Entity(tableName = "account_records")
data class AccountRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String,
    val category: String,
    val note: String,
    val createdAt: Long
) {
    val recordType: RecordType
        get() = RecordType.fromDb(type)
}
