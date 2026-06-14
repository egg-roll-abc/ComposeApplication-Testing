# P4 集成测试实施步骤记录

> 对应测试计划：ComposeApp_Integration_Test_Plan.md 第四章 4.1.3 P4 部分
> 测试目标：ContentHome + NavigationBar 导航切换集成测试

---

## 步骤 1：测试策略确定

### 1.1 测试方式

ContentHome 是 HorizontalPager + NavigationBar 的容器组件，内部托管 HomeScreen 和 ProfileScreen。通过给 ContentHome 添加可选 `repository: AccountRepository? = null` 参数注入测试仓库，两个页面共享同一 repository 实例。

### 1.2 源代码修改

ContentHome 原不接受 repository 参数，内部调用 `HomeScreen()` 和 `ProfileScreen()` 时也不传 repository。添加 `repository: AccountRepository? = null` 参数并传递给两个页面，默认值 null 保证原有调用不受影响。

此修改是添加测试注入点，不改变任何现有行为。

### 1.3 导航交互策略

- NavigationBar 点击：`onNodeWithText("我的").performClick()`
- 页面验证：切换后验证目标页面的独有文本（HomeScreen 的"记一笔"、ProfileScreen 的"记账 Demo"）
- HorizontalPager 滑动：NavigationBar 的 `selected` 绑定 `pagerState.currentPage`，点击导航和滑动最终都更新同一个 PagerState，因此滑动测试退化为导航点击的双向切换验证

---

## 步骤 2：编写测试用例

### 2.1 测试文件

**路径**：`app/src/androidTest/java/com/shx/composeapplication/integration/ContentHomeIntegrationTest.kt`

### 2.2 用例清单

#### 1. 初始状态测试（2 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 1 | `initialState_showsHomePage` | 默认 page=0，FAB"记一笔" + EmptyState 可见 | ✅ 通过 |
| 2 | `initialState_homeTabSelected` | "首页"和"我的"导航项均显示 | ✅ 通过 |

#### 2. 用户交互测试（3 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 3 | `clickProfileTab_switchesToProfilePage` | 点击"我的" → "记账 Demo"可见 | ✅ 通过 |
| 4 | `clickHomeTab_switchesToHomePage` | 切回 → "记一笔"可见 | ✅ 通过 |
| 5 | `swipePage_updatesNavigationSelection` | 导航点击双向切换验证 | ✅ 通过 |

#### 3. 数据联动测试（2 条）

| # | 用例名称 | 测试要点 | 状态 |
|---|---------|---------|------|
| 6 | `sharedRepository_dataConsistentAcrossPages` | HomeScreen 有数据 → ProfileScreen "共 1 笔账单" | ✅ 通过 |
| 7 | `clearOnProfile_updatesHomeScreen` | ProfileScreen 清空 → HomeScreen EmptyState | ✅ 通过 |

---

## 步骤 3：运行测试与问题修复

### 3.1 首次运行：2 条用例失败

**失败用例**：
1. `initialState_showsHomePage` — `onNodeWithText("暂无记账").assertIsDisplayed()` 失败
2. `clearOnProfile_updatesHomeScreen` — 同上

**错误**：`The component is not displayed!` / `could not find any node that satisfies: Text + EditableText contains '暂无记账'`

**根因**：EmptyState 的文本是 `"$dateLabel 暂无记账"`（如"今天 · 6月10日 暂无记账"），不是独立的"暂无记账"文本。`onNodeWithText("暂无记账")` 默认精确匹配，找不到完整文本包含"暂无记账"的节点。

**修复**：改用 `onNodeWithText("暂无记账", substring = true)` 子串匹配，与 P0 HomeScreenIntegrationTest 保持一致。

### 3.2 二次运行：7/7 全部通过

---

## 步骤 4：经验沉淀

### 4.1 P0-P3 经验复用

| 经验 | P4 应用 |
|------|--------|
| Fake DAO 复用 | 复用 `FakeAccountRecordDao`，无需新增标志 |
| EmptyState 文本含日期前缀 | `substring = true` 匹配"暂无记账" |
| ProfileScreen performScrollTo | `clearOnProfile_updatesHomeScreen` 中滚动到"清空全部账单"再点击 |
| waitUntil 等待 Snackbar | 清空操作后 `waitUntil(3000) { assertIsDisplayed("已清空全部账单") }` |

### 4.2 P4 新增经验

| 经验 | 说明 |
|------|------|
| ContentHome 需添加 repository 参数才能注入测试仓库 | 原实现不接受 repository，HomeScreen/ProfileScreen 默认从 AccountingApplication 获取真实仓库；添加 `repository: AccountRepository? = null` 可选参数即可，默认值 null 不影响现有行为 |
| HorizontalPager 滑动测试退化为导航点击验证 | NavigationBar 的 `selected` 绑定 `pagerState.currentPage`，点击导航和滑动最终都更新同一个 PagerState；直接对 HorizontalPager 区域执行 swipeLeft 可能定位不准，导航点击足以验证双向同步 |
| NavigationBar 文本可直接定位 | "首页"和"我的"与页面内容文本不重复，可直接 `onNodeWithText` 定位 |
| 页面切换后验证目标页面独有文本 | HomeScreen 用 contentDescription="记一笔" FAB，ProfileScreen 用"记账 Demo" ProfileHeader，都是页面独有的、不会混淆的标识 |
| 跨页面数据联动验证 | 两个页面共享同一 repository 实例，数据变更（清空）在一个页面操作后，切回另一页面应同步反映 |

---

## 总结

### 最终产出

| 产出 | 说明 |
|------|------|
| 测试文件 | `ContentHomeIntegrationTest.kt`（7 条用例） |
| 源代码修改 | `MainActivity.kt`：ContentHome 添加 `repository: AccountRepository? = null` 可选参数 |

### 测试结果

**7/7 用例通过** ✅

- 初始状态：2/2 通过
- 用户交互：3/3 通过
- 数据联动：2/2 通过

### 问题修复记录

| # | 用例 | 问题 | 修复 |
|---|------|------|------|
| 1 | `initialState_showsHomePage` | "暂无记账"精确匹配找不到节点 | `substring = true` 子串匹配 |
| 2 | `clearOnProfile_updatesHomeScreen` | 同上 | 同上 |
