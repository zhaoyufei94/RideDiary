#!/usr/bin/env python3
"""Fail on missing translations or source strings that refer to absent resources."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
zh={e.attrib['name'] for e in ET.parse(root/'app/src/main/res/values/strings.xml').getroot()}
en={e.attrib['name'] for e in ET.parse(root/'app/src/main/res/values-en/strings.xml').getroot()}
assert zh==en, f'Translation mismatch: {zh ^ en}'
for source in (root/'app/src').rglob('*.kt'):
    missing=set(re.findall(r'R\.string\.(\w+)',source.read_text()))-zh
    assert not missing, f'{source}: {missing}'
print(f'PASS: {len(zh)} bilingual resources; all Kotlin resource references resolve.')
