# P0 集成测试实施步骤记录

> 对应测试计划：ComposeApp_Integration_Test_Plan.md 第四章 4.1.3 P0 部分
> 测试目标：HomeScreen + HomeViewModel 组件化集成测试

---

## 步骤 1：环境准备

### 1.1 补充 MockK 依赖

**操作**：在 `gradle/libs.versions.toml` 和 `app/build.gradle.kts` 中添加 MockK 依赖。

**修改文件**：
- `gradle/libs.versions.toml`：新增 `mockk = "1.13.8"` 版本声明及 `mockk`/`mockk-android` 两个库声明
- `app/build.gradle.kts`：新增 `testImplementation(libs.mockk)` 和 `androidTestImplementation(libs.mockk.android)`

**结果**：✅ 完成

---

### 1.2 创建测试目录结构

**操作**：创建 `app/src/androidTest/java/com/shx/composeapplication/integration/` 目录。

**结果**：✅ 完成

---

### 1.3 运行时问题：META-INF/LICENSE.md 打包冲突

**现象**：Gradle 构建时报错 `6 files found with path 'META-INF/LICENSE.md'`。

**原因**：MockK 依赖传递引入的 JUnit Jupiter 多个模块包含相同 LICENSE 文件，Android 打包不允许重复。

**处理**：在 `app/build.gradle.kts` 的 `android {}` 块中添加 `packaging { resources { excludes += "META-INF/LICENSE.md" } }` 排除重复文件。

**结果**：✅ 已修复

---

### 1.4 运行时问题：MockK JVMTI Agent .so 对齐不兼容

**现象**：所有测试失败，报 `program alignment (4096) cannot be smaller than system page size (16384)`。

**原因**：MockK 1.13.8 的 `libmockkjvmtiagent.so` 按 4KB 对齐编译，API 35+ 模拟器系统页大小为 16KB，`dlopen` 拒绝加载。

**处理**：升级 MockK 到 1.13.13，但该版本仍未修复 x86_64 对齐问题。

**结果**：升级无效，转入 1.5 策略调整

---

### 1.5 策略调整：MockK 替换为 Fake DAO

**决策**：彻底弃用 MockK（androidTest），改用 `FakeAccountRecordDao` 实现内存模拟，构建真实 `AccountRepository`。

**修改内容**：
1. 新增 `FakeAccountRecordDao.kt`：实现 `AccountRecordDao` 接口，内存存储 + `MutableStateFlow` 响应式推送，支持 `insertShouldThrow` / `deleteShouldThrow` 模拟异常
2. 改写 `HomeScreenIntegrationTest.kt`：移除所有 `io.mockk` 引用，数据驱动方式从 `recordsFlow.value = ...` 改为 `fakeDao.setRecords(...)`，异常场景从 `coEvery { ... } throws` 改为 `fakeDao.insertShouldThrow = true`
3. `build.gradle.kts`：移除 `androidTestImplementation(libs.mockk.android)`；保留 `testImplementation(libs.mockk)` 供未来 JVM 测试使用

**优势**：Fake DAO 更贴近真实 DAO 行为，不依赖 JVMTI 原生库，更符合集成测试理念。

**结果**：✅ 已完成改写

---

## 步骤 2：源码审读与测试策略确定

### 2.1 源码审读清单

| 文件 | 关键发现 | 对测试的影响 |
|------|---------|-------------|
| `HomeScreen.kt` | `HomeScreen(repository?)` 接受可选 Repository 参数 | 可直接注入 FakeRepository，无需反射 |
| `HomeScreen.kt` | FAB contentDescription="记一笔"，编辑/删除按钮 contentDescription 分别为"编辑"/"删除" | 通过 contentDescription 定位按钮 |
| `HomeScreen.kt` | 空状态显示 `$dateLabel 暂无记账` 和 `点击右下角 + 记一笔` | 通过完整文本断言验证空状态 |
| `HomeScreen.kt` | Snackbar 通过 `LaunchedEffect(uiState.errorMessage)` 触发 | 错误信息验证依赖 Snackbar 展示 |
| `HomeViewModel.kt` | `uiState` 是 `StateFlow<HomeUiState>`，由 `combine` 四个流生成 | 通过 FakeDao 数据变更驱动 UI 更新 |
| `HomeViewModel.kt` | 保存校验：金额无效→"请输入有效金额"，分类为空→"请选择分类" | 需要覆盖校验失败场景 |
| `HomeViewModel.kt` | 保存/删除异常→errorMessage 设为"保存失败：xxx"/"删除失败：xxx" | 需要覆盖异常场景 |
| `RecordFormDialog.kt` | 新增模式标题"记一笔"，编辑模式标题"编辑账单" | 通过标题文本区分 Dialog 模式 |
| `DeleteConfirmDialog.kt` | 标题"删除账单"，内容含分类和金额 | 通过标题验证删除确认弹窗 |

