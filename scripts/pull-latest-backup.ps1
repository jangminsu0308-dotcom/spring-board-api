# VM(192.168.1.72)의 최신 MySQL 백업을 Windows로 가져온다.
# pull-latest-backup.sh(WSL/bash용)와 같은 역할이지만, Windows 작업 스케줄러가
# 직접 실행할 수 있도록 PowerShell로 작성했다 — WSL은 VirtualBox와 하이퍼바이저가
# 충돌해 VM 네트워크에 못 붙는 경우가 있었지만(13장 사고 참고), Windows 자체의
# OpenSSH 클라이언트는 VirtualBox와 같은 물리 LAN에 있어 문제없이 붙는다.
#
# 사용법 (수동 실행):
#   .\pull-latest-backup.ps1
#
# 작업 스케줄러 등록 (관리자 권한 불필요, 매일 오전 9시):
#   1) 이 파일을 네이티브 Windows 경로로 복사 (WSL 경로가 아니라):
#        Copy-Item "\\wsl$\Ubuntu\home\<사용자명>\projects\maven-demo\demo\scripts\pull-latest-backup.ps1" `
#            "$env:USERPROFILE\scripts\pull-latest-backup.ps1" -Force
#   2) 작업 등록:
#        $Action = New-ScheduledTaskAction -Execute "powershell.exe" `
#            -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$env:USERPROFILE\scripts\pull-latest-backup.ps1`""
#        $Trigger = New-ScheduledTaskTrigger -Daily -At 9am
#        Register-ScheduledTask -TaskName "SpringBoardApi-PullBackup" -Action $Action -Trigger $Trigger `
#            -Description "VM MySQL 백업을 Windows로 오프사이트 복사"

$VMHost = "jang@192.168.1.72"
$DestDir = "$env:USERPROFILE\Desktop\vm-backups"

New-Item -ItemType Directory -Force -Path $DestDir | Out-Null

$Latest = (ssh $VMHost "ls -t ~/backups/mysql/springdb_*.sql.gz 2>/dev/null | head -1").Trim()
if ([string]::IsNullOrEmpty($Latest)) {
    Write-Output "VM에 백업 파일이 없습니다"
    exit 1
}

$BaseName = Split-Path $Latest -Leaf
$DestPath = Join-Path $DestDir $BaseName

if (Test-Path $DestPath) {
    Write-Output "이미 가져온 최신 백업입니다: $BaseName"
    exit 0
}

scp "${VMHost}:${Latest}" "$DestDir\"
Write-Output "$(Get-Date -Format o) 가져옴: $BaseName -> $DestDir"

# 로컬에도 오래된 사본이 쌓이지 않도록 30일 지난 것은 정리
Get-ChildItem $DestDir -Filter "springdb_*.sql.gz" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } |
    ForEach-Object {
        Write-Output "정리: $($_.Name)"
        Remove-Item $_.FullName
    }
