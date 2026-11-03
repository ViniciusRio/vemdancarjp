#!/usr/bin/env bash
# Usage: check-schema-compat.sh <new-image> <current-image>
# Applies the new image's migrations to an empty database, then starts the
# current image on that schema. Never touches the production database.
set -uo pipefail

new_image="${1:?usage: check-schema-compat.sh <new-image> <current-image>}"
current_image="${2:?usage: check-schema-compat.sh <new-image> <current-image>}"
start_attempts="${START_ATTEMPTS:-60}"

run_id="schema-compat-$$"
network="$run_id"
database="$run_id-postgres"
app="$run_id-app"

cleanup() {
  docker rm --force "$app" "$database" >/dev/null 2>&1
  docker network rm "$network" >/dev/null 2>&1
}
trap cleanup EXIT

docker network create "$network" >/dev/null || exit 1
docker run --detach --name "$database" --network "$network" \
  --env POSTGRES_DB=compat --env POSTGRES_USER=compat --env POSTGRES_PASSWORD=compat \
  postgres:17-alpine >/dev/null || exit 1

for ((attempt = 1; attempt <= 60; attempt++)); do
  docker exec "$database" pg_isready --username compat --dbname compat >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$database" pg_isready --username compat --dbname compat >/dev/null 2>&1 || {
  echo "FAIL test database did not start"
  exit 1
}

check_image() {
  local label="$1" image="$2" port status=""
  docker rm --force "$app" >/dev/null 2>&1
  docker run --detach --name "$app" --network "$network" --publish 127.0.0.1::8080 \
    --env "SPRING_DATASOURCE_URL=jdbc:postgresql://$database:5432/compat" \
    --env SPRING_DATASOURCE_USERNAME=compat \
    --env SPRING_DATASOURCE_PASSWORD=compat \
    "$image" >/dev/null || { echo "FAIL $label image could not be started: $image"; return 1; }
  port="$(docker port "$app" 8080/tcp | head -n 1 | sed 's/.*://')"

  for ((attempt = 1; attempt <= start_attempts; attempt++)); do
    if [[ "$(docker inspect --format '{{.State.Running}}' "$app")" != "true" ]]; then
      echo "FAIL $label image exited during startup: $image"
      docker logs --tail 30 "$app" 2>&1
      return 1
    fi
    status="$(curl --silent --output /dev/null --max-time 5 --write-out '%{http_code}' \
      "http://127.0.0.1:$port/api/agenda")"
    if [[ "$status" == "200" ]]; then
      echo "$label image answered 200: $image"
      return 0
    fi
    sleep 2
  done

  echo "FAIL $label image did not answer 200 (last status: ${status:-none}): $image"
  docker logs --tail 30 "$app" 2>&1
  return 1
}

check_image new "$new_image" || { echo "Schema compatibility check failed"; exit 1; }
check_image current "$current_image" || { echo "Schema compatibility check failed"; exit 1; }
echo "Schema compatibility check passed"
