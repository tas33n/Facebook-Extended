"""Fetch only pinned build tools and verify existing/downloaded bytes."""
import hashlib,json,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(path):
    with Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def ensure():
    folder=ROOT/'.tools';folder.mkdir(exist_ok=True)
    for name,item in json.loads((ROOT/'tools.lock.json').read_text()).items():
        path=folder/name
        if not path.exists():
            temp=path.with_suffix('.download');urllib.request.urlretrieve(item['url'],temp)
            if sha(temp)!=item['sha256']:raise RuntimeError('Tool hash mismatch: '+name)
            temp.replace(path)
        if sha(path)!=item['sha256']:raise RuntimeError('Cached tool hash mismatch: '+name)
    return folder
if __name__=='__main__':print(ensure())
