#!/bin/bash
# 매일 쌓이는 mysqldump 백업이 실제로 복원 가능한지 주기적으로 검증한다.
# "백업 파일이 생성됐다"와 "그 백업으로 실제 복구할 수 있다"는 다른 이야기다 —
# 13장에서 겪었듯 mysqldump 자체가 조용히 실패(빈 파일)할 수도 있고,
# gzip이 깨지거나 스키마 일부만 담기는 경우도 있다. 이 스크립트는 최신 백업을
# 별도 테스트용 DB(springdb_restore_test)에 실제로 복원해보고, 최소한의
# 데이터 정합성(테이블 개수, users 레코드 존재)까지 확인한 뒤 그 DB를 지운다.
#
# 사전 준비 (VM에서 한 번, sudo mysql로):
#   GRANT ALL PRIVILEGES ON springdb_restore_test.* TO 'springuser'@'localhost';
#   FLUSH PRIVILEGES;
#   (springuser는 원래 springdb에만 권한이 있어, 검증 전용 DB에도 별도로 권한을 준다 —
#    실제 운영 DB(springdb) 권한은 전혀 넓히지 않는다)
#
# 사용법:
#   cp scripts/verify-backup.sh ~/verify-backup.sh && chmod +x ~/verify-backup.sh
#   crontab에 추가: 0 4 * * 0 /home/jang/verify-backup.sh >> /home/jang/verify-backup.log 2>&1
#   (매일 도는 백업과 겹치지 않게 일요일 새벽 4시, 백업은 3시)

set -uo pipefail

BACKUP_DIR="$HOME/backups/mysql"
CNF_FILE="$HOME/.mysql-backup.cnf"
NTFY_TOPIC_FILE="$HOME/.ntfy-topic"
TEST_DB="springdb_restore_test"

notify() {
    [ -f "$NTFY_TOPIC_FILE" ] || return 0
    curl -s -H "Title: $1" -H "Priority: high" -d "$2" "https://ntfy.sh/$(cat "$NTFY_TOPIC_FILE")" > /dev/null
}

LATEST=$(ls -t "$BACKUP_DIR"/springdb_*.sql.gz 2>/dev/null | head -1)
if [ -z "$LATEST" ]; then
    echo "$(date -Iseconds) 검증할 백업 파일이 없습니다"
    notify "백업 검증 실패" "복원할 백업 파일이 없습니다 (${BACKUP_DIR})"
    exit 1
fi

mysql --defaults-file="$CNF_FILE" -e "DROP DATABASE IF EXISTS ${TEST_DB}; CREATE DATABASE ${TEST_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

if ! zcat "$LATEST" | mysql --defaults-file="$CNF_FILE" "$TEST_DB"; then
    echo "$(date -Iseconds) $(basename "$LATEST") 복원 실패"
    notify "백업 검증 실패" "$(basename "$LATEST") 복원 중 오류 발생"
    mysql --defaults-file="$CNF_FILE" -e "DROP DATABASE IF EXISTS ${TEST_DB};"
    exit 1
fi

TABLE_COUNT=$(mysql --defaults-file="$CNF_FILE" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${TEST_DB}';")
USER_COUNT=$(mysql --defaults-file="$CNF_FILE" -N -e "SELECT COUNT(*) FROM ${TEST_DB}.users;" 2>/dev/null || echo 0)

mysql --defaults-file="$CNF_FILE" -e "DROP DATABASE IF EXISTS ${TEST_DB};"

if [ "$TABLE_COUNT" -lt 4 ] || [ "$USER_COUNT" -lt 1 ]; then
    echo "$(date -Iseconds) $(basename "$LATEST") 복원은 됐지만 데이터가 비정상 (테이블 ${TABLE_COUNT}개, users ${USER_COUNT}행)"
    notify "백업 검증 실패" "$(basename "$LATEST")가 복원은 됐지만 데이터가 비정상입니다 (테이블 ${TABLE_COUNT}개, users ${USER_COUNT}행)"
    exit 1
fi

echo "$(date -Iseconds) 백업 검증 성공: $(basename "$LATEST") (테이블 ${TABLE_COUNT}개, users ${USER_COUNT}행)"
