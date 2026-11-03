# Tasks

As tarefas marcadas com **[Vinícius]** exigem conta, dados pessoais ou aprovação dele. As demais ficam com o implementador.

## 1. Decisão e pré-requisitos

- [x] 1.1 Escrever o ADR-005 sobre o abandono do Supabase, o descarte dos seus dados e a ausência temporária de backup e monitoramento em `docs/decisions/` e atualizar a seção de Rollback e o Passo 10 do `PLAN.md`; verificar que nenhum trecho ainda prevê reconciliação ou retorno ao Supabase
- [ ] 1.2 Escrever um ADR sobre a escolha de Cloud Run + Neon no lugar da VM Oracle, em `docs/decisions/`, e corrigir o ADR-005 e o `PLAN.md`; verificar com `git grep -i oracle` que só restam referências históricas
- [ ] 1.3 **[Vinícius]** Criar o projeto no Google Cloud com faturamento, um alerta de US$ 1 para o projeto e um teto de gasto (_spend cap_) de US$ 5 para o Cloud Run; verificar que os dois aparecem ativos no console, conferir os preços usados em "Limites de custo" do design e levar qualquer diferença relevante à revisão da spec
- [ ] 1.4 **[Vinícius]** Criar o projeto no Neon, em `aws-us-east-1`, sem cartão; verificar a conexão direta com `psql` usando TLS

## 2. CORS da agenda

- [x] 2.1 Escrever testes de integração para os cenários de "Origem permitida para a agenda" e verificar que falham antes da implementação
- [x] 2.2 Implementar o CORS restrito a `GET /api/agenda`, derivado de `APP_FRONTEND_URL`, e verificar com os testes da 2.1
- [x] 2.3 Cobrir os casos de variável ausente (nenhuma origem autorizada) e URL inválida (falha na inicialização) e verificar com `./gradlew test --tests "*SecurityConfig*" -q`

## 3. Imagem e superfície pública

- [x] 3.1 Criar `backend/Dockerfile` e verificar que a imagem sobe localmente contra um PostgreSQL e responde `GET /api/agenda`
- [ ] 3.2 Escrever testes de integração para os cenários de "Superfície pública restrita", com `APP_PUBLIC_SURFACE=agenda-only` e sem ela, e verificar que falham antes da implementação
- [ ] 3.3 Implementar o filtro antes da cadeia do Spring Security e verificar com os testes da 3.2 e com a imagem local que só `GET /api/agenda` responde
- [ ] 3.4 Remover `deploy/compose.prod.yml`, `deploy/Caddyfile`, `deploy/.env.example`, `deploy/deploy.sh` e `deploy/oracle-create-vm.sh`, e a seção de cópia manual de `docs/deploy/backend-production.md`; verificar com `git grep` que nenhum arquivo ativo os referencia
- [ ] 3.5 Escrever um teste de integração que encerre as conexões do pool com `pg_terminate_backend` e exija `200` em `/api/agenda` na requisição seguinte, usando as configurações de Hikari de produção; verificar que o teste passa e que falha com o banco realmente parado
- [ ] 3.6 Criar `deploy/check-public-surface.sh` com todas as rotas e métodos do design; verificar que aprova a imagem local com `APP_PUBLIC_SURFACE=agenda-only` e reprova a mesma imagem sem a variável
- [ ] 3.7 Criar `deploy/check-schema-compat.sh`; verificar que aprova as imagens atuais e reprova um par em que a nova imagem contém uma migration que remove uma coluna usada pela anterior

## 4. Configuração do Google e deploy automático

- [ ] 4.1 Documentar em `docs/deploy/backend-production.md` a criação do serviço (imagem de exemplo, 1 vCPU, 512 MiB, cobrança por requisição, `--max 1` no serviço e `--max-instances 1` na revisão, nenhuma instância mínima, `--cpu-boost`, sonda em `/api/agenda`, Hikari do design), do segredo e da identidade do workflow, além do desligamento com `--scaling=0` e do retorno; verificar seguindo o documento no projeto do Google, inclusive desligando e religando o serviço
- [ ] 4.2 Configurar a Workload Identity Federation e verificar que a credencial só é obtida a partir da `main` deste repositório, que ela não altera outro serviço nem lê o segredo diretamente e que a ruleset da `main` e a lista de colaboradores continuam como descrito no design
- [ ] 4.3 Reescrever `.github/workflows/deploy-backend.yml` (testes, JAR, imagem `amd64`, GHCR, registro do tráfego e da imagem atuais, checagem de compatibilidade em `push` e em `workflow_dispatch`, revisão sem tráfego por digest, conferência de `APP_PUBLIC_SURFACE`, checagem de superfície no endereço da tag, troca de tráfego, nova checagem depois da troca, restauração em falha ou cancelamento, remoção da tag em qualquer resultado, `concurrency` sem cancelamento, actions por SHA), acionado por `push` na `main` em `backend/**`, `deploy/check-*.sh` e no próprio workflow e por `workflow_dispatch`, que valida o digest e tem a opção `simulate_post_traffic_failure`; verificar com `actionlint`
- [ ] 4.4 Abrir o PR de infraestrutura com o handoff e verificar lint, type-check, testes do frontend e do backend passando
- [ ] 4.5 **[Vinícius]** Fazer o merge do PR de infraestrutura e tornar o pacote do GHCR público, se preciso; verificar que o workflow conclui e que o endereço `run.app` responde `200` em `/api/agenda`
- [ ] 4.6 Fazer um segundo deploy válido, com digest distinto do primeiro, e testar por `workflow_dispatch`, antes do corte do site: uma imagem de teste que passa na checagem de compatibilidade e falha ao subir no Cloud Run, identificado pela variável `K_SERVICE`; uma imagem reprovada pela checagem de compatibilidade; um digest de outra origem; `simulate_post_traffic_failure`; e um rollback real para o primeiro digest da aplicação. Verificar em cada caso a revisão que recebe tráfego, a agenda respondendo `200` e o endereço da tag sem responder pela aplicação
- [ ] 4.7 Rodar a imagem em produção contra um PostgreSQL com uma migration compatível aplicada por uma imagem de teste que depois falha ao subir; verificar que a imagem anterior continua respondendo `200` em `/api/agenda`

