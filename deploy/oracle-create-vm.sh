#!/usr/bin/env bash
set -euo pipefail

: "${SUBNET_ID:?Defina SUBNET_ID com o OCID da sub-rede publica}"
: "${SSH_PUBLIC_KEY_FILE:=$HOME/.ssh/vemdancarjp-oracle.pub}"
: "${DISPLAY_NAME:=vemdancarjp-prod}"
: "${OCPUS:=1}"
: "${MEMORY_GB:=4}"
: "${IMAGE_NAME_PREFIX:=Canonical-Ubuntu-24.04-Minimal-aarch64}"
: "${RETRY_SECONDS:=600}"
: "${THROTTLE_SECONDS:=1800}"
: "${MAX_SECONDS:=0}"
: "${LOG_FILE:=$HOME/vemdancarjp-oracle-create-vm.log}"
: "${OCI_PROFILE:=DEFAULT}"
: "${OCI_CONFIG_FILE:=$HOME/.oci/config}"

SHAPE="VM.Standard.A1.Flex"

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG_FILE"
}

fail() {
  log "ERRO: $*"
  exit 1
}

oci_cmd() {
  oci --profile "$OCI_PROFILE" --config-file "$OCI_CONFIG_FILE" "$@"
}

find_existing_instance() {
  oci_cmd compute instance list \
    --compartment-id "$COMPARTMENT_ID" \
    --display-name "$DISPLAY_NAME" \
    --all \
    --query "data[?\"lifecycle-state\"!='TERMINATED' && \"lifecycle-state\"!='TERMINATING'] | [0].id" \
    --raw-output
}

report_public_ip() {
  local instance_id="$1"
  local public_ip
  public_ip="$(oci_cmd compute instance list-vnics \
    --instance-id "$instance_id" \
    --query 'data[0]."public-ip"' \
    --raw-output)"
  log "Instancia: $instance_id"
  log "IP publico: ${public_ip:-nao atribuido}"
  log "Teste: ssh -i ${SSH_PUBLIC_KEY_FILE%.pub} ubuntu@$public_ip"
}

command -v oci >/dev/null || fail "OCI CLI nao encontrada"
[[ -r "$SSH_PUBLIC_KEY_FILE" ]] || fail "Chave publica nao encontrada: $SSH_PUBLIC_KEY_FILE"
[[ -r "$OCI_CONFIG_FILE" ]] || fail "Configuracao nao encontrada: $OCI_CONFIG_FILE. Rode: oci setup config"

COMPARTMENT_ID="${COMPARTMENT_ID:-$(awk -F= -v profile="[$OCI_PROFILE]" '
  $0 == profile { active = 1; next }
  /^\[/ { active = 0 }
  active && $1 ~ /^tenancy[[:space:]]*$/ { gsub(/[[:space:]]/, "", $2); print $2; exit }
' "$OCI_CONFIG_FILE")}"
[[ -n "$COMPARTMENT_ID" ]] || fail "Nao foi possivel obter o tenancy do perfil $OCI_PROFILE"

subnet_blocks_public_ip="$(oci_cmd network subnet get \
  --subnet-id "$SUBNET_ID" \
  --query 'data."prohibit-public-ip-on-vnic"' \
  --raw-output)"
[[ "$subnet_blocks_public_ip" == "false" ]] || fail "A sub-rede nao permite IP publico"

AVAILABILITY_DOMAIN="${AVAILABILITY_DOMAIN:-$(oci_cmd iam availability-domain list \
  --compartment-id "$COMPARTMENT_ID" \
  --query 'data[0].name' \
  --raw-output)}"
[[ -n "$AVAILABILITY_DOMAIN" ]] || fail "Availability domain nao encontrado"

IMAGE_ID="${IMAGE_ID:-$(oci_cmd compute image list \
  --compartment-id "$COMPARTMENT_ID" \
  --shape "$SHAPE" \
  --sort-by TIMECREATED \
  --sort-order DESC \
  --all \
  --query "data[?starts_with(\"display-name\", '$IMAGE_NAME_PREFIX')] | [0].id" \
  --raw-output)}"
[[ -n "$IMAGE_ID" && "$IMAGE_ID" != "null" ]] || fail "Imagem nao encontrada: $IMAGE_NAME_PREFIX"

log "Inicio: $SHAPE, $OCPUS OCPU, $MEMORY_GB GB, em $AVAILABILITY_DOMAIN"
started_at="$SECONDS"
attempt=0

while (( MAX_SECONDS == 0 || SECONDS - started_at < MAX_SECONDS )); do
  if ! existing_id="$(find_existing_instance 2>&1)"; then
    log "Falha ao consultar instancias; nova tentativa em ${RETRY_SECONDS}s"
    sleep "$RETRY_SECONDS"
    continue
  fi
  if [[ -n "$existing_id" && "$existing_id" != "null" ]]; then
    log "Ja existe uma instancia chamada $DISPLAY_NAME; nada foi criado"
    report_public_ip "$existing_id"
    exit 0
  fi

  attempt=$((attempt + 1))
  output_file="$(mktemp)"
  if oci_cmd --no-retry compute instance launch \
    --compartment-id "$COMPARTMENT_ID" \
    --availability-domain "$AVAILABILITY_DOMAIN" \
    --display-name "$DISPLAY_NAME" \
    --shape "$SHAPE" \
    --shape-config "{\"ocpus\":$OCPUS,\"memoryInGBs\":$MEMORY_GB}" \
    --image-id "$IMAGE_ID" \
    --subnet-id "$SUBNET_ID" \
    --assign-public-ip true \
    --ssh-authorized-keys-file "$SSH_PUBLIC_KEY_FILE" \
    --availability-config '{"recoveryAction":"RESTORE_INSTANCE"}' \
    --instance-options '{"areLegacyImdsEndpointsDisabled":true}' \
    --wait-for-state RUNNING \
    --query 'data.id' \
    --raw-output >"$output_file" 2>&1; then
    instance_id="$(grep -Eo 'ocid1\.instance\.[A-Za-z0-9._-]+' "$output_file" | tail -1 || true)"
    rm -f "$output_file"
    [[ -n "$instance_id" ]] || fail "A OCI CLI concluiu sem informar o ID da instancia; confira o console"
    log "Criada na tentativa $attempt"
    report_public_ip "$instance_id"
    exit 0
  fi

  output="$(cat "$output_file")"
  rm -f "$output_file"

  if grep -qiE 'out of (host )?capacity' <<<"$output"; then
    log "Tentativa $attempt: sem capacidade; nova tentativa em ${RETRY_SECONDS}s"
    sleep "$RETRY_SECONDS"
  elif grep -qiE 'timed out|connection|name resolution|temporary failure|"status": *50[234]' <<<"$output"; then
    log "Tentativa $attempt: falha temporaria de rede ou servico; nova tentativa em ${RETRY_SECONDS}s"
    sleep "$RETRY_SECONDS"
  elif grep -qE 'TooManyRequests|"status": *429' <<<"$output"; then
    log "Tentativa $attempt: limite de requisicoes; nova tentativa em ${THROTTLE_SECONDS}s"
    sleep "$THROTTLE_SECONDS"
  else
    log "Tentativa $attempt: erro inesperado; parando"
    printf '%s\n' "$output" | tee -a "$LOG_FILE"
    exit 1
  fi
done

fail "Prazo de ${MAX_SECONDS}s encerrado sem capacidade"
