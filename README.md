# 文言通 WenYanTong

一款采用 Claude 暖白与陶土橙视觉的 Android 古文学习工具。当前版本 **0.2.0** 加入 GitHub 稳定版更新与用户数据备份，按详细设计说明书分阶段实现。

源码与下载：[GitHub 仓库](https://github.com/L-use/WenYanTong) · [最新安装包](https://github.com/L-use/WenYanTong/releases/latest)。版本更新规范见 `docs/RELEASING.md`，本地路径与导入导出见 `docs/USER_DATA.md`。

## 已实现

- Kotlin + Jetpack Compose 原生界面，首页、结果、词典、古文、释义浮层、练习、历史收藏、资源说明与设置。
- 随 APK 安装 SQLite 示例库；SHA-256、SQLite 完整性和 schema 版本校验；资源库只读打开，个人数据独立保存。
- 古今汉语与中英四方向的**已收录文本对照**。仅完整匹配返回译文，未知句子明确提示无译文；字词命中单独展示。
- 8 个字词、12 个义项、8 段古文、2 组原创双语对照、10 道单选题。释义和例句标注篇章出处。
- 点击原文中的已收录字词，打开多义项与例句浮层；从字词/结果进入关联练习。
- 单选题提交、规则判分、解析、练习报告、错题自动保存与复练。
- 本地历史搜索、收藏、二次确认删除；浅色/深色/跟随系统、正文字号设置。
- 自动、AI、离线模式偏好保存。初版所有模式实际均只查询本地资源，界面明确提示。
- 应用内检查 GitHub 发布、展示说明、下载安装；哈希、包名、版本与签名校验。允许联网时每天最多自动检查一次，离线手动检查需确认。
- 查看实际安装/数据路径，使用系统文件选择器导出 JSON 学习备份，预览并合并导入，重复记录去重。

## 当前边界

无需登录、API Key 或 GitHub 配置。联网权限仅用于已实现的 GitHub 更新渠道；学习查询仍使用本地资源。尚未实现云端 AI、OCR、独立资源包在线更新、多选等其余六种题型、收藏分类与完整的 34 页视觉稿。

示例库用于功能体验，释义、译文和题目为项目原创内容，尚未经学术审校。双语资源仅包含“你好。”和“温故知新。”两组，不提供任意文本翻译。古文反向查询需输入已收录的对应译文。

## 快速体验

1. 从 GitHub Releases 下载最新签名 APK，支持 Android 8.0 / API 26 及以上。
2. 首页输入“之”并查询，点击“之 / zhī”打开释义。
3. 浏览四个义项与出处例句，点击“练习「之」的用法”。
4. 选择题量开始练习；仅有四道关联题时，实际组卷为四题。
5. 提交后查看解析，完成后查看报告；错题在“练习 → 我的错题本”复练。
6. 首页“今日读一句”可体验完整古文译文与解析；历史页可收藏和再次查询。

GitHub 发布包关闭调试功能，并保持固定签名身份以支持覆盖升级。密钥仅在本机保存；GitHub 接收签名后的安装包。后续直接运行 `./tools/release/publish.ps1`，本机完成资源校验、测试、lint、构建和签名校验后发布，全程不使用 Actions。

## 构建

构建版本固定：AGP 9.0.1、Gradle 9.1.0、内置 Kotlin 2.2.10、Compose BOM 2025.08.01。Gradle 可用 JDK 17–25，字节码目标为 Java 17。Android SDK Platform 36 与 Build Tools 36.0.0（由 AGP 选择）。

1. 使用 Android Studio 打开本目录。
2. 在 `local.properties` 中配置本机 `sdk.dir`，该文件不提交版本控制。
3. Windows：`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。
4. macOS/Linux：`chmod +x gradlew && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。
5. 输出：`app/build/outputs/apk/debug/app-debug.apk`。

如命令行缺少 Java，可把 Android Studio 自带的 `jbr` 目录配置为 `JAVA_HOME`。

## 资源构建

使用 Python 3 标准库运行：

```sh
python tools/pack-builder/build.py
```

输入是 `tools/pack-builder/sample.json`，输出是 `app/src/main/assets/packs/{sample.db,manifest.json}` 和 `dist/resources/sample-basic-0.1.0.zip`。脚本检查 SQLite 完整性、外键与答案索引，并生成实际哈希、文件大小和内容计数。此 ZIP 是初版示例构建产物，尚无在线安装器或签名索引。

## 测试与后续迭代

`app/src/test` 覆盖文本标准化、精确命中边界、判分边界。`app/src/androidTest` 覆盖真实 SQLite、反向查询、收藏错题持久化、无网络权限与 Compose 学习流程。设备测试：`gradlew.bat :app:connectedDebugAndroidTest`。

0.1.0 初版的历史验证记录见 `docs/VALIDATION.md`；0.2.0 的发布与新增功能验证另见 `docs/VALIDATION_0.2.0.md`。实际截图和本机构建日志在被 Git 排除的 `dist/`。尚未进行真机、多系统版本与横屏验证。

架构与阶段记录见 `docs/ARCHITECTURE.md`、`docs/CHANGELOG.md`、`docs/TEST_PLAN.md`。下一阶段扩充资源 schema 和独立包安装，再接入 AI；不把规划能力写成已实现能力。

源码采用 MIT；原创示例内容采用 CC0-1.0；古籍原文为公有领域。第三方依赖见 NOTICE。
