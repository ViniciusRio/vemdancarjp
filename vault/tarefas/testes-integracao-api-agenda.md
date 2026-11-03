---
type: tarefa
status: concluida
---

# Tarefa: Testes de integração da API de agenda

## Objetivo

Proteger o comportamento atual de `GET /api/agenda` antes da migração de autenticação e CRUD administrativo.

## Escopo

- Testes HTTP com MockMvc e contexto Spring Boot completo.
- PostgreSQL 17 isolado via Testcontainers, schema criado pelo Flyway.
- Eventos e locais fictícios persistidos por JPA; constraint de `day_id` exercitada por SQL.

## Critérios de aceitação

- [x] HTTP 200 sem autenticação e sete dias vazios na ordem correta.
- [x] Agrupamento e ordenação de eventos; ordenação das duas listas de locais.
- [x] Campos e tipos do JSON conferidos, inclusive valores `null`.
- [x] PostgreSQL rejeita `day_id` inválido e o histórico do Flyway registra a migration V1.
- [x] Dados dos testes isolados e execução repetida.

## Contexto relevante

- [[decisoes/2026-09-migracao-para-java]]

## Arquivos modificados

- `backend/build.gradle`
- `backend/src/test/java/com/vemdancarjp/controller/AgendaControllerIntegrationTest.java`
- `backend/gradlew`, `backend/gradlew.bat`, `backend/gradle/wrapper/gradle-wrapper.properties` e `backend/gradle/wrapper/gradle-wrapper.jar`
- `.gitignore` e `LEARNING.md`

## Testes executados

- `cd backend && ./gradlew test` — 7 testes executados, 0 falhas.
- `cd backend && ./gradlew build` — sucesso; teste atualizado pela execução anterior.
- Gradle local 8.13: `test --rerun-tasks` — 7 testes executados novamente, 0 falhas.

## Decisões tomadas

- Alinhar Testcontainers em 1.21.4 no escopo de testes para compatibilidade com Docker 29.
- Restaurar o JAR ausente do Gradle Wrapper, preservando a distribuição 8.10 definida no projeto.
- Não alterar regras de negócio, frontend, migrations ou comportamento de desempate em `sort_order`.
