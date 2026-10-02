import importlib.util
from pathlib import Path
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('migrations',Path(__file__).with_name('check-migrations.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class MigrationManifestTest(unittest.TestCase):
    def test_v13_manifest_rejects_old_missing_duplicate_and_extra_versions(self):
        with tempfile.TemporaryDirectory() as folder:
            for i in range(1,14):Path(folder,f'V{i}__fixture.sql').write_text('select 1;')
            correct=','.join(str(i) for i in range(1,14))
            self.assertEqual(len(m.compare(folder,correct)),13)
            for invalid in [','.join(str(i) for i in range(1,10)),correct+',14',correct+',13',correct.replace('10,11','11,10')]:
                with self.assertRaises(ValueError):m.compare(folder,invalid)
    def test_numeric_order_and_duplicate_manifest(self):
        with tempfile.TemporaryDirectory() as folder:
            for n in [1,2,10]:Path(folder,f'V{n}__one.sql').write_text('select 1;')
            self.assertEqual(m.compare(folder,'1,2,10'),['1','2','10'])
            Path(folder,'V2__two.sql').write_text('select 2;')
            with self.assertRaises(ValueError):m.compare(folder,'1,2,10')

if __name__=='__main__':unittest.main()
