import concurrent.futures, hashlib, json, pathlib, time, urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[2]
BASE=ROOT/'.tools/upstream/user-resources'
SPECS={
 'guwenguanzhi':('niuniu-869/guwenguanzhi','3f20cb23c0e14eeeffb405bc19d32c43b7f6e5b6','niuniu-869-guwenguanzhi',lambda p:p.startswith('data/articles/') or p in ['data/catalog.json','LICENSE','README.md']),
 'cctc':('Scagin/cctc','8a60d1478e2240df8fae3747ada347ff332b1d5f','Scagin-cctc',lambda p:p.endswith('.json') or p in ['LICENSE','README.md']),
 'classical-corpus':('gujilab/chinese-classical-corpus','09a6873e46760f20027e20bf48814f5c5cc05d66','gujilab-chinese-classical-corpus',lambda p:p in ['output/corpus.jsonl','LICENSE','README.md']),
 'tongjiazi':('frederick-wang/tongjiazi-resources','ac5273e854724ef33b8912e050afb984695a1065','frederick-wang-tongjiazi-resources',lambda p:p in ['corpus/tongjiazi_corpus.zip','knowledge_base/tongjiazi_knowledge_base.zip','LICENSE','README.md']),
}
def fetch(item):
 key,repo,commit,row=item
 path=row['path'];target=BASE/key/path;target.parent.mkdir(parents=True,exist_ok=True)
 def valid(data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==row['sha']
 if target.exists() and valid(target.read_bytes()):return
 url=f'https://raw.githubusercontent.com/{repo}/{commit}/{urllib.parse.quote(path)}'
 for attempt in range(4):
  try:
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'WenYanTong-ResourceBuilder'}),timeout=50) as response:data=response.read()
   if not valid(data):raise ValueError('Git blob hash mismatch: '+path)
   target.write_bytes(data);return
  except Exception:
   if attempt==3:raise
   time.sleep(attempt+1)
def main():
 items=[];locks=[]
 for key,(repo,commit,tree,select) in SPECS.items():
  url=f'https://api.github.com/repos/{repo}/git/trees/{commit}?recursive=1'
  with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'WenYanTong-ResourceBuilder'}),timeout=50) as response:
   tree_data=json.load(response)
  if tree_data.get('truncated'):raise ValueError('Incomplete tree listing')
  rows=tree_data['tree']
  chosen=[r for r in rows if r['type']=='blob' and select(r['path'])]
  items.extend((key,repo,commit,r) for r in chosen)
  locks.append(dict(key=key,repository=repo,commit=commit,files=[dict(path=r['path'],gitBlobSha1=r['sha'],bytes=r['size']) for r in chosen]))
 with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
  futures=[pool.submit(fetch,item) for item in items]
  for i,f in enumerate(concurrent.futures.as_completed(futures),1):
   f.result()
   if i%40==0 or i==len(items):print(f'{i}/{len(items)} files verified',flush=True)
 (BASE/'snapshots.json').write_text(json.dumps(locks,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':main()
