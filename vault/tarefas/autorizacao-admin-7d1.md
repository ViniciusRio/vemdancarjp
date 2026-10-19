---
type: tarefa
status: aguardando-revisao
---

# Tarefa: Política isolada de autorização administrativa 7D.1

## Objetivo

Preparar uma decisão reutilizável de autorização administrativa sem liberar rotas HTTP.

## Escopo

- `AuthorizationManager<RequestAuthorizationContext>` que consulta `admin_access` pelo `sub` da sessão Google OIDC.
- Testes unitários da decisão, teste com PostgreSQL/Testcontainers para atualização da aprovação e regressão MockMvc do bloqueio HTTP.
- Atualização de `PLAN.md`, `LEARNING.md` e ADR-003.

## Critérios de aceitação

- [x] Apenas principal autenticado da inscrição OIDC Google, com emissor esperado e `APPROVED`, recebe decisão positiva da política isolada.
- [x] Ausência de registro, `PENDING`, outro `sub`, outro tipo de principal ou outro provedor resultam em negação.
- [x] Mudanças no banco afetam a próxima avaliação com o mesmo principal; falhas de consulta propagam erro sem conceder acesso.
- [x] `/api/admin/**` continua em `denyAll`; agenda, sessão, CSRF e logout mantêm seus contratos.
- [ ] Revisão humana do diff.

## Contexto relevante

- [[decisoes/2026-10-oidc-seguranca-inicial]]
- [[decisoes/2026-10-modelo-admin-access]]

## Arquivos modificados

- `backend/src/main/java/com/vemdancarjp/config/AdminAccessAuthorizationManager.java`
- `backend/src/test/java/com/vemdancarjp/config/AdminAccessAuthorizationManagerTest.java`
- `backend/src/test/java/com/vemdancarjp/config/AdminAccessAuthorizationIntegrationTest.java`
- `backend/src/test/java/com/vemdancarjp/config/AuthEndpointsIntegrationTest.java`
- `PLAN.md`, `LEARNING.md`, ADR-003 e esta nota.

## Testes executados

- `cd backend && ./gradlew test --tests com.vemdancarjp.config.AdminAccessAuthorizationManagerTest` — teste inicial falhou na compilação pela política ausente; depois passou com a decisão básica. A segunda fatia falhou ao aceitar emissor diferente e passou após validar o emissor.
- `cd backend && ./gradlew test --tests com.vemdancarjp.config.AdminAccessAuthorizationIntegrationTest --tests com.vemdancarjp.config.AuthEndpointsIntegrationTest` — sucesso com PostgreSQL/Testcontainers e MockMvc.
- `cd backend && ./gradlew test --rerun-tasks` — 41 testes em 7 suítes, 0 falhas.
- `cd backend && ./gradlew build` — sucesso; testes já atualizados pela execução anterior.
- `git diff --check` — sem erros no diff rastreado.

## Decisões tomadas

- A política aceita apenas `OAuth2AuthenticationToken` autenticado da inscrição `google`, com `OidcUser` e emissor `https://accounts.google.com`.
- A aprovação é consultada pelo `sub` textual a cada avaliação, sem role ou cache. Exceções de persistência propagam para que uma indisponibilidade não pareça simples ausência de aprovação.
- A política não é conectada à cadeia HTTP no 7D.1. O primeiro endpoint administrativo real exigirá autorização explícita e testes HTTP próprios antes de ser liberado.
