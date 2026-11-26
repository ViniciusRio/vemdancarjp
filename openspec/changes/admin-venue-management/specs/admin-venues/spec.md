# Spec Delta

## Purpose

Permitir que administradores aprovados listem, criem, editem e removam os locais de dias variáveis e os outros locais exibidos na agenda pública, com as mesmas garantias de autorização, CSRF e cache das rotas de eventos.

## ADDED Requirements

### Requirement: Recursos de locais
O sistema SHALL expor dois recursos administrativos de locais, cada um com quatro operações: `GET` e `POST` na coleção e `PUT` e `DELETE` no item `{id}`. Os recursos são `/api/admin/variable-venues`, para os locais de dias variáveis, e `/api/admin/other-venues`, para os outros locais. Nenhuma outra combinação de método e caminho SHALL ser liberada dentro de `/api/admin/variable-venues/**` ou `/api/admin/other-venues/**`.

#### Scenario: Rota não liberada
- **WHEN** uma sessão aprovada solicita, com CSRF válido, `GET` ou `PATCH` num item, `PUT` ou `DELETE` na coleção, um subcaminho do item ou o item com barra final
- **THEN** o sistema nega a requisição com `403 {"error":"FORBIDDEN"}` e não altera dados

### Requirement: Autorização e CSRF dos locais
As oito rotas SHALL exigir sessão Google OIDC válida e aprovação `APPROVED` vigente consultada no banco para o `sub` validado a cada requisição. `POST`, `PUT` e `DELETE` SHALL exigir também o token CSRF da sessão no cabeçalho `X-CSRF-TOKEN`. `GET` não exige CSRF.

#### Scenario: Sessão ausente
- **WHEN** a requisição não possui sessão autenticada válida
- **THEN** o sistema responde `401` com `{"error":"UNAUTHENTICATED"}` e não lê nem altera locais

#### Scenario: Identidade sem aprovação vigente
- **WHEN** o usuário autenticado não tem aprovação `APPROVED` atual, inclusive após revogação na mesma sessão, ou não possui identidade Google OIDC validada
- **THEN** o sistema responde `403` com `{"error":"FORBIDDEN"}` e não lê nem altera locais

#### Scenario: CSRF ausente ou inválido em escrita
- **WHEN** uma sessão autenticada envia `POST`, `PUT` ou `DELETE` sem token CSRF válido ou com token de outra sessão
- **THEN** o sistema responde `403` com `{"error":"CSRF_INVALID"}` e não altera locais

#### Scenario: Falha na consulta de aprovação
- **WHEN** a consulta ao estado administrativo falha por indisponibilidade de infraestrutura
- **THEN** o sistema não lê nem altera locais e responde com erro `5xx`, sem apresentar a falha como `403`

### Requirement: Campos dos locais
O corpo de `POST` e `PUT` SHALL ser um objeto JSON. Para um local de dias variáveis, `name`, `neighborhood`, `days` e `sortOrder` são obrigatórios. Para um outro local, `name`, `neighborhood` e `sortOrder` são obrigatórios. Nos dois, `instagram` é opcional ou nulo. `days` SHALL ser um texto livre, sem formato imposto. Qualquer campo fora do contrato do recurso, inclusive `id`, SHALL ser rejeitado.

#### Scenario: Campo fora do contrato
- **WHEN** uma escrita autorizada envia `days` num outro local, omite `days` num local variável ou inclui `id` no corpo
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não altera locais

### Requirement: Validação dos locais
O sistema SHALL aparar espaços nas bordas dos textos, rejeitar obrigatórios vazios depois disso e persistir `instagram` vazio como `null`. `instagram` SHALL aceitar só URL absoluta HTTP(S) com host, e `sortOrder` SHALL ser um inteiro JSON de 32 bits. Caractere NUL, JSON malformado e tipos incompatíveis SHALL ser rejeitados.

#### Scenario: Valor inválido
- **WHEN** uma escrita autorizada envia `instagram` sem URL, `sortOrder` fracionário, texto obrigatório só com espaços ou JSON malformado
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não altera locais

