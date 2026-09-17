#!/bin/bash
# VM에서 주기적으로 돌며 컨테이너/앱 상태를 확인하고, 문제가 있을 때만 ntfy.sh로 푸시 알림을 보낸다.
# 지금까지는 배포가 실패하거나 컨테이너가 죽어도 사람이 curl/docker compose ps로 직접
# 확인하기 전까진 아무도 몰랐다 — 이 스크립트가 그 공백을 메운다.
#
# ntfy.sh는 가입 없이 쓸 수 있는 푸시 알림 서비스다. 토픽 이름을 아는 사람은 누구나
# 구독·발행이 가능하므로(공개 저장소에 올라가는 이 스크립트에는 토픽 이름을 적지 않는다),
# 추측하기 어려운 임의의 토픽 이름을 VM에만 있는 별도 파일에 적어둔다.
#
# 사용법 (VM에서 한 번만):
#   cp scripts/healthcheck-notify.sh ~/healthcheck-notify.sh && chmod +x ~/healthcheck-notify.sh
#   echo "<임의로 정한 토픽 이름>" > ~/.ntfy-topic   # 예: sba-alerts-f8cba0c56d97c4dd
#   ntfy 앱(iOS/Android) 또는 https://ntfy.sh/<토픽 이름> 에서 같은 토픽을 구독해두면 폰으로 알림이 온다
#   crontab에 등록: */5 * * * * /home/jang/healthcheck-notify.sh >> /home/jang/healthcheck.log 2>&1

set -uo pipefail

NTFY_TOPIC_FILE="$HOME/.ntfy-topic"
APP_DIR="$HOME/spring-board-api/actions-runner/_work/spring-board-api/spring-board-api"
STATE_FILE="$HOME/.healthcheck-down"
HEALTH_URL="http://127.0.0.1:8080/api/posts"

if [ ! -f "$NTFY_TOPIC_FILE" ]; then
    echo "$(date -Iseconds) $NTFY_TOPIC_FILE 이 없어 알림을 보낼 수 없습니다"
    exit 1
fi
NTFY_TOPIC=$(cat "$NTFY_TOPIC_FILE")

notify() {
    curl -s -H "Title: $1" -H "Priority: high" -d "$2" "https://ntfy.sh/${NTFY_TOPIC}" > /dev/null
}

is_healthy() {
    docker compose -f "$APP_DIR/docker-compose.prod.yml" ps --status running --services 2>/dev/null | grep -q "^app$" \
        && curl -sf -o /dev/null --max-time 5 "$HEALTH_URL"
}

if is_healthy; then
    if [ -f "$STATE_FILE" ]; then
        echo "$(date -Iseconds) 복구 확인, 알림 전송"
        notify "spring-board-api 복구됨" "정상 응답 확인 (${HEALTH_URL})"
        rm -f "$STATE_FILE"
    fi
else
    if [ ! -f "$STATE_FILE" ]; then
        echo "$(date -Iseconds) 장애 감지, 알림 전송"
        notify "spring-board-api 장애" "컨테이너 미실행 또는 API 무응답 (${HEALTH_URL})"
        touch "$STATE_FILE"
    else
        echo "$(date -Iseconds) 장애 지속 중 (이미 알림 보냄, 재알림 안 함)"
    fi
fi
