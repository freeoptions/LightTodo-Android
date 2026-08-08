# LightTodoAndroid 项目专属规则

通用规则见：`E:\@imFile-Download\AI-Useful-Prompt\通用开发工作规则.md`。

- 本项目是独立的 Android Gradle Kotlin 版本，应用模块位于 `app`。
- 默认不替我执行 Android Studio/Gradle 构建；修改完成后提醒：“已经修改完，可以去 as 构建了”。
- 与 Windows 版 LightTodo 的功能保持语义一致，但不要直接复制桌面端实现或资源；先按 Android 生命周期和权限模型设计。
- 不要提交 `local.properties`、签名文件、密钥或个人待办数据。
