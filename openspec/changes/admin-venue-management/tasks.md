# Tasks

## 1. Base compartilhada

- [x] 1.1 Nos três testes de eventos que usam rotas de locais como exemplo de rota bloqueada (`AdminEventSecurityIntegrationTest:226`, `AdminEventListingIntegrationTest:184` e `AdminEventUpdateDeleteIntegrationTest:328`), trocar a rota por uma que continua bloqueada, como `/api/admin/admins`, com o mesmo método. Nenhuma outra expectativa de eventos muda. Verificação: diff restrito a essas três linhas.
- [x] 1.2 Extrair os helpers de validação e de `{id}` cru de `AdminEventController` para `AdminRequestValidation`, e o tratamento de `400` para um `@RestControllerAdvice` restrito aos controllers admin. Verificação: `./gradlew test --tests "*AdminEvent*" -q` passa sem alterar nenhum teste de eventos além da tarefa 1.1.
- [x] 1.3 Trocar em `SecurityConfig` os matchers de eventos por matchers de coleção e de item derivados da lista `events`, `variable-venues` e `other-venues`, também usados pelo filtro de `no-store`. Verificação: a suíte de eventos continua passando.

## 2. Locais

- [x] 2.1 Criar `AdminVariableVenueResponse`, `AdminOtherVenueResponse` e o `VenueWriteService` transacional (criar, editar com `Optional` e remover sem falhar). Verificação: testes da seção 3.
- [x] 2.2 Criar `AdminVariableVenueController` e `AdminOtherVenueController` com `GET`, `POST`, `PUT /{id}` e `DELETE /{id}`, validando `{id}` cru e corpo antes de acessar o banco. Verificação: testes 3.1 a 3.3.

## 3. Testes de integração (parametrizados pelos dois recursos)

- [x] 3.1 Ciclo completo: criar `201`, listar ordenado por `sortOrder`, editar `200` com corpo exato e `instagram` limpo, remover `204` duas vezes; `PUT` inexistente `404` sem criar; lista vazia `[]`.
- [x] 3.2 Contrato, parametrizado por recurso e por campo de texto (`name`, `neighborhood`, `instagram` e, nos variáveis, `days`): texto com NUL, tipo incompatível (número, booleano, objeto) e obrigatório só com espaços ou ausente com `400`. Também com `400`: `days` num outro local, `id` no corpo, campo desconhecido, `instagram` sem URL, `sortOrder` fracionário, string, nulo ou fora do inteiro de 32 bits, corpo que não é objeto (`[]`, `"x"`, `null`) e JSON malformado. Em todos os casos, confirmar que nada foi gravado. Aparar espaços e `instagram` vazio virando `null`.
- [x] 3.3 `{id}` inválido (`0`, `-1`, `abc`, `007`, `%31` e acima do limite de 64 bits) com `400` no `PUT` e no `DELETE`, sem nenhuma chamada ao repositório (verificado com spy).
- [x] 3.4 Autorização: `401` sem sessão; `403 FORBIDDEN` sem aprovação, com usuário não OIDC e após revogação na mesma sessão; `403 CSRF_INVALID` sem token e com token de outra sessão no `POST`, no `PUT` e no `DELETE`; `GET` sem CSRF aceito.
- [x] 3.5 `Cache-Control: no-store` exato em `200`, `201`, `204`, `400`, `401`, `403` (inclusive `CSRF_INVALID`) e `404`.
- [x] 3.6 Isolamento: `GET` e `PATCH` no item, `PUT` e `DELETE` na coleção, subcaminho, barra final e `;` negados. `GET /api/admin/admins` e `POST /api/admin/venues` negados. `GET /api/agenda` segue público.
- [x] 3.7 Agenda pública: criar, editar e remover locais dos dois tipos aparece em `GET /api/agenda`, na ordem de `sortOrder`.
- [x] 3.8 Falha na consulta de aprovação devolve `5xx` com `no-store` nas rotas de locais, pela cadeia HTTP real, no teste de falhas de autorização renomeado.

## 4. Fechamento

- [x] 4.1 Rodar a suíte completa (`cd backend && ./gradlew test -q`), atualizar o `STATUS.md` e o ADR `docs/decisions/2026-10-oidc-seguranca-inicial.md` com os novos recursos liberados. Verificação: suíte verde e diffs no PR.

## Workflow follow-up

- Revisão do Codex no PR e merge pelo Vinícius.
- Arquivar a change após o merge.
