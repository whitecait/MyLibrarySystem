import unittest
import tables_check as tables

class PaymentSpaceTest(unittest.TestCase):
    def setUp(self):
        self.calls = []
        original = tables.run_table
        def capture(name, variables, constraints, stages, **kwargs):
            self.calls.append((name, constraints, stages))
            return [], 0
        tables.run_table = capture
        try:
            tables.PAY()
        finally:
            tables.run_table = original

    def test_closed_unpaid_timeout_is_valid_and_absorbed(self):
        _, constraints, stages = next(c for c in self.calls if '生命周期' in c[0])
        e = dict(CR=True, TO=True, VAL=False, EXP=False, DUP=False, PD=False, CLOSED=True)
        self.assertTrue(all(f(e) for _, f in constraints))
        hits = [name for name, f, _ in stages[0][1] if f(e)]
        self.assertEqual(hits[0], 'PAY-D15')

    def test_first_order_does_not_require_previous_close(self):
        _, constraints, stages = next(c for c in self.calls if '建单与独占' in c[0])
        e = dict(OCC=False, STILL=True, CLS=False, PD2=False, HAD_ORDER=False)
        self.assertTrue(all(f(e) for _, f in constraints))
        self.assertEqual([name for name, f, _ in stages[0][1] if f(e)], ['PAY-D8'])


class ClosingCurrentPolicyTest(unittest.TestCase):
    def test_normal_and_backlog_rounds_both_wait_for_admin(self):
        calls=[];original=tables.run_table
        def capture(name,variables,constraints,stages,**kwargs):
            calls.append((name,constraints,stages));return [],0
        tables.run_table=capture
        try: tables.CL()
        finally: tables.run_table=original
        _,constraints,stages=next(c for c in calls if '批次推进' in c[0])
        for backlog in (False,True):
            e={'BACKLOG':backlog,'MORE':False,'WAITING':True}
            self.assertTrue(all(f(e) for _,f in constraints))
            hits=[(name,result) for name,f,result in stages[0][1] if f(e)]
            self.assertEqual(hits,[('CL-D9','等待管理员，保持隔离')])

if __name__ == '__main__': unittest.main()
