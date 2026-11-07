"""Inventory candidate fingerprint classes for an unsupported merged APK; no patch."""
import argparse,os,subprocess
from pathlib import Path
from tools import ROOT,ensure
p=argparse.ArgumentParser();p.add_argument('apk',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
tools=ensure();a.out.mkdir(parents=True,exist_ok=False)
exe='.exe' if os.name=='nt' else '';java=Path(os.environ['JAVA_HOME'])/'bin'
cp=os.pathsep.join(str(tools/n) for n in ['dexlib2.jar','guava.jar'])
subprocess.run([str(java/('javac'+exe)),'-cp',cp,'-d',str(a.out),str(ROOT/'patches/java/DiscoverCompatibility.java')],check=True)
with (a.out/'anchors.tsv').open('w',encoding='utf-8') as out:
    subprocess.run([str(java/('java'+exe)),'-Xmx1200m','-cp',cp+os.pathsep+str(a.out),'DiscoverCompatibility',str(a.apk.resolve())],stdout=out,check=True)
print('Candidate anchors saved. Review typed source paths before adding compatibility.json mapping:',a.out/'anchors.tsv')
