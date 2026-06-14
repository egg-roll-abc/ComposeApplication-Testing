# ComposeApplication 集成测试落地计划


---

## 一、测试计划概述

### 1.1 测试对象

基于 Android Jetpack Compose 开发的本地记账应用（ComposeApplication），实际架构为 MVVM 分层：

| 层级 | 实际组件 | 说明 |
|------|---------|------|
| UI层 | `MainActivity` → `ContentHome`（HorizontalPager） → `HomeScreen` / `ProfileScreen` | 单 Activity，双页面通过底部导航栏 + HorizontalPager 切换 |
| ViewModel层 | `HomeViewModel`、`ProfileViewModel` | 均通过 `factory(repository)` 构造，接受 `AccountRepository` 注入 |
| Repository层 | `AccountRepository` | 封装 `AccountRecordDao`，纯本地数据，**无远程数据源** |
| 数据层 | Room 本地数据库（`AppDatabase` + `AccountRecordEntity`） | 单表 `account_records`，无网络请求 |
| 工具层 | `DateFilterUtils`、`FormatUtils`、`AmountInputFilter` | 日期筛选、金额格式化、输入过滤 |
| DI方式 | 手动注入：`AccountingApplication` 懒加载创建 `AccountRepository` | 无 Hilt/Koin 等框架 |

**关键可注入点**：`HomeScreen(repository?)` 和 `ProfileScreen(repository?)` 均接受可选 `AccountRepository?` 参数，默认从 `AccountingApplication` 获取，测试时可直接传入 Fake/Mock 仓库。

### 1.2 测试核心目标

- 验证 Compose UI 组件与 ViewModel 的**集成联动有效性**，确保状态流转、用户交互的稳定性；
- 锁定 ViewModel ↔ Repository、Repository ↔ DAO 层间接口契约，避免迭代导致的层级兼容问题；
- 验证核心记账业务全链路闭环，模拟真实用户使用场景，保障线上可用性。

### 1.3 测试整体流程

**组件化集成测试（主力） → 沙箱全链路测试（验收）**

工具组合：**Compose Test（主力UI测试） + Fake 沙箱（数据模拟） + JUnit**

---

## 二、测试范围与排除范围

### 2.1 测试覆盖范围

1. **UI组件集成**：HomeScreen、ProfileScreen、DateFilterBar、RecordFormDialog、DeleteConfirmDialog 的展示、交互、状态切换；
2. **业务层集成**：页面与 ViewModel 的双向数据绑定、事件分发、状态更新；
3. **层级契约集成**：ViewModel ↔ Repository、Repository ↔ DAO 的接口调用与数据传输；
4. **全链路流程集成**：记账核心业务路径闭环（新增→查看→编辑→删除→统计）。

### 2.2 排除范围

- 底层 SDK 系统适配、机型兼容（专项测试覆盖）；
- 纯单元逻辑计算（由单元测试覆盖，如 `DateFilterUtils`、`FormatUtils`、`AmountInputFilter` 的纯函数已在单元测试中覆盖）；
- 第三方 SDK 底层源码逻辑（仅测试自身业务调用逻辑）；
- 远程数据源/网络请求相关（本项目无远程数据源，不存在此类场景）；
- 登录验证/权限校验相关（本项目无登录和权限系统）。

---

## 三、测试环境与工具栈

### 3.1 测试环境

- 运行设备：Android 模拟器/真机（API 24+，minSdk = 24）；
- 测试环境：沙箱模拟环境（Fake 数据源，本项目无网络依赖，沙箱仅需替换 Repository 层）；
- 编译环境：Android Studio 稳定版、Gradle 适配版本。

### 3.2 核心测试工具

| 测试类型 | 使用工具 | 用途说明 |
|---------|---------|---------|
| Compose UI 组件测试 | Compose Test JUnit4 | 主力 UI 测试，适配 Compose 声明式 UI，测试组件展示、状态、交互 |
| 依赖模拟 | MockK | Mock Repository 层，聚焦当前层级集成逻辑 |
| 层间契约测试 | JUnit + MockK | 校验各层级接口入参、出参、异常返回规范性 |
| 全链路沙箱测试 | FakeAccountRepository + JUnit | 脱离真实数据库，完成端到端闭环验收 |
| 补充流程测试（可选） | Espresso | 已引入但价值有限，仅在需要验证 Activity 级别行为时补充使用 |

