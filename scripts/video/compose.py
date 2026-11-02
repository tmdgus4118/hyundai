"""시연 영상 합성: 슬라이드(PIL) + 에뮬레이터 녹화(ffmpeg) + 내레이션/대화(edge-tts)."""
import asyncio
import json
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import script  # noqa: E402

import edge_tts  # noqa: E402

W, H, FPS = 1920, 1080, 30
WORK = os.path.join(HERE, "build")
os.makedirs(WORK, exist_ok=True)
FONT_DIR = os.path.expanduser("~/Library/Fonts")
F = lambda w, s: ImageFont.truetype(os.path.join(FONT_DIR, f"Pretendard-{w}.otf"), s)  # noqa: E731

NAVY0, NAVY1 = (6, 12, 22), (10, 30, 56)
ACTIVE = (0, 170, 210)
WHITE = (244, 246, 248)
SUB = (160, 176, 195)
DIM = (92, 110, 132)
LINE = (36, 52, 72)

# 녹화 클립: 앱 영역만 잘라 사용 (2560x1440 중 앱 창)
CROP = "crop=1680:1210:855:90"
SCREEN_W = 1100
SCREEN_H = round(SCREEN_W * 1210 / 1680 / 2) * 2
SCREEN_X, SCREEN_Y = 740, (H - SCREEN_H) // 2 + 10
VID_LAG = -0.9  # 로그 시각 대비 녹화 영상 시각 보정(측정값: 영상이 0.9초 늦게 보임)
GAP, LEAD, TAIL = 0.35, 0.5, 0.8


def sh(*a):
    subprocess.run(a, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def dur(path):
    return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                                capture_output=True, text=True).stdout.strip())


EVENTS = {k: json.load(open(os.path.join(HERE, f"clip{k}.events.json"), encoding="utf8")) for k in "ABCD"}


def spoken(clip, kw):
    for s in EVENTS[clip]["spoken"]:
        if kw in s["text"]:
            return s
    raise KeyError(f"{clip}:{kw}")


# ── 1. 음성 ───────────────────────────────────────────
async def tts(text, voice, out):
    if not os.path.exists(out):
        await edge_tts.Communicate(text, voice, rate="+3%").save(out)


async def build_audio():
    plan = []
    for seg in script.SEGMENTS:
        items = []
        for i, (who, text) in enumerate(seg["lines"]):
            anchor = None
            if text.startswith("@spoken:"):
                _, clip, kw = text.split(":", 2)
                sp = spoken(clip, kw)
                anchor = (clip, sp["t"])
                show = sp["text"]
                text = script.speakable(sp["text"])
            else:
                show = text
            out = os.path.join(WORK, f"{seg['id']}_{i}_{who}.mp3")
            await tts(text, script.VOICES[who], out)
            items.append(dict(who=who, text=text, show=show, file=out, anchor=anchor))
        plan.append(dict(seg=seg, items=items))
    return plan


def mix_segment_audio(p):
    """줄마다 간격을 두고 이어 붙이고 각 줄의 시작 시각을 기록한다."""
    t = LEAD
    inputs, filters = [], []
    for i, it in enumerate(p["items"]):
        it["start"] = t
        d = dur(it["file"])
        inputs += ["-i", it["file"]]
        filters.append(f"[{i}:a]aresample=48000,adelay={int(t * 1000)}:all=1[a{i}]")
        t += d + GAP
    total = t - GAP + TAIL
    p["duration"] = total
    out = os.path.join(WORK, f"{p['seg']['id']}_audio.wav")
    mix = "".join(f"[a{i}]" for i in range(len(p["items"])))
    filters.append(f"{mix}amix=inputs={len(p['items'])}:normalize=0,apad,atrim=0:{total:.3f},aformat=channel_layouts=mono[out]")
    sh("ffmpeg", "-y", *inputs, "-filter_complex", ";".join(filters), "-map", "[out]", "-ar", "48000", out)
    p["audio"] = out


