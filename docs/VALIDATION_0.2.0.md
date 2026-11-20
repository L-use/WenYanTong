# 文言通 0.2.0 验证记录

验证日期：2026-11-20。此版新增 GitHub 更新、用户数据导入导出和本地路径设置。

本机构建：debug / release / AndroidTest APK 均成功；6 项 JVM 单元测试通过；debug 与 release lint 无错误。正式 release 关闭 debuggable，保留初版签名身份。

模拟器 Android 17 / API 37：学习流程、设置页面、离线更新确认、更新连接拦截、错误哈希拒绝、包签名与版本验证、备份往返与重复去重、损坏备份拒绝、文档 URI 导出/预览/导入共 9 项执行通过。常规套件另包含两个需显式启用的升级/线上联调场景，默认跳过；二者已另行启用并通过。

独立升级测试：先安装 0.1.0 并写入收藏与错题，覆盖安装非调试 0.2.0 release APK，安装成功；收藏、错题与数据完整保留，正式包 debuggable 标志为 false。

真实线上联调：从 https://github.com/L-use/WenYanTong/releases/tag/v0.2.0 读取实际 latest 发布与 update.json，注入旧版 versionCode 1 模拟待升级客户端，下载公开发布的完整 APK，并通过大小、SHA-256、包名、版本、签名校验。该测试通过，不是仅使用本地假数据。

GitHub 公开仓库与 v0.2.0 发布已上线，包含 APK、update.json、SHA256SUMS 三个附件。本机签名材料没有上传 GitHub；已配置的 CI 只构建无签名候选。云端任务在执行任何步骤前被账户级执行器限制拦截，未声称 GitHub CI 通过；首次发布使用全部通过的本机校验和签名。后续可使用 publish.ps1 -LocalChecksOnly，恢复云端执行器后可省略该参数。

测试日志及截图在本机 `dist/` 和 `.tools/`，均不提交。尚未进行真机、多系统版本、断点续传或全量性能验证。备份上限 20 MB / 5 万条记录；导入采用合并策略。
