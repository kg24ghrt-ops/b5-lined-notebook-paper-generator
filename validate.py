#!/usr/bin/env python3
"""Pre-build validation for the Android notebook paper generator.

Checks that can run without the Android SDK:
- XML well-formedness
- Java brace balance
- Duplicate string resources
- References to removed/deleted resource IDs
- Layout/drawable/color/string/dimen references that exist
- ID references (findViewById / menu items) that exist
- Common API mistakes (e.g. onScaleEnd returning boolean instead of void)
"""
import os, re, sys
import xml.dom.minidom
from collections import Counter

ROOT = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.join(ROOT, 'android')
errors = []
checks = 0

def check(cond, msg):
    global checks
    checks += 1
    if not cond:
        errors.append(msg)
        print(f'FAIL: {msg}')
    return cond

# ---------- Discover files ----------
xml_files = []
for dirpath, _, files in os.walk(os.path.join(ANDROID, 'app', 'src', 'main', 'res')):
    for fn in files:
        if fn.endswith('.xml'):
            xml_files.append(os.path.relpath(os.path.join(dirpath, fn), ROOT))
xml_files.append('android/app/src/main/AndroidManifest.xml')
xml_files.sort()

java_files = []
for mod in ('app', 'library'):
    for dirpath, _, files in os.walk(os.path.join(ANDROID, mod, 'src', 'main', 'java')):
        for fn in files:
            if fn.endswith('.java'):
                java_files.append(os.path.relpath(os.path.join(dirpath, fn), ROOT))
java_files.sort()

print(f'Found {len(xml_files)} XML files, {len(java_files)} Java files\n')

# ---------- XML well-formedness ----------
for rel in xml_files:
    p = os.path.join(ROOT, rel)
    try:
        xml.dom.minidom.parse(p)
        print(f'OK   xml: {rel}')
    except Exception as e:
        check(False, f'XML {rel}: {e}')

# ---------- Java brace balance ----------
for rel in java_files:
    p = os.path.join(ROOT, rel)
    src = open(p, encoding='utf-8', errors='ignore').read()
    ob = src.count('{'); cb = src.count('}')
    check(ob == cb, f'JAVA {rel}: braces unbalanced {ob} vs {cb}')
    if ob == cb:
        print(f'OK   java: {rel}')

# ---------- Collect defined resources ----------
colors, dimens, strings, drawables, all_ids = set(), set(), set(), set(), set()

def collect_resources(p):
    try:
        doc = xml.dom.minidom.parse(p)
    except Exception:
        return
    for el in doc.getElementsByTagName('*'):
        tag = el.tagName
        nm = el.getAttribute('name')
        if tag == 'color' and nm:
            colors.add(nm)
        elif tag == 'dimen' and nm:
            dimens.add(nm)
        elif tag == 'string' and nm:
            strings.add(nm)
        # id attributes (layouts + menus)
        idattr = el.getAttribute('android:id')
        if idattr.startswith('@+id/'):
            all_ids.add(idattr[5:])
        elif idattr.startswith('@id/'):
            all_ids.add(idattr[4:])

for rel in xml_files:
    collect_resources(os.path.join(ROOT, rel))

# Drawables: filename (minus extension) in drawable/ dir
for dp, _, fs in os.walk(os.path.join(ROOT, 'android/app/src/main/res/drawable')):
    for fn in fs:
        if fn.endswith('.xml'):
            drawables.add(fn[:-4])

# ---------- Duplicate string resources ----------
strings_p = os.path.join(ROOT, 'android/app/src/main/res/values/strings.xml')
if os.path.exists(strings_p):
    src = open(strings_p, encoding='utf-8').read()
    names = re.findall(r'name="([^"]+)"', src)
    dups = [n for n, c in Counter(names).items() if c > 1]
    check(not dups, f'DUPLICATE strings: {dups}')
    if not dups:
        print('OK   strings: no duplicates')

# ---------- Resource reference consistency ----------
ref_pattern = re.compile(r'@(color|dimen|string|drawable)/([A-Za-z0-9_]+)')
pool = {'color': colors, 'dimen': dimens, 'string': strings, 'drawable': drawables}
for rel in xml_files:
    p = os.path.join(ROOT, rel)
    src = open(p, encoding='utf-8', errors='ignore').read()
    for m in ref_pattern.finditer(src):
        rtype, rname = m.group(1), m.group(2)
        check(rname in pool[rtype], f'{rel}: @{rtype}/{rname} not defined')

# ID references in Java (findViewById)
id_ref_pattern = re.compile(r'R\.id\.([A-Za-z0-9_]+)')
for rel in java_files:
    p = os.path.join(ROOT, rel)
    src = open(p, encoding='utf-8', errors='ignore').read()
    for m in id_ref_pattern.finditer(src):
        check(m.group(1) in all_ids, f'{rel}: R.id.{m.group(1)} not defined in any layout/menu')

# ---------- Common API mistakes ----------
for rel in java_files:
    p = os.path.join(ROOT, rel)
    src = open(p, encoding='utf-8', errors='ignore').read()
    for m in re.finditer(r'public boolean onScaleEnd\(', src):
        check(False, f'{rel}: onScaleEnd returns boolean; OnScaleGestureListener.SimpleOnScaleGestureListener.onScaleEnd returns void')

# ---------- Summary ----------
print(f'\nChecks: {checks}, Errors: {len(errors)}')
if errors:
    print('ERRORS:')
    for e in errors:
        print('  -', e)
    sys.exit(1)
print('All checks passed.')
