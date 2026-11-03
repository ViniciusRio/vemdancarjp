---
type: adr
numero: "001"
titulo: Migrar de Supabase para backend Java + Spring Boot
data: 2026-09-17
status: aceita
---

# ADR-001: Migrar de Supabase para Java + Spring Boot

## Contexto

O projeto nasceu no Supabase (auth Google OAuth, RLS, tabelas `events`, `variable_venues`, `other_venues`, `admins`, `pending_admins`). A migração busca maior controle do backend e integração com filas (RabbitMQ) e cache (Valkey). Planos detalhados:

- `PLAN.md` — migração geral.
- `PLAN-VALKEY-RABBITMQ.md` — cache e filas.
- `PLAN-UI-UX.md` — redesign de UI/UX.

## Decisão

Adotar Java + Spring Boot como backend, mantendo o frontend Vue 3. OpenRouter (GLM-5.2 e Kimi K3) segue como provedor de modelos para o OpenCode.

## Consequências

- Necessidade de migrations próprias (sem RLS do Supabase).
- Auth Google OAuth precisa ser reimplementada no backend.
- Novas tabelas/colunas devem ser confirmadas em código ou migrations — não inventadas.
- Fluxo de aprendizado obrigatório registrado em `LEARNING.md`.

## Alternativas consideradas

- Permanecer no Supabase: descartado por limitações de controle e custo.

Veja também: [[arquitetura/visao-geral]], [[arquitetura/backend-java]].
