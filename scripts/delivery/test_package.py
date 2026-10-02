import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
spec = importlib.util.spec_from_file_location('delivery_package', Path(__file__).with_name('package.py'))
m = importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class HostCompilerIdentityTest(unittest.TestCase):
    def compiler_fixture(self, folder):
        home = Path(folder)/'selected-jdk';(home/'bin').mkdir(parents=True)
        (home/'bin'/('javac.exe' if os.name == 'nt' else 'javac')).touch()
        return home

    def test_selected_jdk_is_used_by_compiler_and_maven_despite_unrelated_path(self):
        with tempfile.TemporaryDirectory() as folder:
            home = self.compiler_fixture(folder);maven = Path(folder)/'mvnw'
            responses = [subprocess.CompletedProcess([], 0, 'javac 21.0.8', ''),
                         subprocess.CompletedProcess([], 0, 'Apache Maven 3.9.11\nJava version: 21.0.8, vendor: Eclipse Adoptium', '')]
            with patch.dict(os.environ, {'JAVA_HOME': str(home), 'PATH': '/unrelated-jdk/bin'}):
                with patch.object(m, 'invoke', side_effect=responses) as invoke:
                    env, identity = m.host_toolchain(maven)
                    self.assertEqual(env['JAVA_HOME'], str(home))
                    self.assertTrue(env['PATH'].startswith(str(home/'bin') + os.pathsep))
                    self.assertEqual(invoke.call_args_list[0].args[0][0], str(home/'bin'/'javac'))
                    self.assertEqual(invoke.call_args_list[1].kwargs['env'], env)
                    self.assertIn('Java version: 21.0.8', identity['maven_version'])

    def test_configured_old_jdk_fails_before_maven_or_build(self):
        with tempfile.TemporaryDirectory() as folder:
            home = self.compiler_fixture(folder)
            with patch.dict(os.environ, {'JAVA_HOME': str(home)}):
                with patch.object(m, 'invoke', return_value=subprocess.CompletedProcess([], 0, 'javac 17.0.16', '')) as invoke:
                    with self.assertRaises(ValueError):m.host_toolchain(Path(folder)/'mvnw')
                    self.assertEqual(invoke.call_count, 1)

    def test_maven_runtime_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            home = self.compiler_fixture(folder)
            responses = [subprocess.CompletedProcess([], 0, 'javac 21.0.8', ''),
                         subprocess.CompletedProcess([], 0, 'Java version: 17.0.16, vendor: test', '')]
            with patch.dict(os.environ, {'JAVA_HOME': str(home)}), patch.object(m, 'invoke', side_effect=responses):
                with self.assertRaises(ValueError):m.host_toolchain(Path(folder)/'mvnw')

if __name__ == '__main__':unittest.main()