# ── 2. 슬라이드 배경 ───────────────────────────────────
def wrap(draw, text, font, width):
    lines, cur = [], ""
    for word in text.split(" "):
        cand = (cur + " " + word).strip()
        if draw.textlength(cand, font=font) <= width:
            cur = cand
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def slide(idx, total, seg, dialog):
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):  # 세로 그라디언트
        k = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(NAVY0[c] + (NAVY1[c] - NAVY0[c]) * k) for c in range(3)))

    # 브랜드 헤더
    x0 = 80
    d.rounded_rectangle([x0, 70, x0 + 64, 134], 16, fill=(0, 44, 95))
    d.text((x0 + 32, 101), "?", font=F("Bold", 40), fill=WHITE, anchor="mm")
    d.ellipse([x0 + 48, 74, x0 + 58, 84], fill=ACTIVE)
    d.text((x0 + 84, 68), "저거뭐야", font=F("Bold", 34), fill=WHITE)
    d.text((x0 + 84, 110), "창밖 가이드 · 현대자동차그룹 차량용 앱 개발 공모전", font=F("Regular", 18), fill=SUB)

    # 섹션 라벨·제목
    d.text((x0, 230), seg["label"], font=F("SemiBold", 24), fill=ACTIVE)
    y = 272
    for line in wrap(d, seg["title"], F("Bold", 52), 610):
        d.text((x0, y), line, font=F("Bold", 52), fill=WHITE)
        y += 66
    y += 26
    for line in wrap(d, seg["caption"], F("Medium", 23), 600):
        d.text((x0, y), line, font=F("Medium", 23), fill=SUB)
        y += 36

    # 대화 말풍선 (운전자 / 차량)
    y += 28
    for who, show in dialog:
        tag = "운전자" if who == "D" else "차량 음성"
        color = (226, 220, 211) if who == "D" else ACTIVE
        font = F("Medium", 22)
        lines = wrap(d, f"“{show}”", font, 540)
        h = 44 + 32 * len(lines)
        d.rounded_rectangle([x0, y, x0 + 600, y + h], 18, fill=(16, 32, 52), outline=(40, 62, 88))
        d.text((x0 + 22, y + 12), tag, font=F("SemiBold", 17), fill=color)
        for i, l in enumerate(lines):
            d.text((x0 + 22, y + 36 + 32 * i), l, font=font, fill=WHITE)
        y += h + 14

    # 진행 막대
    bw, gap, by = 44, 8, H - 120
    for i in range(total):
        d.rounded_rectangle([x0 + i * (bw + gap), by, x0 + i * (bw + gap) + bw, by + 6], 3,
                            fill=ACTIVE if i == idx else (52, 70, 92))
    d.text((x0, H - 88), "Pleos Connect Emulator v2.1.0에서 실제 구동한 화면 · 음성은 시연용 합성 음성",
           font=F("Regular", 17), fill=DIM)

    # 차량 디스플레이 프레임
    pad = 14
    d.rounded_rectangle([SCREEN_X - pad, SCREEN_Y - pad, SCREEN_X + SCREEN_W + pad, SCREEN_Y + SCREEN_H + pad], 26,
                        fill=(3, 6, 10), outline=LINE, width=2)
    d.text((SCREEN_X + SCREEN_W, SCREEN_Y - pad - 30), "Pleos Connect 앱 화면", font=F("Medium", 17), fill=DIM, anchor="ra")
    out = os.path.join(WORK, f"slide_{seg['id']}.png")
    img.save(out)
    return out


# ── 3. 화면 구간 선택 ──────────────────────────────────
def clip_window(p):
    seg = p["seg"]
    clip, key = seg["clip"]
    anchors = [(it["anchor"][1], it["start"]) for it in p["items"] if it["anchor"]]
    if anchors:  # 앱이 말하기 시작한 순간 = 차량 음성 줄이 시작하는 순간
        t_event, a_start = anchors[0]
        start = t_event - VID_LAG - a_start
    else:
        start = {"idle": 0.0, "sdk": 4.6, "journey": 2.6}.get(key, 0.0)
        if key == "end":
            start = spoken("C", "오른쪽에 지나친")["t"] - VID_LAG + 0.6
    if key == "idle":
        return clip, None  # 정지 화면
    return clip, max(0.0, start)


def render_segment(i, total, p):
    seg = p["seg"]
    dialog = [(it["who"], it["show"]) for it in p["items"] if it["who"] in ("D", "C")]
    bg = slide(i, total, seg, dialog)
    clip, start = clip_window(p)
    src = os.path.join(HERE, f"clip{clip}.mp4")
    D = p["duration"]
    out = os.path.join(WORK, f"seg_{i:02d}.mp4")
    if start is None:
        vin = ["-ss", "1.0", "-i", src]
        vchain = f"[1:v]{CROP},scale={SCREEN_W}:{SCREEN_H},trim=end_frame=1,loop=-1:1,fps={FPS},trim=duration={D:.3f},setpts=PTS-STARTPTS[v]"
    else:
        vin = ["-ss", f"{start:.3f}", "-i", src]
        vchain = f"[1:v]{CROP},scale={SCREEN_W}:{SCREEN_H},fps={FPS},tpad=stop_mode=clone:stop_duration={D:.3f},trim=duration={D:.3f},setpts=PTS-STARTPTS[v]"
    sh("ffmpeg", "-y", "-loop", "1", "-framerate", str(FPS), "-i", bg, *vin, "-i", p["audio"],
       "-filter_complex", f"{vchain};[0:v][v]overlay={SCREEN_X}:{SCREEN_Y}:shortest=0,trim=duration={D:.3f},format=yuv420p[o]",
       "-map", "[o]", "-map", "2:a", "-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "19",
       "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-ac", "1", "-t", f"{D:.3f}", out)
    return out


def main():
    plan = asyncio.run(build_audio())
    for p in plan:
        mix_segment_audio(p)
    segs = [render_segment(i, len(plan), p) for i, p in enumerate(plan)]
    lst = os.path.join(WORK, "concat.txt")
    open(lst, "w").write("".join(f"file '{s}'\n" for s in segs))
    final = os.path.join(HERE, "저거뭐야_시연영상.mp4")
    sh("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", "-movflags", "+faststart", final)
    t = 0.0
    report = []
    for p in plan:
        report.append(dict(id=p["seg"]["id"], at=round(t, 1), dur=round(p["duration"], 1),
                           lines=[(it["who"], it["show"]) for it in p["items"]]))
        t += p["duration"]
    json.dump(report, open(os.path.join(HERE, "timeline.json"), "w", encoding="utf8"), ensure_ascii=False, indent=1)
    print(final, round(t, 1))


if __name__ == "__main__":
    main()
