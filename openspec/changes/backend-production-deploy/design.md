# Design

## Context

Motivação em `proposal.md`; comportamento exigido em `specs/`.

- **Frontend:** `agendaService.ts` já usa `VITE_API_URL`, mas o `deploy.yml` não a define, então a chamada vai para o próprio GitHub Pages e recebe 404. A home mostra "carregando agenda..." e a chamada não tem timeout, então ela espera a aplicação subir sem exibir erro.
- **Backend:** já tem Dockerfile e CORS, e o `docker-compose.yml` atual só sobe PostgreSQL para desenvolvimento. As migrations V1 e V2 só criam tabelas, sem dados.
- **Segurança:** `SecurityConfig` nega por padrão, mas libera `/api/auth/csrf` e as rotas do OIDC. O OIDC só ativa se as credenciais Google existirem. `application.yml` tem senha padrão de desenvolvimento para o banco.
- **Supabase:** a home importa `authStore`, e o cliente é criado na importação. A carga da agenda é independente de `authStore.init()`, então o host morto não bloqueia a agenda, mas a retirada dos secrets `VITE_SUPABASE_*` quebraria a página.
- **Infraestrutura:** repositório público; DNS no Registro.br, sem registro CAA; `www` já redireciona para o domínio principal, então só existe uma origem do site.
- **Oracle (consultado em 03/11/2026):** o relatório de capacidade da conta mostra São Paulo sem capacidade para A1 e E2.1.Micro, e as 10 tentativas do script falharam. A região principal da conta não pode ser trocada.
- **Google (documentação consultada em 03/11/2026):**
  - O IPv4 de uma VM custa US$ 0,005 por hora, com só 1 hora isenta por mês.
  - Cloud Run com cobrança por requisição isenta 180.000 vCPU-segundos, 360.000 GiB-segundos e 2 milhões de requisições por mês. A isenção usa preços de Tier 1, que inclui `us-east1`.
  - A saída de dados tem 1 GiB isento "dentro da América do Norte", e não está claro se o tráfego para o Brasil entra.
- **Neon (documentação consultada em 03/11/2026):** plano gratuito sem cartão, com 1 GB de armazenamento, 100 CU-horas e 5 GB de transferência por mês. Suspende após 5 minutos parado e volta em algumas centenas de milissegundos. Atingir o limite suspende o banco até o próximo ciclo. A restauração alcança até 6 horas.
- **Medição local do tempo de subida (03/11/2026):** com PostgreSQL local e agenda vazia, a primeira resposta `200` levou de 11 a 14 s com 1 vCPU e 512 MiB, e cerca de 6 s com 2 vCPUs ou com `-XX:TieredStopAtLevel=1`. A memória ficou entre 176 e 273 MiB.

## Goals / Non-Goals

**Goals:**

- Devolver a agenda pública ao ar, com custo zero ou de centavos e de forma recriável se um provedor falhar.
- Manter a imagem portátil, sem depender de recursos específicos do Google.
- Permanecer somente em planos gratuitos durante o desenvolvimento.

**Non-Goals:**

- Login, sessão, cookies de produção, CRUD admin ou conexão do painel Vue ao Java. Ficam para as changes seguintes.
- Remover o SDK ou os secrets do Supabase.
- Separar checks e deploy do frontend no `deploy.yml`; é um item próprio do `STATUS.md`.
- Corrigir `lastUpdated`, que hoje sempre mostra a data atual.
- Rate limiting, cache e alta disponibilidade.
- Backup e monitoramento. Ver a decisão abaixo.
- Eliminar o tempo de subida com instância mínima ou imagem nativa.

## Decisions

### Topologia: Cloud Run + Neon

```
Internet --HTTPS--> Cloud Run (filtro -> Spring Boot) --TLS--> Neon (PostgreSQL)
                         |
                         +-- todo o resto: 404
```