### 3.3 前置依赖补充

当前 `build.gradle.kts` 缺少 MockK 依赖，需在执行前添加：

```kotlin
androidTestImplementation("io.mockk:mockk-android:1.13.8")
testImplementation("io.mockk:mockk:1.13.8")
```

> `compose-ui-test-junit4`、`compose-ui-test-manifest`、`espresso-core` 已在 build.gradle.kts 中引入，无需额外操作。

---

## 四、分阶段详细测试方案

### 4.1 第一阶段：组件化集成测试

#### 4.1.1 测试思路

以**单个屏幕/页面为最小集成单元**，集成「Compose UI 页面 + 对应 ViewModel」，Mock Repository 及以下底层依赖，专注测试页面与业务层的联动逻辑。

#### 4.1.2 测试优先级（修正版：由核心到外围）

| 优先级 | 测试目标 | 理由 |
|--------|---------|------|
| P0 | HomeScreen + HomeViewModel | 核心页面，覆盖日期筛选、账单列表展示、增删改全流程 |
| P1 | RecordFormDialog + DeleteConfirmDialog | 核心交互入口，表单校验、分类切换、金额输入过滤 |
| P2 | DateFilterBar 组件 | 独立日期筛选组件，日期选择器交互 |
| P3 | ProfileScreen + ProfileViewModel | 统计展示、清空操作、关于弹窗 |
| P4 | ContentHome + NavigationBar | 底部导航切换、HorizontalPager 页面滑动 |

#### 4.1.3 各页面必测用例

##### P0：HomeScreen + HomeViewModel

**1. 初始状态测试**
- 空数据态：EmptyState 正确显示（"今天 · X月X日 暂无记账"、"点击右下角 + 记一笔"）；
- SummaryCard 展示：结余=0.00，收入=0.00，支出=0.00；
- DateFilterBar 默认选中今天，日期标签显示"今天 · X月X日"；
- FAB 按钮可见。

**2. 用户交互测试**
- 点击 FAB → 弹出 RecordFormDialog（新增模式，标题"记一笔"）；
- 点击记录卡片编辑按钮 → 弹出 RecordFormDialog（编辑模式，标题"编辑账单"，预填数据）；
- 点击记录删除按钮 → 弹出 DeleteConfirmDialog；
- LazyColumn 列表滑动正常。

**3. 状态流转测试**
- 新增记录后：列表刷新显示新记录，SummaryCard 收入/支出/结余更新；
- 编辑记录后：列表中对应记录内容更新，SummaryCard 重新计算；
- 删除记录后：列表移除该记录，SummaryCard 重新计算；
- 删除最后一条记录后：列表消失，EmptyState 显示；
- 金额无效（空/0/负数）→ errorMessage 设为"请输入有效金额"，Snackbar 显示；
- 分类为空 → errorMessage 设为"请选择分类"，Snackbar 显示；
- 保存/删除操作异常 → errorMessage 显示"保存失败：xxx"/"删除失败：xxx"，Snackbar 显示。

**4. 数据绑定测试**
- ViewModel 的 `uiState.records` 变更 → LazyColumn 列表自动刷新；
- ViewModel 的 `uiState.summary` 变更 → SummaryCard 金额实时更新；
- ViewModel 的 `uiState.dialogMode` 变更 → Dialog 显示/隐藏切换；
- ViewModel 的 `uiState.errorMessage` 变更 → Snackbar 弹出并自动消失。

##### P1：RecordFormDialog + DeleteConfirmDialog

**1. RecordFormDialog - 初始状态测试**
- 新增模式：标题"记一笔"，金额为空，类型默认"支出"，分类默认第一个支出分类，备注为空；
- 编辑模式：标题"编辑账单"，预填已有记录的金额、类型、分类、备注。

**2. RecordFormDialog - 用户交互测试**
- 收入/支出 SegmentedButton 切换 → 分类列表切换（支出8项/收入5项），默认选中第一项；
- 金额输入：仅数字和小数点，最多10位整数+2位小数（AmountInputFilter 过滤）；
- 分类 FilterChip 选择 → 选中态切换；
- 备注输入正常；
- 点击"保存" → 触发 `onConfirm(localForm)`；
- 点击"取消"/外部区域 → 触发 `onDismiss()`。