### 2.2 测试策略决策

- **数据层隔离**：使用 `FakeAccountRecordDao` + 真实 `AccountRepository`，不使用 MockK
- **不使用 Espresso**：HomeScreen 是纯 Compose 页面，`createComposeRule()` 完全满足需求
- **数据驱动**：通过 `fakeDao.setRecords()` 动态变更数据，触发 Flow 更新驱动 UI 刷新

**结果**：✅ 策略确定

---

## 步骤 3：编写测试用例

### 3.1 测试文件

| 文件 | 路径 |
|------|------|
| 测试用例 | `app/src/androidTest/java/com/shx/composeapplication/integration/HomeScreenIntegrationTest.kt` |
| Fake DAO | `app/src/androidTest/java/com/shx/composeapplication/integration/FakeAccountRecordDao.kt` |

### 3.2 用例清单

#### 1. 初始状态测试（4 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 1 | `emptyState_displaysEmptyStateText` | 空数据时显示"$dateLabel 暂无记账"和"点击右下角 + 记一笔" | ✅ 通过 |
| 2 | `emptyState_displaysZeroSummary` | SummaryCard 显示"结余"行及收入/支出标签 | ✅ 通过 |
| 3 | `initialState_fabIsDisplayed` | FAB 按钮可见 | ✅ 通过 |
| 4 | `initialState_dateFilterBarShowsToday` | DateFilterBar 默认显示今天日期 | ✅ 通过 |

#### 2. 用户交互测试（4 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 5 | `clickFab_showsRecordFormDialog` | 点击 FAB → RecordFormDialog 弹出 | ✅ 通过 |
| 6 | `clickFab_dialogInAddModeWithDefaultValues` | 新增模式：标题"记一笔"，含"保存"/"取消"按钮 | ✅ 通过 |
| 7 | `clickRecordEditButton_showsEditDialog` | 点击编辑按钮 → Dialog 编辑模式弹出（标题"编辑账单"） | ✅ 通过 |
| 8 | `clickRecordDeleteButton_showsDeleteConfirmDialog` | 点击删除按钮 → DeleteConfirmDialog 弹出（标题"删除账单"） | ✅ 通过 |

#### 3. 状态流转测试（6 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 9 | `addRecord_listUpdatesWithNewRecord` | 新增记录后列表刷新，EmptyState 消失 | ✅ 通过 |
| 10 | `recordsDisplayed_summaryCardUpdatesCorrectly` | 有记录时 SummaryCard 收入/支出/结余正确计算 | ✅ 通过 |
| 11 | `deleteLastRecord_emptyStateShowsAgain` | 删除最后一条记录后 EmptyState 重新出现 | ✅ 通过 |
| 12 | `invalidAmount_showsErrorMessage` | 金额无效时 Snackbar 显示"请输入有效金额" | ✅ 通过 |
| 13 | `saveFailure_showsErrorMessage` | 保存异常时 Snackbar 显示"保存失败：xxx" | ✅ 通过 |
| 14 | `deleteFailure_showsErrorMessage` | 删除异常时 Snackbar 显示"删除失败：xxx" | ✅ 通过 |

#### 4. 数据绑定测试（5 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 15 | `uiStateRecordsChange_lazyColumnUpdates` | records 变更 → LazyColumn 列表自动刷新 | ✅ 通过 |
| 16 | `uiStateSummaryChange_summaryCardUpdates` | summary 变更 → SummaryCard 金额实时更新 | ✅ 通过 |
| 17 | `uiStateDialogModeChange_dialogShowsAndHides` | dialogMode 变更 → Dialog 显示/隐藏切换 | ✅ 通过 |
| 18 | `multipleExpenseRecords_summaryCalculatesCorrectly` | 多条纯支出记录的汇总计算正确性 | ✅ 通过 |
| 19 | `mixedIncomeAndExpenseRecords_summaryCalculatesCorrectly` | 混合收入/支出记录的汇总计算正确性 | ✅ 通过 |

