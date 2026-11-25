# Design

## Context

Ver `proposal.md` para a motivação e `specs/admin-events/spec.md` para o contrato. `backend/src/main/java/com/vemdancarjp/config/SecurityConfig.java` já aplica CSRF aos métodos inseguros, fornece erros JSON 401/403 e mantém `/api/admin/**` em `denyAll`. `AdminAccessAuthorizationManager` exige principal OIDC da inscrição `google`, emissor Google e `APPROVED` obtido de `admin_access` pelo `sub` a cada avaliação; seus testes ainda não atravessam a cadeia HTTP. `Event` e `EventRepository` persistem na tabela `events` da V1. A V1 usa `day_id VARCHAR(10)` limitado aos sete IDs, `sort_order INTEGER` e `TEXT` para os demais campos; a entity usa `String`, `Integer` e `Long` correspondentes. `AgendaService` lê essa tabela para `GET /api/agenda` com ordenação apenas por `sortOrder`. O formulário Vue grava no Supabase e sua função `formatInstagram` produz URL HTTPS para identificadores comuns, mas aceita qualquer entrada iniciada por `http` sem validá-la. Não há personalização de Jackson nem de desserialização JSON em `backend/src/main` ou `application.yml`.

## Goals / Non-Goals

**Goals:**

- Exercitar a política 7D.1 na cadeia HTTP para uma única operação e demonstrar em testes que a autorização depende da aprovação atual no PostgreSQL.
- Definir um contrato de entrada baseado nos campos de evento já presentes no formulário, com resposta mínima `201 {"id":123}`, validação anterior à gravação e erro de entrada estável.
- Persistir na tabela lida pela agenda pública, sem mudança de esquema nem do formato de `GET /api/agenda`.

**Non-Goals:**

- Integrar o painel Vue, migrar suas escritas ou publicar um novo fluxo administrativo de produção.
- Criar bootstrap, aprovação, revogação, listagem, edição, exclusão ou escrita de venues.
- Definir CORS, domínio, cookies de produção ou reconciliação de dados nesta etapa; essas decisões pertencem ao corte previsto no Passo 10.

## Decisions

### 1. Liberação pontual e ordem dos matchers

Inserir em `SecurityConfig` um matcher exato para `POST /api/admin/events`, associado ao `AdminAccessAuthorizationManager`, antes do matcher `/api/admin/**` que permanece em `denyAll`. Preservar `.anyRequest().denyAll()` e o filtro CSRF atual. Uma regra para todo `/api/admin/**` tornaria visíveis operações futuras sem autorização individual; uma anotação isolada no controller deixaria ambígua a precedência da regra HTTP atual. O matcher deve distinguir método e caminho; os testes precisam cobrir `GET /api/admin/events`, outra rota administrativa e outra operação de escrita com sessão aprovada.

### 2. Resposta mínima e persistência explícita

Receber `dayId`, `name`, `venue`, `neighborhood`, `instagram`, `frequency` e `sortOrder`; o cliente não fornece `id`, `googleSub` nem estado administrativo. O controller encaminha dados já validados a um serviço transacional que mapeia somente campos permitidos, grava via `EventRepository` e responde com um record/DTO contendo apenas o `Long id` gerado. Não reutilizar `EventResponse`, que representa a leitura pública, nem devolver a entity inteira: ambos produziriam contrato diferente do `201` mínimo aprovado. O `id` vem do banco e não é aceito no corpo.

### 3. Validação na fronteira HTTP

Usar um `JsonNode` no controller para verificar que o corpo é objeto, conferir uma lista fechada de campos, seus tipos JSON e obrigatoriedade, e só então construir os dados de criação. Essa é a opção local mais simples para rejeitar `id`/campos desconhecidos e coerções como `"99"` ou `1.5` em `sortOrder`; não exige alterar o `ObjectMapper` global. JSON malformado é tratado por um handler restrito à nova rota. Um DTO tipado diretamente dependeria de configuração adicional para impedir que Jackson ignorasse campos desconhecidos ou convertesse escalares.

Após a checagem estrutural, aparar espaços das bordas de todos os textos. Rejeitar `name`, `venue` e `neighborhood` vazios após o corte e `dayId` fora dos sete IDs de `DayOfWeek` ou acima de 10 caracteres. Aceitar `sortOrder` somente como inteiro JSON representável em 32 bits, inclusive negativo, pois a V1 não impõe limite semântico. Rejeitar U+0000 nos textos, que PostgreSQL `TEXT` não aceita. Os campos `name`, `venue`, `neighborhood`, `instagram` e `frequency` são `TEXT` na V1: não inventar limite de 255 caracteres com base no valor padrão de `@Column` para `String`, pois a coluna real não é `VARCHAR(255)`. Ausência, `null` ou texto apenas com espaços nos opcionais vira `null`; os demais textos opcionais são persistidos aparados. Isso evita os erros previsíveis de `NOT NULL`, `day_id` inválido, estouro de `INTEGER`, caractere NUL e tipos incompatíveis antes do `save`.

Para `instagram` não nulo, exigir URL absoluta HTTP(S) com host após aparar espaços. Não limitar ao domínio Instagram: o campo atual armazena uma URL e o formulário pode entregar URLs completas. `EventModal.vue` converte `@usuario` ou `usuario` para `https://www.instagram.com/usuario`, compatível com a regra; seu atalho `startsWith('http')` também pode produzir texto inválido, que receberá `400` quando este novo endpoint for usado. Os dados legados no Supabase/PostgreSQL não são revalidados nem migrados agora.

### 4. Erros HTTP e falha da consulta de aprovação