**3. DeleteConfirmDialog - 初始状态测试**
- 标题"删除账单"，内容显示"确定删除「分类名」￥金额 这条记录吗？"。

**4. DeleteConfirmDialog - 用户交互测试**
- 点击"删除" → 触发 `onConfirm()`；
- 点击"取消"/外部区域 → 触发 `onDismiss()`。

##### P2：DateFilterBar 组件

**1. 初始状态测试**
- 选中今天时：日期标签显示"今天 · X月X日"，**不显示**"回到今天"按钮；
- 选中非今天时：日期标签显示"X年X月X日"，**显示**"回到今天"按钮。

**2. 用户交互测试**
- 点击日期区域 → 弹出 DatePickerDialog；
- DatePicker 选中日期并确认 → 触发 `onDateChange`，传入正确的 `DateFilterState`；
- DatePicker 取消 → 关闭对话框，不触发 `onDateChange`；
- 点击"回到今天" → 触发 `onTodayClick`。

##### P3：ProfileScreen + ProfileViewModel

**1. 初始状态测试**
- ProfileHeader 显示"记账 Demo"、"本地存储 · 隐私安全"；
- 累计统计卡片：收入、支出、结余均为 0.00，"共 0 笔账单"；
- 本月统计卡片：收入、支出、结余均为 0.00；
- 无 TopExpenseCard（数据为空时不显示）；
- SettingsCard：三项设置（数据存储、关于、清空全部账单）。

**2. 用户交互测试**
- 点击"关于" → 弹出关于弹窗（"关于记账 Demo"，含版本信息，"知道了"按钮）；
- 点击"清空全部账单" → 弹出清空确认弹窗（"清空全部账单"，含警告文案）；
- 清空确认 → 调用 `repository.clearAll()`，Snackbar 显示"已清空全部账单"；
- 清空取消 → 关闭弹窗，不调用 `clearAll()`。

**3. 状态流转测试**
- 有数据时：累计统计和本月统计正确计算，TopExpenseCard 显示支出 Top5 分类及进度条；
- 清空后：统计数据归零，TopExpenseCard 消失，账单计数归零；
- `clearAll()` 异常 → Snackbar 显示"清空失败：xxx"。

**4. 数据绑定测试**
- `uiState.allTimeSummary` 变更 → 累计统计卡片更新；
- `uiState.monthSummary` 变更 → 本月统计卡片更新；
- `uiState.topExpenseCategories` 变更 → TopExpenseCard 显示/隐藏及内容更新；
- `uiState.snackbarMessage` 变更 → Snackbar 弹出并自动消失。

##### P4：ContentHome + NavigationBar

**1. 初始状态测试**
- 默认显示首页（page=0），底部导航"首页"选中（蓝色），"我的"未选中（黑色）；
- NavigationBar 高度 50dp，包含 2 个 NavigationBarItem。

**2. 用户交互测试**
- 点击"我的" → HorizontalPager 滚动到 ProfileScreen，导航选中态切换；
- 点击"首页" → HorizontalPager 滚动到 HomeScreen，导航选中态切换；
- HorizontalPager 手势滑动 → 导航选中态跟随同步。

---

### 4.2 第二阶段：沙箱全链路测试

#### 4.2.1 测试思路

搭建离线沙箱环境，使用 **FakeAccountRepository** 实现 `AccountRepository` 的所有方法（内部用内存列表模拟），屏蔽真实 Room 数据库依赖，模拟完整用户操作链路，验证 APP 整体集成闭环能力。

> 本项目无网络依赖，沙箱仅需替换 Repository 层即可完全隔离。

#### 4.2.2 FakeAccountRepository 设计

```kotlin
class FakeAccountRepository : AccountRepository(FakeDao()) {
    // 方案一：直接继承并重写所有公开方法
    // 方案二：MockK 的 relaxed mock，按需配置返回值
}
```

**注入方式**：HomeScreen/ProfileScreen 均接受 `repository: AccountRepository?` 参数，直接传入 Fake 实例即可。

#### 4.2.3 核心必测业务链路

##### 链路1：新增记账全流程

