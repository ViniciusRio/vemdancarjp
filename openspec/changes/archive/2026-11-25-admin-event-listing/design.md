# Design

## Context

Ver `proposal.md` para a motivação e `specs/admin-events/spec.md` para o contrato. `SecurityConfig` libera somente `POST /api/admin/events` pelo `AdminAccessAuthorizationManager` antes de `/api/admin/**.denyAll()`; a mesma política valida Google OIDC e consulta `admin_access` pelo `sub` a cada requisição. O despacho interno `DispatcherType.ERROR` já é permitido para devolver erros de infraestrutura, sem autorizar chamadas HTTP normais a `/error`. Os handlers de segurança devolvem `401 {"error":"UNAUTHENTICATED"}` ou `403 {"error":"FORBIDDEN"}`; só respostas de `/api/auth/**` recebem `no-store` explicitamente nesses handlers.

`AgendaService` busca eventos com `EventRepository.findAllByOrderBySortOrderAsc()` e os distribui na ordem de `DayOfWeek.values()`. A ordem entre empates de `sortOrder` não é contratada. `EventRepository` também tem `findAllByDayIdOrderBySortOrderAsc(String)`. `Event` e a V1 definem `id BIGSERIAL`/`Long`, `sort_order INTEGER`/`Integer`, `day_id VARCHAR(10)` restrito aos sete IDs e campos textuais, com `instagram` e `frequency` anuláveis. O DTO público omite `id`, `dayId` e `sortOrder`. O painel administrativo Vue continua lendo e escrevendo no Supabase.

Não há especificações principais em `openspec/specs/`. O delta anterior `admin-event-creation/specs/admin-events/spec.md` determina que o GET permaneça bloqueado; esta mudança evolui explicitamente essa regra para liberar apenas a listagem. Quando os deltas forem incorporados no futuro, essa cláusula anterior precisará ser reconciliada com a presente especificação. Não sincronizar nem arquivar a mudança anterior nesta etapa.

## Goals / Non-Goals

**Goals:**

- Reutilizar a política de autorização vigente na cadeia HTTP, com matcher exato para GET antes do bloqueio geral.
- Dar à futura edição uma leitura fiel dos eventos do PostgreSQL, sem modificar a consulta pública nem criar ordem artificial entre empates.
- Restringir o novo cabeçalho de cache a `GET /api/admin/events`, inclusive respostas produzidas antes do controller.

**Non-Goals:**

- Integrar o painel Vue, reconciliar Supabase e PostgreSQL ou definir cookies/CORS de produção.
- Implementar edição, exclusão, paginação, novos filtros, migrations ou dependências.
- Atualizar `PLAN.md` nesta mudança de implementação. Registrar seus trechos desatualizados para uma revisão documental posterior; não tratá-los como comportamento atual.

## Decisions

### 1. Autorização pontual e CSRF

Adicionar um matcher exato `GET /api/admin/events` com o `AdminAccessAuthorizationManager`, ao lado do matcher POST e antes de `/api/admin/**.denyAll()`. Preservar `.anyRequest().denyAll()`, a regra `DispatcherType.ERROR` apenas para despachos internos e os handlers JSON atuais. GET usa a regra CSRF padrão do Spring Security, que não exige token para método seguro; POST continua exigindo token. Uma liberação de `/api/admin/**` seria ampla demais, e uma checagem exclusiva no controller ficaria subordinada ao `denyAll` atual. Falha de consulta a `admin_access` continua propagando erro `5xx`, sem transformá-lo em `403`.

### 2. Leitura, projeção e ordem

Criar um DTO de resposta administrativa próprio com `Long id`, `String dayId`, `String name`, `String venue`, `String neighborhood`, `String instagram`, `String frequency` e `Integer sortOrder`, incluindo explicitamente os nulos. Para a lista sem filtro, aproveitar a consulta existente por `sortOrder` e achatar os eventos na sequência `DayOfWeek.values()`; dentro de cada dia, preservar a ordem da consulta, sem comparator secundário. Para um `dayId` válido, usar a consulta existente filtrada e ordenada somente por `sortOrder`. Alternativas de ordenar globalmente apenas por `sortOrder` misturariam dias, enquanto `id` como desempate mudaria o contrato atual. Nenhuma mudança em `AgendaService` ou `GET /api/agenda` é necessária.

