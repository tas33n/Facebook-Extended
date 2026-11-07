#!/usr/bin/env python3
"""Drop an original APK/APKS here, then run python patch.py. Never installs."""
import argparse,getpass,json,os,subprocess,sys
from datetime import datetime
from pathlib import Path

ROOT=Path(__file__).resolve().parent
CONFIG=ROOT/'.extended.local.json'

def choose_input(explicit=None,latest=False):
    if explicit:
        path=Path(explicit).expanduser().resolve()
        if not path.exists():raise ValueError('Input does not exist: '+str(path))
        return path
    candidates=sorted([p for p in ROOT.iterdir() if p.is_file() and p.suffix.lower() in ('.apk','.apks')],key=lambda p:p.stat().st_mtime,reverse=True)
    if not candidates:raise ValueError('Place an original Facebook .apk or .apks in '+str(ROOT))
    if len(candidates)>1 and not latest:
        raise ValueError('Multiple inputs found. Pass the filename or --latest:\n'+'\n'.join(p.name for p in candidates))
    return candidates[0]

def configure():
    print('One-time local setup. Paths are stored in ignored .extended.local.json; passwords are not.')
    def ask(label,default=''):
        answer=input(label+(' ['+default+']' if default else '')+': ').strip().strip('"')
        return answer or default
    java=ask('JDK 21 folder',os.environ.get('JAVA_HOME',''))
    sdk=ask('Android SDK folder',os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','')))
    key=ask('Existing signing keystore path (reuse it for updates)')
    alias=ask('Key alias','extended')
    if not Path(key).is_file():raise ValueError('Provide an existing keystore. Creation instructions: docs/PATCHING.md')
    cfg={'java_home':java,'android_home':sdk,'keystore':str(Path(key).resolve()),'alias':alias,
         'work_root':str(ROOT/'.work'),'output_root':str(ROOT/'artifacts'),'variant':'both'}
    CONFIG.write_text(json.dumps(cfg,indent=2)+'\n',encoding='utf-8')
    return cfg

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('input',nargs='?');p.add_argument('--latest',action='store_true',help='Explicitly choose newest root APK/APKS if several exist')
    p.add_argument('--variant',choices=['main','clone','both']);p.add_argument('--setup',action='store_true')
    p.add_argument('--dry-run',action='store_true',help='Validate paths and print invocation without reading password/building')
    a=p.parse_args()
    try:
        cfg=configure() if a.setup or not CONFIG.exists() else json.loads(CONFIG.read_text(encoding='utf-8'))
        source=choose_input(a.input,a.latest)
        env=os.environ.copy();env['JAVA_HOME']=cfg['java_home'];env['ANDROID_HOME']=cfg['android_home']
        exe='.exe' if os.name=='nt' else ''
        for path in [Path(cfg['java_home'])/'bin'/('java'+exe),Path(cfg['android_home'])/'platforms/android-36/android.jar',Path(cfg['keystore'])]:
            if not path.is_file():raise ValueError('Missing prerequisite: '+str(path))
        run_id=source.stem+'-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f')
        work=Path(cfg.get('work_root',ROOT/'.work'))/run_id
        out=Path(cfg.get('output_root',ROOT/'artifacts'))/run_id
        cmd=[sys.executable,str(ROOT/'scripts/patch.py'),str(source),'--variant',a.variant or cfg.get('variant','both'),
             '--work',str(work),'--out',str(out),'--keystore',cfg['keystore'],'--alias',cfg['alias']]
        print('Input:',source);print('Output:',out)
        if a.dry_run:print(subprocess.list2cmdline(cmd));return
        if not env.get('EXTENDED_KEYSTORE_PASSWORD'):
            env['EXTENDED_KEYSTORE_PASSWORD']=Path(cfg['password_file']).read_text().strip() if cfg.get('password_file') else getpass.getpass('Keystore password: ')
        if not env['EXTENDED_KEYSTORE_PASSWORD']:raise ValueError('Empty keystore password')
        completed=subprocess.run(cmd,env=env,cwd=ROOT)
        if completed.returncode:raise RuntimeError('Build failed. Inspect '+str(work)+'. No previous output was replaced.')
        result={'input':str(source),'output':str(out),'verification':str(out/'verification.json'),'work':str(work)}
        (ROOT/'latest-build.json').write_text(json.dumps(result,indent=2)+'\n')
        print('\nVerified APKs:',out,'\nBuild pointer: latest-build.json')
    except (ValueError,RuntimeError,KeyError,OSError) as e:p.exit(1,str(e)+'\n')

if __name__=='__main__':main()
