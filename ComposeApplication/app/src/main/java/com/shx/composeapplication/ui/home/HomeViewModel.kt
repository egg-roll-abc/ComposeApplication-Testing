package com.shx.composeapplication.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shx.composeapplication.data.entity.AccountRecordEntity
import com.shx.composeapplication.data.model.AccountCategories
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.data.repository.AccountRepository
import com.shx.composeapplication.util.AmountInputFilter
import com.shx.composeapplication.util.DateFilterState
import com.shx.composeapplication.util.DateFilterUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountSummary(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0
) {
    val balance: Double get() = totalIncome - totalExpense
}

data class RecordFormState(
    val id: Long? = null,
    val amount: String = "",
    val type: RecordType = RecordType.EXPENSE,
    val category: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

sealed class RecordDialogMode {
    data object Hidden : RecordDialogMode()
    data class Form(val form: RecordFormState) : RecordDialogMode()
    data class DeleteConfirm(val record: AccountRecordEntity) : RecordDialogMode()
}

data class HomeUiState(
    val selectedDate: DateFilterState = DateFilterState.today(),
    val records: List<AccountRecordEntity> = emptyList(),
    val summary: AccountSummary = AccountSummary(),
    val dialogMode: RecordDialogMode = RecordDialogMode.Hidden,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: AccountRepository
) : ViewModel() {

    private val selectedDate = MutableStateFlow(DateFilterState.today())
    private val recordsFlow = selectedDate.flatMapLatest { date ->
        repository.observeByDay(date)
    }
    private val dialogMode = MutableStateFlow<RecordDialogMode>(RecordDialogMode.Hidden)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        selectedDate,
        recordsFlow,
        dialogMode,
        errorMessage
    ) { date, records, dialog, error ->
        val summary = records.fold(AccountSummary()) { acc, record ->
            when (record.recordType) {
                RecordType.INCOME -> acc.copy(totalIncome = acc.totalIncome + record.amount)
                RecordType.EXPENSE -> acc.copy(totalExpense = acc.totalExpense + record.amount)
            }
        }
        HomeUiState(
            selectedDate = date,
            records = records,
            summary = summary,
            dialogMode = dialog,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun onDateChange(date: DateFilterState) {
        val clampedDay = DateFilterUtils.clampDay(date.year, date.month, date.day)
        selectedDate.value = date.copy(day = clampedDay)
    }

    fun onTodayClick() {
        selectedDate.value = DateFilterState.today()
    }

    fun onAddClick() {
        errorMessage.value = null
        val date = selectedDate.value
        dialogMode.value = RecordDialogMode.Form(
            RecordFormState(
                category = AccountCategories.expense.first(),
                createdAt = DateFilterUtils.defaultCreatedAt(date)
            )
        )
    }

    fun onEditClick(record: AccountRecordEntity) {
        errorMessage.value = null
        dialogMode.value = RecordDialogMode.Form(
            RecordFormState(
                id = record.id,
                amount = AmountInputFilter.filter(formatAmountInput(record.amount)),
                type = record.recordType,
                category = record.category,
                note = record.note,
                createdAt = record.createdAt
            )
        )
    }

    fun onDeleteClick(record: AccountRecordEntity) {
        errorMessage.value = null
        dialogMode.value = RecordDialogMode.DeleteConfirm(record)
    }

    fun onDismissDialog() {
        dialogMode.value = RecordDialogMode.Hidden
        errorMessage.value = null
    }

    fun onDismissError() {
        errorMessage.value = null
    }

    fun onSaveForm(form: RecordFormState) {
        val amount = form.amount.trim().trimEnd('.').toDoubleOrNull()
        if (amount == null || amount <= 0) {
            errorMessage.value = "请输入有效金额"
            return
        }
        if (form.category.isBlank()) {
            errorMessage.value = "请选择分类"
            return
        }

        viewModelScope.launch {
            try {
                if (form.id == null) {
                    repository.insert(
                        amount = amount,
                        type = form.type,
                        category = form.category,
                        note = form.note.trim(),
                        createdAt = form.createdAt
                    )
                } else {
                    repository.update(
                        AccountRecordEntity(
                            id = form.id,
                            amount = amount,
                            type = form.type.name,
                            category = form.category,
                            note = form.note.trim(),
                            createdAt = form.createdAt
                        )
                    )
                }
                onDismissDialog()
            } catch (e: Exception) {
                errorMessage.value = "保存失败：${e.message}"
            }
        }
    }

    fun onConfirmDelete(record: AccountRecordEntity) {
        viewModelScope.launch {
            try {
                repository.delete(record)
                onDismissDialog()
            } catch (e: Exception) {
                errorMessage.value = "删除失败：${e.message}"
            }
        }
    }

    companion object {
        fun factory(repository: AccountRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                        return HomeViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }

        private fun formatAmountInput(amount: Double): String =
            if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
    }
}
