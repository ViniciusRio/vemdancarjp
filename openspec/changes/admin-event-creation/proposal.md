# Proposal

## Why

A etapa 7D.1 entregou a política que consulta a aprovação administrativa no PostgreSQL, mas nenhuma rota HTTP a utiliza: `/api/admin/**` continua bloqueado. O primeiro incremento do Passo 8 precisa exercitar essa política em uma operação real de escrita, sem abrir as demais rotas e sem antecipar o corte do frontend ainda conectado ao Supabase.

## What Changes

- Introduzir `POST /api/admin/events` para criar uma linha em `events` no PostgreSQL e responder `201` com o JSON mínimo `{"id":123}` (identificador gerado).
- Autorizar exclusivamente essa combinação de método e caminho mediante sessão Google OIDC válida e estado `APPROVED` relido de `admin_access` pelo `sub` em cada requisição.
- Exigir token CSRF da sessão e validar os campos de entrada antes da persistência, inclusive `instagram` como URL absoluta HTTP(S) ou `null`, os sete dias válidos e `sortOrder` no intervalo do `INTEGER` PostgreSQL. Tratar falha da consulta de aprovação como erro de infraestrutura, sem conceder acesso ou gravar evento.
- Manter todas as outras rotas de `/api/admin/**` em `denyAll` e preservar a leitura pública de `GET /api/agenda`.
- Cobrir criação, autorização, CSRF, validação e isolamento das demais rotas com testes de integração contra PostgreSQL.
- Registrar como risco operacional a divergência de dados: o painel atual grava no Supabase e a agenda pública lê PostgreSQL. A nova rota não constitui o corte das escritas nem a reconciliação das duas bases.

## Capabilities

### New Capabilities

- `admin-events`: criação administrativa de eventos com contrato de entrada, autorização, proteção CSRF, persistência e exposição na agenda pública.

### Modified Capabilities

Nenhuma; o projeto ainda não possui specs principais do OpenSpec.

## Impact

- Backend: `SecurityConfig`, controller e DTO de criação de evento, camada de serviço, `EventRepository` e testes de segurança e persistência. A tabela `events` e a política `AdminAccessAuthorizationManager` já existem; nenhuma migration é prevista.
- API: uma rota nova, `POST /api/admin/events`; as demais rotas administrativas permanecem bloqueadas.
- Operação: manter o endpoint fora do fluxo de produção até existir decisão de fonte de verdade, reconciliação e estratégia de rollback entre Supabase e PostgreSQL. A integração Vue, bootstrap administrativo, CORS e cookies de produção permanecem em etapas posteriores do `PLAN.md`.
