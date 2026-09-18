import os, xml.etree.ElementTree as ET
NS='{http://schemas.android.com/apk/res/android}'
RES=r'E:\PixelPlayer-master\app\src\main\res'
LOCALES=['de','es','fr','in','it','ko','nb','ru','tr','zh-rCN']
LOCALE_DIRS={l:'values-'+l for l in LOCALES}

def keys_of(path):
    # returns (set_of_keys, dups) on success, or None on parse error
    if not os.path.exists(path): return None
    try:
        root=ET.parse(path).getroot()
    except Exception:
        return None
    out=set(); seen=set(); dups=0
    for e in root:
        n=e.get(NS+'name') or e.get('name')
        if n:
            if n in seen: dups+=1
            seen.add(n); out.add(n)
    return out, dups

basenames=sorted({f for d in ['values']+list(LOCALE_DIRS.values()) for f in os.listdir(os.path.join(RES,d)) if f.startswith('strings') and f.endswith('.xml')})
union={}
for fn in basenames:
    u=set()
    for d in ['values']+list(LOCALE_DIRS.values()):
        r=keys_of(os.path.join(RES,d,fn))
        if r is not None: u|=r[0]
    union[fn]=u

problems=[]; ok=0
for l in LOCALES:
    d=LOCALE_DIRS[l]
    for fn in basenames:
        fp=os.path.join(RES,d,fn)
        r=keys_of(fp)
        if r is None:
            problems.append(f"PARSE/MISSING {l}/{fn}"); continue
        ks,dups=r
        if dups: problems.append(f"DUP KEYS {l}/{fn}: {dups}")
        missing=union[fn]-ks
        extra=ks-union[fn]
        if missing: problems.append(f"STILL MISSING {l}/{fn}: {len(missing)} -> {sorted(missing)[:5]}")
        if extra: problems.append(f"EXTRA KEYS {l}/{fn}: {len(extra)}")
        if not missing and not extra and not dups: ok+=1

print(f"Locale files verified OK: {ok} / {len(LOCALES)*len(basenames)}")
print("Basenames:",len(basenames))
if problems:
    print("\nPROBLEMS:")
    for p in problems: print("  ",p)
else:
    print("\nALL CHECKS PASSED: every locale file == union key set, no parse errors, no duplicates.")
# verify base files parse
basebad=[fn for fn in basenames if keys_of(os.path.join(RES,'values',fn)) is None]
print("Base file parse errors:", basebad or 'none')
