# Estado atual (máx. ~15 linhas; atualizar quando um agente parar)
## Feito
- Backend: agenda pública, OIDC/sessão/CSRF, POST e GET /api/admin/events na main (Passo 8 parcial).
- Specs: admin-event-creation e admin-event-listing arquivadas em openspec/specs/admin-events.
- Fluxo Claude Code + Codex adotado (docs/AI-WORKFLOW.md), com quadro Kanzo via MCP.
## Em revisão
- PUT e DELETE /api/admin/events/{id} (feat/admin-event-update-delete, vdj#9).
## Bloqueado
- Produção: home em erro. O domínio do projeto Supabase não resolve mais (projeto pausado ou removido). Adiado: o foco é remover o Supabase.
- Deploy do backend (Cloud Run + Neon) em feat/backend-production-deploy, parado.
## Próximo
1. CRUD de venues (resto do Passo 8), depois Passo 9. 2. Revisar o Passo 10: sem Supabase, não há reconciliação.
3. Separar checks e deploy no deploy.yml.
