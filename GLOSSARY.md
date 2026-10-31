# Apple Music TV

Native Apple Music client for Fire TV / Android TV / Google TV. This glossary pins down the vocabulary used when discussing **UI performance and motion design** in this project.

## Language

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
每类动效允许的成本上限（焦点动画 100–160ms、页面转场 ≤300ms、氛围动效 30fps），超出即视为性能回归。
_Avoid_: 动画时长表（只是预算的一部分）
