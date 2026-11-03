# Spec Delta

## Purpose

Definir como o backend de produção é atualizado, recuperado e vigiado, para que uma falha de servidor ou do provedor gratuito não cause perda de dados nem queda longa sem aviso.

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
A credencial usada pelos workflows SHALL permitir no servidor somente as operações previstas de deploy e backup, e não SHALL dar acesso a um shell.

#### Scenario: Comando fora do previsto
- **WHEN** a chave de automação tenta executar qualquer comando diferente da operação associada a ela
- **THEN** o servidor não executa esse comando

### Requirement: Segredos fora do repositório
Senhas e chaves de produção SHALL ficar somente no servidor ou nos secrets do GitHub, nunca em arquivos versionados, logs ou imagens.

#### Scenario: Variável obrigatória ausente
- **WHEN** a produção é iniciada sem a senha do banco configurada
- **THEN** a inicialização falha, sem usar a senha padrão de desenvolvimento

### Requirement: Backup diário fora do provedor
O banco de produção SHALL ter um backup diário criptografado, guardado fora da Oracle e retido por pelo menos 30 dias. Só Vinícius SHALL ter a chave capaz de abri-lo.

#### Scenario: Backup concluído
- **WHEN** o horário do backup chega e o banco está disponível
- **THEN** um arquivo criptografado e não vazio fica guardado fora da Oracle, sem dados em claro em logs ou artefatos

#### Scenario: Backup falha
- **WHEN** o dump ou o envio falha
- **THEN** Vinícius recebe um aviso por email

#### Scenario: Conta do provedor perdida
- **WHEN** a VM e a conta Oracle ficam inacessíveis
- **THEN** o último backup continua disponível para restauração

### Requirement: Restauração comprovada
O procedimento de restauração SHALL estar documentado e ter sido executado com sucesso em um backup real antes de o site passar a usar a API.

#### Scenario: Restauração em outro ambiente
- **WHEN** o backup mais recente é restaurado em um PostgreSQL novo, seguindo a documentação
- **THEN** o backend inicia sobre essa base e `GET /api/agenda` retorna os mesmos dados da produção

### Requirement: Migração de provedor possível
A produção SHALL funcionar em `arm64` e `amd64`, sem configuração específica da Oracle na aplicação, para permitir a mudança para o plano B.

#### Scenario: Subida em servidor amd64
- **WHEN** a mesma versão é iniciada em um servidor `amd64` com o backup restaurado
- **THEN** `GET /api/agenda` responde `200` com os dados restaurados

### Requirement: Monitoramento da agenda publicada
A agenda publicada SHALL ser verificada periodicamente de fora do servidor, e Vinícius SHALL receber um aviso por email quando a verificação falhar.

#### Scenario: Agenda fora do ar
- **WHEN** a API não responde `200`
- **THEN** a verificação falha e Vinícius recebe o aviso

#### Scenario: Agenda vazia
- **WHEN** a API responde `200` sem nenhum evento
- **THEN** a verificação falha e Vinícius recebe o aviso

#### Scenario: CORS incorreto
- **WHEN** a resposta não autoriza a origem do site
- **THEN** a verificação falha e Vinícius recebe o aviso
