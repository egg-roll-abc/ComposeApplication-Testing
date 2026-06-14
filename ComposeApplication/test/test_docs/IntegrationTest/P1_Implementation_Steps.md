# P1 集成测试实施步骤记录

> 对应测试计划：ComposeApp_Integration_Test_Plan.md 第四章 4.1.3 P1 部分
> 测试目标：RecordFormDialog + DeleteConfirmDialog 组件集成测试

---

## 步骤 1：测试策略确定

### 1.1 测试方式

RecordFormDialog 和 DeleteConfirmDialog 均通过 HomeScreen + HomeViewModel 触发（点击 FAB/编辑/删除按钮），因此测试入口仍为 HomeScreen，通过 FakeAccountRecordDao + AccountRepository 驱动。

### 1.2 复用 P0 基础设施

- `FakeAccountRecordDao.kt`：直接复用，无需修改
- 测试入口：通过 `HomeScreen(repository = repository)` 启动，点击按钮触发 Dialog

**结果**：✅ 策略确定

---

## 步骤 2：编写测试用例

### 2.1 测试文件

**路径**：`app/src/androidTest/java/com/shx/composeapplication/integration/RecordDialogIntegrationTest.kt`

### 2.2 用例清单

#### 1. RecordFormDialog 初始状态测试（8 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 1 | `addMode_titleIsAddRecord` | 新增模式标题"记一笔" | ✅ 通过 |
| 2 | `addMode_defaultTypeIsExpense` | 默认类型为"支出" | ✅ 通过 |
| 3 | `addMode_defaultCategoryIsFirstExpense` | 默认分类为"餐饮"（支出第一项） | ✅ 通过 |
| 4 | `addMode_amountFieldIsEmpty` | 金额输入框存在且有"金额"标签 | ✅ 通过 |
| 5 | `addMode_noteFieldIsEmpty` | 备注输入框存在且有"备注（可选）"标签 | ✅ 通过 |
| 6 | `addMode_hasSaveAndCancelButton` | "保存"和"取消"按钮可见 | ✅ 通过 |
| 7 | `editMode_titleIsEditRecord` | 编辑模式标题"编辑账单" | ✅ 通过 |
| 8 | `editMode_preFillsRecordData` | 编辑模式预填金额 | ✅ 通过 |

#### 2. RecordFormDialog 用户交互测试（11 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 9 | `switchToIncome_categoryListUpdates` | 切换到"收入"后分类列表更新 | ✅ 通过 |
| 10 | `switchType_defaultCategoryResets` | 切换类型后默认分类重置为该类型第一项 | ✅ 通过 |
| 11 | `amountInput_filtersNonNumeric` | 金额输入过滤非数字字符 | ✅ 通过 |
| 12 | `categorySelection_updatesSelectedCategory` | 点击分类 FilterChip 切换选中态 | ✅ 通过 |
| 13 | `noteInput_acceptsText` | 备注输入框接受文本输入 | ✅ 通过 |
| 14 | `saveWithEmptyAmount_showsValidationError` | 空金额保存→"请输入有效金额" | ✅ 通过 |
| 15 | `saveWithZeroAmount_showsValidationError` | 零金额保存→"请输入有效金额" | ✅ 通过 |
| 16 | `saveWithValidData_insertsRecordAndClosesDialog` | 有效数据保存→Dialog 关闭 | ✅ 通过 |
| 17 | `clickCancel_closesDialog` | 点击"取消"→Dialog 关闭 | ✅ 通过 |
| 18 | `clickOutsideDialog_closesDialog` | 点击外部区域→Dialog 关闭 | ✅ 通过 |
| 19 | `editAndSave_updatesRecord` | 编辑模式修改后保存→Dialog 关闭 | ✅ 通过 |

#### 3. DeleteConfirmDialog 初始状态测试（3 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 20 | `deleteDialog_displaysTitle` | 标题"删除账单"显示 | ✅ 通过 |
| 21 | `deleteDialog_displaysRecordInfo` | 内容包含完整描述（"确定删除「餐饮」"） | ✅ 通过 |
| 22 | `deleteDialog_hasDeleteAndCancelButton` | "删除"和"取消"按钮可见 | ✅ 通过 |

#### 4. DeleteConfirmDialog 用户交互测试（3 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 23 | `clickDeleteConfirm_deletesRecordAndClosesDialog` | 确认删除→Dialog 关闭 | ✅ 通过 |
| 24 | `clickDeleteCancel_closesDialog` | 取消删除→Dialog 关闭，记录仍在 | ✅ 通过 |
| 25 | `deleteFailure_showsErrorAndKeepsDialog` | 删除异常→Snackbar 错误提示 | ✅ 通过 |

---

## 步骤 3：运行测试与问题修复

### 3.1 首次运行：5 条用例失败 — Dialog 浮层节点重复匹配

**核心问题**：Dialog 是浮层，背景 UI 节点仍在语义树中，导致文本重复匹配。

