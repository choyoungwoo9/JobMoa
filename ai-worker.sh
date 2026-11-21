#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

action="${1:-status}"
pid_file="backend/storage/ai-worker.pid"
worker_id_file="backend/storage/ai-worker.id"
log_file="backend/storage/ai-worker.log"
api_url="${AI_WORKER_API_URL:-http://127.0.0.1:18080}"
worker_class='com.youngwoo.jobmoa.aiqa.worker.AiWorkerMainKt'

worker_pid() {
  [[ -f "$pid_file" ]] || return 1
  local pid
  pid="$(<"$pid_file")"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  ps -p "$pid" -o command= | rg -q "$worker_class" || return 1
  printf '%s\n' "$pid"
}

matching_worker_pids() {
  pgrep -f "$worker_class" 2>/dev/null | while IFS= read -r pid; do
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
    [[ "$command" == *"$worker_class"* ]] && printf '%s\n' "$pid"
  done
}

stop_pid() {
  local pid="$1"
  kill -TERM "$pid" 2>/dev/null || return 0
  for _ in {1..30}; do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 0.2
  done
  kill -KILL "$pid" 2>/dev/null || true
}

stop_all_workers() {
  local pids=()
  if pid="$(worker_pid)"; then pids+=("$pid"); fi
  while IFS= read -r pid; do
    [[ -n "$pid" ]] && pids+=("$pid")
  done < <(matching_worker_pids)
  if ((${#pids[@]})); then
    while IFS= read -r pid; do stop_pid "$pid"; done < <(printf '%s\n' "${pids[@]}" | sort -un)
  fi
}

api_worker_status() {
  curl --fail --silent --show-error "$api_url/api/ai/status" 2>/dev/null || true
}

case "$action" in
  status)
    if pid="$(worker_pid)"; then
      expected_id="$(<"$worker_id_file" 2>/dev/null || true)"
      status="$(api_worker_status)"
      if [[ -n "$expected_id" ]] && printf '%s' "$status" | rg -Fq "\"workerId\":\"$expected_id\""; then
        echo "AI 워커 실행 및 백엔드 연결 확인 (PID $pid, $expected_id)"
      else
        echo "AI 워커 프로세스는 실행 중이지만 새 heartbeat가 확인되지 않습니다. ./ai-worker.sh restart 로 다시 적용하세요. (PID $pid)"
      fi
      [[ -f "$log_file" ]] && tail -n 8 "$log_file"
    else
      status="$(api_worker_status)"
      if printf '%s' "$status" | rg -q '"connected":true'; then
        echo "로컬 워커 프로세스는 없지만 백엔드에 최근 heartbeat가 남아 있습니다. 새 workerId가 확인될 때까지 실행 상태로 보지 않습니다."
      else
        echo "AI 워커가 실행되고 있지 않습니다. ./start.sh 를 다시 실행하거나 ./ai-worker.sh start 로 켜세요."
      fi
    fi
    ;;
  stop)
    stop_all_workers
    rm -f "$pid_file" "$worker_id_file"
    echo "AI 워커 종료 완료."
    ;;
  restart)
    stop_all_workers
    rm -f "$pid_file" "$worker_id_file"
    exec "$0" start
    ;;
  start)
    if pid="$(worker_pid)"; then
      expected_id="$(<"$worker_id_file" 2>/dev/null || true)"
      status="$(api_worker_status)"
      if [[ -n "$expected_id" ]] && printf '%s' "$status" | rg -Fq "\"workerId\":\"$expected_id\""; then
        echo "AI 워커가 이미 실행 중입니다. (PID $pid, $expected_id)"
        exit 0
      fi
      echo "기존 워커의 새 heartbeat를 확인할 수 없어 재시작합니다."
    fi
    java_version="$(java -version 2>&1 | sed -n '1s/.*version \"\([0-9]*\).*/\1/p')"
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
    curl --fail --silent "$api_url/actuator/health" >/dev/null || {
      echo "백엔드가 $api_url 에서 실행 중이어야 합니다." >&2
      exit 1
    }
    ./backend/gradlew -p backend classes copyRuntimeLibs --no-daemon --console=plain
    mkdir -p backend/storage

    # 시작할 때 추적 파일이 빠진 이전 워커도 정리해, 두 코드 버전이 같은 큐를 나눠 받지 않게 한다.
    stop_all_workers
    rm -f "$pid_file" "$worker_id_file"

    base_worker_id="${AI_WORKER_ID:-jobmoa-${HOSTNAME:-mac}}"
    worker_id="${base_worker_id}-$(date +%s)-$$"
    classpath="backend/build/classes/kotlin/main:backend/build/resources/main:backend/build/runtime-libs/*"
    CODEX_BIN="$codex_bin" AI_WORKER_API_URL="$api_url" AI_WORKER_ID="$worker_id" \
      nohup java -cp "$classpath" "$worker_class" </dev/null >>"$log_file" 2>&1 &
    pid=$!
    printf '%s\n' "$pid" >"$pid_file"
    printf '%s\n' "$worker_id" >"$worker_id_file"
    for _ in {1..60}; do
      if ! kill -0 "$pid" 2>/dev/null; then
        rm -f "$pid_file" "$worker_id_file"
        tail -n 30 "$log_file" >&2 || true
        exit 1
      fi
      status="$(api_worker_status)"
      if printf '%s' "$status" | rg -Fq "\"workerId\":\"$worker_id\""; then
        echo "AI 워커 실행 및 새 heartbeat 확인 (PID $pid, 모델 gpt-6-luna, Codex 웹 검색 사용 가능)"
        exit 0
      fi
      sleep 1
    done
    stop_pid "$pid"
    rm -f "$pid_file" "$worker_id_file"
    echo "AI 워커의 새 backend heartbeat를 60초 안에 확인하지 못했습니다. 이전 heartbeat는 실행 성공으로 처리하지 않습니다." >&2
    tail -n 30 "$log_file" >&2 || true
    exit 1
    ;;
  *)
    echo "사용법: ./ai-worker.sh [start|restart|stop|status]" >&2
    exit 2
    ;;
esac
