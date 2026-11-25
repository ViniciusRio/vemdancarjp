# Spec Delta

## ADDED Requirements

### Requirement: Edição de evento aprovada
O sistema SHALL aceitar `PUT /api/admin/events/{id}` somente com sessão Google OIDC válida, aprovação `APPROVED` vigente consultada no banco para o `sub` validado a cada requisição e token CSRF da sessão no cabeçalho `X-CSRF-TOKEN`. A edição SHALL substituir todos os campos editáveis do evento.

#### Scenario: Administrador aprovado edita evento
- **WHEN** uma sessão aprovada envia, com CSRF válido, um corpo válido para um `id` existente
- **THEN** o sistema grava os novos valores e responde `200` com o evento completo: `id`, `dayId`, `name`, `venue`, `neighborhood`, `instagram`, `frequency` e `sortOrder`, com os nulos explícitos

#### Scenario: Opcional ausente na edição
- **WHEN** a edição autorizada omite `instagram` ou `frequency`, ou os envia nulos ou só com espaços, para um evento que tinha esses valores
- **THEN** o sistema grava esses campos como `null`

#### Scenario: Sessão ausente
- **WHEN** a requisição não possui sessão autenticada válida
- **THEN** o sistema responde `401` com `{"error":"UNAUTHENTICATED"}` e não altera o evento

#### Scenario: Identidade sem aprovação vigente
- **WHEN** o usuário autenticado não tem aprovação `APPROVED` atual, inclusive após revogação na mesma sessão, ou não possui identidade Google OIDC validada
- **THEN** o sistema responde `403` com `{"error":"FORBIDDEN"}` e não altera o evento

#### Scenario: CSRF ausente ou inválido
- **WHEN** uma sessão autenticada envia a edição sem token CSRF válido ou com token de outra sessão
- **THEN** o sistema responde `403` com `{"error":"CSRF_INVALID"}` e não altera o evento

#### Scenario: Falha na consulta de aprovação
- **WHEN** a consulta ao estado administrativo falha por indisponibilidade de infraestrutura
- **THEN** o sistema não altera o evento e responde com erro `5xx`, sem apresentar a falha como `403`

### Requirement: Contrato e validação da edição
O corpo da edição SHALL seguir o mesmo contrato e as mesmas regras de validação da criação, inclusive aparar espaços, persistir opcionais vazios como `null`, validar a URL do Instagram e rejeitar campos fora do contrato, como `id`.

#### Scenario: Corpo inválido
- **WHEN** uma edição autorizada envia um corpo que a criação rejeitaria, inclusive JSON malformado ou um campo `id` no corpo
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não altera o evento

### Requirement: Remoção de evento aprovada
O sistema SHALL aceitar `DELETE /api/admin/events/{id}` somente com sessão Google OIDC válida, aprovação `APPROVED` vigente consultada no banco a cada requisição e token CSRF da sessão. A remoção SHALL apagar o evento de forma definitiva.

#### Scenario: Administrador aprovado remove evento
- **WHEN** uma sessão aprovada envia, com CSRF válido, a remoção de um `id` existente
- **THEN** o sistema apaga exatamente esse evento e responde `204` sem corpo

#### Scenario: Remoção repetida
- **WHEN** a mesma remoção é enviada de novo depois de concluída
- **THEN** o sistema responde `404` com `{"error":"NOT_FOUND"}`

#### Scenario: Remoção sem autorização
- **WHEN** a remoção chega sem sessão, sem aprovação vigente, sem CSRF válido ou durante falha na consulta de aprovação
- **THEN** o sistema responde `401`, `403` ou `5xx` com os mesmos corpos da edição e não apaga o evento

### Requirement: Identificador do evento
O segmento `{id}` SHALL ser um inteiro decimal positivo que caiba em 64 bits, sem sinal, espaços ou zeros à esquerda. A validação do identificador e do corpo SHALL vir antes da busca do evento.

#### Scenario: Identificador inválido
- **WHEN** uma requisição autorizada usa `{id}` igual a `0`, negativo, não numérico, com zeros à esquerda ou acima do limite de 64 bits
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não altera nem apaga nenhum evento

#### Scenario: Evento inexistente
- **WHEN** uma requisição autorizada, com `{id}` e corpo válidos, aponta para um evento que não existe
- **THEN** o sistema responde `404` com `{"error":"NOT_FOUND"}` e não cria evento

### Requirement: Respostas de edição e remoção sem cache
Todas as respostas de `PUT` e `DELETE` em `/api/admin/events/{id}` SHALL incluir `Cache-Control: no-store`, inclusive `400`, `401`, `403`, `404` e `5xx`.

#### Scenario: Qualquer resultado
- **WHEN** uma edição ou remoção termina com sucesso ou com qualquer erro
- **THEN** a resposta contém `Cache-Control: no-store`

### Requirement: Agenda pública reflete edição e remoção
`GET /api/agenda` SHALL refletir a edição ou remoção já concluída, sem mudar seu formato nem sua ordenação.

#### Scenario: Evento editado muda de dia
- **WHEN** um evento é editado para outro `dayId` e outro `sortOrder` e a agenda pública é consultada
- **THEN** o evento aparece só no novo dia, na posição do novo `sortOrder`, com os campos públicos atualizados

#### Scenario: Evento removido
- **WHEN** um evento é removido e a agenda pública é consultada
- **THEN** o evento não aparece em nenhum dia

## MODIFIED Requirements

### Requirement: Isolamento das demais rotas administrativas
A autorização administrativa SHALL valer apenas para `POST /api/admin/events`, `GET /api/admin/events`, `PUT /api/admin/events/{id}` e `DELETE /api/admin/events/{id}`. Todas as demais combinações de método e caminho em `/api/admin/**` SHALL continuar bloqueadas, inclusive para sessões `APPROVED`.

#### Scenario: Administrador aprovado tenta outra rota
- **WHEN** uma sessão aprovada solicita uma operação administrativa fora dessas quatro, como `GET /api/admin/events/{id}`, `PATCH /api/admin/events/{id}` ou `PUT /api/admin/events`
- **THEN** o sistema nega a requisição e não executa a operação

#### Scenario: Leitura pública continua aberta
- **WHEN** um visitante sem sessão solicita `GET /api/agenda`
- **THEN** o sistema mantém a resposta pública disponível
