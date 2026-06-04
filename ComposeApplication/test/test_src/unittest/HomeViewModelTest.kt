package com.shx.composeapplication.ui.home

import com.shx.composeapplication.data.entity.AccountRecordEntity
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.data.repository.AccountRepository
import com.shx.composeapplication.util.DateFilterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.*

/**
 * HomeViewModel 单元测试
 *
 * 姓名：[你的姓名]
 * 学号：[你的学号]
 * 测试日期：2026-06-03
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var repository: AccountRepository
    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        // 默认返回空列表
        coEvery { repository.observeByDay(any()) } returns flowOf(emptyList())
        viewModel = HomeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ========== 测试1：初始状态 ==========
    @Test
    fun initialSelectedDate_shouldBeToday() = runTest {
        val today = DateFilterState.today()
        assertEquals(today.year, viewModel.uiState.value.selectedDate.year)
        assertEquals(today.month, viewModel.uiState.value.selectedDate.month)
        assertEquals(today.day, viewModel.uiState.value.selectedDate.day)
    }

    // ========== 测试2：修改选中日期 ==========
    @Test
    fun onDateChange_updatesSelectedDate() = runTest {
        val newDate = DateFilterState(year = 2025, month = 12, day = 25)
        viewModel.onDateChange(newDate)
        advanceUntilIdle()

        assertEquals(2025, viewModel.uiState.value.selectedDate.year)
        assertEquals(12, viewModel.uiState.value.selectedDate.month)
        assertEquals(25, viewModel.uiState.value.selectedDate.day)
    }

    // ========== 测试3：日期自动修正 ==========
    @Test
    fun onDateChange_clampsInvalidDay() = runTest {
        // 2025年2月只有28天
        val invalidDate = DateFilterState(year = 2025, month = 2, day = 30)
        viewModel.onDateChange(invalidDate)
        advanceUntilIdle()

        assertEquals(28, viewModel.uiState.value.selectedDate.day)
    }

    // ========== 测试4：回到今天 ==========
    @Test
    fun onTodayClick_returnsToToday() = runTest {
        // 先改到其他日期
        viewModel.onDateChange(DateFilterState(year = 2025, month = 1, day = 1))
        advanceUntilIdle()

        // 再点击今天
        viewModel.onTodayClick()
        advanceUntilIdle()

        val today = DateFilterState.today()
        assertEquals(today.year, viewModel.uiState.value.selectedDate.year)
        assertEquals(today.month, viewModel.uiState.value.selectedDate.month)
        assertEquals(today.day, viewModel.uiState.value.selectedDate.day)
    }

    // ========== 测试5：打开添加对话框 ==========
    @Test
    fun onAddClick_showsFormDialog() = runTest {
        viewModel.onAddClick()
        advanceUntilIdle()

        val dialogMode = viewModel.uiState.value.dialogMode
        assertTrue("对话框应该是 Form 类型", dialogMode is RecordDialogMode.Form)

        val form = (dialogMode as RecordDialogMode.Form).form
        assertNull("新记录的 id 应为 null", form.id)
        assertEquals("", form.amount)
        assertEquals(RecordType.EXPENSE, form.type)
    }

    // ========== 测试6：编辑记录 ==========
    @Test
    fun onEditClick_populatesFormWithRecordData() = runTest {
        val record = AccountRecordEntity(
            id = 1L,
            amount = 99.5,
            type = RecordType.EXPENSE.name,
            category = "餐饮",
            note = "午餐",
            createdAt = 123456789L
        )

        viewModel.onEditClick(record)
        advanceUntilIdle()

        val form = (viewModel.uiState.value.dialogMode as RecordDialogMode.Form).form
        assertEquals(1L, form.id)
        assertEquals("99.5", form.amount)
        assertEquals(RecordType.EXPENSE, form.type)
        assertEquals("餐饮", form.category)
        assertEquals("午餐", form.note)
    }

    // ========== 测试7：整数金额格式化 ==========
    @Test
    fun onEditClick_formatsIntegerAmountWithoutDecimal() = runTest {
        val record = AccountRecordEntity(
            id = 2L,
            amount = 100.0,
            type = RecordType.INCOME.name,
            category = "工资",
            note = "",
            createdAt = System.currentTimeMillis()
        )

        viewModel.onEditClick(record)
        advanceUntilIdle()

        val form = (viewModel.uiState.value.dialogMode as RecordDialogMode.Form).form
        assertEquals("100", form.amount)
    }

    // ========== 测试8：打开删除确认对话框 ==========
    @Test
    fun onDeleteClick_showsDeleteConfirmDialog() = runTest {
        val record = AccountRecordEntity(
            id = 1L,
            amount = 50.0,
            type = RecordType.EXPENSE.name,
            category = "交通",
            note = "",
            createdAt = System.currentTimeMillis()
        )

        viewModel.onDeleteClick(record)
        advanceUntilIdle()

        val dialogMode = viewModel.uiState.value.dialogMode
        assertTrue(dialogMode is RecordDialogMode.DeleteConfirm)
        assertEquals(1L, (dialogMode as RecordDialogMode.DeleteConfirm).record.id)
    }

    // ========== 测试9：关闭对话框 ==========
    @Test
    fun onDismissDialog_hidesDialog() = runTest {
        // 先打开对话框
        viewModel.onAddClick()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.dialogMode is RecordDialogMode.Form)

        // 关闭对话框
        viewModel.onDismissDialog()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dialogMode is RecordDialogMode.Hidden)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ========== 测试10：保存新记录（有效数据） ==========
    @Test
    fun onSaveForm_withValidData_insertsRecord() = runTest {
        coEvery { repository.insert(any(), any(), any(), any(), any()) } returns 1L

        val form = RecordFormState(
            amount = "100",
            type = RecordType.EXPENSE,
            category = "餐饮",
            note = "测试午餐"
        )

        viewModel.onSaveForm(form)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.insert(
                amount = 100.0,
                type = RecordType.EXPENSE,
                category = "餐饮",
                note = "测试午餐",
                createdAt = any()
            )
        }

        assertTrue(viewModel.uiState.value.dialogMode is RecordDialogMode.Hidden)
    }

    // ========== 测试11：金额为空时显示错误 ==========
    @Test
    fun onSaveForm_withEmptyAmount_showsError() = runTest {
        val form = RecordFormState(
            amount = "",
            type = RecordType.EXPENSE,
            category = "餐饮",
            note = ""
        )

        viewModel.onSaveForm(form)
        advanceUntilIdle()

        assertEquals("请输入有效金额", viewModel.uiState.value.errorMessage)
        coVerify(inverse = true) { repository.insert(any(), any(), any(), any(), any()) }
    }

    // ========== 测试12：金额为0时显示错误 ==========
    @Test
    fun onSaveForm_withZeroAmount_showsError() = runTest {
        val form = RecordFormState(
            amount = "0",
            type = RecordType.EXPENSE,
            category = "餐饮",
            note = ""
        )

        viewModel.onSaveForm(form)
        advanceUntilIdle()

        assertEquals("请输入有效金额", viewModel.uiState.value.errorMessage)
    }

    // ========== 测试13：分类为空时显示错误 ==========
    @Test
    fun onSaveForm_withEmptyCategory_showsError() = runTest {
        val form = RecordFormState(
            amount = "100",
            type = RecordType.EXPENSE,
            category = "",
            note = ""
        )

        viewModel.onSaveForm(form)
        advanceUntilIdle()

        assertEquals("请选择分类", viewModel.uiState.value.errorMessage)
    }

    // ========== 测试14：更新已有记录 ==========
    @Test
    fun onSaveForm_withExistingId_updatesRecord() = runTest {
        coEvery { repository.update(any()) } returns Unit

        val form = RecordFormState(
            id = 1L,
            amount = "150",
            type = RecordType.INCOME,
            category = "工资",
            note = "更新后的备注",
            createdAt = 123456789L
        )

        viewModel.onSaveForm(form)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.update(
                match { record ->
                    record.id == 1L &&
                            record.amount == 150.0 &&
                            record.type == RecordType.INCOME.name &&
                            record.category == "工资"
                }
            )
        }
    }

    // ========== 测试15：确认删除 ==========
    @Test
    fun onConfirmDelete_deletesRecord() = runTest {
        coEvery { repository.delete(any()) } returns Unit

        val record = AccountRecordEntity(
            id = 1L,
            amount = 50.0,
            type = RecordType.EXPENSE.name,
            category = "交通",
            note = "",
            createdAt = System.currentTimeMillis()
        )

        viewModel.onConfirmDelete(record)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.delete(record) }
        assertTrue(viewModel.uiState.value.dialogMode is RecordDialogMode.Hidden)
    }

    // ========== 测试16：统计数据计算正确 ==========
    @Test
    fun summary_calculatesCorrectly() = runTest {
        val testRecords = listOf(
            AccountRecordEntity(amount = 100.0, type = RecordType.INCOME.name, category = "工资", note = "", createdAt = 1L),
            AccountRecordEntity(amount = 50.0, type = RecordType.EXPENSE.name, category = "餐饮", note = "", createdAt = 2L),
            AccountRecordEntity(amount = 30.0, type = RecordType.EXPENSE.name, category = "交通", note = "", createdAt = 3L)
        )

        // 重新 mock
        val newRepository = mockk<AccountRepository>(relaxed = true)
        coEvery { newRepository.observeByDay(any()) } returns flowOf(testRecords)

        val newViewModel = HomeViewModel(newRepository)
        advanceUntilIdle()

        assertEquals(100.0, newViewModel.uiState.value.summary.totalIncome, 0.001)
        assertEquals(80.0, newViewModel.uiState.value.summary.totalExpense, 0.001)
        assertEquals(20.0, newViewModel.uiState.value.summary.balance, 0.001)
    }

    // ========== 测试17：清除错误信息 ==========
    @Test
    fun onDismissError_clearsErrorMessage() = runTest {
        // 先触发一个错误
        val form = RecordFormState(amount = "", type = RecordType.EXPENSE, category = "餐饮", note = "")
        viewModel.onSaveForm(form)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        // 清除错误
        viewModel.onDismissError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}