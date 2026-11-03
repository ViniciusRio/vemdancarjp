---
type: modulo
nome: auth-admin
atualizado: 2026-09-17
---

# Módulo — Auth & Admin

## Fluxo

1. Google OAuth via Supabase (legado).
2. Login admin: `src/views/admin/AdminLoginView.vue`.
3. Privilégios verificados no `authStore.ts` usando tabelas `admins` e `pending_admins`.
4. Onboarding: novo admin entra como `pending_admins` e é promovido a `admins`.

## RLS

Segurança por Row Level Security no Supabase. Revisar antes de expor novas tabelas/colunas ao client.

## Tabelas envolvidas

`admins`, `pending_admins`.

Veja também: [[arquitetura/autenticacao]], [[modulos/agenda]].
