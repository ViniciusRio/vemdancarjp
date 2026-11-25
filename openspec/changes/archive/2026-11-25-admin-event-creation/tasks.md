# Tasks

## 1. Autorização HTTP pontual

- [x] 1.1 Acrescentar cenários MockMvc com PostgreSQL em `backend/src/test/java/com/vemdancarjp/config/` para `APPROVED`, `PENDING`, ausência de linha, revogação na mesma sessão, identidade não OIDC, sessão ausente e CSRF ausente/inválido; verificar `401 UNAUTHENTICATED`, `403 FORBIDDEN` e `403 CSRF_INVALID` conforme os handlers existentes e nenhuma gravação nas falhas.
- [x] 1.2 Ligar `POST /api/admin/events` ao `AdminAccessAuthorizationManager` em `SecurityConfig.java`, antes do matcher `/api/admin/**` com `denyAll`, preservando CSRF e `.anyRequest().denyAll()`; verificar os cenários HTTP de 1.1 e os testes existentes de segurança.
- [x] 1.3 Testar com uma sessão `APPROVED` que `GET /api/admin/events`, outra rota administrativa e outro método de escrita permaneçam bloqueados, enquanto `GET /api/agenda` siga público; verificar por MockMvc que nenhuma operação não liberada grava dados.
- [x] 1.4 Simular falha do repositório na consulta de aprovação com sessão OIDC e CSRF válidos; verificar exceção propagada no MockMvc, resposta `5xx` em teste HTTP ao servidor, ausência de criação e nenhuma conversão em `403` ou aprovação implícita.

## 2. Criação e contrato de entrada

- [x] 2.1 Criar testes de integração para corpo válido, resposta `201` contendo somente o `id` inteiro gerado, exatamente uma linha em `events`, opcionais nulos e leitura posterior em `GET /api/agenda`; verificar com MockMvc, `EventRepository` e PostgreSQL/Testcontainers.
- [x] 2.2 Implementar controller, resposta mínima e serviço transacional de criação em `backend/src/main/java/com/vemdancarjp/`, mapeando somente campos permitidos para `EventRepository`; verificar que os testes de 2.1 passam sem migration e que o shape público permanece igual.
- [x] 2.3 Criar testes de validação para `dayId` fora dos sete IDs ou maior que 10 caracteres, obrigatórios ausentes/brancos, NUL textual, `sortOrder` fora de `INTEGER` de 32 bits ou com tipo não inteiro, URL `instagram` válida/inválida, tipos incompatíveis, JSON malformado e campos extras como `id`; verificar `400 {"error":"VALIDATION_ERROR"}` e nenhuma linha nova.
- [x] 2.4 Validar o corpo com `JsonNode` e lista fechada de campos, aparar todos os textos, converter opcionais vazios em `null` e tratar erros de entrada somente na nova rota; verificar os testes de 2.3, a compatibilidade com URLs produzidas por `EventModal.vue` e a ausência de mudança global no JSON.
- [x] 2.5 Testar dois eventos com o mesmo `sortOrder` junto de valores menores e maiores em `GET /api/agenda`; verificar presença e posição do grupo empatado, sem exigir ordem relativa nem mudar `EventRepository.findAllByOrderBySortOrderAsc()`.

## 3. Integração e registro da etapa

- [x] 3.1 Executar `cd backend && ./gradlew test --rerun-tasks` e `cd backend && ./gradlew build`; verificar ausência de regressão em sessão, CSRF, autorização e agenda pública, registrando quaisquer limitações de ambiente.
- [x] 3.2 Atualizar a nota da tarefa no vault, a ADR de segurança e a entrada mais recente de `LEARNING.md` para a etapa 7D.2, registrando que apenas `POST /api/admin/events` foi liberado e que o Passo 8 completo continua pendente; verificar que os documentos não descrevem um corte do Supabase já concluído.
