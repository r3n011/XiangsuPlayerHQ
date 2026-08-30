path = r"E:\PixelPlayer-master\app\src\main\java\com\theveloper\pixelplay\MainActivity.kt"
src = open(path, encoding="utf-8").read()
N = len(src)

depth = 0
stack = []   # (line, col) of unmatched '{'
line = 1; col = 1
modes = ['N']   # N, S2, S3, C, BC, LC, or ('T', td)
i = 0
extra = []   # extra '}' with line

def cur():
    return modes[-1]

while i < N:
    c = src[i]; nxt = src[i+1] if i+1 < N else ""
    m = cur()
    if m == 'LC':
        if c == "\n":
            modes.pop(); line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if m == 'BC':
        if c == "*" and nxt == "/":
            modes.pop(); i+=2; col+=2; continue
        if c == "\n":
            line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if m == 'C':
        if c == "\\":
            i+=2; col+=2; continue
        if c == "'":
            modes.pop(); i+=1; col+=1; continue
        i+=1; col+=1; continue
    if m in ('S2','S3'):
        if c == "\\":
            i+=2; col+=2; continue
        if m == 'S2' and c == '"':
            modes.pop(); i+=1; col+=1; continue
        if m == 'S3':
            if c == '"' and nxt == '"' and i+2 < N and src[i+2]=='"':
                modes.pop(); i+=3; col+=3; continue
            if c == '"':
                i+=1; col+=1; continue
        if c == '$' and nxt == '{':
            i+=2; col+=2
            depth+=1; stack.append((line,col))
            modes.append(('T',1)); continue
        if c == '$':
            i+=1; col+=1; continue
        if c == "\n" and m=='S2':
            modes.pop(); line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if isinstance(m, tuple) and m[0]=='T':
        td = m[1]
        if c == '{':
            td+=1; depth+=1; stack.append((line,col)); modes[-1]=('T',td); i+=1; col+=1; continue
        if c == '}':
            td-=1; depth-=1
            if stack: stack.pop()
            if td==0:
                modes.pop()
            else:
                modes[-1]=('T',td)
            i+=1; col+=1; continue
        if c == '"' and nxt == '"' and i+2 < N and src[i+2]=='"':
            modes.append('S3'); i+=3; col+=3; continue
        if c == '"':
            modes.append('S2'); i+=1; col+=1; continue
        if c == "'":
            modes.append('C'); i+=1; col+=1; continue
        if c == "\n":
            line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    # m == 'N'
    if c == '/' and nxt == '/':
        modes.append('LC'); j = src.find("\n", i)
        if j==-1: break
        i=j; continue
    if c == '/' and nxt == '*':
        modes.append('BC'); i+=2; col+=2; continue
    if c == '"' and nxt == '"' and i+2 < N and src[i+2]=='"':
        modes.append('S3'); i+=3; col+=3; continue
    if c == '"':
        modes.append('S2'); i+=1; col+=1; continue
    if c == "'":
        modes.append('C'); i+=1; col+=1; continue
    if c == '{':
        depth+=1; stack.append((line,col)); i+=1; col+=1; continue
    if c == '}':
        if depth==0:
            extra.append((line,col))
        else:
            depth-=1
            if stack: stack.pop()
        i+=1; col+=1; continue
    if c == "\n":
        line+=1; col=1; i+=1; continue
    i+=1; col+=1

print("Final net depth (open - close):", depth)
print("Unclosed '{' (deepest 15):")
for ln,c in stack[-15:]:
    print("   line", ln)
print("Extra '}' (first 15):")
for ln,c in extra[:15]:
    print("   line", ln)
print("Remaining modes:", modes)
