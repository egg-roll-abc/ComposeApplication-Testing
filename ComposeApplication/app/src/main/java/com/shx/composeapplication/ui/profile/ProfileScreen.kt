package com.shx.composeapplication.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shx.composeapplication.AccountingApplication
import com.shx.composeapplication.data.repository.AccountRepository
import com.shx.composeapplication.ui.home.AccountSummary
import com.shx.composeapplication.util.FormatUtils

private val IncomeColor = Color(0xFF2E7D32)
private val ExpenseColor = Color(0xFFC62828)

@Composable
fun ProfileScreen(
    repository: AccountRepository? = null
) {
    val context = LocalContext.current
    val repo = repository ?: (context.applicationContext as AccountingApplication).accountRepository
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(repo))
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDismissSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileHeader()

            StatsCard(
                title = "累计统计",
                summary = uiState.allTimeSummary,
                subtitle = "共 ${uiState.totalCount} 笔账单"
            )

            StatsCard(
                title = "${uiState.monthLabel}统计",
                summary = uiState.monthSummary
            )

            if (uiState.topExpenseCategories.isNotEmpty()) {
                TopExpenseCard(categories = uiState.topExpenseCategories)
            }

            SettingsCard(
                onClearAll = viewModel::onClearAllClick,
                onAbout = viewModel::onAboutClick
            )
        }
    }

    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissClearConfirm,
            title = { Text("清空全部账单") },
            text = { Text("将删除本地全部记账记录，此操作不可恢复，确定继续吗？") },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmClearAll) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissClearConfirm) {
                    Text("取消")
                }
            }
        )
    }

    if (uiState.showAboutDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissAbout,
            title = { Text("关于记账 Demo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本地记账测试应用，数据仅保存在本机。")
                    Text("版本：$versionName")
                    Text("技术：Jetpack Compose + Room")
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissAbout) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun ProfileHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "记账 Demo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "本地存储 · 隐私安全",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    summary: AccountSummary,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("收入", summary.totalIncome, IncomeColor)
                StatItem("支出", summary.totalExpense, ExpenseColor)
                StatItem("结余", summary.balance, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(
            text = "￥${FormatUtils.formatMoney(amount)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun TopExpenseCard(categories: List<CategoryExpenseItem>) {
    val maxAmount = categories.maxOf { it.amount }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "本月支出 Top",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            categories.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = item.category, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "￥${FormatUtils.formatMoney(item.amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ExpenseColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (item.amount / maxAmount).toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = ExpenseColor,
                            trackColor = ExpenseColor.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    onClearAll: () -> Unit,
    onAbout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.List,
                title = "数据存储",
                subtitle = "所有账单保存在本机数据库",
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = null
            )
            HorizontalDivider()
            SettingsItem(
                icon = Icons.Default.Info,
                title = "关于",
                subtitle = "应用说明与版本信息",
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onAbout
            )
            HorizontalDivider()
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = "清空全部账单",
                subtitle = "删除本地所有记账记录",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = onClearAll
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
