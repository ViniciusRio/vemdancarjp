---
type: modulo
nome: agenda
atualizado: 2026-09-17
---

# Módulo — Agenda

Feature central do produto.

## Componentes

- `src/features/agenda/` — componentes, composables (`useAgendaAdmin`, `useAdminVenues`) e tipos.
- `src/views/AgendaHomeView.vue` — vista pública.
- `src/views/admin/AdminView.vue` — admin.

## Estado e serviço

- `src/stores/agendaStore.ts` — estado público da agenda (Pinia).
- `src/services/agendaService.ts` — CRUD nas tabelas `events`, `variable_venues`, `other_venues`.

## Tabelas envolvidas

`events`, `variable_venues`, `other_venues`.

## Testes

`npm run test:unit` (Vitest). Teste isolado: `npx vitest run path/to/file.spec.ts`.

Veja também: [[modulos/auth-admin]], [[arquitetura/visao-geral]].
