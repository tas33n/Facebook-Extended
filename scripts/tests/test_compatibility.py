import importlib.util,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from compatibility import select_profile,kotlin_profile
spec=importlib.util.spec_from_file_location('extended_launcher',Path(__file__).resolve().parents[2]/'patch.py')
launcher=importlib.util.module_from_spec(spec);spec.loader.exec_module(launcher)

class CompatibilityTests(unittest.TestCase):
    def test_distinct_mapping_generated_for_586(self):
        text=kotlin_profile('586.0.0.52.53',select_profile('586.0.0.52.53'))
        self.assertIn('"X.061Y"',text);self.assertIn('"X.0CJE"',text)
        self.assertIn('"AH8"',text);self.assertNotIn('"X.05gS"',text)
    def test_unknown_version_never_inherits_previous_obfuscated_names(self):
        with self.assertRaises(RuntimeError):select_profile('999.0.0.0.0')
    def test_multiple_inputs_require_explicit_choice(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'one.apk').touch();(root/'two.apks').touch()
            with patch.object(launcher,'ROOT',root):
                with self.assertRaises(ValueError):launcher.choose_input()
                self.assertIn(launcher.choose_input(latest=True).name,['one.apk','two.apks'])
    def test_single_root_input_selected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'Facebook.apks').touch();(root/'artifacts').mkdir()
            (root/'artifacts/old.apk').touch()
            with patch.object(launcher,'ROOT',root):self.assertEqual(launcher.choose_input(),root/'Facebook.apks')

if __name__=='__main__':unittest.main()
