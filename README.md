# 文言通 WenYanTong

一款采用 Claude 暖白与陶土橙视觉的 Android 学习工具。当前版本 **0.6.1** 修复 AI 出题题型不匹配：仅请求所选题型，兼容常见名称，格式不合要求最多重新生成一次。提供中英互译、文言白话互译、多题型、任意查询 AI 出题、错题编辑、多 API 管理及用户自建 Skills；保留 14 个厂商预设和 10 个独立在线资源包。操作见 [LEARNING_0.6.0.md](docs/LEARNING_0.6.0.md)。

源码与下载：[GitHub 仓库](https://github.com/L-use/WenYanTong) · [最新安装包](https://github.com/L-use/WenYanTong/releases/latest)。版本更新规范见 `docs/RELEASING.md`，本地路径与导入导出见 `docs/USER_DATA.md`。

## 已实现

- Kotlin + Jetpack Compose 原生界面，首页、结果、词典、古文、释义浮层、练习、历史收藏、资源说明与设置。
- 随 APK 安装 SQLite 示例库；SHA-256、SQLite 完整性和 schema 版本校验；资源库只读打开，个人数据独立保存。
- 中英互译、文言白话互译两种入口，自动判断方向；离线使用已收录对照，自动/AI 模式可请求模型补充。搜索框“本地资源”标签仅在离线模式显示。
- 8 个字词、12 个义项、8 段古文、2 组原创双语对照、10 道单选题。释义和例句标注篇章出处。
- 点击原文中的已收录字词，打开多义项与例句浮层；从字词/结果进入关联练习。
- 单选、判断、选词填空可选；答题判分、解析、练习报告、错题自动保存与复练，支持修改错题题干、选项、正确答案及解析。
- 本地历史搜索、收藏、二次确认删除；浅色/深色/跟随系统、正文字号设置。
- 自动模式本地优先，AI 模式请求已配置模型，离线模式不调用模型。AI 输出与本地收录分别标记。
- 应用内检查 GitHub 发布、展示说明、下载安装；哈希、包名、版本与签名校验。允许联网时每天最多自动检查一次，离线手动检查需确认。
- 查看实际安装/数据路径，使用系统文件选择器导出 JSON 学习备份，预览并合并导入，重复记录去重。
- 离线资源页独立检查、下载、校验并安装基础资源库，安装后即时查看词条、古文并用于练习。支持本地 ZIP 导入，失败保留原库，个人数据独立保存。
- 可下载常用基础库 1.0.0：32 词条、36 义项、35 组文本对照、34 道单选题。保留原内置示例，无网络也能使用。
- 10 个独立可选包：原有 8 包及 KyleBing 六级词包（3,991 个不同单词）、四六级词频包（5,276 个不同单词）；各包独立更新删除，可按中文/英文和资源类型筛选。词频包保留非商业/署名/相同方式共享限制。
- 全局查词覆盖已下载包，同字聚合并保留不同释义/译文及其出处；大包释义按需加载，古文篇目分页搜索、长篇正文分段显示。
- 古文观止译注包有 222 篇、13,717 个不同字词，现代译注由上游 AI 生成，明确标注；原文包不假装提供白话译文。来源与授权见 `docs/USER_RESOURCE_COLLECTION.md`。
- 修正完整词条命中仍突出“尚无收录译文”的提示，直接展示词义；英文检索不区分大小写。
- 设置中填写 AI Base URL、协议、模型和 API Key，支持兼容 Chat Completions / Claude Messages 及连接测试；Key 经 Keystore 加密，不进入用户备份。
- API 可添加、命名、切换、编辑、删除，独立保留最近连接测试状态；Skills 展示两个内置技能，支持自建指令、任务范围、启停、编辑和删除。
- AI 练习结合当前查询及少量本地词义，核对返回题目结构和义项引用，保存题目快照、错题及备份；仍可使用离线题库。详见 `docs/AI_MODULE.md`。

## 当前边界

离线学习无需账号或 API Key。联网用于更新或用户配置的 AI 请求；AI 功能需要自己的服务商 Key，可能产生费用。尚未实现 OCR、多选、自由输入答案自动评分、收藏分类与完整的 34 页视觉稿。

示例库用于功能体验，释义、译文和题目为项目原创内容，尚未经学术审校。内置双语对照只有两组；下载词库可扩展离线词义，任意文本翻译与未收录查询出题需配置 AI。自动方向判断可能有歧义，AI 可在当前语言组合内纠正。Skills 是任务指令模板，不是手机脚本或外部工具执行器。

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

基础库输入是 `tools/pack-builder/sample.json`，其他包见 `tools/pack-builder/build_user_resources.py`、`build_shuowen.py`。多包发布运行 `python tools/release/publish_catalog.py`，独立上传版本 ZIP，最后更新 `resources-v2` 目录；无需 Actions。完整说明见 `docs/MULTIPACK_RELEASE.md`。`tools/pack-builder/publish.py` 仅维护旧版单库渠道。

## 测试与后续迭代

`app/src/test` 覆盖文本标准化、精确命中边界、判分边界。`app/src/androidTest` 覆盖真实 SQLite、反向查询、收藏错题持久化、无网络权限与 Compose 学习流程。设备测试：`gradlew.bat :app:connectedDebugAndroidTest`。

0.1.0 初版的历史验证记录见 `docs/VALIDATION.md`；0.2.0 和 0.3.0 验证分别见 `docs/VALIDATION_0.2.0.md`、`docs/VALIDATION_0.3.0.md`。实际截图和本机构建日志在被 Git 排除的 `dist/`。尚未进行物理真机、多系统版本与横屏验证。

架构与阶段记录见 `docs/ARCHITECTURE.md`、`docs/CHANGELOG.md`、`docs/TEST_PLAN.md`。下一阶段可继续扩充资源内容与多包支持，再接入 AI。

源码采用 MIT；原创示例内容采用 CC0-1.0；古籍原文为公有领域。第三方依赖见 NOTICE。
