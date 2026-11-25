# Fluxo de trabalho: Claude Code + Codex

Papéis separados, revisão cruzada e aprovação humana. Regras gerais do projeto estão em `AGENTS.md`; o estado atual, em `STATUS.md`.

## Papéis

| Papel | Quem | Responsabilidade |
|---|---|---|
| Implementador | Claude Code | Implementa, corrige o que foi apontado e escreve o handoff |
| Revisor adversarial | Codex | Revisa spec e diff em modo **somente leitura**; aponta problemas, não corrige |
| Aprovador | Vinícius | Aprova a spec, aprova o PR e faz o merge |

- O revisor nunca edita arquivos: use `/codex:adversarial-review` ou um perfil do Codex com `sandbox_mode = "read-only"`.
- Em mudanças de OIDC, CSRF ou autorização admin, peça ao revisor foco explícito nesses pontos.
- De tempos em tempos, inverta os papéis em uma tarefa para evitar pontos cegos.

## Etapas

### 1. Planejar

- Toda mudança relevante começa com uma change no OpenSpec (`proposal.md`, `design.md`, `tasks.md`), explicando o conceito, os arquivos e as decisões embutidas.
- O Codex revisa a spec antes de qualquer código; Vinícius aprova.
- **Exceções:** tarefa pequena (typo, uma linha, ajuste trivial) pula a spec e a revisão cruzada, com a explicação no chat. Mudança só de processo ou documentação pode usar um plano aprovado como spec.

### 2. Implementar

- Sempre em branch própria (`feat/...`, `fix/...`, `chore/...`), nunca na `main`. Card no Kanzo em `doing`; checklist no `tasks.md`.
- Faça a menor mudança que satisfaça a tarefa, sem refactors não relacionados.
- **Definição de pronto antes do handoff:** lint, type-check e testes passando, rodados pelo implementador, e `STATUS.md` atualizado. Separe falhas preexistentes das introduzidas pela mudança; não as corrija sem explicar antes.

### 3. Handoff

O implementador abre o PR e escreve o handoff **na descrição**:

```markdown
## Objetivo
## Branch e commit
## Arquivos alterados
## Decisões (tomadas e descartadas)
## Checagens rodadas (com resultado)
## Dúvidas em aberto
## Próxima ação
```

Em "Arquivos alterados" e "Decisões", ligue código a conceito com `arquivo:linha`, para o dono do projeto entender o que mudou e por quê.

### 4. Revisar

- Cada problema é **bloqueante** (volta para o implementador) ou **sugestão** (fica registrada, não bloqueia), sempre com `arquivo:linha`.
- Na rodada seguinte, o revisor confere cada problema original contra o diff corrigido, não só o código novo.
- **Máximo de 2 rodadas.** Se ainda houver bloqueante, Vinícius decide.
- A revisão fica em comentários no PR, não em arquivos do repositório. O plugin do Codex devolve o resultado no terminal: quem o executou cola no PR.

### 5. Aprovar

- Só Vinícius faz merge. Nenhum agente faz merge, por nenhum meio, mesmo que o comando não esteja bloqueado.
- **As proteções atuais não garantem essa regra.** O ruleset exige PR, mas não aprovação, porque o GitHub não aceita autoaprovação. O `deny` de `.claude/settings.json` é uma proteção adicional, local e contornável: só cobre certos textos de comando. A credencial do `gh` usada pelos agentes consegue fazer merge.
- Merge na `main` é deploy (`.github/workflows/deploy.yml`).

### 6. Modo reserva (limite do Claude Code)

- Com **10% de cota restante**, o Claude Code para num ponto limpo: commit WIP e handoff.
- O Codex continua sozinho a partir do handoff e do `git diff`.
- PRs desse período recebem o label **`sem-revisao-cruzada`**. Quando o Claude Code voltar, ele os revisa, com papéis invertidos. Nada desse período vai para a `main` antes disso.
- Confira que não há `ANTHROPIC_API_KEY` no ambiente, para não trocar a cobrança da assinatura pela da API.

## Quadro de tarefas (Kanzo)

