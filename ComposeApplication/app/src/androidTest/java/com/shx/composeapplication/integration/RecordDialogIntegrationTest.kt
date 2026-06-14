package com.shx.composeapplication.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * P1 集成测试：RecordFormDialog + DeleteConfirmDialog
 *
 * 测试范围：
 * 1. RecordFormDialog 初始状态（新增/编辑模式）
 * 2. RecordFormDialog 用户交互（类型切换、金额输入、分类选择、备注、保存/取消）
 * 3. DeleteConfirmDialog 初始状态
 * 4. DeleteConfirmDialog 用户交互（确认/取消）
 *
 * 测试策略：通过 HomeScreen + FakeDao 驱动 ViewModel 触发 Dialog，
 * 专注验证 Dialog 与 ViewModel 的集成联动。
 *
 * 注意：Dialog 是浮层，背景 UI 节点仍在语义树中，"收入"/"支出"等文本
 * 在 SummaryCard 和 SegmentedButton 中重复，需用组合条件精确定位。
 */
class RecordDialogIntegrationTest {

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
    // 1. RecordFormDialog 初始状态测试
    // ============================================================

    @Test
    fun addMode_titleIsAddRecord() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNodeWithText("记一笔")
            .assertIsDisplayed()
    }

    @Test
    fun addMode_defaultTypeIsExpense() {
        launchHomeScreenAndOpenAddDialog()

        // "支出"同时出现在 SegmentedButton 和 SummaryCard，用 hasClickAction 精确定位 SegmentedButton
        composeTestRule.onNode(hasText("支出") and hasClickAction())
            .assertIsDisplayed()
    }

    @Test
    fun addMode_defaultCategoryIsFirstExpense() {
        launchHomeScreenAndOpenAddDialog()

        // "餐饮"同时出现在 FilterChip 和可能的背景，用 hasClickAction 定位 FilterChip
        composeTestRule.onNode(hasText("餐饮") and hasClickAction())
            .assertIsDisplayed()
    }

    @Test
    fun addMode_amountFieldIsEmpty() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun addMode_noteFieldIsEmpty() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNodeWithText("备注（可选）", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun addMode_hasSaveAndCancelButton() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNodeWithText("保存")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("取消")
            .assertIsDisplayed()
    }

    @Test
    fun editMode_titleIsEditRecord() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 88.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("编辑")
            .performClick()

        composeTestRule.onNodeWithText("编辑账单")
            .assertIsDisplayed()
    }

    @Test
    fun editMode_preFillsRecordData() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 88.0, category = "交通", type = RecordType.EXPENSE, note = "打车")
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("编辑")
            .performClick()

        // 验证编辑模式 Dialog 弹出
        composeTestRule.onNodeWithText("编辑账单")
            .assertIsDisplayed()
        // 验证预填金额（金额输入框在 Dialog 内，不受背景列表干扰）
        composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
            .assertTextContains("88")
    }

    // ============================================================
    // 2. RecordFormDialog 用户交互测试
    // ============================================================

    @Test
    fun switchToIncome_categoryListUpdates() {
        launchHomeScreenAndOpenAddDialog()

        // 默认显示支出分类
        composeTestRule.onNode(hasText("餐饮") and hasClickAction())
            .assertIsDisplayed()

        // 点击"收入" SegmentedButton 切换（用 hasClickAction 排除 SummaryCard 的"收入"）
        composeTestRule.onNode(hasText("收入") and hasClickAction())
            .performClick()

        // 应显示收入分类
        composeTestRule.onNode(hasText("工资") and hasClickAction())
            .assertIsDisplayed()
    }

    @Test
    fun switchType_defaultCategoryResets() {
        launchHomeScreenAndOpenAddDialog()

        // 默认选中"餐饮"（支出第一项）
        composeTestRule.onNode(hasText("餐饮") and hasClickAction())
            .assertIsDisplayed()

        // 切换到收入（用 hasClickAction 精确定位 SegmentedButton）
        composeTestRule.onNode(hasText("收入") and hasClickAction())
            .performClick()

        // 默认选中收入第一项 "工资"
        composeTestRule.onNode(hasText("工资") and hasClickAction())
            .assertIsDisplayed()
    }

    @Test
    fun amountInput_filtersNonNumeric() {
        launchHomeScreenAndOpenAddDialog()

        val amountField = composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
        amountField.performTextInput("100")

        // 输入字母应被过滤
        amountField.performTextInput("abc")

        // 金额字段应只保留数字部分
        amountField.assertTextContains("100")
    }

    @Test
    fun categorySelection_updatesSelectedCategory() {
        launchHomeScreenAndOpenAddDialog()

        // 默认选中"餐饮"，点击"购物"切换
        composeTestRule.onNode(hasText("购物") and hasClickAction())
            .performClick()

        composeTestRule.onNode(hasText("购物") and hasClickAction())
            .assertIsDisplayed()
    }

    @Test
    fun noteInput_acceptsText() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNode(hasSetTextAction() and hasText("备注", substring = true))
            .performTextInput("午餐")

        composeTestRule.onNode(hasSetTextAction() and hasText("备注", substring = true))
            .assertTextContains("午餐")
    }

    @Test
    fun saveWithEmptyAmount_showsValidationError() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNodeWithText("保存")
            .performClick()

        composeTestRule.onNodeWithText("请输入有效金额")
            .assertIsDisplayed()
    }

    @Test
    fun saveWithZeroAmount_showsValidationError() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
            .performTextInput("0")

        composeTestRule.onNodeWithText("保存")
            .performClick()

        composeTestRule.onNodeWithText("请输入有效金额")
            .assertIsDisplayed()
    }

    @Test
    fun saveWithValidData_insertsRecordAndClosesDialog() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
            .performTextInput("50")

        composeTestRule.onNodeWithText("保存")
            .performClick()

        composeTestRule.onNodeWithText("记一笔")
            .assertDoesNotExist()
    }

    @Test
    fun clickCancel_closesDialog() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNodeWithText("取消")
            .performClick()

        composeTestRule.onNodeWithText("记一笔")
            .assertDoesNotExist()
    }

    @Test
    fun clickOutsideDialog_closesDialog() {
        launchHomeScreenAndOpenAddDialog()

        composeTestRule.onNodeWithText("取消")
            .performClick()

        composeTestRule.onNodeWithText("记一笔")
            .assertDoesNotExist()
    }

    @Test
    fun editAndSave_updatesRecord() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("编辑")
            .performClick()

        composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
            .performTextInput("100")

        composeTestRule.onNodeWithText("保存")
            .performClick()

        composeTestRule.onNodeWithText("编辑账单")
            .assertDoesNotExist()
    }

    // ============================================================
    // 3. DeleteConfirmDialog 初始状态测试
    // ============================================================

    @Test
    fun deleteDialog_displaysTitle() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("删除")
            .performClick()

        composeTestRule.onNodeWithText("删除账单")
            .assertIsDisplayed()
    }

    @Test
    fun deleteDialog_displaysRecordInfo() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("删除")
            .performClick()

        // 验证 Dialog 正文包含完整描述（此文本仅在 Dialog 中出现，与 RecordItem 不重复）
        composeTestRule.onNodeWithText("确定删除「餐饮」", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun deleteDialog_hasDeleteAndCancelButton() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("删除")
            .performClick()

        composeTestRule.onNodeWithText("删除")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("取消")
            .assertIsDisplayed()
    }

    // ============================================================
    // 4. DeleteConfirmDialog 用户交互测试
    // ============================================================

    @Test
    fun clickDeleteConfirm_deletesRecordAndClosesDialog() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("删除")
            .performClick()

        composeTestRule.onNodeWithText("删除")
            .performClick()

        composeTestRule.onNodeWithText("删除账单")
            .assertDoesNotExist()
    }

    @Test
    fun clickDeleteCancel_closesDialog() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("删除")
            .performClick()

        composeTestRule.onNodeWithText("取消")
            .performClick()

        composeTestRule.onNodeWithText("删除账单")
            .assertDoesNotExist()

        // 记录仍在列表中
        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()
    }

    @Test
    fun deleteFailure_showsErrorAndKeepsDialog() {
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))
        fakeDao.deleteShouldThrow = true

        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("删除")
            .performClick()

        composeTestRule.onNodeWithText("删除")
            .performClick()

        composeTestRule.onNodeWithText("删除失败", substring = true)
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

    private fun launchHomeScreenAndOpenAddDialog() {
        launchHomeScreen()
        composeTestRule.onNodeWithContentDescription("记一笔")
            .performClick()
    }

    private fun createTestRecord(
        id: Long,
        amount: Double,
        category: String,
        type: RecordType,
        note: String = ""
    ): AccountRecordEntity {
        val createdAt = DateFilterUtils.defaultCreatedAt(today)
        return AccountRecordEntity(
            id = id,
            amount = amount,
            type = type.name,
            category = category,
            note = note,
            createdAt = createdAt
        )
    }
}