O serviço usa 1 vCPU, 512 MiB, cobrança por requisição, nenhuma instância mínima e no máximo uma. O banco fica no Neon porque o PostgreSQL gerenciado do Google não tem plano gratuito equivalente e um PostgreSQL dentro do Cloud Run perderia os dados.

**Alternativas descartadas:**

- Google `e2-micro`: o IPv4 custa cerca de US$ 3,65 por mês.
- Oracle A1 ou Micro: sem capacidade em São Paulo.
- Render + Neon: leva cerca de 1 minuto para acordar, depois de 15 minutos parado.
- AWS e Azure: os planos gratuitos com VM não são permanentes.

### Região: `us-east1`, com Neon em `aws-us-east-1`

O mapeamento de domínio do Cloud Run não existe em `southamerica-east1`. Em `us-east1`, ele funciona, a isenção rende mais por ser Tier 1 e o banco fica próximo da aplicação. O custo é uma latência maior para o público no Brasil, que será medida na validação.

**Alternativa descartada:** São Paulo com o endereço `run.app` no site. A latência seria menor, mas uma troca de provedor exigiria novo build do frontend, e o preço é de Tier 2.

### Domínio: mapeamento do Cloud Run, em Preview

`api.vemdancarjp.com.br` aponta por CNAME para o Google, que emite e renova o certificado. A documentação alerta que esse recurso tem problemas de latência e não é recomendado para produção. Para este projeto, com uma única leitura pública, esse risco é aceito por ser o único caminho gratuito e simples para manter o domínio próprio.

A validação mede o tempo de resposta pelo domínio e pelo endereço `run.app`. Se o domínio for lento demais ou o recurso for retirado, o site passa a usar o `run.app`, e a mudança só exige um novo build.

**Alternativas descartadas:**

- Balanceador de carga externo: é a opção recomendada pelo Google, mas não é gratuita.
- Firebase Hosting com rewrite: adiciona outro serviço entre o site e a API e exige plano com faturamento. Continua como segunda opção se o mapeamento for retirado.

### Filtro da superfície pública na aplicação

Um filtro de servlet, antes da cadeia do Spring Security, responde `404` a tudo que não seja `GET /api/agenda`. Ele é ativado por `APP_PUBLIC_SURFACE=agenda-only`, definida pelo workflow, e fica desligado em desenvolvimento e nos testes dos outros fluxos. As credenciais Google também não são configuradas nesta change, então o OIDC fica desligado.

Como a restrição depende de uma variável, o workflow confere `404` em `/api/admin/events` na versão candidata, antes de ela receber tráfego. O `SecurityConfig` continua como proteção da aplicação, inclusive quando o filtro sair em uma change futura.

**Alternativas descartadas:**

- Caddy como sidecar: mantém o bloqueio fora da aplicação, mas soma um contêiner, memória e tempo de subida, e a regra fica fora dos testes de integração.
- Confiar só no `SecurityConfig`: `/api/auth/csrf` responde hoje e as rotas de OIDC ficariam acessíveis se forem ativadas por engano.

### CORS restrito à agenda e derivado de `APP_FRONTEND_URL`

A origem vem da variável `APP_FRONTEND_URL`, que já existe para o redirecionamento do OIDC, reduzida a esquema, host e porta. A regra vale só para `GET /api/agenda`, sem credenciais. Sem a variável, nenhuma origem é autorizada. Uma URL inválida faz a aplicação falhar na inicialização.

**Alternativas descartadas:**

- Variável separada: seria mais um valor para manter em sincronia.
- Proxy no mesmo domínio: o GitHub Pages não faz proxy.

### Tempo de subida

O serviço usa `--cpu-boost`, que dobrou a velocidade na medição local com 2 vCPUs. `-XX:TieredStopAtLevel=1` também reduziu o tempo à metade com 1 vCPU e será adotado se confirmar o ganho no Cloud Run, já que uma leitura simples da agenda não precisa do compilador otimizado. A sonda de inicialização usa `GET /api/agenda`, para que a primeira requisição real só chegue com a aplicação e o banco prontos.

