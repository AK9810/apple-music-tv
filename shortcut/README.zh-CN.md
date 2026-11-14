# iOS 快捷指令

[English](README.md) | **简体中文**

iPhone / iPad 把 Music-User-Token（MUT）交给电视的通道：一个签名的快捷指令在 Safari 中已登录的
`music.apple.com` 页面上读取 `document.cookie`，再把 Token POST 给电视的 8080 端口服务。
不需要 iOS app，不需要 Xcode，也不需要开发者账号 —— 为什么这样选见
[ADR-0002](../docs/adr/0002-ios-shortcut-instead-of-an-app.md)。

电视一侧早已存在且不做改动：`POST /set-token`（表单字段 `mut`），然后 `GET /status` 读 `hasMUT`。
这个目录只负责手机侧。

## 目录内容

| 路径 | 是什么 |
| --- | --- |
| `source/Send Token to TV.shortcut` | 未签名源文件，就是被签名的那份。正常情况下是从「快捷指令」app 导出的文件；当前这份是生成的脚手架，来源记录在 [`VERIFICATION.md`](VERIFICATION.md)。 |
| `Send Token to TV.shortcut` | 签名后的产物（AEA1 容器）。手机导入的就是它；电视在 `/send-token-to-tv.shortcut` 提供下载，页面的 iOS 路径用它作为离线安装方式。 |
| `sign.sh` | 给 source 签名，并校验 AEA1 文件头。 |
| `VERIFICATION.md` | 支撑这个产物可以发布的真机验证记录。 |

签名文件与 source 的差别只有 AEA1 外壳和签名。两者都不含机密：唯一因用户而异的是电视地址，
而它是一个可编辑的动作。

## 分工

- **配方与签名** —— 本文档和 `sign.sh`，可重复执行。
- **搭建** —— 维护者用「快捷指令」app 做一次。`shortcuts` 命令行能运行、列出、查看和签名，
  但不能创建。当前产物早于这一步：因为 Mac 没有「在网页上运行 JavaScript」这个动作，它是先生成
  plist 脚手架、再经维护者确认作为发布源的；来源记录见 [`VERIFICATION.md`](VERIFICATION.md)。
- **验证** —— 真实 iPhone（不是模拟器）加真实电视：导入、从 Safari 运行、资料库里的歌能播。
  模拟器证明不了其中任何一条。

## 手机上的三个前置条件

在用户动手前就要说清楚。第一条不满足时没有明确提示，很容易被误读成「没取到 Token」。

1. **允许运行脚本。** 设置 → 快捷指令 → 高级 → 允许运行脚本 = 开。这是「快捷指令」的设置，
   不在 Safari 里。关着时 JavaScript 动作会失败。
2. **分享面板可用。** 快捷指令详情里「在分享表单中显示」= 开，接收类型为 Safari 网页。
   这个指令要从 Safari 的分享面板运行；从「快捷指令」app 直接运行就没有网页可读。
   只有 Safari 可以 —— Chrome、Firefox 不会把页面交给这个动作。
3. **本地网络权限。** 第一次运行时 iOS 会问 *「快捷指令」想要查找并连接本地网络上的设备* —— 选允许。
   如果之前拒绝过：设置 → 隐私与安全性 → 本地网络 → 快捷指令 = 开。手机和电视必须在同一网络下。

第一次运行时 Safari 还会问这个快捷指令能否与页面交互（它可以读 Cookie）。每个页面每个指令允许一次。
这是提示，不是前置条件。

## 搭建配方

文件名**就是**导入后快捷指令的名称：保持 `Send Token to TV.shortcut`，否则分享面板里的名字会变
（工作流 plist 本身不存名字）。

在那台要运行它的 iPhone 上搭建。这些动作是跨平台的，Mac 的「快捷指令」也能做出同样的文件
（第一版草稿就是它生成的），但 Mac 没有「在网页上运行 JavaScript」这个动作，所以 JavaScript 步骤
必须直接写进 plist（标识符 `is.workflow.actions.runjavascriptonwebpage`），或者在 iOS 上补加。
当前产物就是这样生成的；下面的步骤描述的是任何一次重建都适用的工作流。

动作按顺序：

1. **在网页上运行 JavaScript** —— 输出重命名为 `Token`；输入为「快捷指令输入」（即 Safari 网页）；
   脚本见下。放在第一位：第一个动作默认接收分享进来的网页。
2. **注释** —— 放在地址动作上方的提示：把 `TV-IP` 换成电视设置页上显示的地址，末尾不要斜杠。
3. **文本** —— 重命名为 `TV address`；内容 `http://TV-IP:8080`。这是每个用户导入后都要改的动作：
   签名约束的是导入，不是导入后的修改。占位符故意不是真实地址，忘了改会明显连不上，
   而不是去探测另一台设备。
4. **如果** `Token` **有任何值**
   1. **获取 URL 内容** —— URL：`TV address` + `/set-token`；方法 `POST`；
      请求体 `表单`；一个字段 `mut` = `Token`。
   2. **获取 URL 内容** —— URL：`TV address` + `/status`；方法 `GET`。
   3. **从输入中获取文本** —— 输入 `URL 内容`；输出 `Status text`。
   4. **如果** `Status text` **包含** `"hasMUT":true`
      - **显示通知** —— 标题 `Apple Music TV`；正文 `Token received — the TV can now play your library.`
      - **否则** → **显示通知** —— 标题 `Apple Music TV`；正文 `The TV answered but did not keep the
        token. Run it again, or paste the token on the TV's setup page.`
