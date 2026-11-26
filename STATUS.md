# Estado atual (máx. ~15 linhas; atualizar quando um agente parar)
## Feito
- Backend: agenda pública, OIDC/sessão/CSRF e CRUD admin de eventos e locais na main (Passo 8 concluído).
- Specs: openspec/specs/admin-events e admin-venues, com todas as changes arquivadas.
- Fluxo Claude Code + Codex adotado (docs/AI-WORKFLOW.md), com quadro Kanzo via MCP.
## Bloqueado
- Produção: home em erro. O domínio do projeto Supabase não resolve mais (projeto pausado ou removido). Adiado: o foco é remover o Supabase.
- Deploy do backend (Cloud Run + Neon) em feat/backend-production-deploy, parado.
## Próximo
1. Passo 9 (gestão administrativa). 2. Revisar o Passo 10: sem Supabase, não há reconciliação.
3. Separar checks e deploy no deploy.yml.
