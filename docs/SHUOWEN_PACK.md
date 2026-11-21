# 说文解字大型独立资源包

本地文件：`dist/resources/shuowen/shuowen-dictionary-1.0.0.zip`。独立包 ID：`dictionary.shuowen`，版本 1.0.0，schemaVersion=2。

## 实际内容

| 项目 | 数量 |
| --- | ---: |
| 不同字头 | 9,815 |
| 上游原始条目 | 9,833 |
| 段玉裁注记录 | 22,897 |
| 原释和古注记录合计 | 32,730 |
| 索引/异体关联 | 12,990 |
| SQLite 大小 | 24,166,400 字节 |
| ZIP 大小 | 4,697,530 字节，约 4.48 MiB |

保留字头、拼音、反切、部首、卷次、说文原释、段玉裁注、异体字以及原始元数据。上游有 18 组重复字头，合并为同一字头下的多个原条目，未丢弃原释或古注。例如“學”在上游以“斆”为字头，另以异体索引关联；不能仅按字头检索。

这是古代字书资料，含大量生僻字、繁体和古字形，不等于 9,815 个现代常用文言词。古注条数不能当作独立现代词义数量。没有生成现代白话译文、例句译文、阅读篇章或练习题，也没有以空壳题目凑数。未做逐条学术校勘，保留上游读音与释文。

## 搜索及客户端要求

用户确认的产品规则：各包独立展示、下载、更新、删除；查词默认全局搜索所有已下载包，结果显示资源来源。未下载包不纳入离线查询。多个包收录同字时保留各自来源，不覆盖另一包的释义。

本次制作的是数据包和可复现构建器。**v0.3.0 只支持 `sample.basic` 整体替换，不能安装本包。** 因此没有把本包冒充基础库更新上传到旧索引，也没有发布新版 APK。需要后续实现多包安装器、目录索引、全局检索、同字多来源展示、异体索引及可缺省译文后接入。

schema 2 沿用词条/释文/篇章/题目表名，增加 `dict_alias`、`dict_entry.metadata_json`；允许字书包无篇章和题目，古释没有现代译文时保持空值。ID 带 `dictionary.shuowen:` 命名空间。ZIP 恰含 `manifest.json` 与 `sample.db`，完整 Apache 许可、版权归属、来源提交和改动说明嵌入 manifest。不得用 v0.3 的发布器上传到 `resources-v1`。

## 来源与授权

- 上游：https://github.com/shuowenjiezi/shuowen
- 数据提供：Shuowen.org；古籍作者许慎，古注作者段玉裁。
- 上游声明 Apache-2.0，Copyright [2019] [Shuowen.org]。包内保留完整许可及归属，数据库转换、同字分组和索引处理在 manifest 标注。
- 固定提交：`6553a350763ce4feca2c5d1cc2cad7e594dc2975`。
- 下载：https://codeload.github.com/shuowenjiezi/shuowen/zip/6553a350763ce4feca2c5d1cc2cad7e594dc2975
- 源 ZIP SHA256：`737ecb6f1a7de474bc0b8ff4cd95b9064441090d6360f6ad9632881edb63426d`。
- 成品 ZIP SHA256：`9fc41b6739682cc2c9e7dd4f8966c2b81ae2cdc91a8ffdaa028be5b0057a8c4b`。

本轮也检查了 NiuTrans/Classical-Modern、CCTC、chinese-poetry 和 gujilab/chinese-classical-corpus。NiuTrans 说明语料来自互联网且原始权利归来源方；二次聚合库的 CC0 标注不能自动解决上游现代译文的权利问题，因此本次大包优先采用直接提供明确许可的字书整理数据。CCTC 明确采用 MIT 且允许其他用途，可作为后续文白对照包候选；chinese-poetry 为原文类候选。未将这些候选中的现代译文混入本包。

## 复现与检查

```powershell
python tools/pack-builder/build_shuowen.py --archive .tools/upstream/shuowen-6553a350.zip
python tools/pack-builder/test_shuowen.py
```

构建器校验源 ZIP 哈希、上游数量、必填字段、ID 唯一、同字保留、SQLite 完整性、外键、代表字和大小上限。独立检查重新读取成品 ZIP、数据库及原始 JSON，逐条比较元数据，核对所有原释/古注未丢失、异体关联与许可证。报告在 `dist/resources/shuowen/validation.json`。检查不代表语义逐条审校。
