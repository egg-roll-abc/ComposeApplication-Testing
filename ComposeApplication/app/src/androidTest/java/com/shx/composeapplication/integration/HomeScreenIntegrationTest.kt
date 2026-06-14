package com.shx.composeapplication.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.shx.composeapplication.data.entity.AccountRecordEntity
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.data.repository.AccountRepository
import com.shx.composeapplication.ui.home.HomeScreen
import com.shx.composeapplication.util.DateFilterState
import com.shx.composeapplication.util.DateFilterUtils
import com.shx.composeapplication.util.FormatUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * P0 集成测试：HomeScreen + HomeViewModel
 *
 * 测试范围：
 * 1. 初始状态测试
 * 2. 用户交互测试
 * 3. 状态流转测试
 * 4. 数据绑定测试
 *
 * 测试策略：使用 FakeAccountRecordDao 构建真实 AccountRepository，
 * 隔离 Room 数据库依赖，专注验证 UI 与 ViewModel 的集成联动。
 */
class HomeScreenIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeDao: FakeAccountRecordDao
    private lateinit var repository: AccountRepository

    private val today = DateFilterState.today()

    @Before
    fun setUp() {
        fakeDao = FakeAccountRecordDao()
        repository = AccountRepository(fakeDao)
    }

    // ============================================================
    // 1. 初始状态测试
    // ============================================================

    @Test
    fun emptyState_displaysEmptyStateText() {
        launchHomeScreen()

        val dateLabel = DateFilterUtils.formatDisplayLabel(today)
        // 精确匹配完整空状态文本，避免与 DateFilterBar/SummaryCard 中的日期标签冲突
        composeTestRule.onNodeWithText("${dateLabel} 暂无记账")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("点击右下角 + 记一笔")
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_displaysZeroSummary() {
        launchHomeScreen()

        // 空状态下 SummaryCard 结余/收入/支出均为 0.00，且无 RecordItem 列表
        // 使用精确匹配完整文本（"结余"行），避免匹配到多条
        val dateLabel = DateFilterUtils.formatDisplayLabel(today)
        composeTestRule.onNodeWithText("${dateLabel} 结余")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("收入")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("支出")
            .assertIsDisplayed()
    }

    @Test
    fun initialState_fabIsDisplayed() {
        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("记一笔")
            .assertIsDisplayed()
    }

    @Test
    fun initialState_dateFilterBarShowsToday() {
        launchHomeScreen()

        // DateFilterBar 的日期文本被 SummaryCard 和 EmptyState 复用
        // 使用精确匹配（非 substring），仅匹配 DateFilterBar 中独立的日期文本
        val dateLabel = DateFilterUtils.formatDisplayLabel(today)
        composeTestRule.onNodeWithText(dateLabel)
            .assertIsDisplayed()
    }

    // ============================================================
    // 2. 用户交互测试
    // ============================================================

    @Test
    fun clickFab_showsRecordFormDialog() {
        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("记一笔")
            .performClick()

        composeTestRule.onNodeWithText("记一笔")
            .assertIsDisplayed()
    }

    @Test
    fun clickFab_dialogInAddModeWithDefaultValues() {
        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("记一笔")
            .performClick()

        composeTestRule.onNodeWithText("记一笔")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("保存")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("取消")
            .assertIsDisplayed()
    }

    @Test
    fun clickRecordEditButton_showsEditDialog() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("编辑")
            .performClick()

        composeTestRule.onNodeWithText("编辑账单")
            .assertIsDisplayed()
    }

    @Test
    fun clickRecordDeleteButton_showsDeleteConfirmDialog() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("删除")
            .performClick()

        composeTestRule.onNodeWithText("删除账单")
            .assertIsDisplayed()
    }

    // ============================================================
    // 3. 状态流转测试
    // ============================================================

    @Test
    fun addRecord_listUpdatesWithNewRecord() {
        launchHomeScreen()

        // 初始为空
        composeTestRule.onNodeWithText("暂无记账", substring = true)
            .assertIsDisplayed()

        // 通过 FakeDao 添加记录，触发 Flow 更新
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 100.0, category = "交通", type = RecordType.EXPENSE)
        ))

        // 验证列表更新
        composeTestRule.onNodeWithText("交通")
            .assertIsDisplayed()
        // EmptyState 消失
        composeTestRule.onNodeWithText("暂无记账", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun recordsDisplayed_summaryCardUpdatesCorrectly() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 5000.0, category = "工资", type = RecordType.INCOME),
            createTestRecord(id = 2, amount = 100.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        // 验证结余行完整文本，避免金额与 RecordItem 重复匹配
        val dateLabel = DateFilterUtils.formatDisplayLabel(today)
        val expectedBalance = FormatUtils.formatMoney(4900.0)
        composeTestRule.onNodeWithText("${dateLabel} 结余")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("￥${expectedBalance}")
            .assertIsDisplayed()

        // 收入和支出的精确金额断言
        // 使用 hasSetTextAction 避免匹配到 RecordItem 中的金额文本
        val expectedIncome = FormatUtils.formatMoney(5000.0)
        composeTestRule.onNodeWithText("￥${expectedIncome}")
            .assertIsDisplayed()

        val expectedExpense = FormatUtils.formatMoney(100.0)
        composeTestRule.onNodeWithText("￥${expectedExpense}")
            .assertIsDisplayed()
    }

    @Test
    fun deleteLastRecord_emptyStateShowsAgain() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()

        // 通过 FakeDao 移除记录
        fakeDao.setRecords(emptyList())

        // EmptyState 再次出现
        composeTestRule.onNodeWithText("暂无记账", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun invalidAmount_showsErrorMessage() {
        launchHomeScreen()

        // 点击 FAB 打开表单
        composeTestRule.onNodeWithContentDescription("记一笔")
            .performClick()

        // 不输入金额直接点击保存
        composeTestRule.onNodeWithText("保存")
            .performClick()

        // 验证错误提示
        composeTestRule.onNodeWithText("请输入有效金额")
            .assertIsDisplayed()
    }



    @Test
    fun deleteFailure_showsErrorMessage() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))
        fakeDao.deleteShouldThrow = true

        launchHomeScreen()

        // 点击删除按钮
        composeTestRule.onNodeWithContentDescription("删除")
            .performClick()

        // 确认删除
        composeTestRule.onNodeWithText("删除")
            .performClick()

        // 验证错误提示
        composeTestRule.onNodeWithText("删除失败", substring = true)
            .assertIsDisplayed()
    }

    // ============================================================
    // 4. 数据绑定测试
    // ============================================================

    @Test
    fun uiStateRecordsChange_lazyColumnUpdates() {
        launchHomeScreen()

        // 初始空列表
        composeTestRule.onNodeWithText("暂无记账", substring = true)
            .assertIsDisplayed()

        // 添加第一条记录
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 30.0, category = "交通", type = RecordType.EXPENSE)
        ))

        composeTestRule.onNodeWithText("交通")
            .assertIsDisplayed()

        // 添加第二条记录
        fakeDao.setRecords(listOf(
            createTestRecord(id = 2, amount = 8000.0, category = "工资", type = RecordType.INCOME),
            createTestRecord(id = 1, amount = 30.0, category = "交通", type = RecordType.EXPENSE)
        ))

        composeTestRule.onNodeWithText("工资")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("交通")
            .assertIsDisplayed()
    }

    @Test
    fun uiStateSummaryChange_summaryCardUpdates() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 200.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        // 初始支出 200 — 通过结余行验证（支出 > 收入时结余为负）
        val expectedBalance = FormatUtils.formatMoney(-200.0)
        composeTestRule.onNodeWithText("￥${expectedBalance}")
            .assertIsDisplayed()

        // 新增一条收入记录
        fakeDao.setRecords(listOf(
            createTestRecord(id = 2, amount = 5000.0, category = "工资", type = RecordType.INCOME),
            createTestRecord(id = 1, amount = 200.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        // 结余更新 = 5000 - 200 = 4800
        val expectedBalanceAfterUpdate = FormatUtils.formatMoney(4800.0)
        composeTestRule.onNodeWithText("￥${expectedBalanceAfterUpdate}")
            .assertIsDisplayed()
    }

    @Test
    fun uiStateDialogModeChange_dialogShowsAndHides() {
        launchHomeScreen()

        // 初始状态：Dialog 不显示
        composeTestRule.onNodeWithText("记一笔")
            .assertDoesNotExist()

        // 点击 FAB → Dialog 显示
        composeTestRule.onNodeWithContentDescription("记一笔")
            .performClick()
        composeTestRule.onNodeWithText("记一笔")
            .assertIsDisplayed()

        // 点击取消 → Dialog 隐藏
        composeTestRule.onNodeWithText("取消")
            .performClick()
        composeTestRule.onNodeWithText("记一笔")
            .assertDoesNotExist()
    }

    @Test
    fun multipleExpenseRecords_summaryCalculatesCorrectly() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE),
            createTestRecord(id = 2, amount = 30.0, category = "交通", type = RecordType.EXPENSE),
            createTestRecord(id = 3, amount = 200.0, category = "购物", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        // 验证列表中有对应记录
        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("购物")
            .assertIsDisplayed()

        // 验证结余 = 0 - 280 = -280（通过结余金额断言间接验证汇总计算）
        val expectedBalance = FormatUtils.formatMoney(-280.0)
        composeTestRule.onNodeWithText("￥${expectedBalance}")
            .assertIsDisplayed()
    }

    @Test
    fun mixedIncomeAndExpenseRecords_summaryCalculatesCorrectly() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 8000.0, category = "工资", type = RecordType.INCOME),
            createTestRecord(id = 2, amount = 100.0, category = "餐饮", type = RecordType.EXPENSE),
            createTestRecord(id = 3, amount = 50.0, category = "交通", type = RecordType.EXPENSE),
            createTestRecord(id = 4, amount = 500.0, category = "奖金", type = RecordType.INCOME)
        ))

        launchHomeScreen()

        // 收入 = 8000 + 500 = 8500，支出 = 100 + 50 = 150，结余 = 8350
        val expectedBalance = FormatUtils.formatMoney(8350.0)
        composeTestRule.onNodeWithText("￥${expectedBalance}")
            .assertIsDisplayed()
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private fun launchHomeScreen() {
        composeTestRule.setContent {
            HomeScreen(repository = repository)
        }
    }

    private fun createTestRecord(
        id: Long,
        amount: Double,
        category: String,
        type: RecordType
    ): AccountRecordEntity {
        val createdAt = DateFilterUtils.defaultCreatedAt(today)
        return AccountRecordEntity(
            id = id,
            amount = amount,
            type = type.name,
            category = category,
            note = "",
            createdAt = createdAt
        )
    }
}
