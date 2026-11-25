# Estado atual (máx. ~15 linhas; atualizar quando um agente parar)
## Feito
- Backend: agenda pública, OIDC/sessão/CSRF e POST /api/admin/events na main (Passo 8 parcial).
- Fluxo Claude Code + Codex adotado (docs/AI-WORKFLOW.md), com quadro Kanzo via MCP.
## Bloqueado
- Produção: home em erro desde 16/09. Correção no PR fix/production-agenda (vdj#1), aguardando revisão e merge.
## Próximo
1. Merge da correção do site. 2. Separar checks e deploy no deploy.yml.
3. Arquivar admin-event-creation. 4. PR da listagem (WIP local). 5. Corte do Passo 10.
## Não mexer
- Dados do Supabase, até a reconciliação do Passo 10.
