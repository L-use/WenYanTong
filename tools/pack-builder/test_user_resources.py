"""Independent artifact checks: complete provenance, no invented translations, exact dedup retains sources."""
import json
import hashlib
import sqlite3
import tempfile
import unittest
import zipfile
from pathlib import Path
from source_pack import Pack

ROOT=Path(__file__).resolve().parents[2]
OUTPUT=ROOT/'dist/resources/user-collection'

class UserResourcesTest(unittest.TestCase):
    def test_every_pack_has_valid_data_and_field_sources(self):
        summary=json.loads((OUTPUT/'collection.json').read_text(encoding='utf-8'))
        self.assertEqual(6,summary['packCount'])
        for report in summary['packs']:
            with self.subTest(pack=report['id']):
                folder=OUTPUT/report['id'];m=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
                package=folder/(report['id']+'-1.0.0.zip')
                self.assertEqual(report['sha256'],hashlib.sha256(package.read_bytes()).hexdigest())
                with zipfile.ZipFile(package) as archive:
                    self.assertEqual({'manifest.json','sample.db'},set(archive.namelist()))
                    self.assertEqual(m,json.loads(archive.read('manifest.json')))
                    self.assertEqual(m['files'][0]['sha256'],hashlib.sha256(archive.read('sample.db')).hexdigest())
                self.assertTrue(m['licenseText'])
                with sqlite3.connect(f"file:{(folder/'sample.db').as_posix()}?mode=ro",uri=True) as db:
                    self.assertEqual('ok',db.execute('PRAGMA integrity_check').fetchone()[0])
                    self.assertEqual([],db.execute('PRAGMA foreign_key_check').fetchall())
                    for table,field in [('dict_entry','headword'),('dict_sense','gloss'),('passage','original'),('article','original')]:
                        missing=db.execute(f"SELECT COUNT(*) FROM {table} r WHERE NOT EXISTS(SELECT 1 FROM record_source s WHERE s.record_type=? AND s.record_id=r.id AND s.field=?)",(table,field)).fetchone()[0]
                        self.assertEqual(0,missing,(table,field))
                    for table in ['passage','article','dict_sense']:
                        missing=db.execute(f"SELECT COUNT(*) FROM {table} r WHERE r.translation<>'' AND NOT EXISTS(SELECT 1 FROM record_source s WHERE s.record_type=? AND s.record_id=r.id AND s.field='translation')",(table,)).fetchone()[0]
                        self.assertEqual(0,missing,table)
                    self.assertEqual(0,db.execute("SELECT COUNT(*) FROM source_file WHERE repository='' OR commit_sha='' OR file_path='' OR url='' OR license=''").fetchone()[0])
                    self.assertEqual(0,db.execute("SELECT COUNT(*) FROM provenance WHERE locator='' OR origin_kind=''").fetchone()[0])
                    if report['id'].startswith('corpus.'):
                        self.assertEqual(0,db.execute("SELECT COUNT(*) FROM article WHERE translation<>''").fetchone()[0])
                    if report['id']=='reading.guwenguanzhi':
                        self.assertEqual(222,m['articleCount'])
                        self.assertEqual(0,db.execute("SELECT COUNT(*) FROM record_source s JOIN provenance p ON p.id=s.provenance_id WHERE s.field='translation' AND p.origin_kind<>'upstream_ai_generated'").fetchone()[0])

    def test_cctc_dedup_keeps_all_original_pair_locations(self):
        folder=OUTPUT/'reading.cctc'
        input_root=ROOT/'.tools/upstream/user-resources/cctc'
        pairs=[]
        for file in input_root.rglob('*.json'):
            for chapter in json.loads(file.read_text(encoding='utf-8-sig')):
                pairs.extend(chapter['contents'])
        self.assertEqual(7841,len(pairs))
        with sqlite3.connect(folder/'sample.db') as db:
            converted=set(db.execute('SELECT original,translation FROM passage'))
            self.assertEqual({(p['source'].strip(),p['target'].strip()) for p in pairs},converted)
            self.assertEqual(len(pairs)*2,db.execute("SELECT COUNT(*) FROM provenance WHERE locator LIKE '%].contents[%'").fetchone()[0])

    def test_exact_duplicates_merge_but_keep_both_sources_and_alternative_translation(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack=Pack(temporary,'test.pack','test','https://github.com/example/data','a'*40,'MIT','MIT','test')
            one=pack.source('one.json','line:1','book one','author','original','test')
            two=pack.source('two.json','line:2','book two','author','original','test')
            first=pack.passage('学而时习之。','温习所学。','book one','',[],one,one)
            duplicate=pack.passage('学而时习之。','温习所学。','book two','',[],two,two)
            different=pack.passage('学而时习之。','按时练习所学。','book two','',[],two,two)
            self.assertEqual(first,duplicate);self.assertNotEqual(first,different)
            self.assertEqual(2,pack.db.execute("SELECT COUNT(*) FROM record_source WHERE record_type='passage' AND record_id=? AND field='original'",(first,)).fetchone()[0])
            pack.db.close()

if __name__=='__main__':unittest.main()
