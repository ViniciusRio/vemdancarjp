---
type: runbook
nome: dev-local
atualizado: 2026-09-17
---

# Rodar o projeto localmente

## Frontend

```bash
npm install
npm run dev
```

Variáveis (`.env`): `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`.

## Backend (Java — WIP)

```bash
docker compose up -d
```

Veja `PLAN.md` e `docker-compose.yml` para detalhes.

## Verificação

[[runbooks/verify]].
