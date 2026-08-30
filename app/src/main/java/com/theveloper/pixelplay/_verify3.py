import re
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

decl_re = re.compile(r'\b(private\s+|override\s+|internal\s+|protected\s+)?fun\s+\w+')
results = []
while i < N:
    # at start of line `line`, depth is current; detect declaration
    # read current line text
    j = src.find("\n", i)
    if j == -1: j = N
    text = src[i:j]
    if 'fun ' in text and decl_re.search(text):
        results.append((line, depth, text.strip()[:55]))
    # process current line char by char (including its newline)
    while i < N and src[i] != "\n":
        scan()
    if i < N and src[i] == "\n":
        scan()  # processes the newline (advances line)
    else:
        break

print("depth at start of each function/method declaration line:")
print("(class opens at 240 -> depth 1 inside class; member if depth==1)")
for ln, d, t in results:
    tag = "MEMBER" if d == 1 else ("NESTED" if d >= 2 else "FILE/TOP")
    print(f"  L{ln}: depth={d} [{tag}] {t}")
print("final depth:", depth)
