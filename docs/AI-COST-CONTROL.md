# Performance e Economia de Tokens

Diagnóstico real: gasta-se com **contexto**, não resposta (134k entrada vs 84 saída nas chamadas recentes; cache hit 84,9%). Cada passo do agente recarrega a conversa inteira. Regras abaixo são obrigatórias.

## Regras

- **R1 — Kimi K3 nunca dentro de loop de agente.** K3 custa ~$3/M entrada vs ~$0,20/M do GLM (18×). A 134k, ~$0,40/passo. Padrão: GLM-5.2 coleta achados em arquivo; K3 lê **só esse arquivo** (~8k) numa chamada curta e dá veredito. K3 só entra onde julgamento vale o preço e o contexto é enxuto (prompt 04, trade-offs, revisão final).
- **R2 — uma sessão por tarefa.** Sessão longa é o multiplicador: no passo 30 paga-se o histórico dos 29 anteriores. `/compact` ao passar de ~50% da janela; sessão nova a cada fase do `PLAN*.md` (análise, implementação, auditoria não compartilham sessão). Olhar o % de preenchimento no sidebar antes de continuar.
- **R3 — o que é sempre-presente tem que ser pequeno.** `AGENTS.md` é auto-injetado em toda chamada: 1.000 tokens aqui viram 30.000 numa sessão de 30 passos. Regras, não explicação; meta <2k tokens. Cite o caminho de `PLAN*.md`/`DESIGN.md` em vez de colar conteúdo. Proibido `Read` de arquivo inteiro por hábito — usar `grep -n`/`rg -n` + ler só o intervalo de linhas.
- **R4 — proteja o cache.** 84,9% de acerto segura a conta (leitura de cache custa 0,1×–0,5× de token fresco). Cache vale por prefixo estável: não edite, no meio da sessão, arquivo que está no topo do contexto; não troque de modelo no meio (cache é por modelo+provedor); adicione contexto no fim, nunca insira no começo.
- **R5 — trabalho determinístico não paga token.** Tarefa com resposta única e verificável é script, não pergunta: `tokens/core.json → tokens.css` (conversão), contraste WCAG (fórmula), caça a cor hardcoded (`rg -n "green-|gray-|#[0-9a-fA-F]{3,6}" src/`), renomear/mover/formatar (`sed`, `prettier`). Modelo é para julgamento.
- **R6 — guardrails na conta, não na disciplina.** Limite de gasto por chave OpenRouter com `limit_reset` recorrente; alerta de gasto na chave; chave separada por trilha (UI/UX vs backend); roteamento por preço é o padrão (`:nitro`/`throughput` só quando velocidade importar de verdade). Raciocínio em nível baixo/desligado em tarefa mecânica (tokens de raciocínio são cobrados como saída).

## Checklist pré-sessão

1. A tarefa é uma só e cabe nesta sessão?
2. Modelo certo: mecânico → GLM-5.2 · julgamento curto e enxuto → K3 (uma chamada, arquivo de achados)?
3. Arquivos sempre-presentes (`AGENTS.md`) estão enxutos?
4. O que é determinístico já virou script?
5. Vou ler por `rg -n` + intervalo, não arquivo inteiro?
6. `/compact` ou sessão nova ao passar da metade da janela?

## Ferramentas e hábitos

- **Seja específico.** Nomeie arquivos, funções e símbolos (`src/stores/agendaStore.ts:fetchEvents`), não "a app".
- **Plane antes de construir.** Tarefa grande → plano primeiro, depois implementa. Evita retrabalho.
- **Sem explicações genéricas.** Não pergunte "como o Vue funciona" — pergunte sobre arquivos reais deste repo.
- **Prefira `read`/`glob`/`grep`** a `cat`/`find`/`grep` no Bash. Não rode `ls` quando `glob` resolve.
- **Escope o diff.** Menor mudança que satisfaça o objetivo; rejeite refactors não relacionados embutidos.
- **Verifique uma vez.** `npm run lint && npm run type-check && npm run test:unit` no final, não após cada edição. Use o comando `/verify`.

## Limites contra gasto acidental (OpenRouter)

- Defina limite de crédito para a chave.
- Use uma chave específica para este projeto.
- Não compartilhe a chave entre várias automações.
- Monitore o uso do Kimi.
- Evite roteamento `nitro`/throughput quando não houver necessidade.
- Nunca comite a chave em `.env`, histórico Git ou logs.

Uma instrução no `AGENTS.md` é menos confiável do que uma permissão que bloqueia efetivamente uma operação — por isso os guardrails de execução vivem em `.opencode/agents/*.md` e `opencode.jsonc`.
