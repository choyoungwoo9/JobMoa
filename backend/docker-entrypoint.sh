#!/bin/sh
set -eu

# 창을 요구하는 사이트도 컨테이너 내부 가상 화면에서 처리한다.
Xvfb "$DISPLAY" -screen 0 1920x1080x24 -nolisten tcp &
# Java를 PID 1로 실행해 Docker 종료 신호가 Spring에 직접 전달되도록 한다.
exec java -jar /app/app.jar "$@"
