# Tasks

As tarefas marcadas com **[Vinícius]** exigem conta, dados pessoais ou aprovação dele. As demais ficam com o implementador.

## 1. Decisão e pré-requisitos

- [x] 1.1 Escrever o ADR-005 sobre o abandono do Supabase, o descarte dos seus dados e a ausência temporária de backup e monitoramento em `docs/decisions/` e atualizar a seção de Rollback e o Passo 10 do `PLAN.md`; verificar que nenhum trecho ainda prevê reconciliação ou retorno ao Supabase
- [ ] 1.2 **[Vinícius]** Criar a VM Ampere A1 (1 OCPU, 2 GB, Ubuntu LTS `arm64`) na conta Oracle, usando `deploy/oracle-create-vm.sh` enquanto faltar capacidade; verificar com acesso SSH por chave
- [ ] 1.3 **[Vinícius]** Criar o registro `api.vemdancarjp.com.br` no Registro.br apontando para o IP da VM; verificar resolução por DNS público

## 2. CORS da agenda

- [x] 2.1 Escrever testes de integração para os cenários de "Origem permitida para a agenda" e verificar que falham antes da implementação
- [x] 2.2 Implementar o CORS restrito a `GET /api/agenda`, derivado de `APP_FRONTEND_URL`, e verificar com os testes da 2.1
- [x] 2.3 Cobrir os casos de variável ausente (nenhuma origem autorizada) e URL inválida (falha na inicialização) e verificar com `./gradlew test --tests "*SecurityConfig*" -q`

## 3. Imagem e ambiente de produção

- [x] 3.1 Criar `backend/Dockerfile` e verificar que a imagem sobe localmente contra um PostgreSQL e responde `GET /api/agenda`
- [x] 3.2 Criar `deploy/compose.prod.yml` com limites de memória, volumes persistentes, sem portas de banco publicadas e com `${VAR:?}` nas variáveis obrigatórias; verificar com `docker compose config` que a ausência da senha falha
- [x] 3.3 Criar `deploy/Caddyfile` e verificar localmente que só `GET /api/agenda` chega à aplicação e que as demais rotas e métodos recebem erro, conforme "Superfície pública restrita"
- [x] 3.4 Criar `deploy/.env.example` sem valores reais e verificar que `.env` de produção está coberto pelo `.gitignore`
- [ ] 3.5 Documentar em `docs/deploy/backend-production.md` a preparação do servidor (SSH só por chave, atualizações automáticas, firewall da VCN e iptables) e verificar seguindo o documento na VM
- [ ] 3.6 Aplicar a preparação na VM e verificar de fora que só as portas 22, 80 e 443 respondem e que a do PostgreSQL está fechada

## 4. Deploy automático

- [x] 4.1 Criar o script de deploy com `flock`, validação do digest, dump prévio, teste da agenda e volta à imagem anterior lida depois da trava; verificar localmente com uma imagem boa, uma que falha ao subir e duas execuções simultâneas
- [ ] 4.2 Criar a chave de deploy com `restrict,command=` e verificar que outro comando enviado por ela não é executado e que `ssh -L` até o PostgreSQL é recusado
- [x] 4.3 Criar `.github/workflows/deploy-backend.yml` (testes, JAR, imagem `arm64` e `amd64`, GHCR, deploy por digest, `concurrency` sem cancelamento), acionado por `push` na `main` em `backend/**` e `deploy/**` e por `workflow_dispatch`; verificar com `actionlint` ou revisão da sintaxe antes do merge
- [ ] 4.4 Abrir o PR de infraestrutura com o handoff e verificar lint, type-check, testes do frontend e do backend passando
- [ ] 4.5 **[Vinícius]** Aprovar os secrets no GitHub, criar o `.env` na VM e fazer o merge do PR de infraestrutura; verificar que o workflow conclui e que `https://api.vemdancarjp.com.br/api/agenda` responde `200`
- [x] 4.7 Documentar em `docs/deploy/backend-production.md` a cópia manual de `compose.prod.yml`, `Caddyfile` e `deploy.sh` e verificar a interpolação dos dois arquivos de ambiente com `docker compose config`
- [ ] 4.6 Testar um rollback real por `workflow_dispatch` para o digest anterior e verificar a versão em execução

## 5. Carga inicial

- [ ] 5.1 **[Vinícius]** Revisar os dados de `public/agenda.json` e aprovar a lista; verificar com a aprovação registrada no PR
- [ ] 5.2 Criar `deploy/seed/agenda-initial.sql` e um teste de integração que comprove carga em base vazia, falha sem alterações em base preenchida e tabelas administrativas vazias; verificar com o teste focado
- [ ] 5.3 Criar um script reutilizável que compare `GET /api/agenda` com a carga versionada, verificando que ele detecta uma divergência simulada
- [ ] 5.4 Executar a carga na produção e verificar com o script da 5.3 que a agenda retorna os mesmos itens, na mesma ordem

## 6. Recriação do ambiente

- [ ] 6.1 Documentar em `docs/deploy/backend-production.md` a recriação do ambiente e o fluxo de correção (versionar, revisar, aplicar e comparar); verificar seguindo o documento em um ambiente `amd64` novo, com `/api/agenda` retornando os dados da carga

## 7. Corte do site

- [ ] 7.1 Definir `VITE_API_URL` em `.github/workflows/deploy.yml`, mantendo `VITE_SUPABASE_*`, e verificar com um build local que o bundle contém a URL da API
- [ ] 7.2 Atualizar `STATUS.md`, inclusive o bloqueio e o "Não mexer", e abrir o PR de corte com o handoff, com a suíte completa passando
- [ ] 7.3 **[Vinícius]** Fazer o merge do PR de corte e verificar que a home publicada exibe a agenda sem erro de CORS no console, inclusive em um celular

## 8. Validação em produção

- [ ] 8.1 Verificar cada cenário de `public-agenda-access` contra o domínio publicado (rotas administrativas com 404, HTTP redireciona, banco fechado) e registrar o resultado no PR
- [ ] 8.2 Medir a memória da VM por 7 dias no painel da Oracle e, se o P95 ficar abaixo de 25% ou perto do total, ajustar o tamanho da VM e medir de novo; verificar registrando os valores no `STATUS.md`

## Workflow follow-up

- Revisão adversarial da spec pelo Codex e aprovação de Vinícius antes de iniciar a implementação.
- Arquivar a change depois da validação em produção.
- Próximas changes: CRUD admin restante, gestão de admins com primeiro admin, e corte do frontend admin com remoção do Supabase.
- Backup e monitoramento são pré-requisitos da change que voltar a permitir escrita pelo painel admin em produção.
