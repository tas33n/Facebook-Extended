"""CI selection/build/publication. Never upload original inputs or signing files."""
import argparse,base64,json,os,re,shutil,subprocess,sys,urllib.parse,urllib.request,zipfile
from pathlib import Path
from tools import ROOT,sha

MAX_INPUT_BYTES=2*1024**3

def git(*args):
    return subprocess.check_output(['git',*args],cwd=ROOT).decode('utf-8')

def root_input_name(name):
    return (isinstance(name,str) and name not in ('','.','..') and '/' not in name and '\\' not in name
            and not any(ord(c)<32 for c in name) and Path(name).suffix in ('.apk','.apks'))

def changed_inputs(before,after):
    if not re.fullmatch(r'[0-9a-f]{40,64}',after or ''):raise ValueError('Invalid event commit')
    if before and set(before)!={'0'}:
        if not re.fullmatch(r'[0-9a-f]{40,64}',before):raise ValueError('Invalid previous commit')
        names=git('diff','--no-renames','--name-only','-z','--diff-filter=AMT',before,after,'--').split('\0')
    else:names=git('ls-tree','-r','--name-only','-z',after).split('\0')
    return sorted({n for n in names if root_input_name(n)})

def validate_https(url):
    u=urllib.parse.urlsplit(url)
    if u.scheme!='https' or not u.hostname or u.username or u.password:
        raise ValueError('Use an HTTPS URL without embedded credentials')
    return u

class HTTPSOnlyRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        validate_https(newurl)
        return super().redirect_request(req,fp,code,msg,headers,newurl)

def fetch_input(url,expected,folder):
    u=validate_https(url)
    if not re.fullmatch(r'[0-9a-fA-F]{64}',expected):raise ValueError('URL input requires exact input_sha256')
    suffix=Path(urllib.parse.unquote(u.path)).suffix.lower()
    if suffix not in ('.apk','.apks'):raise ValueError('URL path must end in .apk or .apks')
    folder.mkdir(parents=True,exist_ok=True);dest=folder/('original'+suffix)
    opener=urllib.request.build_opener(HTTPSOnlyRedirect())
    try:
        with opener.open(url,timeout=120) as response,dest.open('wb') as out:
            total=0
            while True:
                chunk=response.read(1024*1024)
                if not chunk:break
                total+=len(chunk)
                if total>MAX_INPUT_BYTES:raise ValueError('Input exceeds 2 GiB')
                out.write(chunk)
    except Exception:
        dest.unlink(missing_ok=True)
        raise RuntimeError('Input download failed; check URL/access/size (URL omitted from logs)') from None
    if sha(dest)!=expected.lower():
        dest.unlink();raise ValueError('Downloaded input SHA-256 mismatch')
    return dest

def select(root):
    event=json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    kind=os.environ['GITHUB_EVENT_NAME'];selected=[]
    if kind=='push':
        if event.get('ref')!='refs/heads/main':raise ValueError('Only main may publish')
        selected=[ROOT/n for n in changed_inputs(event.get('before'),event.get('after'))]
    elif kind=='workflow_dispatch':
        name=os.environ.get('INPUT_FILE','').strip();url=os.environ.get('INPUT_URL','').strip()
        if bool(name)==bool(url):raise ValueError('Choose exactly one: input_file OR input_url')
        if name:
            if not root_input_name(name):raise ValueError('input_file must be a root APK/APKS filename')
            selected=[ROOT/name]
        else:selected=[fetch_input(url,os.environ.get('INPUT_SHA256','').strip(),root/'download')]
    else:raise ValueError('Unsupported event')
    rows=[]
    for path in selected:
        if path.is_symlink() or not path.is_file():raise ValueError('Missing or symlinked input: '+path.name)
        with path.open('rb') as f:prefix=f.read(100)
        if prefix.startswith(b'version https://git-lfs.github.com/spec/v1'):
            raise ValueError('Git LFS pointer was not downloaded: '+path.name)
        if not zipfile.is_zipfile(path):raise ValueError('Not an APK/APKS ZIP: '+path.name)
        rows.append({'path':str(path.resolve()),'name':path.name,'sha256':sha(path)})
    (root/'inputs.json').write_text(json.dumps(rows,indent=2))
    with Path(os.environ['GITHUB_OUTPUT']).open('a') as f:f.write('has_inputs='+str(bool(rows)).lower()+'\n')
    print('Selected',len(rows),'original input(s)')
    for row in rows:print(row['name'],row['sha256'])

