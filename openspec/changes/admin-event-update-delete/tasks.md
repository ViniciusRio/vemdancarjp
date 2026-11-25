# Tasks

## 1. Serviço de escrita

- [ ] 1.1 Renomear `EventCreationService` para `EventWriteService`, mantendo `create` e ajustando o controller e os testes existentes. Verificação: `./gradlew test -q` passa sem mudança de comportamento.
- [ ] 1.2 Adicionar `update(id, request)` e `delete(id)` transacionais, com o caso "não encontrado" distinguível pelo controller. Verificação: testes de integração da seção 3.

## 2. Rotas e segurança

- [ ] 2.1 Adicionar `PUT` e `DELETE` em `/{id}` no `AdminEventController`, validando o `{id}` como `String` antes da busca e reusando `parse` para o corpo. Responder `200` com `AdminEventResponse` ou `404 NOT_FOUND` no `PUT`, e `204` sempre no `DELETE` com `id` válido. Verificação: testes 3.1 a 3.3.
- [ ] 2.2 Adicionar os matchers `PUT /api/admin/events/*` e `DELETE /api/admin/events/*` com `AdminAccessAuthorizationManager`, antes do `denyAll`. Verificação: testes 3.4 e 3.6.
- [ ] 2.3 Estender o filtro de `no-store` aos dois pares novos. Verificação: teste 3.5.

## 3. Testes de integração

- [ ] 3.1 Edição: sucesso com corpo exato, opcionais que viram `null`, corpo inválido, `id` no corpo e JSON malformado com `400`, sem alterar a linha.
- [ ] 3.2 Remoção: `204` sem corpo, só a linha alvo apagada, e `204` de novo na repetição e para `id` inexistente.
- [ ] 3.3 `{id}` inválido (`0`, `-1`, `abc`, `007` e acima do limite de 64 bits) com `400` nas duas rotas, sem nenhuma chamada ao `EventRepository` (verificado com spy). No `PUT`, `id` inexistente com `404`, sem criar evento.
- [ ] 3.4 Autorização nas duas rotas: `401` sem sessão, `403 FORBIDDEN` sem aprovação, `403 CSRF_INVALID` sem token ou com token de outra sessão, revogação na mesma sessão e `5xx` em falha da consulta de aprovação.
- [ ] 3.5 `Cache-Control: no-store` em `200`, `204`, `400`, `401`, `403`, `404` (só no `PUT`) e `5xx`.
- [ ] 3.6 Isolamento: `GET` e `PATCH` em `/api/admin/events/{id}`, `PUT` e `DELETE` na coleção e subcaminho `/api/admin/events/{id}/x` negados para sessão aprovada. `GET /api/agenda` segue público.
- [ ] 3.7 Agenda pública: um evento editado para outro dia aparece só no dia novo, na ordem do novo `sortOrder`, e um evento removido some.

## 4. Fechamento

- [ ] 4.1 Rodar a suíte completa (`cd backend && ./gradlew test -q`) e atualizar o `STATUS.md`. Verificação: suíte verde e STATUS no PR.
- [ ] 4.2 Atualizar o ADR `docs/decisions/2026-10-oidc-seguranca-inicial.md` com as duas rotas liberadas. Verificação: diff do ADR no PR.

## Workflow follow-up

- Revisão do Codex no PR e merge pelo Vinícius.
- Arquivar a change após o merge.
