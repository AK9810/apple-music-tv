# TV 端性能优化路线（三阶段）

> 依据：[ADR-0001](../adr/0001-tv-motion-and-render-principles.md) · [调研报告](research.md)
> 优先级（用户体感）：**A 焦点移动 > D Now Playing 打开/切歌 > C 页面转场 > F 列表滚动**
> 构建不入库：不导出 APK，改动完成后由你或 CI 自行构建（`./gradlew assembleDebug`）。

## 阶段 1 — 低风险快赢

每项独立可回退。目标：不改变应用结构，只去掉最贵的每帧成本和最长的时间窗口。

| # | 改动 | 位置（预期） | 预期收益 | 风险 |
|---|------|--------------|----------|------|
| 1.1 | **Dev 菜单帧时间悬浮窗**（首个提交）。`Window.addOnFrameMetricsAvailableListener`（API 24+）统计每帧总耗时，显示近 5 秒 P50/P90 与掉帧计数；Dev 菜单开关，默认关闭。 | `ui/screens/DevMenuScreen.kt`、`ui/viewmodel/DevMenuViewModel.kt`、新增 `util/FrameStats.kt` | 让之后每个改动的效果可在真机上肉眼+数字复核 | 极低，纯诊断代码 |
| 1.2 | **去掉卡片焦点 glow**，保留 1.5dp 描边 + 1.08 缩放。涉及 `AlbumCard`（18dp）、`SpotlightHeroCard`/`SpotlightCard`（22dp）、`NowPlayingBar` 传输按钮（10dp）、歌词行（无 glow，确认即可）。 | `ui/components/AlbumCard.kt:45-55`、`SpotlightCards.kt:59-61,118-120`、`NowPlayingBar.kt:97-99` | A/F：每次聚焦少一次 `setShadowLayer` 模糊 | 低；视觉从"柔光"变"清晰描边"（已批准） |
| 1.3 | **NavHost 转场 700ms → 200ms** 淡入 + 8dp 位移（进入/退出/返回三组）。 | `ui/AppShell.kt:263` | C：每次导航从 ~42 帧全屏合成降到 ~12 帧 | 低；转场变快，已批准 |
| 1.4 | **封面硬切**：删除外层 Compose `Crossfade` 与内层 Coil `crossfade(1000)`，过渡交给背景渐变的调色动画。 | `NowPlayingScreen.kt:307-320` | D：切歌时少两层全屏/大图淡入合成 | 低；视觉更接近 Apple Music 的"直接换图" |
| 1.5 | **Now Playing 模式切换 Crossfade 450ms → 250ms**（播放器/全屏歌词/屏保）。 | `NowPlayingScreen.kt:228` | D | 低 |
| 1.6 | **EqBars 改为 drawBehind 延迟读取**：3 个 `rememberInfiniteTransition` 值与脉冲值不再 `by` 读在 composable 作用域，移入绘制 lambda（可复用 Skeleton 的写法）。 | `ui/components/TopNavBar.kt:31-45` | 全局：播放中顶栏不再每帧重组 | 低 |
| 1.7 | **启用 Compose 编译器稳定性报告**（`reportsDestination` + `metricsDestination`），CI/本地构建时产出，人工检查不稳定参数。 | `android/app/build.gradle.kts` | 本地可量化的验收依据；发现整行重组的隐藏项 | 低，仅构建配置 |
| 1.8 | **补 `contentType`**：`HomeScreen` 的 `LazyColumn items`、`LibraryScreen` 两个 `LazyVerticalGrid`。 | `HomeScreen.kt:65-75`、`LibraryScreen.kt:294,391` | F：Lazy 复用策略更准 | 低 |

**阶段 1 验收**：Compose 稳定性报告无新增不稳定项；模拟器冒烟（焦点走位、页面进出、Now Playing 切换、歌词滚动无异常）；真机安装后 GPU 呈现条 + 悬浮窗对比改前改后。

## 阶段 2 — 结构性改动

| # | 改动 | 预期收益 | 风险/注意 |
|---|------|----------|-----------|
| 2.1 | **DynamicBackground 30fps 节流**：用自定义 `withFrameNanos` 计时器（或对 `t1s..t4s` 做量化）把绘制失效降到 30fps；暂停/Reduce Motion 时彻底冻结（现有逻辑保留）。 | D：Now Playing 稳态绘制成本减半 | 中；改动集中在 draw 触发，需确认 `BlendMode.Screen` 合成行为不变 |
| 2.2 | **图片管线审计**：逐屏确认每张海报都拿到与显示尺寸匹配的 URL/`ImageRequest.size()`；网格与列表的大图是否有多余解码；`PlaylistCard` 等使用服务端已 resolve 的 URL 的场景确认尺寸合理。 | A/F：减少解码 CPU 与 GC 抖动 | 低-中；需逐个屏幕核对，避免把已正确的改坏 |
| 2.3 | **封面 motion loop 策略复核**：`MOTION_ENABLED = true` 时 Now Playing 会起第二个视频解码器；在低端设备上评估是否默认关闭或改为仅在 Dev 菜单开启。 | D：少一个解码器竞争 | 中；涉及产品决策（动效可见性） |
| 2.4 | **下一首封面预取**：转场前完成解码（内存缓存命中）。 | D：切歌转场顺滑 | 低 |
| 2.5 | **评估 Baseline Profile / R8**：仓库当前 CI 构建 debug 包，收益主要在启动与首屏；若后续改为 release 分发再启用。 | 启动/首屏 | 低，暂缓 |
| 2.6 | **首页 `LazyColumn` 套 `LazyRow` 结构评估**：官方已知的纵向滚动性能问题；评估固定行高 + `contentType` 是否足够，不轻易重构。 | F | 中高；重构风险大，非必要不动 |

**阶段 2 验收**：同上，另加"改前后各录制 30 秒同一操作序列的 GPU 呈现条"。

## 阶段 3 — 真机复核与数据补测

- 用帧时间悬浮窗 + GPU 呈现条对比阶段 1/2 改动的效果；
- 若家庭网络恢复（换路由器/关闭客户端隔离），运行 `tools/measure-tv.sh`（待建）：连接 → 清计数 → `input keyevent` 导航脚本 → `dumpsys gfxinfo <pkg> framestats` → 解析 janky% / P50/P90/P95；
- 根据真机数据决定是否需要：进一步降 `DynamicBackground` blob 数、调整焦点动画时长、或回退某项改动。

## 未决/观察项

- tv-material 1.0.0 的 `Card` 焦点缩放动画时长不可配（内部实现），如真机显示焦点动画仍偏长，需评估替换为自绘焦点态。
- `tv-foundation` 1.0.0 的 AAR 实际只含 3 个类（无 TvLazy* API），与常见文档不符——升级依赖前需核实，现阶段不依赖它。
- iPhone/Android 热点在客户端隔离下也无法打通 adb；若将来换路由器，优先恢复 adb 量化测量。
