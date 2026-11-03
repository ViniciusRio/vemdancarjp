---
type: arquitetura
topico: visao-geral
atualizado: 2026-09-17
---

# Arquitetura — Visão Geral

Stack atual (em migração):

- **Frontend:** Vue 3 (Composition API) + TypeScript + Vite + Tailwind CSS v4 + Pinia + Supabase client.
- **Backend (WIP):** Java + Spring Boot migrando do Supabase. Plano em `PLAN.md`.
- **Path alias:** `@/` → `src/`.

## Fronteiras de diretório

- `src/features/agenda/` — componentes, composables e tipos da feature agenda.
- `src/views/` — `AgendaHomeView.vue`, `admin/AdminLoginView.vue`, `admin/AdminView.vue`.
- `src/stores/` — `agendaStore.ts`, `authStore.ts`.
- `src/services/` — `supabase.ts` (singleton), `agendaService.ts` (CRUD).
- `backend/` — serviço Java (em construção).

## Tabelas Supabase (legado)

`events`, `variable_venues`, `other_venues`, `admins`, `pending_admins`.

## Ambiente

- `.env`: `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` (frontend).
- `.env.docker`: variáveis do backend Java/Postgres.
- OpenRouter: chave configurada no OpenCode via `/connect` (não no `.env`).

Veja também: [[arquitetura/autenticacao]], [[arquitetura/backend-java]], [[decisoes/2026-09-migracao-para-java]].
