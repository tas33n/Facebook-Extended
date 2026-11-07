"""Independent final package/identity checks, used by patch.py and release tooling."""
import json,re,subprocess,zipfile,hashlib,os
from pathlib import Path
from tools import sha
from compact import verify_nested
def nodes(text):
    result=[];current=None
    for line in text.splitlines():
        if 'E: ' in line:current={'tag':line.split('E: ')[1].split()[0],'attrs':{}};result.append(current)
        elif current is not None:
            m=re.search(r'A: (?:[^\s]+:)?([\w]+)(?:\([^)]*\))?="([^"]*)"',line)
            if m:current['attrs'][m[1]]=m[2]
    return result
def verify(final,origin,module,baseline,target,bt,java,logs):
    exe='.exe' if os.name=='nt' else ''
    def run(args,name):
        r=subprocess.run([str(a) for a in args],capture_output=True);text=(r.stdout+r.stderr).decode('utf-8',errors='replace')
        (logs/name).write_text(text,encoding='utf-8');assert r.returncode==0,name
        return text
    sig=run([java,'-jar',bt/'lib/apksigner.jar','verify','--min-sdk-version','30','--verbose','--print-certs',final],'signature.txt')
    run([bt/('zipalign'+exe),'-c','-P','16','4',final],'alignment.txt')
    badging=run([bt/('aapt2'+exe),'dump','badging',final],'badging.txt');assert "name='"+target+"'" in badging.splitlines()[0]
    text=run([bt/('aapt2'+exe),'dump','xmltree','--file','AndroidManifest.xml',final],'manifest.txt')
    for marker in ['requiredSplitTypes(', 'isSplitRequired(', 'E: uses-split','A: split=']:assert marker not in text,marker
    original=nodes(run([bt/('aapt2'+exe),'dump','xmltree','--file','AndroidManifest.xml',baseline],'baseline-manifest.txt'))
    final_nodes=nodes(text)
    def authorities(ns):return {a.strip() for n in ns if n['tag']=='provider' for a in n['attrs'].get('authorities','').split(';') if a.strip()}
    def permissions(ns):return {n['attrs']['name'] for n in ns if n['tag']=='permission'}
    if target!='com.facebook.katana':
        assert not authorities(original)&authorities(final_nodes),'Provider collision'
        assert not permissions(original)&permissions(final_nodes),'Permission collision'
        assert len(authorities(original))==len(authorities(final_nodes))
        assert not any('sharedUserId' in n['attrs'] for n in final_nodes)
    helper=next(n for n in final_nodes if n['tag']=='activity' and n['attrs'].get('name')=='tn.loukious.fbar.embedded.RestartActivity')
    assert helper['attrs']['process']==':fbar_restart'
    nested=verify_nested(final,origin)
    with zipfile.ZipFile(final) as z,zipfile.ZipFile(origin) as a,zipfile.ZipFile(module) as m:
        assert hashlib.sha256(z.read('assets/lspatch/modules/tn.loukious.facebookappadsremover.apk')).hexdigest()==sha(module)
        assert m.read('META-INF/xposed/scope.list').decode().strip()==target
        cfg=json.loads(z.read('assets/lspatch/config.json'));assert not cfg['useManager'] and cfg['injectDex']
        assert cfg['appComponentFactory']=='com.facebook.common.appcomponentfactory.fb4a.Fb4aAppComponentFactory'
        for name in a.namelist():
            if name.startswith('META-INF/') or name in ['AndroidManifest.xml','stamp-cert-sha256']:continue
            assert a.read(name)==z.read(name),name
        for line in z.read('assets/secondary-program-dex-jars/metadata.txt').decode().splitlines():
            parts=line.split()
            if parts and parts[0].endswith('.dex'):assert hashlib.sha1(z.read(parts[0])).hexdigest()==parts[1]
    return {'path':str(final),'sha256':sha(final),'bytes':final.stat().st_size,'package':target,
            'signer_sha256':sorted(set(re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)',sig))),
            'packaging':nested,
            'checks':{'signature':'pass','alignment16k':'pass','CRC':'pass','embeddedPayload':'pass','scope':'pass','secondaryDexDigests':'pass','cloneConflicts':'pass' if target!='com.facebook.katana' else 'not-applicable'}}
