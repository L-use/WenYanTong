"""Local multi-pack SQLite writer with field-level provenance and conservative exact deduplication."""
import hashlib
import json
import re
import sqlite3
import unicodedata
import zipfile
from pathlib import Path

def digest(value):
    return hashlib.sha256(value).hexdigest()

def text(value):
    return value.strip() if isinstance(value,str) else ''

def fingerprint(*values):
    # Only Unicode canonical composition/outer whitespace: punctuation, variants and different readings survive.
    return digest(json.dumps([unicodedata.normalize('NFC',text(v)) for v in values],ensure_ascii=False).encode())[:24]

class Pack:
    def __init__(self, root, pack_id, title, repository, commit, license_id, license_text, review):
        self.folder=Path(root)/pack_id;self.folder.mkdir(parents=True,exist_ok=True)
        self.path=self.folder/'sample.db';self.path.unlink(missing_ok=True)
        self.id=pack_id;self.title=title;self.repo=repository;self.commit=commit
        self.license=license_id;self.license_text=license_text;self.review=review
        self.raw_count=0;self.rejected=[];self.books=set();self.source_files={};self.sources={}
        self.db=sqlite3.connect(self.path)
        self.db.executescript('''
            PRAGMA foreign_keys=ON; PRAGMA user_version=2;
            CREATE TABLE source(id TEXT PRIMARY KEY,title TEXT NOT NULL,license TEXT NOT NULL,review_status TEXT NOT NULL);
            CREATE TABLE dict_entry(id TEXT PRIMARY KEY,headword TEXT NOT NULL UNIQUE,pinyin TEXT NOT NULL,summary TEXT NOT NULL,metadata_json TEXT NOT NULL);
            CREATE TABLE dict_sense(id TEXT PRIMARY KEY,entry_id TEXT NOT NULL REFERENCES dict_entry(id),gloss TEXT NOT NULL,pos TEXT NOT NULL,example TEXT NOT NULL,translation TEXT NOT NULL,reference TEXT NOT NULL);
            CREATE INDEX sense_entry ON dict_sense(entry_id);
            CREATE TABLE dict_alias(alias TEXT NOT NULL,entry_id TEXT NOT NULL REFERENCES dict_entry(id),kind TEXT NOT NULL,PRIMARY KEY(alias,entry_id));
            CREATE TABLE passage(id TEXT PRIMARY KEY,original TEXT NOT NULL,translation TEXT NOT NULL,direction TEXT NOT NULL,work TEXT NOT NULL,analysis TEXT NOT NULL,entry_ids TEXT NOT NULL,normalized TEXT NOT NULL);
            CREATE INDEX passage_lookup ON passage(direction,normalized);
            CREATE TABLE question(id TEXT PRIMARY KEY,stem TEXT NOT NULL,options_json TEXT NOT NULL,answer INTEGER NOT NULL,explanation TEXT NOT NULL,entry_id TEXT NOT NULL REFERENCES dict_entry(id));
            CREATE TABLE article(id TEXT PRIMARY KEY,title TEXT NOT NULL,author TEXT NOT NULL,dynasty TEXT NOT NULL,original TEXT NOT NULL,translation TEXT NOT NULL,background TEXT NOT NULL,appreciation TEXT NOT NULL,metadata_json TEXT NOT NULL);
            CREATE TABLE article_segment(article_id TEXT NOT NULL REFERENCES article(id),passage_id TEXT NOT NULL REFERENCES passage(id),position INTEGER NOT NULL,PRIMARY KEY(article_id,position));
            CREATE TABLE source_file(id INTEGER PRIMARY KEY,repository TEXT NOT NULL,commit_sha TEXT NOT NULL,file_path TEXT NOT NULL UNIQUE,license TEXT NOT NULL,url TEXT NOT NULL);
            CREATE TABLE provenance(id INTEGER PRIMARY KEY,file_id INTEGER NOT NULL REFERENCES source_file(id),locator TEXT NOT NULL,work TEXT NOT NULL,author TEXT NOT NULL,field_kind TEXT NOT NULL,origin_kind TEXT NOT NULL,upstream_json TEXT NOT NULL);
            CREATE TABLE record_source(record_type TEXT NOT NULL,record_id TEXT NOT NULL,field TEXT NOT NULL,provenance_id INTEGER NOT NULL REFERENCES provenance(id),PRIMARY KEY(record_type,record_id,field,provenance_id)) WITHOUT ROWID;
        ''')
        self.db.execute('INSERT INTO source VALUES(?,?,?,?)',(pack_id,title,license_id,review))

    def source(self,path,locator,work,author,kind,origin,metadata=None):
        cache_key=(path,locator,work,author,kind,origin)
        if cache_key in self.sources:return self.sources[cache_key]
        from urllib.parse import quote
        url=f'{self.repo}/blob/{self.commit}/{quote(path)}'
        if path not in self.source_files:
            self.source_files[path]=self.db.execute('INSERT INTO source_file(repository,commit_sha,file_path,license,url) VALUES(?,?,?,?,?)',(self.repo,self.commit,path,self.license,url)).lastrowid
        key=self.db.execute('INSERT INTO provenance(file_id,locator,work,author,field_kind,origin_kind,upstream_json) VALUES(?,?,?,?,?,?,?)',
            (self.source_files[path],locator,work,author,kind,origin,json.dumps(metadata or {},ensure_ascii=False))).lastrowid
        self.sources[cache_key]=key
        return key

    def link(self,kind,key,field,source):
        self.db.execute('INSERT OR IGNORE INTO record_source VALUES(?,?,?,?)',(kind,key,field,source))

    def word(self,word,pinyin,gloss,pos,example,translation,reference,source,metadata=None,example_source=None,translation_source=None):
        word=text(word);gloss=text(gloss)
        if not word or not gloss:return None
        entry=self.id+':entry:'+fingerprint(word)
        self.db.execute('INSERT OR IGNORE INTO dict_entry VALUES(?,?,?,?,?)',(entry,word,text(pinyin),gloss,json.dumps(metadata or {},ensure_ascii=False)))
        if text(pinyin):
            previous=self.db.execute('SELECT pinyin FROM dict_entry WHERE id=?',(entry,)).fetchone()[0]
            if text(pinyin) not in previous.split(' / '):self.db.execute('UPDATE dict_entry SET pinyin=? WHERE id=?',(' / '.join(filter(None,[previous,text(pinyin)])),entry))
        self.link('dict_entry',entry,'headword',source)
        if text(pinyin):self.link('dict_entry',entry,'pinyin',source)
        sense=self.id+':sense:'+fingerprint(word,gloss,pos,example,translation)
        self.db.execute('INSERT OR IGNORE INTO dict_sense VALUES(?,?,?,?,?,?,?)',(sense,entry,gloss,text(pos),text(example),text(translation),reference))
        for field,value in [('gloss',gloss),('example',example),('translation',translation)]:
            if text(value):self.link('dict_sense',sense,field,example_source if field=='example' and example_source else translation_source if field=='translation' and translation_source else source)
        return entry

    def passage(self,original,translation,work,analysis,entries,original_source,translation_source=None):
        if not text(original):return None
        key=self.id+':passage:'+fingerprint(original,translation,analysis)
        normalized=''.join(c for c in text(original).lower() if not c.isspace() and c not in '，。？！；：、,.?!;:"“”‘’（）()')
        self.db.execute('INSERT OR IGNORE INTO passage VALUES(?,?,?,?,?,?,?,?)',(key,text(original),text(translation),'CLASSICAL_TO_ZH',work,text(analysis),json.dumps(list(dict.fromkeys(entries))),normalized))
        self.link('passage',key,'original',original_source)
        if text(translation):self.link('passage',key,'translation',translation_source or original_source)
        if text(analysis):self.link('passage',key,'analysis',translation_source or original_source)
        return key

    def article(self,title,author,dynasty,original,translation,background,appreciation,metadata,original_source,translation_source=None):
        key=self.id+':article:'+fingerprint(title,author,original,translation)
        self.db.execute('INSERT OR IGNORE INTO article VALUES(?,?,?,?,?,?,?,?,?)',(key,title,author,dynasty,text(original),text(translation),text(background),text(appreciation),json.dumps(metadata,ensure_ascii=False)))
        self.books.add(title)
        for field,value in [('original',original),('translation',translation),('background',background),('appreciation',appreciation)]:
            if text(value):self.link('article',key,field,original_source if field=='original' else translation_source or original_source)
        return key

    def finish(self,description,extra=None):
        properties=dict(extra or {})
        pack_type=properties.pop('type','learning')
        self.db.commit()
        if self.db.execute('PRAGMA integrity_check').fetchone()[0]!='ok' or self.db.execute('PRAGMA foreign_key_check').fetchall():raise ValueError('SQLite validation failed')
        counts={table:self.db.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0] for table in ['dict_entry','dict_sense','article','passage','provenance','record_source']}
        self.db.close()
        if self.path.stat().st_size>128*1024*1024:raise ValueError('Database exceeds the multi-pack format limit of 128 MiB')
        manifest=dict(schemaVersion=2,id=self.id,title=self.title,type=pack_type,version='1.0.0',revision=1,database='sample.db',
            entryCount=counts['dict_entry'],senseCount=counts['dict_sense'],articleCount=counts['article'],passageCount=counts['passage'],questionCount=0,
            provenanceCount=counts['provenance'],sourceLinkCount=counts['record_source'],inputRecordCount=self.raw_count,description=description,
            source=dict(repository=self.repo,commit=self.commit),license=self.license,licenseText=self.license_text,reviewStatus=self.review,
            requiredCapabilities=['multiple-resource-packs','field-level-provenance','optional-examples-and-translations','articles-schema-2'],
            readerDatabaseLimitBytes=128*1024*1024,
            compatibilityNote='Requires a new multi-pack reader; not installable in v0.3.0. Keep packs separate; search downloaded packs globally with source attribution.',
            deduplication='Only exact NFC-normalized records merge within this pack; all contributing locators retained. Different translations or readings remain separate.',
            files=[dict(path='sample.db',bytes=self.path.stat().st_size,sha256=digest(self.path.read_bytes()))],
            **properties)
        manifest_path=self.folder/'manifest.json';manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        archive=self.folder/(self.id+'-1.0.0.zip')
        with zipfile.ZipFile(archive,'w') as package:
            for path in [manifest_path,self.path]:
                info=zipfile.ZipInfo(path.name,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;package.writestr(info,path.read_bytes())
        report=dict(id=self.id,title=self.title,counts=counts,inputRecordCount=self.raw_count,rejectedRecords=self.rejected,bytes=archive.stat().st_size,sha256=digest(archive.read_bytes()),path=str(archive))
        (self.folder/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        return report
