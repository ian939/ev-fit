#!/usr/bin/env bash
# Pleos 에뮬레이터(2560x1440)에서 시연 흐름(v2 경로 지도→탭→상세→충전 준비→충전→리포트)을 탭으로 재생하며 화면 캡처를 docs/screenshots에 저장한다
# 사용: bash tools/demo_flow.sh [shots|play]   (play는 캡처 없이 영상 녹화용으로 같은 흐름만 재생)
# 전제: 에뮬레이터 실행 중, 앱 설치됨. 앱 데이터를 지우고 첫 실행부터 시작한다(위치 권한 허용 포함).
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash가 기기 경로를 윈도 경로로 바꾸지 않게
MODE="${1:-shots}"
A="${ADB:-/c/Android/sdk/platform-tools/adb.exe}"
OUT="$(cd "$(dirname "$0")/.." && pwd -W)/docs/screenshots"
mkdir -p "$OUT"
if [ "$MODE" = shots ]; then rm -f "$OUT"/*.png; fi

tap() { "$A" shell input tap "$1" "$2"; "$A" shell sleep "${3:-2}"; }
shot() { if [ "$MODE" = shots ]; then "$A" exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; fi; }

# 처음 상태로: 현재 사용자(AAOS는 10) 데이터 삭제 후 런처 인텐트로 실행
USER_ID="$("$A" shell am get-current-user | tr -d '\r')"
"$A" shell pm clear --user "$USER_ID" com.ian.evfit > /dev/null
"$A" shell am start --user "$USER_ID" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.ian.evfit/.MainActivity > /dev/null
"$A" shell sleep 4
tap 1705 643          # 위치 권한 [허용]
tap 1827 209 4        # 지도 오른쪽 위 [시연 시작] → 경로·회랑·FIT 추천
shot 01_route_fit
tap 2148 357          # [경로 우선]
shot 02_route_onroute
tap 2291 357          # [최저가]
shot 03_route_price
tap 2148 357          # [경로 우선]으로 돌아가 1위(망향휴게소) 상세
tap 2243 1192
shot 04_detail
tap 2040 1088 6       # 상세 [충전 준비] → 차가 망향까지 이동 → 준비 화면
shot 05_prep
tap 2164 1215 9       # [충전 시작(시연)] → 충전 중
shot 06_charging
"$A" shell sleep 20   # 재생이 끝나면 리포트로 자동 이동
shot 07_report_session
tap 2202 202          # 리포트 [같은 차종]
shot 08_report_model
tap 2400 202          # 리포트 [이번 달]
shot 09_report_month
tap 905 352           # 레일 [지갑]
shot 10_wallet
tap 905 154           # 레일 [경로]로 복귀
