# 独立基础资源库（应用 0.3.0 起）

用户在「设置 → 离线资源」检查资源更新，点击「下载并安装资源包」。安装后立即点击「查看词条」或「查看古文」浏览；词典、查询、练习同步使用新库。默认离线模式检查前需确认联网，也可通过系统文件选择器导入本地 ZIP。

应用保留原内置示例库（8 词条、12 义项、10 组对照、10 题）；可下载基础库 1.0.0 包含 32 词条、36 义项、35 组对照、34 题。古文为节选，非完整教材或全文数据库。英文仍只有两组功能示例。

## 开发者发布

1. 编辑 `tools/pack-builder/sample.json`，维护 entries、passages、questions；现代注解须为原创或有清楚的可分发授权，保留原文作者、篇章出处。
2. 修改 `tools/pack-builder/resource.json`：version 使用三段版本号，revision 必须递增，notes 说明变化。保留已发布条目的 ID 与含义；新增内容使用新 ID，避免错题关联漂移。
3. 运行 `python tools/pack-builder/build.py` 和 `python tools/pack-builder/test_resources.py`，检查生成内容。输出到 `dist/resource-build/` 和 `dist/resources/common-basic-版本.zip`、`resources.json`，不会改动 APK 内置示例库。
4. 提交来源 JSON、资源版本配置及相关代码后，Windows 运行 `.\tools\pack-builder\publish.ps1`（或 `python tools/pack-builder/publish.py`）。脚本本地构建和校验、推送 main、创建 `resource-版本` Release 并上传 ZIP，最后替换独立 `resources-v1` Release 的 `resources.json`。不使用 Actions，不上传签名密钥。
5. 在手机检查、下载并抽查新增词条和题目。修改不兼容数据库格式时先升级应用；正常内容更新只需发布资源，不用重新发 APK。

资源 Release 均使用 `--latest=false`，APK 更新仍读取应用 stable latest。已发布资源禁止覆盖；索引是唯一可替换文件。索引固定地址：

https://github.com/L-use/WenYanTong/releases/download/resources-v1/resources.json

## 格式与校验

ZIP 根目录恰好两个文件：`manifest.json`、`sample.db`。禁止目录、其他文件、重复条目与路径穿越。数据库 schema=1，表为 source、dict_entry、dict_sense、passage、question。每个包是完整累积基础库，本期不提供多个不同主题库并存。

manifest 包含 id=`sample.basic`、version、revision、schemaVersion=1、minAppVersionCode、database=`sample.db`、内容计数及 files 数组（path、sha256、bytes）。索引包含 schemaVersion、id、version、revision、minAppVersionCode、url、bytes、sha256、notes。格式参考构建器实际输出。

下载只接受本仓库 `resource-版本` Release 的 HTTPS ZIP 地址，限制重定向、下载大小、解压大小（数据库 32 MiB，manifest 256 KiB），校验 ZIP 和数据库 SHA256、大小、数据库版本、SQLite 完整性、外键、表类型、字段、引用、题目答案和最低应用版本。当前信任 GitHub HTTPS 与仓库维护权限，哈希用于完整性检查，尚无独立离线数字签名；本地 ZIP 需由用户确认可信来源。

先写入唯一目录并完整校验，再打开新库、同步保存版本指针并切换只读连接。查询与连接切换互斥；失败不替换原库。个人记录独立保存。启动重新校验外部库，损坏则回退内置库。旧目录暂保留供排查，暂不提供手动切换旧版按钮。

古文为公有领域节选，现代释义、译文、解析和题目为项目原创 CC0-1.0，未经专业学术审校。未复制现代商业词典、教材译文或网站现代译注。来源见 `docs/RESOURCE_SOURCES.md` 和各条目。
