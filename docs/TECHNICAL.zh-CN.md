# 技术细节

以下内容过去都放在 README 里。移到这里是为了让首页专注于*使用*应用；本页面向构建和理解它。

[English](TECHNICAL.md) | **简体中文**

## 模式

| 模式 | 运行内容 | 何时使用 |
|------|-----------|-------------|
| **独立模式**（默认） | 电视上纯 Kotlin 的软件 **Widevine CDM** + CENC 解密器在应用内解密每首歌。无需电脑。 | 日常使用 —— 无需运行其他东西。 |
| **代理模式**（可选） | PC 服务器获取并解密音频，通过局域网提供可拖动的 MP4。 | 调试，或应用内解密吃力的设备。 |

在 **⚙ 设置 → 播放 → 本地独立播放** 中切换，或在 **设置 → 打开开发者菜单 → PC 服务器** 中把应用指向一台电脑。

## 架构

| 部分 | 技术栈 |
|------|--------|
| **Android 应用** | Jetpack Compose for TV、Media3 ExoPlayer、Hilt、Retrofit + Moshi、Coil |
| **设备端解密**（独立模式） | 纯 Kotlin 软件 **Widevine L3 CDM** + `cenc` AES-CTR fMP4 解密器，把明文的 AAC 直接送入 ExoPlayer |
| **代理服务器**（可选） | Bun + Hono，封装 `amp-api-edge.music.apple.com` + `play.itunes.apple.com` |
| **代理端解密** | `gamdl` + `pywidevine`（Widevine 许可证）+ `mp4decrypt` + `ffmpeg` 重封装 |

**独立模式：** 应用直接与 `amp-api-edge.music.apple.com` 通信，获取 Widevine 许可证，在设备端推导内容密钥，解密 CENC 音频并播放 —— 全程不涉及电脑。**代理模式（可选）：** 改由服务器获取/解密/重封装，并通过 HTTP Range 提供可拖动的 MP4。

## 获取你的 Music-User-Token

电视上运行着一个监听 **8080** 端口的小型 Web 服务器。在手机上打开 `http://<TV_IP>:8080` 并粘贴你的 Music-User-Token。它会保存在设备上（如果你使用代理服务器，也会同步过去）。

获取 Token 的步骤：
1. 在浏览器中打开 `music.apple.com`。
2. 开发者工具 → Network 面板。
3. 点击任意会加载音乐内容的操作。
4. 找到任意发往 `amp-api-edge.music.apple.com` 的请求。
5. 复制 `Music-User-Token` 请求头的值。

**bearer token** 会从 `music.apple.com` 的 JS bundle 中自动抓取 —— 你永远不需要手动设置它。

> 你的 MUT 拥有对你 Apple Music 资料库和账户的完全访问权限 —— 请像对待密码一样对待它。见 [SECURITY.zh-CN.md](SECURITY.zh-CN.md)。

## 可选的 PC 代理服务器

独立模式完全不需要这些。只有当你想要代理音频路径时才需要搭建服务器。

**前置条件（macOS/Linux）：** [Bun](https://bun.sh)；Python 3 以及 [`gamdl`](https://github.com/glomatico/gamdl) + `pywidevine`；`PATH` 中有 `mp4decrypt`（Bento4）和 `ffmpeg`。

### 一键安装

安装脚本会装好一切（Bun、Python + gamdl + pywidevine、ffmpeg、Bento4 mp4decrypt）并写出 `server/.env`。gamdl 自带内嵌的 Widevine 设备，因此不需要 `.wvd` 文件。

**macOS：**
```bash
cd server
./setup-mac.sh
bun run src/index.ts      # http://0.0.0.0:3000
```

**Windows（PowerShell）：**
```powershell
cd server
powershell -ExecutionPolicy Bypass -File .\setup-windows.ps1
bun run src/index.ts
```

<details>
<summary>手动安装 / 脚本做了什么</summary>

安装 `bun`、`ffmpeg`、`mp4decrypt`（Bento4）和 Python；创建虚拟环境并 `pip install gamdl pywidevine httpx`；然后把 `.env.example` 复制为 `.env` 并设置路径。`.env`（已 gitignore）保存机器相关的值：

```
GAMDL_SITE=              # 当 PYTHON_BIN 是已装好 gamdl 的 venv 时留空
PYTHON_BIN=/path/to/.venv/bin/python
MP4DECRYPT_BIN=mp4decrypt
FFMPEG_BIN=ffmpeg
```
</details>

### 把应用指向服务器

把本机的局域网 IP 加到 `android/local.properties`（已 gitignore），或在开发者菜单中运行时设置：

```
proxyBaseUrl=http://192.168.1.50:3000/
```

### 认证状态存储

两个 token 都持久化在 `server/auth-state.json`（已 gitignore）中：

```json
{ "mut": "...", "bearerToken": "eyJ...", "mutSetAt": 1234567890000 }
```

绝不提交。要重置：删除 `auth-state.json` 并重启 —— bearer 会被重新抓取，MUT 则通过手机 Web 服务器重新输入。

## 从源码构建

**Android：**
```bash
cd android
./gradlew assembleDebug
adb connect <TV_IP>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

要求：Android Studio / SDK（compileSdk 35）、JDK 17、一台开启 ADB 的电视设备。

## CI 与发布

**Android APK** 工作流（[`.github/workflows/android.yml`](../.github/workflows/android.yml)）在每次推送到 `main` 的 push/PR 以及手动触发时运行。每次运行都会配置 JDK 17 + SDK、写入默认的 `local.properties`、运行单元测试、构建 debug APK，然后以带版本号的文件名暂存：tag 构建为 `AppleMusicTV-<tag>.apk`，分支构建为 `AppleMusicTV-<versionName>-dev-<sha>.apk`。推送到 `main` 时，它还会刷新滚动的 **`dev`** 预发布（每次推送都删除并重建，使其排在 Releases 列表顶部）。

**获取 APK：** 仓库 **Releases** → 最新构建 → `AppleMusicTV-dev.apk`（始终指向最新 dev 构建的固定别名，无需登录），或某次运行的 **Actions → Artifacts**。稳定版的文件名带版本号，例如 `AppleMusicTV-v1.4.apk`。用 `adb install -r <下载的文件>.apk` 侧载。

### 发布版本（以及应用内更新器）

应用启动时会轮询 `releases/latest`。要发布更新，请同时提升 `android/app/build.gradle.kts` 中的 **`versionCode`** 和 **`versionName`**，然后打一个比当前构建版本号更高的 GitHub Release tag（例如 `v1.2`）。更新器会去掉开头的 `v` 后按数字比较；相同版本不会提示。把 Release 标记为 **prerelease** 只会推送给在 设置 → 软件 中启用了 **Beta 更新** 的用户。

### 提交 / 推送

`main` 是工作分支；推送到那里会触发工作流并刷新 `dev` 发布。原生 Widevine 库位于 `android/app/src/main/jniLibs/`，FFmpeg Media3 解码器位于 `android/app/libs/` —— 两者都已提交（构建需要它们）。机密（`auth-state.json`、`*.keystore`、`local.properties`、`.env`）已被 gitignore，绝不能提交。
