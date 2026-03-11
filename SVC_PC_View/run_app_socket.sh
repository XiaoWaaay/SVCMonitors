#!/usr/bin/env bash
set -euo pipefail

APP_PORT="${1:-8080}"
WEB_PORT="${2:-0}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH"
  exit 1
fi

adb forward "tcp:${APP_PORT}" "tcp:${APP_PORT}" >/dev/null

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python3 "${SCRIPT_DIR}/server/app.py" --no-bridge --app-socket "127.0.0.1:${APP_PORT}" --port "${WEB_PORT}"
