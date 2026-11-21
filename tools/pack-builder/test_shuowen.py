"""Check the built artifact against original records, not just declared counts."""
import hashlib
import json
import sqlite3
import unittest
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'dist/resources/shuowen'

class ShuowenPackTest(unittest.TestCase):
    def test_archive_license_hash_and_all_original_records(self):
        manifest=json.loads((FOLDER/'manifest.json').read_text(encoding='utf-8'))
        with zipfile.ZipFile(FOLDER/'shuowen-dictionary-1.0.0.zip') as package:
            self.assertEqual({'manifest.json','sample.db'},set(package.namelist()))
            self.assertEqual(manifest,json.loads(package.read('manifest.json')))
            self.assertEqual(manifest['files'][0]['sha256'],hashlib.sha256(package.read('sample.db')).hexdigest())
        self.assertEqual('dictionary.shuowen',manifest['id'])
        self.assertEqual(2,manifest['schemaVersion'])
        self.assertIn('Apache License',manifest['licenseText'])
        self.assertIn('Shuowen.org',manifest['attribution'])
        with zipfile.ZipFile(ROOT/'.tools/upstream/shuowen-6553a350.zip') as upstream:
            original={r['id']:r for r in (json.loads(upstream.read(n)) for n in upstream.namelist() if '/data/' in n and n.endswith('.json'))}
        with sqlite3.connect(f"file:{(FOLDER/'sample.db').as_posix()}?mode=ro",uri=True) as db:
            converted={r['id']:r for row in db.execute('SELECT metadata_json FROM dict_entry') for r in json.loads(row[0])}
            self.assertEqual(original,converted)
            self.assertEqual(9833,len(converted))
            self.assertEqual(9815,db.execute('SELECT COUNT(*) FROM dict_entry').fetchone()[0])
            for record in original.values():
                self.assertEqual(record['explanation'],db.execute('SELECT gloss FROM dict_sense WHERE id=?',(f"dictionary.shuowen:sense:{record['id']}",)).fetchone()[0])
                for i,note in enumerate(record.get('duan_notes',[])):
                    if note.get('note','').strip():
                        self.assertEqual(note['note'],db.execute('SELECT gloss FROM dict_sense WHERE id=?',(f"dictionary.shuowen:note:{record['id']}:{i}",)).fetchone()[0])
            self.assertGreater(db.execute("SELECT COUNT(*) FROM dict_alias WHERE alias='學'").fetchone()[0],0)
            self.assertEqual(0,db.execute("SELECT COUNT(*) FROM dict_sense WHERE translation<>''").fetchone()[0])
            self.assertEqual('ok',db.execute('PRAGMA integrity_check').fetchone()[0])
            self.assertEqual([],db.execute('PRAGMA foreign_key_check').fetchall())

if __name__=='__main__':unittest.main()
