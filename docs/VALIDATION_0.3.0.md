# 0.3.0 本地验证

- Windows 本地构建：testDebugUnitTest、lintRelease、assembleRelease、assembleDebug、assembleDebugAndroidTest 通过；不使用 GitHub Actions。
- JVM 测试 6 项通过；Python 资源关系及数据库测试 2 项通过。基础库 1.0.0：32 词条、36 义项、35 组对照、34 题。
- Android API 37 模拟器设备测试 18 项报告成功，其中 14 项实际执行通过，4 项条件测试默认跳过（真实 APK 渠道、真实资源下载、资源下载 UI、旧版本升级）。
- 新增验证覆盖安装切换与重启后加载、个人历史保留、ZIP 路径越界/未知文件/损坏内容拒绝、禁止降级、索引来源和兼容版本、下载哈希与大小、离线确认及查看词条入口。
- 签名与线上下载验证在正式发布后追加结果。测试日志和截图保存在本地忽略目录 `.tools/`、`dist/`。

尚未完成物理手机、多 Android 版本、横屏与性能专项验证。基础库为教学节选，未经专业学术审校。
