"""One-command release: local signing, GitHub CI validation, then public APK assets.

Private signing material is never sent to GitHub. Run from a clean main checkout.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
from package_release import ROOT, package

def run(args, **kwargs):
    subprocess.run([str(a) for a in args],cwd=ROOT,check=True,**kwargs)

def output(args):
    return subprocess.check_output([str(a) for a in args],cwd=ROOT,text=True,encoding='utf-8').strip()

def props(path):
    return dict(line.split('=',1) for line in path.read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))

def main(local_checks_only=False):
    version=props(ROOT/'version.properties')
    repository=version['updateRepository']
    tag='v'+version['versionName']
    if output(['git','branch','--show-current'])!='main': raise SystemExit('Publish from main only')
    if output(['git','status','--porcelain']): raise SystemExit('Commit the working tree before publishing')
    if not (ROOT/'.signing/release.properties').exists() and not os.getenv('ANDROID_KEYSTORE_PATH'):
        raise SystemExit('Local signing identity is not configured; see docs/RELEASING.md')
    existing=subprocess.run(['gh','release','view',tag,'--repo',repository,'--json','isDraft'],capture_output=True,text=True)
    if existing.returncode==0: raise SystemExit('This version already has a release; increase the version')
    env=os.environ.copy()
    if not env.get('JAVA_HOME') and os.name=='nt':
        candidate=Path(os.getenv('ProgramFiles','C:/Program Files'))/'Android/Android Studio/jbr'
        if candidate.exists(): env['JAVA_HOME']=str(candidate)
    env.setdefault('GRADLE_USER_HOME',str(ROOT/'.gradle-user-home'))
    if os.name=='nt':
        cached=ROOT/'.tools/gradle-9.1.0/bin/gradle.bat'
        gradle=['cmd.exe','/d','/c',str(cached if cached.exists() else ROOT/'gradlew.bat')]
    else:
        gradle=['sh',str(ROOT/'gradlew')]
    print('Building and signing locally; no private credentials leave this computer.',flush=True)
    run([sys.executable,ROOT/'tools/pack-builder/test_resources.py'])
    run(gradle+['--no-daemon','testDebugUnitTest','lintRelease','assembleRelease'],env=env)
    sdk=env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT')
    if not sdk:
        sdk=props(ROOT/'local.properties')['sdk.dir'].replace('\\:',':').replace('\\\\','\\')
    sdk=Path(sdk)/'build-tools/36.0.0'
    java=Path(env['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java') if env.get('JAVA_HOME') else Path(shutil.which('java') or 'java')
    apk=ROOT/'app/build/outputs/apk/release/app-release.apk'
    run([java,'-jar',sdk/'lib/apksigner.jar','verify','--verbose',apk])
    package(apk,repository,tag,ROOT/'dist/release',str(sdk/('aapt2.exe' if os.name=='nt' else 'aapt2')))
    if output(['git','status','--porcelain']): raise SystemExit('Build changed tracked files; review and commit before publishing')
    sha=output(['git','rev-parse','HEAD'])
    tag_check=subprocess.run(['git','rev-parse','--verify',f'refs/tags/{tag}'],capture_output=True,text=True)
    if tag_check.returncode:
        run(['git','tag','-a',tag,'-m',f'WenYanTong {version["versionName"]}'])
    elif output(['git','rev-list','-n','1',tag])!=sha:
        raise SystemExit('Tag already points to another commit; choose a new version')
    run(['git','push','origin','main'])
    run(['git','push','origin',tag])
    if local_checks_only:
        print('Using complete local verification; no cloud CI result is claimed.',flush=True)
    else:
        print('Waiting for GitHub release-candidate checks...',flush=True)
        deadline=time.monotonic()+1200
        while time.monotonic()<deadline:
            jobs=json.loads(output(['gh','run','list','--repo',repository,'--workflow','release.yml','--commit',sha,'--limit','20','--json','databaseId,status,conclusion,headBranch,event']))
            jobs=[job for job in jobs if job['event']=='push']
            if jobs:
                job=jobs[0]
                if job['status']=='completed':
                    if job['conclusion']!='success': raise SystemExit(f'GitHub checks failed; inspect run {job["databaseId"]}. For unavailable runners, use --local-checks-only after reviewing the cause.')
                    break
            time.sleep(10)
        else: raise SystemExit('GitHub checks did not finish in time; rerun this command after they finish')
    assets=ROOT/'dist/release'
    run(['gh','release','create',tag,assets/f'WenYanTong-{version["versionName"]}.apk',assets/'update.json',assets/'SHA256SUMS',
         '--repo',repository,'--verify-tag','--draft','--title',f'文言通 {version["versionName"]}',
         '--notes-file',ROOT/'docs/releases'/f'{version["versionName"]}.md'])
    run(['gh','release','edit',tag,'--repo',repository,'--draft=false','--latest'])
    print(f'Published https://github.com/{repository}/releases/tag/{tag}',flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--local-checks-only',action='store_true',help='Run all local checks without requiring a cloud CI result')
    main(parser.parse_args().local_checks_only)
