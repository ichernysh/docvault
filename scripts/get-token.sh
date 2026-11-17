#!/usr/bin/env bash
# Usage: scripts/get-token.sh <username> [password]   -> prints decoded access token claims
# Local dev only: uses password grant on the `web` client.
set -euo pipefail
USER_NAME="${1:?username}"; PASS="${2:-password}"
KC="${KC_URL:-http://localhost:8080}"
TOKEN=$(curl -s -d client_id=web -d grant_type=password -d "username=$USER_NAME" -d "password=$PASS" \
  "$KC/realms/docvault/protocol/openid-connect/token" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
if [ "${RAW:-0}" = 1 ]; then echo "$TOKEN"; exit 0; fi
python3 - "$TOKEN" <<'PY'
import sys, json, base64
p = sys.argv[1].split('.')[1]
print(json.dumps(json.loads(base64.urlsafe_b64decode(p + '=' * (-len(p) % 4))), indent=2, ensure_ascii=False))
PY
