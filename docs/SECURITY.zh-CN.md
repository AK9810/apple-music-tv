# 安全

[English](../SECURITY.md) | **简体中文**

## 你的 Token

- 你的 **Music-User-Token（MUT）** 拥有对你 Apple Music 资料库和账户的完全访问权限。**请像对待密码一样对待它。** 不要分享、不要粘贴到不可信的工具中，也不要提交到仓库。
- 应用会把 MUT 保存在设备上；只有当你使用代理服务器时，才会写入 `server/auth-state.json`。**bearer token** 会从 `music.apple.com` 自动抓取。
- 以下文件已被 gitignore，绝不能提交：`server/auth-state.json`、`server/.env`、`android/local.properties`，以及任何 `*.keystore`。

## 安全运行

- 可选的代理服务器绑定在 `0.0.0.0` —— **只在可信的局域网内运行**，绝不要暴露到公网。
- 手机设置页（8080 端口）和代理服务器都没有鉴权；同一网络中任何能访问它们的人都可以读取状态/日志并设置 Token。请将它们保持在私有网络中。这同样适用于下面两条自动交接 Token 的通道：两者都没有配对步骤，也没有任何鉴权，而且都会通过**局域网内的明文 HTTP** 发送 MUT —— 在家用路由器上没问题，但不要在共享网络中使用。
- **Android 伴侣应用**会在**自己的 WebView** 中登录 Apple，然后用原生 `GET /set-token?mut=…` 把 Token 交给电视。该会话（Cookie，包括 `media-user-token`）保存在手机的 App 私有存储中；卸载伴侣应用就会清除。Token 本身随后会像手动粘贴的一样保存在电视上。
- **iOS 快捷指令**会从 Safari 中已登录的 `music.apple.com` 页面读取 `document.cookie`（`media-user-token`），POST 到同一个 `http://<TV_IP>:8080/set-token`，再读取 `GET /status`（`hasMUT`）确认成功。它以签名的 `.shortcut` 文件分发，无需安装 app，文件本身也不含机密 —— 唯一因用户而异的只是电视地址，而它是一个可编辑的动作。为什么 iOS 不做 app：[ADR-0002](adr/0002-ios-shortcut-instead-of-an-app.md)。
- 音频流在**本地解密，仅用于播放**。不要用它来重新分发内容。

## 报告漏洞

这是一个没有正式支持的个人 / 教育项目。如果你发现安全问题（例如 Token 泄露、不安全的网络默认值），请**提交一个 GitHub Issue** 描述问题；如果内容敏感，请不要公开细节，并说明你希望私下交流。

请**不要**在 Issue 或附件日志中包含真实 Token、凭据或个人信息。
