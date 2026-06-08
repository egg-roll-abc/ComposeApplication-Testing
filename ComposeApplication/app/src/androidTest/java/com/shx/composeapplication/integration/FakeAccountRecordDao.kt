package com.shx.composeapplication.integration

import com.shx.composeapplication.data.dao.AccountRecordDao
import com.shx.composeapplication.data.entity.AccountRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * AccountRecordDao 的内存模拟实现，用于集成测试。
 *
 * 特性：
 * - 所有数据存储在内存中，不依赖真实 Room 数据库
 * - observeByDateRange / observeAll 返回响应式 Flow，数据变更时自动推送
 * - 支持通过 insertShouldThrow / deleteShouldThrow 模拟异常场景
 */
class FakeAccountRecordDao : AccountRecordDao {

    private val records = mutableListOf<AccountRecordEntity>()
    private var nextId = 1L
    private val recordsFlow = MutableStateFlow<List<AccountRecordEntity>>(emptyList())

    /** 设置为 true 后，下一次 insert 调用将抛出 RuntimeException */
    var insertShouldThrow = false

    /** 设置为 true 后，下一次 delete 调用将抛出 RuntimeException */
    var deleteShouldThrow = false

    private fun updateFlow() {
        recordsFlow.value = records.toList()
    }

    // ---- 直接操控方法（仅供测试使用） ----

    /** 直接设置记录列表，触发 Flow 更新，等效于数据库被外部修改 */
    fun setRecords(newRecords: List<AccountRecordEntity>) {
        records.clear()
        records.addAll(newRecords)
        nextId = (newRecords.maxOfOrNull { it.id } ?: 0) + 1
        updateFlow()
    }

    /** 重置所有状态，包括异常标志 */
    fun reset() {
        records.clear()
        nextId = 1L
        insertShouldThrow = false
        deleteShouldThrow = false
        updateFlow()
    }

    // ---- AccountRecordDao 接口实现 ----

    override fun observeByDateRange(startMillis: Long, endMillis: Long): Flow<List<AccountRecordEntity>> {
        return recordsFlow.map { recordList ->
            recordList.filter { it.createdAt >= startMillis && it.createdAt < endMillis }
        }
    }

    override suspend fun getById(id: Long): AccountRecordEntity? {
        return records.find { it.id == id }
    }

    override suspend fun insert(record: AccountRecordEntity): Long {
        if (insertShouldThrow) {
            insertShouldThrow = false
            throw RuntimeException("数据库错误")
        }
        val id = if (record.id == 0L) nextId++ else record.id
        records.add(record.copy(id = id))
        updateFlow()
        return id
    }

    override suspend fun update(record: AccountRecordEntity) {
        val index = records.indexOfFirst { it.id == record.id }
        if (index >= 0) records[index] = record
        updateFlow()
    }

    override suspend fun delete(record: AccountRecordEntity) {
        if (deleteShouldThrow) {
            deleteShouldThrow = false
            throw RuntimeException("数据库错误")
        }
        records.removeIf { it.id == record.id }
        updateFlow()
    }

    override fun observeAll(): Flow<List<AccountRecordEntity>> = recordsFlow

    override suspend fun deleteAll() {
        records.clear()
        updateFlow()
    }
}
