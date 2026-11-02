#!/usr/bin/env python3
"""Convert aconfig dump rows to Metalava's explicit export API flag policy.

Input format: {package}|{name}|{permission}|{state}, one flag per line.
The default matches Soong's Exportable filter with ExportRuntimeApis false.
An explicit option adds mutable flags, matching ExportRuntimeApis true;
callers must establish their release configuration and complete cache inputs.
It is not an API compatibility or licensing check.
"""

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


NAMESPACE = 'http://www.google.com/tools/metalava/config'
IDENTIFIER = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*\Z')


def exportable_config(rows, *, export_runtime_apis=False):
    flags = {}
    for row in rows:
        row = row.strip()
        if not row:
            continue
        fields = row.split('|')
        if len(fields) != 4:
            raise ValueError('expected package|name|permission|state')
        package, name, permission, state = fields
        if not all(IDENTIFIER.fullmatch(p) for p in package.split('.')):
            raise ValueError('invalid flag package')
        if not IDENTIFIER.fullmatch(name):
            raise ValueError('invalid flag name')
        if permission not in ('READ_ONLY', 'READ_WRITE') or state not in ('ENABLED', 'DISABLED'):
            raise ValueError('unknown flag permission or state')
        key = (package, name)
        if key in flags:
            raise ValueError('duplicate flag: ' + package + '.' + name)
        flags[key] = (permission, state)
    root = ET.Element('config', xmlns=NAMESPACE)
    api = ET.SubElement(root, 'api-flags')
    for (package, name), (permission, state) in sorted(flags.items()):
        if permission == 'READ_ONLY' and state == 'ENABLED':
            ET.SubElement(api, 'api-flag', package=package, name=name,
                          mutability='immutable', status='enabled')
        elif permission == 'READ_WRITE' and export_runtime_apis:
            ET.SubElement(api, 'api-flag', package=package, name=name,
                          mutability='mutable', status=state.lower())
    ET.indent(root)
    return ET.tostring(root, encoding='unicode') + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--export-runtime-apis', action='store_true',
                        help='Include read-write flags regardless of current state')
    args = parser.parse_args()
    try:
        config = exportable_config(sys.stdin, export_runtime_apis=args.export_runtime_apis)
        with args.output.open('x', encoding='utf-8') as stream:
            stream.write(config)
    except (OSError, ValueError) as error:
        parser.exit(2, f'API flag configuration failed: {error}\n')


if __name__ == '__main__':
    main()
