"""Build the bundled offline sample using only Python's standard library.

Run: python tools/pack-builder/build.py
The source translations, glosses and questions are original project samples (CC0).
Ancient texts are public domain. These samples are not a reviewed scholarly edition.
"""
import hashlib
import json
import sqlite3
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/pack-builder/sample.json"
OUTPUT = ROOT / "app/src/main/assets/packs"


def build():
    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT / "sample.tmp.db"
    temporary.unlink(missing_ok=True)
    db = sqlite3.connect(temporary)
    db.executescript("""
      PRAGMA foreign_keys=ON;
      PRAGMA user_version=1;
      CREATE TABLE source(id TEXT PRIMARY KEY,title TEXT NOT NULL,license TEXT NOT NULL,review_status TEXT NOT NULL);
      CREATE TABLE dict_entry(id TEXT PRIMARY KEY,headword TEXT NOT NULL UNIQUE,pinyin TEXT NOT NULL,summary TEXT NOT NULL);
      CREATE TABLE dict_sense(id TEXT PRIMARY KEY,entry_id TEXT NOT NULL REFERENCES dict_entry(id),gloss TEXT NOT NULL,pos TEXT NOT NULL,example TEXT NOT NULL,translation TEXT NOT NULL,reference TEXT NOT NULL);
      CREATE INDEX sense_entry ON dict_sense(entry_id);
      CREATE TABLE passage(id TEXT PRIMARY KEY,original TEXT NOT NULL,translation TEXT NOT NULL,direction TEXT NOT NULL,work TEXT NOT NULL,analysis TEXT NOT NULL,entry_ids TEXT NOT NULL,normalized TEXT NOT NULL);
      CREATE INDEX passage_lookup ON passage(direction,normalized);
      CREATE TABLE question(id TEXT PRIMARY KEY,stem TEXT NOT NULL,options_json TEXT NOT NULL,answer INTEGER NOT NULL,explanation TEXT NOT NULL,entry_id TEXT NOT NULL REFERENCES dict_entry(id));
    """)
    db.execute("INSERT INTO source VALUES(?,?,?,?)", ("sample", "古籍公有领域原文；项目原创释义、译文和题目", "CC0-1.0 (original annotations); public-domain ancient texts", "sample_not_scholarly_reviewed"))
    for e in data["entries"]:
        db.execute("INSERT INTO dict_entry VALUES(?,?,?,?)", (e["id"], e["headword"], e["pinyin"], e["summary"]))
        for s in e["senses"]:
            db.execute("INSERT INTO dict_sense VALUES(?,?,?,?,?,?,?)", (s["id"], e["id"], s["gloss"], s["pos"], s["example"], s["translation"], s["source"]))
    punctuation = set('，。？！；：、,.?!;:\"“”‘’（）()')
    for p in data["passages"]:
        normalized = ''.join(c for c in p["original"].strip().lower() if not c.isspace() and c not in punctuation)
        db.execute("INSERT INTO passage VALUES(?,?,?,?,?,?,?,?)", (p["id"], p["original"], p["translation"], p["direction"], p["work"], p["analysis"], json.dumps(p["entryIds"]), normalized))
    for q in data["questions"]:
        assert len(q["options"]) >= 2 and 0 <= q["answer"] < len(q["options"])
        db.execute("INSERT INTO question VALUES(?,?,?,?,?,?)", (q["id"], q["stem"], json.dumps(q["options"], ensure_ascii=False), q["answer"], q["explanation"], q["entryId"]))
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    assert not db.execute("PRAGMA foreign_key_check").fetchall()
    db.commit()
    db.close()
    target = OUTPUT / "sample.db"
    temporary.replace(target)
    manifest = dict(id="sample.basic", type="sample", version="0.1.0", schemaVersion=1,
                    minAppVersion="0.1.0", database="sample.db", license="CC0-1.0; public-domain ancient texts",
                    source="tools/pack-builder/sample.json", reviewStatus="原创教学示例，未经学术审校",
                    entryCount=len(data["entries"]), passageCount=len(data["passages"]), questionCount=len(data["questions"]),
                    files=[dict(path=target.name, sha256=hashlib.sha256(target.read_bytes()).hexdigest(), bytes=target.stat().st_size)])
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    export = ROOT / "dist/resources"
    export.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(export / "sample-basic-0.1.0.zip", "w", zipfile.ZIP_DEFLATED) as package:
        package.write(target, target.name)
        package.write(OUTPUT / "manifest.json", "manifest.json")
    print(json.dumps(manifest, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    build()
