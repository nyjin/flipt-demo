#!/usr/bin/env bash
#
# Seeds the GrowthBook scenario features (so /api/growthbook/{variant,targeted,rollout}
# can be demonstrated) via the GrowthBook REST API, mirroring the Flipt git-native
# flags. Flipt gets these from config/<env>/features.yaml automatically; GrowthBook
# has no git config, so this script is the GrowthBook equivalent of that YAML.
#
# Creates (all enabled in $GROWTHBOOK_ENV):
#   - ui-theme        (string)  experiment 50/50 control|treatment   → /variant
#   - premium-feature (boolean) force true when tier == premium      → /targeted
#   - gradual-rollout (boolean) rollout 50% (hash by id)             → /rollout
# Then creates an SDK Connection and prints its client key for .env.
#
# One-time bootstrap (manual, GrowthBook has no API to create the first admin):
#   1. docker compose up -d mongo growthbook
#   2. Open http://localhost:3000 → create the admin account/organization
#   3. Settings → API Keys → create a *Secret* key (full access)
#   4. Note your environment name (default GrowthBook org has "production")
#
# Usage:
#   GROWTHBOOK_SECRET=secret_abc... ./scripts/seed-growthbook.sh
#   GROWTHBOOK_SECRET=... GROWTHBOOK_ENV=dev GROWTHBOOK_API_HOST=http://localhost:3100 ./scripts/seed-growthbook.sh
#
set -euo pipefail

API_HOST="${GROWTHBOOK_API_HOST:-http://localhost:3100}"
SECRET="${GROWTHBOOK_SECRET:-}"
ENV="${GROWTHBOOK_ENV:-production}"
OWNER="${GROWTHBOOK_OWNER:-}"

if [[ -z "$SECRET" ]]; then
  echo "ERROR: set GROWTHBOOK_SECRET (Settings → API Keys → create a Secret key)." >&2
  echo "       See the bootstrap steps at the top of this script." >&2
  exit 1
fi

base="${API_HOST%/}/api/v1"

# POST JSON to a path; on non-2xx, print the response body and fail.
post() {
  local path="$1" body="$2" resp http
  resp="$(curl -sS -w $'\n%{http_code}' -X POST "$base$path" \
    -H "Authorization: Bearer $SECRET" -H 'Content-Type: application/json' -d "$body")"
  http="$(printf '%s' "$resp" | tail -n1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  if [[ "$http" != 2* ]]; then
    echo "  ✗ $path -> HTTP $http" >&2
    echo "$body" | sed 's/^/    /' >&2
    return 1
  fi
  printf '%s' "$body"
}

feature() {
  local id="$1" body="$2"
  echo "• feature: $id"
  # Create; if it already exists (409/400), update instead so the script is re-runnable.
  if ! post "/features" "$body" >/dev/null 2>&1; then
    echo "  (이미 존재하거나 생성 실패 → 업데이트 시도: POST /features/$id)"
    post "/features/$id" "$body" >/dev/null
  fi
  echo "  ✓ ok"
}

echo "GrowthBook 시드 → $base (env=$ENV)"

feature ui-theme "$(cat <<JSON
{ "id":"ui-theme","owner":"$OWNER","valueType":"string","defaultValue":"control",
  "description":"Multivariate demo — control/treatment (mirrors Flipt ui-theme)",
  "environments": { "$ENV": { "enabled": true, "rules": [
    { "type":"experiment", "condition":"{}", "trackingKey":"ui-theme", "hashAttribute":"id", "coverage":1,
      "values":[ {"value":"control","weight":0.5,"name":"control"},
                 {"value":"treatment","weight":0.5,"name":"treatment"} ] }
  ] } } }
JSON
)"

feature premium-feature "$(cat <<JSON
{ "id":"premium-feature","owner":"$OWNER","valueType":"boolean","defaultValue":"false",
  "description":"Segment targeting — on for tier=premium (mirrors Flipt premium-feature)",
  "environments": { "$ENV": { "enabled": true, "rules": [
    { "type":"force", "value":"true", "condition":"{\"tier\":\"premium\"}" }
  ] } } }
JSON
)"

feature gradual-rollout "$(cat <<JSON
{ "id":"gradual-rollout","owner":"$OWNER","valueType":"boolean","defaultValue":"false",
  "description":"Percentage rollout — 50% by id (mirrors Flipt gradual-rollout)",
  "environments": { "$ENV": { "enabled": true, "rules": [
    { "type":"rollout", "value":"true", "coverage":0.5, "hashAttribute":"id" }
  ] } } }
JSON
)"

echo "• SDK Connection: flipt-demo-backend"
conn="$(post "/sdk-connections" "{\"name\":\"flipt-demo-backend\",\"language\":\"java\",\"environment\":\"$ENV\"}")"
key="$(printf '%s' "$conn" | python3 -c 'import sys,json
d=json.load(sys.stdin)
c=d.get("sdkConnection",d)
print(c.get("key") or c.get("clientKey") or "")')"

echo
if [[ -n "$key" ]]; then
  echo "✓ 완료. 아래 클라이언트 키를 .env 에 넣고 백엔드를 재기동하세요:"
  echo
  echo "    GROWTHBOOK_CLIENT_KEY=$key"
  echo
  echo "    docker compose up -d --build backend   # 또는 ./gradlew bootRun (로컬)"
else
  echo "⚠ SDK Connection 응답에서 client key를 못 찾았습니다. 응답 원문:" >&2
  printf '%s\n' "$conn" >&2
  echo "  GrowthBook UI(Settings → SDK Connections)에서 기존 연결의 Client Key를 복사해도 됩니다." >&2
fi
