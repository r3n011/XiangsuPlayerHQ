import os, xml.etree.ElementTree as ET, json
NS='{http://schemas.android.com/apk/res/android}'
RES=r'E:\PixelPlayer-master\app\src\main\res'
ET.register_namespace('android','http://schemas.android.com/apk/res/android')

def parse(p):
    d={}
    for e in ET.parse(p).getroot():
        n=e.get(NS+'name') or e.get('name')
        if n: d[n]=e
    return d

locales=['values-de','values-es','values-fr','values-in','values-it','values-ko','values-nb','values-ru','values-tr','values-zh-rCN']
basenames=sorted({f for d in ['values']+locales for f in os.listdir(os.path.join(RES,d)) if f.startswith('strings') and f.endswith('.xml')})

missing=[]  # keys in union but not in base
for fn in basenames:
    base=parse(os.path.join(RES,'values',fn))
    per_lang={l:parse(os.path.join(RES,l,fn)) for l in locales if os.path.exists(os.path.join(RES,l,fn))}
    union=set(base)
    for l,d in per_lang.items(): union|=set(d)
    for k in sorted(union-set(base)):
        # find a source value (prefer zh-rCN, else any lang)
        src_lang=src_val=None
        if k in per_lang.get('values-zh-rCN',{}):
            src_lang='zh-rCN'; src=per_lang['values-zh-rCN'][k]
        else:
            for l,d in per_lang.items():
                if k in d:
                    src_lang=l; src=d[k]; break
        def txt(e):
            if e.tag in ('string-array','plurals','integer-array'):
                return [ (it.get('quantity'), (it.text or '').strip()) for it in e ]
            return (e.text or '').strip()
        missing.append({'basename':fn,'name':k,'type':src.tag if src is not None else '?',
                        'src_lang':src_lang,'src_value':txt(src) if src is not None else None})

OUT=os.path.join(r'E:\PixelPlayer-master\.workbuddy','scripts','missing_en.json')
with open(OUT,'w',encoding='utf-8') as f:
    json.dump(missing,f,ensure_ascii=False,indent=1)
print("Total keys missing from English base:",len(missing))
from collections import Counter
print("By type:",Counter(m['type'] for m in missing))
print("By basename:",Counter(m['basename'] for m in missing))
print("\nSample (first 5):")
for m in missing[:5]:
    print(" ",m['basename'],m['name'],'|',m['src_lang'],'|',m['src_value'])
