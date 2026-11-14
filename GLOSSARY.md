# Apple Music TV

Native Apple Music client for Fire TV / Android TV / Google TV. This glossary pins down the vocabulary used when discussing **UI performance and motion design** and **sign-in / token intake** in this project.

## Language

### 性能与动效

**基线设备**:
用于所有性能验收对比的那台具体电视——当前为雷鸟 FF 55R69A（四核 Cortex-A73 / 3GB 内存，UI 约 60Hz）。
_Avoid_: 测试机, 目标电视

**导航动效**:
由用户操作直接触发、服务于焦点与空间定位的动效——焦点移动、页面转场、侧栏开合、列表滚动。
_Avoid_: 交互动效, 过渡动画

**氛围动效**:
无需用户操作、持续运行以营造气氛的动效——Now Playing 的渐变背景、歌词滚动。
_Avoid_: 背景特效, 装饰动画

**渲染型卡顿**:
画面掉帧或动画帧率不足导致的卡顿；与之相对的是响应型卡顿（按键后要等才动，属主线程或输入问题）。
_Avoid_: 掉帧（仅指单帧，太笼统）, 卡（口语）

**真机观测通道**:
在没有 adb 的条件下从真机获取流畅度证据的途径——电视开发者选项的「GPU 呈现模式分析」柱状条、App Dev 菜单里的帧时间悬浮窗。
_Avoid_: 性能面板（易与厂商的“性能模式”混淆）

**动效预算**:
每类动效允许的成本上限（焦点动画 100–160ms、页面转场 200ms（预算上限 300ms）、氛围动效 30fps），超出即视为性能回归。
_Avoid_: 动画时长表（只是预算的一部分）

### 签入与令牌

**MUT（Media-User-Token）**:
Apple 音乐服务里的账号标识令牌，决定"这是谁的音乐资料库"；带错它的表现是资料库为空或歌曲播不了。
_Avoid_: token, 用户 token, 音乐 token

**bearer**:
从 Apple 网页抓来的应用级访问令牌，决定"这个请求由哪个客户端发出"，与账号无关；它失效的表现是整站 401。
_Avoid_: token, 授权码, access token（与 OAuth 的 access token 混淆）

**developer token**:
MusicKit 的开发者令牌，证明"这个应用被 Apple 授权"，需要开发者账号；本项目不走这条路。
_Avoid_: 开发者证书, Apple token

**捕获（capture）**:
从已签入的 Apple 会话里取出 MUT 的动作。
_Avoid_: 抓 token, 读 token

**录入（intake）**:
让电视最终持有可用 MUT 的完整过程，包含捕获、交接，以及手动粘贴这一兜底路径。
_Avoid_: 配 token, 绑定账号

**交接（hand-off）**:
companion 或网页把 MUT 交给电视的那一次传递。
_Avoid_: 同步, 上报, 推送

**签入（sign-in）**:
用户用自己的 Apple ID 建立 Apple Music 会话的动作，发生在手机或浏览器一侧，与"录入"是两回事。
_Avoid_: 登录（单独使用时不区分是建立会话还是把 MUT 交给电视）, 授权

**companion**:
在同一局域网内协助用户完成签入并把 MUT 交回电视的伴随应用，按平台分安卓版与 iOS 版。
_Avoid_: 手机端, 助手 app, 客户端（与电视 app 混淆）
