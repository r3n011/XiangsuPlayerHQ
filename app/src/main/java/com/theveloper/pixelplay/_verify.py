path = r"E:\PixelPlayer-master\app\src\main\java\com\theveloper\pixelplay\MainActivity.kt"
src = open(path, encoding="utf-8").read()
N = len(src)
modes = ['N']; depth = 0; line = 1; col = 1; i = 0
def cur(): return modes[-1]
def scan():
    global depth, line, col, i
    c = src[i]; nxt = src[i+1] if i+1 < N else ""
    m = cur()
    if m == 'LC':
        if c == "\n": modes.pop(); line+=1; col=1; i+=1; return
        i+=1; col+=1; return
    if m == 'BC':
        if c == "*" and nxt == "/": modes.pop(); i+=2; col+=2; return
        if c == "\n": line+=1; col=1; i+=1; return
        i+=1; col+=1; return
    if m == 'C':
        if c == "\\": i+=2; col+=2; return
        if c == "'": modes.pop(); i+=1; col+=1; return
        i+=1; col+=1; return
    if m in ('S2','S3'):
        if c == "\\": i+=2; col+=2; return
        if m == 'S2' and c == '"': modes.pop(); i+=1; col+=1; return
        if m == 'S3':
            if c == '"' and nxt == '"' and i+2 < N and src[i+2]=='"': modes.pop(); i+=3; col+=3; return
            if c == '"': i+=1; col+=1; return
        if c == '$' and nxt == '{': i+=2; col+=2; depth+=1; modes.append(('T',1)); return
        if c == '$': i+=1; col+=1; return
        if c == "\n" and m=='S2': modes.pop(); line+=1; col=1; i+=1; return
        i+=1; col+=1; return
    if isinstance(m, tuple) and m[0]=='T':
        td = m[1]
        if c == '{': td+=1; depth+=1; modes[-1]=('T',td); i+=1; col+=1; return
        if c == '}': td-=1; depth-=1
        if td==0: modes.pop()
        else: modes[-1]=('T',td)
        i+=1; col+=1; return
        if c == '"' and nxt == '"' and i+2 < N and src[i+2]=='"': modes.append('S3'); i+=3; col+=3; return
        if c == '"': modes.append('S2'); i+=1; col+=1; return
        if c == "'": modes.append('C'); i+=1; col+=1; return
        if c == "\n": line+=1; col=1; i+=1; return
        i+=1; col+=1; return
    if c == '/' and nxt == '/':
        modes.append('LC'); j = src.find("\n", i)
        if j == -1: return
        i = j; return
    if c == '/' and nxt == '*': modes.append('BC'); i+=2; col+=2; return
    if c == '"' and nxt == '"' and i+2 < N and src[i+2]=='"': modes.append('S3'); i+=3; col+=3; return
    if c == '"': modes.append('S2'); i+=1; col+=1; return
    if c == "'": modes.append('C'); i+=1; col+=1; return
    if c == '{': depth+=1; i+=1; col+=1; return
    if c == '}': depth-=1; i+=1; col+=1; return
    if c == "\n": line+=1; col=1; i+=1; return
    i+=1; col+=1; return

interest = [240,887,1508,1580,1858,1969,2021,2036,2050,2058,2086,2090,2282,2309,2349,2395]
depth_at = {}
for ln in interest:
    while line < ln and i < N:
        scan()
    if line == ln:
        depth_at[ln] = depth
while i < N: scan()
print("depth at declaration lines (class opens at 240 -> depth 1):")
for ln in interest:
    d = depth_at.get(ln, '?')
    note = "MEMBER (class level)" if d == 1 else ("inside func / nested" if d >= 2 else "?")
    print(f"  L{ln}: depth={d}  -> {note}")
print("final depth:", depth)
