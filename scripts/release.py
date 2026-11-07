"""Publish explicitly selected local release artifacts using GitHub CLI."""
import argparse,json,subprocess
from pathlib import Path
from tools import sha
p=argparse.ArgumentParser();p.add_argument('--repo',default='tas33n/Extended');p.add_argument('--tag',required=True);p.add_argument('--dist',type=Path,required=True);p.add_argument('--notes',type=Path,required=True);p.add_argument('--draft',action='store_true');p.add_argument('--dry-run',action='store_true')
a=p.parse_args();report=json.loads((a.dist/'verification.json').read_text());assets=[]
for build in report['builds']:
    f=a.dist/build['path'].replace('\\','/').rsplit('/',1)[-1]
    if sha(f)!=build['sha256']:raise SystemExit('Release artifact hash mismatch: '+f.name)
    assets.append(str(f))
assets += [str(a.dist/'SHA256SUMS'),str(a.dist/'verification.json')]
if (a.dist/'runtime-observations.json').exists():assets.append(str(a.dist/'runtime-observations.json'))
cmd=['gh','release','create',a.tag,*assets,'--repo',a.repo,'--title','Extended '+a.tag,'--notes-file',str(a.notes),'--verify-tag']
if a.draft:cmd.append('--draft')
if a.dry_run:print(' '.join(cmd))
else:subprocess.run(cmd,check=True)
