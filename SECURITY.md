# Security

## API 密钥

- DeepSeek API 密钥由用户在 App 内填写，使用 Android Keystore 加密保存。
- 密钥不进入代码仓库、ZIP 备份或普通日志。
- 如果怀疑密钥泄露，请立即到 DeepSeek 控制台撤销并重新生成。

## 报告问题

请通过 GitHub Security Advisory 私下报告以下问题：

- API 密钥泄露；
- 图片或聊天记录越权访问；
- ZIP 导入路径穿越；
- 数据库迁移导致的数据破坏；
- 任意代码执行或权限绕过。

不要在公开 Issue 中粘贴真实密钥、个人错题图片或备份文件。
