# Backend em produção

Guia de operação do backend no Cloud Run, com banco no Neon. Decisão: ADR-006. Nada aqui deve conter senhas ou valores reais: troque os itens entre `<...>` no momento de usar.

## Preparar o ambiente

Pré-requisitos: projeto no Google com faturamento, alerta de US$ 1 no projeto, teto de gasto de US$ 5 no Cloud Run e projeto no Neon em `aws-us-east-1`.

Defina as variáveis no terminal:

```bash
export PROJECT_ID=<id-do-projeto>
export PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
export REGION=us-east1
export SERVICE=vemdancarjp-api
export REPOSITORY=ViniciusRio/vemdancarjp
gcloud config set project "$PROJECT_ID"
gcloud services enable run.googleapis.com secretmanager.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
```

### Contas de serviço e senha do banco

```bash
gcloud iam service-accounts create "$SERVICE-runtime"
gcloud iam service-accounts create "$SERVICE-deploy"

gcloud secrets create "$SERVICE-database-password" \
  --replication-policy=user-managed --locations="$REGION"
read -rs DB_PASSWORD
printf '%s' "$DB_PASSWORD" | gcloud secrets versions add "$SERVICE-database-password" --data-file=-
unset DB_PASSWORD

gcloud secrets add-iam-policy-binding "$SERVICE-database-password" \
  --member="serviceAccount:$SERVICE-runtime@$PROJECT_ID.iam.gserviceaccount.com" \
  --role=roles/secretmanager.secretAccessor
```

Mantenha uma única versão ativa do segredo. A conta de deploy não recebe acesso a ele.

### Serviço

O serviço nasce com a imagem de exemplo do Google e sem segredo. O primeiro deploy do workflow aplica a imagem real e a configuração da aplicação.

```bash
gcloud run deploy "$SERVICE" \
  --region "$REGION" \
  --image us-docker.pkg.dev/cloudrun/container/hello \
  --service-account "$SERVICE-runtime@$PROJECT_ID.iam.gserviceaccount.com" \
  --cpu 1 --memory 512Mi --cpu-throttling --cpu-boost \
  --min-instances 0 --max-instances 1 \
  --allow-unauthenticated

gcloud run services update "$SERVICE" --region "$REGION" --max 1
```

### Identidade do workflow

```bash
gcloud iam workload-identity-pools create github --location=global

gcloud iam workload-identity-pools providers create-oidc vemdancarjp \
  --location=global \
  --workload-identity-pool=github \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping='google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref' \
  --attribute-condition="assertion.repository == '$REPOSITORY' && assertion.ref == 'refs/heads/main'"

gcloud iam service-accounts add-iam-policy-binding "$SERVICE-deploy@$PROJECT_ID.iam.gserviceaccount.com" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPOSITORY"

gcloud run services add-iam-policy-binding "$SERVICE" --region "$REGION" \
  --member="serviceAccount:$SERVICE-deploy@$PROJECT_ID.iam.gserviceaccount.com" \
  --role=roles/run.developer

gcloud iam service-accounts add-iam-policy-binding "$SERVICE-runtime@$PROJECT_ID.iam.gserviceaccount.com" \
  --member="serviceAccount:$SERVICE-deploy@$PROJECT_ID.iam.gserviceaccount.com" \
  --role=roles/iam.serviceAccountUser
```

### Variáveis do repositório

Em **Settings → Secrets and variables → Actions → Variables**:

| Variável | Valor |
|---|---|
| `GCP_PROJECT_ID` | ID do projeto |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github/providers/vemdancarjp` |
| `GCP_DEPLOY_SERVICE_ACCOUNT` | `vemdancarjp-api-deploy@<PROJECT_ID>.iam.gserviceaccount.com` |
| `PRODUCTION_DATABASE_JDBC_URL` | `jdbc:postgresql://<host-sem-pooler>/<banco>?sslmode=require` |
| `PRODUCTION_DATABASE_USERNAME` | Usuário do banco |
| `API_PUBLIC_URL` | `https://api.vemdancarjp.com.br`, só depois do mapeamento do domínio |

A URL não leva senha. O pacote `vemdancarjp-backend` precisa ficar público no GHCR, para o Cloud Run baixar a imagem.

### Domínio

