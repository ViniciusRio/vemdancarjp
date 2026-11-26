# Spec Delta

## MODIFIED Requirements

### Requirement: Isolamento das demais rotas administrativas
Dentro de `/api/admin/events/**`, a autorização administrativa SHALL valer apenas para `POST /api/admin/events`, `GET /api/admin/events`, `PUT /api/admin/events/{id}` e `DELETE /api/admin/events/{id}`. As demais combinações de método e caminho nesse prefixo SHALL continuar bloqueadas, inclusive para sessões `APPROVED`. Rotas fora desse prefixo são regidas pelos requisitos de suas próprias capabilities.

#### Scenario: Administrador aprovado tenta outra rota
- **WHEN** uma sessão aprovada solicita uma operação de eventos fora dessas quatro, como `GET /api/admin/events/{id}`, `PATCH /api/admin/events/{id}` ou `PUT /api/admin/events`
- **THEN** o sistema nega a requisição e não executa a operação

#### Scenario: Leitura pública continua aberta
- **WHEN** um visitante sem sessão solicita `GET /api/agenda`
- **THEN** o sistema mantém a resposta pública disponível

### Requirement: Autorização restrita da listagem
O sistema SHALL permitir `GET /api/admin/events` apenas com sessão Google OIDC válida e aprovação `APPROVED` vigente consultada no backend para o `sub` validado. Essa liberação SHALL evoluir a regra anterior que bloqueava o GET, sem alterar o POST, o PUT e o DELETE autorizados por seus próprios requisitos nem liberar outras operações em `/api/admin/events/**`.

#### Scenario: Sessão aprovada sem CSRF
- **WHEN** uma sessão Google OIDC aprovada faz `GET /api/admin/events` sem token CSRF
- **THEN** o sistema retorna a listagem com `200` e não altera eventos

#### Scenario: Sessão ausente
- **WHEN** a requisição não possui sessão autenticada válida
- **THEN** o sistema responde `401` com `{"error":"UNAUTHENTICATED"}` e não divulga eventos

#### Scenario: Identidade sem aprovação vigente
- **WHEN** a identidade autenticada não é Google OIDC validada, não tem registro aprovado ou possui estado diferente de `APPROVED`
- **THEN** o sistema responde `403` com `{"error":"FORBIDDEN"}` e não divulga eventos

#### Scenario: Revogação na mesma sessão
- **WHEN** uma sessão aprovada consulta a lista, sua aprovação deixa de ser `APPROVED` no banco e a mesma sessão faz outro GET sem novo login
- **THEN** a segunda requisição recebe `403` com `{"error":"FORBIDDEN"}` e não divulga eventos

#### Scenario: Falha na consulta de aprovação
- **WHEN** a consulta ao estado administrativo falha por indisponibilidade de infraestrutura
- **THEN** a requisição não recebe a lista nem é tratada como ausência normal de aprovação; a falha produz erro `5xx`

#### Scenario: Outras operações administrativas permanecem bloqueadas
- **WHEN** uma sessão aprovada solicita, dentro de `/api/admin/events/**`, um método ou caminho fora de `GET` e `POST /api/admin/events` e de `PUT` e `DELETE /api/admin/events/{id}`
- **THEN** o sistema nega a requisição e não executa a operação
