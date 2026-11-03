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
- **WHEN** a nova versão responde com status diferente de `404` a qualquer rota ou método da checagem de superfície, ou sua configuração não contém `APP_PUBLIC_SURFACE=agenda-only`
- **THEN** o workflow falha e o tráfego continua na versão anterior

#### Scenario: Endereço de teste depois do deploy
- **WHEN** um deploy termina, com sucesso ou com falha
- **THEN** o endereço de teste da revisão candidata deixa de responder pela aplicação

#### Scenario: Falha depois da troca de tráfego
- **WHEN** a agenda não responde `200`, ou a superfície não é a esperada, depois de o tráfego ir para a nova versão
- **THEN** o workflow devolve o tráfego à divisão registrada antes do deploy, confere a agenda e termina com falha

#### Scenario: Cancelamento durante a troca
- **WHEN** o workflow é cancelado depois de iniciar a troca de tráfego
- **THEN** o tráfego volta à divisão registrada antes do deploy

### Requirement: Migrations compatíveis com a versão em produção
Uma nova versão SHALL aplicar ao banco de produção somente migrations com as quais a imagem em produção continue funcionando.

#### Scenario: Migration incompatível
- **WHEN** a imagem em produção não responde `200` em `/api/agenda` sobre um banco de teste com as migrations da nova versão
- **THEN** o workflow falha antes de criar a revisão, e o banco de produção não é alterado

#### Scenario: Imagem reprovada disparada manualmente
- **WHEN** o workflow manual recebe o digest de uma imagem cujas migrations a imagem em produção não aceita
- **THEN** o workflow falha antes de criar a revisão, e o banco de produção não é alterado

#### Scenario: Falha depois de aplicar a migration
- **WHEN** a nova versão aplica uma migration compatível no banco de produção e depois falha em uma checagem
- **THEN** a versão anterior continua recebendo tráfego e respondendo `200` em `/api/agenda`

#### Scenario: Primeiro deploy
- **WHEN** ainda não existe imagem da aplicação em produção
- **THEN** a checagem de compatibilidade é pulada, e as demais continuam obrigatórias

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
- **THEN** essa imagem passa pelas mesmas checagens no endereço de teste e, se aprovada, passa a rodar em produção

#### Scenario: Imagem incompatível com o schema atual
- **WHEN** a imagem alvo do rollback não sobe ou não responde `200` em `/api/agenda` com o schema atual do banco
- **THEN** o workflow falha e o tráfego continua na versão em produção

#### Scenario: Imagem de outra origem
- **WHEN** o workflow manual recebe uma imagem que não seja um digest do pacote deste repositório
- **THEN** o workflow falha sem criar revisão

### Requirement: Credencial de automação limitada
A credencial usada pelo workflow de deploy SHALL permitir somente o deploy do serviço do backend, a partir da `main` deste repositório, sem chave de longa duração guardada no GitHub e sem acesso direto ao banco. Como a imagem publicada pode ler a senha do banco durante a execução, o acesso ao deploy SHALL ser tratado como acesso indireto a essa senha e limitado a merge revisado na `main` protegida ou a disparo manual por Vinícius.

#### Scenario: Execução fora da main
- **WHEN** um workflow de outra branch ou de outro repositório tenta obter a credencial
- **THEN** o provedor recusa a credencial

#### Scenario: Operação fora do previsto
- **WHEN** a credencial tenta alterar outro serviço ou ler diretamente a senha do banco
- **THEN** o provedor recusa a operação

#### Scenario: Alteração direta na main
- **WHEN** alguém tenta enviar um commit para a `main` sem PR
- **THEN** o GitHub recusa o envio, e nenhum deploy é iniciado

### Requirement: Segredos fora do repositório
Senhas e chaves de produção SHALL ficar somente no gerenciador de segredos do provedor, nunca em arquivos versionados, logs, imagens ou na configuração visível do serviço.

#### Scenario: Senha indisponível
- **WHEN** uma nova versão é iniciada sem acesso à senha do banco
- **THEN** ela não recebe tráfego, e a senha padrão de desenvolvimento não é aceita pela base de produção

### Requirement: Custo limitado, sem garantia de zero
A produção SHALL escalar a zero quando parada, SHALL usar no máximo uma instância recebendo tráfego e SHALL ter um alerta de orçamento e um teto de gasto ativos. Essas medidas reduzem o risco, mas não garantem custo zero: o Google pode cobrar pelo uso acima das isenções, inclusive além do alerta e do teto. Atingir um limite de plano gratuito do banco SHALL interromper o serviço, sem perda de dados e sem gerar cobrança no banco.

#### Scenario: Sem acessos
- **WHEN** a API passa um período sem requisições
- **THEN** nenhuma instância fica cobrando CPU ou memória

#### Scenario: Pico de acessos
- **WHEN** chegam mais requisições do que uma instância consegue atender
- **THEN** o serviço não cria uma segunda instância, mesmo que algumas requisições falhem

#### Scenario: Gasto acima de zero
- **WHEN** o projeto no provedor registra qualquer cobrança acima do valor configurado no alerta
- **THEN** Vinícius recebe um aviso do alerta de orçamento, sujeito ao atraso de relatório do provedor

#### Scenario: Ação depois do aviso
- **WHEN** Vinícius recebe um alerta e o gasto não tem explicação
- **THEN** ele consegue desligar o serviço seguindo o guia de operação, e novas requisições deixam de ser atendidas

#### Scenario: Teto de gasto atingido
- **WHEN** o custo estimado do Cloud Run ultrapassa o teto configurado
- **THEN** o provedor pausa o uso novo do serviço, a home mostra a mensagem de erro de carregamento e nenhum dado é perdido

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
