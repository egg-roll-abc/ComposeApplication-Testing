package com.shx.composeapplication.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shx.composeapplication.data.model.AccountCategories
import com.shx.composeapplication.data.model.RecordType
import com.shx.composeapplication.util.AmountInputFilter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordFormDialog(
    form: RecordFormState,
    onDismiss: () -> Unit,
    onConfirm: (RecordFormState) -> Unit
) {
    var localForm by remember(form) { mutableStateOf(form) }
    val categories = AccountCategories.forType(localForm.type)
    val title = if (localForm.id == null) "记一笔" else "编辑账单"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RecordType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = localForm.type == type,
                            onClick = {
                                val newCategories = AccountCategories.forType(type)
                                localForm = localForm.copy(
                                    type = type,
                                    category = newCategories.firstOrNull().orEmpty()
                                )
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = RecordType.entries.size
                            )
                        ) {
                            Text(type.label)
                        }
                    }
                }

                OutlinedTextField(
                    value = localForm.amount,
                    onValueChange = { input ->
                        localForm = localForm.copy(amount = AmountInputFilter.filter(input))
                    },
                    label = { Text("金额") },
                    placeholder = { Text("0.00") },
                    supportingText = { Text("仅数字，最多${AmountInputFilter.MAX_DIGITS}位") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("分类")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = localForm.category == category,
                            onClick = { localForm = localForm.copy(category = category) },
                            label = { Text(category) }
                        )
                    }
                }

                OutlinedTextField(
                    value = localForm.note,
                    onValueChange = { localForm = localForm.copy(note = it) },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localForm) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    category: String,
    amount: Double,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除账单") },
        text = { Text("确定删除「$category」￥${amount} 这条记录吗？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
