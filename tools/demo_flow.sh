#!/usr/bin/env bash
# Pleos 에뮬레이터(2560x1440)에서 시연 흐름을 탭으로 재생하며 화면 캡처 9장을 docs/screenshots에 저장한다
# 사용: bash tools/demo_flow.sh [shots|play]   (play는 캡처 없이 영상 녹화용으로 같은 흐름만 재생)
# 전제: 에뮬레이터 실행 중, 앱 설치됨. 앱 데이터를 지우고 첫 실행부터 시작한다(위치 권한 허용 포함).
set -euo pipefail
MODE="${1:-shots}"
A="${ADB:-/c/Android/sdk/platform-tools/adb.exe}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/docs/screenshots"
mkdir -p "$OUT"

tap() { "$A" shell input tap "$1" "$2"; "$A" shell "sleep ${3:-2}"; }
shot() { if [ "$MODE" = shots ]; then "$A" exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; fi; }

# 처음 상태로: 현재 사용자(AAOS는 10) 데이터 삭제 후 런처 인텐트로 실행
USER_ID="$("$A" shell am get-current-user | tr -d '\r')"
"$A" shell pm clear --user "$USER_ID" com.ian.evfit > /dev/null
"$A" shell am start --user "$USER_ID" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.ian.evfit/.MainActivity > /dev/null
"$A" shell sleep 4
tap 1705 643          # 위치 권한 [허용]
shot 01_home
tap 1687 376          # [시연 시작]
shot 02_next_stop
tap 1833 582          # [다른 충전소 비교]
shot 03_finder
tap 1280 448          # 1위 망향휴게소 → 상세
shot 04_detail
tap 1389 716 3        # [충전 준비]
shot 05_prep
tap 1033 850 7        # [충전 시작(시연)] → 충전 중
shot 06_charging
"$A" shell sleep 22   # 재생이 끝나면 리포트로 자동 이동
shot 07_report_session
tap 1121 239          # 리포트 [동일 차량]
shot 08_report_model
tap 893 360           # 왼쪽 탭 [지갑]
shot 09_wallet
tap 893 150           # 홈으로 복귀
