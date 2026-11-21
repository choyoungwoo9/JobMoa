#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./ai-worker.sh stop || true
source scripts/ports.sh
command -v lsof >/dev/null || { echo "lsof가 필요합니다." >&2; exit 1; }
if command -v docker >/dev/null && docker info >/dev/null 2>&1; then
  docker compose down --timeout 5 --remove-orphans
fi
jobmoa_release_ports
echo "JobMoa 종료 완료. DB와 캡처 볼륨은 보존했습니다."
