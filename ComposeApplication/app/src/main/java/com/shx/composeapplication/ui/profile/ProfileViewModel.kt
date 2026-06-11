package com.shx.composeapplication.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shx.composeapplication.data.entity.AccountRecordEntity
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.data.repository.AccountRepository
import com.shx.composeapplication.ui.home.AccountSummary
import com.shx.composeapplication.util.DateFilterUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryExpenseItem(
    val category: String,
    val amount: Double
)

data class ProfileUiState(
    val totalCount: Int = 0,
    val allTimeSummary: AccountSummary = AccountSummary(),
    val monthSummary: AccountSummary = AccountSummary(),
    val monthLabel: String = "",
    val topExpenseCategories: List<CategoryExpenseItem> = emptyList(),
    val showClearConfirm: Boolean = false,
    val showAboutDialog: Boolean = false,
    val snackbarMessage: String? = null
)

class ProfileViewModel(
    private val repository: AccountRepository
) : ViewModel() {

    private val currentYear = DateFilterUtils.currentYearMonth().first
    private val currentMonth = DateFilterUtils.currentYearMonth().second
    private val monthLabel = DateFilterUtils.formatMonthLabel(currentYear, currentMonth)

    private val showClearConfirm = MutableStateFlow(false)
    private val showAboutDialog = MutableStateFlow(false)
    private val snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.observeAll(),
        repository.observeByMonth(currentYear, currentMonth),
        showClearConfirm,
        showAboutDialog,
        snackbarMessage
    ) { allRecords, monthRecords, clearConfirm, about, message ->
        ProfileUiState(
            totalCount = allRecords.size,
            allTimeSummary = summarize(allRecords),
            monthSummary = summarize(monthRecords),
            monthLabel = monthLabel,
            topExpenseCategories = topExpenseCategories(monthRecords),
            showClearConfirm = clearConfirm,
            showAboutDialog = about,
            snackbarMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(monthLabel = monthLabel)
    )

    fun onClearAllClick() {
        showClearConfirm.value = true
    }

    fun onDismissClearConfirm() {
        showClearConfirm.value = false
    }

    fun onConfirmClearAll() {
        viewModelScope.launch {
            try {
                repository.clearAll()
                showClearConfirm.value = false
                snackbarMessage.value = "已清空全部账单"
            } catch (e: Exception) {
                snackbarMessage.value = "清空失败：${e.message}"
            }
        }
    }

    fun onAboutClick() {
        showAboutDialog.value = true
    }

    fun onDismissAbout() {
        showAboutDialog.value = false
    }

    fun onDismissSnackbar() {
        snackbarMessage.value = null
    }

    companion object {
        fun factory(repository: AccountRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                        return ProfileViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }

        private fun summarize(records: List<AccountRecordEntity>): AccountSummary =
            records.fold(AccountSummary()) { acc, record ->
                when (record.recordType) {
                    RecordType.INCOME -> acc.copy(totalIncome = acc.totalIncome + record.amount)
                    RecordType.EXPENSE -> acc.copy(totalExpense = acc.totalExpense + record.amount)
                }
            }

        private fun topExpenseCategories(records: List<AccountRecordEntity>): List<CategoryExpenseItem> =
            records
                .filter { it.recordType == RecordType.EXPENSE }
                .groupBy { it.category }
                .map { (category, items) ->
                    CategoryExpenseItem(category, items.sumOf { it.amount })
                }
                .sortedByDescending { it.amount }
                .take(5)
    }
}
