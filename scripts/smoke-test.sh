#!/usr/bin/env bash
# End-to-end check of docs-api authorization against a running docker compose stack.
# Usage: scripts/smoke-test.sh   (needs: docker compose up -d, users alice/bob/carol from the realm)
set -uo pipefail
API="${API_URL:-http://localhost:8081}"
DIR="$(cd "$(dirname "$0")" && pwd)"
FAILED=0

token() { RAW=1 "$DIR/get-token.sh" "$1"; }
sub()   { python3 -c "import sys,json,base64;p=sys.argv[1].split('.')[1];print(json.loads(base64.urlsafe_b64decode(p+'='*(-len(p)%4)))['sub'])" "$1"; }
call()  { # call <token> <method> <path> [json-body] -> prints "<status>\n<body>"
  local t="$1" m="$2" path="$3" body="${4:-}"
  if [ -n "$body" ]; then curl -s -w '\n%{http_code}' -X "$m" -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d "$body" "$API$path"
  else curl -s -w '\n%{http_code}' -X "$m" -H "Authorization: Bearer $t" "$API$path"; fi
}
expect() { # expect <label> <expected-status> <actual-status>
  if [ "$2" = "$3" ]; then echo "ok    $1 -> $3"; else echo "FAIL  $1 -> expected $2, got $3"; FAILED=1; fi
}
status() { tail -n1; }

ALICE=$(token alice); BOB=$(token bob); CAROL=$(token carol); BOB_ID=$(sub "$BOB")

expect "no token: list"                         401 "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/documents")"
expect "viewer bob creates a document"          403 "$(call "$BOB" POST /api/documents '{"title":"x"}' | status)"

RESP=$(call "$ALICE" POST /api/documents '{"title":"Contract","content":"v1"}')
expect "editor alice creates a document"        201 "$(echo "$RESP" | status)"
DOC=$(echo "$RESP" | sed '$d' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

expect "bob reads alice's doc (not shared)"     403 "$(call "$BOB" GET "/api/documents/$DOC" | status)"
expect "carol (other tenant) reads it"          404 "$(call "$CAROL" GET "/api/documents/$DOC" | status)"
expect "bob shares alice's doc (not owner)"     403 "$(call "$BOB" PUT "/api/documents/$DOC/shares" "{\"userId\":\"$BOB_ID\",\"permission\":\"READ\"}" | status)"

expect "alice shares doc with bob (READ)"       200 "$(call "$ALICE" PUT "/api/documents/$DOC/shares" "{\"userId\":\"$BOB_ID\",\"permission\":\"READ\"}" | status)"
expect "bob reads the shared doc"               200 "$(call "$BOB" GET "/api/documents/$DOC" | status)"
expect "bob updates it (viewer role)"           403 "$(call "$BOB" PUT "/api/documents/$DOC" '{"title":"hack"}' | status)"
expect "bob deletes it"                         403 "$(call "$BOB" DELETE "/api/documents/$DOC" | status)"
expect "alice updates her doc"                  200 "$(call "$ALICE" PUT "/api/documents/$DOC" '{"title":"Contract","content":"v2"}' | status)"
expect "carol is in another tenant: still 404"  404 "$(call "$CAROL" DELETE "/api/documents/$DOC" | status)"
expect "alice deletes her doc"                  204 "$(call "$ALICE" DELETE "/api/documents/$DOC" | status)"
expect "deleted doc is gone"                    404 "$(call "$ALICE" GET "/api/documents/$DOC" | status)"

[ "$FAILED" = 0 ] && echo "ALL OK" || { echo "SOME CHECKS FAILED"; exit 1; }
