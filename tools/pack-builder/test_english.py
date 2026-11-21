"""Verify complete original word coverage, provenance, license boundaries and v0.4-compatible fields."""
import hashlib,json,sqlite3,unittest,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'dist/resources/english'
BASE=ROOT/'.tools/upstream/english'

class EnglishPackTest(unittest.TestCase):
    def test_both_artifacts_are_consistent_and_keep_field_sources(self):
        for identity,expected in [('english.cet6.kyle',3991),('english.cet.examdata',5276)]:
            with self.subTest(pack=identity):
                folder=OUT/identity;m=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
                self.assertEqual(expected,m['entryCount']);self.assertEqual('en',m['language'])
                self.assertEqual(4,m['minAppVersionCode']);self.assertTrue(m['licenseText'])
                with zipfile.ZipFile(folder/(identity+'-1.0.0.zip')) as z:
                    self.assertEqual({'manifest.json','sample.db'},set(z.namelist()))
                    self.assertEqual(m['files'][0]['sha256'],hashlib.sha256(z.read('sample.db')).hexdigest())
                with sqlite3.connect(folder/'sample.db') as db:
                    self.assertEqual('ok',db.execute('PRAGMA integrity_check').fetchone()[0]);self.assertEqual([],db.execute('PRAGMA foreign_key_check').fetchall())
                    self.assertEqual(0,db.execute("SELECT COUNT(*) FROM dict_entry WHERE id='' OR headword='' OR summary='' OR pinyin=''").fetchone()[0])
                    for field in ['gloss','example','translation']:
                        self.assertEqual(0,db.execute(f"SELECT COUNT(*) FROM dict_sense s WHERE {field}<>'' AND NOT EXISTS(SELECT 1 FROM record_source r WHERE r.record_type='dict_sense' AND r.record_id=s.id AND r.field=?)",(field,)).fetchone()[0])
                    self.assertEqual(0,db.execute("SELECT COUNT(*) FROM record_source r JOIN dict_entry e ON e.id=r.record_id WHERE r.field='pinyin' AND e.pinyin='音标未收录'").fetchone()[0])
                    self.assertTrue(db.execute("SELECT 1 FROM dict_alias WHERE alias='ABANDON'").fetchone())

    def test_kyle_retains_every_original_book_record_and_distinct_definition(self):
        rows=[json.loads(s) for s in (BASE/'kyle/full_line_jsonl/full/正序/六级.jsonl').read_text(encoding='utf-8').splitlines() if s.strip()]
        with sqlite3.connect(OUT/'english.cet6.kyle/sample.db') as db:
            originals=[json.loads(r[0]) for r in db.execute('SELECT upstream_json FROM provenance ORDER BY id')]
            self.assertEqual(rows,originals)
            self.assertEqual({r['headWord'].casefold() for r in rows},{r[0] for r in db.execute('SELECT headword FROM dict_entry')})
            for record in rows:
                detail=record['content']['word']['content']
                for meaning in detail.get('trans',[]):
                    if meaning.get('tranCn','').strip():
                        self.assertTrue(db.execute('SELECT 1 FROM dict_sense s JOIN dict_entry e ON e.id=s.entry_id WHERE e.headword=? AND s.gloss=?',(record['headWord'].casefold(),meaning['tranCn'].strip())).fetchone())

    def test_exam_license_ai_labels_and_exact_source_coverage(self):
        rows=json.loads((BASE/'exam/cet_full_list.json').read_text(encoding='utf-8'))['四六级词汇词频排序表']
        folder=OUT/'english.cet.examdata';m=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual('CC-BY-NC-SA-4.0',m['license']);self.assertTrue(m['nonCommercial']);self.assertTrue(m['shareAlike'])
        self.assertEqual(5278,len(rows))
        with sqlite3.connect(folder/'sample.db') as db:
            saved=[json.loads(r[0]) for r in db.execute("SELECT upstream_json FROM provenance WHERE field_kind='word_definition' ORDER BY id")]
            self.assertEqual(rows,saved)
            self.assertGreater(db.execute("SELECT COUNT(*) FROM provenance WHERE origin_kind='upstream_ai_generated'").fetchone()[0],0)
            self.assertEqual(0,db.execute("SELECT COUNT(*) FROM dict_entry WHERE pinyin<>'音标未收录'").fetchone()[0])

if __name__=='__main__':unittest.main()
