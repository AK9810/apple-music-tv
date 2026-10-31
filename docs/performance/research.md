# TV 端性能与动效调研报告

> 2026-10-31 · 基线设备：雷鸟 FF 55R69A（四核 Cortex-A73 / 3GB / 约 60Hz）
> 结论服务于 [ADR-0001](../adr/0001-tv-motion-and-render-principles.md) 与 [三阶段路线](roadmap.md)。

## 1. 代码审计发现（本仓库，已确认）

按"每帧真实成本"排序：

| # | 位置 | 事实 | 影响场景 |
|---|------|------|----------|
| 1 | `ui/components/AlbumCard.kt:45-55`、`SpotlightCards.kt:59-61` | tv-material 1.0.0 的 `CardDefaults.glow` 经反编译确认用 `Paint.setShadowLayer(blurRadiusPx, ...)` 绘制真模糊阴影（18–22dp）。聚焦卡片每帧都在做模糊。 | A 焦点移动 / F 列表滚动 |
| 2 | `ui/AppShell.kt:263` | `NavHost` 未配置 `enterTransition/exitTransition`，采用 navigation-compose 2.8.1 默认 **700ms 全屏淡入淡出**。每次导航两块全屏内容同时合成约 42 帧。 | C 页面转场 |
| 3 | `ui/screens/NowPlayingScreen.kt:307-320` | 封面 `Crossfade(tween(1000))` 外层 + Coil `crossfade(1000)` 内层 = 双层淡入；同屏还有 450ms 的 `Crossfade`（播放器/全屏歌词/屏保）和 60fps `DynamicBackground`。 | D 打开 Now Playing / 切歌 |
| 4 | `ui/components/TopNavBar.kt:31-40` | `EqBars` 用 `by` 在 composable 作用域读取 3 个 `rememberInfiniteTransition` 值和 1 个脉冲值 → 播放中整条顶栏每帧重组。违反 CLAUDE.md 自己的"不要 `by` 读每帧 State"戒律。 | 全局（顶栏常驻） |
| 5 | `ui/screens/NowPlayingScreen.kt:1099+` | `DynamicBackground` 已高度优化（draw 阶段延迟读取、零分配 scratch 数组、brush 缓存、3 blob、Screen 混合），但仍是 60fps 全屏绘制 3 个混合 blob + 暗角。 | D 氛围动效 |
| 6 | `AppleMusicApp.kt:31-43` | Coil 已配置 RGB_565、`crossfade(false)`、`respectCacheHeaders(false)`、40MB 内存缓存——无需改。 | — |
| 7 | 各 `AsyncImage(model = url)` 调用（28 处） | 多数传 `artworkUrl(size*2)` 的已替换尺寸 URL；`LibraryScreen:337`、`SearchScreen:519`、`PlaylistDetailScreen:122` 直接使用服务端已 resolve 的 URL。**未发现把 `{w}x{h}` 模板原样喂给 Coil 的路径**（服务端/直连路径均已替换）。 | — |
| 8 | `NowPlayingScreen.kt:100` | `MOTION_ENABLED = true`：Now Playing 封面在提供 motion URL 时额外起第二个视频解码器（封面 motion loop）。 | D |
| 9 | 构建配置 | Kotlin 2.1.20（strong skipping 默认开启）、Compose BOM 2024.09.00、tv-material 1.0.0、Coil 2.7.0。未配置 Compose 编译器稳定性报告，无 baseline profile。 | 全局 |
| 10 | `HomeScreen.kt:65` | 首页是 `LazyColumn` 套多个 `LazyRow`（电视端已知的滚动性能结构），items 未加 `contentType`。 | F |

## 2. 外部调研：成熟 TV 应用的动效词汇

| 产品 | 焦点 | 转场 | 列表滚动 | 氛围背景 | 歌词 |
|------|------|------|----------|----------|------|
| Netflix TV（*产品观察，未验证*） | 缩放 ~1.1–1.2 + 白色描边，无 glow/阴影 | 全屏快速淡入淡出 ~200–300ms，无共享元素 | 每次按键滚一行并吸附 | 静态模糊封面 + 暗色 scrim；"动"来自停留 500ms 后的静音视频预览 | — |
| Google TV / Compose for TV（*部分验证*） | 缩放 1.05–1.08 + 环/描边；时长 100–160ms 为主流实践 | 快速淡入/滑入，"动画 < 300ms"是社区共识（无官方出处） | 吸附式懒加载行；官方已知问题：`LazyColumn` 套 `LazyRow` 纵向滚动差 | 静态背景/渐变；屏保是慢速低帧率漂移 | — |
| Apple Music tvOS（*产品观察，未验证*） | 视差抬升 + 轻微倾斜 + 高光，短弹簧 | 封面放大的全屏过渡 | 横向货架，每次按键吸附 | 静态模糊封面圆盘（不是实时重算的渐变） | 逐行滚动 + 卡拉 OK 填充，无其他动效 |
| 网易云音乐 TV 版 | **未找到任何可靠的公开实现分析**；搜索只得到通用国产 TV UI 设计规范（缩放 1.1–1.2、焦点动画约 150ms、焦点样式 = 边框/放大/光晕/填充） | | | | |
| 夸克网盘 TV 版 | **同上，无公开资料** | | | | |

