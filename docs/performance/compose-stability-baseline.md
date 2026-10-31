# Compose 稳定性基线（阶段 1）

> 2026-10-31 · 分支 `perf/10-review-fixes`（阶段 1 集成 tip `33d4d8d` + 代码审查修复）
> 依据：Compose 编译器稳定性报告（工单 #4）。本文件是「当前不稳定参数清单」的人工基线，后续阶段对比用。

## 构建配置

报告与指标由 `android/app/build.gradle.kts` 的 `composeCompiler` 块启用：

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
}
```

- 报告：`android/app/build/compose_reports/app_debug-{composables,classes}.txt`（另有 `.csv`）
- 指标：`android/app/build/compose_metrics/app_debug-module.json`
- 这些目录不是 Gradle 任务的输出，编译 up-to-date 时不会重写；刷新基线必须加 `--rerun`。

## 重新生成

```bash
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :app:compileDebugKotlin --rerun --no-daemon
```

两个报告目录都在 `app/build/` 下，已被 gitignore，不进 APK、不影响运行时。

## 汇总

| 指标 | 数值 |
|------|------|
| 本模块 composable 总数 | 124 |
| restartable | 124 |
| skippable | 124 |
| 至少有一个不稳定参数的 composable | 42 |
| 不稳定参数出现次数 | 54 |
| 涉及文件 | 17 |
| 报告中的不稳定类 / 稳定类 | 172 / 65 |
| StrongSkipping | 开启（Kotlin 2.1.20 Compose 编译器默认，`build.gradle.kts` 未覆盖 featureFlags）|

StrongSkipping 下不稳定参数按**实例身份**比较：同一个实例回流仍可跳过，但每次传入新的 `List`/`Set`/holder 实例都会让该 composable 重新执行。

### 主要成因

1. **集合参数**：`List<Album>`、`List<Song>`、`Set<String>`、`List<Pair<…>>` 等没有 `@Immutable` 包装，全部判为不稳定（`LibraryScreen`、`BrowseScreen`、`NowPlayingScreen` 歌词/队列最集中）。
2. **不稳定 holder 类**：`GenreSection`、`HomeSection` 未被标注/推断为稳定，导致 `ContentRow`、`GenreScreen` 的行组件整行重组。
3. **ViewModel / BeatAnalyzer 仅按身份稳定**：`PlayerViewModel`、`LibraryViewModel`、`MusicVideoViewModel`、`NavigationViewModel`、`OnboardingViewModel`、`BeatAnalyzer` 是 Hilt/单例对象，实际不会换实例，但编译器把它们列为不稳定参数，无法跳过（收益低，留给阶段 2 用 `@Stable` 标注或 lambda 化评估）。

## 不稳定参数清单（按文件）

路径相对 `android/app/src/main/java/com/applemusicktv/`。

### ui/components/AddToDialog.kt
- `AddToDialog(playerVm: PlayerViewModel)`

### ui/components/NowPlayingBar.kt
- `NowPlayingBar(playerVm: PlayerViewModel)`

### ui/components/TopNavBar.kt
- `EqBars(beatAnalyzer: BeatAnalyzer?)`
- `TopNavBar(beatAnalyzer: BeatAnalyzer?)`

### ui/screens/AlbumDetailScreen.kt
- `AlbumDetailScreen(playerVm: PlayerViewModel)`

### ui/screens/ArtistDetailScreen.kt
- `ArtistDetailScreen(playerVm: PlayerViewModel)`
- `AlbumRow(albums: List<Album>)`

### ui/screens/BrowseScreen.kt
- `BrowseScreen(playerVm: PlayerViewModel)`
- `SpotlightRow(albums: List<Album>, playerVm: PlayerViewModel?)`
- `BrowseVideoRow(videos: List<Song>, playerVm: PlayerViewModel)`
- `BrowseRow(albums: List<Album>, playerVm: PlayerViewModel?)`

### ui/screens/CategoryScreen.kt
- `CategoryScreen(playerVm: PlayerViewModel?)`

### ui/screens/DevMenuScreen.kt
- `DevMenuScreen(playerVm: PlayerViewModel)`
- `PhoneServerRow(playerVm: PlayerViewModel)`

### ui/screens/GenreScreen.kt
- `GenreScreen(playerVm: PlayerViewModel)`
- `AlbumRow(section: GenreSection)`
- `VideoRow(section: GenreSection, playerVm: PlayerViewModel)`

### ui/screens/HomeScreen.kt
- `HomeScreen(playerVm: PlayerViewModel, vm: HomeViewModel?)`
- `ContentRow(section: HomeSection, playerVm: PlayerViewModel)`

### ui/screens/LibraryScreen.kt
- `LibraryScreen(vm: LibraryViewModel, playerVm: PlayerViewModel)`
- `SortDialog(fields: List<Pair<SortField, Int>>)`
- `PlaylistGrid(playlists: List<PlaylistDto>, pinnedIds: Set<String>)`
- `AlbumGrid(albums: List<Album>)`
- `ArtistList(artists: List<Artist>)`
- `SongList(songs: List<Song>, playerVm: PlayerViewModel)`
- `PlaylistPickerDialog(playerVm: PlayerViewModel)`

### ui/screens/MusicVideoScreen.kt
- `MusicVideoScreen(vm: MusicVideoViewModel, queue: List<Song>?)`

### ui/screens/NowPlayingScreen.kt
- `NowPlayingScreen(playerVm: PlayerViewModel, navVm: NavigationViewModel)`
- `FullScreenLyrics(lyrics: List<LyricLine>)`
- `PlayerProgressBar(player: Player)`
- `DynamicBackground(beatAnalyzer: BeatAnalyzer)`
- `LyricsPanel(lyrics: List<LyricLine>)`
- `WordWipeLine(words: List<LyricWord>)`
- `QueuePanel(queue: List<Song>, userQueue: List<Song>?)`

### ui/screens/OnboardingScreen.kt
- `OnboardingScreen(vm: OnboardingViewModel)`
- `StepLanguage(vm: OnboardingViewModel)`
- `StepAccount(vm: OnboardingViewModel)`（参数未被使用，报告标记 `unused unstable`）
- `StepRemote(vm: OnboardingViewModel)`
- `StepPreferences(vm: OnboardingViewModel)`

### ui/screens/PlaylistDetailScreen.kt
- `PlaylistDetailScreen(playerVm: PlayerViewModel)`

### ui/screens/RadioScreen.kt
- `RadioScreen(playerVm: PlayerViewModel)`

### ui/screens/SearchScreen.kt
- `SearchScreen(playerVm: PlayerViewModel)`

## 怎么读这份基线

- 阶段 1 的目标是「无新增不稳定项」：本表是新的对照基线。
- 优先看**集合参数**与 **holder 类**（第 1、2 类成因）：它们会在每次父级重组时制造新实例，是整行/整块重组的来源。
- ViewModel/`BeatAnalyzer` 参数（第 3 类）不建议仅为了报告好看去改：对象身份不变，收益有限。
