# 正在播放的动态背景 —— 它是如何实现的

[English](dynamic_background_tutorial.md) | **简体中文**

关于「正在播放」页面背后流动色彩背景的完整故事：实现过程、设计决策与代码。全部代码都在一个 composable 文件里：
[`NowPlayingScreen.kt`](../android/app/src/main/java/com/applemusicktv/ui/screens/NowPlayingScreen.kt)，
节拍数据来自
[`BeatAnalyzer.kt`](../android/app/src/main/java/com/applemusicktv/media/BeatAnalyzer.kt)。

---

## 1. 它是什么

一个全屏、纯 canvas 的渐变背景 —— **从不绘制封面图片本身**。
四个柔和的径向颜色「光球」在四个象限中缓慢漂移，以 Screen 混合模式叠加，重叠处的颜色会更亮。光球颜色从专辑封面中提取，向鲜艳方向推，并在每次切歌时交叉淡入淡出。整个画面随音乐节拍轻微脉动。

为什么不用模糊图片？模糊位图在电视上放大后会像素化，而且需要硬件模糊，这在 Fire TV（API < 31）上是空操作。Canvas 渐变与分辨率无关，而且开销很低。见 [`DynamicBackground`](../android/app/src/main/java/com/applemusicktv/ui/screens/NowPlayingScreen.kt)。

---

## 2. 流水线

```
专辑封面 URL
   │  （Coil 以 1200px 加载，allowHardware=false）
   ▼
Palette.from(bitmap)  ──►  最多 7 个色板
   │  vibrant → lightVibrant → darkVibrant → muted → lightMuted → darkMuted → dominant
   ▼
逐色板 HSV 推色：  sat ×1.45（下限 0.55），  value 上限 0.80
   │
   ▼
spreadByHue()  ──►  色相环上彼此相隔 ≥28° 的 6 种颜色
   │
   ▼
4 个漂移的径向光球  ──►  BlendMode.Screen
   │        ▲                        ▲
   │        │ 节拍能量               │ 3–4 个 InfiniteTransition 漂移浮点
   │        │（半径 + 透明度脉动）   │（20s / 27s / 34s / 15s，Reverse）
   ▼
中央 + 右侧压暗（歌词可读性）
```

