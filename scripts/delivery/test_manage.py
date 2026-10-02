import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('manage',Path(__file__).with_name('manage.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class DeliverySafetyTest(unittest.TestCase):
    def test_configuration_and_backups_refuse_overwrite(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'config').mkdir();env=root/'config/runtime.env'
            (root/'config/runtime.env.example').write_text('DB_PASSWORD=REPLACE_WITH_RANDOM_VALUE\nAI_VISITOR_COOKIE_SIGNING_KEY=REPLACE_WITH_64_HEX_CHARACTERS\nFRONTEND_PORT=8088\n')
            m.init(root,env,8089);before=env.read_bytes()
            with self.assertRaises(FileExistsError):m.init(root,env,8090)
            self.assertEqual(before,env.read_bytes())
            backup=root/'backup.sql';backup.write_bytes(b'original')
            with patch.object(m,'compose') as compose:
                with self.assertRaises(FileExistsError):m.backup(root,env,'source',backup)
                compose.assert_not_called()
            self.assertEqual(backup.read_bytes(),b'original')

    def test_binary_backup_is_preserved_and_failure_does_not_report_success(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);data=b'-- utf8 \xe4\xb9\xa6\n\xff\x00\n'
            def operation(*args,**kwargs):
                if 'stdout' in kwargs:kwargs['stdout'].write(data)
            with patch.object(m,'compose',side_effect=operation):m.backup(root,root/'env','source',root/'backup.sql')
            self.assertEqual(data,(root/'backup.sql').read_bytes())
            with patch.object(m,'compose',side_effect=RuntimeError('failed')):
                with self.assertRaises(RuntimeError):m.backup(root,root/'env','source',root/'bad.sql')

    def test_restore_rejects_existing_volume_without_writes(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);env=root/'source.env';target=root/'target.env';sql=root/'backup.sql'
            for p in [env,target,sql]:p.write_text('fixture')
            results=[subprocess.CompletedProcess([],0,stdout=''),subprocess.CompletedProcess([],0,stdout='existing-volume')]
            with patch.object(m,'invoke',side_effect=results),patch.object(m,'compose') as compose:
                with self.assertRaises(ValueError):m.restore(root,env,'source',sql,target,'restore')
                compose.assert_not_called()
            with self.assertRaises(ValueError):m.restore(root,env,'source',sql,target,'source')
            with self.assertRaises(ValueError):m.restore(root,env,'source',sql,env,'restore')

    def test_restore_import_failure_never_starts_application(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);env=root/'source.env';target=root/'target.env';sql=root/'backup.sql'
            for p in [env,target,sql]:p.write_text('fixture')
            with patch.object(m,'invoke',return_value=subprocess.CompletedProcess([],0,stdout='')):
                with patch.object(m,'compose',side_effect=[None,RuntimeError('SQL failed')]) as compose:
                    with self.assertRaises(RuntimeError):m.restore(root,env,'source',sql,target,'restore')
                    self.assertEqual(compose.call_count,2)

    def test_empty_backup_is_rejected_before_any_docker_call(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);env=root/'source.env';target=root/'target.env';sql=root/'empty.sql'
            env.write_text('source');target.write_text('target');sql.touch()
            with patch.object(m,'invoke') as invoke, patch.object(m,'compose') as compose:
                with self.assertRaises(ValueError):m.restore(root,env,'source',sql,target,'restore')
                invoke.assert_not_called();compose.assert_not_called()

    def test_explicit_file_configuration_is_not_overridden_by_shell_secrets(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);env=root/'runtime.env';env.write_text('DB_PASSWORD=file-fixture\nAI_API_KEY=file-model-fixture\n')
            with patch.dict(m.os.environ, {'DB_PASSWORD':'shell-fixture','AI_API_KEY':'shell-model-fixture'}):
                with patch.object(m,'invoke') as invoke:
                    m.compose(root,env,'source','config','--quiet')
                    inherited=invoke.call_args.kwargs['env']
                    self.assertNotIn('DB_PASSWORD',inherited)
                    self.assertNotIn('AI_API_KEY',inherited)

if __name__=='__main__':unittest.main()
