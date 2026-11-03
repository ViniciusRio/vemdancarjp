#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
IMAGE_REPO="${IMAGE_REPO:-ghcr.io/viniciusrio/vemdancarjp-backend}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-180}"
LOCK_TIMEOUT_SECONDS="${LOCK_TIMEOUT_SECONDS:-900}"
DUMPS_TO_KEEP="${DUMPS_TO_KEEP:-5}"

STATE_FILE="$DEPLOY_DIR/image.env"
LOCK_FILE="$DEPLOY_DIR/.deploy.lock"
DUMP_DIR="$DEPLOY_DIR/dumps"

log() {
  printf '%s %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"
}

compose() {
  docker compose --ansi never --project-directory "$DEPLOY_DIR" \
    -f "$DEPLOY_DIR/compose.prod.yml" --env-file "$DEPLOY_DIR/.env" "$@"
}

read_current_image() {
  [[ -r "$STATE_FILE" ]] || return 0
  sed -n 's/^BACKEND_IMAGE=//p' "$STATE_FILE" | tail -1
}

save_current_image() {
  local temp_file
  temp_file="$(mktemp "$STATE_FILE.XXXXXX")"
  printf 'BACKEND_IMAGE=%s\n' "$1" >"$temp_file"
  mv "$temp_file" "$STATE_FILE"
}

agenda_responds() {
  compose exec -T caddy wget -q -O /dev/null -T 5 http://backend:8080/api/agenda >/dev/null 2>&1
}

running_image() {
  local container_id
  container_id="$(compose ps -q backend)"
  [[ -n "$container_id" ]] || return 0
  docker inspect --format '{{.Config.Image}}' "$container_id"
}

start_and_check() {
  local image="$1"
  export BACKEND_IMAGE="$image"
  compose up -d --remove-orphans || return 1

  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if [[ "$(running_image)" == "$image" ]] && agenda_responds; then
      return 0
    fi
    sleep 5
  done
  return 1
}

write_dump() {
  local timestamp dump_file
  timestamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  dump_file="$DUMP_DIR/pre-deploy-$timestamp.sql.gz"
  mkdir -p "$DUMP_DIR"
  rm -f "$DUMP_DIR"/*.partial
  compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
    | gzip >"$dump_file.partial"
  mv "$dump_file.partial" "$dump_file"
  log "Dump gravado: $dump_file"
  find "$DUMP_DIR" -maxdepth 1 -name 'pre-deploy-*.sql.gz' | sort -r \
    | tail -n "+$((DUMPS_TO_KEEP + 1))" | xargs -r rm -f --
}

digest="${SSH_ORIGINAL_COMMAND:-${1:-}}"
if [[ ! "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
  log "ERRO: informe somente o digest no formato sha256:<64 hexadecimais>"
  exit 2
fi

umask 077
exec 9>"$LOCK_FILE"
log "Aguardando a trava de deploy"
if ! flock --wait "$LOCK_TIMEOUT_SECONDS" 9; then
  log "ERRO: outro deploy continua em andamento; nada foi alterado"
  exit 75
fi

target_image="$IMAGE_REPO@$digest"
previous_image="$(read_current_image)"
log "Imagem atual: ${previous_image:-nenhuma}"
log "Nova imagem: $target_image"

export BACKEND_IMAGE="$target_image"
compose pull --quiet backend
compose up -d --wait postgres
write_dump

if start_and_check "$target_image"; then
  save_current_image "$target_image"
  log "Deploy concluido: $target_image"
  exit 0
fi

log "ERRO: a nova imagem nao respondeu em ${HEALTH_TIMEOUT_SECONDS}s"
compose logs --no-color --tail 40 backend || true

if [[ -z "$previous_image" ]]; then
  compose stop backend || true
  log "Nao ha imagem anterior; o backend ficou parado"
  exit 1
fi

log "Voltando para $previous_image"
if start_and_check "$previous_image"; then
  log "Imagem anterior restaurada: $previous_image"
  exit 1
fi

log "ERRO: a imagem anterior tambem nao respondeu; intervencao manual necessaria"
exit 3
