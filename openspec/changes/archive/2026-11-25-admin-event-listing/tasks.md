# Tasks

## 1. Contrato e leitura de eventos

- [x] 1.1 Adicionar testes HTTP de `GET /api/admin/events` para array com exatamente oito campos e tipos corretos, nulos, lista vazia, todos os dias, filtro `dayId=friday`, valores inválidos, `dayId` repetido e parâmetros de consulta desconhecidos; verificar que os casos inválidos devolvem exatamente `400 {"error":"VALIDATION_ERROR"}` e não consultam eventos, enquanto parâmetros desconhecidos são ignorados com ou sem filtro válido.
- [x] 1.2 Adicionar testes de ordenação com eventos de vários dias e `sortOrder` diferentes, mais dois empatados entre grupos menor e maior; verificar sequência segunda a domingo e ordem crescente dentro de cada dia, sem afirmar ordem entre empatados.
- [x] 1.3 Implementar DTO administrativo, leitura e GET no controller usando os métodos existentes de `EventRepository` e os IDs de `DayOfWeek`; verificar que os testes de 1.1 e 1.2 passam e que `AgendaService` e a resposta pública não foram modificados.

## 2. Segurança e cache da rota

- [x] 2.1 Adicionar testes pela cadeia HTTP para sessão anônima (401), identidade não OIDC ou sem aprovação (403), GET aprovado sem CSRF, POST com CSRF preservado e outras operações `/api/admin/**` bloqueadas; verificar códigos e corpos atuais e ausência de mutação por GET.
- [x] 2.2 Adicionar teste de revogação com uma única `MockHttpSession`: primeiro GET aprovado, mudança de `admin_access` para não aprovado e segundo GET na mesma sessão, sem reinjetar autenticação; verificar 200 seguido de 403 e que o segundo GET usa o contexto recuperado da sessão.
- [x] 2.3 Adicionar teste de falha de infraestrutura na consulta de aprovação, incluindo a cadeia HTTP real; verificar `5xx`, ausência de lista e ausência de conversão silenciosa para 403.
- [x] 2.4 Liberar somente o matcher exato `GET /api/admin/events` com `AdminAccessAuthorizationManager` antes de `/api/admin/**.denyAll()`, preservando o POST, CSRF, handlers e `DispatcherType.ERROR`; verificar os testes 2.1–2.3 e que uma requisição HTTP normal a `/error` continua negada.
- [x] 2.5 Adicionar testes de `Cache-Control: no-store` em 200, 400, 401, 403 e erro de infraestrutura; implementar o cabeçalho somente para o GET exato em um ponto anterior à autorização; verificar que outras rotas mantêm seus contratos e que os testes passam.
- [x] 2.6 Atualizar a nota da tarefa no vault, a ADR de segurança afetada e `LEARNING.md` conforme `AGENTS.md`, registrando o GET liberado, o POST preservado, as demais rotas bloqueadas e a divergência Supabase/PostgreSQL; verificar que os documentos não declaram corte de dados ou frontend concluído.

## 3. Verificação integrada

- [x] 3.1 Executar `./gradlew test --rerun-tasks`, `./gradlew build` e `git diff --check` no backend/repositório, registrar comandos e resultados reais; verificar que `GET /api/agenda` continua público com formato e ordem existentes, que nenhuma migration/dependência/Vue foi alterada e que mudanças locais preexistentes foram preservadas.

## Workflow follow-up

- Registrar para uma revisão documental separada os trechos de `PLAN.md` que ainda descrevem `/api/admin/**` integralmente em `denyAll` e todos os endpoints administrativos como apenas propostos.
- Antes de sincronizar ou arquivar deltas em uma etapa futura, reconciliar a regra anterior de bloqueio do GET em `admin-event-creation` com a liberação pontual desta mudança; não sincronizar nem arquivar automaticamente durante esta implementação.
