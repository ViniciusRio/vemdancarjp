# Design

## Context

Motivação em `proposal.md`; comportamento exigido em `specs/`.

- **Frontend:** `agendaService.ts` já usa `VITE_API_URL`, mas o `deploy.yml` não a define, então a chamada vai para o próprio GitHub Pages e recebe 404.
- **Backend:** não tem Dockerfile nem CORS, e o `docker-compose.yml` atual só sobe PostgreSQL para desenvolvimento, publicando a porta 5433. As migrations V1 e V2 só criam tabelas, sem dados.
- **Segurança:** `SecurityConfig` nega por padrão e só ativa o OIDC se as credenciais Google existirem. `application.yml` tem senha padrão de desenvolvimento para o banco.
- **Supabase:** a home importa `authStore`, e o cliente é criado na importação. A carga da agenda é independente de `authStore.init()`, então o host morto não bloqueia a agenda, mas a retirada dos secrets `VITE_SUPABASE_*` quebraria a página.
- **Infraestrutura:** repositório público; DNS no Registro.br, sem registro CAA; `www` já redireciona para o domínio principal, então só existe uma origem do site.
- **Oracle Always Free (documentação consultada em 02/11/2026):** até 2 OCPUs e 12 GB em Ampere A1, 200 GB de disco, somente na região principal. Instância pode ser recuperada se CPU (P95), rede e memória ficarem abaixo de 20% por 7 dias.
- **Conta Oracle:** região principal Brazil East (São Paulo), em Free Trial. Ao fim do trial, a conta fica limitada aos recursos Always Free, então a VM precisa usar somente recursos elegíveis.

## Goals / Non-Goals

**Goals:**

- Devolver a agenda pública ao ar, com custo zero e de forma recriável se a Oracle falhar.
- Manter a configuração portátil, sem depender de recursos específicos da Oracle.
- Permanecer somente em planos gratuitos durante o desenvolvimento.

**Non-Goals:**

- Login, sessão, cookies de produção, CRUD admin ou conexão do painel Vue ao Java. Ficam para as changes seguintes.
- Remover o SDK ou os secrets do Supabase.
- Separar checks e deploy do frontend no `deploy.yml`; é um item próprio do `STATUS.md`.
- Corrigir `lastUpdated`, que hoje sempre mostra a data atual.
- Rate limiting, cache e alta disponibilidade. Uma única VM é aceita para este projeto.
- Backup e monitoramento. Ver a decisão abaixo.

## Decisions

### Topologia: uma VM com Docker Compose

```
Internet --443--> Caddy --> Spring Boot --> PostgreSQL
                    |        (rede interna do Compose, sem portas publicadas)
                    +-- todo o resto: 404
```

A VM inicial é Ampere A1 com 1 OCPU e 2 GB, em Ubuntu LTS `arm64`. O Compose de produção fica em `deploy/`, separado do Compose de desenvolvimento, e usa `${VAR:?}` para falhar se faltar uma variável obrigatória.

**Alternativa descartada:** Autonomous Database da Oracle, porque prenderia o projeto ao provedor.

### Caddy como filtro da superfície pública

O Caddy obtém e renova o certificado sozinho e encaminha só `GET /api/agenda`; o resto recebe 404. Mesmo que a aplicação tenha outras rotas, inclusive as da listagem administrativa quando entrar na `main`, elas não ficam acessíveis pela internet. As credenciais Google também não são configuradas nesta change, então o OIDC fica desligado.

**Alternativa descartada:** confiar só no `SecurityConfig`. Ele continua sendo a proteção da aplicação, mas a segunda camada reduz a exposição enquanto a administração não é publicada.

### CORS restrito à agenda e derivado de `APP_FRONTEND_URL`

A origem vem da variável `APP_FRONTEND_URL`, que já existe para o redirecionamento do OIDC, reduzida a esquema, host e porta. A regra vale só para `GET /api/agenda`, sem credenciais. Sem a variável, nenhuma origem é autorizada. Uma URL inválida faz a aplicação falhar na inicialização.

**Alternativas descartadas:**

- Variável separada: seria mais um valor para manter em sincronia.
- Proxy no mesmo domínio: o GitHub Pages não faz proxy.
- CORS no Caddy: a regra ficaria fora dos testes de integração.

### Imagem multi-arquitetura a partir de um JAR

O workflow roda os testes em `x86`, gera o JAR e monta a imagem para `linux/arm64` e `linux/amd64` com buildx, sobre `eclipse-temurin:21-jre`. Como a imagem só copia o JAR, a emulação custa pouco, e a imagem `amd64` também permite testar localmente e recriar o ambiente em outro servidor. As imagens vão para o GHCR, e o deploy usa sempre o digest, nunca uma tag mutável.

**Alternativa descartada:** compilar na VM, o que consumiria a pouca CPU da produção e exigiria o código-fonte no servidor.

### Deploy por SSH com comando forçado

O GitHub Actions conecta com uma chave cujo `authorized_keys` usa `restrict,command=` para só executar o script de deploy, recebendo o digest como argumento validado. O `restrict` desativa encaminhamento de portas, agente e PTY, porque `command=` sozinho ainda permitiria abrir um túnel até o PostgreSQL. O script grava um `pg_dump` local, sobe a imagem, testa `/api/agenda` e volta à imagem anterior se falhar. O host key do servidor fica fixado nos secrets. A porta 22 fica aberta porque os IPs do Actions variam, então o SSH aceita só chave.

O workflow só roda em `push` na `main` e em `workflow_dispatch`, nunca em PR, para que forks não alcancem os secrets.

