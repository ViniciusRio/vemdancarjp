---
type: tarefa
status: aguardando-revisao
---

# Tarefa: Sessão OIDC, CSRF e logout 7C

## Objetivo

Expor o estado da sessão OIDC, o token CSRF e o logout local no backend Spring Boot.

## Escopo

- `GET /api/auth/session`, `GET /api/auth/csrf` e `POST /api/auth/logout`.
- Testes MockMvc com PostgreSQL via Testcontainers.
- Atualização de `PLAN.md`, `LEARNING.md` e ADR-003.

## Critérios de aceitação

- [x] A sessão usa apenas o `sub` OIDC e lê `admin_access` em toda consulta.
- [x] Visitantes anônimos recebem token CSRF, mas não podem fazer logout.
- [x] Logout exige CSRF, responde 204 e invalida a sessão HTTP.
- [x] Agenda pública e bloqueio administrativo são mantidos.
- [ ] Revisão humana do diff.

## Contexto relevante

- [[decisoes/2026-10-oidc-seguranca-inicial]]
- [[decisoes/2026-10-modelo-admin-access]]

## Arquivos modificados

- `backend/src/main/java/com/vemdancarjp/config/SecurityConfig.java`
- `backend/src/main/java/com/vemdancarjp/controller/AuthController.java`
- `backend/src/test/java/com/vemdancarjp/config/AuthEndpointsIntegrationTest.java`
- `PLAN.md`, `LEARNING.md` e esta documentação do vault.

## Testes executados

- `cd backend && ./gradlew test --rerun-tasks` — 34 testes após a revisão, 0 falhas.
- `cd backend && ./gradlew build` — sucesso.
- `git diff --check` — sem erros.

## Decisões tomadas

- Spring Security mantém o token CSRF e o logout associados à sessão HTTP.
- O matcher do logout identifica somente método e rota; um filtro anterior exige autenticação antes do logout nativo.
- A consulta informa aprovação, mas não altera autorização administrativa.