### 步骤 A —— 加载封面并提取调色板
[`rememberArtworkPalette`](../android/app/src/main/java/com/applemusicktv/ui/screens/NowPlayingScreen.kt#L536)

- 封面模板（`{w}x{h}bb.{f}`）填充为 **1200×1200 jpg**。调色板始终取自**静态**封面，绝不使用动态封面的视频 URL。
- Coil 以 `allowHardware(false)` 加载 —— `Palette` 需要一个可读取像素的软件位图。
- `Palette.from(bitmap).generate()` 最多产生 7 个具名色板。它们按 `population`（该颜色覆盖图像的比例）排序，让主色排在前面。
- 回退：一个接近黑色的六色列表，用于图片加载前、任何错误时，或封面本身确实是黑色时（主导亮度 < 0.06）。

### 步骤 B —— 把每个色板推向鲜艳
同一个函数中的 HSV 代码块：

```kotlin
android.graphics.Color.colorToHSV(swatch.rgb, hsv)
hsv[1] = (hsv[1] * SAT_BOOST).coerceIn(SAT_FLOOR, 1f)   // 1.45，下限 0.55
hsv[2] = hsv[2].coerceAtMost(VALUE_CEILING)              // 0.80
```

**value 上限是这里最重要的一个。** 一个浅色、高明度的色板读起来像浅灰色，并会与上方的白色歌词竞争。限制 value（并给饱和度设下限）能逼出深而饱和的颜色，读起来是*颜色*，而不是雾。曾尝试过并移除了一个单纯的饱和度*滤镜* —— 它把鲜艳的粉色和青色都洗掉了。
常量：[`SAT_BOOST`/`SAT_FLOOR`/`VALUE_CEILING`](../android/app/src/main/java/com/applemusicktv/ui/screens/NowPlayingScreen.kt#L530)。

**不要把 `VALUE_CEILING` 推过约 0.85** —— 那是背景开始冲淡歌词的临界线。

### 步骤 C —— 把颜色散布到色相环上
[`spreadByHue`](../android/app/src/main/java/com/applemusicktv/ui/screens/NowPlayingScreen.kt#L508)

挑选最多 6 种在色相上**彼此相隔 ≥28°** 的颜色，避免四个光球最终都变成同一种颜色的不同深浅。如果通过间距测试的少于 6 种，剩余位置用最接近的非重复颜色填充。

---

## 3. 渲染光球
[`DynamicBackground`](../android/app/src/main/java/com/applemusicktv/ui/screens/NowPlayingScreen.kt#L586)

### 切歌时的颜色交叉淡入
每个调色板颜色都包在 `animateColorAsState(tween(1500))` 中，因此切歌时背景会在 1.5 秒内从旧调色板**融化**到新调色板，而不是硬切。

### 「律动」颜色循环
四个光球，六个调色板颜色。每个光球在两者之间缓慢插值，由某个漂移浮点驱动，因此即使暂停播放，颜色也会持续微妙变化：

```kotlin
val colorFracs = listOf(t4, 1f - t3, t1, 1f - t2)
val colors4 = List(4) { i -> lerp(animated[(i*2)%n], animated[(i*2+1)%n], colorFracs[i]) }
```

### 漂移
三到四个 `rememberInfiniteTransition` 浮点，周期 **20s / 27s / 34s / 15s**，`LinearEasing`，`RepeatMode.Reverse`。四个光球中心偏向四个角落（例如左上光球游走于 `x∈[0.02,0.28]`、`y∈[0.05,0.32]`），因此它们永远不会在歌词所在的中央汇聚。

### 绘制调用
在单个 `drawBehind` 中：

1. **4 个径向渐变光球**，`radius = max(w,h) * 0.62 * beatScale`，各自从 `color(alpha=beatAlpha) → color(alpha=0)` 淡出，以 **`BlendMode.Screen`** 绘制（重叠处叠加/提亮）。
2. **中央压暗** —— 屏幕中心一个黑色 47%→0% 的径向渐变。
3. **右侧压暗** —— 右侧一个 0%→48% 的水平黑色渐变，即歌词所在处，用于提高对比度。
4. **一层平坦的 `0x22000000` 薄幕** —— 保持很低，因为它会同等程度地压低所有色相；可读性主要靠右侧渐变。

---

## 4. 节拍响应

脉动来自 [`BeatAnalyzer`](../android/app/src/main/java/com/applemusicktv/media/BeatAnalyzer.kt)，
一个暴露 `energy: StateFlow<Float>` 的 `@Singleton` 总线。

- 每个 ExoPlayer 实例通过 `beatAnalyzer.newProcessor()` 获得**自己**的 `BeatProcessor`（一个 Media3 `BaseAudioProcessor`），经由 `BeatAwareRenderersFactory` 注入。`AudioProcessor` 不能在两个音频接收器之间共享，交叉淡入播放器需要自己的处理器，这样交换后节拍仍能工作。只有**活跃**的处理器会发布数据（`activate(p)`）。
- **检测基于低频起音，而非响度**：单声道下混 → 130 Hz 单极点低通 → 固定 10 ms 窗口内的能量 → 当某窗口超过最近约 1 秒的 `mean + 1.5·stddev` 时判定起音，并有 120 ms 不应期 → 先冲后衰的包络（约 250 ms 衰落）。仅在变化 >0.015 时发射，以限制重组。
- 仅在 `PCM_16BIT` 输出下运行；浮点输出时旁路。

在 composable 中：

```kotlin
val rawEnergy by beatAnalyzer.energy.collectAsState()
val scaledRaw = (rawEnergy * beatMultiplier).coerceIn(0f, 1f)
val energy by animateFloatAsState(scaledRaw, spring(dampingRatio = 0.5f, ...))
```

- `beatMultiplier` 是用户的**节拍脉动**设置 —— ··· 菜单中的 正常 1× / 强烈 2× / 狂热 3.5×。
- 一个低刚度弹簧平滑原始起音，让视觉是弹跳而不是硬切。
- `energy` 驱动三处：光球**半径**（`1 + energy*0.25`）、光球**透明度**（`0.66 + energy*0.22`），以及每个光球一点位置**轻推**。

`energy` **只在 `DynamicBackground` 内部**收集，因此一次节拍不会重组整个「正在播放」页面 —— 只重组背景。

---

## 5. 性能预算（Fire TV）

Fire TV Stick 4K 很弱，所以背景是刻意限制的：

- **每帧 6 次绘制调用**：4 个光球 + 中央压暗 + 右侧渐变。
- **光球 ≤4 个。** `BlendMode.Screen` 会为每次绘制调用强制一次离屏合成；6 个光球会造成可见卡顿。
- **3–4 个漂移动画。** 不再多。
- **不用 `Modifier.blur()`**，不做多通道绘制，不用 `CubicBezierEasing` —— 这些都试过并且卡。坚持使用 `LinearEasing` 和纯渐变数学。

---

## 6. 动态（动画）专辑封面

与背景相互独立：当 `GET /api/motion/:songId` 返回 URL 时，
[`MotionCover`](../android/app/src/main/java/com/applemusicktv/ui/screens/NowPlayingScreen.kt#L430)
会在静态封面上播放一段静音循环的方形视频。注意调色板仍然从**静态**封面 URL 提取，而不是视频。绿闪修复方案保留 `setShutterBackgroundColor(BLACK)`，并在恢复时重新置位 `ready` 标志 —— 绝不使用 `TRANSPARENT`，否则在 surface 重新附着时会显示一帧绿色的 YUV 画面。

---

## 7. 调参速查表

| 想改…… | 改这里 |
|---|---|
| 鲜艳程度 / 苍白程度 | `SAT_BOOST`、`SAT_FLOOR`、`VALUE_CEILING`（≤0.85） |
| 4 个光球颜色差异多大 | `spreadByHue` 的 `minAngle`（28°） |
| 切歌时的交叉淡入速度 | `animateColorAsState(tween(1500))` |
| 漂移速度 | 四个 `tween(20_000 / 27_000 / 34_000 / 15_000)` |
| 光球大小 | `radius = max(w,h) * 0.62f` |
| 脉动强度 | `beatScale`/`beatAlpha` 系数，或用户的节拍脉动设置 |
| 歌词对比度 | 右侧 `horizontalGradient` 末端 alpha（`0x7A`）、平坦薄幕（`0x22`） |
| 节拍灵敏度 | `BeatAnalyzer` 中的 `mean + 1.5·stddev` 阈值 + 不应期 |

> 黄金法则：这个背景位于**白色歌词之后**。每个旋钮都在鲜艳度与可读性之间做权衡。犹豫时，就调暗一点。
