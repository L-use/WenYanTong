# 文言通 0.1.0 验证记录

验证日期：2026-11-20。交付范围为离线学习初版，尚未覆盖完整 V1 规划。

## 实际执行结果

| 检查 | 结果 |
| --- | --- |
| Android debug APK 与测试 APK 构建 | 通过 |
| Kotlin/JVM 单元测试 | 3 项通过，0 失败 |
| SQLite 示例资源校验测试 | 2 项通过，0 失败 |
| Android 设备测试 | 3 项通过，0 失败；AndroidJUnitRunner 输出 OK (3 tests) |
| Android lint | 0 errors，9 warnings；均为固定依赖版本或 target SDK 的更新建议 |
| APK 签名 | apksigner verify 通过；APK Signature Scheme v2，1 个调试签名者 |
| 截图检查 | 首页、查询、释义、答题、解析、报告、深色主题、26sp 正文与可滚动浮层 |

构建使用 Android Studio JBR 25.0.4、Gradle 9.1.0、AGP 9.0.1、SDK Platform 36。设备为现有 AVD 的只读临时运行实例，Android 17 / API 37，竖屏 1080×2400。UI 测试显式使用 Espresso 3.7.0 与 runner 1.7.0，修复旧测试依赖在 API 37 上的 InputManager 反射兼容问题。设备测试通过 adb am instrument 直接执行；未触发 GitHub Actions。

覆盖的真实流程：输入“之” → 四个候选义项及出处例句 → 关联四道单选题 → 提交/解析 → 报告 → 阅读设置 → 首页 → 最大字号释义滚动至练习按钮。测试发现并修正了底栏错误恢复旧练习页面的问题；视觉复核修正了深色浮层状态栏对比度与选中态配色。

数据测试验证了已知与未知文本、字词命中与整句译文的区别、双向对照、收藏和错题跨数据库实例持久保存，以及主应用没有 INTERNET 权限。资源测试校验实际 SHA-256、SQLite 完整性、外键、来源与关联 ID。

## 交付文件

安装包：`dist/WenYanTong-0.1.0-debug.apk`，18,292,448 字节，versionCode 1，minSdk 26，targetSdk 36。

SHA-256：`07d250d74c2c5169ff9604cf86bb2c47bad603e7f1bc0f4139170d2dae94cdfd`。

验证日志：`dist/validation/`。实际截图：`dist/screenshots/`。资源示例 ZIP：`dist/resources/sample-basic-0.1.0.zip`。

尚未进行真机、多 Android 版本、横屏及性能专项测试。AI、OCR、独立资源包下载更新和其他六种题型尚未实现，不属于本轮通过范围。源码、配置与资源构建流程见 README；调试包适合初版体验，正式发布需维护者配置正式签名。
