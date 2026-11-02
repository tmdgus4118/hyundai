"""시연 영상 대본. N=내레이터(현수), D=운전자(인준), C=차량 음성(선희).
C 대사는 녹화 중 앱이 실제로 말한 문장으로 채운다(@spoken:클립:키워드)."""

VOICES = {"N": "ko-KR-HyunsuMultilingualNeural", "D": "ko-KR-InJoonNeural", "C": "ko-KR-SunHiNeural"}

SEGMENTS = [
    dict(id="s1", label="왜 필요한가", title="\"방금 저거 뭐였지?\"", clip=("A", "idle"),
         caption="운전 중엔 검색할 수 없고 · 궁금해질 땐 이미 지나친 뒤",
         lines=[("N", "운전하다 보면 차창 밖으로 궁금한 것들이 수없이 지나갑니다. 하지만 운전 중에는 검색할 수 없고, 궁금해지는 순간에는 이미 지나친 뒤라, 이름조차 모릅니다.")]),
    dict(id="s2", label="저거뭐야", title="차가 지나온 길을 기억합니다", clip=("A", "idle"),
         caption="Pleos Connect 창밖 가이드 · 지나온 궤적과 진행 방향으로 좌·우 판정",
         lines=[("N", "저거뭐야는 플레오스 커넥트 차량을 위한 창밖 가이드입니다. 차가 지나온 궤적과 진행 방향을 기억해서, 방금 왼쪽이나 오른쪽에 무엇을 지나쳤는지 음성으로 답합니다.")]),
    dict(id="s3", label="기술 활용", title="Pleos SDK 5종 실제 연동", clip=("A", "sdk"),
         caption="Vehicle · FusedLocation · NaviHelper · TextToSpeech · SpeechToText",
         lines=[("N", "비히클, 퓨즈드 로케이션, 내비헬퍼, 텍스트 투 스피치, 스피치 투 텍스트. 다섯 가지 플레오스 에스디케이를 모두 연결했습니다. 지금부터 보시는 화면은, 플레오스 커넥트 에뮬레이터에서 실제로 구동한 화면입니다.")]),
    dict(id="s4", label="1. 자동 해설", title="곧 보일 명소를 먼저 알려 줍니다", clip=("B", "auto"),
         caption="실제 GPS 경로 → FusedLocation · 차선 변경 중엔 침묵 · 고속 주행 중엔 짧게",
         lines=[("N", "올림픽대로를 달리면, 앞으로 창밖에 나타날 명소를 먼저 알려 줍니다."),
                ("C", "@spoken:B:동작대교"),
                ("N", "방향지시등을 켜고 차선을 바꾸는 중에는 말을 걸지 않고, 빠르게 달릴 때는 한 문장으로 짧게 답합니다.")]),
    dict(id="s5", label="2. 방금 지나친 것", title="\"방금 왼쪽에 지나간 거 뭐야?\"", clip=("B", "left"),
         caption="지나온 궤적의 구간별 최근접점으로 좌·우와 경과 시간을 계산",
         lines=[("D", "방금 왼쪽에 지나간 거 뭐야?"),
                ("C", "@spoken:B:지나친 곳은"),
                ("N", "휴대폰은 지금 위치만 알지만, 저거뭐야는 지나온 궤적을 따라 이미 지나친 곳과 그 방향까지 계산합니다.")]),
    dict(id="s6", label="3. 거기 가자", title="궁금증이 방문으로 이어집니다", clip=("B", "go"),
         caption="NaviHelper로 목적지 설정 · 안내 중이면 경유지로 추가",
         lines=[("D", "거기 가자"),
                ("C", "@spoken:B:길 안내"),
                ("N", "내비헬퍼로 플레오스 내비게이션에 목적지를 바로 설정하고, 이미 길 안내 중이라면 경유지로 추가합니다.")]),
    dict(id="s7", label="4. 도심 주행", title="정면은 정면이라고 말합니다", clip=("C", "ahead"),
         caption="좌·우가 애매한 정면 명소는 \"정면에\" · 고개를 돌려 찾지 않도록",
         lines=[("C", "@spoken:C:숭례문이 보여요"),
                ("N", "좌우가 애매한 정면의 명소는 정면이라고 안내해서, 운전자가 고개를 돌려 찾지 않게 합니다.")]),
    dict(id="s7b", label="4. 도심 주행", title="\"아까 오른쪽에 있던 건물 뭐였지?\"", clip=("C", "ago"),
         caption="\"아까\"는 20분 전까지 기억 · 바로 옆을 지난 곳을 우선",
         lines=[("N", "조금 전에 지나친 곳도 물어볼 수 있습니다."),
                ("D", "아까 오른쪽에 있던 건물 뭐였지?"),
                ("C", "@spoken:C:오른쪽에 지나친")]),
    dict(id="s8", label="5. 정차하면", title="오늘 지나온 곳", clip=("D", "journey"),
         caption="P단·주행 규제 해제 시에만 상세 화면 · 위치는 차 밖으로 보내지 않음",
         lines=[("N", "기어를 피 단으로 바꾸면 주행 규제가 풀리고, 오늘 물어본 곳들이 모여 있습니다. 설명을 다시 듣거나, 다음에 갈 곳으로 바로 길 안내를 받을 수 있습니다. 위치 정보는 차 안에서만 계산하고, 밖으로 보내지 않습니다.")]),
    dict(id="s9", label="저거뭐야", title="궁금한 게 지나가면, 그냥 물어보세요", clip=("C", "end"),
         caption="지자체·관광지 해설 팩 · 휴게소·관광지 방문 제휴 · 운전자 과금 없음",
         lines=[("N", "지자체와 관광지 해설 팩, 휴게소와 관광지 방문 제휴로, 운전자에게 비용을 받지 않고도 서비스를 키울 수 있습니다. 저거뭐야. 궁금한 게 지나가면, 그냥 물어보세요.")]),
]


def speakable(text: str) -> str:
    """앱 문장의 숫자를 내레이션용 한글 발음으로 바꾼다(대본 규칙)."""
    import re

    digits = "영일이삼사오육칠팔구"

    def num(n: int) -> str:
        if n == 0:
            return "영"
        out = ""
        for unit, name in ((10000, "만"), (1000, "천"), (100, "백"), (10, "십")):
            q = n // unit
            if q:
                out += ("" if q == 1 else num(q) if unit == 10000 else digits[q]) + name
                n %= unit
        return out + (digits[n] if n else "")

    text = re.sub(r"(\d+)\.(\d+)킬로미터", lambda m: f"{num(int(m.group(1)))} 점 {''.join(digits[int(c)] for c in m.group(2))} 킬로미터", text)
    text = text.replace("63빌딩", "육삼빌딩")
    return re.sub(r"\d+", lambda m: num(int(m.group(0))), text)