Deploys são serializados em duas camadas: `concurrency` no workflow, sem cancelar o que está em andamento, e `flock` no script do servidor. A imagem de retorno é lida depois de obter a trava, para uma tentativa com falha nunca restaurar uma versão anterior à última publicada.

**Alternativa descartada:** Tailscale ou outro túnel, mais um serviço para um ganho pequeno neste momento.

### Sem backup e monitoramento nesta change

Decisão de Vinícius. Nesta fase, o banco não guarda nada que não possa ser recriado: o painel está fora do ar, `admin_access` fica vazia e a agenda vem de um arquivo versionado. Por isso, perder a VM significa subir outra e repetir a carga.

Isso vale enquanto duas condições forem cumpridas:

1. Toda correção entra primeiro em `deploy/seed/agenda-initial.sql`, revisada e versionada. Só depois é aplicada ao banco, e um script compara a agenda publicada com a carga.
2. Backup e monitoramento são pré-requisitos da change que voltar a permitir escrita pelo painel admin. A proposta anterior, com backup em artifact criptografado e alerta independente, foi retirada e pode servir de ponto de partida.

### Carga inicial fora do Flyway

Os dados ficam em `deploy/seed/agenda-initial.sql`, executado uma única vez e à mão, em uma transação que aborta se alguma das três tabelas tiver linhas. `sort_order` segue a ordem da fonte.

**Alternativa descartada:** migration Flyway. Ela rodaria em todos os testes com Testcontainers e misturaria schema com conteúdo, e o arquivo aplicado não poderia mais ser corrigido.

**Fonte:** `public/agenda.json`. Vinícius decidiu não recuperar os dados do Supabase. Os dados só são publicados depois da revisão dele, porque o arquivo não recebe commits desde 09/05.

### Publicação em dois PRs

Como merge na `main` é deploy, o site só deve apontar para a API depois de ela estar validada:

1. **PR de infraestrutura:** Dockerfile, CORS, `deploy/` e workflows. O site continua como está, sem regressão.
2. Deploy, carga inicial e validação da API em produção.
3. **PR de corte:** `VITE_API_URL` no `deploy.yml`. O merge desse PR faz o site voltar.

**Alternativa descartada:** PR único, que publicaria um frontend apontando para uma API ainda vazia.

### Sem carga artificial para evitar a recuperação da VM

Não usaremos scripts que simulam uso, como NeverIdle ou LookBusy. Isso é frágil e tenta contornar a regra do provedor. A VM tem o tamanho ajustado ao uso real: a instância só é considerada ociosa se CPU, rede e memória ficarem abaixo de 20%, e a memória consumida por Java e PostgreSQL é o caminho legítimo para ficar acima disso. A VM começa com 2 GB porque o teste local mediu cerca de 425 MB entre Java, PostgreSQL e Caddy, sem contar o sistema. Em 4 GB, o uso poderia ficar abaixo do limite de 800 MB. Após 7 dias de medição, ajustamos o tamanho se o P95 da memória estiver abaixo de 25% ou perto do total.

## Risks / Trade-offs

- [A Oracle recupera a VM ou bloqueia a conta] → A agenda é recriada pela carga versionada, com a imagem `amd64` e o passo a passo de recriação prontos. Não há perda de dados se a condição de versionar correções for cumprida.
- [Queda não percebida, já que não há monitoramento] → Risco aceito por Vinícius, que abre o site depois de cada deploy e periodicamente. A queda anterior durou semanas sem correção, então o monitoramento volta antes do painel admin.
- [Correção manual fora da carga] → Uma recriação restauraria dados antigos. A spec exige versionar antes de aplicar, e a comparação automatizada detecta divergências.
- [Sem capacidade Arm em São Paulo, que tem um único domínio de disponibilidade e é a região principal da conta] → `deploy/oracle-create-vm.sh` repete a criação sem prazo. O site continua fora do ar enquanto isso, pois opções pagas estão descartadas nesta fase.
- [VM criada com recurso não elegível durante o trial] → Conferir o selo Always Free no shape e no disco na criação, para a VM não parar nem gerar cobrança ao fim do trial.
- [Pouca memória para os dois serviços] → Limites explícitos no Compose e no heap do Java, ajustados por medição.
- [Migration incompatível em um deploy futuro] → Rollback de imagem não desfaz schema; o script grava dump antes de cada deploy, e as migrations seguem aditivas.
- [Docker contorna o firewall da VM] → Publicar só as portas do Caddy e testar de fora que a porta do PostgreSQL está fechada, além das regras da VCN e do iptables da imagem Oracle.
- [Agenda desatualizada] → Revisão obrigatória antes do corte. Até o painel voltar, correções exigem uma operação manual no banco.
- [Sem Supabase, painel admin quebrado no site] → Aceito nesta change. A rota `/admin` continua existindo sem funcionar até o corte do frontend.

## Migration Plan

1. Vinícius cria a conta Oracle, a VM e o registro DNS `api`.
2. Merge do PR de infraestrutura; o workflow publica a imagem e faz o deploy.
3. Vinícius revisa os dados; a carga inicial é executada.
4. Validação da API em produção e da recriação em ambiente `amd64`.
5. Merge do PR de corte; validação da home publicada.

**Rollback:**

- **Site:** reverter o PR de corte volta ao estado atual, com a home em erro. Não há versão anterior funcional para onde voltar.
- **Backend:** `workflow_dispatch` com o digest anterior.
- **Provedor:** subir outra VM, repetir a carga versionada e apontar o DNS `api` para ela. Nenhum build do frontend é necessário.

## Open Questions

- **Registro de IP:** usar IP público reservado na Oracle, se for gratuito na conta, para o DNS não mudar ao recriar a VM. Se não for, o passo a passo inclui atualizar o DNS.
