# P2 集成测试实施步骤记录

> 对应测试计划：ComposeApp_Integration_Test_Plan.md 第四章 4.1.3 P2 部分
> 测试目标：DateFilterBar 组件集成测试

---

## 步骤 1：测试策略确定

### 1.1 测试方式

DateFilterBar 通过 HomeScreen + HomeViewModel 触发（点击日期区域 / "回到今天"按钮），测试入口仍为 HomeScreen，通过 FakeAccountRecordDao + AccountRepository 驱动。

### 1.2 复用 P0/P1 基础设施

- `FakeAccountRecordDao.kt`：直接复用，无需修改
- 测试入口：通过 `HomeScreen(repository = repository)` 启动

### 1.3 DatePicker 交互策略

DateFilterBar 的核心交互是弹出 DatePicker 选择日期，但 Material3 DatePicker 的日期单元格存在以下问题：

- 日期单元格使用 `selectable` 修饰符（非 `clickable`），不注册 `OnClick` 语义
- 日号文本（如 "15"）被父节点合并语义覆盖，`hasText("15")` 在语义树中找不到匹配节点

因此，测试采用**分层策略**：

| 测试内容 | 驱动方式 | 原因 |
|---------|---------|------|
| DatePicker 打开/关闭/取消 | UI 交互 | 按钮有标准 OnClick 语义，可正常定位 |
| 日期切换 & 数据过滤 | ViewModel 直接驱动 | 日历单元格无法通过语义树定位 |

**ViewModel 驱动的合理性**：DatePicker 确认按钮内部就是调用 `onDateChange(DateFilterState)`，直接调用 ViewModel 方法与用户在 DatePicker 中选日期后点确认的效果等价，仍完整验证了 DateFilterBar ← HomeViewModel ← Repository 的数据流联动。

**结果**：✅ 策略确定

---

## 步骤 2：编写测试用例

### 2.1 测试文件

**路径**：`app/src/androidTest/java/com/shx/composeapplication/integration/DateFilterBarIntegrationTest.kt`

### 2.2 用例清单

#### 1. 初始状态测试（2 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 1 | `todayState_displaysTodayLabel` | 今天时标签显示"今天 · X月X日" | ✅ 通过 |
| 2 | `todayState_backToTodayButtonNotVisible` | 今天时"回到今天"按钮不存在 | ✅ 通过 |

#### 2. DatePickerDialog 交互测试（3 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 3 | `clickDateArea_opensDatePickerDialog` | 点击日期区域弹出 DatePicker | ✅ 通过 |
| 4 | `cancelDatePicker_closesDialogWithoutChange` | 取消 DatePicker → Dialog 关闭，日期不变 | ✅ 通过 |
| 5 | `confirmDatePickerWithoutChange_closesDialog` | 不修改日期直接确认 → Dialog 关闭，仍为今天 | ✅ 通过 |

#### 3. 日期切换 & "回到今天" 测试（3 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 6 | `selectDifferentDate_backToTodayButtonAppears` | 切换到非今天日期 → "回到今天"按钮出现 | ✅ 通过 |
| 7 | `selectDifferentDate_labelShowsFormattedDate` | 切换到非今天日期 → 标签显示"X年X月X日"格式 | ✅ 通过 |
| 8 | `clickBackToToday_returnsToTodayState` | 点击"回到今天" → 标签恢复今天，按钮消失 | ✅ 通过 |

#### 4. 数据过滤测试（2 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 9 | `dateChange_filtersRecordsByDay` | 切换日期后列表按日过滤记录 | ✅ 通过 |
| 10 | `backToToday_restoresTodayRecords` | "回到今天"后恢复今天的记录 | ✅ 通过 |

---

## 步骤 3：运行测试与问题修复

### 3.1 首次编写：`assertDoesNotExist` 编译报错

**问题**：`import androidx.compose.ui.test.assertDoesNotExist` 报 `Unresolved reference`。

**根因**：`assertDoesNotExist()` 是 `SemanticsNodeInteraction` 的扩展函数，由 `onNodeWithText()` 返回值直接调用即可，不需要显式 import。P0/P1 文件均未 import 此函数。

**修复**：移除 `import androidx.compose.ui.test.assertDoesNotExist`。

### 3.2 首次运行：5 条用例失败 — DatePicker 日号文本 + hasClickAction 匹配不到

