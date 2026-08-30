path = r"E:\PixelPlayer-master\app\src\main\java\com\theveloper\pixelplay\MainActivity.kt"
src = open(path, encoding="utf-8").read()
N = len(src)

# Correct Kotlin brace scanner (handles templates) -> record depth at given lines
lines_of_interest = [240,270,283,534,539,616,658,680,694,736,796,887,1507,1579,1857,1968,2020,2035,2049,2057,2089,2281,2308,2348,2394]
depth_at = {}
depth = 0
line = 1; col = 1
modes = ['N']
i = 0

def cur(): return modes[-1]

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
            i+=2; col+=2; depth+=1; modes.append(('T',1)); continue
        if c == '$':
            i+=1; col+=1; continue
        if c == "\n" and m=='S2':
            modes.pop(); line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if isinstance(m, tuple) and m[0]=='T':
        td = m[1]
        if c == '{':
            td+=1; depth+=1; modes[-1]=('T',td); i+=1; col+=1; continue
        if c == '}':
            td-=1; depth-=1
            if td==0: modes.pop()
            else: modes[-1]=('T',td)
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
    # N
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
        depth+=1
        if line in depth_at: pass
        i+=1; col+=1; continue
    if c == '}':
        depth-=1; i+=1; col+=1; continue
    if c == "\n":
        line+=1; col=1; i+=1; continue
    i+=1; col+=1
    # record AFTER consuming, using current line
    if line in lines_of_interest and line not in depth_at:
        depth_at[line] = depth

# also record at each line start precisely: recompute via scanning line by line is simpler.
# Re-scan recording depth at START of each interest line.
depth = 0
modes = ['N']
i = 0; line = 1; col = 1
depth_start = {}
def cur(): return modes[-1]
while i < N:
    # record depth at the moment we are at beginning of `line`
    if line in lines_of_interest:
        depth_start[line] = depth
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
            i+=2; col+=2; depth+=1; modes.append(('T',1)); continue
        if c == '$':
            i+=1; col+=1; continue
        if c == "\n" and m=='S2':
            modes.pop(); line+=1; col=1; i+=1; continue
        i+=1; col+=1; continue
    if isinstance(m, tuple) and m[0]=='T':
        td = m[1]
        if c == '{':
            td+=1; depth+=1; modes[-1]=('T',td); i+=1; col+=1; continue
        if c == '}':
            td-=1; depth-=1
            if td==0: modes.pop()
            else: modes[-1]=('T',td)
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
        depth+=1; i+=1; col+=1; continue
    if c == '}':
        depth-=1; i+=1; col+=1; continue
    if c == "\n":
        line+=1; col=1; i+=1; continue
    i+=1; col+=1

print("depth at START of each interest line (relative to file; class opens at 240):")
for ln in lines_of_interest:
    d = depth_start.get(ln, '?')
    # interpret: even depth => class-member level if class opened at 240 (then depth at 240 start should be 1)
    print(f"  line {ln}: depth={d}  -> {'MEMBER' if (d % 2 == 1) else 'LOCAL/TOP'}")
print("Final depth:", depth)
