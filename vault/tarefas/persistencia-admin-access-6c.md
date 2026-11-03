---
type: tarefa
status: aguardando-revisao
---

# Tarefa: Persistência administrativa 6C

## Objetivo

Criar somente a persistência do novo modelo administrativo, sem autenticação ou importação de identidades legadas.

## Escopo

- Migration V2, entidade, enum e repository de `admin_access`.
- Testes de integração das constraints em PostgreSQL.
- Alinhamento pontual de `PLAN.md` e registro em `LEARNING.md`.

## Critérios de aceitação

- [x] V1 e V2 aplicadas em PostgreSQL limpo nos testes.
- [x] Hibernate valida o esquema e o repository persiste e consulta por `google_sub`.
- [x] Unicidade, campos obrigatórios, estados válidos e perfil opcional cobertos.
- [x] Testes existentes de `GET /api/agenda` passam sem login.
- [ ] Revisão humana do diff.

## Contexto relevante

- [[decisoes/2026-10-modelo-admin-access]]
- [[modulos/auth-admin]]

## Arquivos modificados

- `backend/src/main/resources/db/migration/V2__create_admin_access.sql`
- `backend/src/main/java/com/vemdancarjp/entity/AdminAccess.java`
- `backend/src/main/java/com/vemdancarjp/entity/AdminAccessStatus.java`
- `backend/src/main/java/com/vemdancarjp/repository/AdminAccessRepository.java`
- `backend/src/test/java/com/vemdancarjp/repository/AdminAccessPersistenceIntegrationTest.java`
- `PLAN.md`, `LEARNING.md` e esta documentação do vault.

## Testes executados

- `cd backend && ./gradlew test` — 12 testes, 0 falhas.
- `cd backend && ./gradlew build` — sucesso.
- `git diff --check` — sem erros.

## Decisões tomadas

- Modelo de tabela única escolhido pelo responsável pelo produto; sem importação de identidades Supabase.
- V2 apenas aditiva; V1 e tabelas da agenda permanecem intactas.