---

## 步骤 4：运行测试与问题修复

### 4.1 首次运行：5 条用例失败 — 节点匹配重复问题

**问题 A：日期/金额文本在多处重复匹配**（4 条失败）

同一日期文本出现在 DateFilterBar、SummaryCard、EmptyState 三个位置；同一金额文本出现在 SummaryCard 和 RecordItem 中。使用 `substring = true` 匹配时命中多个节点，Compose Test 要求 `assertIsDisplayed()` 只作用于唯一节点。

**修复**：
- `initialState_dateFilterBarShowsToday`：改用精确匹配（`substring = false`）
- `emptyState_displaysZeroSummary`：改为匹配 `"${dateLabel} 结余"` 完整文本 + "收入"/"支出"标签
- `recordsDisplayed_summaryCardUpdatesCorrectly`：精确匹配 + 结余行完整文本辅助定位
- `uiStateSummaryChange_summaryCardUpdates`：改用结余金额断言，避免与 RecordItem 冲突

**问题 B：金额输入框无法定位**（1 条失败）

`onNodeWithText("0.00")` 找不到节点，`OutlinedTextField` 的 placeholder 不在 semantics tree 的可编辑文本中。

**修复**：改用 `hasSetTextAction()` 定位输入框。

### 4.2 二次运行：1 条用例失败 — 输入框定位不够精确

`saveFailure_showsErrorMessage` 中 `hasSetTextAction()` 匹配到 2 个节点（金额输入框和备注输入框都有 SetText 语义动作）。

**修复**：改用 `hasSetTextAction() and hasText("金额", substring = true)` 组合条件精确定位金额输入框。

### 4.3 三次运行：全部通过 ✅

19/19 用例全部通过。

---

## 步骤 5：P0 总结

### 5.1 最终产出

| 产出 | 说明 |
|------|------|
| 测试文件 | `HomeScreenIntegrationTest.kt`（19 条用例） |
| 基础设施 | `FakeAccountRecordDao.kt`（内存 DAO，可复用于后续 P1-P4 测试） |
| 构建配置 | `build.gradle.kts` 中 `packaging` 排除配置、MockK 依赖调整 |
| 版本目录 | `libs.versions.toml` 中 MockK 版本升级到 1.13.13 |

### 5.2 遇到的问题汇总

| # | 问题 | 根因 | 解决方式 |
|---|------|------|---------|
| 1 | META-INF/LICENSE.md 打包冲突 | MockK 传递依赖 JUnit Jupiter 文件重复 | `packaging { excludes }` 排除 |
| 2 | MockK JVMTI Agent .so 对齐不兼容 | MockK 原生库 4KB 对齐 vs API 35 模拟器 16KB 页大小 | 弃用 MockK，改用 Fake DAO |
| 3 | 日期/金额文本节点重复匹配 | DateFilterBar/SummaryCard/EmptyState/RecordItem 复用相同文本 | 改用精确匹配或完整上下文文本定位 |
| 4 | 金额输入框 placeholder 不可达 | `OutlinedTextField` 的 placeholder 不在 semantics tree 可编辑文本中 | 改用 `hasSetTextAction()` 定位 |
| 5 | 双输入框语义匹配不精确 | 金额和备注输入框都有 `SetText` 语义动作 | `hasSetTextAction() and hasText("金额")` 组合条件 |

### 5.3 测试结果

**19/19 用例全部通过 ✅**

- 初始状态测试：4/4 通过
- 用户交互测试：4/4 通过
- 状态流转测试：6/6 通过
- 数据绑定测试：5/5 通过

### 5.4 经验沉淀（供后续 P1-P4 参考）

1. **优先使用 Fake 而非 Mock**：Fake DAO 在 androidTest 中更稳定，不受 JVMTI/模拟器兼容性影响
2. **避免 substring 匹配**：Compose UI 中日期/金额文本常被多个组件复用，优先使用精确匹配或完整上下文文本
3. **输入框定位用组合条件**：`hasSetTextAction()` 单独使用可能匹配多个输入框，需结合 `hasText(label)` 精确筛选
4. **FakeAccountRecordDao 可复用**：P1-P4 测试及阶段三沙箱全链路测试均可复用此 Fake DAO
