# AGENTS.md

## Commands & Verification

- **Dev server:** `npm run dev`
- **Typecheck:** `npm run type-check` (`vue-tsc --build`)
- **Linting:** `npm run lint` (runs `oxlint . --fix` followed by `eslint . --fix --cache`)
- **Formatting:** `npm run format` (`prettier --write --experimental-cli src/`)
- **Build:** `npm run build` (`vue-tsc --build && vite build`)
- **Unit tests:** `npm run test:unit` (`vitest --passWithNoTests`)
- **Single test file:** `npx vitest run path/to/file.spec.ts`
- **Verification sequence:** `npm run lint && npm run type-check && npm run test:unit`

## Architecture & Structure

- **Stack:** Vue 3 (Composition API) + TypeScript + Vite + Tailwind CSS v4 + Pinia + Supabase.
- **Path alias:** `@/` maps to `src/`.
- **Directory boundaries:**
  - `src/features/agenda/`: Feature components, composables (`useAgendaAdmin`, `useAdminVenues`), and domain types.
  - `src/views/`: `AgendaHomeView.vue`, `admin/AdminLoginView.vue`, `admin/AdminView.vue`.
  - `src/stores/`: `agendaStore.ts` (public schedule state), `authStore.ts` (Supabase authentication & admin rights).
  - `src/services/`: `supabase.ts` (Supabase client singleton), `agendaService.ts` (Database CRUD logic).

## Environment & Supabase

- **Environment variables:** Configured in `.env` (template in `.env.example`):
  - `VITE_SUPABASE_URL`
  - `VITE_SUPABASE_ANON_KEY`
- **Database tables:** `events`, `variable_venues`, `other_venues`, `admins`, `pending_admins`.
- **Auth:** Google OAuth only (`supabase.auth.signInWithOAuth({ provider: 'google' })`). Security enforced by Row Level Security (RLS).

## Conventions

- **Language:** Code, comments, variable names, and commit messages in English; UI text in Portuguese (`pt-BR`).
- **Vue components:** Use `<script setup lang="ts">` with explicit TypeScript types for props/emits. Options API is prohibited.
- **Naming:** `PascalCase.vue` for components; `camelCase.ts` for composables, services, stores, and helpers.
- **State management:** Use Pinia stores (`authStore`, `agendaStore`). Do not read/write `localStorage` directly for auth or app state.