### 3. Validação local do filtro

Inspecionar todas as ocorrências de `dayId` antes de convertê-las a um `String` único: ausência significa lista completa; exatamente uma ocorrência é válida somente se o valor literal pertencer aos IDs de `DayOfWeek`; zero caracteres, espaços, maiúsculas, IDs desconhecidos e repetições recebem `400 {"error":"VALIDATION_ERROR"}`. Ignorar parâmetros de consulta com outros nomes, inclusive quando acompanharem um `dayId` válido; eles não alteram a validação estrita de `dayId`. Não aparar nem normalizar o parâmetro, conforme decisão aprovada. Reutilizar o formato do erro local de `AdminEventController` sem mudar a desserialização global ou os handlers das outras rotas. Os testes devem verificar o corpo exato e que filtro inválido não executa a leitura de eventos.

### 4. Cache restrito à rota

Definir `Cache-Control: no-store` em um ponto da cadeia que execute antes da decisão de autorização, com correspondência exata de método e caminho para `GET /api/admin/events`. Assim, sucesso, validação, 401/403 e falhas de infraestrutura `5xx` mantêm o mesmo cabeçalho sem mudar globalmente o formato ou os códigos dos erros. Conferir em testes MockMvc e, para erros de infraestrutura, na cadeia HTTP real se o despacho de erro preserva o cabeçalho; ajustar somente o tratamento específico dessa rota se necessário. Acrescentar o cabeçalho apenas no controller não cobriria 401/403, que surgem antes dele. Aplicá-lo globalmente mudaria contratos não relacionados.

### 5. Provas de integração

Estender os testes HTTP existentes com PostgreSQL de teste: array e tipos exatos, nulos, lista vazia, filtro válido/inválido/repetido, dias cruzados e grupos empatados sem presumir ordem interna; autenticação anônima/não OIDC/sem aprovação, revogação após primeiro GET na mesma `MockHttpSession` sem reinjetar autenticação, falha de consulta de aprovação que resulta em `5xx`, GET sem CSRF, `no-store` em 200/400/401/403, outras rotas negadas e agenda pública inalterada. Reutilizar o padrão dos testes de falha de infraestrutura para observar o comportamento real de despacho de erro.

## Risks / Trade-offs

- **Divergência Supabase/PostgreSQL** → O painel Vue ainda usa Supabase; a lista nova e a agenda pública usam PostgreSQL. Manter a rota fora do fluxo de administração de produção até definir fonte de verdade, reconciliação, backup e rollback do Passo 10.
- **Empates em `sortOrder`** → A consulta atual não garante ordem entre eventos empatados. Testar apenas que o grupo aparece entre valores menores e maiores; não adicionar desempate sem requisito aprovado.
- **Cabeçalho antes do controller** → 401/403 são escritos pela segurança e erros `5xx` podem envolver despacho interno. Verificar o cabeçalho em respostas reais da rota e manter qualquer ajuste restrito ao GET exato.
- **Deltas ainda não consolidados** → O delta anterior contém uma regra de bloqueio que esta mudança substitui. Registrar a evolução agora; reconciliar os deltas antes de qualquer sincronização ou arquivamento futuro.
- **Documentação de planejamento atrasada** → `PLAN.md` ainda diz que `/api/admin/**` está integralmente em `denyAll` e descreve endpoints como apenas propostos. Fazer uma revisão documental separada, sem alterar este escopo funcional.

## Migration Plan

Não há alteração de schema nem corte de dados. Após implementação e verificação local, a publicação operacional da listagem depende da estratégia de reconciliação entre Supabase e PostgreSQL prevista no Passo 10. Reverter o incremento consiste em remover o matcher GET e o código exclusivo da listagem, preservando o POST e a agenda pública; nenhuma migração de dados seria necessária.
