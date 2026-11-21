# 0.3.0 本地验证

- Windows 本地构建：testDebugUnitTest、lintRelease、assembleRelease、assembleDebug、assembleDebugAndroidTest 通过；不使用 GitHub Actions。
- JVM 测试 6 项通过；Python 资源关系及数据库测试 2 项通过。基础库 1.0.0：32 词条、36 义项、35 组对照、34 题。
- Android API 37 模拟器设备测试 18 项报告成功，其中 14 项实际执行通过，4 项条件测试默认跳过（真实 APK 渠道、真实资源下载、资源下载 UI、旧版本升级）。
- 新增验证覆盖安装切换与重启后加载、个人历史保留、ZIP 路径越界/未知文件/损坏内容拒绝、禁止降级、索引来源和兼容版本、下载哈希与大小、离线确认及查看词条入口。
- apksigner v2 签名验证通过，正式包无 debuggable 标志。实际安装公开 v0.2.0、写入收藏和错题，再覆盖安装正式 v0.3.0：升级测试通过，个人记录保留。
- 真实 GitHub 资源渠道：下载 ZIP、校验 SHA256/大小/版本/数据库、安装后查询新增古文及 34 道题通过。Compose 实际点击检查、下载、安装后搜索并打开新增“濯”词条通过，截图 `dist/screenshots/14-downloaded-resource-entry.png`。
- 真实 APK 更新渠道：请求公开 latest、下载完整 v0.3.0 APK、校验哈希/大小/包名/版本/签名通过（模拟旧客户端 versionCode=1）。GitHub stable latest 确认为 v0.3.0，资源发布使用独立索引且不覆盖它。
- 以上 4 项条件测试均已分别显式开启并通过。测试日志与截图保存在本地忽略目录 `.tools/`、`dist/`。

公开产物：应用 `v0.3.0`；资源 `resource-1.0.0`；独立资源索引 `resources-v1`。APK SHA256：`e9a772de8248aec78963ce8d372fca872a0f905b06ff4bd7920b4539221adc48`。

尚未完成物理手机、多 Android 版本、横屏与性能专项验证。基础库为教学节选，未经专业学术审校。
