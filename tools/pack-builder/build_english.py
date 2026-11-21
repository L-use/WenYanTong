"""Build two separate attributed English vocabulary packs; never label the combined exam table CET6-only."""
import hashlib,json,re
from pathlib import Path
from source_pack import Pack,text

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'.tools/upstream/english'
OUTPUT=ROOT/'dist/resources/english'
MISSING='音标未收录'

def read(path):return Path(path).read_text(encoding='utf-8-sig')

def verify():
    for source in json.loads(read(ROOT/'tools/pack-builder/english-sources.lock.json')):
        for row in source['files']:
            data=(BASE/source['key']/row['path']).read_bytes()
            if hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()!=row['sha1']:raise ValueError('Input snapshot mismatch')

def aliases(pack,entry,word,other=None):
    values={word,word.lower(),word.upper(),word.title()}
    if isinstance(other,str):values.update(s.strip() for s in re.split(r'[,，;；/]',other) if s.strip())
    for value in values:
        pack.db.execute('INSERT OR IGNORE INTO dict_alias VALUES(?,?,?)',(value,entry,'original_spelling_or_case_variant'))

def kyle():
    path='full_line_jsonl/full/正序/六级.jsonl'
    rows=[json.loads(line) for line in read(BASE/'kyle'/path).splitlines() if line.strip()]
    p=Pack(OUTPUT,'english.cet6.kyle','六级英语词汇包（KyleBing）','https://github.com/KyleBing/english-vocabulary','731240a848dca14e2cf17510f384312b788863c1','BSD-3-Clause',read(BASE/'kyle/LICENSE'),'上游整理词书数据；非官方完整性认证，未逐条审校；保留词书编号及原始来源链')
    words=set();pronunciations={};books={}
    for index,row in enumerate(rows):
        p.raw_count+=1;head=text(row.get('headWord'));word=head.casefold()
        detail=row.get('content',{}).get('word',{}).get('content',{})
        trans=detail.get('trans',[]);sentences=detail.get('sentence',{}).get('sentences',[])
        if not word or not any(text(t.get('tranCn')) for t in trans):
            p.rejected.append(dict(line=index+1,word=head,reason='missing headword or Chinese definition'));continue
        words.add(word);book=text(row.get('bookId'));books[book]=books.get(book,0)+1
        parts=[label+text(detail.get(key)) for label,key in [('英 /','ukphone'),('美 /','usphone')] if text(detail.get(key))]
        phonetic=' / '.join(s+'/' for s in parts) or MISSING
        source=p.source(path,f'line:{index+1};wordId:{row.get("content",{}).get("word",{}).get("wordId","")}',book,'','word_definition_and_examples','upstream_dictionary_compilation',row)
        first=sentences[0] if sentences else {}
        entry=None
        for definition in trans:
            if not text(definition.get('tranCn')):continue
            entry=p.word(word,phonetic,definition['tranCn'],definition.get('pos'),first.get('sContent'),first.get('sCn'),f'KyleBing · {book} · {head}',source,{'originalWord':head,'bookId':book})
        if entry:
            aliases(p,entry,head)
            if phonetic!=MISSING:pronunciations[word]=phonetic
            else:p.db.execute("DELETE FROM record_source WHERE record_type='dict_entry' AND record_id=? AND field='pinyin' AND provenance_id=?",(entry,source))
    for word,phonetic in pronunciations.items():
        # Display a real reading when another book supplies one; missing-reading labels are not phonetic data.
        p.db.execute('UPDATE dict_entry SET pinyin=? WHERE headword=?',(phonetic,word))
    return p.finish('六级三份上游词书合并：5,651 条原始记录，按单词去重保留多书释义、音标、例句及来源。词组、同根词等完整结构保存在原始来源元数据中。',dict(type='dictionary',language='en',minAppVersionCode=4,uniqueWords=len(words),bookRecordCounts=books,phoneticField='legacy pinyin column stores clearly labelled UK/US IPA; 音标未收录 is only a missing-value display label',sourceChain=['KyleBing/english-vocabulary','KyleBing/dict','kajweb/dict'],coverageNote='5,651 是三个词书的记录总量，不等于 5,651 个不同单词；未声称已逐词对齐官方考纲。'))

def exam():
    path='cet_full_list.json';data=json.loads(read(BASE/'exam'/path));rows=data['四六级词汇词频排序表']
    p=Pack(OUTPUT,'english.cet.examdata','四六级英语词汇包（词频版）','https://github.com/exam-data/CETVocabulary','83eaa58b181bc388dc7a662d00179e3d888d900a','CC-BY-NC-SA-4.0',read(BASE/'exam/LICENSE'),'数据仅限非商业使用，署名并相同方式共享；保留例句来源/AI 标记、六级标记和词频；并非纯六级新增词表')
    origin_counts={};level_counts={}
    for index,row in enumerate(rows):
        p.raw_count+=1;head=text(row.get('单词'));word=head.casefold();gloss=text(row.get('释义'))
        if not word or not gloss:
            p.rejected.append(dict(index=index,word=head,reason='missing word or definition'));continue
        label=str(row.get('六级'));level_counts[label]=level_counts.get(label,0)+1
        origin=text(row.get('例句来源')) or '上游未注明';origin_counts[origin]=origin_counts.get(origin,0)+1
        locator=f'$.四六级词汇词频排序表[{index}];序号:{row.get("序号")}'
        definition=p.source(path,locator,'四六级词汇词频排序表','exam-data','word_definition','upstream_wordlist_compilation',row)
        ai=any(marker in origin.lower() for marker in ['deepseek','gpt','gemini','claude','ai','生成'])
        example=p.source(path,locator+'.例句/例句翻译','四六级词汇词频排序表','', 'example_and_translation','upstream_ai_generated' if ai else 'upstream_example_with_named_source',{'exampleSource':origin,'original':row.get('例句'),'translation':row.get('例句翻译')})
        entry=p.word(word,MISSING,gloss,'',row.get('例句'),row.get('例句翻译'),f'exam-data · 词频 {row.get("词频")} · 六级标记 {row.get("六级")} · 例句来源 {origin}',definition,row,example_source=example,translation_source=example)
        p.db.execute("DELETE FROM record_source WHERE record_type='dict_entry' AND record_id=? AND field='pinyin'",(entry,))
        aliases(p,entry,head,row.get('其他拼写'))
    return p.finish('四、六级合并词表，包含词频、六级标记、中文释义、主题分类、CEFR 和例句来源；不把合并表数量当作纯六级词汇量。',dict(type='dictionary',language='en',minAppVersionCode=4,nonCommercial=True,shareAlike=True,attributionRequired=True,licenseUrl='https://creativecommons.org/licenses/by-nc-sa/4.0/',exampleSourceCounts=origin_counts,levelMarkerCounts=level_counts,phoneticsAvailable=False,missingPhoneticDisplay=MISSING,modifications='转换为独立 SQLite 包；按不区分大小写的单词合并，保留所有原始位置和各自释义；未新增释义或伪造音标。'))

def main():
    verify();reports=[kyle(),exam()]
    (OUTPUT/'collection.json').write_text(json.dumps(dict(packs=reports,distribution='Local preparation; exam-data package is non-commercial and share-alike.'),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(reports,ensure_ascii=True,indent=2))
if __name__=='__main__':main()
