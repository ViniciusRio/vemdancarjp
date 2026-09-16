# Plano: UI/UX Design System

## Contexto

O projeto Vue 3 + Tailwind v4 tem cores hardcoded (`bg-green-50`, `text-green-900`, etc.) sem um design system formal. Este plano usa o kit em `ui-ux/` (Penpot + MCP + `DESIGN.md` + tokens DTCG) para estruturar a identidade visual, alinhando-a ao tom cultural do projeto (agenda de forró pé de serra em João Pessoa).

Paralelo ao `PLAN.md` (backend Java). Ambos alimentam o mesmo `LEARNING.md` — entradas prefixadas com `UI/UX — Fase N` vs `Passo N` (backend).

## Stack

- **Canvas:** Penpot self-hosted (Docker, porta 9001)
- **MCP:** Penpot MCP local (porta 4401) — ponte IA ↔ canvas
- **Contrato:** `DESIGN.md` + `tokens/*.json` (formato DTCG, versionados no git)
- **Motores:** GLM-5.2 (materializar, sincronizar) | Kimi K3 (extrair, auditar)

## Fases

### Fase 0 — Referências (manual)

- Coletar 2-3 referências visuais (prints ou URLs) em `ui-ux/refs/`.
- Não precisam ser agendas de forró — qualquer produto cuja vibe você quer capturar.

### Fase 1 — Extração do design system (Kimi K3)

- Rodar `ui-ux/prompts/04-extrair-de-referencias.md` com Kimi K3.
- Entrada: referências + `DESIGN.md` atual (andaime).
- Saída: `DESIGN.md` reescrito, `tokens/core.json` + `tokens/dark.json` atualizados, `ui-ux/refs/analise.md`.
- Regra: onde as referências divergem, escolher e justificar em uma linha — não fazer média entre elas (média é o caminho de volta ao genérico).

### Fase 2 — Materializar no canvas (GLM-5.2) [opcional]

- Subir Penpot (`scripts/01-penpot-up.sh`) + MCP (`scripts/02-mcp-up.sh`).
- Rodar `prompts/01-materializar-no-canvas.md` com GLM-5.2: criar páginas `Foundations` (cores, tipografia, espaço, elevação) e `Componentes` (botão, input, card, tabela, KPI, navegação) no Penpot.
- Revisar visualmente e editar à mão o que não gostar.

### Fase 3 — Sincronizar canvas → código (GLM-5.2)

- Converter `tokens/core.json` em CSS variables / Tailwind v4 theme em `src/styles/` ou `src/assets/`.
- Aplicar tokens aos componentes Vue existentes (`AgendaHomeView`, `DayFilter`, `AgendaList`, `VariableVenues`, `OtherVenues`, `AdminView`, etc.).
- Trocar cores hardcoded (`bg-green-50`, `text-green-900`, `border-green-200`) pelos tokens do design system.

### Fase 4 — Auditar (Kimi K3) [opcional se Penpot]

- Rodar `prompts/02-auditar.md`: Kimi K3 inspeciona canvas via MCP, produz relatório de violações em `reports/audit-<data>.md`.
- Corrigir o que for severidade alta.

### Fase 5 — Loop contínuo

- Canvas editado → rodar `prompts/03-ler-de-volta.md` (GLM-5.2 lê de volta, atualiza `DESIGN.md` + tokens).
- Feature entregue → rodar `prompts/02-auditar.md` (Kimi K3 audita).
- `DESIGN.md` é a fonte da verdade, não o canvas.

## Observações

- **Penpot é opcional.** Dá pra pular Fases 2 e 4 e ir direto: extração (Fase 1) → código (Fase 3). O canvas é útil pra visualizar antes de codar, mas não é obrigatório. O `DESIGN.md` + `tokens/*.json` funcionam sozinhos como contrato.
- **Paralelo ao `PLAN.md`.** Este plano é uma trilha separada do backend Java — intercalar ou fazer um de cada vez.
- **`LEARNING.md` compartilhado.** Entradas prefixadas: `UI/UX — Fase N` vs `Passo N` (backend).

## Rollback

Se a nova direção visual não funcionar:

1. O `DESIGN.md` e `tokens/*.json` originais do kit ficam no git (commitados antes de qualquer alteração) — basta `git checkout` para reverter.
2. Os componentes Vue não são destruídos — apenas as cores hardcoded são trocadas por tokens. Reverter é trocar tokens de volta pra cores hardcoded via `git checkout`.
3. O Penpot self-hosted é descartável: `docker compose -p penpot -f docker-compose.yaml down -v` remove tudo.
