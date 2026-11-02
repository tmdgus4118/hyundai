#!/usr/bin/env python3
"""
에뮬레이터 GPS 에 실제 주행 경로를 흘려 보내는 시연/테스트 도구.
앱 내부 시연 모드와 달리 Pleos FusedLocation SDK 경로를 그대로 거친다.

  python3 scripts/drive_route.py olympic --speed 70 --scale 3
  (기어는 Pleos Connect Virtual Console 에서 D 로 바꾼다: adb forward tcp:8088 tcp:8088 → http://127.0.0.1:8088)
"""
import argparse
import math
import os
import subprocess
import time

ROUTES = {
    "olympic": [(37.5170, 126.9300), (37.5150, 126.9450), (37.5120, 126.9560), (37.5075, 126.9700),
                (37.5050, 126.9810), (37.5080, 126.9930), (37.5170, 127.0080), (37.5270, 127.0230),
                (37.5290, 127.0350), (37.5250, 127.0560), (37.5185, 127.0700), (37.5200, 127.0850),
                (37.5230, 127.0950), (37.5290, 127.1050), (37.5330, 127.1200)],
    "sejong": [(37.5560, 126.9733), (37.5585, 126.9740), (37.5620, 126.9762), (37.5650, 126.9768),
               (37.5690, 126.9768), (37.5720, 126.9768), (37.5742, 126.9768)],
    "gyeongbu": [(37.3930, 127.1150), (37.4100, 127.1050), (37.4300, 127.0850), (37.4500, 127.0550),
                 (37.4590, 127.0400), (37.4700, 127.0390), (37.4850, 127.0330), (37.5000, 127.0230),
                 (37.5130, 127.0130), (37.5250, 127.0130), (37.5350, 127.0110)],
}


def dist_m(a, b):
    lat = math.radians((a[0] + b[0]) / 2)
    dx = math.radians(b[1] - a[1]) * math.cos(lat) * 6371000
    dy = math.radians(b[0] - a[0]) * 6371000
    return math.hypot(dx, dy)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("route", choices=ROUTES.keys())
    p.add_argument("--speed", type=float, default=60.0, help="km/h")
    p.add_argument("--scale", type=float, default=3.0, help="재생 배속")
    p.add_argument("--tick", type=float, default=1.0, help="초 단위 갱신 간격")
    args = p.parse_args()

    adb = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk")), "platform-tools", "adb")
    step = args.speed / 3.6 * args.scale * args.tick
    pts = ROUTES[args.route]
    for a, b in zip(pts, pts[1:]):
        seg = dist_m(a, b)
        n = max(1, int(seg / step))
        for i in range(n):
            t = i / n
            lat, lng = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
            subprocess.run([adb, "emu", "geo", "fix", f"{lng:.6f}", f"{lat:.6f}"], stdout=subprocess.DEVNULL)
            print(f"{lat:.5f},{lng:.5f}", flush=True)
            time.sleep(args.tick)
    last = pts[-1]
    subprocess.run([adb, "emu", "geo", "fix", f"{last[1]:.6f}", f"{last[0]:.6f}"], stdout=subprocess.DEVNULL)
    print("arrived")


if __name__ == "__main__":
    main()
