import os, xml.etree.ElementTree as ET, shutil

NS='{http://schemas.android.com/apk/res/android}'
RES=r'E:\PixelPlayer-master\app\src\main\res'
LOCALES=['de','es','fr','in','it','ko','nb','ru','tr','zh-rCN']
LOCALE_DIRS={l:'values-'+l for l in LOCALES}
DIR_PRIORITY=['values']+[LOCALE_DIRS[l] for l in LOCALES]  # base English first, then siblings
ET.register_namespace('android','http://schemas.android.com/apk/res/android')

def esc(s):
    return s.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;')

def parse(path):
    try:
        return ET.parse(path).getroot()
    except Exception:
        return None

def items_of(root):
    out={}
    if root is None: return out
    for e in root:
        n=e.get(NS+'name') or e.get('name')
        if n: out[n]=e
    return out

def keys_of(path):
    return set(items_of(parse(path)))

def serialize(e):
    # element -> clean XML string, strip redundant xmlns on the node
    s=ET.tostring(e, encoding='unicode')
    s=s.replace(' xmlns:android="http://schemas.android.com/apk/res/android"','')
    return s

def insert_before_resources(path, block):
    with open(path,'r',encoding='utf-8') as f:
        text=f.read()
    idx=text.rfind('</resources>')
    if idx==-1:
        text=text.rstrip()+"\n"+block
    else:
        text=text[:idx]+block+text[idx:]
    with open(path,'w',encoding='utf-8') as f:
        f.write(text)

basenames=sorted({f for d in ['values']+list(LOCALE_DIRS.values()) for f in os.listdir(os.path.join(RES,d)) if f.startswith('strings') and f.endswith('.xml')})

union={}; src_elem={}
for fn in basenames:
    u=set(); best={}
    for d in DIR_PRIORITY:
        fp=os.path.join(RES,d,fn)
        if not os.path.exists(fp): continue
        items=items_of(parse(fp))
        u|=set(items)
        for n,e in items.items():
            if n not in best: best[n]=e
    union[fn]=u
    for n,e in best.items(): src_elem[(fn,n)]=e

report=[]; created=0; added_base=0; added_locale=0; placeholder=0

for fn in basenames:
    bp=os.path.join(RES,'values',fn)
    bk=keys_of(bp)
    missing=sorted(union[fn]-bk)
    if missing:
        lines=[]
        for m in missing:
            e=src_elem[(fn,m)]
            lines.append('    '+serialize(e))
            if (e.get(NS+'name') or e.get('name')) and (LOCALE_DIRS.get('zh-rCN') and False): pass
        insert_before_resources(bp, "".join(l+"\n" for l in lines))
        added_base+=len(missing)
        report.append(f"[base] {fn}: +{len(missing)} keys")

for l in LOCALES:
    d=LOCALE_DIRS[l]
    for fn in basenames:
        fp=os.path.join(RES,d,fn)
        if not os.path.exists(fp):
            shutil.copyfile(os.path.join(RES,'values',fn), fp)
            created+=1
            report.append(f"[new ] {l}/{fn}: created (copy of completed English base)")
            continue
        existing=keys_of(fp)
        missing=sorted(union[fn]-existing)
        if not missing: continue
        lines=[]
        for m in missing:
            e=src_elem[(fn,m)]
            src_dir = 'values'  # base preferred
            # detect if value actually came from a non-English source
            lines.append('    '+serialize(e))
        insert_before_resources(fp, "".join(x+"\n" for x in lines))
        added_locale+=len(missing)
        report.append(f"[add ] {l}/{fn}: +{len(missing)} keys")

print("Files created:",created)
print("Keys added to English base:",added_base)
print("Keys added to locale files:",added_locale)
print("Total edits:",len(report))
for r in report:
    print("  ",r)
