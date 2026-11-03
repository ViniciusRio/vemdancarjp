---
type: arquitetura
topico: autenticacao
atualizado: 2026-09-17
---

# Arquitetura — Autenticação

## Frontend (legado Supabase)

- Apenas Google OAuth: `supabase.auth.signInWithOAuth({ provider: 'google' })`.
- Segurança por Row Level Security (RLS) no Supabase.
- Estado de auth no `authStore.ts` (Pinia). Não usar `localStorage` para auth.
- Privilégios de admin derivam das tabelas `admins` e `pending_admins`.

## Backend Java (WIP)

A ser definido durante a migração. Ver [[decisoes/2026-09-migracao-para-java]].

## Riscos e cuidados

- Não expor `admins`/`pending_admins` sem RLS.
- Onboarding de admins via `pending_admins` antes de promoção a `admins`.
- Revisar mutações em `agendaService.ts` antes de expor novas tabelas ao client.

Veja também: [[modulos/auth-admin]], [[arquitetura/visao-geral]].
