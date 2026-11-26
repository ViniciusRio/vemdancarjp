# Design

## Context

Ver `proposal.md` para a motivação e as specs `admin-venues` e `admin-events` para o contrato.

Hoje o CRUD de eventos funciona assim:
- `AdminEventController` valida o corpo com `parse(JsonNode)` e helpers privados (`requiredText`, `optionalText`, `validHttpUrl` e a checagem de `sortOrder`). O `{id}` é validado cru, a partir de `getRequestURI`, e um `@ExceptionHandler` local devolve `400 VALIDATION_ERROR`.
- `SecurityConfig` tem um matcher exato para o GET e o POST da coleção e um matcher com regex de segmento único para o PUT e o DELETE do item, os dois antes do `denyAll`. O filtro de `no-store` roda antes do `CsrfFilter`.
- `EventWriteService` concentra as escritas de eventos.

As tabelas `variable_venues` e `other_venues` já existem, com entidades, repositórios e o método `findAllByOrderBySortOrderAsc`, usado pela agenda pública. `days` é `TEXT NOT NULL`. Não há chaves estrangeiras entre locais e eventos.

## Goals / Non-Goals

**Goals:**

- Ter um único ponto de validação de corpo e de `{id}` para eventos e locais, sem mudar o comportamento de eventos.
- Que as regras de segurança e de `no-store` derivem de uma lista explícita de recursos administrativos, sem curingas em `/api/admin/**`.

**Non-Goals:**

- Estruturar `days` (por exemplo, como uma lista de dias), o que exigiria migration e mudança na agenda pública.
- Unificar as duas tabelas de locais, controle de concorrência, filtros e paginação.
- Painel Vue, Supabase e Passo 10.

## Decisions

### 1. Uma change para as duas listas

As duas tabelas têm o mesmo ciclo de vida, o mesmo formulário (a menos de `days`) e o mesmo contrato de eventos. Uma change só evita duplicar revisão e testes de segurança. **Alternativa descartada:** uma change por tabela. Repetiria todo o trabalho de segurança para uma diferença de um campo.

### 2. Validação compartilhada

Extrair os helpers de `AdminEventController` para um componente `AdminRequestValidation`, sem estado e com métodos estáticos:
- verificar os campos permitidos;
- ler texto obrigatório e texto opcional;
- validar a URL do Instagram;
- ler o `sortOrder`;
- validar o `{id}` cru.

Os três controllers chamam esse componente. Um `@RestControllerAdvice(assignableTypes = ...)` restrito aos controllers admin substitui o `@ExceptionHandler` local de eventos e mantém o corpo `{"error":"VALIDATION_ERROR"}` para `HttpMessageNotReadableException`. **Alternativa descartada:** copiar os helpers em cada controller. Três cópias de regras de segurança acabariam divergindo.

### 3. Controllers e serviço

`AdminVariableVenueController` e `AdminOtherVenueController` seguem o desenho de `AdminEventController`. Um `VenueWriteService` concentra criar, editar e remover nas duas tabelas, todos `@Transactional`, com `update` devolvendo `Optional` e `delete` via `deleteAllByIdInBatch`. Dois records de resposta, `AdminVariableVenueResponse` e `AdminOtherVenueResponse`, trazem nulos explícitos.

### 4. Segurança derivada de uma lista de recursos

`SecurityConfig` passa a ter uma lista constante de coleções admin: `events`, `variable-venues` e `other-venues`. Dela saem dois matchers:
- **coleção:** `GET` ou `POST` com o caminho exatamente igual a `/api/admin/<recurso>`;
- **item:** `PUT` ou `DELETE` em `/api/admin/<recurso>/<segmento>`, com o segmento casando `[^/;]+`.

Os dois usam o `AdminAccessAuthorizationManager` e ficam antes do `denyAll`. O filtro de `no-store`, que já roda antes do `CsrfFilter`, usa os mesmos dois matchers. Assim, liberar um recurso novo é acrescentar um nome na lista. **Alternativa descartada:** um `/api/admin/**` liberado com checagem no controller. Isso tiraria do `denyAll` a proteção de rotas que ainda não existem.

### 5. Provas

Um teste de integração parametrizado pelos dois recursos cobre:
- o ciclo completo;
- contrato e validação, incluindo `days`;
- `id` cru inválido sem tocar no repositório;
- `401`, `403`, `CSRF_INVALID` e revogação na mesma sessão;
- `no-store` em todos os resultados;
- rotas vizinhas negadas;
- reflexo na agenda pública.

O `5xx` por falha na consulta de aprovação entra em `AdminEventAuthorizationFailureIntegrationTest`, renomeado para `AdminAuthorizationFailureIntegrationTest`. Os testes atuais de eventos continuam passando sem mudança de expectativa, o que prova que a extração da validação não alterou o comportamento.

## Risks / Trade-offs

- **[Refatorar a validação de eventos]** Mexer em código já revisado pode mudar o comportamento. → A suíte de eventos atual roda sem ajuste. Qualquer teste de eventos que precise mudar é sinal de regressão.
- **[Última escrita vence]** É o mesmo risco aceito para eventos. → Fica registrado na spec, para revisão no Passo 9.
- **[`days` livre]** O admin pode gravar texto inconsistente, como "qui-dom" num local e "Quinta a domingo" em outro. → A agenda pública já exibe esse texto como está. Estruturar o campo é outra change.

## Migration Plan

Não há mudança de schema nem de dados. Para reverter, basta remover os dois recursos da lista e os controllers novos. Eventos e agenda pública continuam como estão.
