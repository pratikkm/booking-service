#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec python3 "$SCRIPT_DIR/scripts/burst.py" "$BASE_URL"
