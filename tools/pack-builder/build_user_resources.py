"""Convert the user's inspected source snapshots into separate local learning packs.

No upstream code is executed. Does not publish to GitHub or the legacy v0.3 resource index.
"""
import collections
import hashlib
import json
from pathlib import Path
import re
import zipfile
from source_pack import Pack, text

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/'.tools/upstream/user-resources'
OUTPUT=ROOT/'dist/resources/user-collection'

def read(path):return Path(path).read_text(encoding='utf-8-sig')

def verify_inputs():
    locks=json.loads(read(ROOT/'tools/pack-builder/user-sources.lock.json'))
    for source in locks:
        for item in source['files']:
            path=INPUT/source['key']/item['path']
            data=path.read_bytes()
            actual=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
            if actual!=item['gitBlobSha1']:raise ValueError('Input differs from inspected snapshot: '+str(path))

def guwen():
    root=INPUT/'guwenguanzhi';commit='3f20cb23c0e14eeeffb405bc19d32c43b7f6e5b6'
    pack=Pack(OUTPUT,'reading.guwenguanzhi','古文观止译注包','https://github.com/niuniu-869/guwenguanzhi',commit,'MIT',read(root/'LICENSE'),'上游 AI 生成译注；未经本项目逐条学术审校')
    paragraph_count=sentence_count=word_count=0
    for path in sorted((root/'data/articles').rglob('*.json')):
        d=json.loads(read(path));relative=path.relative_to(root).as_posix();pack.raw_count+=1
        title=d['title'];author=d['author']['name'] if isinstance(d.get('author'),dict) else text(d.get('author'))
        base=pack.source(relative,'$',title,author,'original','public_domain_ancient_text',{'originalBook':d.get('source'),'upstreamId':d.get('id')})
        ai=pack.source(relative,'$',title,'','translation_and_commentary','upstream_ai_generated',{'promptVersion':d.get('_prompt_version'),'translator':'未注明个人译者；上游说明为 LLM 生成'})
        paragraphs=d.get('paragraphs',[])
        original='\n\n'.join(text(p.get('original')) for p in paragraphs)
        translated='\n\n'.join(text(p.get('translation')) for p in paragraphs)
        meta={k:v for k,v in d.items() if k!='paragraphs'}
        article=pack.article(title,author,text(d.get('dynasty')),original,translated,text(d.get('background')),text(d.get('appreciation')),meta,base,ai)
        position=0
        for pi,paragraph in enumerate(paragraphs):
            paragraph_count+=1
            paragraph_original=pack.source(relative,f'$.paragraphs[{pi}]',title,author,'original','public_domain_ancient_text')
            paragraph_ai=pack.source(relative,f'$.paragraphs[{pi}]',title,'','translation','upstream_ai_generated')
            for si,sentence in enumerate(paragraph.get('sentences',[])):
                locator=f'$.paragraphs[{pi}].sentences[{si}]';sentence_count+=1
                original_source=pack.source(relative,locator,title,author,'original','public_domain_ancient_text')
                translation_source=pack.source(relative,locator,title,'','translation','upstream_ai_generated')
                entry_ids=[]
                for wi,word in enumerate(sentence.get('words',[])):
                    word_count+=1
                    source=pack.source(relative,locator+f'.words[{wi}]',title,'','gloss_and_pinyin','upstream_ai_generated',{'originalBook':d.get('source'),'wordAnnotation':word})
                    entry=pack.word(word.get('word'),word.get('pinyin'),word.get('meaning'),word.get('type'),sentence.get('original'),sentence.get('translation'),f'{title} · 第{pi+1}段第{si+1}句',source,word,example_source=original_source,translation_source=translation_source)
                    if entry:entry_ids.append(entry)
                passage=pack.passage(sentence.get('original'),sentence.get('translation'),title,'',entry_ids,original_source,translation_source)
                if passage:
                    pack.db.execute('INSERT INTO article_segment VALUES(?,?,?)',(article,passage,position));position+=1
            # Retain paragraph-level originals/translations even when sentence alignment is incomplete.
            pack.passage(paragraph.get('original'),paragraph.get('translation'),title,'',[],paragraph_original,paragraph_ai)
    return pack.finish('222 篇《古文观止》原文与上游 AI 逐句译注；词义可全局检索并追溯至段、句和词位置。',dict(paragraphCount=paragraph_count,sentenceCount=sentence_count,wordAnnotationCount=word_count,aiGeneratedFields=['translation','gloss','pinyin','background','appreciation','author.bio']))

