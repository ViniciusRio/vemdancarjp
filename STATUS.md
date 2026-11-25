# Estado atual (máx. ~15 linhas; atualizar quando um agente parar)
## Feito
- Backend: agenda pública, OIDC/sessão/CSRF e POST /api/admin/events na main (Passo 8 parcial).
- Fluxo Claude Code + Codex adotado (docs/AI-WORKFLOW.md), com quadro Kanzo via MCP.
## Em revisão
- GET /api/admin/events (feat/admin-event-listing, Passo 8).
## Bloqueado
- Produção: home em erro. O domínio do projeto Supabase não resolve mais (projeto pausado ou removido). Adiado: o foco é remover o Supabase.
- Deploy do backend (Cloud Run + Neon) em feat/backend-production-deploy, parado.
## Próximo
1. Resto do Passo 8 e Passo 9. 2. Revisar o Passo 10: sem Supabase, não há reconciliação.
3. Separar checks e deploy no deploy.yml. 4. Arquivar admin-event-creation.
