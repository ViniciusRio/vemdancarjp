---
type: tarefa
status: implementacao-em-revisao
---

# Tarefa: Criação administrativa de eventos

## Objetivo

Implementar a etapa 7D.2 e o primeiro incremento do Passo 8: `POST /api/admin/events` com Google OIDC, aprovação atual em `admin_access`, CSRF, validação e persistência PostgreSQL.

## Escopo

- Mudança OpenSpec em `openspec/changes/admin-event-creation/`.
- Liberação exclusiva de `POST /api/admin/events`; o restante de `/api/admin/**` permanece bloqueado.
- Sem migration, alteração no frontend, dependência nova ou corte dos dados Supabase/PostgreSQL.

## Critérios de aceitação

- [x] Proposta, spec, desenho e tarefas escritos e validados pelo OpenSpec.
- [x] Cenários de autenticação, aprovação, revogação, CSRF, validação e isolamento das demais rotas especificados.
- [x] Divergência entre escritas Supabase e leitura PostgreSQL registrada como risco e condicionante da exposição operacional.
- [x] Implementação e testes do primeiro incremento, com registro de aprendizado.
- [ ] Revisão do responsável sobre a implementação; o Passo 8 completo continua pendente.

## Contexto relevante

- [[decisoes/2026-09-migracao-para-java]]
- [[decisoes/2026-10-modelo-admin-access]]
- [[decisoes/2026-10-oidc-seguranca-inicial]]
- `PLAN.md`, Passos 7, 8 e 10.

## Arquivos modificados

- `backend/src/main/java/com/vemdancarjp/config/SecurityConfig.java`
- `backend/src/main/java/com/vemdancarjp/controller/AdminEventController.java`
- `backend/src/main/java/com/vemdancarjp/service/EventCreationService.java`
- `backend/src/main/java/com/vemdancarjp/dto/request/CreateEventRequest.java`
- `backend/src/main/java/com/vemdancarjp/dto/response/CreatedEventResponse.java`
- Testes de integração em `backend/src/test/java/com/vemdancarjp/config/` e `backend/src/test/java/com/vemdancarjp/controller/`.
- `openspec/changes/admin-event-creation/design.md` e `tasks.md`; esta nota, ADR-003 e `LEARNING.md`.

## Testes executados

- `cd backend && ./gradlew test --rerun-tasks` — 75 testes, 0 falhas; inclui PostgreSQL/Testcontainers, sessão, CSRF, autorização, validação e agenda pública.
- `cd backend && ./gradlew build` — sucesso; tarefa `test` atualizada pela execução anterior.
- Avisos não bloqueantes: agente OpenJDK no teste e recursos obsoletos do Gradle incompatíveis com Gradle 9.

## Decisões tomadas

- Associar apenas `POST /api/admin/events` à política 7D.1, antes de `/api/admin/**` com `denyAll`; manter CSRF e os handlers 401/403 existentes.
- Validar JSON localmente, rejeitar campos de controle, aparar textos, gravar somente os campos permitidos na tabela `events` e responder `201` com apenas `id`.
- Permitir o despacho interno `ERROR` para preservar resposta `5xx` quando a consulta de aprovação falha; requisição direta a `/error` permanece bloqueada.
- O painel Vue ainda escreve no Supabase, enquanto esta rota escreve no PostgreSQL e a agenda pública lê PostgreSQL. Essa divergência impede considerar concluída a integração operacional; bootstrap e corte seguem pendentes.