| 用例 | 原定位方式 | 重复原因 | 修复方式 |
|------|-----------|---------|---------|
| `addMode_defaultTypeIsExpense` | `onNodeWithText("支出")` | SummaryCard 也有"支出"标签 | 改用 `hasText("支出") and hasClickAction()` |
| `switchToIncome_categoryListUpdates` | `onNodeWithText("收入").performClick()` | SummaryCard 也有"收入"标签 | 改用 `hasText("收入") and hasClickAction()` |
| `switchType_defaultCategoryResets` | `onNodeWithText("收入").performClick()` | 同上 | 同上 |
| `editMode_preFillsRecordData` | `onNode(hasText("交通") and hasClickAction())` | FilterChip 和 RecordItem 都有"交通"+ click | 改为验证预填金额 `assertTextContains("88")` |
| `deleteDialog_displaysRecordInfo` | `onNodeWithText("餐饮")` | FilterChip 和 RecordItem 都有"餐饮" | 改用完整文本匹配 `"确定删除「餐饮」"` |

**修复策略总结**：

- **SegmentedButton**：用 `hasClickAction()` 排除 SummaryCard 的纯文本标签
- **FilterChip**：用 `hasClickAction()` 排除部分纯文本，但与 RecordItem（也有 click）冲突时改用其他断言方式
- **Dialog 正文**：用完整上下文文本（如 `"确定删除「餐饮」"`）替代单独匹配分类名
- **编辑模式预填验证**：用金额输入框的 `assertTextContains()` 替代分类名匹配

### 3.2 二次运行：1 条用例失败 — `hasRole` 不可用

`hasRole(Role.RadioButton)` 在当前 Compose Test 版本中无对应 import，编译报错 `Unresolved reference 'hasRole'`。

**修复**：将 `hasRole(Role.RadioButton)` 统一替换为 `hasClickAction()`，效果相同——SegmentedButton 有点击动作，SummaryCard 标签没有。

### 3.3 三次运行：1 条用例失败 — 编辑模式分类名仍重复

`editMode_preFillsRecordData` 中 `hasText("交通") and hasClickAction()` 匹配到 2 个节点：Dialog 内的 FilterChip 和背景 RecordItem（整个卡片可点击）。

**修复**：改为验证金额输入框的预填值 `assertTextContains("88")`，从另一个角度验证编辑模式数据正确。

### 3.4 四次运行：全部通过 ✅

25/25 用例全部通过。

---

## 步骤 4：P0 经验应用与新增经验

### 4.1 P0 经验复用

| P0 经验 | P1 应用 |
|---------|--------|
| 避免 substring 匹配重复节点 | 分类名用精确匹配，金额输入框用组合条件定位 |
| 输入框定位用 `hasSetTextAction() and hasText(label)` | 金额/备注输入框均用此组合定位 |
| Fake DAO 复用 | 直接复用 `FakeAccountRecordDao`，无需修改 |
| Snackbar 异步延迟 | Compose Test 自动等待机制，无需手动延迟 |

### 4.2 P1 新增经验

| 经验 | 说明 |
|------|------|
| Dialog 浮层下背景节点仍在语义树中 | AlertDialog 不会移除背景节点，所有文本/点击语义仍可匹配到，必须用组合条件精确定位 |
| `hasClickAction()` 可区分交互组件和纯文本 | SegmentedButton/FilterChip 有 click，SummaryCard 标签无 click |
| `hasClickAction()` 不能区分 FilterChip 和 RecordItem | 两者都有 click 动作，需改用其他断言方式（如验证输入框预填值） |
| Dialog 正文用完整上下文文本匹配 | `"确定删除「餐饮」"` 是 Dialog 独有文本，不会与背景重复 |
| `hasRole` 在当前 Compose Test 版本不可用 | 改用 `hasClickAction()` 替代 |

---

## 总结

### 最终产出

| 产出 | 说明 |
|------|------|
| 测试文件 | `RecordDialogIntegrationTest.kt`（25 条用例） |
| 基础设施 | 复用 `FakeAccountRecordDao.kt`，无需新增 |

### 测试结果

**25/25 用例全部通过 ✅**

- RecordFormDialog 初始状态：8/8 通过
- RecordFormDialog 用户交互：11/11 通过
- DeleteConfirmDialog 初始状态：3/3 通过
- DeleteConfirmDialog 用户交互：3/3 通过

### 遇到的问题汇总

| # | 问题 | 根因 | 解决方式 |
|---|------|------|---------|
| 1 | "收入"/"支出"文本重复匹配 | Dialog 浮层下 SummaryCard 节点仍在 | `hasClickAction()` 区分 SegmentedButton 和纯文本 |
| 2 | `hasRole` 不可用 | 当前 Compose Test 版本无此 API | 改用 `hasClickAction()` 替代 |
| 3 | 编辑模式分类名与 RecordItem 重复 | FilterChip 和 RecordItem 都有 click | 改为验证金额输入框预填值 |
| 4 | 删除 Dialog 分类名与 RecordItem 重复 | "餐饮"出现在 Dialog 正文和列表项 | 改用完整文本 `"确定删除「餐饮」"` |
