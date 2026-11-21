"""Fetch pinned English vocabularies without running upstream code."""
import hashlib,json,urllib.request,urllib.parse
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'.tools/upstream/english'
SOURCES=[
    ('kyle','KyleBing/english-vocabulary','731240a848dca14e2cf17510f384312b788863c1',['LICENSE','README.md','full_line_jsonl/full/正序/六级.jsonl']),
    ('exam','exam-data/CETVocabulary','83eaa58b181bc388dc7a662d00179e3d888d900a',['LICENSE','README.md','cet_full_list.json']),
]
def main():
    locks=[]
    for key,repo,commit,paths in SOURCES:
        with urllib.request.urlopen(urllib.request.Request(f'https://api.github.com/repos/{repo}/git/trees/{commit}?recursive=1',headers={'User-Agent':'WenYanTong'}),timeout=40) as response:tree=json.load(response)
        if tree.get('truncated'):raise ValueError('Incomplete tree')
        entries={r['path']:r for r in tree['tree'] if r['type']=='blob'}
        files=[]
        for path in paths:
            row=entries[path];target=BASE/key/path;target.parent.mkdir(parents=True,exist_ok=True)
            def valid(data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==row['sha']
            if not target.exists() or not valid(target.read_bytes()):
                with urllib.request.urlopen(f'https://raw.githubusercontent.com/{repo}/{commit}/{urllib.parse.quote(path)}',timeout=60) as response:data=response.read()
                if not valid(data):raise ValueError('Hash mismatch: '+path)
                target.write_bytes(data)
            files.append(dict(path=path,sha1=row['sha'],bytes=row['size']))
        locks.append(dict(key=key,repository=repo,commit=commit,files=files))
        print(key+' downloaded and verified',flush=True)
    (BASE/'snapshots.json').write_text(json.dumps(locks,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':main()
