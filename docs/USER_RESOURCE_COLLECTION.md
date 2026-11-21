# 用户提供的文言文资源制包记录

本次根据用户上传的《文言文资源包名称及来源》清单核对 6 个来源，使用其中 4 个来源制作 6 个独立包。其中古典文献库按内容拆为 3 包；不是把六个来源都合成了一个库。这六包已加入 0.4.0 的独立资源目录，可分别下载；旧版单库索引保持不变。

## 已制作内容

| 独立包 | 内容 | ZIP 约大小 |
| --- | --- | --- |
| 古文观止译注包 | 222 篇；13,717 个不同字词；78,785 条按语境保留的释义记录 | 21.83 MiB |
| CCTC 文言文白话对照包 | 82 个上游章节单元；7,841 条原始句对，精确去重后 7,817 条 | 2.35 MiB |
| 经籍原文包 | 587 个章节/篇目单元 | 0.89 MiB |
| 历代史籍原文包 | 1,297 个章节单元 | 16.04 MiB |
| 资治通鉴原文包 | 294 卷级记录 | 6.67 MiB |
| 古汉语通假字包 | 2,527 个不同字头；12,498 条原始标注语料，保留知识图谱数据 | 5.89 MiB |

“古文观止译注包”的现代译文、词义、拼音、背景、赏析等由上游 AI 管线生成；包内逐字段标注 `upstream_ai_generated`，不作为权威教材或人工逐条审校内容。78,785 条是结合例句区分的注释记录，不等于这么多个独立字词。

经籍、史籍和资治通鉴包只含古籍原文，没有现代白话译文。没有将古文原文重复写入译文列，也没有编造练习题。原文语料中的说文解字 9,831 条不重复收入本次三个原文包，已有单独说文字书包。

## 每个来源的处理

1. **niuniu-869/guwenguanzhi**：MIT；固定提交 `3f20cb23c0e14eeeffb405bc19d32c43b7f6e5b6`。222 篇 JSON 逐文件核对 Git blob SHA。保留古籍原书、作者、篇名、段句位置、词位置与 AI 标记，制作古文观止译注包。来源：https://github.com/niuniu-869/guwenguanzhi 。
2. **aopao/chinese-gushiwen**：固定检查版本 `c2345d0abf2404b8b3601e4afc2e8fd12f90d6c8`，未发现明确的仓库再分发许可。包含现代译文、注释、赏析和音频链接，未直接制成公开分发包；记录为待确认来源。来源：https://github.com/aopao/chinese-gushiwen 。
3. **gujilab/chinese-classical-corpus**：原文 `output/` 数据声明 CC0-1.0，代码另用 MIT；固定提交 `09a6873e46760f20027e20bf48814f5c5cc05d66`。实读 `output/corpus.jsonl` 共 12,009 条，按用途排除另有字书包的说文条目后，保留 2,178 个章节/篇目，拆成三个原文包。各条 `_source`、`_filled_from` 等底本/补入说明保留在元数据及来源表。未纳入其古今翻译训练指令数据。来源：https://github.com/gujilab/chinese-classical-corpus 。
4. **Scagin/CCTC**：MIT；固定提交 `8a60d1478e2240df8fae3747ada347ff332b1d5f`。逐条保留古文及白话对照、文件路径、章节与句对位置；现代译者未逐条注明，不能误标为古籍作者翻译。来源：https://github.com/Scagin/cctc 。
5. **古汉语通假字资源库**：官网 https://tjz.zhaoji.ac.cn/benchmark ，对应 GitHub https://github.com/frederick-wang/tongjiazi-resources ，固定提交 `ac5273e854724ef33b8912e050afb984695a1065`，仓库声明 MIT。官网当前 ZIP 的哈希与页面声明一致，但内部数据与 GitHub 固定版本不相同。本包使用 GitHub 固定版本并保留其 LICENSE，不把官网当前的数量标成该版本的数量。语料、知识库原始字段、原书出处、字头义项数字后缀及词典来源等均保留，不把字头编号当作汉字的一部分。CCL 2023 及官网另列的期刊 2024 引用分别记录，避免混用。
6. **daichangya/chinese-poetry-md**：固定检查版本 `0f9ae510671f363772d79881a6ac9e852a11b137`。原文来自 chinese-poetry，但本仓还含新增译文、赏析、注释和推断作者简介，未发现其独立再分发许可证，不能把上游古诗原文的许可直接视为这些新增字段的许可；暂列待确认，不制作公开分发包。来源：https://github.com/daichangya/chinese-poetry-md 。

许可证文本、原始声明与固定版本一并保留；上游声明不等于本项目对所有底本的权利或语义质量作独立担保。上述“待确认”不影响其余包的本地制作。

## 多来源及去重

各包独立 ID，所有记录带包命名空间，不覆盖另一包的同名字词。`source_file` 记录仓库、固定提交、文件路径、许可证和来源 URL；`provenance` 记录原书、作者、字段类别、原始行或 JSON 位置、人工/AI/古籍原文属性和上游元数据；`record_source` 将这些信息关联到具体字头、释义、原文、译文和文章。

只对 Unicode NFC 与首尾空白归一后完全相同的内容去重。相同释义但例句不同、原文相同而译文不同、异文和不同读音均保留。精确重复合并仍保留所有来源关联；不能只留下第一个来源。

用户确认的使用规则是：包独立展示、下载、更新和删除；全局查词覆盖所有已下载包，合并结果时展示全部来源。**0.4.0 已实现这些功能，v0.3.0 仍不能直接安装。** 客户端先读词条摘要，释义与来源按需读取，文章分页查询和分段阅读，避免将约 119 MiB 的古文观止数据库整库加载到 UI 内存。发布步骤见 MULTIPACK_RELEASE.md，实际验证见 VALIDATION_0.4.0.md。

## 复现

```powershell
python tools/pack-builder/fetch_user_resources.py
python tools/pack-builder/build_user_resources.py
python tools/pack-builder/test_user_resources.py
```

下载器只读取固定提交的数据文件，核对 Git blob SHA，不运行上游脚本。通假字 ZIP 使用作者公开的解压密码。输出在 `dist/resources/user-collection/`，每包有 ZIP、SQLite、manifest 和 validation，根目录 `collection.json` 汇总数量、大小、哈希、待确认来源及兼容状态。

验证覆盖 ZIP 文件清单/哈希、数据库完整性/外键、每项内容的字段级来源、AI 译文字段标记、CCTC 全量句对与所有原始位置、重复记录合并后保留多来源、不同译文不误合并。通过结构检查不表示逐条学术审校。
