---
type: adr
numero: "004"
titulo: Adotar Claude Code como implementador e Codex como revisor
data: 2026-11-02
status: aceita
---

# ADR-004: Adotar Claude Code como implementador e Codex como revisor

## Contexto

O repositório documentava um fluxo no opencode, com agentes por papel e modelos GPT Sol e Luna. Esse fluxo não separava quem implementa de quem revisa, permitia commit direto na `main` e mantinha o estado espalhado entre `PLAN.md`, `LEARNING.md` e `vault/tarefas/`. Arquivos longos lidos pelos agentes também aumentavam o consumo de tokens.

## Decisão

- Claude Code implementa, Codex revisa em modo somente leitura e Vinícius aprova e faz o merge. Detalhes em `docs/AI-WORKFLOW.md`.
- Toda mudança chega à `main` por PR, com handoff na descrição.
- `STATUS.md` é a fonte única e curta do estado atual.
- Decisões que valem além de uma change ficam em `vault/decisoes/`.
- `.opencode/`, `opencode.jsonc` e `Modelfile` são removidos. Os guias antigos vão para `docs/archive/`.
- O Fluxo de Aprendizado continua nas etapas de spec e handoff, mas `LEARNING.md` deixa de receber registros e fica como histórico congelado. Isso substitui a consequência de registro obrigatório da ADR-001.

## Consequências

- O vault deixa de ter MCP e passa a ser lido como Markdown comum.
- `vault/tarefas/` também fica congelado; o progresso vai para o `tasks.md` do OpenSpec.
- O ruleset exige PR, mas não aprovação, porque o GitHub não aceita autoaprovação. O impedimento a merge por agente fica em regra de processo e no `deny` do cliente, sem bloqueio no servidor.
- `ui-ux/` e `PLAN-UI-UX.md` também são removidos, por decisão do dono do projeto. Seguem recuperáveis pelo histórico do Git. A menção ao plano na ADR-001 é histórica.

## Alternativas consideradas

- Manter o opencode junto do fluxo novo: duplicaria skills e regras.
- Mover os ADRs para `docs/decisions/`: quebraria os wikilinks do vault sem ganho.
- Manter `LEARNING.md` como definição de pronto: descartado por decisão do dono do projeto, que não quer mais registros de aprendizado.

Veja também: [[decisoes/2026-09-migracao-para-java]].
