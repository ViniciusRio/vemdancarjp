# Spec Delta

## Purpose

Definir como o backend de produção é atualizado, revertido e recriado, para que a perda do servidor não cause perda de dados enquanto toda a agenda vier de uma fonte versionada.

## ADDED Requirements

### Requirement: Deploy a partir da main
O backend SHALL ser atualizado em produção automaticamente após merge na `main` que altere o backend ou sua configuração de deploy, e somente se os testes do backend passarem.

#### Scenario: Merge com testes aprovados
- **WHEN** um merge na `main` altera `backend/` ou `deploy/` e os testes passam
- **THEN** a nova versão entra em produção e a agenda responde `200` ao final do deploy

#### Scenario: Testes falham
- **WHEN** os testes do backend falham no workflow
- **THEN** nada é publicado e a versão em produção continua a mesma

#### Scenario: Nova versão não sobe
- **WHEN** a nova versão não responde `200` em `/api/agenda` dentro do tempo limite
- **THEN** o workflow falha e a versão anterior volta a rodar

### Requirement: Rollback por versão anterior
Vinícius SHALL poder voltar o backend a uma imagem já publicada, sem novo build.

#### Scenario: Rollback manual
- **WHEN** o workflow de deploy é disparado manualmente com o digest de uma imagem anterior
- **THEN** essa imagem passa a rodar em produção, desde que seja compatível com o schema atual do banco

### Requirement: Credencial de automação limitada
A credencial usada pelo workflow de deploy SHALL permitir no servidor somente a operação de deploy e não SHALL dar acesso a um shell nem a serviços internos.

#### Scenario: Comando fora do previsto
- **WHEN** a chave de automação tenta executar qualquer comando diferente da operação de deploy
- **THEN** o servidor não executa esse comando

#### Scenario: Tentativa de túnel
- **WHEN** a chave de automação tenta abrir um encaminhamento de portas até um serviço interno, como o PostgreSQL
- **THEN** o servidor recusa o encaminhamento

### Requirement: Segredos fora do repositório
Senhas e chaves de produção SHALL ficar somente no servidor ou nos secrets do GitHub, nunca em arquivos versionados, logs ou imagens.

#### Scenario: Variável obrigatória ausente
- **WHEN** a produção é iniciada sem a senha do banco configurada
- **THEN** a inicialização falha, sem usar a senha padrão de desenvolvimento

### Requirement: Base recriável a partir do repositório
O conteúdo da agenda em produção SHALL ser sempre reproduzível pela carga versionada, de modo que uma correção manual no banco também entre nessa carga.

#### Scenario: Recriação em servidor novo
- **WHEN** a produção é recriada em um servidor novo, com banco vazio, seguindo a documentação
- **THEN** depois da carga versionada, `GET /api/agenda` retorna os mesmos dados da produção anterior

#### Scenario: Correção manual na agenda
- **WHEN** um dado da agenda é corrigido diretamente no banco de produção
- **THEN** a mesma correção é versionada na carga, antes de a tarefa ser considerada concluída

### Requirement: Migração de provedor possível
A produção SHALL funcionar em `arm64` e `amd64`, sem configuração específica da Oracle na aplicação, para permitir a mudança para o plano B.

#### Scenario: Subida em servidor amd64
- **WHEN** a mesma versão é iniciada em um servidor `amd64` com banco vazio e a carga versionada
- **THEN** `GET /api/agenda` responde `200` com os dados da carga
