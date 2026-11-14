#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v docker >/dev/null || { echo "Docker가 필요합니다." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker Desktop을 먼저 실행하세요." >&2; exit 1; }
./stop.sh
trap 'echo "시작에 실패했습니다. docker compose logs --tail=80 로 원인을 확인하세요." >&2' ERR
docker compose up --build -d --wait --wait-timeout 180
echo "화면: http://127.0.0.1:15173"
echo "백엔드: http://127.0.0.1:18080 / MySQL: 127.0.0.1:13306"
