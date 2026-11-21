"""Build a separately identifiable, attributed Shuowen dictionary pack from a pinned upstream ZIP.

Usage: python tools/pack-builder/build_shuowen.py --archive path/to/shuowen.zip
This pack targets the multi-pack dictionary format (schema 2), NOT the v0.3 base-library installer.
"""
import argparse
import collections
import hashlib
import json
import sqlite3
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMMIT = "6553a350763ce4feca2c5d1cc2cad7e594dc2975"
REPO = "https://github.com/shuowenjiezi/shuowen"
PACK_ID = "dictionary.shuowen"
ARCHIVE_SHA256 = "737ecb6f1a7de474bc0b8ff4cd95b9064441090d6360f6ad9632881edb63426d"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def build(archive, output):
    archive_bytes = archive.read_bytes()
    if sha(archive_bytes) != ARCHIVE_SHA256:
        raise ValueError("Upstream archive hash differs from the inspected snapshot")
    with zipfile.ZipFile(archive) as upstream:
        prefix = f"shuowen-{COMMIT}/"
        names = sorted(n for n in upstream.namelist() if n.startswith(prefix + "data/") and n.endswith(".json"))
        if len(names) != 9833:
            raise ValueError("Unexpected upstream record count; inspect upstream revision")
        records = [json.loads(upstream.read(n)) for n in names]
        license_text = upstream.read(prefix + "LICENSE").decode("utf-8")
        if "Apache License" not in license_text or "Version 2.0" not in license_text:
            raise ValueError("Unexpected upstream license")
    ids = [r["id"] for r in records]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate upstream IDs")
    for record in records:
        for field in ("wordhead", "explanation", "pinyin_full"):
            if not isinstance(record[field], str) or not record[field].strip():
                raise ValueError(f"Missing {field}: {record['id']}")
        if len(record["wordhead"]) != 1:
            raise ValueError("Unexpected headword; inspect instead of guessing a character")
    # Repeated headwords are separate original entries. Group them under one headword without losing definitions.
    groups = collections.defaultdict(list)
    for record in sorted(records, key=lambda row: row["id"]):
        groups[record["wordhead"]].append(record)
    output.mkdir(parents=True, exist_ok=True)
    database = output / "sample.db"
    temporary = output / "dictionary.pending.db"
    temporary.unlink(missing_ok=True)
    db = sqlite3.connect(temporary)
    db.executescript("""
        PRAGMA foreign_keys=ON;
        PRAGMA user_version=2;
        CREATE TABLE source(id TEXT PRIMARY KEY,title TEXT NOT NULL,license TEXT NOT NULL,review_status TEXT NOT NULL);
        CREATE TABLE dict_entry(id TEXT PRIMARY KEY,headword TEXT NOT NULL UNIQUE,pinyin TEXT NOT NULL,summary TEXT NOT NULL,metadata_json TEXT NOT NULL);
        CREATE TABLE dict_sense(id TEXT PRIMARY KEY,entry_id TEXT NOT NULL REFERENCES dict_entry(id),gloss TEXT NOT NULL,pos TEXT NOT NULL,example TEXT NOT NULL,translation TEXT NOT NULL,reference TEXT NOT NULL);
        CREATE INDEX sense_entry ON dict_sense(entry_id);
        CREATE INDEX entry_headword ON dict_entry(headword);
        CREATE TABLE dict_alias(alias TEXT NOT NULL,entry_id TEXT NOT NULL REFERENCES dict_entry(id),kind TEXT NOT NULL,PRIMARY KEY(alias,entry_id));
        CREATE TABLE passage(id TEXT PRIMARY KEY,original TEXT NOT NULL,translation TEXT NOT NULL,direction TEXT NOT NULL,work TEXT NOT NULL,analysis TEXT NOT NULL,entry_ids TEXT NOT NULL,normalized TEXT NOT NULL);
        CREATE INDEX passage_lookup ON passage(direction,normalized);
        CREATE TABLE question(id TEXT PRIMARY KEY,stem TEXT NOT NULL,options_json TEXT NOT NULL,answer INTEGER NOT NULL,explanation TEXT NOT NULL,entry_id TEXT NOT NULL REFERENCES dict_entry(id));
    """)
    db.execute("INSERT INTO source VALUES(?,?,?,?)", (PACK_ID, "许慎《说文解字》及段玉裁注；Shuowen.org 整理数据", "Apache-2.0; public-domain ancient texts", "upstream_transcription_not_independently_scholarly_reviewed"))
    notes_count = 0
    for word, rows in groups.items():
        entry_id = f"{PACK_ID}:{rows[0]['id']}"
        pinyin = " / ".join(dict.fromkeys(r["pinyin_full"] for r in rows))
        db.execute("INSERT INTO dict_entry VALUES(?,?,?,?,?)", (entry_id, word, pinyin, rows[0]["explanation"], json.dumps(rows, ensure_ascii=False)))
        for row in rows:
            aliases = set(row.get("indexes", [])) | {variant["wordhead"] for variant in row.get("variants", [])}
            for alias in sorted(aliases):
                if isinstance(alias, str) and alias.strip():
                    db.execute("INSERT OR IGNORE INTO dict_alias VALUES(?,?,?)", (alias, entry_id, "upstream_index_or_variant"))
            reference = f"许慎《说文解字》卷{row['volume']}·{row['radical']}部；Shuowen.org #{row['id']}"
            # Original classical explanations are definitions, not manufactured modern translations/examples.
            db.execute("INSERT INTO dict_sense VALUES(?,?,?,?,?,?,?)", (f"{PACK_ID}:sense:{row['id']}", entry_id, row["explanation"], "说文原释", "", "", reference))
            for index, note in enumerate(row.get("duan_notes", [])):
                if not note.get("note", "").strip():
                    continue
                db.execute("INSERT INTO dict_sense VALUES(?,?,?,?,?,?,?)", (f"{PACK_ID}:note:{row['id']}:{index}", entry_id, note["note"], "段玉裁注", note.get("explanation", ""), "", reference + "；段玉裁《说文解字注》"))
                notes_count += 1
    db.commit()
    if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok" or db.execute("PRAGMA foreign_key_check").fetchall():
        raise ValueError("Database validation failed")
    for character in ("一", "之", "學", "而", "水", "山", "人"):
        row = db.execute("SELECT id,summary FROM dict_entry WHERE headword=? OR id IN (SELECT entry_id FROM dict_alias WHERE alias=?)", (character, character)).fetchone()
        if not row or not row[1]:
            raise ValueError(f"Missing representative character: {character}")
    counts = {table: db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] for table in ("dict_entry", "dict_sense", "dict_alias", "passage", "question")}
    db.close()
    temporary.replace(database)
    manifest = dict(
        schemaVersion=2, id=PACK_ID, type="dictionary", title="说文解字大型字书包", version="1.0.0", revision=1,
        database="sample.db", entryCount=counts["dict_entry"], senseCount=counts["dict_sense"], passageCount=0, questionCount=0,
        originalRecordCount=len(records), annotationCount=notes_count, aliasCount=counts["dict_alias"], language="zh-Hant", definitionLanguage="Classical Chinese",
        description="原文字头、拼音、反切、部首、说文原释、段玉裁注及异体字元数据。古代字书，包含生僻字；不是现代常用词译注库。",
        requiredCapabilities=["multiple-resource-packs", "optional-examples-and-translations", "dictionary-schema-2"],
        compatibilityNote="v0.3.0 尚不支持此独立包；不能作为 sample.basic 覆盖安装。多包客户端应在所有已安装资源中全局查词，并标明来源。",
        reviewStatus="上游转录；结构全量校验与代表字抽查，未经逐条学术审校",
        source=dict(repository=REPO, commit=COMMIT, archiveSha256=sha(archive_bytes), recordPaths="data/*.json"),
        license="Apache-2.0", attribution="Copyright [2019] [Shuowen.org]. 古籍作者：许慎；古注作者：段玉裁。",
        licenseText=license_text,
        modifications="合并同字字头为一个词条；保留全部原始条目、字段和古注；增加命名空间 ID；转换为 SQLite；未生成现代译文或试题。",
        files=[dict(path="sample.db", bytes=database.stat().st_size, sha256=sha(database.read_bytes()))],
    )
    manifest_path = output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if manifest_path.stat().st_size > 256 * 1024 or database.stat().st_size > 32 * 1024 * 1024:
        raise ValueError("Pack exceeds planned reader limits")
    target = output / "shuowen-dictionary-1.0.0.zip"
    with zipfile.ZipFile(target, "w") as package:
        for file in (manifest_path, database):
            info = zipfile.ZipInfo(file.name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            package.writestr(info, file.read_bytes())
    report = dict(id=PACK_ID, upstreamCommit=COMMIT, originalRecords=len(records), uniqueHeadwords=len(groups),
                  repeatedHeadwords=sum(len(rows) > 1 for rows in groups.values()), annotationCount=notes_count,aliasCount=counts["dict_alias"],
                  databaseBytes=database.stat().st_size, zipBytes=target.stat().st_size, zipSha256=sha(target.read_bytes()),
                  tests=["all required fields present", "original IDs unique", "all original records retained in metadata", "duplicate headwords grouped without dropping definitions", "SQLite integrity_check=ok", "foreign_key_check=empty", "representative characters present", "size limits"],
                  compatibility="Requires multi-pack schema-2 reader; v0.3.0 is not compatible")
    (output / "validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--output", default=ROOT / "dist/resources/shuowen", type=Path)
    args = parser.parse_args()
    build(args.archive, args.output)
