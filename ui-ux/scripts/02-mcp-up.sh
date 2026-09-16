#!/usr/bin/env bash
# Sobe o servidor MCP local da Penpot (porta 4401) + servidor do plugin (porta 4400).
# Requer Node.js 22 (v20 tende a funcionar).
set -euo pipefail

node --version

echo "-> iniciando @penpot/mcp (stable)"
echo "   endpoints: http://localhost:4401/mcp  (streamable HTTP)"
echo "              http://localhost:4401/sse  (legado, para mcp-remote)"
echo
cat <<'TXT'
Depois que subir, no navegador:
  1. abra o Penpot e entre em um arquivo de design
  2. menu Plugins -> carregar via URL: http://localhost:4400/manifest.json
  3. abra a UI do plugin e clique em "Connect to MCP server"
  4. o status deve virar "Connected to MCP server"

Atencao: nao feche a UI do plugin — fechar derruba a conexao.
Chromium 142+ pede permissao de acesso a rede local; aprove o popup.
No Brave, desligue o Shield no dominio do Penpot. Firefox nao impoe a restricao.
TXT
echo
npx -y @penpot/mcp@stable
