#!/usr/bin/env bash
# Sobe o Penpot self-hosted via Docker Compose.
# Requer docker e docker compose instalados.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

if [ ! -f docker-compose.yaml ]; then
  echo "-> baixando docker-compose.yaml oficial da Penpot"
  curl -fsSL -o docker-compose.yaml \
    https://raw.githubusercontent.com/penpot/penpot/main/docker/images/docker-compose.yaml
fi

echo "-> subindo a stack"
docker compose -p penpot -f docker-compose.yaml up -d

cat <<'TXT'

Penpot no ar: http://localhost:9001

Proximos passos dentro do Penpot:
  1. crie sua conta (ou use o manage.py, ver README)
  2. Sua conta -> Integracoes -> MCP Server -> ativar e gerar a chave
     (a chave aparece uma unica vez; guarde em config/.env.local)
  3. crie um arquivo de design e deixe-o aberto

Para parar:
  docker compose -p penpot -f docker-compose.yaml down
Para atualizar:
  docker compose -f docker-compose.yaml pull && docker compose -p penpot -f docker-compose.yaml up -d
TXT
