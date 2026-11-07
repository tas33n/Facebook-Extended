"""Record a user-supplied VirusTotal URL against exact file bytes; no upload."""
import argparse,json,re
from datetime import datetime,timezone
from pathlib import Path
from tools import ROOT,sha
p=argparse.ArgumentParser();p.add_argument('apk',type=Path);p.add_argument('url');p.add_argument('--summary',default='User-supplied scan link; engine results not independently reviewed')
a=p.parse_args();digest=sha(a.apk)
if not re.fullmatch(r'https://(?:www\.)?virustotal\.com/gui/file/'+digest+r'(?:/[^\s]*)?',a.url):p.error('URL must be a VirusTotal file report matching this APK SHA-256')
path=ROOT/'docs/scans.json';data=json.loads(path.read_text());data['reports'].append({'file':a.apk.name,'sha256':digest,'url':a.url,'recorded_at':datetime.now(timezone.utc).isoformat(),'summary':a.summary});data['status']='links-provided';path.write_text(json.dumps(data,indent=2)+'\n')
print('Recorded matching hash; no file uploaded and no safety verdict inferred.')
