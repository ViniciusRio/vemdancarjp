# Spec Delta

## Purpose

Garantir que visitantes do site publicado leiam a agenda pelo backend de produção, expondo na internet somente a leitura pública e nenhuma operação de login ou administração.

## ADDED Requirements

### Requirement: Home publicada carrega a agenda
O site publicado SHALL obter a agenda em `https://api.vemdancarjp.com.br/api/agenda` e exibi-la aos visitantes, sem depender do Supabase para isso.

#### Scenario: Visitante abre a home
- **WHEN** um visitante abre `https://vemdancarjp.com.br/` com o backend disponível
- **THEN** a página exibe os eventos e locais retornados pela API, sem a mensagem de erro de carregamento

#### Scenario: Backend indisponível
- **WHEN** a API não responde ou responde com erro
- **THEN** a página continua abrindo e mostra a mensagem de erro de carregamento

#### Scenario: Supabase inacessível
- **WHEN** o host do Supabase não resolve e a API está disponível
- **THEN** a agenda é exibida normalmente

### Requirement: Origem permitida para a agenda
A API SHALL autorizar por CORS somente a origem configurada do site na leitura de `GET /api/agenda`, sem permitir credenciais.

#### Scenario: Requisição da origem do site
- **WHEN** `GET /api/agenda` chega com `Origin: https://vemdancarjp.com.br`
- **THEN** a resposta é `200` com `Access-Control-Allow-Origin: https://vemdancarjp.com.br` e sem `Access-Control-Allow-Credentials`

#### Scenario: Requisição de outra origem
- **WHEN** `GET /api/agenda` chega com uma origem diferente da configurada
- **THEN** a resposta não contém `Access-Control-Allow-Origin` para essa origem

#### Scenario: Requisição sem origem
- **WHEN** `GET /api/agenda` chega sem cabeçalho `Origin`
- **THEN** a resposta é `200` com a agenda

#### Scenario: Rotas administrativas não recebem CORS
- **WHEN** uma requisição para `/api/admin/events` ou `/api/auth/session` chega com a origem do site
- **THEN** a resposta não contém cabeçalhos de CORS que autorizem essa origem

### Requirement: Superfície pública restrita
Em produção, o domínio da API SHALL encaminhar à aplicação somente `GET /api/agenda` e SHALL recusar todo o resto, enquanto a administração não for publicada por uma change própria.

#### Scenario: Rota administrativa em produção
- **WHEN** alguém acessa `/api/admin/events`, `/api/auth/session`, `/api/auth/csrf` ou `/oauth2/authorization/google` no domínio da API
- **THEN** a resposta é `404` e a requisição não chega à aplicação

#### Scenario: Método diferente de GET na agenda
- **WHEN** alguém envia `POST` ou `DELETE` para `/api/agenda` no domínio da API
- **THEN** a resposta é um erro e nenhum dado é alterado

#### Scenario: Banco não exposto
- **WHEN** alguém tenta conectar na porta do PostgreSQL pelo IP público do servidor
- **THEN** a conexão não é aceita

### Requirement: Acesso somente por HTTPS
O domínio da API SHALL responder por HTTPS com certificado válido e renovação automática.

#### Scenario: Acesso por HTTP
- **WHEN** alguém acessa `http://api.vemdancarjp.com.br/api/agenda`
- **THEN** a resposta redireciona para o endereço HTTPS equivalente

### Requirement: Conteúdo inicial revisado
Antes de o site passar a usar a API, a base de produção SHALL conter exatamente os eventos e locais da fonte revisada e aprovada por Vinícius.

#### Scenario: Carga em base vazia
- **WHEN** a carga inicial é executada com as três tabelas da agenda vazias
- **THEN** todas as linhas são gravadas em uma única transação, e `GET /api/agenda` retorna os mesmos dados da fonte, na mesma ordem

#### Scenario: Carga repetida
- **WHEN** a carga inicial é executada com qualquer uma das três tabelas já preenchida
- **THEN** a carga falha sem alterar nenhuma linha

#### Scenario: Contas legadas não são importadas
- **WHEN** a carga inicial é concluída
- **THEN** `admin_access`, `admins` e `pending_admins` continuam vazias
