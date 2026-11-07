import io,struct,sys,tempfile,unittest,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from compact import verify_nested

def fixture(folder):
    origin=folder/'origin.apk'
    with zipfile.ZipFile(origin,'w') as z:
        entry=zipfile.ZipInfo('classes.dex');entry.extra=b'\0'*((-30-len(entry.filename))%4)
        z.writestr(entry,b'dex payload')
    apk=folder/'outer.apk'
    with zipfile.ZipFile(apk,'w') as z:
        entry=zipfile.ZipInfo('assets/lspatch/origin.apk')
        entry.extra=b'\0'*((-30-len(entry.filename))%16384)
        z.writestr(entry,origin.read_bytes())
        z.writestr('loader.dex',b'loader',compress_type=zipfile.ZIP_DEFLATED)
    # Append a central-directory alias into the nested stored ZIP, like LSPatch.
    data=bytearray(apk.read_bytes());eocd=len(data)-22
    cd_size,cd_offset=struct.unpack_from('<II',data,eocd+12)
    name_len,extra_len=struct.unpack_from('<HH',data,26);start=30+name_len+extra_len
    inner=origin.read_bytes();inner_eocd=len(inner)-22
    inner_size,inner_offset=struct.unpack_from('<II',inner,inner_eocd+12)
    alias=bytearray(inner[inner_offset:inner_offset+inner_size])
    struct.pack_into('<I',alias,42,start)
    end=bytearray(data[eocd:]);struct.pack_into('<HH',end,8,3,3);struct.pack_into('<I',end,12,cd_size+len(alias))
    apk.write_bytes(data[:eocd]+alias+end)
    return apk,origin,start

class CompactArchiveTests(unittest.TestCase):
    def test_nested_alias_validates_without_general_overlap_bypass(self):
        with tempfile.TemporaryDirectory() as temp:
            apk,origin,_=fixture(Path(temp))
            with zipfile.ZipFile(apk) as z:self.assertEqual(z.testzip(),'assets/lspatch/origin.apk')
            self.assertEqual(verify_nested(apk,origin)['sharedEntries'],1)
    def test_changed_origin_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            apk,origin,start=fixture(Path(temp));data=bytearray(apk.read_bytes());data[start+45]^=1;apk.write_bytes(data)
            with self.assertRaisesRegex(ValueError,'CRC/hash'):verify_nested(apk,origin)
    def test_unrelated_overlap_is_not_accepted_as_alias(self):
        with tempfile.TemporaryDirectory() as temp:
            apk,origin,start=fixture(Path(temp));data=bytearray(apk.read_bytes())
            pos=data.rfind(b'PK\x01\x02');struct.pack_into('<I',data,pos+42,start+1);apk.write_bytes(data)
            with self.assertRaises(ValueError):verify_nested(apk,origin)

    def test_mismatched_origin_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            apk,origin,_=fixture(Path(temp))
            wrong=Path(temp)/'wrong.apk'
            with zipfile.ZipFile(wrong,'w') as z:z.writestr('classes.dex',b'different')
            with self.assertRaisesRegex(ValueError,'CRC/hash'):verify_nested(apk,wrong)

if __name__=='__main__':unittest.main()