> 启动 → HomeScreen 空状态 → 点击 FAB → 填写表单 → 保存 → 列表刷新显示新记录

**验证点**：
- 空状态下 EmptyState 正确展示；
- FAB 点击后 RecordFormDialog 弹出，默认选中"支出"类型和第一个分类；
- 输入金额、选择分类后点击"保存"；
- Dialog 关闭，列表出现新记录，SummaryCard 金额更新。

##### 链路2：编辑与删除全流程

> 列表展示记录 → 点击编辑 → 修改金额/分类 → 保存 → 列表更新 → 点击删除 → 确认删除 → 列表移除

**验证点**：
- 编辑按钮点击 → RecordFormDialog 编辑模式弹出，预填原有数据；
- 修改后保存 → Dialog 关闭，列表和 SummaryCard 更新；
- 删除按钮点击 → DeleteConfirmDialog 弹出，显示分类和金额；
- 确认删除 → Dialog 关闭，记录从列表移除，SummaryCard 更新。

##### 链路3：日期筛选全流程

> HomeScreen 默认今天 → 点击日期 → 选择其他日期 → 列表按日过滤 → 点击"回到今天" → 回到当天数据

**验证点**：
- 切换日期后，`observeByDay` 按新日期查询，列表显示对应日期记录；
- 非今天时"回到今天"按钮可见；
- 点击"回到今天"后恢复当天数据。

##### 链路4：页面切换 + 统计联动 + 清空全流程

> HomeScreen → 切换到 ProfileScreen → 统计数据与 HomeScreen 一致 → 清空全部账单 → 返回 HomeScreen 空状态

**验证点**：
- 底部导航切换到"我的"页 → ProfileScreen 展示累计统计和本月统计；
- 统计数据与 HomeScreen 记录一致（收入/支出/结余正确）；
- 点击"清空全部账单" → 确认弹窗 → 确认清空；
- 切换回 HomeScreen → EmptyState 显示，数据已清空。

##### 链路5：异常容错流程

> 保存时数据库异常 → Snackbar 提示 → 清空时数据库异常 → Snackbar 提示

**验证点**：
- FakeRepository 抛出异常时，ViewModel 捕获并设置 errorMessage/snackbarMessage；
- Snackbar 显示错误信息，应用不崩溃；
- 错误信息格式为"保存失败：xxx"/"删除失败：xxx"/"清空失败：xxx"。

---

## 五、测试准入与退出标准

### 5.1 测试准入条件

- 项目核心页面（HomeScreen、ProfileScreen）、ViewModel（HomeViewModel、ProfileViewModel）、Repository、DAO 代码开发完成 ✅（已确认）；
- 层级接口、数据契约定义稳定，无频繁变更 ✅（已确认）；
- 测试依赖环境引入完成 ✅（已确认）；
- Compose Test 已引入 ✅（已确认）。

### 5.2 测试退出标准（集成测试通过）

- 所有页面组件集成测试用例 100% 通过，无 UI 展示、交互、状态流转 bug；
- 核心业务全链路流程跑通（5 条链路），无崩溃、无功能阻断问题；
- 严重、高级 bug 清零，低级 bug 已确认修复或合规留存。

---

## 六、测试执行顺序（落地执行步骤）

| 步骤 | 内容 | 产出物 |
|------|------|--------|
| 1 | 环境搭建：创建 `androidTest` 目录结构，实现 FakeAccountRecordDao | 可编译运行的测试脚手架 |
| 2 | P0 用例：HomeScreen + HomeViewModel 组件化集成测试 | 首页核心测试用例集 |
| 3 | P1 用例：RecordFormDialog + DeleteConfirmDialog 组件集成测试 | 表单与确认弹窗测试用例集 |
| 4 | P2 用例：DateFilterBar 组件集成测试 | 日期筛选测试用例集 |
| 5 | P3 用例：ProfileScreen + ProfileViewModel 组件集成测试 | 个人页测试用例集 |
| 6 | P4 用例：ContentHome + NavigationBar 组件集成测试 | 导航切换测试用例集 |
| 7 | 阶段二：沙箱全链路测试（5 条链路） | 全链路验收用例集 |
| 8 | 问题复盘：记录测试问题、优化测试用例，归档测试报告 | 测试报告 |

---


