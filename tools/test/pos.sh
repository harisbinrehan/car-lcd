#!/bin/bash
# pos.sh <view id>  -> centre x y of that view on the emulator's launcher home screen
S="${CARLCD_WORK:-/tmp/carlcd}"; mkdir -p "$S"
"${ADB:-adb}" -s emulator-5554 shell dumpsys activity com.harislcd.launcher/.MainActivity | python3 -c "
import sys,re
stack=[]
for line in sys.stdin:
    g=re.match(r'^(\s*)\S+\{[0-9a-f]+ \S+ \S+ (-?\d+),(-?\d+)-(-?\d+),(-?\d+)',line)
    if not g: continue
    ind=len(g.group(1)); l,t,r,b=map(int,g.groups()[1:])
    while stack and stack[-1][0]>=ind: stack.pop()
    ox=sum(v[1] for v in stack); oy=sum(v[2] for v in stack)
    if 'app:id/$1 ' in line or line.rstrip().endswith('app:id/$1}'): print((ox+l+ox+r)//2,(oy+t+oy+b)//2); break
    stack.append((ind,l,t))"
