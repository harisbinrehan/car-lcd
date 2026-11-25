import cv2, sys
cap=cv2.VideoCapture(sys.argv[1]); n=0; worst=0; first=None; vals=[]
while True:
    ok,f=cap.read()
    if not ok: break
    n+=1; g=cv2.cvtColor(cv2.resize(f,(160,90)),cv2.COLOR_BGR2GRAY)[4:84,:]   # ignore system bars
    w=float((g>225).mean())*100; vals.append(round(w)); worst=max(worst,w)
print(f"{sys.argv[2]:34s} frames={n:3d}  worst white share={worst:5.1f}%   sequence={vals[::max(1,len(vals)//14)]}")
