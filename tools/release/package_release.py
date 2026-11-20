"""Package signed GitHub release assets using Python's standard library."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def package(apk, repository, tag, output, aapt2):
    version = dict(line.split('=',1) for line in (ROOT/'version.properties').read_text(encoding='utf-8').splitlines() if '=' in line)
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',repository):
        raise ValueError('Invalid repository')
    if tag != 'v'+version['versionName'] or repository != version['updateRepository']:
        raise ValueError('Tag/repository differs from version.properties')
    badging = subprocess.check_output([aapt2,'dump','badging',str(apk)],text=True,encoding='utf-8')
    info = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    minimum = re.search(r"(?:minSdkVersion|sdkVersion):'(\d+)'",badging)
    if not info or not minimum: raise ValueError('Cannot read APK metadata')
    name,code,label = info.groups()
    if name != 'com.wenyantong.app' or label != version['versionName'] or int(code) != int(version['versionCode']):
        raise ValueError('APK package/version mismatch')
    if 'application-debuggable' in badging: raise ValueError('Refusing debuggable APK')
    notes=(ROOT/'docs/releases'/f'{label}.md').read_text(encoding='utf-8').strip()
    output.mkdir(parents=True,exist_ok=True)
    target=output/f'WenYanTong-{label}.apk'
    if apk.resolve()!=target.resolve(): shutil.copy2(apk,target)
    metadata=dict(schemaVersion=1,packageName=name,versionName=label,versionCode=int(code),minSdk=int(minimum.group(1)),tag=tag,
        apkUrl=f'https://github.com/{repository}/releases/download/{tag}/{target.name}',apkBytes=target.stat().st_size,
        sha256=hashlib.sha256(target.read_bytes()).hexdigest(),releaseUrl=f'https://github.com/{repository}/releases/tag/{tag}',notes=notes)
    (output/'update.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (output/'SHA256SUMS').write_text('\n'.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}' for p in [target,output/'update.json'])+'\n',encoding='ascii')
    print(f'Packaged {target.name}, update.json and SHA256SUMS')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--apk',required=True,type=Path)
    parser.add_argument('--repository',required=True)
    parser.add_argument('--tag',required=True)
    parser.add_argument('--output',type=Path,default=ROOT/'dist/release')
    parser.add_argument('--aapt2',required=True)
    a=parser.parse_args()
    package(a.apk,a.repository,a.tag,a.output,a.aapt2)