结论：国产竞品没有可借鉴的实现证据，"借鉴成熟设计"实际只能落到 Netflix / Google TV / Apple Music 这个层面；而这三者的共同点是 **焦点 = 描边 + 缩放（不用模糊光晕）、转场短（<300ms）、背景静态或低频动画**。

## 3. 技术清单（按预期收益排序）

| # | 技术 | 预期收益 | 成本 | 本项目适用性 |
|---|------|----------|------|--------------|
| 1 | 把逐帧动画值延迟到 `graphicsLayer`/`drawBehind` 中读取（composition → draw） | 最高。焦点移动只失效绘制层，不重组、不重新布局 | 低（机械改造） | ✅ 项目已大量采用，剩余热点见第 1 节 #4 |
| 2 | 焦点高亮避免 shadow/elevation，用描边 + 缩放 | 高。每次聚焦少一次模糊/离屏层 | 低 | ✅ 本项目主要缺口 = glow |
| 3 | 焦点/滚动事件里不做任何非绘制工作（分析上报、日志、每卡 `LaunchedEffect`） | 很高。有 Fire TV 实测：jank **2.8% → 0.1%**、p99 帧时间 **117ms → 49ms**（[来源](https://daily.dev/posts/improve-jetpack-compose-tv-scrolling-experience-zwamulqnf)） | 中 | ✅ 已有 `MotionArtwork` 1s 防抖先例，需全面复查 |
| 4 | 为每张海报传显式 `ImageRequest.size()`（约 tile 像素尺寸）；硬件位图 API 26+ 默认；无 alpha 用 RGB_565 | 高。1000×1000 ARGB ≈ 4MB vs 400×400 ≈ 0.6MB，约 6 倍解码 CPU / 上传带宽 / GC 抖动 | 低 | ⚠️ 本项目多数路径已按尺寸取图；阶段 2 审计一遍 |
| 5 | 网格中关闭 crossfade；大图封面保留单层短淡入 | 中高（列表滚动） | 低 | ✅ 本项目的双层 crossfade 是缺口 |
| 6 | 氛围背景：静态底层 + 低频动画高光；brush 只建一次；**30fps 节流**；暂停/不聚焦时冻结 | 高（Now Playing） | 中 | ✅ 已有 3 blob + scratch 复用；再加 30fps |
| 7 | 缩短焦点动画到 100–160ms、转场 ≤ 300ms | 中（减少落在昂贵窗口内的帧数） | 极低 | ✅ 主要缺口 = 700ms 默认转场、450ms 模式切换 |
| 8 | Lazy 列表：`key` + `contentType` + 固定 tile 尺寸 + 足够 contentPadding | 中（滚动） | 中 | ⚠️ 首页/网格需补 contentType |
| 9 | Compose 编译器稳定性报告：找出不稳定参数导致的整行重组 | 中（本地可量化，无需真机） | 低 | ✅ 未启用，阶段 1 加上 |
| 10 | 下一首封面预取（Now Playing 转场前解码完成） | 中（转场顺滑） | 低-中 | ⏳ 阶段 2 评估 |
| 11 | Baseline Profile + R8 | 启动/首屏中等收益，稳态 UI 收益小 | 低 | ⏳ 仓库无 baseline profile；阶段 2 评估（CI 构建 debug 包的现状下优先级低） |

## 4. 真机观测手段（无 adb 条件）

- **电视开发者选项 →「GPU 呈现模式分析 / Profile HWUI rendering」→ 屏幕上的柱状条**：每根柱 = 一帧，绿线 = 16.6ms 截止线；柱越过绿线即掉帧，颜色分段指示 input/动画、measure+layout、sync/upload、draw 命令、swap 等阶段。改前改后各录一段对比即可。
- **App 内帧时间悬浮窗（本项目将实现）**：`Window.addOnFrameMetricsAvailableListener`（API 24+）给出每帧总耗时与分阶段耗时；或 `Choreographer.postFrameCallback` 差值粗估。Dev 菜单开关，默认关闭。
- 注：`dumpsys gfxinfo framestats` 仍是最细的，但需要 adb——在当前网关客户端隔离下不可用，脚本方案保留待用。

## 5. 证据质量说明

- **已验证**：Fire TV jank 实测数字（daily.dev 文章）；Compose for TV 官方 release notes 中 `LazyColumn-of-LazyRow` 已知问题；国产 TV UI 设计规范（站酷/绘学霸）。`developer.android.com` 在当前网络下无法访问，官方文档条目按 canonical URL 引用但**本次未重新读取**。
- **未验证（产品观察）**：Netflix / Apple Music tvOS 的具体时长与样式，来自产品观察而非公开资料。
- **明确不存在**：网易云音乐 TV 版、夸克网盘 TV 版的任何公开实现分析。不要把它们的具体参数当作依据。