#### Scenario: Espaços e opcionais
- **WHEN** uma escrita autorizada envia textos com espaços nas bordas e `instagram` só com espaços
- **THEN** o sistema persiste os textos aparados e `instagram` como `null`

### Requirement: Listagem de locais
O `GET` da coleção SHALL responder `200` com um array de todos os locais do recurso, ordenados por `sortOrder` crescente. Cada item traz `id` e todos os campos do contrato, com `instagram` nulo explícito. Não há filtros, e a ordem entre locais com o mesmo `sortOrder` não é garantida.

#### Scenario: Lista ordenada
- **WHEN** um admin aprovado lista um recurso com locais de `sortOrder` diferentes
- **THEN** a resposta traz todos eles em ordem crescente de `sortOrder`, e uma lista vazia vira `[]`

### Requirement: Criação de local
O `POST` da coleção SHALL criar exatamente um local e responder `201` com JSON contendo só `id`.

#### Scenario: Local criado
- **WHEN** um admin aprovado envia, com CSRF válido, um corpo válido
- **THEN** o sistema cria um local e responde `201` com `{"id":<id gerado>}`

### Requirement: Edição de local
O `PUT` do item SHALL substituir todos os campos do local e responder `200` com o local completo. Edições concorrentes não são detectadas: a última gravação prevalece.

#### Scenario: Local editado
- **WHEN** um admin aprovado envia, com CSRF válido, um corpo válido para um local existente sem `instagram`
- **THEN** o sistema grava os novos valores, grava `instagram` como `null` e responde `200` com o local completo

#### Scenario: Edição de local inexistente
- **WHEN** uma edição autorizada, com `{id}` e corpo válidos, aponta para um local que não existe
- **THEN** o sistema responde `404` com `{"error":"NOT_FOUND"}` e não cria local

### Requirement: Remoção de local
O `DELETE` do item SHALL apagar o local e responder `204` sem corpo, inclusive quando ele já não existe.

#### Scenario: Remoção repetida
- **WHEN** um admin aprovado remove um local e repete a remoção
- **THEN** as duas respostas são `204` sem corpo, e só aquele local foi apagado

### Requirement: Identificador dos locais
O segmento `{id}` SHALL ser validado como aparece na URI, sem decodificação: um inteiro decimal positivo que caiba em 64 bits, sem sinal, espaços, zeros à esquerda ou codificação percentual. A validação do identificador e do corpo SHALL ocorrer depois da autorização e antes de qualquer leitura ou escrita em locais.

#### Scenario: Identificador inválido
- **WHEN** uma requisição autorizada usa `{id}` igual a `0`, `-1`, `abc`, `007`, `%31` ou acima do limite de 64 bits
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não lê, altera nem apaga locais

### Requirement: Respostas de locais sem cache
Todas as respostas das oito rotas SHALL incluir `Cache-Control: no-store`, inclusive `400`, `401`, `403`, `404` e `5xx`.

#### Scenario: Qualquer resultado
- **WHEN** uma das oito rotas responde com sucesso ou com qualquer erro, inclusive falha de CSRF
- **THEN** a resposta contém exatamente `Cache-Control: no-store`

### Requirement: Agenda pública reflete os locais
`GET /api/agenda` SHALL refletir criações, edições e remoções de locais já concluídas, sem mudar seu formato nem sua ordenação.

#### Scenario: Mudanças aparecem na agenda
- **WHEN** um local é criado, outro é editado e um terceiro é removido, e a agenda pública é consultada
- **THEN** a agenda mostra o local novo e os valores editados na ordem de `sortOrder` e não mostra o removido

### Requirement: Demais rotas administrativas bloqueadas
Rotas em `/api/admin/**` fora de `/api/admin/events/**`, `/api/admin/variable-venues/**` e `/api/admin/other-venues/**` SHALL continuar bloqueadas, inclusive para sessões `APPROVED`.

#### Scenario: Rota administrativa desconhecida
- **WHEN** uma sessão aprovada solicita, com CSRF válido, uma rota como `GET /api/admin/admins` ou `POST /api/admin/venues`
- **THEN** o sistema nega a requisição com `403` e não executa nenhuma operação
