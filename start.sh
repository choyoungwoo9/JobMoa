#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
start_ai_worker=false
if [[ "${1:-}" == "--ai-worker" && $# == 1 ]]; then
  start_ai_worker=true
elif [[ $# -gt 0 ]]; then
  echo "사용법: ./start.sh [--ai-worker]" >&2
  exit 2
fi
command -v docker >/dev/null || { echo "Docker가 필요합니다." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker Desktop을 먼저 실행하세요." >&2; exit 1; }
./stop.sh
trap 'echo "시작에 실패했습니다. docker compose logs --tail=80 로 원인을 확인하세요." >&2' ERR
docker compose up --build -d --wait --wait-timeout 180
if [[ "$start_ai_worker" == true ]]; then
  if ! ./ai-worker.sh start; then
    echo "화면과 백엔드는 실행됐고, AI 워커는 시작되지 않았습니다. ./ai-worker.sh status 로 확인하세요."
  fi
fi
echo "화면: http://127.0.0.1:15173"
echo "백엔드: http://127.0.0.1:18080 / MySQL: 127.0.0.1:13306"
if [[ "$start_ai_worker" == true ]]; then echo "AI 질문: 워커 시작을 요청했습니다. 화면에서 연결 상태를 확인하세요."; fi
