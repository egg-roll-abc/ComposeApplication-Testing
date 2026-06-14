# P5 阶段二实施步骤记录：沙箱全链路集成测试

> 对应测试计划：ComposeApp_Integration_Test_Plan.md 第四章 4.2 沙箱全链路测试
> 测试目标：使用 FakeAccountRecordDao 构建离线沙箱环境，通过 ContentHome(repository) 注入，模拟完整用户操作链路，验证 APP 整体集成闭环能力

---

## 步骤 1：测试策略确定

### 1.1 沙箱环境

与第一阶段组件化测试一致，使用 `FakeAccountRecordDao` + 真实 `AccountRepository` 构建离线沙箱。通过 `ContentHome(repository)` 注入，HomeScreen 和 ProfileScreen 共享同一 repository 实例。

### 1.2 沙箱入口

使用 `ContentHome` 作为 `setContent` 的根组件，而非单独启动 HomeScreen 或 ProfileScreen。这样可以：
- 同时覆盖两个页面的交互
- 验证 NavigationBar 页面切换
- 验证跨页面数据联动

### 1.3 日期切换策略

与 P2 DateFilterBarIntegrationTest 一致：DatePicker 打开/关闭通过 UI 交互验证，日期变更通过 `ViewModel.onDateChange()` 驱动。原因：Material3 DatePicker 日历单元格不注册 OnClick 语义。

### 1.4 异常注入策略

利用 FakeDao 的 `insertShouldThrow` / `deleteAllShouldThrow` 标志位模拟数据库异常，验证异常容错流程。

---

## 步骤 2：编写测试用例

### 2.1 测试文件

**路径**：`app/src/androidTest/java/com/shx/composeapplication/integration/SandboxFullChainTest.kt`

### 2.2 用例清单

#### 链路1：新增记账全流程（1 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 1 | `chain1_addRecordFullFlow` | 空状态→FAB→填金额+选分类→保存→列表刷新+SummaryCard更新 | ✅ 通过 |

#### 链路2：编辑与删除全流程（1 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 2 | `chain2_editAndDeleteFullFlow` | 点编辑→修改金额/分类→保存→列表更新→点删除→确认→记录移除+EmptyState | ✅ 通过 |

#### 链路3：日期筛选全流程（1 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 3 | `chain3_dateFilterFullFlow` | 今天数据→打开DatePicker→取消不变→切换日期→列表按日过滤→"回到今天"→恢复当天数据 | ✅ 通过 |

#### 链路4：页面切换+统计联动+清空全流程（1 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 4 | `chain4_pageSwitchAndClearFullFlow` | HomeScreen→切ProfileScreen→统计一致→清空→Snackbar→切回HomeScreen空状态 | ✅ 通过 |

#### 链路5：异常容错流程（2 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 5 | `chain5_saveFailure_showsSnackbarError` | `insertShouldThrow=true`→保存→Snackbar"保存失败"→Dialog未关闭+应用不崩溃 | ✅ 通过 |
| 6 | `chain5_clearAllFailure_showsSnackbarError` | `deleteAllShouldThrow=true`→清空→Snackbar"清空失败"→数据未清空+应用不崩溃 | ✅ 通过 |

---

## 步骤 3：运行测试与问题修复

### 3.1 首次运行：4 条用例失败

**失败 A：chain1 line 94 — "支出"找到 2 个节点**

**根因**：RecordFormDialog 中 SegmentedButton 显示"支出"文本，同时 SummaryCard 也有"支出"标签。`onNodeWithText("支出")` 匹配到两个节点。

**修复**：改为 `onNode(hasText("支出") and hasClickAction())`，用 `hasClickAction` 精确定位 SegmentedButton，排除不可点击的 SummaryItem 标签。与 P1 RecordDialogIntegrationTest 的经验一致。

**失败 B：chain1 line 99 / chain5 line 337 — "0.00" 找不到节点**

**根因**：RecordFormDialog 的 `OutlinedTextField` 使用 `placeholder = { Text("0.00") }`，placeholder 文本不注册 `EditableText` 语义，`onNodeWithText("0.00")` 找不到。

**修复**：改为 `onNode(hasSetTextAction() and hasText("金额", substring = true))`，用 `hasSetTextAction` 定位可输入字段，结合 label 文本"金额"精确筛选。与 P0/P1 的输入框定位策略一致。

**失败 C：chain2 line 168 — 编辑按钮不在可视区域**

**根因**：RecordItem 的编辑/删除 IconButton 在卡片右侧，不在可视区域内。

**修复**：在 `performClick()` 前添加 `performScrollTo()` 滚动到可见区域。

**失败 D：chain4 line 284 — "￥8,000.00" 找到 2 个节点**

**根因**：ProfileScreen 有两个 StatsCard（"累计统计"和"当月统计"），当数据全部是当月时，两个卡片的收入金额相同，`onNodeWithText` 匹配到两个节点。

**修复**：首次尝试改为断言不重复的结余金额 "￥7,900.00"，但累计统计和当月统计的结余也相同，仍然匹配 2 个节点。最终改用 `onAllNodesWithText("￥7,900.00")[0].assertIsDisplayed()` 取第一个验证存在即可，配合"累计统计"标题断言间接验证数据正确性。