O [Kanzo](https://github.com/emanuele-em/kanzo) é um kanban local que também é servidor MCP. Vinícius usa o quadro no navegador (`kanzo`, em `http://localhost:4242`); os agentes usam o MCP `kanzo`, que identifica o projeto pelo remote do git.

- **Um card por change do OpenSpec ou tarefa pequena.** O card aponta para a change; o checklist detalhado fica no `tasks.md`, sem duplicar no card.
- **Pegar trabalho:** o implementador usa `next_task` e move o card para `doing` ao começar. Cards marcados como Human são do Vinícius; agente não pega.
- **Handoff:** ao abrir o PR, o card vai para `review`, com a URL do PR no card e um comentário curto. O handoff completo continua na descrição do PR.
- **Concluir:** só Vinícius move para `done`, depois do merge.
- **Dependências:** use `waits_on` quando um card depende de outro; o `next_task` pula os bloqueados.
- O quadro é dado local (`~/kanzo`) e não entra no repositório. `STATUS.md` continua sendo o resumo curto do estado.

## Fluxo de Aprendizado

O objetivo do Fluxo de Aprendizado (`PLAN.md`) continua valendo nos Passos da migração: o dono do projeto precisa entender o que está sendo feito.

| Fluxo de Aprendizado | Onde acontece |
|---|---|
| Explicar e aguardar confirmação | Etapa 1: spec ou plano aprovado; em tarefa pequena, no chat |
| Implementar | Etapa 2 |
| Resumir o diff | Etapa 3: handoff |
| Verificar | Etapa 2: definição de pronto |
| Só então avançar | Etapa 5: merge antes do próximo Passo |

**Modo direto** ("só faz"): pula a explicação prévia; o handoff continua obrigatório.

`LEARNING.md` é histórico congelado: não recebe novas entradas e não é lido por padrão.

## Isolamento

Branch não isola a pasta de trabalho. **Worktree é obrigatório quando dois agentes editam ao mesmo tempo:**

```bash
git worktree add ../vemdancarjp-claude feat/minha-tarefa
git worktree add ../vemdancarjp-codex feat/outra-tarefa
```

No fluxo sequencial, o revisor somente leitura pode revisar o commit sem worktree próprio.

## Onde fica cada coisa

| Informação | Local |
|---|---|
| Regras, comandos e correções recorrentes | `AGENTS.md` (`CLAUDE.md` só importa esse arquivo) |
| Estado atual, **no máximo ~15 linhas** | `STATUS.md`, atualizado quando um agente para |
| Roteiro da migração | `PLAN.md` |
| Fila e estado das tarefas (backlog, doing, review) | Quadro Kanzo (`~/kanzo`, fora do repositório) |
| Checklist de uma change | `openspec/changes/*/tasks.md` |
| Decisão de uma change | `design.md` do OpenSpec |
| Decisão que vale além de uma change | ADR em `docs/decisions/` |
| Handoff e revisão | Descrição e comentários do PR |
| Histórico | Git. `LEARNING.md` está congelado |

## Economia de tokens

O que gasta é o que o agente lê: cada arquivo, log ou saída é reenviado a cada turno.

- Testes focados durante o trabalho; suíte completa uma vez, antes do handoff.
- Uma sessão por tarefa: `/clear` no Claude Code, `/new` no Codex. O handoff carrega o contexto.
- Para compactar no meio de uma tarefa: `/compact mantenha: objetivo, arquivos alterados, teste que falha, próximo passo`.
- Para esperar CI, use `gh pr checks --watch` ou `gh run watch`, sem loop de checagem.
- Revisão com `/codex:review` ou `/codex:adversarial-review`, que devolvem só o resultado. Se usar `codex exec` por script, rode num subagente.
- Mantenha só as skills usadas, com descrições curtas: o catálogo vai em toda sessão.

| Tarefa | Modelo | Esforço |
|---|---|---|
| Spec, arquitetura, segurança (OIDC, CSRF, autorização admin, corte do Supabase) | Opus / modelo forte do Codex | Alto |
| Implementação a partir de spec aprovada (CRUD admin, testes) | Sonnet / modelo padrão | Médio |
| Busca, renomeações, docs | Haiku / mini | Baixo |

Troque de ferramenta só entre etapas, nunca no meio de uma edição.

## Commits

Conventional Commits (`feat(backend): ...`, `fix: ...`, `docs: ...`). O agente é identificado pelo trailer, não pelo prefixo:

```
Co-Authored-By: Claude <noreply@anthropic.com>
```

## Revisão periódica do processo

A cada alguns ciclos:

- A revisão do Codex acha problemas reais ou só detalhes? Se só detalhes, ajuste o foco do prompt ou inverta os papéis.
- O `STATUS.md` continua curto?
- O `AGENTS.md` está crescendo com coisas que o código já mostra?
