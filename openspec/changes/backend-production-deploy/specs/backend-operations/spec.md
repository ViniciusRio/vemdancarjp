# Spec Delta

## Purpose

Definir como o backend de produção é atualizado, revertido, mantido dentro dos planos gratuitos e recriado, para que a perda do ambiente não cause perda de dados enquanto toda a agenda vier de uma fonte versionada.

## ADDED Requirements

### Requirement: Deploy a partir da main
O backend SHALL ser atualizado em produção automaticamente após merge na `main` que altere o backend ou seu workflow de deploy, e somente se os testes do backend passarem. Uma nova versão SHALL receber tráfego somente depois de responder corretamente em um endereço próprio de teste.

#### Scenario: Merge com testes aprovados
- **WHEN** um merge na `main` altera `backend/` ou o workflow de deploy do backend e os testes passam
- **THEN** a nova versão entra em produção e a agenda responde `200` ao final do deploy

#### Scenario: Testes falham
- **WHEN** os testes do backend falham no workflow
- **THEN** nada é publicado e a versão em produção continua a mesma

#### Scenario: Nova versão não sobe
- **WHEN** a nova versão não responde `200` em `/api/agenda` dentro do tempo limite
- **THEN** o workflow falha e o tráfego continua na versão anterior

#### Scenario: Nova versão expõe outras rotas
- **WHEN** a nova versão responde em `/api/admin/events` com qualquer status diferente de `404`
- **THEN** o workflow falha e o tráfego continua na versão anterior

### Requirement: Deploy exclusivo
Somente um deploy, automático ou manual, SHALL atuar em produção por vez. Uma tentativa que falha SHALL manter o tráfego na última versão válida.

#### Scenario: Dois deploys ao mesmo tempo
- **WHEN** um deploy é disparado enquanto outro está em andamento
- **THEN** o segundo só atua em produção depois que o primeiro terminar

#### Scenario: Falha depois de um deploy válido
- **WHEN** um deploy válido termina e o deploy seguinte falha ao subir
- **THEN** a produção continua na versão publicada pelo deploy válido

### Requirement: Rollback por versão anterior
Vinícius SHALL poder voltar o backend a uma imagem já publicada, sem novo build.

#### Scenario: Rollback manual
- **WHEN** o workflow de deploy é disparado manualmente com o digest de uma imagem anterior
- **THEN** essa imagem passa a rodar em produção, desde que seja compatível com o schema atual do banco

### Requirement: Credencial de automação limitada
A credencial usada pelo workflow de deploy SHALL permitir somente o deploy do serviço do backend, a partir da `main` deste repositório, sem chave de longa duração guardada no GitHub e sem acesso ao banco.

#### Scenario: Execução fora da main
- **WHEN** um workflow de outra branch ou de outro repositório tenta obter a credencial
- **THEN** o provedor recusa a credencial

#### Scenario: Operação fora do previsto
- **WHEN** a credencial tenta alterar outro serviço ou ler a senha do banco
- **THEN** o provedor recusa a operação

### Requirement: Segredos fora do repositório
Senhas e chaves de produção SHALL ficar somente no gerenciador de segredos do provedor, nunca em arquivos versionados, logs, imagens ou na configuração visível do serviço.

#### Scenario: Senha indisponível
- **WHEN** uma nova versão é iniciada sem acesso à senha do banco
- **THEN** ela não recebe tráfego, e a senha padrão de desenvolvimento não é aceita pela base de produção

### Requirement: Custo dentro dos planos gratuitos
A produção SHALL escalar a zero quando parada, SHALL usar no máximo uma instância e SHALL ter um alerta de orçamento ativo. Atingir um limite de plano gratuito do banco SHALL interromper o serviço, sem perda de dados e sem gerar cobrança no banco.

#### Scenario: Sem acessos
- **WHEN** a API passa um período sem requisições
- **THEN** nenhuma instância fica cobrando CPU ou memória

#### Scenario: Pico de acessos
- **WHEN** chegam mais requisições do que uma instância consegue atender
- **THEN** o serviço não cria uma segunda instância, mesmo que algumas requisições falhem

#### Scenario: Gasto acima de zero
- **WHEN** o projeto no provedor registra qualquer cobrança acima do valor configurado no alerta
- **THEN** Vinícius recebe um aviso do alerta de orçamento

#### Scenario: Limite do banco atingido
- **WHEN** o banco atinge o limite mensal do plano gratuito
- **THEN** a API responde com erro, a home mostra a mensagem de erro de carregamento e nenhum dado é perdido

### Requirement: Base recriável a partir do repositório
O conteúdo da agenda em produção SHALL ser sempre reproduzível pela carga versionada, de modo que nenhuma correção chegue ao banco sem antes estar nessa carga.

#### Scenario: Recriação em um ambiente novo
- **WHEN** a produção é recriada em um ambiente novo, com banco vazio, seguindo a documentação
- **THEN** depois da carga versionada, `GET /api/agenda` retorna os mesmos dados da produção anterior

#### Scenario: Correção na agenda
- **WHEN** um dado da agenda precisa ser corrigido em produção
- **THEN** a correção é versionada e revisada na carga antes de ser aplicada ao banco

#### Scenario: Conferência depois da correção
- **WHEN** uma correção é aplicada ao banco de produção
- **THEN** uma comparação automatizada confirma que `GET /api/agenda` retorna os mesmos dados da carga versionada

### Requirement: Produção portátil
A imagem de produção SHALL funcionar em qualquer servidor `amd64` com um PostgreSQL padrão, sem configuração específica do provedor na aplicação, para permitir a recriação em outro ambiente.

#### Scenario: Subida fora do provedor
- **WHEN** a mesma imagem é iniciada em um servidor `amd64` com banco vazio e a carga versionada
- **THEN** `GET /api/agenda` responde `200` com os dados da carga
