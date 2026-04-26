# MyTODOAndroid 实施计划

> **对于代理智能体：** 必须使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务执行此计划。步骤使用复选框 (`- [ ]`) 语法进行跟踪。

**目标：** 构建一个原生安卓 TODO 应用，支持父子任务、多种重复逻辑（包括每 X 天）以及可交互的桌面小组件。

**架构：** 采用 MVVM 架构。数据持久化使用 Room，UI 使用 Jetpack Compose。桌面小组件使用 Jetpack Glance 实现，通过共用 Repository 确保 App 与小组件状态同步。

**技术栈：** Kotlin, Jetpack Compose, Room, Hilt (依赖注入), Jetpack Glance, java.time。

---

### 任务 1: 项目基础结构搭建

**文件：**
- 创建: `build.gradle.kts` (root), `app/build.gradle.kts`
- 创建: `app/src/main/AndroidManifest.xml`
- 创建: `app/src/main/java/com/mytodo/android/MyApplication.kt`

- [ ] **步骤 1: 初始化 Gradle 配置**
配置项目根目录和 app 模块的 build.gradle.kts，添加 Compose, Room, Hilt, Glance 依赖。

- [ ] **步骤 2: 创建 Application 类**
初始化 Hilt 依赖注入。

- [ ] **步骤 3: 提交代码**
```bash
git add .
git commit -m "chore: initial android project structure with dependencies"
```

### 任务 2: 数据模型与 Room 数据库实现

**文件：**
- 创建: `app/src/main/java/com/mytodo/android/data/TodoEntity.kt`
- 创建: `app/src/main/java/com/mytodo/android/data/TodoDao.kt`
- 创建: `app/src/main/java/com/mytodo/android/data/AppDatabase.kt`

- [ ] **步骤 1: 定义 TodoEntity**
包含 id, content, parentId, repeatMode, intervalDays, anchorDate, completed 等字段。

- [ ] **步骤 2: 定义 TodoDao**
实现基本的 CRUD 以及按日期查询今日任务的 SQL 逻辑。

- [ ] **步骤 3: 编写数据库迁移与初始化**

- [ ] **步骤 4: 编写单元测试验证 SQL 逻辑**
特别是复杂的重复逻辑（如每 X 天计算）。

- [ ] **步骤 5: 提交代码**
```bash
git add app/src/main/java/com/mytodo/android/data/
git commit -m "feat: room database and data models"
```

### 任务 3: 核心业务逻辑 (Repository & ViewModel)

**文件：**
- 创建: `app/src/main/java/com/mytodo/android/data/TodoRepository.kt`
- 创建: `app/src/main/java/com/mytodo/android/ui/MainViewModel.kt`

- [ ] **步骤 1: 实现 TodoRepository**
封装 DAO 操作，提供业务层可用的数据流。

- [ ] **步骤 2: 实现 MainViewModel**
处理 UI 状态，包括今日进度计算、任务勾选逻辑（处理父子任务联动）。

- [ ] **步骤 3: 编写逻辑单元测试**
验证勾选父任务时所有子任务自动标记完成的逻辑。

- [ ] **步骤 4: 提交代码**
```bash
git add app/src/main/java/com/mytodo/android/
git commit -m "feat: repository and viewmodel with business logic"
```

### 任务 4: App 首页 UI 实现 (Compose)

**文件：**
- 创建: `app/src/main/java/com/mytodo/android/ui/MainActivity.kt`
- 创建: `app/src/main/java/com/mytodo/android/ui/components/TodoItem.kt`
- 创建: `app/src/main/java/com/mytodo/android/ui/components/TodoList.kt`

- [ ] **步骤 1: 实现今日标题与进度条**
展示日期、农历信息及完成百分比。

- [ ] **步骤 2: 实现可折叠的任务列表项**
支持父子结构展示，右侧勾选框交互。

- [ ] **步骤 3: 适配小米 13 圆角风格**
使用 `MaterialTheme` 定义形状。

- [ ] **步骤 4: 提交代码**
```bash
git add app/src/main/java/com/mytodo/android/ui/
git commit -m "feat: main activity and compose components"
```

### 任务 5: 新增任务表单 (Bottom Sheet)

**文件：**
- 创建: `app/src/main/java/com/mytodo/android/ui/components/AddTodoSheet.kt`

- [ ] **步骤 1: 实现 Bottom Sheet 基础布局**

- [ ] **步骤 2: 实现动态表单**
根据选择的重复模式显示不同的配置项（如 X 天输入框、星期选择）。

- [ ] **步骤 3: 实现子任务动态添加**

- [ ] **步骤 4: 提交代码**
```bash
git add app/src/main/java/com/mytodo/android/ui/components/AddTodoSheet.kt
git commit -m "feat: add todo bottom sheet with dynamic forms"
```

### 任务 6: 桌面小组件实现 (Jetpack Glance)

**文件：**
- 创建: `app/src/main/java/com/mytodo/android/widget/TodoWidget.kt`
- 创建: `app/src/main/java/com/mytodo/android/widget/TodoWidgetReceiver.kt`

- [ ] **步骤 1: 实现 Glance 小组件 UI**
展示今日任务列表。

- [ ] **步骤 2: 实现小组件交互**
点击勾选框通过 `ActionCallback` 直接更新数据库并刷新小组件。

- [ ] **步骤 3: 配置小组件预览与资源文件**

- [ ] **步骤 4: 提交代码**
```bash
git add app/src/main/java/com/mytodo/android/widget/
git commit -m "feat: interactive desktop widget using glance"
```

### 任务 7: 农历与节日数据接入

**文件：**
- 修改: `app/src/main/java/com/mytodo/android/utils/DateUtils.kt`

- [ ] **步骤 1: 集成农历计算逻辑**
复用或移植 Windows 版中的逻辑或使用开源 Kotlin 库。

- [ ] **步骤 2: 在首页标题栏展示对应文案**

- [ ] **步骤 3: 提交代码**
```bash
git add app/src/main/java/com/mytodo/android/utils/
git commit -m "feat: lunar and festival integration"
```

### 任务 8: 整体适配与测试

- [ ] **步骤 1: 在小米 13 实机（或模拟器）上验证小组件勾选**
确保状态双向同步。

- [ ] **步骤 2: 修复发现的 UI 或逻辑漏洞**

- [ ] **步骤 3: 最终代码整理与文档更新**
