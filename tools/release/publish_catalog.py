"""Publish independently versioned resource ZIPs and update the multi-pack catalog last. No Actions."""
import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
REPO='L-use/WenYanTong'

def run(*args):subprocess.run([str(x) for x in args],cwd=ROOT,check=True)
def output(*args):return subprocess.check_output([str(x) for x in args],cwd=ROOT,text=True,encoding='utf-8').strip()

def collect():
    collection=ROOT/'dist/resources/user-collection'
    files=sorted((ROOT/'dist/resources').glob('common-basic-*.zip'))+sorted((ROOT/'dist/resources/shuowen').glob('shuowen-dictionary-*.zip'))
    files+=sorted(collection.glob('*/*.zip'))
    if not files:raise SystemExit('No locally built packages found')
    selected={}
    for archive in files:
        with zipfile.ZipFile(archive) as zipped:
            if set(zipped.namelist())!={'manifest.json','sample.db'}:raise SystemExit('Invalid package contents')
            m=json.loads(zipped.read('manifest.json'))
            db=zipped.read('sample.db');info=m['files'][0]
            if len(db)!=info['bytes'] or hashlib.sha256(db).hexdigest()!=info['sha256']:raise SystemExit('Resource DB mismatch')
        basic=m['id']=='sample.basic'
        tag=f'resource-{m["version"]}' if basic else f'pack-{m["id"]}-{m["version"]}'
        title=m.get('title','常用文言文基础库')
        notes=m.get('description',f'{m["entryCount"]} 个字词；基础古文对照及单选题。')
        item=dict(id=m['id'],title=title,version=m['version'],revision=m['revision'],tag=tag,
            url=f'https://github.com/{REPO}/releases/download/{tag}/{archive.name}',bytes=archive.stat().st_size,
            sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),minAppVersionCode=4,
            entryCount=m['entryCount'],articleCount=m.get('articleCount',0),notes=notes,
            license=m['license'],reviewStatus=m.get('reviewStatus',''))
        previous=selected.get(item['id'])
        if previous and previous[0]['revision']==item['revision'] and previous[0]['sha256']!=item['sha256']:
            raise SystemExit('Same resource revision has different contents: '+item['id'])
        if not previous or previous[0]['revision']<item['revision']:selected[item['id']]=(item,archive)
    return list(selected.values())

def main(local_only=False):
    packs=collect()
    catalog=ROOT/'dist/resources/catalog-v2.json'
    catalog.write_text(json.dumps(dict(schemaVersion=2,packs=[p for p,_ in packs]),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if local_only:
        print(f'Catalog validated locally: {len(packs)} independent packs');return
    if output('git','status','--porcelain'):raise SystemExit('Commit source changes before publishing')
    run('git','push','origin','main')
    for item,archive in packs:
        result=subprocess.run(['gh','release','view',item['tag'],'--repo',REPO,'--json','assets,isDraft'],capture_output=True,text=True,encoding='utf-8')
        if result.returncode==0:
            release=json.loads(result.stdout)
            matching=[a for a in release['assets'] if a['name']==archive.name]
            if release['isDraft'] or len(matching)!=1 or matching[0].get('digest')!='sha256:'+item['sha256']:
                raise SystemExit('Existing immutable resource differs: '+item['id'])
        else:
            run('gh','release','create',item['tag'],archive,'--repo',REPO,'--target','main','--latest=false',
                '--title',item['title']+' '+item['version'],'--notes',item['notes']+'\n\n'+item['reviewStatus'])
    # The stable catalog has a fixed filename, separate from the legacy v1 and the APK latest pointer.
    stable=catalog.with_name('resources.json');stable.write_bytes(catalog.read_bytes())
    existing=subprocess.run(['gh','release','view','resources-v2','--repo',REPO],capture_output=True)
    if existing.returncode:
        run('gh','release','create','resources-v2',stable,'--repo',REPO,'--target','main','--latest=false','--title','独立资源包目录','--notes','每个资源包独立安装、更新和删除；多包客户端使用此索引。')
    else:run('gh','release','upload','resources-v2',stable,'--repo',REPO,'--clobber')
    print(f'Published {len(packs)} independent packages and resources-v2 catalog.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--local-only',action='store_true')
    main(parser.parse_args().local_only)