5. **否则** → **显示通知** —— 标题 `Apple Music TV`；正文 `No token on this page. Sign in at
   music.apple.com in Safari, then run this Shortcut from that page's share sheet.`

Token 为空的分支在 POST **之前**，所以空值永远不会被写进去。`否则` 是单独插入的动作
（在 `如果` 块里搜索「否则」），不是某个开关。

让动作引用变量的方式：点参数栏 → 从「选择变量」里选对应动作。重命名 JavaScript 动作和文本动作，
就是为了让变量名不会混淆。

### 为什么状态检查用文本匹配

用 `获取字典值` 读 `hasMUT` 看似更直接，但它的输出是通用类型，编辑器只提供「有任何值」类条件 ——
手工构造的「为真」条件导入后会变成未解析（「请为每个参数选取值」）。把布尔值转成文本也不行：
转换结果会本地化，英文是 `true`，中文是 `是`，用字面量 `true` 比较会静默走失败分支。这两点都是在
搭这一版时实测到的。`GET /status` 返回的 `{"hasMUT":true,…}` 不含空格
（`InAppWebServer.status()`），所以用响应文本包含 `"hasMUT":true` 来判断，既不依赖语言也确定。
这是一个格式依赖：如果那段 JSON 将来加了空格，这个条件必须一起改。

### 页面脚本

```js
const match = document.cookie.match(/(?:^|;\s*)media-user-token=([^;]*)/);
if (!match || !match[1]) {
  completion();            // 无输出，而不是空字符串
} else {
  let token = match[1];
  try { token = decodeURIComponent(token); } catch (e) { /* keep the raw value */ }
  completion(token);
}
```

- `completion(...)` 是这个动作的回调，它的值就是 `Token`。
- 没有 Cookie → 调用不带参数的 `completion()`，动作**没有输出**，`如果` 判为假。不要改成返回
  `""`：快捷指令可能把空文本当成「有值」，于是 POST 一个空的 `mut`；电视会忽略它，但如果原本
  已经有 Token，状态仍是 true，快捷指令就会误报成功。（搭这一版时实测到的问题。）
- `decodeURIComponent` 有保护：遇到孤立的 `%` 时保留原始值，而不是让指令中断。
- 字面量 `+` 从端到端都是安全的：电视的表单解析会保留字面 `+`，不再把它解码成空格
  （[FormBody.kt，#11](../android/app/src/main/java/com/applemusicktv/util/FormBody.kt)）。被悄悄改坏的 Token
  表现成「已登录但资料库是空的」。
- 页面必须是 `music.apple.com`。Apple 自家网页播放器读的就是同一个 Cookie，所以这条路不涉及
  Apple API 或开发者 Token。

### 电视不可达时

快捷指令没有 try/catch，`获取 URL 内容` 连接失败会直接中止整个指令。所以「电视不可达」是 iOS 自己的
**Could not connect to the server** 弹窗，不是我们发的通知 —— 它与上面两条通知都不同，而且发生在
读取 `/status` 之前。用户该检查 Wi-Fi、电视 app 是否在运行、以及第 3 个动作（`TV address`）里的地址。
不要用「把 Token 放进 URL」来"修"这一点：不把 Token 放进 URL 是这套设计的硬要求
（[#10](https://github.com/AK9810/apple-music-tv/issues/10)）。

## 导出、签名、验证

1. 在快捷指令编辑器里点顶部名称旁的下箭头 → **导出文件** → 存到「文件」或 AirDrop 过来。
   不要用 *iCloud 链接*：那是发布一份副本，而不是给我们文件。
2. 把导出文件放到 `shortcut/source/Send Token to TV.shortcut`。
3. `./shortcut/sign.sh` —— 执行 `shortcuts sign -m anyone`，不是 AEA1 容器就报错，并打印两个
   SHA-256。
4. 在真实 iPhone 上：删掉本地未签名副本，从「文件」或 AirDrop 导入签名产物，在 Safari 里已登录的
   `music.apple.com` 页面从分享面板运行，然后在电视上确认：Token 已设置、资料库已填充、
   资料库歌曲能播放。
5. 把这次运行记录到 [`VERIFICATION.md`](VERIFICATION.md)：设备、系统版本、哈希、以及每种失败的样子。

签名是手机能直接导入、不需要旧的「不受信任的快捷指令」开关的原因。签名时 Mac 需要登录 iCloud；
如果报认证错误，先检查 系统设置 → Apple 账户（以及 iCloud 云盘）再重试。

## 修改后重建

在「快捷指令」里改 → 导出 → 替换 `source/…` → `./sign.sh` → 替换签名产物 → 重做
`VERIFICATION.md` 里的真机检查，并更新日期、哈希和结果。永远不要签一个不是 `source/` 里的文件：
这个目录存在的意义就是防止两者漂移。
