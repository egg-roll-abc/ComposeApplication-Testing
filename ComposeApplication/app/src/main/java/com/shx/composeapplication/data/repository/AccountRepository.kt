package com.shx.composeapplication.data.repository

import com.shx.composeapplication.data.dao.AccountRecordDao
import com.shx.composeapplication.data.entity.AccountRecordEntity
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.util.DateFilterState
import com.shx.composeapplication.util.DateFilterUtils
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val dao: AccountRecordDao
) {
    fun observeByDay(date: DateFilterState): Flow<List<AccountRecordEntity>> {
        val (startMillis, endMillis) = DateFilterUtils.dayRangeMillis(date.year, date.month, date.day)
        return dao.observeByDateRange(startMillis, endMillis)
    }

    suspend fun insert(
        amount: Double,
        type: RecordType,
        category: String,
        note: String,
        createdAt: Long = System.currentTimeMillis()
    ): Long = dao.insert(
        AccountRecordEntity(
            amount = amount,
            type = type.name,
            category = category,
            note = note,
            createdAt = createdAt
        )
    )

    suspend fun update(record: AccountRecordEntity) = dao.update(record)

    suspend fun delete(record: AccountRecordEntity) = dao.delete(record)

    suspend fun getById(id: Long): AccountRecordEntity? = dao.getById(id)

    fun observeAll(): Flow<List<AccountRecordEntity>> = dao.observeAll()

    fun observeByMonth(year: Int, month: Int): Flow<List<AccountRecordEntity>> {
        val (startMillis, endMillis) = DateFilterUtils.monthRangeMillis(year, month)
        return dao.observeByDateRange(startMillis, endMillis)
    }

    suspend fun clearAll() = dao.deleteAll()
}
