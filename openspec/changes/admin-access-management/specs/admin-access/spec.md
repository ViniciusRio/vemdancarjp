# Spec Delta

## Purpose

Controlar quem é administrador: criar o primeiro admin de forma controlada, receber pedidos de acesso e permitir que admins aprovados aprovem, rejeitem e revoguem acessos, sem nenhum caminho de autoaprovação.

## ADDED Requirements

### Requirement: Primeiro administrador
Ao iniciar, se `APP_BOOTSTRAP_ADMIN_SUB` estiver definida, não vazia e com formato válido, e não existir nenhum registro `APPROVED`, o sistema SHALL deixar `APPROVED` o registro com aquele `sub`, criando-o se não existir. Se já existir algum registro `APPROVED`, a variável SHALL ser ignorada, sem alterar nenhum registro. O valor do `sub` não SHALL aparecer em logs.

#### Scenario: Base sem admin
- **WHEN** o sistema inicia com a variável definida e nenhum registro `APPROVED`
- **THEN** existe exatamente um registro `APPROVED`, com aquele `sub`

#### Scenario: Pedido pendente do mesmo sub
- **WHEN** o sistema inicia com a variável definida, nenhum `APPROVED` e um registro `PENDING` com o mesmo `sub`
- **THEN** esse registro passa a `APPROVED`, sem criar outro

#### Scenario: Já existe admin
- **WHEN** o sistema inicia com a variável definida para outro `sub` e já existe um registro `APPROVED`
- **THEN** nenhum registro é criado nem alterado

#### Scenario: Variável ausente
- **WHEN** o sistema inicia sem a variável ou com ela vazia
- **THEN** nenhum registro é criado nem alterado, e a aplicação sobe normalmente

#### Scenario: Formato inválido
- **WHEN** o valor da variável tem espaços, caracteres fora de `[A-Za-z0-9]` ou mais de 255 caracteres
- **THEN** a aplicação não sobe, com uma mensagem de erro que não contém o valor, e nenhum registro é criado nem alterado

#### Scenario: Recuperação de sub errado
- **WHEN** um `sub` errado foi aprovado pelo bootstrap, o operador apaga os registros `APPROVED` direto no banco, corrige a variável e reinicia
- **THEN** o `sub` corrigido fica `APPROVED`

### Requirement: Identificador da própria sessão
`GET /api/auth/session` SHALL incluir, para uma sessão Google OIDC autenticada, o campo `subject` com o `sub` da própria sessão, além de `authenticated` e `accessStatus`. É assim que o operador obtém o valor de `APP_BOOTSTRAP_ADMIN_SUB`. Nenhuma outra resposta SHALL expor `sub`.

#### Scenario: Consultar o próprio sub
- **WHEN** um usuário Google autenticado consulta a sessão
- **THEN** a resposta traz `subject` igual ao `sub` do seu token, com `Cache-Control: no-store`

### Requirement: Pedido de acesso
`POST /api/auth/access-request` SHALL exigir sessão Google OIDC válida (emissor Google e `sub` presentes) e token CSRF, sem exigir aprovação. Sem registro para o `sub`, SHALL criar um `PENDING` com e-mail e nome do token, mas só se o token tiver `email` e `email_verified` igual a `true`. Com registro existente, não SHALL alterar nada. O corpo da requisição SHALL ser ignorado e nunca altera status, e-mail, nome ou `sub`.

#### Scenario: Primeiro pedido
- **WHEN** um usuário Google sem registro envia o pedido com CSRF válido
- **THEN** o sistema cria um registro `PENDING` com e-mail e nome do token e responde `201 {"accessStatus":"PENDING"}`

#### Scenario: Pedido repetido ou de admin
- **WHEN** um usuário que já tem registro `PENDING` ou `APPROVED` envia o pedido, inclusive com um corpo tentando mudar o status
- **THEN** o sistema responde `200` com o status atual e não altera o registro

