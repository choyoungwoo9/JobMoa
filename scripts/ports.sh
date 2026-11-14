#!/usr/bin/env bash

# JobMoa에 지정한 포트만 정리한다. 다른 프로젝트의 5173/3307은 대상이 아니다.
JOBMOA_PORTS=(15173 18080 13306)

jobmoa_release_ports() {
  local port containers pid command remaining attempt
  for port in "${JOBMOA_PORTS[@]}"; do
    if docker info >/dev/null 2>&1; then
      containers=$(docker ps --filter "publish=$port" --format '{{.ID}}')
      if [[ -n "$containers" ]]; then
        echo "포트 $port 사용 컨테이너 종료"
        while IFS= read -r pid; do
          docker stop --time 3 "$pid" >/dev/null
        done <<< "$containers"
      fi
    fi
    remaining=$(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
    [[ -n "$remaining" ]] || continue
    echo "포트 $port 사용 프로세스 종료: $remaining"
    while IFS= read -r pid; do
      command=$(ps -p "$pid" -o comm= 2>/dev/null || true)
      case "$command" in
        *com.docker*|*Docker.app*)
          echo "Docker 포트 해제가 지연되고 있습니다. Docker 전체 프로세스는 종료하지 않습니다." >&2
          return 1
          ;;
      esac
      kill -TERM "$pid" 2>/dev/null || true
    done <<< "$remaining"
    for ((attempt=0; attempt<15; attempt++)); do
      remaining=$(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
      [[ -n "$remaining" ]] || break
      sleep 0.2
    done
    if [[ -n "$remaining" ]]; then
      while IFS= read -r pid; do
        # 종료 대기 중 새 프로세스가 들어와도 현재 리스너에 대해서만 처리한다.
        command=$(ps -p "$pid" -o comm= 2>/dev/null || true)
        case "$command" in *com.docker*|*Docker.app*) return 1 ;; esac
        kill -KILL "$pid" 2>/dev/null || true
      done <<< "$remaining"
      sleep 0.2
    fi
    if lsof -nP -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "포트 $port 정리에 실패했습니다. 위 프로세스 정보를 확인하세요." >&2
      return 1
    fi
  done
}
