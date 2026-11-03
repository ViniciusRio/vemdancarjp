---
type: adr
numero: "003"
titulo: Iniciar Google OIDC no backend sem abrir a administração
data: 2026-10-13
status: aceita
---

# ADR-003: Iniciar Google OIDC no backend sem abrir a administração

## Contexto

O frontend ainda usa Supabase para login e escrita administrativa. Antes da etapa 7C, o backend Java precisava iniciar o fluxo Google OIDC e ainda não dispunha de autorização administrativa nem de endpoints de sessão. A etapa 7C implementou os endpoints de sessão e CSRF; a autorização administrativa continua pendente.

## Decisão

Usar o OAuth2 Client nativo do Spring Security quando client ID, client secret e URL fixa de retorno estiverem configurados. Sem credenciais Google, a aplicação inicia com a API administrativa fechada e sem login; configuração parcial impede a inicialização. A cadeia permite `GET /api/agenda`, protege métodos inseguros por CSRF, responde 401/403 em JSON na API e nunca concede administração nesta etapa. O tratamento de erros CSRF usa o `AccessDeniedHandler` de `exceptionHandling`, sem personalização de `CsrfFilter`.

## Consequências

- Não há credenciais Google no repositório nem JWT próprio no frontend.
- A aplicação pode ser testada sem segredos reais; o início do login usa valores fictícios apenas nos testes.
- A etapa 7C adiciona consulta da sessão OIDC pelo `sub` validado a cada requisição, token CSRF de sessão e logout local pelo Spring Security. O status `APPROVED` é apenas informado pela consulta; não libera `/api/admin/**`.
- A etapa 7D.1 adicionou uma política isolada que exige autenticação OIDC da inscrição Google com emissor esperado e relê `admin_access` pelo `sub` a cada avaliação. Na etapa 7D.2, somente `POST /api/admin/events` passou a usar essa política na cadeia HTTP. As demais combinações de método e caminho em `/api/admin/**` continuam em `denyAll`, inclusive para `APPROVED`.
- O POST exige CSRF, valida o corpo localmente e grava em `events` no PostgreSQL, respondendo `201` com apenas o `id`. Os handlers de autenticação e CSRF existentes permanecem com seus contratos 401/403.
- Uma falha da consulta de aprovação propaga erro de infraestrutura. A cadeia permite apenas o despacho interno `DispatcherType.ERROR` para que o servidor devolva `5xx`; uma chamada direta a `/error` continua bloqueada por `.anyRequest().denyAll()`.
- Bootstrap, outras operações administrativas, CORS, cookies de produção e o corte do painel Vue do Supabase para PostgreSQL continuam pendentes. Até esse corte, as duas bases podem divergir.
- A regra `.anyRequest().denyAll()` continua bloqueando as rotas sem liberação explícita; a etapa 7C liberou somente sessão, CSRF e logout conforme seus contratos.

## Alternativas consideradas

- Manter `permitAll()` até concluir todo o login: deixaria futuros caminhos de escrita expostos durante a migração.
- Exigir credenciais reais em todos os ambientes: impediria iniciar e testar a aplicação sem segredos.

Veja também: [ADR-002](2026-10-modelo-admin-access.md), `PLAN.md`.