#### Scenario: Sem sessão Google válida
- **WHEN** o pedido chega sem sessão, com identidade não OIDC ou com emissor diferente do Google
- **THEN** o sistema responde `401 {"error":"UNAUTHENTICATED"}` sem sessão, ou `403 {"error":"FORBIDDEN"}` nos demais casos, e não cria registro

#### Scenario: E-mail não verificado
- **WHEN** um usuário Google sem registro envia o pedido e o token não tem `email` ou tem `email_verified` diferente de `true`
- **THEN** o sistema responde `403 {"error":"EMAIL_NOT_VERIFIED"}` e não cria registro

#### Scenario: Sem CSRF válido
- **WHEN** uma sessão Google envia o pedido sem token CSRF válido
- **THEN** o sistema responde `403 {"error":"CSRF_INVALID"}` e não cria registro

### Requirement: Rotas de gestão de acesso
O sistema SHALL liberar só três rotas de gestão: `GET /api/admin/access`, `PUT /api/admin/access/{id}` e `DELETE /api/admin/access/{id}`. Elas SHALL exigir sessão Google OIDC com aprovação `APPROVED` vigente, consultada no banco a cada requisição. `PUT` e `DELETE` SHALL exigir também token CSRF. As demais combinações de método e caminho em `/api/admin/access/**`, inclusive `POST /api/admin/access`, SHALL ser negadas com `403`.

#### Scenario: Sem aprovação
- **WHEN** uma das três rotas recebe uma requisição sem sessão, de identidade não aprovada ou de sessão revogada no meio do uso
- **THEN** o sistema responde `401` ou `403` com os corpos `UNAUTHENTICATED` ou `FORBIDDEN` e não lê nem altera registros

#### Scenario: Escrita sem CSRF
- **WHEN** um admin aprovado envia `PUT` ou `DELETE` sem token CSRF válido
- **THEN** o sistema responde `403 {"error":"CSRF_INVALID"}` e não altera registros

#### Scenario: Rota não liberada
- **WHEN** um admin aprovado envia, com CSRF válido, `POST /api/admin/access`, `GET` ou `PATCH` num item, ou um subcaminho do item
- **THEN** o sistema responde `403 {"error":"FORBIDDEN"}` e não altera registros

#### Scenario: Falha na consulta de aprovação
- **WHEN** a consulta ao estado administrativo falha por indisponibilidade de infraestrutura
- **THEN** o sistema responde com erro `5xx`, sem apresentar a falha como `403`, e não altera registros

### Requirement: Listagem de acessos
`GET /api/admin/access` SHALL responder `200` com um array de todos os registros. Os `PENDING` vêm primeiro e os `APPROVED` depois. Dentro de cada grupo, a ordem é por `requestedAt` crescente. Cada item SHALL ter exatamente `id`, `email`, `name`, `status` e `requestedAt` (ISO-8601 em UTC), com nulos explícitos. O `email` de um pedido é sempre verificado pelo Google. Só registros criados pelo bootstrap podem ter `email` nulo. O `sub` Google não SHALL aparecer.

#### Scenario: Lista mista
- **WHEN** existem registros pendentes e aprovados criados em momentos diferentes
- **THEN** a resposta traz os pendentes antes dos aprovados, cada grupo em ordem de pedido, sem o campo de `sub`

### Requirement: Aprovação
`PUT /api/admin/access/{id}` SHALL aceitar só o corpo `{"status":"APPROVED"}`. Para um registro `PENDING`, SHALL torná-lo `APPROVED` e responder `200` com o item no formato da listagem. Para um registro já `APPROVED`, SHALL responder `200` sem alterar nada. Para um registro inexistente, SHALL responder `404 {"error":"NOT_FOUND"}`. Qualquer outro corpo SHALL receber `400 {"error":"VALIDATION_ERROR"}`.

#### Scenario: Aprovar pendente
- **WHEN** um admin aprovado aprova um pedido pendente
- **THEN** o registro passa a `APPROVED`, e a pessoa passa a ter acesso às rotas administrativas na próxima requisição, sem novo login

