# Estado atual (máx. ~15 linhas; atualizar quando um agente parar)
## Feito
- Backend: agenda pública, OIDC/sessão/CSRF e POST /api/admin/events na main (Passo 8 parcial).
- Fluxo Claude Code + Codex adotado (docs/AI-WORKFLOW.md).
## Bloqueado
- Produção: home em erro desde 16/09. O build chama /api/agenda sem backend publicado. Primeira tarefa.
## Próximo
1. Corrigir o site. 2. Separar checks e deploy no deploy.yml.
3. Arquivar admin-event-creation. 4. PR da listagem (WIP local). 5. Corte do Passo 10.
## Não mexer
- Dados do Supabase, até a reconciliação do Passo 10.
