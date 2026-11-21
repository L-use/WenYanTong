# 两个独立英文词汇包

本轮按用户选择制作两个独立 ZIP，保留各自来源和许可，不混成一个数据库。产物在 `dist/resources/english/`。当前为本地制包与导入验证，未推送 GitHub 下载目录、未发布新 APK。

| 包 ID | 内容 | 原始记录 | 不同单词 | 释义记录 | ZIP |
| --- | --- | ---: | ---: | ---: | ---: |
| english.cet6.kyle | 六级词书合并版 | 5,651 | 3,991 | 6,534 | 6,329,224 字节 |
| english.cet.examdata | 四、六级合并词频表 | 5,278 | 5,276 | 5,278 | 2,839,024 字节 |

词头按不区分大小写去重，所有原始词书、位置和释义仍通过来源表保留。两包之间不互相替换，重复单词由客户端全局词典合并入口并保留各包释义。补充原始/大写/首字母大写及上游其他拼写索引。不同单词数不等于官方考纲认证数量。

## 六级词书包

- 来源：https://github.com/KyleBing/english-vocabulary ，固定提交 `731240a848dca14e2cf17510f384312b788863c1`。
- 输入：`full_line_jsonl/full/正序/六级.jsonl`；包含 CET6_1、CET6_2、CET6_3 三套词书，不能把合计记录数当作不同单词数。
- 上游声明 BSD-3-Clause，完整版权/许可随 manifest 保留；上游链 `KyleBing/english-vocabulary → KyleBing/dict → kajweb/dict` 同时记录，不把转载数据声称为项目原创。
- 展示中文释义、词性、英美音标和例句。上游完整词组、同根词、同近义词等结构保存在来源元数据，不宣称旧客户端已有专门的词组学习界面。
- SHA256：`47024d77ec69edacfa164990f5f9e03dd6d2cb14df0c348e9f34f54600887b97`。

## 四六级词频包

- 来源：https://github.com/exam-data/CETVocabulary ，固定提交 `83eaa58b181bc388dc7a662d00179e3d888d900a`。
- 输入：`cet_full_list.json` 的“四六级词汇词频排序表”，包含四级基础和六级标记，不叫“纯六级新增词包”。保留词频、主题、CEFR、六级标记、其他拼写及例句来源等完整字段。
- **数据许可 CC BY-NC-SA 4.0：非商业使用、署名、相同方式共享。** 包内保存完整许可证、来源、链接和转换说明；代码仓库的 MIT 不改变该数据包的许可。不能用于商业化发布而不另取得相应授权。
- 例句若上游标记 DeepSeek 等生成来源，字段级来源明确标为 AI 生成；不冒充官方例句或已人工审校教材。
- 上游未提供音标，界面如实显示“音标未收录”。兼容旧数据库字段时使用该缺省显示标签，不生成读音，也不给该标签伪造上游音标来源。
- SHA256：`3f31c00f896082f9c806a92dbc1e66966ea4f2df8f68b12124ec08913d4cfacf`。

## 使用与验证

包格式与 0.4.0 多包导入兼容：设置 → 离线资源 → 导入本地资源 ZIP；导入后通过“全局查词”或该包“查看词条”使用。只提供已收录词汇释义，不等于任意英语句子翻译；首页中英对照能力仍按客户端当前实现，不因安装词表就变成通用翻译器。

复现：

```powershell
python tools/pack-builder/fetch_english.py
python tools/pack-builder/build_english.py
python tools/pack-builder/test_english.py
```

构建前按已检查的 `english-sources.lock.json` 验证源文件 Git blob SHA。Python 检查覆盖 ZIP/数据库哈希、完整性与外键、词头全集、全部原始记录与释义保留、字段来源、缺音标标记及 NC/SA/AI 声明。Android 专项为 `EnglishPackImportTest`，显式参数 `englishPacks=true`，验证两包同时安装、同词多来源、大小写索引和单包删除隔离。实测结果在本地 `.tools/english-device-test.log`。
