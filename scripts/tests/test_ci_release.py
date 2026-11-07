import json,sys,tempfile,unittest,zipfile
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import ci_release as ci
from tools import sha

class CIReleaseTests(unittest.TestCase):
    def test_only_root_apk_inputs_are_selected(self):
        with patch.object(ci,'git',return_value='Facebook.apks\0nested/old.apk\0README.md\0Other.apk\0') as git:
            self.assertEqual(ci.changed_inputs('a'*40,'b'*40),['Facebook.apks','Other.apk'])
            self.assertIn('--diff-filter=AMT',git.call_args.args)
            self.assertIn('--no-renames',git.call_args.args)
        for name in ['../x.apk','nested/x.apk','nested\\x.apk','x.apk\n','file.txt']:
            self.assertFalse(ci.root_input_name(name))

    def test_first_push_and_deleted_only_have_distinct_selection(self):
        with patch.object(ci,'git',return_value='original.apks\0') as git:
            self.assertEqual(ci.changed_inputs('0'*40,'b'*40),['original.apks'])
            self.assertEqual(git.call_args.args[0],'ls-tree')
        with patch.object(ci,'git',return_value=''):
            self.assertEqual(ci.changed_inputs('a'*40,'b'*40),[])

    def test_url_requires_https_without_credentials(self):
        for url in ['http://example.org/a.apk','file:///a.apk','https://user:pass@example.org/a.apk']:
            with self.assertRaises(ValueError):ci.validate_https(url)
        self.assertEqual(ci.validate_https('https://github.com/a/b/releases/download/v1/input.apks').scheme,'https')

    def test_stable_assets_match_hash_and_exclude_private_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);dist=root/'dist';dist.mkdir();builds=[]
            for variant in ['main','clone']:
                f=dist/('Extended-586-'+variant+'.apk');f.write_bytes(variant.encode())
                builds.append({'variant':variant,'version':'586.0.0.52.53','path':'C:\\private\\'+f.name,
                    'sha256':sha(f),'checks':{'signature':'pass'}})
            (dist/'verification.json').write_text(json.dumps({'inputs':[{'path':'/private/input.apk'}],'builds':builds}))
            version,report=ci.prepare_assets(dist,root/'publish',{'name':'original.apks','sha256':'a'*64})
            self.assertEqual(version,'586.0.0.52.53')
            self.assertEqual({x['path'] for x in report['builds']},{'Facebook-Extended-main.apk','Facebook-Extended-clone.apk'})
            self.assertNotIn('private',json.dumps(report))
            (dist/Path(builds[0]['path'].replace('\\','/')).name).write_bytes(b'changed')
            with self.assertRaises(ValueError):ci.prepare_assets(dist,root/'bad',{'name':'original.apks','sha256':'a'*64})

    def test_notes_use_actual_version_and_no_runtime_claim(self):
        notes=ci.release_notes('586.0.0.52.53',{'builds':[]},{'sha256':'a'*64},'b'*40,'- Fix video picker')
        self.assertIn('Fix video picker',notes)
        self.assertIn('not tested on a phone',notes)
        self.assertIn('Facebook-Extended-clone.apk',notes)

    def test_pointer_is_rejected_before_building(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);repo=root/'repo';repo.mkdir()
            (repo/'Facebook.apks').write_text('version https://git-lfs.github.com/spec/v1\noid sha256:abc\n')
            event=root/'event.json';event.write_text('{}')
            env={'GITHUB_EVENT_PATH':str(event),'GITHUB_EVENT_NAME':'workflow_dispatch',
                 'INPUT_FILE':'Facebook.apks','INPUT_URL':'','GITHUB_OUTPUT':str(root/'output')}
            with patch.object(ci,'ROOT',repo),patch.dict(ci.os.environ,env):
                with self.assertRaisesRegex(ValueError,'LFS pointer'):ci.select(root)

    def test_empty_push_marks_no_work(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);event=root/'event.json'
            event.write_text(json.dumps({'ref':'refs/heads/main','before':'a'*40,'after':'b'*40}))
            env={'GITHUB_EVENT_PATH':str(event),'GITHUB_EVENT_NAME':'push','GITHUB_OUTPUT':str(root/'output')}
            with patch.dict(ci.os.environ,env),patch.object(ci,'changed_inputs',return_value=[]):ci.select(root)
            self.assertEqual(json.loads((root/'inputs.json').read_text()),[])
            self.assertIn('has_inputs=false',(root/'output').read_text())

if __name__=='__main__':unittest.main()
