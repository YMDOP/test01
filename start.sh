#!/usr/bin/env bash
# InterviewForge 启动脚本（macOS / Linux）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$ROOT/InterviewForge/target/InterviewForge-0.0.1-SNAPSHOT.jar"

if [[ ! -f "$JAR" ]]; then
  echo "[错误] 没有找到 $JAR，请先执行 build.ps1 或 mvn clean package 构建。" >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "[错误] 未检测到 Java，请安装 JDK 17 或更高版本。" >&2
  exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)"
if [[ "${JAVA_MAJOR:-0}" -lt 17 ]]; then
  echo "[错误] 当前 Java 版本为 ${JAVA_MAJOR}，本程序需要 17 或更高版本。" >&2
  exit 1
fi

if command -v lsof >/dev/null 2>&1 && lsof -i :8081 >/dev/null 2>&1; then
  echo "[错误] 端口 8081 已被占用。" >&2
  exit 1
fi

echo "正在启动 InterviewForge（Java ${JAVA_MAJOR}）..."
java -Dfile.encoding=UTF-8 -jar "$JAR" --spring.profiles.active=prod &
PID=$!

for _ in $(seq 1 60); do
  sleep 1
  if curl -s -o /dev/null "http://127.0.0.1:8081/api/health"; then
    echo "已启动：http://127.0.0.1:8081"
    if command -v open >/dev/null 2>&1; then open "http://127.0.0.1:8081"; fi
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "http://127.0.0.1:8081" >/dev/null 2>&1 || true; fi
    wait "$PID"
    exit 0
  fi
done

echo "[错误] 启动超时（60 秒）。" >&2
kill "$PID" 2>/dev/null || true
exit 1
