"""Build and publish a resource release without Actions; update its independent index last."""
import json, subprocess, sys, tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def run(*args):
    subprocess.run([str(a) for a in args],cwd=ROOT,check=True)
def main():
    if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT).strip():
        raise SystemExit('Commit all source/resource changes before publishing')
    run(sys.executable,ROOT/'tools/pack-builder/build.py')
    run(sys.executable,ROOT/'tools/pack-builder/test_resources.py')
    if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT).strip():
        raise SystemExit('Resource build differs from committed assets; commit generated assets first')
    config=json.loads((ROOT/'tools/pack-builder/resource.json').read_text(encoding='utf-8'))
    repo='L-use/WenYanTong';tag='resource-'+config['version']
    index=subprocess.run(['gh','release','view','resources-v1','--repo',repo],capture_output=True)
    if index.returncode==0:
        with tempfile.TemporaryDirectory() as temporary:
            run('gh','release','download','resources-v1','--repo',repo,'--pattern','resources.json','--dir',temporary)
            previous=json.loads((Path(temporary)/'resources.json').read_text(encoding='utf-8'))
            if config['revision']<=previous['revision']: raise SystemExit('Resource revision must increase')
    existing=subprocess.run(['gh','release','view',tag,'--repo',repo],capture_output=True)
    if existing.returncode==0: raise SystemExit('Resource version already exists; increase version and revision')
    folder=ROOT/'dist/resources'
    run('git','push','origin','main')
    run('gh','release','create',tag,folder/f'common-basic-{config["version"]}.zip','--repo',repo,'--target','main','--latest=false','--title',f'常用文言文基础库 {config["version"]}','--notes',config['notes'])
    if index.returncode:
        run('gh','release','create','resources-v1',folder/'resources.json','--repo',repo,'--target','main','--latest=false','--title','离线资源更新索引','--notes','客户端专用资源索引；APK 更新使用独立渠道。')
    else:
        # Only this small mutable index is replaced; versioned ZIP releases stay immutable.
        run('gh','release','upload','resources-v1',folder/'resources.json','--repo',repo,'--clobber')
    print('Resource index published; APK latest release was not changed.')
if __name__=='__main__': main()
