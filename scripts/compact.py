"""Validate the pinned LSPatch nested ZIP layout without disabling overlap checks.

Only origin.apk is read as a raw bounded range: Python zipfile rejects its
intentional containment of aliases. Every alias must match the original ZIP's
entry offset and metadata; all other entries retain normal zipfile validation.
"""
import hashlib,struct,zipfile,zlib
from tools import sha

ORIGIN='assets/lspatch/origin.apk'

def data_offset(file,entry):
    file.seek(entry.header_offset);header=file.read(30)
    if len(header)!=30 or header[:4]!=b'PK\x03\x04':raise ValueError('Invalid local ZIP header')
    name,extra=struct.unpack_from('<HH',header,26)
    return entry.header_offset+30+name+extra

def verify_nested(apk,origin):
    with zipfile.ZipFile(apk) as outer,zipfile.ZipFile(origin) as inner,open(apk,'rb') as file:
        entries=outer.infolist();names=[e.filename for e in entries]
        if len(names)!=len(set(names)):raise ValueError('Duplicate ZIP names')
        entry=outer.getinfo(ORIGIN)
        if entry.compress_type!=zipfile.ZIP_STORED or entry.file_size!=entry.compress_size:raise ValueError('Origin must be stored')
        start=data_offset(file,entry);end=start+entry.file_size
        if start%16384:raise ValueError('Nested origin is not 16 KiB aligned')
        if end>outer.start_dir:raise ValueError('Origin crosses central directory')
        digest=hashlib.sha256();crc=0;remaining=entry.file_size;file.seek(start)
        while remaining:
            chunk=file.read(min(1024*1024,remaining))
            if not chunk:raise ValueError('Truncated origin')
            digest.update(chunk);crc=zlib.crc32(chunk,crc);remaining-=len(chunk)
        if crc!=entry.CRC or digest.hexdigest()!=sha(origin):raise ValueError('Embedded origin CRC/hash mismatch')
        aliases=0
        for e in entries:
            if e.filename==ORIGIN:continue
            offset=data_offset(file,e)
            alignment=16384 if e.filename.endswith('.so') else 4
            if e.compress_type==zipfile.ZIP_STORED and offset%alignment:
                raise ValueError('Stored entry alignment mismatch: '+e.filename)
            if start<=e.header_offset<end:
                original=inner.getinfo(e.filename)
                if e.header_offset!=start+original.header_offset:raise ValueError('Unexpected nested alias offset: '+e.filename)
                if (e.CRC,e.file_size,e.compress_size,e.compress_type)!=(original.CRC,original.file_size,original.compress_size,original.compress_type):raise ValueError('Alias metadata mismatch')
                if offset+e.compress_size>end:raise ValueError('Alias exceeds origin')
                aliases+=1
            elif e.header_offset<end and offset+e.compress_size>start:
                raise ValueError('Unexpected overlap with origin')
            # Stream every normal entry through zipfile's CRC/overlap checks.
            with outer.open(e) as stream:
                while stream.read(1024*1024):pass
        return {'sharedEntries':aliases,'originSha256':digest.hexdigest(),'originOffset':start}
