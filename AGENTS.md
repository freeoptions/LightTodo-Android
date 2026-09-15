# LightTodoAndroid 项目专属规则

通用规则见：`E:\@imFile-Download\AI-Useful-Prompt\通用开发工作规则.md`。

- 本项目是独立的 Android Gradle Kotlin 版本，应用模块位于 `app`。
- 默认不替我执行 Android Studio/Gradle 构建；修改完成后提醒：“已经修改完，可以去 as 构建了”。
- 与 Windows 版 LightTodo 的功能保持语义一致，但不要直接复制桌面端实现或资源；先按 Android 生命周期和权限模型设计。
- 不要提交 `local.properties`、签名文件、密钥或个人待办数据。

## 小组件点击完成/取消完成（已验证）

- 小组件勾选框使用显式 `actionSendBroadcast`，由 `TodoWidgetToggleReceiver` 接收；Intent 同时携带 `todo_id` 和包含待办 ID 的唯一 URI，避免 Launcher 复用 RemoteViews 点击槽位后把不同待办串在一起。
- 点击状态必须以 Room 当前值为准，在 `TodoRepository.toggleTodoFromWidget()` 内执行切换；不要依赖旧快照、缓存状态或 `ToggleableStateKey` 推断目标状态。完成和取消完成共用同一条路径，分别验证 `false → true`、`true → false`。
- `GlanceAppWidget.updateAll()` 返回成功不代表会重新执行 `provideGlance()`：已有 composition 运行时可能不会重启。因此 `TodoWidget.provideContent` 内必须观察 `TodoRepository.observeToday()`，由 Room Flow 驱动当前桌面状态；`updateAll()` 仅作为实例刷新和 composition 失效后的兜底。
- 列表项使用稳定的业务 ID 作为 `itemId`，并用 `key(todoId)` 保持行身份；只有勾选框负责点击，整行不要复用完成动作，避免任务移动、重排后点击目标错乱。
- 完成后的视觉状态保留约 3 秒：使用 `completedAt` 判断“完成啦”庆祝态和橙色背景，等待到期后再将任务排到已完成列表底部。composition 内的到期计时负责正常重排，AlarmManager 刷新作为后台/失活 composition 的兜底；取消完成时应由 Room Flow 立即清除庆祝态并取消旧计时。
- 为保证点击稳定性，当前勾选框使用静态矢量图标配合显式广播，不强行恢复原生 `CheckBox` 动画。Glance 基于 RemoteViews，不能按普通 Compose 列表假设支持连续动画；后续若尝试动画，必须先验证完成、取消、重排和 Launcher 点击槽位，不能牺牲已验证的状态同步。
- 排查时过滤 `LightTodoWidget`，重点看：`toggle:received` → `toggle:room_end`（前后状态）→ `flow:emit` → `render:checkbox`；只看到 `refresh:update_all_success` 而没有后续渲染时，优先检查 composition 内是否仍在观察 Room Flow。
