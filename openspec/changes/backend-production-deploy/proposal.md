# Proposal

## Why

A home publicada está em erro desde 16/09: o build chama `/api/agenda`, mas não existe backend em produção. O projeto Supabase que o build aponta também não resolve mais no DNS (NXDOMAIN em 02/11), e foi decidido abandonar o Supabase em vez de recuperá-lo. Publicar o backend Java com PostgreSQL devolve a agenda ao ar e é a base para migrar o painel admin nas changes seguintes.

## What Changes

- Publicar o backend e o PostgreSQL em uma VM Arm do Oracle Cloud Always Free, em `https://api.vemdancarjp.com.br`, com a Hostinger KVM 1 como plano B documentado.
- Expor na internet somente `GET /api/agenda`. Login, sessão e rotas administrativas ficam inacessíveis em produção nesta change.
- Permitir que `https://vemdancarjp.com.br` leia a agenda por CORS, sem credenciais.
- Popular o PostgreSQL de produção com uma carga inicial a partir do `public/agenda.json`, revisado por Vinícius. Os dados do Supabase são descartados por decisão dele.
- Fazer deploy automático do backend a partir da `main`, com rollback por imagem anterior.
- Manter a agenda recriável a partir da carga versionada no repositório. Backup e monitoramento ficam fora desta change, por decisão de Vinícius, e passam a ser pré-requisito antes de o painel admin voltar a gravar em produção.
- Definir `VITE_API_URL` no build de produção do frontend.
- Registrar em ADR o abandono do Supabase e atualizar `PLAN.md` e `STATUS.md`: a reconciliação e o rollback para o Supabase deixam de existir.

## Capabilities

### New Capabilities

- `public-agenda-access`: leitura pública da agenda pelo site publicado, incluindo origem permitida, superfície exposta e conteúdo inicial.
- `backend-operations`: operação do backend em produção: deploy, rollback e recriação do ambiente.

### Modified Capabilities

Nenhuma; o projeto ainda não possui specs principais do OpenSpec.

## Impact

- **Backend:** configuração de CORS em `SecurityConfig` e testes de integração. Sem novos endpoints, sem mudanças de schema e sem novas dependências de aplicação.
- **Novos arquivos:** `backend/Dockerfile`, `deploy/` (Compose de produção, Caddy, scripts, carga inicial) e o workflow de deploy do backend.
- **Frontend:** só `VITE_API_URL` em `.github/workflows/deploy.yml`. O código não muda.
- **Infraestrutura:** conta Oracle, VM, registro DNS `api` no Registro.br e secrets no GitHub, criados ou aprovados por Vinícius.
- **Limitações aceitas:** o painel admin continua fora do ar até as changes seguintes, e alterar a agenda exige uma operação manual no banco, também versionada na carga. Sem monitoramento, uma queda só é notada quando alguém abre o site.
- **Dependência mantida:** os secrets `VITE_SUPABASE_*` e o SDK permanecem, porque a home importa o cliente Supabase e removê-los quebraria a página. A remoção fica para a change de corte do frontend.
- **Independente de:** PR da listagem administrativa (`feat/admin-event-listing`) e arquivamento de `admin-event-creation`.
