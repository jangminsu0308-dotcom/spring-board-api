#!/bin/bash
# VM의 네이티브 MySQL(springdb)을 매일 덤프해 로컬에 보관한다.
# 이 프로젝트는 MySQL을 컨테이너화하지 않고 VM에 실제 운영 데이터로 두고 있어(11장 참고),
# 배포 자동화와 별개로 데이터 자체를 지키는 장치가 없었다 — 그 구멍을 메우는 스크립트.
#
# 사용법 (VM에서 한 번만):
#   mkdir -p ~/backups/mysql
#   cp scripts/backup-mysql.sh ~/backup-mysql.sh && chmod +x ~/backup-mysql.sh
#   cat > ~/.mysql-backup.cnf <<'CNF'
#   [client]
#   user=springuser
#   password=springpass
#   CNF
#   chmod 600 ~/.mysql-backup.cnf
#   crontab -e
#   # 추가: 0 3 * * * /home/jang/backup-mysql.sh >> /home/jang/backups/mysql/backup.log 2>&1
#
# 주의: 이 백업은 VM 로컬 디스크에만 저장된다. VM 디스크 자체가 통째로 망가지면
# 백업도 함께 사라지므로, 주기적으로 scp 등으로 VM 바깥(WSL, 외장 스토리지 등)에도
# 복사해두는 것이 안전하다.

set -euo pipefail

BACKUP_DIR="$HOME/backups/mysql"
CNF_FILE="$HOME/.mysql-backup.cnf"
RETENTION_DAYS=14

mkdir -p "$BACKUP_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUT_FILE="$BACKUP_DIR/springdb_${TIMESTAMP}.sql.gz"

mysqldump --defaults-file="$CNF_FILE" \
    --single-transaction \
    --no-tablespaces \
    --routines \
    springdb | gzip > "$OUT_FILE"

echo "[$(date -Iseconds)] backup written: $OUT_FILE ($(du -h "$OUT_FILE" | cut -f1))"

# 오래된 백업 정리
find "$BACKUP_DIR" -name "springdb_*.sql.gz" -mtime "+${RETENTION_DAYS}" -print -delete
