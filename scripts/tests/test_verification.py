import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from verify import nodes,application_label_resource

class ManifestParsing(unittest.TestCase):
    def test_inline_application_label_is_rejected_even_with_activity_resource(self):
        text='''E: manifest
  E: application
    A: http://schemas.android.com/apk/res/android:label(0x01010001)="Facebook Extended"
    E: activity
      A: http://schemas.android.com/apk/res/android:label(0x01010001)=@0x7f1400c5
'''
        self.assertIsNone(application_label_resource(text))

    def test_application_reference_survives_launcher_branding(self):
        text='''E: manifest
  E: application
    A: http://schemas.android.com/apk/res/android:label(0x01010001)=@0x7f1400c5
    E: activity
      A: http://schemas.android.com/apk/res/android:label(0x01010001)="Extended Clone"
'''
        self.assertEqual(application_label_resource(text),0x7f1400c5)
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
