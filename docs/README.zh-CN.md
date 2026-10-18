<div align="center">

![Apple Music TV](../images/banner.svg)

# Apple Music TV

**专为 Fire TV、Android TV 和 Google TV 打造的原生 Apple Music 体验。**

[English](../README.md) | **简体中文**

[![下载 APK](https://img.shields.io/badge/Download-APK-FA233B?style=for-the-badge&logo=android&logoColor=white)](https://github.com/AK9810/apple-music-tv/releases/download/dev/AppleMusicTV-dev.apk)
[![所有版本](https://img.shields.io/badge/All-Releases-333?style=for-the-badge&logo=github&logoColor=white)](https://github.com/AK9810/apple-music-tv/releases)

![status](https://img.shields.io/badge/status-active-brightgreen) ![platform](https://img.shields.io/badge/platform-Fire%20TV%20%7C%20Android%20TV%20%7C%20Google%20TV-blue) ![license](https://img.shields.io/badge/license-GPL--3.0-blue)

<br>

<img src="../images/nowplaying.gif" alt="正在播放演示" width="640">

</div>

> 需要自备 Apple Music 订阅。音频仅在本地解密，供个人播放使用。本项目与 Apple 无任何关联。

---

## 功能

- **立即聆听（Listen Now）、浏览（Browse）、资料库（Library）、搜索（Search）** —— 真实的 Apple 编辑推荐栏目，全部针对遥控器布局。
- **正在播放（Now Playing）**：随专辑封面变色的实时背景，以及**逐词同步歌词**。
- **艺人页、播放列表、专辑** —— 完整的目录浏览。
- **音乐视频**与**电台 / 电台节目** —— 均可播放。
- **后台播放**与氛围式**屏幕保护**。
- **在电视上独立运行** —— 纯 Kotlin 的 Widevine 管线在设备端解密每首歌，无需电脑；同时也支持可选的 PC 代理服务器。

---

## 截图

<div align="center">

**正在播放 —— 实时专辑配色背景 + 逐词同步歌词**

[![正在播放](../images/now-playing.png)](../images/now-playing.png)

</div>

| 立即聆听 | 浏览 |
|:---:|:---:|
| [![立即聆听](../images/listen-now.png)](../images/listen-now.png) | [![浏览](../images/browse.png)](../images/browse.png) |
| **资料库** | **搜索** |
| [![资料库](../images/library.png)](../images/library.png) | [![搜索](../images/search.png)](../images/search.png) |
| **音乐视频** | **分类** |
| [![音乐视频](../images/music-video.png)](../images/music-video.png) | [![分类](../images/category.png)](../images/category.png) |

---

## 安装

1. **下载 APK** —— [最新构建](https://github.com/AK9810/apple-music-tv/releases/download/dev/AppleMusicTV-dev.apk)，或从 [Releases](https://github.com/AK9810/apple-music-tv/releases) 中挑选一个版本。
2. **侧载到你的 Fire TV / Android TV**：
   ```bash
   adb connect <TV_IP>
   adb install -r AppleMusicTV-dev.apk
   ```
   （也可以使用 Downloader 之类的侧载应用，直接填入 Release 下载地址。）
3. **添加你的 Music-User-Token** —— 在手机上打开 `http://<TV_IP>:8080` 并粘贴。 [如何获取 Token →](TECHNICAL.zh-CN.md#获取你的-music-user-token)

独立模式不需要任何服务器。可选的 PC 代理方式见[技术文档](TECHNICAL.zh-CN.md#可选的-pc-代理服务器)。

---

## 开发

使用 Jetpack Compose for TV、Media3（ExoPlayer）、Hilt、Retrofit/Moshi 和 Coil 构建。可选的代理服务器是一个基于 Bun + Hono 的应用，封装了 Apple Music 的 Web API。

架构、设备端 Widevine/CENC 管线、认证流程、CI 和构建说明都在 **[TECHNICAL.zh-CN.md](TECHNICAL.zh-CN.md)** 中。深入阅读：[Apple Music API 笔记](apple-music-api.zh-CN.md) · [MUT 流程](mut-flow.zh-CN.md) · [投影模式](PROJECTOR_MODE_NOTES.zh-CN.md)。

构建方法见 [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md)，Token 处理见 [SECURITY.zh-CN.md](SECURITY.zh-CN.md)，后续计划见 [ROADMAP.zh-CN.md](ROADMAP.zh-CN.md)。

---

## 致谢

Apple Music 解密流程改编自 [gamdl](https://github.com/glomatico/gamdl)（MIT），并依赖 [pywidevine](https://github.com/devine-dl/pywidevine)（GPL-3.0）。

所有第三方组件的完整署名见 **[THIRD_PARTY_NOTICES.zh-CN.md](THIRD_PARTY_NOTICES.zh-CN.md)**。

## 许可证

基于 **[GPL-3.0](../LICENSE)** 授权。第三方组件保留各自的许可证（见 [THIRD_PARTY_NOTICES.zh-CN.md](THIRD_PARTY_NOTICES.zh-CN.md)）。

## 免责声明

仅供个人学习与教育用途。Apple Music、其内容及相关商标归 Apple 所有。本项目与 Apple 无关联，也未获得 Apple 认可。