def cctc():
    root=INPUT/'cctc';commit='8a60d1478e2240df8fae3747ada347ff332b1d5f'
    pack=Pack(OUTPUT,'reading.cctc','文言文白话对照包 CCTC','https://github.com/Scagin/cctc',commit,'MIT',read(root/'LICENSE'),'上游翻译平行语料；现代译者未逐条注明；保留上下文，不作为权威教材')
    book_names={'lunyu':'论语','shiji':'史记','zhongyong':'中庸'}
    for path in sorted(root.rglob('*.json')):
        relative=path.relative_to(root).as_posix();book=book_names.get(path.parent.name,path.parent.name)
        for ci,chapter in enumerate(json.loads(read(path))):
            title=book+'·'+text(chapter.get('title'));pairs=chapter.get('contents',[])
            source=pack.source(relative,f'$[{ci}]',title,'','original','public_domain_ancient_text')
            target=pack.source(relative,f'$[{ci}]',title,'','translation','upstream_modern_translation_unspecified_author')
            article=pack.article(title,'','', '\n'.join(text(p.get('source')) for p in pairs),'\n'.join(text(p.get('target')) for p in pairs),'','',{'book':book,'upstreamTitle':chapter.get('title')},source,target)
            for pi,pair in enumerate(pairs):
                pack.raw_count+=1
                if not text(pair.get('source')) or not text(pair.get('target')):
                    pack.rejected.append(dict(path=relative,locator=f'$[{ci}].contents[{pi}]',reason='missing source or translation'));continue
                original=pack.source(relative,f'$[{ci}].contents[{pi}].source',title,'','original','public_domain_ancient_text')
                translation=pack.source(relative,f'$[{ci}].contents[{pi}].target',title,'','translation','upstream_modern_translation_unspecified_author')
                passage=pack.passage(pair['source'],pair['target'],title,'',[],original,translation)
                pack.db.execute('INSERT INTO article_segment VALUES(?,?,?)',(article,passage,pi))
    return pack.finish('《论语》《史记》《中庸》的古今对照语料。句对保持章节和原位置；没有额外编造字词释义或题目。')

def classical():
    root=INPUT/'classical-corpus';repo='https://github.com/gujilab/chinese-classical-corpus';commit='09a6873e46760f20027e20bf48814f5c5cc05d66'
    groups={};skipped=0
    with (root/'output/corpus.jsonl').open(encoding='utf-8') as stream:
        for line_number,line in enumerate(stream,1):
            if not line.strip():continue
            row=json.loads(line)
            if row.get('source')=='说文解字':skipped+=1;continue
            group='tongjian' if row.get('source')=='资治通鉴' else 'histories' if row.get('category')=='史' else 'classics'
            groups.setdefault(group,[]).append((line_number,row))
    titles={'tongjian':'资治通鉴原文包','histories':'历代史籍原文包','classics':'经籍原文包'}
    reports=[]
    for group,rows in sorted(groups.items()):
        pack=Pack(OUTPUT,'corpus.'+group,titles[group],repo,commit,'CC0-1.0',read(root/'LICENSE'),'古籍原文结构化转录；保留上游底本说明和异文；无现代译文')
        for line_number,row in rows:
            pack.raw_count+=1
            body=text(row.get('content'));book=text(row.get('source'));title=text(row.get('chapter')) or text(row.get('title')) or f'{book}·{row.get("id")}'
            meta={k:v for k,v in row.items() if k!='content'}
            source=pack.source('output/corpus.jsonl',f'line:{line_number};id:{row.get("id")}',book,text(row.get('author')),'original','public_domain_ancient_text',meta)
            if not body:pack.rejected.append(dict(line=line_number,id=row.get('id'),reason='empty original'));continue
            pack.article(title,text(row.get('author')),text(row.get('era')) or text(row.get('period')),body,'','','',meta,source)
        reports.append(pack.finish('古典文献原文阅读资料；没有现代译文。不包含本项目另包已有的说文解字条目。',dict(excludedShuowenRecords=skipped,translationCount=0)))
    return reports

