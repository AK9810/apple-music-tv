# iOS 快捷指令设置

[English](ios-shortcut.md) | **简体中文**

在 iPhone / iPad 上没有 Chrome 开发者工具，桌面浏览器那套复制 Token 的步骤用不了。
改用签名的**快捷指令**：它从你在 Safari 中已签入的 `music.apple.com` 会话里捕获
Media-User-Token（MUT），再通过局域网把 Token 交接给电视。不需要 iOS app，不需要 Xcode，
也不需要开发者账号 —— 为什么这样选见
[ADR-0002](adr/0002-ios-shortcut-instead-of-an-app.md)。这份指南覆盖从一台全新的
iPhone 或 iPad 到完成录入、资料库歌曲开始播放的全过程。电视上的 8080 设置页面支持简体中文与
英文：右上角可切换，或在地址后加 `?lang=zh`。本指南以中文标签为准，括号里给出对应的英文标签。

## 开始之前：三个前置条件

第一次运行前设置好。名称与电视设置页上的一致。

1. **允许运行脚本（Allow Running Scripts）** —— 设置 → 快捷指令 → 高级 → 允许运行脚本 = 开。
   这是「快捷指令」的设置，不在 Safari 里。关着时，快捷指令会停在「在网页上运行 JavaScript」动作并报错，
   什么都不会到达电视。
2. **分享面板可用（Share-sheet availability）** —— 打开快捷指令的详情，把「在分享表单中显示」
   设为开，接收类型为 Safari 网页。关着时，Safari 的分享面板里不会出现「Send Token to TV」；
   若改从「快捷指令」app 直接运行，就没有网页可读，运行会以「页面上没有 Token」的通知结束。
3. **本地网络权限（Local Network permission）** —— 第一次运行时，iOS 会问「快捷指令」能否
   查找并连接本地网络上的设备，选**允许**。如果之前拒绝过，运行就连不上电视，iOS 会显示连接提示。
   重新打开：设置 → 隐私与安全性 → 本地网络 → 快捷指令 = 开。手机和电视必须在同一网络下。

第一次运行时 Safari 还会问这个快捷指令能否与页面交互 —— 它需要这个权限来读取 Cookie。
每个页面每个快捷指令允许一次。这是正常提示，不是前置条件。

## 安装快捷指令

### 从电视页面一键安装

1. 在手机上打开 `http://<TV_IP>:8080` —— 地址显示在电视的签入界面上。页面会自动落在
   **iOS 快捷指令**页签。
2. 在该页签里点 **安装快捷指令**（Install the Shortcut）。这个链接会直接
   打开「快捷指令」的导入预览。
3. 点 **添加** 完成。iOS 总会要求这一次确认，页面无法跳过：看到预览并不代表安装失败，
   只是在等这一下。

iOS 不接受 `http://` 的导入目标，所以一键链接指向 https 上的发布资产。它与电视提供的是
同一份签名文件。

### 离线：从电视下载

如果设置时手机上不了互联网，电视仍然可以把文件交给你：

1. 在同一个 **iOS 快捷指令**页签里点 **从电视下载快捷指令**（Download the Shortcut from this TV）。
2. 在**文件**中打开下载的 `Send Token to TV.shortcut`。链接路径是连字符形式
   `/send-token-to-tv.shortcut`，但保存到「文件」里的文件名保留空格 —— 导入后的快捷指令
   就用这个名字。
3. 点 **Add**。

这与一键安装是同一份签名文件 —— 只是 URL 不同，不是第二份构建。换手机时，重复任一种安装
方式并重新设置电视地址即可；电视一侧不需要任何改动。

## 设置电视地址

导入后打开快捷指令，找到名为 `TV address` 的**文本**（Text）动作。把 `http://TV-IP:8080` 换成
电视设置页顶部显示的地址，例如 `http://192.168.1.4:8080` —— 末尾不要加斜杠。

导入后地址仍然可编辑；签名约束的是导入，不是之后的修改。电视 IP 变了就改这个动作，
不需要重新导入快捷指令。

## 运行

1. 打开 **Safari**，像平时一样在 `music.apple.com` 签入你的 Apple ID（密码和双重认证）。
2. 留在该页面，点 **分享**（Share）。
3. 在分享面板里选择 **Send Token to TV**。
4. 让它运行。它从页面读取 `media-user-token`，把 Token POST 到
   `http://<TV_IP>:8080/set-token`，再读取 `/status` 确认电视已经收下。

## 成功的样子

