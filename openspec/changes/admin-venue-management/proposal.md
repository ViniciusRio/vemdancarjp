# Proposal

## Why

O backend já tem o CRUD protegido de eventos, mas a agenda pública também mostra duas listas de locais: os de dias variáveis (`variable_venues`) e os outros locais (`other_venues`). Hoje elas só podem ser alteradas direto no banco. O CRUD dessas duas listas fecha o Passo 8, que é pré-requisito para tirar o painel do Supabase.

## What Changes

- Para `variable_venues`, adicionar `GET` e `POST /api/admin/variable-venues` e `PUT` e `DELETE /api/admin/variable-venues/{id}`.
- Para `other_venues`, adicionar as mesmas quatro rotas em `/api/admin/other-venues`.
- Os contratos copiam os de eventos já aprovados:
  - mesma autorização (Google OIDC com `APPROVED` consultado a cada requisição) e CSRF nas escritas;
  - `POST` devolve `201 {"id":...}`;
  - `PUT` substitui o local inteiro e devolve `200` com o local, ou `404 NOT_FOUND`;
  - `DELETE` devolve `204` sempre que o `{id}` é válido;
  - `{id}` em decimal literal, validado cru, com `400 VALIDATION_ERROR` quando inválido;
  - `no-store` em todas as respostas;
  - vale a última gravação.
- Os campos seguem o schema atual. Os dois tipos têm `name`, `neighborhood` e `sortOrder` obrigatórios e `instagram` opcional, validado como URL. Os locais variáveis têm também `days`, um texto livre obrigatório, como "Quinta a domingo".
- A listagem não tem filtro e é ordenada por `sortOrder`, como na agenda pública.
- Mudança no isolamento de `admin-events`: a regra passa a valer só dentro de `/api/admin/events/**`. Cada capability governa as próprias rotas, e o resto de `/api/admin/**` continua bloqueado.

## Capabilities

### New Capabilities

- `admin-venues`: gestão administrativa protegida das duas listas de locais da agenda.

### Modified Capabilities

- `admin-events`: os requisitos "Isolamento das demais rotas administrativas" e "Autorização restrita da listagem" deixam de proibir rotas fora de `/api/admin/events/**`, que passam a ser regidas por `admin-venues`.

## Impact

- **Backend:** controllers para as duas listas, serviço de escrita de locais, validação compartilhada com eventos e matchers e `no-store` em `SecurityConfig`.
- **Testes:** integração com PostgreSQL de teste para os dois recursos, isolamento e reflexo na agenda pública.
- **Sem mudanças** em schema, migrations, dependências ou frontend.
