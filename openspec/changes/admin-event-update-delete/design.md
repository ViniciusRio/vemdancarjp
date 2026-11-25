# Design

## Context

Ver `proposal.md` para a motivação e `specs/admin-events/spec.md` para o contrato.

`SecurityConfig` libera `GET` e `POST` em `/api/admin/events` pelo `AdminAccessAuthorizationManager` e, em seguida, aplica `denyAll` a `/api/admin/**`. O `CsrfFilter` roda antes da autorização. O `accessDeniedHandler` devolve `401 UNAUTHENTICATED` para quem não tem sessão, `403 CSRF_INVALID` para falha de CSRF e `403 FORBIDDEN` para os demais casos. O `no-store` da listagem é aplicado por um filtro com matcher exato de método e caminho, antes da autorização.

`AdminEventController` valida o corpo do `POST` com `parse(JsonNode)` e devolve `400 VALIDATION_ERROR` por um `@ExceptionHandler` local. `EventCreationService` grava via `EventRepository`. `AdminEventResponse` já tem o formato completo que o `PUT` precisa devolver. A tabela `events` não tem coluna de versão nem chave estrangeira que dependa dela.

## Goals / Non-Goals

**Goals:**

- Reusar sem duplicação a autorização, o CSRF, a validação do corpo e o formato de erro já usados pelo `POST`.
- Liberar só os quatro pares exatos de método e caminho, e manter o `denyAll` para o resto.

**Non-Goals:**

- Controle de concorrência. Sem coluna de versão, vale a última escrita. Adicionar versão exige migration e fica para quando houver mais de um admin editando ao mesmo tempo.
- Edição parcial (`PATCH`), remoção em lote, soft delete ou histórico.
- CRUD de `variable_venues` e `other_venues`, que fica para outra change do Passo 8.
- Painel Vue, Supabase e Passo 10.

## Decisions

### 1. `PUT` com substituição completa

A edição recebe o mesmo corpo do `POST` e substitui o evento inteiro. Assim, a validação existente é reaproveitada tal como está, e um opcional omitido vira `null`, como na criação. **Alternativa descartada:** um `PATCH` parcial. Ele exigiria distinguir "campo omitido" de "campo nulo" e criaria regras de validação novas, sem ganho para um painel que sempre envia o formulário inteiro.

### 2. Respostas

O `PUT` devolve `200` com `AdminEventResponse`, para o painel não precisar fazer uma nova leitura. O `DELETE` devolve `204`. Um `id` inexistente devolve `404 NOT_FOUND` nas duas rotas. **Alternativa descartada:** um `DELETE` idempotente, com `204` mesmo quando o evento não existe. Isso esconderia de quem chama que o evento já tinha sumido. Em nenhum caso o `PUT` cria um evento.

### 3. Validação antes da busca

O `{id}` chega como `String` e é validado no controller: precisa casar com `[1-9][0-9]{0,18}` e caber em `Long`. Ele e o corpo são validados antes de qualquer acesso ao banco, então um pedido inválido nunca revela se o evento existe. **Alternativa descartada:** declarar `@PathVariable Long`. Isso aceitaria `+5` e zeros à esquerda e jogaria o erro de conversão para o handler global, fora do formato `VALIDATION_ERROR`.

### 4. Escrita transacional

Um serviço `EventWriteService` absorve o `EventCreationService` e ganha `update(id, request)` e `delete(id)`, cada um em `@Transactional`. O `update` carrega a entidade, aplica os campos e devolve a entidade salva. O `delete` remove a linha e informa se ela existia, por exemplo com um `deleteByIdReturningCount` ou com um `existsById` seguido do delete na mesma transação. O controller traduz o caso "não encontrado" para `404`. **Alternativa descartada:** manter `EventCreationService` e criar mais dois serviços. Seriam três classes para a mesma entidade, cada uma com um único método.

### 5. Segurança e cache

São dois matchers novos, `PUT /api/admin/events/*` e `DELETE /api/admin/events/*`, com o `AdminAccessAuthorizationManager`, ambos antes do `denyAll`. O `*` cobre exatamente um segmento, então `/api/admin/events/1/x` continua negado. O filtro de `no-store` passa a cobrir também esses dois pares. Como ele roda antes da autorização, o cabeçalho sai inclusive em `401` e `403`. O CSRF continua com a regra padrão, que já exige token em `PUT` e `DELETE`.

### 6. Provas

Os testes de integração seguem o padrão de `AdminEventListingIntegrationTest`, com PostgreSQL de teste e `MockHttpSession`. Eles cobrem:

- sucesso e corpo exato;
- opcionais que viram nulos;
- `id` inválido sem acesso ao repositório;
- `404`;
- `401`, `403` e `CSRF_INVALID`;
- revogação na mesma sessão;
- falha de infraestrutura com `5xx`, reusando o padrão de `AdminEventAuthorizationFailureIntegrationTest`;
- `no-store` em todos os casos;
- reflexo na agenda pública;
- rotas vizinhas negadas.

## Risks / Trade-offs

- **[Última escrita vence]** Dois admins editando o mesmo evento se sobrescrevem. → Hoje é aceitável, porque há um único admin. Fica registrado como não objetivo, a revisar no Passo 9.
- **[Remoção definitiva]** Um evento apagado não volta. → O painel deve pedir confirmação, e isso fica a cargo do frontend no Passo 10. Não há backup em produção, porque o backend ainda não foi publicado.
- **[Matcher com curinga]** Um padrão largo demais liberaria rotas vizinhas. → Os testes de isolamento cobrem `GET`, `PATCH` e subcaminhos de `/api/admin/events/{id}`, além do `PUT` na coleção.

## Migration Plan

Não há mudança de schema nem de dados. Para reverter, basta remover os dois matchers e as rotas novas. `POST`, `GET` e a agenda pública continuam como estão.
