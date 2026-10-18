# 第三方声明

[English](../THIRD_PARTY_NOTICES.md) | **简体中文**

Apple Music TV 基于 **GPL-3.0** 授权（见 [LICENSE](../LICENSE)）。它使用了下列第三方项目。每个项目保留自己的许可证；这些许可证适用于相应组件，而非本项目的其余部分。

## 改编代码

- **gamdl** — MIT — https://github.com/glomatico/gamdl
  服务器端的 Apple Music CENC 解密流程（通过 Apple 的 `acquireWebPlaybackLicense` 端点获取 Widevine 许可证、PSSH 重建，以及 `mp4decrypt` 交接）改编自 gamdl。相关文件：`server/get_key.py`、`server/stream_decrypt.py` 和 `server/ref_key.py`；这些脚本在运行时还会导入 gamdl 的 `WVD` 设备。设备端 Kotlin 移植版本（`AppleMusicDrmCallback`、`AppleDirectClient`）沿用相同的授权方式，使用 Android 原生 MediaDrm。

## 运行时依赖

- **pywidevine** — GPL-3.0 — https://github.com/devine-dl/pywidevine
  Python 解密脚本用来获取内容密钥的 Widevine CDM。其 copyleft 许可证正是本项目采用 GPL-3.0 分发的原因。
- **Bento4 / mp4decrypt** — GPL-2.0 或商业许可 — https://github.com/axiomatic-systems/Bento4
  用于移除解密音频中 CENC 加密的外部二进制程序。
- **FFmpeg** — LGPL-2.1+/GPL — https://ffmpeg.org
  服务器端用于重封装/转码音频的外部二进制程序；设备端通过 Media3 FFmpeg 解码器扩展使用。
- **AndroidX、Jetpack Compose、Media3（ExoPlayer）** — Apache-2.0 — https://developer.android.com/jetpack
  Android / Fire TV 应用的 UI 工具包（Compose for TV）与媒体播放栈。
- **Hilt / Dagger** — Apache-2.0 — https://dagger.dev/hilt
  Android 应用的依赖注入。
- **OkHttp、Retrofit、Moshi** — Apache-2.0 — https://square.github.io/okhttp
  Android 应用的 HTTP 客户端、REST 层与 JSON 解析。
- **Coil** — Apache-2.0 — https://coil-kt.github.io/coil
  Android 应用的封面图加载。
- **Hono** — MIT — https://hono.dev
  可选 PC 代理服务器的 Web 框架。
- **@syncfm/applemusic-api** — MIT — https://github.com/syncfm/applemusic-api
  代理服务器用于搜索和目录调用的 Apple Music Web API 客户端。
- **axios** — MIT — https://github.com/axios/axios
  代理服务器使用的 HTTP 客户端。
- **httpx** — BSD-3-Clause — https://www.python-httpx.org
  Python 解密脚本使用的 HTTP 客户端。

## 数据来源

- **Apple Music** — https://music.apple.com
  提供所有目录内容、封面、编辑推荐数据和主要歌词。本项目与 Apple 无关联，也未获其认可。你需要自备 Apple Music 订阅；音频仅在本地解密供个人播放使用。
- **LRCLIB** — https://lrclib.net
  社区歌词数据库，在 Apple 没有该曲目时间轴歌词时作为逐行同步歌词的备选来源。