### 3.2 二次运行：1 条用例失败

**失败：chain2 line 172 — SummaryCard 更新后金额不匹配**

**根因**：编辑模式下金额输入框预填 "50"，使用 `performTextInput("200")` 是**追加**操作，结果为 "50200" 而非 "200"。

**修复**：改为 `performTextReplacement("200")`，替换整个文本而非追加。`performTextInput` 适用于空输入框（如新增模式），`performTextReplacement` 适用于已有内容的输入框（如编辑模式）。

### 3.3 三次运行：6/6 全部通过 ✅

---

## 步骤 4：经验沉淀

### 4.1 P0-P4 经验复用

| 经验 | P5 应用 |
|------|--------|
| Fake DAO 复用 | 复用 `FakeAccountRecordDao`，新增 `insertShouldThrow` / `deleteAllShouldThrow` 标志位用于异常注入 |
| 输入框定位用 `hasSetTextAction` + `hasText("金额")` 组合 | chain1/chain5 金额输入均使用此组合定位 |
| EmptyState 文本含日期前缀 | `substring = true` 匹配"暂无记账" |
| ProfileScreen `performScrollTo` | chain4 中"清空全部账单"需滚动到底部 |
| `waitUntil` 等待 Snackbar | chain4/chain5 异步操作后等待 Snackbar 显示 |
| ViewModel 驱动日期变更 | chain3 通过 `capturedHomeViewModel!!.onDateChange()` 切换日期 |

### 4.2 P5 新增经验

| 经验 | 说明 |
|------|------|
| `performTextInput` vs `performTextReplacement` | `performTextInput` 在已有文本后**追加**，`performTextReplacement` **替换**整个文本。新增模式用 `performTextInput`（输入框为空），编辑模式用 `performTextReplacement`（需替换预填值） |
| `hasClickAction` 区分同文本可交互/不可交互节点 | 同一文本"支出"出现在 SegmentedButton（可点击）和 SummaryItem（不可点击），`hasText("支出") and hasClickAction()` 精确定位 SegmentedButton |
| `onAllNodesWithText` 处理不可避免的重复文本 | ProfileScreen 的两个 StatsCard（累计/当月）在数据相同时会产生完全相同的金额文本，无法通过文本内容区分。`onAllNodesWithText("金额")[0]` 取第一个验证存在即可，配合标题断言间接验证 |
| 全链路测试入口用 ContentHome | 以 ContentHome 为 `setContent` 根组件，可同时覆盖两个页面的交互、NavigationBar 切换、跨页面数据联动 |
| `DisposableEffect` 捕获 ViewModel | 通过 `viewModel()` + `DisposableEffect` 在 `setContent` 中捕获 HomeViewModel 实例，与 HomeScreen 内部使用同一实例，可直接驱动日期变更 |
| `import onNode` 不需要 | `onNode(matcher)` 是 `ComposeTestRule` 上的方法，通过 `composeTestRule.onNode(...)` 调用，不需要单独导入 |

### 4.3 问题修复记录

| # | 链路 | 问题 | 根因 | 修复 |
|---|------|------|------|------|
| 1 | chain1 | "支出"匹配 2 节点 | SegmentedButton + SummaryItem 同文本 | `hasText("支出") and hasClickAction()` |
| 2 | chain1/chain5 | "0.00" 找不到 | placeholder 不注册 EditableText | `hasSetTextAction() and hasText("金额")` |
| 3 | chain2 | 编辑按钮不可见 | IconButton 不在可视区域 | `performScrollTo()` |
| 4 | chain4 | "￥8,000.00"匹配 2 节点 | 累计/当月 StatsCard 金额重复 | `onAllNodesWithText("金额")[0]` |
| 5 | chain2 | 编辑后金额为 "50200" | `performTextInput` 是追加而非替换 | `performTextReplacement("200")` |
| 6 | chain4 | "￥7,900.00"匹配 2 节点 | 累计/当月结余也相同 | `onAllNodesWithText("￥7,900.00")[0]` |

---

## 总结

### 最终产出

| 产出 | 说明 |
|------|------|
| 测试文件 | `SandboxFullChainTest.kt`（6 条用例，覆盖 5 条核心业务链路） |

### 测试结果

**6/6 用例通过 ✅**

| 链路 | 用例数 | 通过 |
|------|--------|------|
| 链路1：新增记账全流程 | 1 | ✅ |
| 链路2：编辑与删除全流程 | 1 | ✅ |
| 链路3：日期筛选全流程 | 1 | ✅ |
| 链路4：页面切换+统计联动+清空全流程 | 1 | ✅ |
| 链路5：异常容错流程 | 2 | ✅ |

### 集成测试总体进度

| 阶段 | 状态 | 用例数 | 通过率 |
|------|------|--------|--------|
| 第一阶段：组件化集成测试 | ✅ 完成 | 76 | 98.7%（1条@Ignore） |
| 第二阶段：沙箱全链路测试 | ✅ 完成 | 6 | 100% |
| 第三阶段：问题复盘 & 测试报告 | ❌ 未开始 | — | — |
