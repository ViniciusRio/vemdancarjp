---
type: tarefa
status: aguardando-revisao
---

# Tarefa: Configuração inicial de segurança e Google OIDC 7B

## Objetivo

Iniciar Google OIDC e fechar a API administrativa no backend sem alterar o login Supabase do Vue.

## Escopo

- Starter OAuth2 Client, cadeia Spring Security e configuração por ambiente.
- Testes HTTP de agenda pública, 401/403, CSRF e redirecionamento Google.
- Atualização pontual de `PLAN.md` e `LEARNING.md`.

## Critérios de aceitação

- [x] `GET /api/agenda` continua público.
- [x] `/api/admin/**` é negado mesmo com principal autenticado.
- [x] A API responde 401/403 em JSON; mutações sem CSRF válido são bloqueadas.
- [x] O início Google redireciona ao provedor e cria sessão para o estado OAuth2.
- [x] Testes iniciam sem credenciais Google reais.
- [x] Configuração parcial do Google impede a inicialização, sem disponibilizar login incompleto.
- [ ] Revisão humana do diff.

## Contexto relevante

- [[decisoes/2026-10-oidc-seguranca-inicial]]
- [[decisoes/2026-10-modelo-admin-access]]

## Arquivos modificados

- `backend/build.gradle`
- `backend/src/main/java/com/vemdancarjp/config/SecurityConfig.java`
- `backend/src/test/java/com/vemdancarjp/config/SecurityConfigIntegrationTest.java`
- `backend/src/test/java/com/vemdancarjp/config/SecurityConfigStartupTest.java`
- `.env.example`, `PLAN.md`, `LEARNING.md` e esta documentação do vault.

## Testes executados

- `cd backend && ./gradlew test --rerun-tasks` — 26 testes, 0 falhas.
- `cd backend && ./gradlew build` — sucesso.

## Decisões tomadas

- OAuth2 Login é ativado somente com registro Google configurado por variáveis de ambiente.
- Erros CSRF usam o `AccessDeniedHandler` padrão de `exceptionHandling`; não há personalização de `CsrfFilter`.
- `.anyRequest().denyAll()` bloqueia os futuros endpoints de sessão e CSRF até sua implementação.
- Toda a administração permanece bloqueada até existir autorização no backend.
