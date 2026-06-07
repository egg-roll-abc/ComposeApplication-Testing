package com.shx.composeapplication.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shx.composeapplication.data.database.AppDatabase
import com.shx.composeapplication.data.dao.AccountRecordDao
import com.shx.composeapplication.data.entity.AccountRecordEntity
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.util.DateFilterState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * AccountRepository 仪器化单元测试
 * 测试日期：2026-06-03
 */
@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AccountRepository
    private lateinit var dao: AccountRecordDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 创建内存数据库
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.accountRecordDao()
        repository = AccountRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========== 测试1：插入并查询 ==========
    @Test
    fun testInsertAndQueryById() = runTest {
        val amount = 100.0
        val type = RecordType.EXPENSE
        val category = "餐饮"
        val note = "午餐"

        val insertedId = repository.insert(amount, type, category, note)
        val result = repository.getById(insertedId)

        assertNotNull("查询结果不应为null", result)
        assertEquals("金额不匹配", amount, result?.amount ?: 0.0, 0.001)
        assertEquals("类型不匹配", type.name, result?.type)
        assertEquals("分类不匹配", category, result?.category)
        assertEquals("备注不匹配", note, result?.note)
    }

    // ========== 测试2：查询所有记录 ==========
    @Test
    fun testObserveAllReturnsAllRecords() = runTest {
        repository.insert(100.0, RecordType.EXPENSE, "餐饮", "午餐")
        repository.insert(200.0, RecordType.INCOME, "工资", "1月工资")
        repository.insert(50.0, RecordType.EXPENSE, "交通", "地铁")

        val allRecords = repository.observeAll().first()
        assertEquals("记录数量应为3", 3, allRecords.size)
    }

    // ========== 测试3：更新记录 ==========
    @Test
    fun testUpdateRecord() = runTest {
        val insertedId = repository.insert(100.0, RecordType.EXPENSE, "餐饮", "原备注")

        val originalRecord = repository.getById(insertedId)
        assertNotNull(originalRecord)

        val updatedRecord = originalRecord!!.copy(
            amount = 150.0,
            note = "新备注"
        )

        repository.update(updatedRecord)

        val result = repository.getById(insertedId)
        assertEquals("金额应更新为150", 150.0, result?.amount ?: 0.0, 0.001)
        assertEquals("备注应更新为'新备注'", "新备注", result?.note)
    }

    // ========== 测试4：删除记录 ==========
    @Test
    fun testDeleteRecord() = runTest {
        val insertedId = repository.insert(100.0, RecordType.EXPENSE, "餐饮", "待删除")

        assertNotNull("插入后应该能查到", repository.getById(insertedId))

        val recordToDelete = repository.getById(insertedId)!!
        repository.delete(recordToDelete)

        val result = repository.getById(insertedId)
        assertNull("删除后查询结果应为null", result)
    }

    // ========== 测试5：按日查询 ==========
    @Test
    fun testObserveByDay() = runTest {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        calendar.set(year, month - 1, day, 0, 0, 0)
        val startOfDay = calendar.timeInMillis

        repository.insert(
            100.0, RecordType.EXPENSE, "餐饮", "今天的记录",
            createdAt = startOfDay + 1000
        )

        val yesterday = startOfDay - 24 * 60 * 60 * 1000
        repository.insert(
            200.0, RecordType.INCOME, "工资", "昨天的记录",
            createdAt = yesterday
        )

        val dateFilter = DateFilterState(year, month, day)
        val todayRecords = repository.observeByDay(dateFilter).first()

        assertEquals("今天应该有1条记录", 1, todayRecords.size)
    }

    // ========== 测试6：按月查询 ==========
    @Test
    fun testObserveByMonth() = runTest {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        calendar.set(year, month - 1, 1, 0, 0, 0)
        val startOfMonth = calendar.timeInMillis

        repository.insert(100.0, RecordType.EXPENSE, "餐饮", "本月记录", startOfMonth + 1000)
        repository.insert(200.0, RecordType.INCOME, "工资", "上月记录", startOfMonth - 1000)

        val monthRecords = repository.observeByMonth(year, month).first()

        assertTrue("本月至少应有1条记录", monthRecords.size >= 1)
        val hasCurrentMonthRecord = monthRecords.any { it.amount == 100.0 }
        assertTrue("应包含本月记录", hasCurrentMonthRecord)
    }

    // ========== 测试7：清空所有记录 ==========
    @Test
    fun testClearAll() = runTest {
        repository.insert(100.0, RecordType.EXPENSE, "餐饮", "记录1")
        repository.insert(200.0, RecordType.INCOME, "工资", "记录2")

        assertEquals("清空前应有2条记录", 2, repository.observeAll().first().size)

        repository.clearAll()

        val afterClear = repository.observeAll().first()
        assertEquals("清空后应为0条记录", 0, afterClear.size)
    }
}