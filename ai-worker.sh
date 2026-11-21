#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

action="${1:-status}"
pid_file="backend/storage/ai-worker.pid"
log_file="backend/storage/ai-worker.log"

worker_pid() {
  [[ -f "$pid_file" ]] || return 1
  local pid
  pid="$(<"$pid_file")"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  ps -p "$pid" -o command= | rg -q 'com\.youngwoo\.jobmoa\.aiqa\.worker\.AiWorkerMainKt' || return 1
  printf '%s\n' "$pid"
}

case "$action" in
  status)
    if pid="$(worker_pid)"; then
      echo "AI 워커 실행 중 (PID $pid)"
      [[ -f "$log_file" ]] && tail -n 8 "$log_file"
    else
      echo "AI 워커가 실행되고 있지 않습니다. ./start.sh --ai-worker 또는 ./ai-worker.sh start 로 켜세요."
    fi
    ;;
  stop)
    if pid="$(worker_pid)"; then
      kill "$pid"
      for _ in {1..30}; do
        kill -0 "$pid" 2>/dev/null || break
        sleep 0.2
      done
      if kill -0 "$pid" 2>/dev/null; then kill -9 "$pid"; fi
      echo "AI 워커 종료 완료."
    else
      echo "실행 중인 AI 워커가 없습니다."
    fi
    rm -f "$pid_file"
    ;;
  start)
    if pid="$(worker_pid)"; then
      echo "AI 워커가 이미 실행 중입니다. (PID $pid)"
      exit 0
    fi
    java_version="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
    [[ "${java_version:-0}" =~ ^[0-9]+$ && "$java_version" -ge 21 ]] || {
      echo "AI 워커에는 Java 21 이상이 필요합니다." >&2
      exit 1
    }
    codex_bin="${CODEX_BIN:-}"
    if [[ -z "$codex_bin" ]]; then codex_bin="$(command -v codex || true)"; fi
    if [[ -z "$codex_bin" || ! -x "$codex_bin" ]]; then
      for candidate in \
        "/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex" \
        "/Applications/Codex.app/Contents/Resources/codex"; do
        if [[ -x "$candidate" ]]; then codex_bin="$candidate"; break; fi
      done
    fi
    if [[ -z "$codex_bin" || ! -x "$codex_bin" ]]; then
      echo "Codex CLI를 찾을 수 없습니다. 기본 사이트는 실행되며, CODEX_BIN에 CLI 경로를 지정하면 AI 워커를 켤 수 있습니다." >&2
      exit 1
    fi
    "$codex_bin" login status >/dev/null 2>&1 || {
      echo "Codex CLI 로그인이 필요합니다. 'codex login'으로 로그인한 뒤 다시 실행하세요." >&2
      exit 1
    }
    curl --fail --silent http://127.0.0.1:18080/actuator/health >/dev/null || {
      echo "백엔드가 127.0.0.1:18080에서 실행 중이어야 합니다." >&2
      exit 1
    }
    ./backend/gradlew -p backend classes copyRuntimeLibs --no-daemon --console=plain
    mkdir -p backend/storage
    classpath="backend/build/classes/kotlin/main:backend/build/resources/main:backend/build/runtime-libs/*"
    CODEX_BIN="$codex_bin" AI_WORKER_API_URL="${AI_WORKER_API_URL:-http://127.0.0.1:18080}" \
      nohup java -cp "$classpath" com.youngwoo.jobmoa.aiqa.worker.AiWorkerMainKt \
      </dev/null >>"$log_file" 2>&1 &
    pid=$!
    printf '%s\n' "$pid" >"$pid_file"
    for _ in {1..60}; do
      if ! kill -0 "$pid" 2>/dev/null; then
        rm -f "$pid_file"
        tail -n 30 "$log_file" >&2 || true
        exit 1
      fi
      if curl --fail --silent http://127.0.0.1:18080/api/ai/status | rg -q '"connected":true'; then
        echo "AI 워커 실행 중 (PID $pid, 모델 gpt-6-luna, Codex 웹 검색 사용 가능)"
        exit 0
      fi
      sleep 1
    done
    echo "AI 워커 프로세스는 실행됐지만 백엔드 연결 확인이 늦어지고 있습니다. ./ai-worker.sh status 로 확인하세요."
    ;;
  *)
    echo "사용법: ./ai-worker.sh [start|stop|status]" >&2
    exit 2
    ;;
esac