**Alternativas descartadas:** instância mínima, que sai do plano gratuito; imagem nativa, que exigiria mudanças maiores no build.

### Imagem pública no GHCR

O workflow roda os testes, gera o JAR e monta a imagem `linux/amd64` sobre `eclipse-temurin:21-jre`, publicada no GHCR. O Cloud Run aceita imagens públicas do GHCR diretamente, e o deploy usa sempre o digest, nunca uma tag mutável. O pacote precisa ficar público.

**Alternativas descartadas:**

- Artifact Registry: é a recomendação do Google, mas tem custo de armazenamento além do limite gratuito.
- Imagem `arm64`: o Cloud Run não precisa dela, e ela exigiria QEMU no build.

### Deploy com tráfego só depois do teste

1. O workflow cria a revisão sem tráfego, com uma tag própria.
2. Confere `200` em `/api/agenda` e `404` em `/api/admin/events` no endereço dessa tag.
3. Só então move o tráfego e confere a agenda no domínio público.

Se qualquer etapa antes da troca falhar, o tráfego continua na revisão anterior, sem script de retorno. O serviço é criado uma vez, antes do primeiro deploy, porque criar uma revisão sem tráfego exige que ele já exista.

O workflow roda em `push` na `main` e em `workflow_dispatch`, nunca em PR, com `concurrency` no workflow inteiro e sem cancelar o que está em andamento. Assim, deploys não se sobrepõem nem terminam fora de ordem.

### Credencial por Workload Identity Federation

O GitHub Actions troca seu token OIDC por uma credencial temporária do Google, sem chave guardada nos secrets. O provedor de identidade aceita só este repositório e a `main`. A conta de serviço do deploy só pode atualizar este serviço do Cloud Run e usar a conta de serviço de execução. A leitura do segredo fica com a conta de execução.

**Alternativa descartada:** chave JSON de conta de serviço, que é uma credencial de longa duração.

### Conexão com o Neon

A aplicação usa a conexão direta, sem `-pooler`, porque o Flyway roda na subida com o mesmo datasource e o Neon recomenda conexão direta para migrations, além de desaconselhar pool do cliente sobre conexão com pool. A URL exige `sslmode=require`, e o Hikari usa um pool pequeno, de no máximo 2 conexões.

A senha fica no Secret Manager, com um único segredo, replicação só em `us-east1` e uma versão ativa, dentro do limite gratuito de 6 versões.

A documentação do Neon não diz se conexões ociosas impedem a suspensão. A validação confere o consumo de CU-horas, para detectar esse caso antes de o limite mensal parar o banco.

### Limites de custo

O alerta de orçamento avisa, mas não bloqueia gastos. O limite real vem de uma instância no máximo e de nenhuma instância mínima. Na conta com 15 s por subida e 2 vCPUs, 100 subidas por dia consomem 90.000 dos 180.000 vCPU-segundos isentos.

Se a isenção de saída de dados não valer para o Brasil, 10 mil respostas de 5 KB somam cerca de 50 MB, ou centavos, supondo um preço de até US$ 0,20/GB, que ainda será conferido na primeira fatura.

### Sem backup e monitoramento nesta change

Decisão de Vinícius. Nesta fase, o banco não guarda nada que não possa ser recriado: o painel está fora do ar, `admin_access` fica vazia e a agenda vem de um arquivo versionado. Por isso, perder o banco significa criar outro e repetir a carga.

Isso vale enquanto duas condições forem cumpridas:

1. Toda correção entra primeiro em `deploy/seed/agenda-initial.sql`, revisada e versionada. Só depois é aplicada ao banco, e um script compara a agenda publicada com a carga.
2. Backup e monitoramento são pré-requisitos da change que voltar a permitir escrita pelo painel admin. A proposta anterior, com backup em artifact criptografado e alerta independente, foi retirada e pode servir de ponto de partida.

### Carga inicial fora do Flyway