def signing(root):
    required=['EXTENDED_KEYSTORE_BASE64','EXTENDED_KEYSTORE_PASSWORD','EXTENDED_KEY_ALIAS']
    if any(not os.environ.get(k) for k in required):raise ValueError('Configure all three signing secrets: '+', '.join(required))
    try:data=base64.b64decode(''.join(os.environ[required[0]].split()),validate=True)
    except Exception:raise ValueError('Signing key secret is not valid base64') from None
    if not data:raise ValueError('Signing key is empty')
    key=root/'signing.p12';key.write_bytes(data);key.chmod(0o600)
    java=Path(os.environ['JAVA_HOME'])/'bin'/'keytool'
    result=subprocess.run([str(java),'-list','-keystore',str(key),'-alias',os.environ['EXTENDED_KEY_ALIAS'],
        '-storepass:env','EXTENDED_KEYSTORE_PASSWORD'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if result.returncode:raise ValueError('Signing key, alias or password did not validate')
    print('Signing key validated')

def safe_version(version):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,79}',version):raise ValueError('Unexpected APK version name')
    return version

def prepare_assets(dist,publish,input_row):
    report=json.loads((dist/'verification.json').read_text())
    builds=report.get('builds',[])
    if sorted(b.get('variant') for b in builds)!=['clone','main']:raise ValueError('Both verified variants required')
    versions={b['version'] for b in builds}
    if len(versions)!=1:raise ValueError('Variant versions differ')
    version=safe_version(versions.pop());publish.mkdir(parents=True,exist_ok=False)
    public=json.loads(json.dumps(report))
    public['inputs']=[{'name':input_row['name'],'sha256':input_row['sha256']}]
    for b in public['builds']:
        name=b['path'].replace('\\','/').rsplit('/',1)[-1];source=dist/name
        if sha(source)!=b['sha256']:raise ValueError('Output hash mismatch: '+name)
        checks=b.get('checks',{})
        if not checks or any(v not in ('pass','not-applicable') for v in checks.values()):raise ValueError('Output checks incomplete')
        asset='Facebook-Extended-'+b['variant']+'.apk'
        shutil.copy2(source,publish/asset);b['path']=asset
    (publish/'verification.json').write_text(json.dumps(public,indent=2)+'\n')
    (publish/'SHA256SUMS').write_text(''.join(b['sha256']+'  '+b['path']+'\n' for b in public['builds']))
    return version,public

def release_notes(version,report,input_row,commit,changes):
    lines=['# Facebook Extended — Facebook '+version,'',
        '## Downloads','- **Main:** Facebook-Extended-main.apk — same package as stock Facebook; different signer.',
        '- **Clone:** Facebook-Extended-clone.apk — separate package for side-by-side use.','',
        '## Changes in this build',changes.strip() or '- Build the supplied Facebook update with the current Extended patches.','',
        '## Build identity','- Facebook version: `'+version+'`','- Source commit: `'+commit+'`',
        '- Original input SHA-256: `'+input_row['sha256']+'`','', '## Verification',
        'Both variants passed the patch pipeline, module tests, signatures, ZIP alignment/CRC and embedding/scope checks. '
        'Clone namespace checks also passed. See verification.json and SHA256SUMS.',
        '**This GitHub Actions build was not tested on a phone.** Existing version observations do not establish every device/account feature.',
        '', '## Installation','Android 11+ / ARM64 for the currently supported input variants. Open the APK normally. '
        'Main cannot update vendor-signed stock Facebook. Reuse the same signing key for future updates. '
        'Exclude embedded apps from rooted Vector/LSPosed injection.','',
        '## Credits','@tas33n · Loukious’s [Facebook App Ads Remover](https://github.com/Xposed-Modules-Repo/tn.loukious.facebookappadsremover) '
        '· [JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch) and Vector/LSPlant.','',
        'VirusTotal links are pending unless linked separately. Scan results are not a safety guarantee.']
    if any(b.get('experimental_version') for b in report['builds']):lines+=['','**Experimental mapping:** static review only; inspect compatibility status before use.']
    return '\n'.join(lines)+'\n'

def build(root):
    rows=json.loads((root/'inputs.json').read_text());completed=[]
    # Build every selected input before publishing any, so one failure cannot publish half a batch.
    for i,row in enumerate(rows):
        work=root/('work-'+str(i));dist=root/('dist-'+str(i))
        result=subprocess.run([sys.executable,str(ROOT/'scripts/patch.py'),row['path'],'--variant','both',
            '--work',str(work),'--out',str(dist),'--keystore',str(root/'signing.p12'),
            '--alias',os.environ['EXTENDED_KEY_ALIAS']],cwd=ROOT)
        if result.returncode:raise RuntimeError('Patch pipeline failed; no release published')
        publish=root/('publish-'+str(i));version,report=prepare_assets(dist,publish,row)
        completed.append({'directory':str(publish),'version':version,'input':row})
        # Work is disposable and contains large redundant APKs; remove only our completed CI work.
        shutil.rmtree(work);shutil.rmtree(dist)
    (root/'releases.json').write_text(json.dumps(completed,indent=2))

def publish(root):
    repo=os.environ['GITHUB_REPOSITORY'];commit=os.environ['GITHUB_SHA'];run_id=os.environ['GITHUB_RUN_ID'];attempt=os.environ.get('GITHUB_RUN_ATTEMPT','1')
    if not re.fullmatch(r'[\w.-]+/[\w.-]+',repo):raise ValueError('Unexpected repository')
    if not run_id.isdigit() or not attempt.isdigit():raise ValueError('Unexpected run identity')
    tag_list=subprocess.run(['gh','release','list','--repo',repo,'--exclude-drafts','--limit','1','--json','tagName'],capture_output=True,text=True)
    if tag_list.returncode:raise RuntimeError('Unable to read repository releases; check workflow permissions')
    tags=json.loads(tag_list.stdout)
    previous=tags[0]['tagName'] if tags else None
    try:changes=git('log','--format=- %s',((previous+'..'+commit) if previous else commit),'-15','--','.')
    except subprocess.CalledProcessError:changes=git('log','-1','--format=- %s',commit)
    for i,row in enumerate(json.loads((root/'releases.json').read_text())):
        folder=Path(row['directory']);report=json.loads((folder/'verification.json').read_text())
        tag='facebook-'+row['version']+'-build-'+run_id+'-'+attempt+'-'+str(i+1)
        notes=folder/'release-notes.md';notes.write_text(release_notes(row['version'],report,row['input'],commit,changes),encoding='utf-8')
        for b in report['builds']:
            if sha(folder/b['path'])!=b['sha256']:raise ValueError('Release asset changed')
        assets=[folder/'Facebook-Extended-main.apk',folder/'Facebook-Extended-clone.apk',folder/'SHA256SUMS',folder/'verification.json']
        # Draft first; failed asset upload cannot replace the latest working public release.
        subprocess.run(['gh','release','create',tag,*map(str,assets),'--repo',repo,'--target',commit,
            '--title','Facebook Extended · '+row['version'],'--notes-file',str(notes),'--draft'],check=True)
        subprocess.run(['gh','release','edit',tag,'--repo',repo,'--draft=false','--latest'],check=True)
        summary=os.environ.get('GITHUB_STEP_SUMMARY')
        if summary:
            with Path(summary).open('a') as f:f.write('Published ['+tag+'](https://github.com/'+repo+'/releases/tag/'+tag+')\n\n')

def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['select','signing','build','publish','cleanup']);p.add_argument('--root',type=Path,required=True);a=p.parse_args()
    root=a.root.resolve();root.mkdir(parents=True,exist_ok=True)
    try:
        if a.command=='cleanup':(root/'signing.p12').unlink(missing_ok=True)
        else:globals()[a.command](root)
    except Exception as e:p.exit(1,str(e)+'\n')

if __name__=='__main__':main()
