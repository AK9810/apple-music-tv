# iOS 用快捷指令而不是 app

**状态**: accepted（2026-11-14）

## 背景

Android 有配套的伴侣应用：它在自己的 WebView 里登录 Apple，再用一次原生 `GET /set-token?mut=…` 把 Music-User-Token（MUT）交给电视。iOS 没有对应实现，本项目也没有 iOS 目标。

MUT 是访问资料库（歌曲、专辑、艺人、个人歌单）的前提，而 iOS 用户手边通常只有 iPhone / iPad。没有交接通道时，iOS 用户只能手动复制 Token，而这通常又要借助桌面浏览器的开发者工具。Android 伴侣应用的做法无法直接搬过来：它需要 Xcode 工程、开发者账号和证书，侧载的 app 还要周期性重新签名。本项目不打算为此引入这些依赖，调研中也确认了几条看似可行的捷径都走不通（见「考虑过的替代方案」）。

## 决策

不开发原生 iOS app。iOS 使用一个 **iOS 快捷指令（Shortcut）**：从用户已登录的 Safari 会话中取出 MUT，经局域网交给电视。

1. **不引入 Xcode / 开发者账号 / 证书。** iOS 侧没有任何需要编译或签名的二进制，也就没有重新签名的问题。
2. **快捷指令由「快捷指令」app 签名，作为文件分发。** 用 `shortcuts sign -m anyone` 签名后发布 `.shortcut` 文件；用户打开导入即可，不需要 App Store，也不需要 Apple Developer 账号。
3. **捕获方式就是 `document.cookie`。** 快捷指令在已登录的 `music.apple.com` 页面上读取 `media-user-token` —— 与 Apple 自家网页播放器读取的是同一个值。
4. **交接复用现有的表单 Token 端点。** 以 `application/x-www-form-urlencoded` 把 `mut` POST 到 `http://<TV_IP>:8080/set-token`，再 `GET /status`（`hasMUT`）确认结果；不新增接口，也不改电视端协议。
5. **不走配对，也不加鉴权。** 和 Android 伴侣应用一样，这是局域网内明文 HTTP 的一次性交接，暴露面与缓解措施见 [SECURITY.md](../../SECURITY.md)。

## 考虑过的替代方案

- **做 iOS 伴侣应用（与 Android 对应）**：需要 Xcode 工程、开发者账号和证书；侧载的 app 需要周期性重新签名（免费签名 7 天过期），分发还要经过 TestFlight / App Store 或个人重签。对一个个人项目是持续的维护成本，放弃。
- **中间人拦截 iOS「音乐」app 的 API 流量**：音乐 app 固定（pin）了它的 API 流量，中间人代理无法解密，拿不到 MUT，放弃。
- **用 MusicKit 换 Token**：MusicKit 签发的是团队范围的开发者 Token，作用域与本项目抓取到的 bearer 不同，用它访问会返回 403，拿不到资料库，放弃。
- **用 `ASWebAuthenticationSession` / `SFSafariViewController` 读 Cookie**：两者都不向宿主暴露 Cookie，读不到 `media-user-token`，放弃。
- **在页面里 iframe `music.apple.com`**：Apple 禁止被 iframe，这条路不通，放弃。
- **只提供桌面浏览器操作说明**：把「在沙发上用电视」变成「先去开电脑」，违背这个项目的产品前提，放弃。

## 后果

- iOS 通道依赖 `document.cookie` 对页面脚本保持可读。如果 Apple 哪天改成 HttpOnly 之类的限制，快捷指令会**显式失败**（页面上取不到 Token），而不是静默写入错误的值。
- 那时的升级路径是 **WKWebView 外壳 app**：由 app 自己持有会话并读出 Cookie。已有采用同一模式的 GPL-3.0 实现，与本项目许可证兼容；但这是备选方案，不是当前决策。
- 分发有一个无法避免的确认步骤：导入快捷指令时用户必须手动点确认。
- 分发的 `.shortcut` 文件本身不含机密；唯一因用户而异的是电视地址，而它在快捷指令里是一个可编辑的动作。
