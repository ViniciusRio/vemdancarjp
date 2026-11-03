#!/usr/bin/env bash
# Usage: check-public-surface.sh <base-url>
set -uo pipefail

base_url="${1:?usage: check-public-surface.sh <base-url>}"
base_url="${base_url%/}"
agenda_attempts="${AGENDA_ATTEMPTS:-12}"
agenda_retry_seconds="${AGENDA_RETRY_SECONDS:-5}"
failures=0

request() {
  local method="$1" path="$2"
  local method_args=(--request "$method")
  [[ "$method" == "HEAD" ]] && method_args=(--head)
  curl --silent --path-as-is --output /dev/null --max-time 30 \
    --write-out '%{http_code} %{content_type}' "${method_args[@]}" "$base_url$path"
}

expect_not_found() {
  local method="$1" path="$2" status
  status="$(request "$method" "$path")"
  status="${status%% *}"
  if [[ "$status" != "404" ]]; then
    echo "FAIL $method $path: expected 404, got $status"
    failures=$((failures + 1))
  fi
}

agenda_result=""
for ((attempt = 1; attempt <= agenda_attempts; attempt++)); do
  agenda_result="$(request GET /api/agenda)"
  [[ "${agenda_result%% *}" == "200" ]] && break
  sleep "$agenda_retry_seconds"
done
if [[ "$agenda_result" != "200 application/json"* ]]; then
  echo "FAIL GET /api/agenda: expected 200 with JSON, got ${agenda_result:-no response}"
  failures=$((failures + 1))
fi

expect_not_found GET /api/admin/events
expect_not_found POST /api/admin/events
for path in /api/auth/session /api/auth/csrf /oauth2/authorization/google \
  /login/oauth2/code/google /actuator/health /; do
  expect_not_found GET "$path"
done
for method in POST PUT PATCH DELETE HEAD OPTIONS; do
  expect_not_found "$method" /api/agenda
done
for path in /api/agenda/ '/api/agenda;x=1' /api/agenda/../admin/events; do
  expect_not_found GET "$path"
done

if ((failures > 0)); then
  echo "Public surface check failed for $base_url: $failures problem(s)"
  exit 1
fi
echo "Public surface check passed for $base_url"
