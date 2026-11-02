import unittest
import xml.etree.ElementTree as ET

from aconfig_api_config import exportable_config


class ExportableApiFlagsTest(unittest.TestCase):
    def test_matches_enabled_read_only_projection_and_orders_output(self):
        rows = [
            'com.example|z_feature|READ_ONLY|ENABLED',
            'com.example|disabled|READ_ONLY|DISABLED',
            'com.example|mutable|READ_WRITE|ENABLED',
            'com.example|a_feature|READ_ONLY|ENABLED',
        ]
        first = exportable_config(rows)
        self.assertEqual(first, exportable_config(reversed(rows)))
        root = ET.fromstring(first)
        ns = {'m': 'http://www.google.com/tools/metalava/config'}
        flags = root.findall('m:api-flags/m:api-flag', ns)
        self.assertEqual([f.attrib for f in flags], [
            {'package': 'com.example', 'name': 'a_feature',
             'mutability': 'immutable', 'status': 'enabled'},
            {'package': 'com.example', 'name': 'z_feature',
             'mutability': 'immutable', 'status': 'enabled'},
        ])
        empty = ET.fromstring(exportable_config([]))
        self.assertIsNotNone(empty.find('m:api-flags', ns))

    def test_runtime_projection_retains_mutable_flags_even_when_disabled(self):
        rows = [
            'com.example|readonly_enabled|READ_ONLY|ENABLED',
            'com.example|readonly_disabled|READ_ONLY|DISABLED',
            'com.example|mutable_enabled|READ_WRITE|ENABLED',
            'com.example|mutable_disabled|READ_WRITE|DISABLED',
        ]
        config = exportable_config(rows, export_runtime_apis=True)
        self.assertEqual(config, exportable_config(reversed(rows), export_runtime_apis=True))
        flags = ET.fromstring(config).findall(
            'm:api-flags/m:api-flag', {'m': 'http://www.google.com/tools/metalava/config'})
        self.assertEqual([f.attrib for f in flags], [
            {'package': 'com.example', 'name': 'mutable_disabled',
             'mutability': 'mutable', 'status': 'disabled'},
            {'package': 'com.example', 'name': 'mutable_enabled',
             'mutability': 'mutable', 'status': 'enabled'},
            {'package': 'com.example', 'name': 'readonly_enabled',
             'mutability': 'immutable', 'status': 'enabled'},
        ])

    def test_rejects_ambiguous_or_malformed_cache_rows(self):
        good = 'com.example|feature|READ_ONLY|ENABLED'
        for rows in ([good, good], [good, 'com.example|feature|READ_WRITE|DISABLED'],
                     ['com.example|feature|UNKNOWN|ENABLED'],
                     ['com.example|feature|READ_ONLY|UNKNOWN'],
                     ['invalid package|feature|READ_ONLY|ENABLED'],
                     ['com.example|bad/name|READ_ONLY|ENABLED'], ['partial|row']):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                exportable_config(rows)


if __name__ == '__main__':
    unittest.main()
