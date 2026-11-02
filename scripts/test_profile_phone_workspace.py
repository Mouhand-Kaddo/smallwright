"""Host-only numeric process selector regression tests; no ADB or physical evidence."""
import unittest
from profile_phone_workspace import parse_children, parse_selection

class ProcessSelectorTests(unittest.TestCase):
    def test_valid_android_empty_selector_exit_one_is_not_a_failure(self):
        self.assertEqual(parse_selection(1, '  PID  PPID   UID\n', '', 123, 10318), [])

    def test_failed_selector_does_not_accept_rows_or_error_output(self):
        for rc, out, err in [(1, 'PID PPID UID\n234 123 10318\n', ''), (1, 'PID PPID UID\n', 'denied'), (2, 'PID PPID UID\n', ''), (0, 'PID PPID UID\n', 'denied')]:
            with self.subTest(rc=rc, out=out, err=err), self.assertRaises(AssertionError):
                parse_selection(rc, out, err, 123, 10318)

    def test_accepts_exact_parent_and_uid_with_empty_or_multiple_children(self):
        self.assertEqual(parse_children('PID PPID UID\n', 123, 10318), [])
        self.assertEqual(parse_children('PID PPID UID\n234 123 10318\n235 123 10318\n', 123, 10318), [234, 235])

    def test_rejects_foreign_owner_or_parent_before_process_reads(self):
        for row in ['234 123 10319', '234 124 10318', '123 123 10318']:
            with self.subTest(row=row), self.assertRaises(AssertionError):
                parse_children('PID PPID UID\n' + row + '\n', 123, 10318)

    def test_rejects_malformed_duplicate_or_unbounded_rows(self):
        for data in ['PID PPID CMD\n234 123 secret', 'PID PPID UID\n-1 123 10318', 'PID PPID UID\n234 123 u0_a318', 'PID PPID UID\n234 123 10318\n234 123 10318', 'PID PPID UID\n' + '\n'.join(f'{i} 123 10318' for i in range(200, 217))]:
            with self.subTest(data=data), self.assertRaises(AssertionError):
                parse_children(data, 123, 10318)

if __name__ == '__main__': unittest.main()
