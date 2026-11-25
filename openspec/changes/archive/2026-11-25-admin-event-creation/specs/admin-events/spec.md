# Spec Delta

## Purpose

Permitir a criação controlada de eventos da agenda por administradores aprovados, com persistência na base lida pela agenda pública e sem ampliar o acesso às outras operações administrativas.

## ADDED Requirements

### Requirement: Criação de evento aprovada
O sistema SHALL aceitar `POST /api/admin/events` somente com sessão autenticada por Google OIDC e aprovação `APPROVED` vigente associada ao `sub` validado no backend. A aprovação SHALL ser consultada no banco a cada requisição e não SHALL ser inferida de email, dados do corpo ou privilégios legados do Supabase.

#### Scenario: Administrador aprovado cria evento
- **WHEN** uma sessão Google OIDC válida, com `sub` aprovado, envia uma requisição válida com CSRF válido
- **THEN** o sistema cria exatamente um evento no PostgreSQL e responde `201` com JSON contendo somente `id`, cujo valor é o identificador inteiro gerado (por exemplo, `{"id":123}`)

#### Scenario: Sessão ausente ou expirada
- **WHEN** a requisição não possui sessão autenticada válida
- **THEN** o sistema responde `401` com `{"error":"UNAUTHENTICATED"}` e não cria evento

#### Scenario: Identidade sem aprovação atual
- **WHEN** um usuário autenticado está sem linha de acesso, está `PENDING`, perdeu a aprovação ou não possui identidade Google OIDC validada
- **THEN** o sistema responde `403` com `{"error":"FORBIDDEN"}` e não cria evento

#### Scenario: Revogação afeta sessão existente
- **WHEN** uma sessão antes aprovada faz nova requisição após a aprovação deixar de estar `APPROVED` no banco
- **THEN** o sistema nega a nova criação com `403` e não cria evento

#### Scenario: Falha na consulta de aprovação
- **WHEN** a consulta a `admin_access` falha por indisponibilidade do banco durante uma requisição autenticada com CSRF válido
- **THEN** o sistema não autoriza nem cria evento e responde com erro de infraestrutura `5xx`, sem apresentar a falha como ausência normal de aprovação (`403`)

### Requirement: Proteção CSRF da criação
O sistema SHALL exigir token CSRF da sessão no cabeçalho `X-CSRF-TOKEN` para `POST /api/admin/events`, usando o fluxo existente de `GET /api/auth/csrf`.

#### Scenario: Token ausente ou inválido em sessão autenticada
- **WHEN** uma sessão autenticada envia `POST /api/admin/events` sem token CSRF válido
- **THEN** o sistema responde `403` com `{"error":"CSRF_INVALID"}` e não cria evento

#### Scenario: Token de outra sessão
- **WHEN** uma sessão autenticada envia token CSRF de outra sessão
- **THEN** o sistema responde `403` com `{"error":"CSRF_INVALID"}` e não cria evento

### Requirement: Contrato e validação da entrada
O sistema SHALL aceitar um objeto JSON apenas com `dayId`, `name`, `venue`, `neighborhood` e `sortOrder` obrigatórios, mais `instagram` e `frequency` opcionais ou nulos. Ele SHALL aparar espaços nas bordas dos textos, rejeitar obrigatórios vazios após isso e persistir opcionais vazios como `null`.

#### Scenario: Corpo válido com campos opcionais ausentes
- **WHEN** a requisição autorizada contém `dayId` válido, textos obrigatórios não vazios e `sortOrder` inteiro, sem `instagram` ou `frequency`
- **THEN** o sistema persiste os campos opcionais como `null` e devolve `201` com apenas o `id` gerado

#### Scenario: Espaços em campos textuais
- **WHEN** uma requisição autorizada envia espaços nas bordas de textos obrigatórios ou opcionais, e um opcional contém apenas espaços
- **THEN** o sistema persiste os textos não vazios sem os espaços das bordas e o opcional vazio como `null`

#### Scenario: URL de Instagram válida
- **WHEN** `instagram` contém uma URL absoluta com esquema `http` ou `https` e host após remoção de espaços das bordas
- **THEN** o sistema aceita e persiste a URL aparada

#### Scenario: URL de Instagram inválida
- **WHEN** `instagram` contém identificador sem URL, URL relativa, esquema diferente de HTTP(S), URL sem host ou URL malformada
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não cria evento

#### Scenario: Entrada inválida
- **WHEN** falta um campo obrigatório, um texto obrigatório contém apenas espaços, `dayId` não é um dos sete IDs existentes ou excede 10 caracteres, `sortOrder` não é um inteiro JSON de 32 bits ou um texto contém caractere NUL
- **THEN** o sistema responde `400` em JSON com `error` igual a `VALIDATION_ERROR` e não cria evento

#### Scenario: JSON malformado
- **WHEN** a requisição autorizada apresenta JSON malformado ou tipos incompatíveis
- **THEN** o sistema responde `400` em JSON com `error` igual a `VALIDATION_ERROR` e não cria evento

#### Scenario: Campo de controle ou desconhecido
- **WHEN** o corpo inclui `id`, dados de autorização ou qualquer campo fora do contrato de criação
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não cria evento

### Requirement: Visibilidade na agenda pública
O evento criado SHALL aparecer em `GET /api/agenda` no dia indicado, respeitando a ordenação crescente por `sortOrder` já usada na leitura pública, sem alterar o formato da agenda. A ordem relativa entre eventos com `sortOrder` igual não é garantida pelo contrato atual.

#### Scenario: Leitura depois da criação
- **WHEN** um evento é criado com sucesso e a agenda pública é consultada
- **THEN** o evento aparece no dia correspondente com os campos públicos existentes

#### Scenario: Eventos com mesma ordem
- **WHEN** dois eventos do mesmo dia compartilham `sortOrder` e há eventos com valores menores e maiores
- **THEN** ambos aparecem na agenda entre os grupos de valores menores e maiores, sem garantia sobre a ordem relativa dos dois empatados

### Requirement: Isolamento das demais rotas administrativas
A autorização desta mudança SHALL valer apenas para `POST /api/admin/events`. Todas as demais combinações de método e caminho em `/api/admin/**` SHALL continuar bloqueadas, inclusive para sessões `APPROVED`.

#### Scenario: Administrador aprovado tenta outra rota
- **WHEN** uma sessão aprovada solicita `GET /api/admin/events` ou outra operação administrativa não liberada
- **THEN** o sistema nega a requisição e não executa a operação

#### Scenario: Leitura pública continua aberta
- **WHEN** um visitante sem sessão solicita `GET /api/agenda`
- **THEN** o sistema mantém a resposta pública disponível