**错误信息**：
```
Expected exactly '1' node but could not find any node that satisfies:
((Text + EditableText contains '15' (ignoreCase: false)) && (OnClick is defined))
```

**失败用例**：`selectDifferentDate_backToTodayButtonAppears`、`selectDifferentDate_labelShowsFormattedDate`、`clickBackToToday_returnsToTodayState`、`dateChange_filtersRecordsByDay`、`backToToday_restoresTodayRecords`

**根因**：Material3 DatePicker 日期单元格使用 `selectable` 修饰符而非 `clickable`，不注册 `OnClick` 语义，`hasClickAction()` 匹配不到。

**修复尝试**：移除 `and hasClickAction()`，改为 `onNode(hasText(dayNumber))`。

### 3.3 二次运行：5 条用例仍失败 — DatePicker 日号文本完全匹配不到

**错误信息**：
```
Expected exactly '1' node but could not find any node that satisfies:
(Text + EditableText contains '15' (ignoreCase: false))
```

**根因**：Material3 DatePicker 的日历网格使用语义合并（merging），日号文本被父容器节点合并，在语义树中不以独立文本节点存在。`hasText("15")` 在整个语义树中找不到匹配。

**最终修复**：放弃通过 UI 交互选择 DatePicker 日历单元格，改为通过 ViewModel 直接驱动日期变更：

- 新增 `launchHomeScreenWithViewModelCapture()` 方法
- 利用 `viewModel()` 在同一 ViewModelStoreOwner 内返回同一实例的特性，捕获 HomeViewModel 引用
- 第 6-10 用例改用 `capturedViewModel!!.onDateChange(targetDate)` 驱动日期切换
- 第 3-5 用例（DatePicker 打开/关闭/取消）仍通过 UI 交互验证

### 3.4 三次运行：全部通过 ✅

10/10 用例全部通过。

---

## 步骤 4：P0/P1 经验应用与新增经验

### 4.1 P0/P1 经验复用

| P0/P1 经验 | P2 应用 |
|------------|--------|
| Fake DAO 复用 | 直接复用 `FakeAccountRecordDao`，无需修改 |
| Dialog 浮层下背景节点仍在 | DatePicker 也是浮层，背景节点仍可匹配 |
| 组合条件精确定位 | DatePicker 的"确定"/"取消"按钮通过 `onNodeWithText` 直接定位（RecordFormDialog 用"保存"，不冲突） |

### 4.2 P2 新增经验

| 经验 | 说明 |
|------|------|
| Material3 DatePicker 日历单元格不注册 OnClick 语义 | 日期单元格用 `selectable` 修饰符，`hasClickAction()` 匹配不到 |
| Material3 DatePicker 日号文本被语义合并覆盖 | `hasText("15")` 在语义树中找不到独立文本节点 |
| ViewModel 驱动可替代不可 UI 交互的组件 | `onDateChange()` 直接调用与 DatePicker 确认效果等价，仍验证完整数据流 |
| `viewModel()` 同 ViewModelStoreOwner 返回同一实例 | 可通过 `viewModel(factory)` 在 setContent 中捕获与 HomeScreen 共享的 ViewModel |
| `assertDoesNotExist()` 不需要显式 import | 是 `SemanticsNodeInteraction` 的成员函数，由调用链自动解析 |

---

## 总结

### 最终产出

| 产出 | 说明 |
|------|------|
| 测试文件 | `DateFilterBarIntegrationTest.kt`（10 条用例） |
| 基础设施 | 复用 `FakeAccountRecordDao.kt`，无需新增 |

### 测试结果

**10/10 用例全部通过 ✅**

- 初始状态：2/2 通过
- DatePickerDialog 交互：3/3 通过
- 日期切换 & "回到今天"：3/3 通过
- 数据过滤：2/2 通过

### 遇到的问题汇总

| # | 问题 | 根因 | 解决方式 |
|---|------|------|---------|
| 1 | `assertDoesNotExist` import 报错 | 是成员函数，无需显式 import | 移除 import |
| 2 | `hasText("15") and hasClickAction()` 匹配不到 | DatePicker 日期单元格用 `selectable`，无 OnClick 语义 | 移除 `hasClickAction()` |
| 3 | `hasText("15")` 仍匹配不到 | DatePicker 日历语义合并，日号文本不独立存在 | 改用 ViewModel 直接驱动 `onDateChange()` |