Os dados ficam em `deploy/seed/agenda-initial.sql`, executado uma única vez e à mão com `psql` na conexão direta do Neon, em uma transação que aborta se alguma das três tabelas tiver linhas. `sort_order` segue a ordem da fonte.

**Alternativa descartada:** migration Flyway. Ela rodaria em todos os testes com Testcontainers e misturaria schema com conteúdo, e o arquivo aplicado não poderia mais ser corrigido.

**Fonte:** `public/agenda.json`. Vinícius decidiu não recuperar os dados do Supabase. Os dados só são publicados depois da revisão dele, porque o arquivo não recebe commits desde 09/05.

### Publicação em dois PRs

Como merge na `main` é deploy, o site só deve apontar para a API depois de ela estar validada:

1. **PR de infraestrutura:** Dockerfile, CORS, filtro, carga e workflow. O site continua como está, sem regressão.
2. Deploy, carga inicial e validação da API em produção.
3. **PR de corte:** `VITE_API_URL` no `deploy.yml`. O merge desse PR faz o site voltar.

**Alternativa descartada:** PR único, que publicaria um frontend apontando para uma API ainda vazia.

## Risks / Trade-offs

- [O mapeamento de domínio é lento ou retirado] → Medir na validação. O site pode passar a usar o endereço `run.app` com um novo build.
- [Primeiro acesso lento] → `--cpu-boost`, a sonda de inicialização e o estado de carregamento da home. O tempo real é medido na validação. Visitantes podem esperar vários segundos.
- [Queda do GHCR impede a subida de uma instância] → O Cloud Run mantém imagens públicas em cache por até uma hora. Se isso causar falhas, a imagem passa por um repositório remoto do Artifact Registry.
- [Cobrança inesperada no Google] → Uma instância no máximo, escala a zero e alerta de orçamento. O alerta não bloqueia gastos.
- [O Neon atinge o limite mensal] → A API fica em erro até o próximo ciclo, sem perda de dados. A validação confere o consumo.
- [Mudança nos planos gratuitos do Google ou do Neon] → A imagem é portátil, e a agenda é recriada pela carga versionada.
- [Neon exposto na internet] → TLS obrigatório e senha gerada, guardada só no Secret Manager. O plano gratuito não oferece restrição por IP.
- [Correção manual fora da carga] → Uma recriação restauraria dados antigos. A spec exige versionar antes de aplicar, e a comparação automatizada detecta divergências.
- [Migration incompatível em um deploy futuro] → Rollback de imagem não desfaz schema. A restauração do Neon alcança 6 horas, e as migrations seguem aditivas.
- [Agenda desatualizada] → Revisão obrigatória antes do corte. Até o painel voltar, correções exigem uma operação manual no banco.
- [Sem Supabase, painel admin quebrado no site] → Aceito nesta change. A rota `/admin` continua existindo sem funcionar até o corte do frontend.
- [Queda não percebida, já que não há monitoramento] → Risco aceito por Vinícius, que abre o site depois de cada deploy e periodicamente. A queda anterior durou semanas sem correção, então o monitoramento volta antes do painel admin.

## Migration Plan

1. Vinícius cria o projeto no Google com alerta de orçamento, o projeto no Neon, e verifica o domínio.
2. O serviço, a identidade do workflow e o segredo são configurados no Google.
3. Merge do PR de infraestrutura; o workflow publica a imagem, e Vinícius torna o pacote público, se preciso.
4. Mapeamento do domínio, carga inicial revisada e validação da API em produção.
5. Merge do PR de corte; validação da home publicada.

**Rollback:**

- **Site:** reverter o PR de corte volta ao estado atual, com a home em erro. Não há versão anterior funcional para onde voltar.
- **Backend:** `workflow_dispatch` com o digest anterior.
- **Provedor:** subir a mesma imagem em outro ambiente, repetir a carga versionada e apontar o DNS `api` para ele. Nenhum build do frontend é necessário, salvo se o site estiver usando o `run.app`.
