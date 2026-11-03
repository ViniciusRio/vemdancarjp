#!/usr/bin/env bash
# Usage: compare-agenda.sh <base-url> <image>
# Loads deploy/seed/agenda-initial.sql into an empty database, serves it with
# <image> and compares the result with GET <base-url>/api/agenda.
set -uo pipefail

base_url="${1:?usage: compare-agenda.sh <base-url> <image>}"
image="${2:?usage: compare-agenda.sh <base-url> <image>}"
base_url="${base_url%/}"
seed="$(cd "$(dirname "$0")" && pwd)/seed/agenda-initial.sql"
normalize='del(.lastUpdated)'

run_id="compare-agenda-$$"
network="$run_id"
database="$run_id-postgres"
app="$run_id-app"
work_dir="$(mktemp -d)"

cleanup() {
  docker rm --force "$app" "$database" >/dev/null 2>&1
  docker network rm "$network" >/dev/null 2>&1
  rm -rf "$work_dir"
}
trap cleanup EXIT

curl --fail --silent --show-error --max-time 60 "$base_url/api/agenda" >"$work_dir/published.json" || {
  echo "FAIL could not read the published agenda"
  exit 1
}

docker network create "$network" >/dev/null || exit 1
docker run --detach --name "$database" --network "$network" \
  --env POSTGRES_DB=compare --env POSTGRES_USER=compare --env POSTGRES_PASSWORD=compare \
  postgres:17-alpine >/dev/null || exit 1
for ((attempt = 1; attempt <= 60; attempt++)); do
  docker exec "$database" pg_isready --username compare --dbname compare >/dev/null 2>&1 && break
  sleep 1
done

docker run --detach --name "$app" --network "$network" --publish 127.0.0.1::8080 \
  --env "SPRING_DATASOURCE_URL=jdbc:postgresql://$database:5432/compare" \
  --env SPRING_DATASOURCE_USERNAME=compare \
  --env SPRING_DATASOURCE_PASSWORD=compare \
  "$image" >/dev/null || exit 1
port="$(docker port "$app" 8080/tcp | head -n 1 | sed 's/.*://')"

started=false
for ((attempt = 1; attempt <= 60; attempt++)); do
  if curl --fail --silent --output /dev/null --max-time 5 "http://127.0.0.1:$port/api/agenda"; then
    started=true
    break
  fi
  sleep 2
done
if [[ "$started" != "true" ]]; then
  echo "FAIL the comparison image did not start"
  docker logs --tail 30 "$app" 2>&1
  exit 1
fi

docker exec --interactive "$database" psql --quiet --set ON_ERROR_STOP=1 \
  --username compare --dbname compare <"$seed" >/dev/null || {
  echo "FAIL the versioned load did not apply"
  exit 1
}
curl --fail --silent --show-error "http://127.0.0.1:$port/api/agenda" >"$work_dir/expected.json" || exit 1

if ! diff --unified \
  <(jq --sort-keys "$normalize" "$work_dir/expected.json") \
  <(jq --sort-keys "$normalize" "$work_dir/published.json"); then
  echo "Agenda differs from the versioned load"
  exit 1
fi
echo "Agenda matches the versioned load"
