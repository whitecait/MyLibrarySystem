import os
from pathlib import Path
import tempfile
import unittest
from formal.verify import run_case, verdict, tool_jar

MODEL = '''---- MODULE GateFixture ----
EXTENDS Naturals
VARIABLE n
Init == n = 0
Next == /\\ n < 1
        /\\ n' = n + 1
Good == n \\in 0..1
Bad == n = 0
ProbeNotOne == n # 1
ProbeNotTwo == n # 2
====
'''

class RealTlcGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.jar = tool_jar(os.environ.get('TLA_TOOLS_JAR'))

    def execute(self, prop, kind, expected=None, source=MODEL, timeout=30):
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder);model=base/'GateFixture.tla';model.write_text(source)
            config=base/'GateFixture.cfg';config.write_text('INIT Init\nNEXT Next\nINVARIANT '+prop+'\n')
            return run_case(self.jar,model,config,base/'cases/run',kind,expected,timeout)

    def test_valid_invariant_finishes(self):
        self.assertTrue(self.execute('Good','safety')['passed'])

    def test_actual_invariant_violation_blocks(self):
        self.assertFalse(self.execute('Bad','safety')['passed'])

    def test_designated_reachable_probe_passes(self):
        self.assertTrue(self.execute('ProbeNotOne','reachability','ProbeNotOne')['passed'])

    def test_unreachable_probe_blocks(self):
        self.assertFalse(self.execute('ProbeNotTwo','reachability','ProbeNotTwo')['passed'])

    def test_other_property_violation_blocks(self):
        self.assertFalse(self.execute('Bad','reachability','ProbeNotOne')['passed'])

    def test_syntax_error_blocks(self):
        self.assertFalse(self.execute('Good','safety',source=MODEL.replace('n = 0','n = )',1))['passed'])

    def test_timeout_blocks(self):
        self.assertFalse(self.execute('Good','safety',timeout=0.001)['passed'])

    def test_no_counterexample_and_wrong_exit_code_block(self):
        self.assertFalse(verdict(12,'Invariant ProbeNotOne is violated.','reachability','ProbeNotOne'))
        self.assertFalse(verdict(1,'Invariant ProbeNotOne is violated.\nState 1:','reachability','ProbeNotOne'))

    def test_tool_checksum_mismatch_blocks(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'tool.jar';p.write_bytes(b'wrong tool')
            with self.assertRaises(ValueError):tool_jar(p)

if __name__ == '__main__': unittest.main()
