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
OUTPUT = ROOT / "dist/resource-build"


def build():
    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    for section in ("entries", "passages", "questions"):
        ids = [row["id"] for row in data[section]]
        if len(ids) != len(set(ids)): raise ValueError(f"Duplicate IDs in {section}")
    ids = {entry["id"] for entry in data["entries"]}
    for passage in data["passages"]:
        if not set(passage["entryIds"]) <= ids: raise ValueError("Unresolved passage link")
        if passage["direction"] not in ("CLASSICAL_TO_ZH", "ZH_TO_EN"): raise ValueError("Invalid direction")
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
        if not (2 <= len(q["options"]) <= 8 and 0 <= q["answer"] < len(q["options"])): raise ValueError("Invalid answer")
        db.execute("INSERT INTO question VALUES(?,?,?,?,?,?)", (q["id"], q["stem"], json.dumps(q["options"], ensure_ascii=False), q["answer"], q["explanation"], q["entryId"]))
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    assert not db.execute("PRAGMA foreign_key_check").fetchall()
    db.commit()
    db.close()
    target = OUTPUT / "sample.db"
    temporary.replace(target)
    config = json.loads((ROOT / "tools/pack-builder/resource.json").read_text(encoding="utf-8"))
    manifest = dict(id="sample.basic", type="base", version=config["version"], revision=config["revision"], schemaVersion=1,
                    minAppVersionCode=3, minAppVersion="0.3.0", database="sample.db", license="CC0-1.0; public-domain ancient texts",
                    source="tools/pack-builder/sample.json", reviewStatus="原创教学示例，未经学术审校",
                    entryCount=len(data["entries"]), passageCount=len(data["passages"]), questionCount=len(data["questions"]),
                    files=[dict(path=target.name, sha256=hashlib.sha256(target.read_bytes()).hexdigest(), bytes=target.stat().st_size)])
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    export = ROOT / "dist/resources"
    export.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(export / f'common-basic-{config["version"]}.zip', "w", zipfile.ZIP_DEFLATED) as package:
        for path in (target, OUTPUT / "manifest.json"):
            info = zipfile.ZipInfo(path.name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            package.writestr(info, path.read_bytes())
    archive = export / f'common-basic-{config["version"]}.zip'
    index = dict(schemaVersion=1,id="sample.basic",version=config["version"],revision=config["revision"],minAppVersionCode=3,
                 url=f'https://github.com/L-use/WenYanTong/releases/download/resource-{config["version"]}/{archive.name}',
                 bytes=archive.stat().st_size,sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),notes=config["notes"])
    (export / "resources.json").write_text(json.dumps(index,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    build()
