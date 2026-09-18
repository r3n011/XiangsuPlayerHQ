import os, xml.etree.ElementTree as ET
from collections import defaultdict

NS = '{http://schemas.android.com/apk/res/android}'
RES = r'E:\PixelPlayer-master\app\src\main\res'

ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')

STRING_LIKE = {'string','string-array','plurals','integer-array','array',
               'color','dimen','style','bool','integer','fraction','item'}

def parse_keys(path):
    keys = {}
    try:
        tree = ET.parse(path)
    except Exception as e:
        return None, str(e)
    root = tree.getroot()
    for elem in root:
        if elem.tag in STRING_LIKE:
            name = elem.get(NS+'name') or elem.get('name')
            if name:
                keys[name] = elem.tag
    return keys, None

base_dir = os.path.join(RES,'values')
locale_dirs = sorted(d for d in os.listdir(RES) if d.startswith('values-')
                     and os.path.isdir(os.path.join(RES,d)))

data = defaultdict(dict)
all_basenames = set()
for d in ['values']+locale_dirs:
    od = os.path.join(RES,d)
    for fn in sorted(os.listdir(od)):
        if fn.startswith('strings') and fn.endswith('.xml'):
            all_basenames.add(fn)
            keys,err = parse_keys(os.path.join(od,fn))
            loc = 'base' if d=='values' else d
            data[fn][loc] = keys

print("Locales (values-):", locale_dirs)
print("Total string basenames:", len(all_basenames))
print("="*70)
totals_missing = defaultdict(int)
totals_extra_files = defaultdict(int)   # files a lang lacks that base has
for fn in sorted(all_basenames):
    entries = data[fn]
    base = entries.get('base')
    print(f"\n== {fn} ==")
    if base is None:
        print("   !! NO base English file")
        for loc in sorted(entries):
            print(f"   {loc}: {len(entries[loc])} keys")
        continue
    print(f"   base keys: {len(base)}")
    for loc in sorted(entries):
        if loc=='base': continue
        kk = entries[loc]
        missing = set(base)-set(kk)
        totals_missing[loc]+=len(missing)
        print(f"   {loc}: {len(kk)} keys, missing vs base: {len(missing)}")
    langs_with = set(entries)-{'base'}
    langs_missing_file = [l for l in locale_dirs if l not in langs_with]
    if langs_missing_file:
        totals_extra_files['__files__']+=len(langs_missing_file)
        print(f"   langs WITHOUT this file: {langs_missing_file}")

print("\n"+"="*70)
print("SUMMARY per language (total missing keys vs base):")
for loc in locale_dirs:
    print(f"   {loc}: missing keys = {totals_missing.get(loc,0)}")