#### Scenario: Aprovações simultâneas
- **WHEN** dois admins aprovam o mesmo pedido ao mesmo tempo
- **THEN** as duas respostas são `200`, e existe um único registro `APPROVED` para aquele `sub`

#### Scenario: Corpo inválido
- **WHEN** o corpo pede `PENDING`, outro status, inclui campos extras como `email` ou `googleSub`, não é um objeto ou é JSON malformado
- **THEN** o sistema responde `400 {"error":"VALIDATION_ERROR"}` e não altera o registro

### Requirement: Rejeição e revogação
`DELETE /api/admin/access/{id}` SHALL apagar o registro, rejeitando um pedido `PENDING` ou revogando um `APPROVED`, e responder `204` sem corpo, inclusive quando o registro já não existe. O sistema SHALL recusar com `409 {"error":"CANNOT_REMOVE_SELF"}` a remoção do registro do próprio admin que faz a requisição. SHALL recusar com `409 {"error":"LAST_ADMIN"}` a remoção que deixaria a base sem nenhum `APPROVED`. A segunda regra SHALL valer também quando duas remoções correm ao mesmo tempo.

#### Scenario: Rejeitar pedido
- **WHEN** um admin aprovado remove um pedido `PENDING`
- **THEN** o registro deixa de existir, a resposta é `204`, e a pessoa pode pedir acesso de novo

#### Scenario: Revogar admin com sessão ativa
- **WHEN** um admin revoga outro admin que está com uma sessão aberta
- **THEN** a próxima requisição administrativa dessa sessão recebe `403`, sem precisar que a sessão expire

#### Scenario: Remover a si mesmo
- **WHEN** um admin tenta remover o próprio registro
- **THEN** o sistema responde `409 {"error":"CANNOT_REMOVE_SELF"}` e mantém o registro

#### Scenario: Revogações cruzadas simultâneas
- **WHEN** existem exatamente dois admins e cada um tenta, ao mesmo tempo, revogar o outro
- **THEN** no máximo uma revogação é concluída, a outra recebe `409 {"error":"LAST_ADMIN"}`, e continua existindo pelo menos um `APPROVED`

### Requirement: Reconferência de quem executa
Na mesma transação de cada aprovação ou remoção, o sistema SHALL reconferir e travar o registro `APPROVED` de quem faz a requisição. Se ele não existir mais, o sistema SHALL responder `403 {"error":"FORBIDDEN"}` sem alterar nada. Isso fecha a janela entre a autorização e a escrita.

#### Scenario: Executor revogado durante a aprovação
- **WHEN** a aprovação de B já passou pela autorização e, antes de gravar, outro admin revoga B
- **THEN** a aprovação de B recebe `403 {"error":"FORBIDDEN"}`, e o pedido continua `PENDING`

#### Scenario: Executor revogado durante a remoção
- **WHEN** a remoção feita por B já passou pela autorização e, antes de gravar, outro admin revoga B
- **THEN** a remoção de B recebe `403 {"error":"FORBIDDEN"}` e não apaga nada

### Requirement: Identificador de acesso
O segmento `{id}` SHALL ser validado como aparece na URI, sem decodificação, e SHALL ser um UUID canônico em minúsculas (`8-4-4-4-12` caracteres hexadecimais). A validação do identificador e do corpo SHALL ocorrer depois da autorização e antes de qualquer leitura ou escrita em `admin_access` além da própria checagem de aprovação.

#### Scenario: Identificador inválido
- **WHEN** um admin aprovado usa `{id}` em maiúsculas, sem hífens, com codificação percentual, numérico ou com tamanho errado
- **THEN** o sistema responde `400 {"error":"VALIDATION_ERROR"}` e não altera registros

### Requirement: Respostas de acesso sem cache
Todas as respostas de `POST /api/auth/access-request` e das três rotas de gestão SHALL incluir exatamente `Cache-Control: no-store`, inclusive nos erros e nas falhas de CSRF.

#### Scenario: Qualquer resultado
- **WHEN** qualquer uma dessas rotas responde, com sucesso ou erro
- **THEN** a resposta contém exatamente `Cache-Control: no-store`
