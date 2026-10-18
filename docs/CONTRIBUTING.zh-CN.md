# 贡献指南

感谢你有兴趣参与。这是一个个人 / 教育性质的项目，但仍然欢迎 PR 和 Issue。

[English](../CONTRIBUTING.md) | **简体中文**

## 环境准备

- **Android 应用：** Android Studio、JDK 17、SDK compileSdk 35。构建：`cd android && ./gradlew assembleDebug`，安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`。
- **可选的代理服务器：** 见 [TECHNICAL.zh-CN.md](TECHNICAL.zh-CN.md#可选的-pc-代理服务器)。
- 端到端运行需要你自己的 Apple Music 订阅和 Music-User-Token。

## 代码位置

| 区域 | 路径 |
|------|------|
| Android 应用 | `android/app/src/main/java/com/applemusicktv/` |
| Compose 界面 | `.../ui/screens/`，共享 UI 在 `.../ui/components/` |
| 播放 / ExoPlayer | `.../ui/viewmodel/PlayerViewModel.kt`、`.../media/` |
| 设备端解密（独立模式） | `.../media/`（Widevine CDM、CENC 解密器、`AppleDirectClient`） |
| 代理服务器路由 | `server/src/routes/` |

关于各部分如何拼合、以及那些不那么显然的坑，见 [CLAUDE.md](../CLAUDE.md)（仓库里最详细的文档）。

## 约定

- 与周围代码风格保持一致 —— 本项目更青睐小而注释清晰的改动，而不是大范围重写。
- 改动数据获取逻辑时，请保持独立（设备端）与代理两条路径一致；绝大多数数据在 `DirectMusicDataSource` / `DirectBrowseSource` 中都有镜像实现。
- 绝不要提交机密信息：`auth-state.json`、`.env`、`local.properties` 和 `*.keystore` 被 gitignore 是有原因的。
- 尽量在真实的 Fire TV / Android TV 设备上测试 —— 模拟器会漏掉焦点、HDCP 和解码器行为等问题。

## Pull Request

- 从 `main` 拉出分支，保持 PR 聚焦，并说明你改了什么、如何验证。
- CI 会在每个 PR 上构建 APK；请确保它是绿的。
