#!/bin/bash
# VM에만 쌓이는 백업을 VM 바깥(이 스크립트를 실행하는 쪽, 보통 WSL)으로도 한 벌 복사해둔다.
# VM 로컬 디스크에만 있으면 "VM 디스크 자체가 죽는" 시나리오에는 백업도 함께 사라진다.
#
# 완전 자동화는 하지 않는다 — WSL은 개발 환경이라 상시 켜져 있는 서버가 아니라서,
# 여기에 크론을 걸어도 컴퓨터가 꺼져 있으면 그냥 안 돈다. 대신 이 프로젝트를 만질 때마다
# (또는 생각날 때마다) 이 스크립트를 한 번씩 실행하는 것으로 충분하다 — 실제 재해 복구
# 시나리오에서 필요한 건 "최근 며칠 안의 백업 중 하나"이지 "1분 전 백업"이 아니다.
#
# 사용법 (WSL 등 VM에 SSH로 접속 가능한 쪽에서):
#   ./scripts/pull-latest-backup.sh

set -euo pipefail

VM_HOST="jang@192.168.1.72"
DEST_DIR="$HOME/vm-backups/mysql"

mkdir -p "$DEST_DIR"

LATEST=$(ssh "$VM_HOST" 'ls -t ~/backups/mysql/springdb_*.sql.gz 2>/dev/null | head -1')
if [ -z "$LATEST" ]; then
    echo "VM에 백업 파일이 없습니다"
    exit 1
fi

BASENAME=$(basename "$LATEST")
if [ -f "$DEST_DIR/$BASENAME" ]; then
    echo "이미 가져온 최신 백업입니다: $BASENAME"
    exit 0
fi

scp "$VM_HOST:$LATEST" "$DEST_DIR/"
echo "가져옴: $BASENAME -> $DEST_DIR/"

# 로컬에도 오래된 사본이 쌓이지 않도록 30일 지난 것은 정리
find "$DEST_DIR" -name "springdb_*.sql.gz" -mtime +30 -print -delete
