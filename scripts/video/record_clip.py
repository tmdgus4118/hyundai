"""에뮬레이터 화면을 녹화하면서 주행 경로를 GPS 로 흘리고, 지정 위치에서 음성 명령을 주입한다.
사용: python3 record_clip.py <out_name> <route|none> <scale> <steps.json>
steps.json: [{"at": {"lng_gte": 126.998} | {"t": 3.0} | {"after": 8}, "do": "ask"|"tap"|"key", "arg": ...}]
이벤트 시각(녹화 시작 기준 초)과 앱이 말한 문장을 <out_name>.events.json 으로 남긴다."""
import json
import math
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from drive_route import ROUTES, dist_m  # noqa: E402

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
OUT_DIR = os.path.dirname(os.path.abspath(__file__))
name, route, scale, steps_path = sys.argv[1], sys.argv[2], float(sys.argv[3]), sys.argv[4]
steps = json.load(open(steps_path, encoding="utf8"))
SPEED = {"olympic": 70, "sejong": 35, "gyeongbu": 90}.get(route, 0)


def adb(*a, check=False):
    return subprocess.run([ADB, *a], capture_output=True, text=True, check=check).stdout


def act(step):
    d, arg = step["do"], step.get("arg")
    if d == "ask":
        adb("shell", f"am broadcast -a io.whatsthat.car.DEBUG_ASK --es q '{arg}'")
    elif d == "tap":
        adb("shell", "input", "tap", str(arg[0]), str(arg[1]))
    elif d == "key":
        adb("shell", "input", "keyevent", arg)
    elif d == "start":
        adb("shell", "am", "start", "-n", "io.whatsthat.car/.ui.MainActivity")


adb("logcat", "-c")
rec = subprocess.Popen([ADB, "shell", "screenrecord", "--bit-rate", "12000000", "--time-limit", "180", f"/sdcard/{name}.mp4"])
t0 = time.time()
time.sleep(1.0)
events = []
pending = list(steps)
last_fire = t0


def fire_ready(lat, lng):
    global last_fire
    now = time.time()
    while pending:
        cond = pending[0]["at"]
        ok = (("t" in cond and now - t0 >= cond["t"]) or
              ("after" in cond and now - last_fire >= cond["after"]) or
              ("lng_gte" in cond and lng is not None and lng >= cond["lng_gte"]) or
              ("lat_gte" in cond and lat is not None and lat >= cond["lat_gte"]))
        if not ok:
            break
        s = pending.pop(0)
        act(s)
        last_fire = time.time()
        events.append({"t": round(last_fire - t0, 2), "do": s["do"], "arg": s.get("arg"), "label": s.get("label", "")})
        print(f"[{events[-1]['t']:6.1f}s] {s['do']} {s.get('arg')}", flush=True)


if route != "none":
    step = SPEED / 3.6 * scale
    pts = ROUTES[route]
    for a, b in zip(pts, pts[1:]):
        n = max(1, int(dist_m(a, b) / step))
        for i in range(n):
            t = i / n
            lat, lng = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
            subprocess.run([ADB, "emu", "geo", "fix", f"{lng:.6f}", f"{lat:.6f}"], stdout=subprocess.DEVNULL)
            fire_ready(lat, lng)
            time.sleep(1.0)
    last = pts[-1]
    subprocess.run([ADB, "emu", "geo", "fix", f"{last[1]:.6f}", f"{last[0]:.6f}"], stdout=subprocess.DEVNULL)
while pending:
    fire_ready(None, None)
    time.sleep(0.2)
time.sleep(steps[-1].get("tail", 6) if steps else 6)

adb("shell", "pkill", "-INT", "screenrecord")
rec.wait(timeout=20)
time.sleep(1.5)
adb("pull", f"/sdcard/{name}.mp4", os.path.join(OUT_DIR, f"{name}.mp4"))

# 앱이 실제로 말한 문장 (TTS 요청 로그)
log = adb("logcat", "-d", "-v", "epoch")
spoken = []
for line in log.splitlines():
    if "doSpeak(io.whatsthat.car) text=" in line:
        ts = float(line.split()[0])
        text = line.split("text=", 1)[1].rsplit(", isFinal", 1)[0]
        spoken.append({"t": round(ts - t0, 2), "text": text})
json.dump({"events": events, "spoken": spoken, "duration": round(time.time() - t0, 1)},
          open(os.path.join(OUT_DIR, f"{name}.events.json"), "w", encoding="utf8"), ensure_ascii=False, indent=1)
print(json.dumps(spoken, ensure_ascii=False, indent=1))