Preservar os handlers de `SecurityConfig`: requisição anônima sem sessão recebe `401 {"error":"UNAUTHENTICATED"}`, inclusive quando falta CSRF; sessão autenticada com CSRF ausente/inválido recebe `403 {"error":"CSRF_INVALID"}` antes da autorização; autenticação presente com CSRF válido e sem aprovação recebe `403 {"error":"FORBIDDEN"}`. Os testes existentes confirmam essas combinações para `/api/admin/events` ainda bloqueada. O `400 {"error":"VALIDATION_ERROR"}` será novo e restrito a falhas de entrada da rota; não mudar os handlers de segurança nem impor esse formato a todas as APIs. A ordem dos filtros exige CSRF válido nos testes de autorização. Alterar globalmente os handlers arriscaria regressão de sessão e logout.

`AdminAccessAuthorizationManager` já deixa propagar exceções do repositório; seu teste unitário comprova essa decisão. Manter essa propagação: falha de infraestrutura não vira `Optional.empty()`, `PENDING` nem `403`. Em MockMvc, comprovar a exceção propagada e a ausência de gravação; em requisição HTTP ao servidor, comprovar resposta `5xx` sem dados sensíveis. Não introduzir um fallback que conceda acesso ou converter a falha em erro de autorização.

A verificação HTTP real mostrou que `.anyRequest().denyAll()` intercepta o despacho interno `ERROR` do servlet e substitui uma falha da consulta por `403`. Permitir somente `DispatcherType.ERROR` na cadeia para que o tratamento padrão do servidor devolva `5xx`; uma requisição direta a `/error` continua sujeita a `denyAll`. Essa exceção de despacho não libera método ou caminho administrativo adicional. O teste HTTP cobre o `5xx` e o teste MockMvc confirma a propagação original.

### 5. Testes de autorização e de dado observável

Estender os testes MockMvc com PostgreSQL/Testcontainers para uma sessão OIDC simulada que reflita a inscrição Google e o emissor esperado. Exercitar a requisição pela cadeia completa, não apenas a política diretamente, e verificar contagem/linha no `EventRepository` após cada sucesso ou falha. Cobrir `APPROVED`, `PENDING`, ausência de linha, revogação entre requisições, falha da consulta de aprovação, principal não OIDC, ausência de sessão, CSRF ausente/inválido, validação, isolamento das demais rotas, resposta com apenas `id` e aparição do evento em `GET /api/agenda`. Preservar os testes unitários existentes da política como cobertura de suas regras internas. Em empate de `sortOrder`, verificar a presença de ambos os eventos e a posição de seu grupo frente a valores menores/maiores, sem impor desempate: `EventRepository.findAllByOrderBySortOrderAsc()` não define coluna secundária.

## Risks / Trade-offs

- **Divergência entre Supabase e PostgreSQL** → O painel atual continua escrevendo no Supabase, enquanto o endpoint novo escreve no PostgreSQL e a agenda pública lê PostgreSQL. Uma gravação feita por um caminho pode não aparecer no outro. Manter a rota fora do fluxo de produção até definir fonte de verdade, backup, reconciliação, bloqueio de escritas concorrentes no corte e rollback; não trocar o frontend nesta mudança.
- **Bootstrap ainda inexistente** → Não haverá administrador real aprovado pelo novo backend sem o procedimento interno controlado previsto no `PLAN.md`. Testes podem semear `admin_access` em banco isolado, mas isso não equivale a um fluxo de bootstrap público.
- **Precedência entre CSRF e autorização** → O filtro CSRF pode responder antes da consulta de aprovação. Os testes devem incluir token válido ao medir 401/403 de autorização e verificar separadamente `CSRF_INVALID`.
- **Coerção de JSON e erro de entrada novo** → A configuração atual não desativa coerções nem rejeita campos desconhecidos. A validação por `JsonNode` e o handler local evitam mudar globalmente contratos JSON existentes; testes de regressão devem comprovar que os handlers de segurança seguem iguais.
- **URL gerada pelo formulário** → `formatInstagram` aceita qualquer prefixo `http`, inclusive URL inválida. O novo endpoint responderá `400` nesses casos; corrigir o formulário antes de conectá-lo ao backend pertence à integração futura.
- **Empate na ordenação** → A consulta atual ordena somente por `sortOrder`. Não prometer nem implementar desempate sem requisito de produto; testar presença e agrupamento dos empatados.
- **Erros de banco** → Um erro de persistência não deve produzir resposta `201` nem gravação parcial; a transação e os testes de integração comprovam esse limite.

## Migration Plan

1. Implementar e verificar a rota apenas em ambiente local/teste, mantendo o painel Vue no Supabase e sem migrations.
2. Antes de qualquer exposição operacional, decidir a fonte de verdade de `events`, comparar e reconciliar as duas bases, garantir backup e planejar o período sem escritas concorrentes conforme `PLAN.md` Passo 10.
3. Se a liberação causar problema, retirar somente o matcher pontual e o endpoint para restaurar `denyAll`; preservar e reconciliar as linhas já criadas no PostgreSQL antes de redirecionar clientes. Reverter apenas a configuração HTTP não desfaz dados gravados.

## Open Questions

- Quais serão os domínios, origens CORS, políticas de cookie e ambiente de exposição do backend quando ocorrer o corte? Isso afeta a implantação e o fluxo real do navegador, sem mudar o contrato desta rota.
- Qual será o procedimento interno e auditável de bootstrap do primeiro `APPROVED`? A criação de eventos depende dele em produção, mas sua definição pertence à etapa administrativa própria.