- 通知正文是 `Token received — the TV can now play your library.`
- 电视设置页顶部的状态行变绿，显示 `Music-User-Token 已生效`。
- 电视的**资料库**开始出现你的歌曲、专辑和个人歌单。
- 资料库里的歌完整播放，而不是 30 秒试听。

真正的验收是播放，不是通知：电视上原本就有 Token 时也会显示已签入，所以要确认资料库有内容
并播一首歌。

## 如果它不工作

| 现象 | 含义与处理 |
| --- | --- |
| `No token on this page. Sign in at music.apple.com in Safari, then run this Shortcut from that page share sheet.` | 没有签入，或页面上没有 `media-user-token` 这个 Cookie。**在 Safari 中**签入 `music.apple.com`，留在该页面，再从它的分享面板运行快捷指令。 |
| 快捷指令停在「在网页上运行 JavaScript」（`Run JavaScript on Web Page`）动作并报错；没有任何通知。 | **允许运行脚本**关着。设置 → 快捷指令 → 高级 → 允许运行脚本 = 开，然后重新运行。 |
| Safari 分享面板里没有 **Send Token to TV**，或运行以「页面上没有 Token」的通知结束。 | **分享面板可用**关着，或快捷指令是从「快捷指令」app 运行的，或页面开在 Chrome / Firefox 里。把「在分享表单中显示」设为开（接收 Safari 网页），在 Safari 中打开页面并从那里分享 —— Chrome 和 Firefox 不会把页面交给这个动作。 |
| 手机明明连着 Wi-Fi，每次运行都连不上电视。 | **本地网络权限**被拒绝了。设置 → 隐私与安全性 → 本地网络 → 快捷指令 = 开，然后重新运行。 |
| iOS 显示 `Could not connect to the server.`（中文系统显示 `无法连接服务器`）。 | 电视不可达。这是 iOS 自己的提示，不是快捷指令发的通知。检查手机和电视是否在同一 Wi-Fi、电视 app 是否在运行、以及 `TV address` 是否与电视页面顶部的地址一致。 |
| `The TV answered but did not keep the token. Run it again, or paste the token on the TV setup page.` | 电视回应了，但没有保存 Token。再运行一次；如果仍然如此，用下面的兜底方式。 |
| 资料库用了几个月后突然变空；歌曲播不了。 | Token 大约六个月有效。在 Safari 中重新签入 `music.apple.com`，再运行一次快捷指令 —— 不需要重新导入。 |

## Token 有效期

MUT 大约六个月有效。快捷指令和电视都不需要为此更新；过期后重新在 `music.apple.com`
签入，再运行一次快捷指令即可。资料库突然变空、或歌曲突然播不了，就是信号。

## 兜底方式

### 在电视页面手动粘贴

打开 `http://<TV_IP>:8080`，切到 **登录**页签，找到 **设置 Token** 卡片：把 Token 粘进输入框并点
**保存 Token**。表单会 POST 到 `/set-token`，电视随后重定向到 `/?tab=1&saved=1` —— 回到该页签并
在顶部显示绿色横幅，状态行变绿并显示 `Music-User-Token 已生效`。要粘贴的值就是
`media-user-token` 这个 Cookie；如何复制它见
[TECHNICAL.zh-CN.md](TECHNICAL.zh-CN.md#获取你的-music-user-token) 中的桌面步骤，或下面的
Mac 走查。

### Mac + Safari Web Inspector

手边有 Mac 时：

1. 在 Mac 的 Safari 中打开 `music.apple.com` 并签入。
2. 打开开发者菜单：Safari → 设置 → 高级 → 勾选 **显示网页开发者功能**。
3. 让 `music.apple.com` 标签页在前台，选择 开发 → **显示 Web 检查器**。
4. 打开 **存储** → **Cookie** → `music.apple.com`。
5. 选中 `media-user-token`，复制它的值。
6. 粘贴到电视页面 **登录**页签的 **设置 Token** 卡片，点 **保存 Token**。

**登录**页签是桌面浏览器的默认页签，其中的粘贴框是所有方式的兜底 —— 无论从哪个页签进来，
它都可达。

## 安全

这次交接是家庭局域网内的明文 HTTP，没有配对步骤。任何能访问电视 8080 端口的人都可以读取
状态和日志并设置 Token。请把电视留在可信的家庭网络中，不要把 8080 端口暴露到公网 ——
详见 [SECURITY.zh-CN.md](SECURITY.zh-CN.md)。