def read_encrypted(name,expected):
    path=INPUT/name
    if hashlib.sha256(path.read_bytes()).hexdigest()!=expected:raise ValueError('Official archive hash mismatch: '+name)
    with zipfile.ZipFile(path) as package:
        return {n:[json.loads(line) for line in package.read(n,pwd=b'BEIJING_NORMAL_UNIVERSITY').decode('utf-8-sig').splitlines() if line.strip()] for n in package.namelist() if n.endswith('.jsonl')}

def tongjia():
    corpus=read_encrypted('tongjiazi/corpus/tongjiazi_corpus.zip','b2c5110899935deba75f23dc9cb1fa846046866a46501278f1d0463928a6f8e6')['corpus.jsonl']
    knowledge=read_encrypted('tongjiazi/knowledge_base/tongjiazi_knowledge_base.zip','d553bc34b7251eb0558ed15c78dd1e15e562ea639e256eb2b89ad39d6affb012')
    license_text=read(INPUT/'tongjiazi/LICENSE')
    pack=Pack(OUTPUT,'dictionary.tongjiazi','古汉语通假字包','https://github.com/frederick-wang/tongjiazi-resources','ac5273e854724ef33b8912e050afb984695a1065','MIT',license_text,'学术标注语料；保留原始词典字头义项编号、出处和知识库元数据')
    for index,row in enumerate(corpus):
        pack.raw_count+=1
        word=text(row.get('通假字字头'));clean=re.sub(r'[0-9]+$','',word)
        work=text(row.get('出处'));source=pack.source('corpus/tongjiazi_corpus.zip',f'corpus.jsonl:line:{index+1};语料ID:{row.get("语料ID")}',work,'','gloss_and_annotated_example','upstream_scholarly_annotation',row)
        pack.word(clean,row.get('拼音'),row.get('释义'),'通假',row.get('语料文本'),'',work,source,row)
    # Preserve the full knowledge graph separately, rather than treating relation labels as fabricated quiz questions.
    pack.db.execute('CREATE TABLE knowledge_record(kind TEXT NOT NULL,upstream_id TEXT NOT NULL,data_json TEXT NOT NULL,PRIMARY KEY(kind,upstream_id))')
    knowledge_counts={}
    for filename,records in knowledge.items():
        knowledge_counts[filename]=len(records)
        for index,row in enumerate(records):
            pack.db.execute('INSERT INTO knowledge_record VALUES(?,?,?)',(filename,str(index),json.dumps(row,ensure_ascii=False)))
            origin=pack.source('knowledge_base/tongjiazi_knowledge_base.zip',f'{filename}:line:{index+1}',text(row.get('出处')),'','knowledge_graph','upstream_scholarly_annotation',row)
            pack.link('knowledge_record',filename+':'+str(index),'data',origin)
    return pack.finish('通假字字词释义、真实古籍例句及知识图谱；保留原始释义与来源，不生成虚构译文或练习题。',dict(knowledgeCounts=knowledge_counts,officialDownloadPage='https://tjz.zhaoji.ac.cn/benchmark',sourceVersionNote='官网当前 ZIP 与 GitHub 固定版本内部记录不同；本包使用带 MIT 许可的固定 GitHub 版本，不套用官网新版的数量。',citations=[dict(title='古汉语通假字资源库的构建及应用研究',authors='王兆基、张诗睿、胡韧奋、张学涛等；作者顺序依各发表版本',url='https://aclanthology.org/2023.ccl-1.47/',note='仓库提供 CCL 2023 引用；官网另列《中文信息学报》2024,38(3):152–162，分别保留，不混同发表版本。')]))

def main():
    verify_inputs()
    reports=[guwen(),cctc(),*classical(),tongjia()]
    summary=dict(packCount=len(reports),packs=reports,pendingSources=[
        dict(repository='https://github.com/aopao/chinese-gushiwen',reason='未发现明确再分发许可证；现代译注/赏析不可仅因仓库公开就视为授权，暂未制作公开分发包。'),
        dict(repository='https://github.com/daichangya/chinese-poetry-md',reason='仓库未发现独立许可证；包含新增译文/赏析及推断作者简介，不能将上游古籍原文许可直接套用于这些字段。暂未制作公开分发包。')],
        clientCompatibility='Local preparation only. All packs require the forthcoming multi-pack reader; not compatible with v0.3.0.')
    (OUTPUT/'collection.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=True,indent=2))

if __name__=='__main__':main()
