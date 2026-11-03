# AGENTS.md

Fonte única de regras do projeto. Estado atual: `STATUS.md`.

## Fluxo de trabalho

- Claude Code implementa, Codex revisa em modo somente leitura e Vinícius aprova e faz o merge.
- Mudança relevante começa com change no OpenSpec, aprovada antes do código. Tarefa pequena pula a spec.
- Sempre em branch própria, nunca na `main`. Agente nunca faz merge. Merge na `main` é deploy.
- Pronto = checagens passando e `STATUS.md` atualizado; o handoff vai na descrição do PR.
- Detalhes (handoff, rodadas de revisão, modo reserva, worktree): `docs/AI-WORKFLOW.md`.

## Comandos

- **Dev:** `npm run dev`
- **Typecheck:** `npm run type-check`
- **Lint:** `npm run lint` (aplica `--fix`)
- **Format:** `npm run format`
- **Build:** `npm run build`
- **Backend:** `cd backend && ./gradlew test -q` (Testcontainers exige Docker) e `./gradlew build`

Testes focados durante o trabalho:

- `npx vitest run caminho/arquivo.spec.ts`
- `cd backend && ./gradlew test --tests "*AdminEvent*" -q`

Suíte completa uma vez, antes do handoff: `npm run lint && npm run type-check && npm run test:unit` e o teste do backend.

## Saída de comandos

- Modo silencioso, mostrando só falhas, com `set -o pipefail` para não esconder erros:
  - `cd backend && ./gradlew test -q 2>&1 | tail -50`
  - `npm run lint 2>&1 | tail -40`
  - `npm run type-check 2>&1 | tail -40`
- Se falhar, procure o erro (`grep -n "FAILED\|error"`) antes de abrir o relatório completo.
- Nunca imprima o relatório HTML de testes nem o log completo do Testcontainers.

## Leitura econômica

- `PLAN.md`: leia só o Passo atual (`grep -n "Passo 8" PLAN.md` e depois a faixa de linhas).
- `LEARNING.md`: histórico congelado. Não leia por padrão e não adicione entradas.

## Idioma

- Código, nomes, tipos e mensagens de commit em inglês.
- Documentação, explicações e texto de UI em pt-BR. Agentes respondem em pt-BR.
- Termos técnicos consagrados permanecem em inglês.

## Stack e estrutura

Migração de Supabase para Java + Spring Boot em andamento; roteiro em `PLAN.md`.

- **Frontend:** Vue 3 (Composition API) + TypeScript + Vite + Tailwind CSS v4 + Pinia. Alias `@/` → `src/`.
  - A agenda pública lê `GET /api/agenda` (`src/services/agendaService.ts`).
  - Login e painel admin ainda usam Supabase (Google OAuth e RLS), inclusive para escrita.
- **Backend (`backend/`):** Java 21, Spring Boot, Flyway, PostgreSQL, Google OIDC com sessão e CSRF. Ainda não está em produção.
- **Produção:** GitHub Pages, só o frontend.
- **Env:** `.env` (template em `.env.example`). Segredo nunca no frontend nem no repositório.

## Convenções

- **Componentes Vue:** `<script setup lang="ts">` com tipos explícitos para props e emits. Options API é proibido.
- **Nomes:** `PascalCase.vue` para componentes; `camelCase.ts` para composables, services, stores e helpers.
- **Estado:** stores Pinia. Não use `localStorage` para estado de auth ou da app.
- **Comentários:** não adicione a menos que solicitado.
- **Sem invenção:** não invente tabelas, colunas, endpoints ou políticas sem confirmar no código ou nas migrations.

## Decisões

- Antes de propor arquitetura ou mudança de alto risco, consulte os ADRs em `docs/decisions/`. Decisão que vale além de uma change vira ADR lá.
- Progresso de tarefas fica no `tasks.md` do OpenSpec.
- **Nunca** coloque segredos, tokens ou dados pessoais em arquivos versionados.

## Correções recorrentes

Quando um agente repetir um erro já corrigido, registre aqui em uma linha.
