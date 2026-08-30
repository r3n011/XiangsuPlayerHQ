path = r"E:\PixelPlayer-master\app\src\main\java\com\theveloper\pixelplay\MainActivity.kt"
src = open(path, encoding="utf-8").read()
N = len(src)
modes = ['N']; depth = 0; line = 1; col = 1; i = 0
def cur(): return modes[-1]
trace_from, trace_to = 880, 1510
out = []
while i < N:
    if line >= trace_from and line <= trace_to:
        out.append((line, depth))
    c = src[i]; nxt = src[i+1] if i+1 < N else ""
    m = cur()
    if m == 'LC':
        if c == "\n": modes.pop(); line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if m == 'BC':
        if c == "*" and nxt == "/": modes.pop(); i+=2; col+=2; continue
        if c == "\n": line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if m == 'C':
        if c == "\\": i+=2; col+=2; continue
        if c == "'": modes.pop(); i+=1; col+=1; continue
        i+=1; col+=1; continue
    if m in ('S2','S3'):
        if c == "\\": i+=2; col+=2; continue
        if m == 'S2' and c == '"': modes.pop(); i+=1; col+=1; continue
        if m == 'S3':
            if c == '"' and nxt == '"' and i+2<N and src[i+2]=='"': modes.pop(); i+=3; col+=3; continue
            if c == '"': i+=1; col+=1; continue
        if c == '$' and nxt == '{': i+=2; col+=2; depth+=1; modes.append(('T',1)); continue
        if c == '$': i+=1; col+=1; continue
        if c == "\n" and m=='S2': modes.pop(); line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if isinstance(m, tuple) and m[0]=='T':
        td = m[1]
        if c == '{': td+=1; depth+=1; modes[-1]=('T',td); i+=1; col+=1; continue
        if c == '}': td-=1; depth-=1
        if td==0: modes.pop()
        else: modes[-1]=('T',td)
        i+=1; col+=1; continue
        if c == '"' and nxt == '"' and i+2<N and src[i+2]=='"': modes.append('S3'); i+=3; col+=3; continue
        if c == '"': modes.append('S2'); i+=1; col+=1; continue
        if c == "'": modes.append('C'); i+=1; col+=1; continue
        if c == "\n": line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if c == '/' and nxt == '/':
        modes.append('LC'); j = src.find("\n", i)
        if j==-1: break
        i=j; continue
    if c == '/' and nxt == '*':
        modes.append('BC'); i+=2; col+=2; continue
    if c == '"' and nxt == '"' and i+2<N and src[i+2]=='"':
        modes.append('S3'); i+=3; col+=3; continue
    if c == '"': modes.append('S2'); i+=1; col+=1; continue
    if c == "'": modes.append('C'); i+=1; col+=1; continue
    if c == '{': depth+=1; i+=1; col+=1; continue
    if c == '}': depth-=1; i+=1; col+=1; continue
    if c == "\n": line+=1; col=1; i+=1; continue
    i+=1; col+=1

# print depth at start of each line in range (recompute from out: out gives depth AFTER consuming that line's chars start)
# out[k] = depth recorded at the moment line==k (before consuming line k's tokens)
import re
prev = None
for ln, d in out:
    changed = (prev is None) or (d != prev)
    prev = d
    s = open(path, encoding="utf-8").read().split("\n")[ln-1]
    is_decl = bool(re.search(r'\bfun\s+\w+|\bclass\s+\w+|companion\s+object', s))
    if changed or is_decl:
        tag = "  <-- decl" if is_decl else ""
        print(f"L{ln}: depth={d}{tag}")
