---
description: Planeja uma feature sem editar arquivos. Usa o agente architect (Kimi K3).
agent: architect
model: openrouter/moonshotai/kimi-k3
---

Analise o pedido abaixo sem modificar arquivos. Você é somente leitura.

Produza, nesta ordem:

1. **Problema** — um parágrafo.
2. **Arquivos e símbolos afetados** — com `file:line`.
3. **Alternativas** — 2–3 opções com prós/contras.
4. **Trade-offs** — custo, risco, reversibilidade.
5. **Recomendação** — qual alternativa e por quê.
6. **Plano passo a passo** — cada passo nomeando o(s) arquivo(s)/SQL tocado(s).
7. **Rollback** — como desfazer cada passo.
8. **Dúvidas bloqueadoras** — se houver.

Cite arquivos/símbolos/migrations reais. Não invente tabelas, colunas, endpoints ou contratos. Se faltar informação, liste perguntas antes de recomendar.

Pedido: $ARGUMENTS
