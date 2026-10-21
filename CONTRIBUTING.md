# Contributing

感谢参与 AI 错题本。提交改动前请先确认不会提交个人错题图片、API 密钥或导出的 ZIP 文件。

## 开发流程

1. Fork 仓库并创建功能分支。
2. 使用 JDK 17 或 21，安装 Android SDK Platform 35。
3. 运行测试和静态检查：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

4. 在 Pull Request 中说明改动目的、页面变化和测试方式。

## 约束

- 保持本地优先：新增页面不能要求网络才能查看已有错题。
- API 密钥不得写入代码、资源文件、日志或测试快照。
- AI 输出必须允许人工修正。
- 数据库结构发生变化时，必须提供 Room 迁移并更新 schema。
- 不提交 `app/build`、`.android-sdk`、`.toolchain`、`local.properties` 或 APK。

## 数据隐私

提交截图或日志前，请遮盖学生姓名、学校、题目答案及其他可识别信息。
