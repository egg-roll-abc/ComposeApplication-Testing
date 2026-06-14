package com.shx.composeapplication.integration

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shx.composeapplication.data.entity.AccountRecordEntity
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.data.repository.AccountRepository
import com.shx.composeapplication.ui.home.HomeScreen
import com.shx.composeapplication.ui.home.HomeViewModel
import com.shx.composeapplication.util.DateFilterState
import com.shx.composeapplication.util.DateFilterUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * P2 集成测试：DateFilterBar 组件
 *
 * 测试范围：
 * 1. 初始状态测试（今天标签、"回到今天"按钮可见性）
 * 2. DatePickerDialog 交互测试（打开、取消、确认）
 * 3. 日期切换 & "回到今天" 测试（非今天标签、按钮出现/消失）
 * 4. 数据过滤测试（切换日期后记录按日过滤、"回到今天"恢复）
 *
 * 测试策略：
 * - DatePicker 打开/关闭：通过 UI 交互验证（点击日期区域、取消/确认按钮）
 * - 日期切换 & 数据过滤：通过 ViewModel.onDateChange() 直接驱动，绕过 DatePicker
 *   日历单元格交互。原因：Material3 DatePicker 日期单元格的 selectable 修饰符
 *   不注册 OnClick 语义，且日号文本可能被父节点合并语义覆盖，导致无法通过
 *   hasText + hasClickAction 定位。ViewModel 驱动仍完整验证了
 *   DateFilterBar ← HomeViewModel ← Repository 的数据流联动。
 *
 * 注意：
 * - DatePicker 是浮层 Dialog，背景节点仍在语义树中
 * - "确定"/"取消"仅在 DatePickerDialog 中出现（RecordFormDialog 用 "保存"/"取消"）
 */
class DateFilterBarIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeDao: FakeAccountRecordDao
    private lateinit var repository: AccountRepository

    /** 通过 ViewModel 驱动日期变更时使用的 ViewModel 引用 */
    private var capturedViewModel: HomeViewModel? = null

    private val today = DateFilterState.today()

    /** 选择一个与今天不同的日期（当前月内），用于日期切换测试 */
    private val targetDate: DateFilterState
        get() = if (today.day != 15) {
            DateFilterState(today.year, today.month, 15)
        } else {
            DateFilterState(today.year, today.month, 14)
        }

    @Before
    fun setUp() {
        fakeDao = FakeAccountRecordDao()
        repository = AccountRepository(fakeDao)
        capturedViewModel = null
    }

    // ============================================================
    // 1. 初始状态测试
    // ============================================================

    @Test
    fun todayState_displaysTodayLabel() {
        launchHomeScreen()

        val dateLabel = DateFilterUtils.formatDisplayLabel(today)
        composeTestRule.onNodeWithText(dateLabel)
            .assertIsDisplayed()
    }

    @Test
    fun todayState_backToTodayButtonNotVisible() {
        launchHomeScreen()

        composeTestRule.onNodeWithText("回到今天")
            .assertDoesNotExist()
    }

    // ============================================================
    // 2. DatePickerDialog 交互测试
    // ============================================================

    @Test
    fun clickDateArea_opensDatePickerDialog() {
        launchHomeScreen()

        // 点击日期区域打开 DatePicker（"选择日期" 是 KeyboardArrowDown 图标的内容描述，
        // clickable Row 会合并子节点语义，因此 onNodeWithContentDescription 能找到带点击的合并节点）
        composeTestRule.onNodeWithContentDescription("选择日期")
            .performClick()

        composeTestRule.onNodeWithText("确定")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("取消")
            .assertIsDisplayed()
    }

    @Test
    fun cancelDatePicker_closesDialogWithoutChange() {
        launchHomeScreen()

        val dateLabel = DateFilterUtils.formatDisplayLabel(today)

        composeTestRule.onNodeWithContentDescription("选择日期")
            .performClick()

        // 点击"取消"关闭 DatePicker
        composeTestRule.onNodeWithText("取消")
            .performClick()

        // Dialog 关闭："确定"不再存在
        composeTestRule.onNodeWithText("确定")
            .assertDoesNotExist()
        // 日期标签未变
        composeTestRule.onNodeWithText(dateLabel)
            .assertIsDisplayed()
    }

    @Test
    fun confirmDatePickerWithoutChange_closesDialog() {
        launchHomeScreen()

        composeTestRule.onNodeWithContentDescription("选择日期")
            .performClick()

        // 不修改日期，直接点击"确定"
        composeTestRule.onNodeWithText("确定")
            .performClick()

        // Dialog 关闭
        composeTestRule.onNodeWithText("确定")
            .assertDoesNotExist()
        // 日期仍是今天 → "回到今天"不可见
        composeTestRule.onNodeWithText("回到今天")
            .assertDoesNotExist()
    }

    // ============================================================
    // 3. 日期切换 & "回到今天" 测试
    // ============================================================

    @Test
    fun selectDifferentDate_backToTodayButtonAppears() {
        launchHomeScreenWithViewModelCapture()

        // 通过 ViewModel 切换到非今天日期
        capturedViewModel!!.onDateChange(targetDate)

        // "回到今天"按钮出现
        composeTestRule.onNodeWithText("回到今天")
            .assertIsDisplayed()
    }

    @Test
    fun selectDifferentDate_labelShowsFormattedDate() {
        launchHomeScreenWithViewModelCapture()

        // 通过 ViewModel 切换到非今天日期
        capturedViewModel!!.onDateChange(targetDate)

        // 日期标签显示非今天格式 "X年X月X日"
        val expectedLabel = DateFilterUtils.formatDisplayLabel(targetDate)
        composeTestRule.onNodeWithText(expectedLabel)
            .assertIsDisplayed()
    }

    @Test
    fun clickBackToToday_returnsToTodayState() {
        launchHomeScreenWithViewModelCapture()

        // 先切换到其他日期
        capturedViewModel!!.onDateChange(targetDate)
        composeTestRule.onNodeWithText("回到今天")
            .assertIsDisplayed()

        // 点击"回到今天"
        composeTestRule.onNodeWithText("回到今天")
            .performClick()

        // 日期标签回到今天
        val todayLabel = DateFilterUtils.formatDisplayLabel(today)
        composeTestRule.onNodeWithText(todayLabel)
            .assertIsDisplayed()
        // "回到今天"按钮消失
        composeTestRule.onNodeWithText("回到今天")
            .assertDoesNotExist()
    }

    // ============================================================
    // 4. 数据过滤测试
    // ============================================================

    @Test
    fun dateChange_filtersRecordsByDay() {
        // 预设：今天有 "餐饮" 记录，targetDate 有 "交通" 记录
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE, date = today),
            createTestRecord(id = 2, amount = 30.0, category = "交通", type = RecordType.EXPENSE, date = targetDate)
        ))

        launchHomeScreenWithViewModelCapture()

        // 初始显示今天的记录
        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()

        // 切换到 targetDate
        capturedViewModel!!.onDateChange(targetDate)

        // 显示 targetDate 的记录
        composeTestRule.onNodeWithText("交通")
            .assertIsDisplayed()
        // 今天的记录消失
        composeTestRule.onNodeWithText("餐饮")
            .assertDoesNotExist()
    }

    @Test
    fun backToToday_restoresTodayRecords() {
        // 预设：今天有 "餐饮" 记录，targetDate 有 "交通" 记录
        fakeDao.setRecords(listOf(
            createTestRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE, date = today),
            createTestRecord(id = 2, amount = 30.0, category = "交通", type = RecordType.EXPENSE, date = targetDate)
        ))

        launchHomeScreenWithViewModelCapture()

        // 切换到 targetDate
        capturedViewModel!!.onDateChange(targetDate)
        composeTestRule.onNodeWithText("交通")
            .assertIsDisplayed()

        // 点击"回到今天"
        composeTestRule.onNodeWithText("回到今天")
            .performClick()

        // 恢复今天的记录
        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()
        // targetDate 的记录消失
        composeTestRule.onNodeWithText("交通")
            .assertDoesNotExist()
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private fun launchHomeScreen() {
        composeTestRule.setContent {
            HomeScreen(repository = repository)
        }
    }

    /**
     * 启动 HomeScreen 并捕获 HomeViewModel 实例。
     *
     * 由于 viewModel() 在同一 ViewModelStoreOwner 内返回同一实例，
     * 此处通过 viewModel() 获取的引用与 HomeScreen 内部使用的
     * 是同一个 HomeViewModel，可用于直接驱动日期变更。
     */
    private fun launchHomeScreenWithViewModelCapture() {
        composeTestRule.setContent {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
            DisposableEffect(vm) {
                capturedViewModel = vm
                onDispose { capturedViewModel = null }
            }
            HomeScreen(repository = repository)
        }
    }

    private fun createTestRecord(
        id: Long,
        amount: Double,
        category: String,
        type: RecordType,
        date: DateFilterState
    ): AccountRecordEntity {
        val createdAt = DateFilterUtils.defaultCreatedAt(date)
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
