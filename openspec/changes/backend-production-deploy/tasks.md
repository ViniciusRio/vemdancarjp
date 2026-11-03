# Tasks

As tarefas marcadas com **[Vinícius]** exigem conta, dados pessoais ou aprovação dele. As demais ficam com o implementador.

## 1. Decisão e pré-requisitos

- [x] 1.1 Escrever o ADR-005 sobre o abandono do Supabase, o descarte dos seus dados e a ausência temporária de backup e monitoramento em `docs/decisions/` e atualizar a seção de Rollback e o Passo 10 do `PLAN.md`; verificar que nenhum trecho ainda prevê reconciliação ou retorno ao Supabase
- [ ] 1.2 Escrever um ADR sobre a escolha de Cloud Run + Neon no lugar da VM Oracle, em `docs/decisions/`, e corrigir o ADR-005 e o `PLAN.md`; verificar com `git grep -i oracle` que só restam referências históricas
- [ ] 1.3 **[Vinícius]** Criar o projeto no Google Cloud com faturamento e um alerta de orçamento de valor baixo; verificar que o alerta aparece ativo no console
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

## 4. Configuração do Google e deploy automático

- [ ] 4.1 Documentar em `docs/deploy/backend-production.md` a criação do serviço (1 vCPU, 512 MiB, cobrança por requisição, 0 a 1 instância, `--cpu-boost`, sonda em `/api/agenda`), do segredo e da identidade do workflow; verificar seguindo o documento no projeto do Google
- [ ] 4.2 Configurar a Workload Identity Federation e verificar que a credencial só é obtida a partir da `main` deste repositório e que ela não altera outro serviço nem lê o segredo
- [ ] 4.3 Reescrever `.github/workflows/deploy-backend.yml` (testes, JAR, imagem `amd64`, GHCR, revisão sem tráfego por digest, testes de `200` na agenda e `404` em `/api/admin/events`, troca de tráfego, `concurrency` sem cancelamento), acionado por `push` na `main` em `backend/**` e no próprio workflow e por `workflow_dispatch`; verificar com `actionlint`
- [ ] 4.4 Abrir o PR de infraestrutura com o handoff e verificar lint, type-check, testes do frontend e do backend passando
- [ ] 4.5 **[Vinícius]** Fazer o merge do PR de infraestrutura e tornar o pacote do GHCR público, se preciso; verificar que o workflow conclui e que o endereço `run.app` responde `200` em `/api/agenda`
- [ ] 4.6 Testar uma versão que falha ao subir e um rollback real por `workflow_dispatch` para o digest anterior; verificar que o tráfego fica na versão válida e conferir a versão em execução

## 5. Domínio

- [ ] 5.1 **[Vinícius]** Verificar a propriedade de `vemdancarjp.com.br` no Google e criar o CNAME `api` no Registro.br, conforme o mapeamento do serviço; verificar resolução por DNS público
- [ ] 5.2 Mapear `api.vemdancarjp.com.br` ao serviço e verificar o certificado válido, `200` em `/api/agenda` e o redirecionamento de HTTP para HTTPS

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
- [ ] 9.3 Conferir após 7 dias o consumo de CU-horas no Neon, de vCPU-segundos no Cloud Run e o custo no Google; verificar registrando os valores no `STATUS.md`, inclusive se conexões ociosas impedem a suspensão do Neon

## Workflow follow-up

- Revisão adversarial da spec pelo Codex e aprovação de Vinícius antes de iniciar a implementação.
- Arquivar a change depois da validação em produção.
- Próximas changes: CRUD admin restante, gestão de admins com primeiro admin, e corte do frontend admin com remoção do Supabase.
- Backup e monitoramento são pré-requisitos da change que voltar a permitir escrita pelo painel admin em produção.
