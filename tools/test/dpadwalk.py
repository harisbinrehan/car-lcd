# Walks the home screen of each theme with D-pad keys and lists which controls received focus.
import subprocess, re, time, sys, random
import os
S=os.environ.get("CARLCD_WORK","/tmp/carlcd"); os.makedirs(S,exist_ok=True)
ADB_BIN=os.environ.get("ADB","adb")
ADB=[ADB_BIN,"-s","emulator-5554"]
def sh(*a): return subprocess.run(ADB+["shell"]+list(a),capture_output=True,text=True).stdout
def focused():
    for line in sh("dumpsys","activity","com.harislcd.launcher/.MainActivity").splitlines():
        if re.search(r'\{[0-9a-f]+ \S+ .F',line):
            g=re.search(r'app:id/(\w+)',line); return g.group(1) if g else "?"
    return None
WANT={"item_music","item_apps","item_theme","item_settings","tile_radio","tile_bt","tile_auto","tile_eq","tile_youtube","np_prev","np_play","np_next","car_view","compass_view"}
random.seed(7); allok=True
for t in sys.argv[1:]:
    subprocess.run([S+"/emu.sh","set",t,"dark"]); time.sleep(6)
    seen=set(); keys=["DPAD_RIGHT","DPAD_DOWN","DPAD_LEFT","DPAD_UP"]
    # sweeps in each direction, then a random walk
    plan=["DPAD_DOWN"]*6+["DPAD_RIGHT"]*10+["DPAD_UP"]*6+["DPAD_LEFT"]*10+["DPAD_DOWN"]*3+["DPAD_RIGHT"]*5+["DPAD_UP"]*2+["DPAD_RIGHT"]*5+[random.choice(keys) for _ in range(45)]
    chain=None
    for k in plan[:110]:
        sh("input","keyevent","KEYCODE_"+k); f=focused()
        if f: seen.add(f)
        if f in ("np_prev","np_play","np_next") and chain is None:
            # walk the three playback buttons left to right and back
            route={"np_prev":[("DPAD_RIGHT","np_play"),("DPAD_RIGHT","np_next"),("DPAD_LEFT","np_play"),("DPAD_LEFT","np_prev")],
                   "np_play":[("DPAD_LEFT","np_prev"),("DPAD_RIGHT","np_play"),("DPAD_RIGHT","np_next")],
                   "np_next":[("DPAD_LEFT","np_play"),("DPAD_LEFT","np_prev"),("DPAD_RIGHT","np_play"),("DPAD_RIGHT","np_next")]}[f]
            got=[]; chain=True
            for kk,want in route:
                sh("input","keyevent","KEYCODE_"+kk); g=focused(); got.append(g); chain&=(g==want)
            seen.update(x for x in got if x)
        if WANT<=seen: break
    if chain is False: print(f"{t:8s} playback buttons did not chain correctly",flush=True); allok=False
    miss=sorted(WANT-seen); allok&=not miss
    print(f"{t:8s}", f"all {len(WANT)} controls reachable" if not miss else "NOT REACHED: "+", ".join(miss), flush=True)
print("crashes:",sh("logcat","-d","-s","AndroidRuntime:E").count("FATAL EXCEPTION")); print("DONE", "OK" if allok else "GAPS")