Depois de verificar `vemdancarjp.com.br` no Google e antes de definir `API_PUBLIC_URL`:

```bash
gcloud beta run domain-mappings create --region "$REGION" \
  --service "$SERVICE" --domain api.vemdancarjp.com.br
gcloud beta run domain-mappings describe --region "$REGION" --domain api.vemdancarjp.com.br
```

Crie no Registro.br o registro DNS indicado pelo segundo comando.

## Deploy e rollback

Um merge na `main` que altere `backend/`, os scripts `deploy/check-*.sh` ou o workflow publica uma nova versão. O workflow testa a versão candidata antes de ela receber tráfego e restaura o tráfego anterior se uma checagem falhar depois da troca.

Para voltar a uma imagem já publicada:

```bash
gh workflow run deploy-backend.yml --ref main -f digest=sha256:<digest>
```

A imagem passa pelas mesmas checagens. Rollback de imagem **não desfaz migrations**.

Se o workflow avisar que não conseguiu restaurar o tráfego, execute o comando indicado no log.

### Migrations

Toda migration precisa funcionar com a imagem em produção. Para remover ou renomear uma coluna, faça em duas versões: primeiro pare de usá-la e, em um deploy seguinte, remova. O workflow bloqueia migrations incompatíveis com `deploy/check-schema-compat.sh`. Migration aplicada nunca é editada.

Se uma migration causar dano, restaure o Neon dentro da janela de 6 horas ou recrie o banco a partir da carga.

## Corrigir a agenda

Nenhuma correção vai direto ao banco:

1. Altere `deploy/seed/agenda-initial.sql` em um PR revisado. O arquivo é a fonte da agenda de produção.
2. Depois do merge, aplique ao banco um comando SQL com a mesma alteração, em uma transação, usando `psql` na conexão direta.
3. Compare o resultado com a carga versionada. O script precisa de Docker e sobe uma base local a partir da carga:

   ```bash
   deploy/compare-agenda.sh https://api.vemdancarjp.com.br ghcr.io/viniciusrio/vemdancarjp-backend@sha256:<digest>
   ```

## Carga inicial

Com as tabelas da agenda vazias, depois do primeiro deploy, e com a senha informada pelo `psql`:

```bash
psql "postgresql://<usuario>@<host-sem-pooler>/<banco>?sslmode=require" \
  --set ON_ERROR_STOP=1 --file deploy/seed/agenda-initial.sql
```

A carga aborta sem alterar nada se alguma das tabelas tiver linhas. Confira o resultado com `deploy/compare-agenda.sh`.

## Recriar o ambiente

Em outro projeto do Google, repita "Preparar o ambiente", faça o deploy por `workflow_dispatch` com o digest desejado e execute a carga inicial.

Em outro provedor, a mesma imagem `amd64` funciona com qualquer PostgreSQL:

```bash
docker run --detach --publish 8080:8080 \
  --env SPRING_DATASOURCE_URL='jdbc:postgresql://<host>/<banco>?sslmode=require' \
  --env SPRING_DATASOURCE_USERNAME='<usuario>' \
  --env SPRING_DATASOURCE_PASSWORD \
  --env APP_PUBLIC_SURFACE=agenda-only \
  --env APP_FRONTEND_URL=https://vemdancarjp.com.br \
  ghcr.io/viniciusrio/vemdancarjp-backend@sha256:<digest>
```

Coloque HTTPS na frente da aplicação, execute a carga inicial, aponte o registro DNS `api` para o novo servidor e confira com `deploy/check-public-surface.sh` e `deploy/compare-agenda.sh`.

## Conter um gasto inesperado

Alertas e o teto de gasto têm atraso, e o teto pode não cobrir a saída de dados. Ao receber um alerta, confira a origem em **Faturamento → Relatórios**. Se não houver explicação, desligue o serviço:

```bash
gcloud run services update vemdancarjp-api --region us-east1 --scaling=0
```

As novas requisições passam a falhar, e a home mostra o erro de carregamento. Para religar:

```bash
gcloud run services update vemdancarjp-api --region us-east1 --scaling=auto
gcloud run services update vemdancarjp-api --region us-east1 --max 1
```

Se o teto for atingido, o uso novo fica pausado até o próximo mês ou até a edição do orçamento. Depois de editá-lo, o serviço pode levar até uma hora para voltar ao normal.
