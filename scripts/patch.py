"""Input APK/APKS/directory -> verified main + clone single APKs. Never installs."""
import argparse,json,os,re,shutil,subprocess,zipfile,hashlib
from pathlib import Path
from tools import ROOT,ensure,sha

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('input',type=Path);p.add_argument('--variant',choices=['main','clone','both'],default='both')
    p.add_argument('--work',type=Path,required=True,help='New empty working directory')
    p.add_argument('--out',type=Path,required=True)
    p.add_argument('--keystore',type=Path,required=True);p.add_argument('--alias',required=True)
    p.add_argument('--password-env',default='EXTENDED_KEYSTORE_PASSWORD')
    p.add_argument('--sdk',type=Path,default=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT',''))))
    p.add_argument('--build-tools',default='36.0.0');p.add_argument('--allow-untested-version',action='store_true',help='Deprecated: cannot bypass a missing or invalid compatibility mapping')
    a=p.parse_args();a.input=a.input.resolve();a.work=a.work.resolve();a.out=a.out.resolve();a.keystore=a.keystore.resolve()
    if a.work.exists():p.error('--work must not exist; use a fresh run directory')
    password=os.environ.get(a.password_env)
    if not password:p.error('Set '+a.password_env+' (password is never printed)')
    tools=ensure();a.work.mkdir(parents=True);a.out.mkdir(parents=True,exist_ok=True)
    java_home=Path(os.environ['JAVA_HOME']);exe='.exe' if os.name=='nt' else ''
    java=java_home/'bin'/('java'+exe);javac=java_home/'bin'/('javac'+exe);jar=java_home/'bin'/('jar'+exe)
    sdk=a.sdk.resolve();bt=sdk/'build-tools'/a.build_tools;android=sdk/'platforms/android-36/android.jar'
    def run(args,log,cwd=None):
        print('Stage:',log,flush=True)
        r=subprocess.run([str(x) for x in args],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        (a.work/log).write_bytes(r.stdout)
        if r.returncode:raise RuntimeError(str(log)+' failed (see log; command credentials not printed)')
        return r.stdout.decode('utf-8',errors='replace')
    def sign(path,log):
        run([java,'-jar',bt/'lib/apksigner.jar','sign','--ks',a.keystore,'--ks-key-alias',a.alias,'--ks-pass','env:'+a.password_env,'--min-sdk-version','30','--alignment-preserved','true','--v4-signing-enabled','false',path],log)
    input_dir=a.work/'input';input_dir.mkdir();identities=[]
    if a.input.is_dir():
        for src in sorted(a.input.glob('*.apk')):
            shutil.copy2(src,input_dir/src.name);identities.append({'path':str(src),'sha256':sha(src)})
        selected=input_dir
    else:
        selected=input_dir/a.input.name;shutil.copy2(a.input,selected);identities=[{'path':str(a.input),'sha256':sha(a.input)}]
    if not identities:raise RuntimeError('No input APKs')
    merged=a.work/'merged.apk'
    if selected.is_file() and selected.suffix.lower()=='.apk':shutil.copy2(selected,merged)
    else:run([java,'-Xmx1536m','-jar',tools/'apkeditor.jar','m','-i',selected,'-o',merged,'-validate-modules'],'merge.log')
    with zipfile.ZipFile(merged) as z:
        if 'assets/lspatch/config.json' in z.namelist():raise RuntimeError('Use original/unpatched input, not an embedded build')
        assert z.testzip() is None
    badging=run([bt/('aapt2'+exe),'dump','badging',merged],'input-badging.txt')
    if "name='com.facebook.katana'" not in badging.splitlines()[0]:raise RuntimeError('Expected Facebook main package')
    version=re.search(r"versionName='([^']+)'",badging).group(1)
    from compatibility import select_profile,kotlin_profile
    profile=select_profile(version)
    tested=profile['status']=='device-tested-bounded'
    manifest=run([bt/('aapt2'+exe),'dump','xmltree','--file','AndroidManifest.xml',merged],'input-manifest.txt')
    for marker in ['requiredSplitTypes(', 'isSplitRequired(', 'E: uses-split']:
        if marker in manifest:raise RuntimeError('Input still needs splits; provide complete APKS or split directory')
    classes=a.work/'java';classes.mkdir()
    cp=os.pathsep.join(str(tools/n) for n in ['dexlib2.jar','guava.jar','apkeditor.jar'])
    run([javac,'-cp',cp,'-d',classes,ROOT/'patches/java/CloneFacebook.java',ROOT/'patches/java/VerifyCloneDex.java',ROOT/'patches/java/AddRestartManifest.java',ROOT/'patches/java/ValidateCompatibility.java'],'java-compile.log')
    run([javac,'-cp',tools/'lspatch.jar','-d',classes,ROOT/'patches/java/CompactPatch.java'],'compact-compile.log')
    mapping_args=[profile[k] for k in ['playerClass','headerClass','headerModelField','lowResField','modelGetter','imageHelperClass','imageHelperMethod']]+[','.join(profile['coverPath'])]
    run([java,'-Xmx1200m','-cp',cp+os.pathsep+str(classes),'ValidateCompatibility',merged,*mapping_args],'compatibility-check.txt')
    helper=a.work/'helper';helper.mkdir();helper_dex=a.work/'helper-dex';helper_dex.mkdir()
    run([javac,'-source','8','-target','8','-cp',android,'-d',helper,ROOT/'patches/java/RestartActivity.java'],'helper-compile.log')
    run([jar,'cf',a.work/'helper.jar','-C',helper,'.'],'helper-jar.log')
    run([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','30','--output',helper_dex,a.work/'helper.jar'],'helper-d8.log')
    records=[]
    variants=['main','clone'] if a.variant=='both' else [a.variant]
    for variant in variants:
        v=a.work/variant;v.mkdir();patches={};target='com.facebook.katana' if variant=='main' else 'com.tas33n.facebook.extended'
        if variant=='clone':
            rewritten=v/'rewritten'
            run([java,'-Xmx1536m','-cp',cp+os.pathsep+str(classes),'CloneFacebook',merged,rewritten],variant+'/clone.log')
            patches={f.relative_to(rewritten).as_posix():f.read_bytes() for f in rewritten.rglob('*') if f.suffix in ['.dex','.xml']}
            with zipfile.ZipFile(merged) as z:
                metadata='assets/secondary-program-dex-jars/metadata.txt';text=z.read(metadata).decode()
                for name,data in patches.items():
                    if re.fullmatch(r'classes\d*\.dex',name):
                        original=v/'compare.dex';original.write_bytes(z.read(name))
                        run([java,'-Xmx1024m','-cp',cp+os.pathsep+str(classes),'VerifyCloneDex',original,rewritten/name,rewritten/'identity-map.tsv'],variant+'/'+name+'-semantics.txt')
                        if name!='classes.dex':
                            text,count=re.subn(r'(?m)^'+re.escape(name)+r' [a-f0-9]{40} ',name+' '+hashlib.sha1(data).hexdigest()+' ',text)
                            if count!=1:raise RuntimeError('Secondary DEX metadata format changed')
                patches[metadata]=text.encode()
        stage=v/'stage.apk'
        with zipfile.ZipFile(merged) as z,zipfile.ZipFile(stage,'w') as out:
            for item in z.infolist():
                if item.filename.startswith('META-INF/') or item.filename=='stamp-cert-sha256':continue
                out.writestr(item,patches.get(item.filename,z.read(item.filename)))
        run([java,'-cp',cp+os.pathsep+str(classes),'AddRestartManifest',stage,v/'AndroidManifest.xml'],variant+'/manifest.log')
        origin=v/'origin.apk'
        with zipfile.ZipFile(stage) as z,zipfile.ZipFile(origin,'w') as out:
            nums=[int(re.match(r'classes(\d*)\.dex',n)[1] or '1') for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n)]
            for item in z.infolist():out.writestr(item,(v/'AndroidManifest.xml').read_bytes() if item.filename=='AndroidManifest.xml' else z.read(item.filename))
            out.write(helper_dex/'classes.dex','classes'+str(max(nums)+1)+'.dex',compress_type=zipfile.ZIP_STORED)
        aligned_origin=v/'origin-aligned.apk'
        run([bt/('zipalign'+exe),'-P','16','4',origin,aligned_origin],variant+'/origin-align.log')
        origin=aligned_origin
        sign(origin,variant+'/origin-sign.log')
        module_tree=v/'module';shutil.copytree(ROOT/'module',module_tree,ignore=shutil.ignore_patterns('build','.gradle','local.properties'))
        (module_tree/'app/src/main/resources/META-INF/xposed/scope.list').write_text(target+'\n')
        (module_tree/'app/src/main/java/tn/loukious/facebookappadsremover/core/HostCompatibility.kt').write_text(kotlin_profile(version,profile),encoding='utf-8')
        gradle=module_tree/('gradlew.bat' if os.name=='nt' else 'gradlew')
        if os.name!='nt':gradle.chmod(0o755)
        run([gradle,':app:assembleRelease',':app:testReleaseUnitTest','--no-daemon','--max-workers=2','-PtargetPackage='+target,'-Dorg.gradle.jvmargs=-Xmx1200m -Dfile.encoding=UTF-8','-Pkotlin.compiler.execution.strategy=in-process'],variant+'/module-build.log',module_tree)
        unsigned=list((module_tree/'app/build/outputs/apk/release').glob('*-unsigned.apk'));assert len(unsigned)==1
        module=v/'module.apk';run([bt/('zipalign'+exe),'-P','16','4',unsigned[0],module],variant+'/module-align.log');sign(module,variant+'/module-sign.log')
        patched=v/'patched'
        run([java,'-Xmx1536m','-cp',str(tools/'lspatch.jar')+os.pathsep+str(classes),'CompactPatch','-m',module,'-k',a.keystore,password,a.alias,password,'-l','2','--injectdex','-o',patched,origin],variant+'/lspatch.log')
        outputs=list(patched.glob('*.apk'));assert len(outputs)==1
        final=a.out/('Extended-'+version+'-'+variant+'.apk')
        if final.exists():raise RuntimeError('Output exists; choose a new --out')
        # LSPatch has already signed the aligned, shared-entry archive. Rewriting
        # it through zipalign/apksigner would duplicate the nested payload.
        shutil.copy2(outputs[0],final)
        from verify import verify
        record=verify(final,origin,module,merged,target,bt,java,v)
        record.update(variant=variant,version=version,experimental_version=not tested,runtimeTested=False)
        records.append(record)
    for item in identities:assert sha(item['path'])==item['sha256'],'Input modified'
    report={'inputs':identities,'builds':records,'inputUnchanged':True,'compatibility':{'version':version,'profile':profile,'staticValidation':'pass','runtimeTestedByThisCommand':False}}
    (a.out/'verification.json').write_text(json.dumps(report,indent=2))
    (a.out/'SHA256SUMS').write_text(''.join(x['sha256']+'  '+Path(x['path']).name+'\n' for x in records))
    print(json.dumps(report,indent=2))
if __name__=='__main__':main()