## 5. Domínio

- [ ] 5.1 **[Vinícius]** Verificar a propriedade de `vemdancarjp.com.br` no Google e criar o CNAME `api` no Registro.br, conforme o mapeamento do serviço; verificar resolução por DNS público
- [ ] 5.2 Mapear `api.vemdancarjp.com.br` ao serviço, definir a variável `API_PUBLIC_URL` no repositório e verificar o certificado válido, `200` em `/api/agenda`, o redirecionamento de HTTP para HTTPS e a checagem de superfície no domínio em um novo `workflow_dispatch`

## 6. Carga inicial

- [ ] 6.1 **[Vinícius]** Revisar os dados de `public/agenda.json` e aprovar a lista; verificar com a aprovação registrada no PR
- [ ] 6.2 Criar `deploy/seed/agenda-initial.sql` e um teste de integração que comprove carga em base vazia, falha sem alterações em base preenchida e tabelas administrativas vazias; verificar com o teste focado
- [ ] 6.3 Criar um script reutilizável que compare `GET /api/agenda` com a carga versionada, verificando que ele detecta uma divergência simulada
- [ ] 6.4 Executar a carga no Neon com `psql` e verificar com o script da 6.3 que a agenda retorna os mesmos itens, na mesma ordem

## 7. Recriação do ambiente

- [ ] 7.1 Documentar em `docs/deploy/backend-production.md` a recriação do ambiente e o fluxo de correção (versionar, revisar, aplicar e comparar); verificar subindo a mesma imagem em `amd64` com um PostgreSQL novo, fora do Google, com `/api/agenda` retornando os dados da carga

## 8. Corte do site

- [ ] 8.1 Definir `VITE_API_URL` em `.github/workflows/deploy.yml`, mantendo `VITE_SUPABASE_*`, e verificar com um build local que o bundle contém a URL da API
- [ ] 8.2 Atualizar `STATUS.md`, inclusive o bloqueio e o "Não mexer", e abrir o PR de corte com o handoff, com a suíte completa passando
- [ ] 8.3 **[Vinícius]** Fazer o merge do PR de corte e verificar que a home publicada exibe a agenda sem erro de CORS no console, inclusive em um celular

## 9. Validação em produção

- [ ] 9.1 Verificar cada cenário de `public-agenda-access` contra o domínio e o endereço `run.app` (rotas administrativas com 404, HTTP redireciona, banco sem TLS recusado) e registrar o resultado no PR
- [ ] 9.2 Medir o primeiro acesso com a aplicação parada, com e sem `-XX:TieredStopAtLevel=1`, e o tempo de resposta pelo domínio e pelo `run.app`; adotar a opção mais rápida e registrar os valores no PR. Se o domínio for lento demais, levar a decisão a Vinícius
- [ ] 9.3 Conferir após 7 dias o consumo de CU-horas no Neon, de vCPU-segundos e saída de dados no Cloud Run, e o custo no Google; verificar registrando os valores no `STATUS.md` e ajustando o teto de gasto se o valor de lista estiver perto dele
- [ ] 9.4 Fazer uma requisição, esperar o Neon suspender enquanto a mesma instância do Cloud Run segue ativa e repetir a requisição; verificar pelo console do Neon e pelo identificador da instância nos logs que o cenário ocorreu, com `200` na primeira requisição, e registrar no PR

## Workflow follow-up

- Revisão adversarial da spec pelo Codex e aprovação de Vinícius antes de iniciar a implementação.
- Arquivar a change depois da validação em produção.
- Próximas changes: CRUD admin restante, gestão de admins com primeiro admin, e corte do frontend admin com remoção do Supabase.
- Backup, monitoramento, migrations em passo separado e credenciais de banco separadas para migration e leitura são pré-requisitos da change que voltar a permitir escrita pelo painel admin em produção.
