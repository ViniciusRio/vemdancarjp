# AGENTS.md

Regras gerais do projeto. Detalhes de modelo, custo, fluxo e padrões de prompt estão em `docs/AI-*.md`; conhecimento especializado por papel está nas skills em `.opencode/skills/`.

## Comandos e Verificação

- **Dev:** `npm run dev`
- **Typecheck:** `npm run type-check` (`vue-tsc --build`)
- **Lint:** `npm run lint` (`oxlint . --fix` + `eslint . --fix --cache`)
- **Format:** `npm run format` (`prettier --write --experimental-cli src/`)
- **Build:** `npm run build` (`vue-tsc --build && vite build`)
- **Testes unitários:** `npm run test:unit` (`vitest --passWithNoTests`)
- **Teste isolado:** `npx vitest run path/to/file.spec.ts`
- **Sequência completa:** `npm run lint && npm run type-check && npm run test:unit` (ou comando `/verify`)

## Idioma

- Código, nomes de variáveis, tipos e mensagens de commit em inglês.
- Documentação, explicações e texto de UI em português (pt-BR).
- Agentes respondem em pt-BR, salvo pedido expresso em inglês.
- Termos técnicos consagrados permanecem em inglês (Vue, Pinia, Supabase, RLS, Composition API, composable, store, service, router, Vite, Tailwind, etc.).

## Arquitetura e Estrutura

- **Stack:** Vue 3 (Composition API) + TypeScript + Vite + Tailwind CSS v4 + Pinia + Supabase.
- **Path alias:** `@/` → `src/`.
- **Fronteiras de diretório:**
  - `src/features/agenda/`: componentes, composables (`useAgendaAdmin`, `useAdminVenues`) e tipos de domínio.
  - `src/views/`: `AgendaHomeView.vue`, `admin/AdminLoginView.vue`, `admin/AdminView.vue`.
  - `src/stores/`: `agendaStore.ts` (estado público da agenda), `authStore.ts` (auth Supabase + privilégios de admin).
  - `src/services/`: `supabase.ts` (singleton), `agendaService.ts` (CRUD).

## Ambiente e Supabase

- **Env:** `.env` (template em `.env.example`): `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`.
- **Tabelas:** `events`, `variable_venues`, `other_venues`, `admins`, `pending_admins`.
- **Auth:** apenas Google OAuth (`supabase.auth.signInWithOAuth({ provider: 'google' })`). Segurança por Row Level Security (RLS).

## Convenções de código

- **Componentes Vue:** `<script setup lang="ts">` com tipos explícitos para props/emits. Options API é proibido.
- **Nomenclatura:** `PascalCase.vue` para componentes; `camelCase.ts` para composables, services, stores e helpers.
- **Estado:** stores Pinia (`authStore`, `agendaStore`). Não ler/gravar `localStorage` para estado de auth ou da app.
- **Comentários:** não adicione comentários a menos que solicitado.
- **Sem invenção:** não invente tabelas, colunas, endpoints ou políticas que não estejam confirmados no código ou nas migrations.

## Migração para Backend Java (WIP)

Migrando de Supabase para backend Java + Spring Boot. Plano em `PLAN.md`.

- **Fluxo de Aprendizado obrigatório** para cada passo da "Ordem de Implementação" do `PLAN.md`: explicar → confirmar → implementar → resumir diff → verificar → **registrar em `LEARNING.md`** → avançar.
- **`LEARNING.md`** atualizado ao final de cada passo concluído (entrada mais recente no topo). Nunca pular — mesmo em modo direto.
- **Modo direto** ("só faz"): pula explicação prévia e resumo do diff, mas **nunca** pula o `LEARNING.md`.

## Estratégia de modelo

- **Padrão:** GLM-5.2 (`openrouter/z-ai/glm-5.2`) — dia a dia.
- **Pesado:** Kimi K3 (`openrouter/moonshotai/kimi-k3`) — arquitetura, migrations, RLS, decisões de alto risco.
- Regra prática: comece no GLM-5.2. Se a tarefa for "projetar", "decidir", "migrar" ou "proteger", troque para Kimi K3.
- Detalhes e fluxos ideais: `docs/AI-MODEL-STRATEGY.md`. Economia de contexto/custos: `docs/AI-COST-CONTROL.md`.

## Skills e Agentes

Conhecimento especializado vive em `.opencode/skills/<nome>/SKILL.md`; modelo, modo e permissões vivem em `.opencode/agents/<nome>.md`.

- **`agenda-dev`** — implementação cotidiana da feature agenda. GLM-5.2. Edita; comandos destrutivos pedem aprovação.
- **`architect`** — arquitetura, migrations, decisões difíceis. Kimi K3. **Somente leitura.**
- **`security-reviewer`** — RLS, auth, autorização. Kimi K3. **Somente leitura.**

Carregue a skill correspondente antes de trabalhar em sua área. Selecione o agente via `/agents` ou use os comandos `/verify` e `/plan-feature`.

## Documentação complementar

- Estratégia de modelos: `docs/AI-MODEL-STRATEGY.md`
- Economia de contexto e custos: `docs/AI-COST-CONTROL.md`
- Fluxo de trabalho: `docs/AI-WORKFLOW.md`
- Padrões de prompt: `docs/AI-PROMPT-PATTERNS.md`
- Templates de prompt prontos: `docs/ai-prompts.md`
