# Proposal

## Why

A home publicada está em erro desde 16/09: o build chama `/api/agenda`, mas não existe backend em produção. O projeto Supabase que o build aponta também não resolve mais no DNS (NXDOMAIN em 02/11), e foi decidido abandonar o Supabase em vez de recuperá-lo. Publicar o backend Java com PostgreSQL devolve a agenda ao ar e é a base para migrar o painel admin nas changes seguintes.

A primeira opção era uma VM Arm do Oracle Cloud Always Free, mas São Paulo, a região principal da conta, ficou sem capacidade tanto para o shape Arm quanto para o Micro. Uma VM `e2-micro` do Google não é gratuita, porque o IPv4 em uso é cobrado. Por isso, a publicação passa para serviços gerenciados.

## What Changes

- Publicar o backend no Google Cloud Run, em `us-east1`, e o PostgreSQL no Neon, em `aws-us-east-1`, com a API em `https://api.vemdancarjp.com.br`. Durante o desenvolvimento, o projeto fica somente em planos gratuitos, por decisão de Vinícius.
- Expor na internet somente `GET /api/agenda`, com um filtro na própria aplicação. Login, sessão e rotas administrativas ficam inacessíveis em produção nesta change.
- Permitir que `https://vemdancarjp.com.br` leia a agenda por CORS, sem credenciais.
- Popular o PostgreSQL de produção com uma carga inicial a partir do `public/agenda.json`, revisado por Vinícius. Os dados do Supabase são descartados por decisão dele.
- Fazer deploy automático do backend a partir da `main`. Cada versão é testada antes de receber tráfego, uma falha depois da troca devolve o tráfego à versão anterior, e o rollback usa uma imagem já publicada. Migrations precisam ser compatíveis com a versão em produção.
- Reduzir o risco de custo com escala a zero, no máximo uma instância, alerta de orçamento e teto de gasto. **O custo zero não é garantido: o Google pode cobrar além do alerta e do teto.**
- Manter a agenda recriável a partir da carga versionada no repositório. Backup e monitoramento ficam fora desta change, por decisão de Vinícius, e passam a ser pré-requisito antes de o painel admin voltar a gravar em produção.
- Definir `VITE_API_URL` no build de produção do frontend.
- Registrar em ADR o abandono do Supabase e a escolha do provedor, e atualizar `PLAN.md` e `STATUS.md`: a reconciliação e o rollback para o Supabase deixam de existir.
- Remover os arquivos da abordagem com VM: `deploy/compose.prod.yml`, `deploy/Caddyfile`, `deploy/.env.example`, `deploy/deploy.sh` e `deploy/oracle-create-vm.sh`.

## Capabilities

### New Capabilities

- `public-agenda-access`: leitura pública da agenda pelo site publicado, incluindo origem permitida, superfície exposta e conteúdo inicial.
- `backend-operations`: operação do backend em produção: deploy, rollback, custo e recriação do ambiente.

### Modified Capabilities

Nenhuma; o projeto ainda não possui specs principais do OpenSpec.

## Impact

- **Backend:** configuração de CORS em `SecurityConfig`, filtro da superfície pública ativado por variável de ambiente e testes de integração. Sem novos endpoints, sem mudanças de schema e sem novas dependências de aplicação.
- **Novos arquivos:** `backend/Dockerfile`, `deploy/seed/` (carga inicial), `deploy/check-public-surface.sh`, `deploy/check-schema-compat.sh`, `docs/deploy/backend-production.md` e o workflow de deploy do backend.
- **Frontend:** só `VITE_API_URL` em `.github/workflows/deploy.yml`. O código não muda.
- **Infraestrutura:** projeto no Google Cloud com faturamento, projeto no Neon, verificação do domínio e registro DNS `api` no Registro.br, criados ou aprovados por Vinícius.
- **Limitações aceitas:**
  - O primeiro acesso depois de um tempo parado é lento, porque a aplicação precisa subir.
  - O mapeamento de domínio do Cloud Run está em Preview, e o Google não o recomenda para produção.
  - A API fica nos EUA, longe do público.
  - O painel admin continua fora do ar até as changes seguintes, e alterar a agenda exige uma operação manual no banco, também versionada na carga.
  - Sem monitoramento, uma queda só é notada quando alguém abre o site.
  - Quem pode publicar uma imagem consegue, indiretamente, ler a senha do banco. Por isso, o deploy fica restrito à `main` protegida e ao disparo manual por Vinícius.
  - Atingir o teto de gasto deixa a agenda fora do ar até o próximo mês, salvo edição do orçamento.
- **Custo:** o Google exige cartão, e a saída de dados para o Brasil é tratada como cobrada. No uso esperado, o valor fica abaixo de US$ 0,01 por mês. Em um abuso contínuo de 100 requisições por segundo, chegaria a cerca de US$ 14 por dia. Alerta e teto de gasto reduzem esse risco, mas têm atraso, e o teto pode não cobrir a saída de dados. Ver "Limites de custo" em `design.md`.
- **Dependência mantida:** os secrets `VITE_SUPABASE_*` e o SDK permanecem, porque a home importa o cliente Supabase e removê-los quebraria a página. A remoção fica para a change de corte do frontend.
- **Independente de:** PR da listagem administrativa (`feat/admin-event-listing`) e arquivamento de `admin-event-creation`.
