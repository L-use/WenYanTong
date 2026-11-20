# 初版示例资源

输入：tools/pack-builder/sample.json。构建器：tools/pack-builder/build.py，仅依赖 Python 3 标准库。输出 SQLite、manifest 和 ZIP。构建流程校验外键、SQLite integrity_check、答案索引，并生成真实 sha256 与 bytes。

表：source、dict_entry、dict_sense、passage、question。dict_sense 保存一条对应出处的示例，passage.entry_ids 与 question.entry_id 用于关联学习。当前 schema=1，为小型功能示例，尚不是详细设计中完整的多包关系模型或 FTS 检索器。

古文示例八条、双语对照两组、词条八个、义项十二个、单选题十道。授权与篇章见 NOTICE、sample.json 和各页面；原创释义/译文/题目采用 CC0-1.0，审校状态为 sample_not_scholarly_reviewed。

后续扩展方向：独立作品、例句、语法规则与来源表；逐句 sense 标注；FTS 与可靠繁简映射；四类独立资源包；可信索引签名；下载限额与 ZIP 路径防护；只读数据库连接释放后切换版本；失败回滚。上述能力不属于当前版本。
