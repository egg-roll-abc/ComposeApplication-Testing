package com.shx.composeapplication.integration

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shx.composeapplication.ContentHome
import com.shx.composeapplication.data.entity.AccountRecordEntity
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.data.repository.AccountRepository
import com.shx.composeapplication.ui.home.HomeViewModel
import com.shx.composeapplication.util.DateFilterState
import com.shx.composeapplication.util.DateFilterUtils
import com.shx.composeapplication.util.FormatUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 阶段二：沙箱全链路集成测试
 *
 * 测试思路：
 * 使用 FakeAccountRecordDao 构建离线沙箱环境，通过 ContentHome(repository) 注入，
 * HomeScreen 和 ProfileScreen 共享同一 repository 实例，模拟完整用户操作链路，
 * 验证 APP 整体集成闭环能力。
 *
 * 5 条核心业务链路：
 * 1. 新增记账全流程
 * 2. 编辑与删除全流程
 * 3. 日期筛选全流程
 * 4. 页面切换 + 统计联动 + 清空全流程
 * 5. 异常容错流程
 *
 * 日期切换策略：
 * 与 P2 DateFilterBarIntegrationTest 一致，DatePicker 日历单元格无法通过语义树定位，
 * 链路3的日期切换通过 ViewModel.onDateChange() 直接驱动，与 DatePicker 确认效果等价，
 * 仍完整验证 DateFilterBar ← HomeViewModel ← Repository 的数据流联动。
 */
class SandboxFullChainTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeDao: FakeAccountRecordDao
    private lateinit var repository: AccountRepository

    /** 通过 ViewModel 驱动日期变更时使用的 ViewModel 引用 */
    private var capturedHomeViewModel: HomeViewModel? = null

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
        capturedHomeViewModel = null
    }

    // ============================================================
    // 链路1：新增记账全流程
    // 启动 → HomeScreen 空状态 → 点击 FAB → 填写表单 → 保存 → 列表刷新显示新记录
    // ============================================================

    @Test
    fun chain1_addRecordFullFlow() {
        launchContentHome()

        // 1. 空状态：EmptyState 展示
        composeTestRule.onNodeWithText("暂无记账", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("点击右下角 + 记一笔")
            .assertIsDisplayed()

        // 2. 点击 FAB → RecordFormDialog 弹出（新增模式）
        composeTestRule.onNodeWithContentDescription("记一笔")
            .performClick()
        composeTestRule.onNodeWithText("记一笔")
            .assertIsDisplayed()
        // 默认选中"支出"类型（SegmentedButton），用 hasClickAction 排除 SummaryCard 的"支出"
        composeTestRule.onNode(hasText("支出") and hasClickAction())
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("保存")
            .assertIsDisplayed()

        // 3. 输入金额（placeholder "0.00" 不注册 EditableText 语义，用 hasSetTextAction 定位输入框）
        composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
            .performTextInput("100")

        // 4. 选择分类（默认已选中第一个支出分类"餐饮"，点击"购物"切换）
        composeTestRule.onNodeWithText("购物")
            .performClick()

        // 5. 点击保存
        composeTestRule.onNodeWithText("保存")
            .performClick()

        // 6. Dialog 关闭，列表出现新记录
        composeTestRule.onNodeWithText("购物")
            .assertIsDisplayed()

        // 7. SummaryCard 更新：支出 100，结余 -100
        val expectedExpense = FormatUtils.formatMoney(100.0)
        composeTestRule.onNodeWithText("￥${expectedExpense}")
            .assertIsDisplayed()
    }

    // ============================================================
    // 链路2：编辑与删除全流程
    // 列表展示记录 → 点击编辑 → 修改金额/分类 → 保存 → 列表更新
    // → 点击删除 → 确认删除 → 列表移除
    // ============================================================

    @Test
    fun chain2_editAndDeleteFullFlow() {
        fakeDao.setRecords(listOf(
            createTodayRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchContentHome()

        // 1. 列表展示记录
        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()
        val initialExpense = FormatUtils.formatMoney(50.0)
        composeTestRule.onNodeWithText("￥${initialExpense}")
            .assertIsDisplayed()

        // 2. 点击编辑按钮 → RecordFormDialog 编辑模式（需先滚动到可见区域）
        composeTestRule.onNodeWithContentDescription("编辑")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("编辑账单")
            .assertIsDisplayed()

        // 3. 修改金额（用 performTextReplacement 替换整个文本，performTextInput 是追加）
        composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
            .performTextReplacement("200")

        // 4. 切换分类
        composeTestRule.onNodeWithText("交通")
            .performClick()

        // 5. 保存
        composeTestRule.onNodeWithText("保存")
            .performClick()

        // 6. 列表更新：新分类可见，旧分类消失
        composeTestRule.onNodeWithText("交通")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("餐饮")
            .assertDoesNotExist()

        // 7. SummaryCard 更新
        val updatedExpense = FormatUtils.formatMoney(200.0)
        composeTestRule.onNodeWithText("￥${updatedExpense}")
            .assertIsDisplayed()

        // 8. 点击删除按钮 → DeleteConfirmDialog（需先滚动到可见区域）
        composeTestRule.onNodeWithContentDescription("删除")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("删除账单")
            .assertIsDisplayed()

        // 9. 确认删除
        composeTestRule.onNodeWithText("删除")
            .performClick()

        // 10. 记录从列表移除，EmptyState 显示
        composeTestRule.onNodeWithText("暂无记账", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("交通")
            .assertDoesNotExist()
    }

    // ============================================================
    // 链路3：日期筛选全流程
    // HomeScreen 默认今天 → 切换日期 → 列表按日过滤 → 点击"回到今天" → 回到当天数据
    //
    // 注：日期切换通过 ViewModel.onDateChange() 驱动，原因见类注释
    // ============================================================

    @Test
    fun chain3_dateFilterFullFlow() {
        // 预设：今天有"餐饮"记录，targetDate 有"交通"记录
        fakeDao.setRecords(listOf(
            createTodayRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE),
            createRecordForDate(id = 2, amount = 30.0, category = "交通", type = RecordType.EXPENSE, date = targetDate)
        ))

        launchContentHomeWithViewModelCapture()

        // 1. 默认今天 → 显示今天的"餐饮"记录
        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()
        // "回到今天"按钮不可见
        composeTestRule.onNodeWithText("回到今天")
            .assertDoesNotExist()

        // 2. 点击日期区域 → DatePicker 弹出
        composeTestRule.onNodeWithContentDescription("选择日期")
            .performClick()
        composeTestRule.onNodeWithText("确定")
            .assertIsDisplayed()

        // 3. 取消 DatePicker → 日期不变
        composeTestRule.onNodeWithText("取消")
            .performClick()
        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()

        // 4. 切换到 targetDate（ViewModel 驱动，等效于 DatePicker 选日期后确认）
        capturedHomeViewModel!!.onDateChange(targetDate)

        // 5. 列表按日过滤：显示 targetDate 的"交通"，今天的"餐饮"消失
        composeTestRule.onNodeWithText("交通")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("餐饮")
            .assertDoesNotExist()

        // 6. "回到今天"按钮出现
        composeTestRule.onNodeWithText("回到今天")
            .assertIsDisplayed()

        // 7. 点击"回到今天" → 恢复今天数据
        composeTestRule.onNodeWithText("回到今天")
            .performClick()

        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("交通")
            .assertDoesNotExist()

        // 8. "回到今天"按钮消失
        composeTestRule.onNodeWithText("回到今天")
            .assertDoesNotExist()
    }

    // ============================================================
    // 链路4：页面切换 + 统计联动 + 清空全流程
    // HomeScreen → 切换到 ProfileScreen → 统计数据与 HomeScreen 一致
    // → 清空全部账单 → 返回 HomeScreen 空状态
    // ============================================================

    @Test
    fun chain4_pageSwitchAndClearFullFlow() {
        fakeDao.setRecords(listOf(
            createTodayRecord(id = 1, amount = 8000.0, category = "工资", type = RecordType.INCOME),
            createTodayRecord(id = 2, amount = 100.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchContentHome()

        // 1. HomeScreen 显示记录
        composeTestRule.onNodeWithText("工资")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("餐饮")
            .assertIsDisplayed()

        // 2. 切换到 ProfileScreen
        composeTestRule.onNodeWithText("我的")
            .performClick()

        // 3. ProfileScreen 统计数据与 HomeScreen 一致
        composeTestRule.onNodeWithText("记账 Demo")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("共 2 笔账单")
            .assertIsDisplayed()

        // 验证累计统计：结余 7900（收入 8000 - 支出 100）
        // 注：累计统计和当月统计都显示结余 7900，用 onAllNodesWithText 取第一个验证存在即可
        val expectedBalance = FormatUtils.formatMoney(7900.0)
        composeTestRule.onAllNodesWithText("￥${expectedBalance}")[0]
            .assertIsDisplayed()
        // "累计统计"标题存在，间接验证 ProfileScreen 展示了正确的统计卡片
        composeTestRule.onNodeWithText("累计统计")
            .assertIsDisplayed()

        // 4. 点击"清空全部账单" → 确认弹窗
        composeTestRule.onNodeWithText("清空全部账单")
            .performScrollTo()
            .performClick()

        // 5. 确认清空
        composeTestRule.onNodeWithText("清空")
            .performClick()

        // 6. Snackbar 显示"已清空全部账单"
        composeTestRule.waitUntil(3000) {
            try {
                composeTestRule.onNodeWithText("已清空全部账单")
                    .assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // 7. 统计数据归零
        composeTestRule.onNodeWithText("共 0 笔账单")
            .assertIsDisplayed()

        // 8. 切回 HomeScreen
        composeTestRule.onNodeWithText("首页")
            .performClick()

        // 9. HomeScreen 空状态
        composeTestRule.onNodeWithText("暂无记账", substring = true)
            .assertIsDisplayed()
    }

    // ============================================================
    // 链路5：异常容错流程
    // 保存时数据库异常 → Snackbar 提示 → 清空时数据库异常 → Snackbar 提示
    // ============================================================

    @Test
    fun chain5_saveFailure_showsSnackbarError() {
        launchContentHome()

        // 1. 点击 FAB 打开表单
        composeTestRule.onNodeWithContentDescription("记一笔")
            .performClick()

        // 2. 输入金额（placeholder "0.00" 不注册 EditableText 语义，用 hasSetTextAction 定位输入框）
        composeTestRule.onNode(hasSetTextAction() and hasText("金额", substring = true))
            .performTextInput("100")

        // 3. 设置下次 insert 抛出异常
        fakeDao.insertShouldThrow = true

        // 4. 点击保存 → 应触发异常
        composeTestRule.onNodeWithText("保存")
            .performClick()

        // 5. Snackbar 显示错误信息
        composeTestRule.waitUntil(3000) {
            try {
                composeTestRule.onNodeWithText("保存失败", substring = true)
                    .assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // 6. Dialog 仍显示（未关闭），应用不崩溃
        composeTestRule.onNodeWithText("记一笔")
            .assertIsDisplayed()
    }

    @Test
    fun chain5_clearAllFailure_showsSnackbarError() {
        fakeDao.setRecords(listOf(
            createTodayRecord(id = 1, amount = 50.0, category = "餐饮", type = RecordType.EXPENSE)
        ))

        launchContentHome()

        // 1. 切到 ProfileScreen
        composeTestRule.onNodeWithText("我的")
            .performClick()

        composeTestRule.onNodeWithText("共 1 笔账单")
            .assertIsDisplayed()

        // 2. 设置下次 deleteAll 抛出异常
        fakeDao.deleteAllShouldThrow = true

        // 3. 点击"清空全部账单"
        composeTestRule.onNodeWithText("清空全部账单")
            .performScrollTo()
            .performClick()

        // 4. 确认清空
        composeTestRule.onNodeWithText("清空")
            .performClick()

        // 5. Snackbar 显示"清空失败"
        composeTestRule.waitUntil(3000) {
            try {
                composeTestRule.onNodeWithText("清空失败", substring = true)
                    .assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // 6. 应用不崩溃，数据未清空
        composeTestRule.onNodeWithText("共 1 笔账单")
            .assertIsDisplayed()
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private fun launchContentHome() {
        composeTestRule.setContent {
            ContentHome(repository = repository)
        }
    }

    /**
     * 启动 ContentHome 并捕获 HomeViewModel 实例。
     *
     * 由于 viewModel() 在同一 ViewModelStoreOwner 内返回同一实例，
     * 此处通过 viewModel() 获取的引用与 HomeScreen 内部使用的
     * 是同一个 HomeViewModel，可用于直接驱动日期变更。
     */
    private fun launchContentHomeWithViewModelCapture() {
        composeTestRule.setContent {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
            DisposableEffect(vm) {
                capturedHomeViewModel = vm
                onDispose { capturedHomeViewModel = null }
            }
            ContentHome(repository = repository)
        }
    }

    private fun createTodayRecord(
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

    private fun createRecordForDate(
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
