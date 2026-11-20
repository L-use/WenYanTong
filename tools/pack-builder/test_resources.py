"""Validate relationships and provenance that must stay intact in sample data."""
import hashlib
import json
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class SampleResourcesTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((ROOT / "tools/pack-builder/sample.json").read_text(encoding="utf-8"))

    def test_every_link_resolves_and_every_example_has_provenance(self):
        ids = {entry["id"] for entry in self.data["entries"]}
        self.assertEqual(len(ids), len(self.data["entries"]))
        sense_ids = []
        for entry in self.data["entries"]:
            for sense in entry["senses"]:
                self.assertTrue(sense["source"].strip())
                self.assertTrue(sense["example"].strip())
                self.assertTrue(sense["translation"].strip())
                sense_ids.append(sense["id"])
        self.assertEqual(len(sense_ids), len(set(sense_ids)))
        for passage in self.data["passages"]:
            self.assertTrue(set(passage["entryIds"]) <= ids)
            self.assertTrue(passage["work"].strip())
        for question in self.data["questions"]:
            self.assertIn(question["entryId"], ids)
            self.assertTrue(0 <= question["answer"] < len(question["options"]))

    def test_manifest_hash_matches_database_and_sqlite_constraints_hold(self):
        assets = ROOT / "app/src/main/assets/packs"
        manifest = json.loads((assets / "manifest.json").read_text(encoding="utf-8"))
        database = assets / manifest["database"]
        self.assertEqual(manifest["files"][0]["sha256"], hashlib.sha256(database.read_bytes()).hexdigest())
        self.assertEqual(manifest["files"][0]["bytes"], database.stat().st_size)
        with sqlite3.connect(f"file:{database.as_posix()}?mode=ro", uri=True) as db:
            self.assertEqual("ok", db.execute("PRAGMA integrity_check").fetchone()[0])
            self.assertFalse(db.execute("PRAGMA foreign_key_check").fetchall())
            self.assertEqual(4, db.execute("SELECT count(*) FROM dict_sense WHERE entry_id='zhi'").fetchone()[0])
            self.assertEqual(4, db.execute("SELECT count(*) FROM question WHERE entry_id='zhi'").fetchone()[0])
            for table, count in [("dict_entry", "entryCount"), ("passage", "passageCount"), ("question", "questionCount")]:
                self.assertEqual(manifest[count], db.execute(f"SELECT count(*) FROM {table}").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
