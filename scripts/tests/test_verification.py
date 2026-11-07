import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from verify import nodes

class ManifestParsing(unittest.TestCase):
    def test_namespaced_and_root_attributes_keep_component_identity(self):
        parsed=nodes('''E: manifest (line=1)
  A: package="com.tas33n.facebook.extended"
  E: provider (line=2)
    A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.facebook.Provider"
    A: http://schemas.android.com/apk/res/android:authorities(0x01010018)="one; two"
  E: permission (line=3)
    A: http://schemas.android.com/apk/res/android:name(0x01010003)="private.permission"
''')
        self.assertEqual(parsed[0]['attrs']['package'],'com.tas33n.facebook.extended')
        self.assertEqual(parsed[1]['attrs']['name'],'com.facebook.Provider')
        self.assertEqual(parsed[1]['attrs']['authorities'],'one; two')
        self.assertEqual(parsed[2]['attrs']['name'],'private.permission')

if __name__=='__main__':unittest.main()
