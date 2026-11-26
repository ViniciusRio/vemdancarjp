# admin-events Specification

## Purpose
Permitir a criação controlada de eventos da agenda por administradores aprovados, com persistência na base lida pela agenda pública e sem ampliar o acesso às outras operações administrativas.

## Requirements

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
Dentro de `/api/admin/events/**`, a autorização administrativa SHALL valer apenas para `POST /api/admin/events`, `GET /api/admin/events`, `PUT /api/admin/events/{id}` e `DELETE /api/admin/events/{id}`. As demais combinações de método e caminho nesse prefixo SHALL continuar bloqueadas, inclusive para sessões `APPROVED`. Rotas fora desse prefixo são regidas pelos requisitos de suas próprias capabilities.

#### Scenario: Administrador aprovado tenta outra rota
- **WHEN** uma sessão aprovada solicita uma operação de eventos fora dessas quatro, como `GET /api/admin/events/{id}`, `PATCH /api/admin/events/{id}` ou `PUT /api/admin/events`
- **THEN** o sistema nega a requisição e não executa a operação

#### Scenario: Leitura pública continua aberta
- **WHEN** um visitante sem sessão solicita `GET /api/agenda`
- **THEN** o sistema mantém a resposta pública disponível

### Requirement: Listagem administrativa de eventos
O sistema SHALL responder a `GET /api/admin/events` com `200` e um array JSON simples, sem agrupamento por dia, contendo todos os eventos armazenados no PostgreSQL quando `dayId` estiver ausente. Cada item SHALL conter exatamente `id`, `dayId`, `name`, `venue`, `neighborhood`, `instagram`, `frequency` e `sortOrder`.

#### Scenario: Listagem completa
- **WHEN** um administrador aprovado solicita `GET /api/admin/events` sem filtro e existem eventos em dias diferentes
- **THEN** o sistema retorna todos eles em um único array, com `id` numérico inteiro, `sortOrder` numérico inteiro, textos nos demais campos e `null` em `instagram` ou `frequency` quando ausentes no banco

#### Scenario: Nenhum evento
- **WHEN** a consulta autorizada não encontra eventos
- **THEN** o sistema responde `200` com `[]`

### Requirement: Filtro de dia estrito
O sistema SHALL aceitar no máximo uma ocorrência de `dayId`, com valor literal igual a um dos sete identificadores minúsculos de dia existentes. Valor inválido ou ocorrência repetida SHALL produzir `400` com exatamente `{"error":"VALIDATION_ERROR"}`. Parâmetros de consulta desconhecidos SHALL ser ignorados.

#### Scenario: Filtro válido
- **WHEN** um administrador aprovado solicita `GET /api/admin/events?dayId=friday`
- **THEN** a resposta `200` contém somente eventos de sexta-feira, ou `[]` se não houver nenhum

#### Scenario: Valor inválido
- **WHEN** `dayId` está vazio, contém espaços, letras maiúsculas ou um identificador desconhecido
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}`

#### Scenario: Parâmetro repetido
- **WHEN** a requisição contém duas ou mais ocorrências de `dayId`, mesmo que os valores sejam válidos e iguais
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}`

#### Scenario: Parâmetro desconhecido
- **WHEN** uma requisição autorizada contém um parâmetro de consulta diferente de `dayId`, com ou sem um `dayId` válido
- **THEN** o sistema ignora o parâmetro desconhecido e retorna a lista completa ou filtrada pelo `dayId` válido, respectivamente

### Requirement: Ordenação compatível com a agenda
O sistema SHALL apresentar os dias na sequência de segunda a domingo e, dentro de cada dia, eventos com `sortOrder` crescente. Para eventos do mesmo dia com `sortOrder` igual, a ordem relativa SHALL permanecer não especificada; o sistema não SHALL introduzir um critério de desempate.

#### Scenario: Dias misturados e ordens diferentes
- **WHEN** há eventos de dias distintos e eventos do mesmo dia com `sortOrder` diferentes
- **THEN** a lista apresenta primeiro os eventos de segunda-feira, depois os de terça-feira e assim até domingo, com `sortOrder` crescente dentro de cada dia

#### Scenario: Empate de sortOrder
- **WHEN** dois eventos do mesmo dia compartilham `sortOrder` e existem eventos desse dia com valores menores e maiores
- **THEN** os dois empatados aparecem entre os grupos menor e maior, sem garantia da ordem relativa entre eles

### Requirement: Autorização restrita da listagem
O sistema SHALL permitir `GET /api/admin/events` apenas com sessão Google OIDC válida e aprovação `APPROVED` vigente consultada no backend para o `sub` validado. Essa liberação SHALL evoluir a regra anterior que bloqueava o GET, sem alterar o POST, o PUT e o DELETE autorizados por seus próprios requisitos nem liberar outras operações em `/api/admin/events/**`.

#### Scenario: Sessão aprovada sem CSRF
- **WHEN** uma sessão Google OIDC aprovada faz `GET /api/admin/events` sem token CSRF
- **THEN** o sistema retorna a listagem com `200` e não altera eventos

#### Scenario: Sessão ausente
- **WHEN** a requisição não possui sessão autenticada válida
- **THEN** o sistema responde `401` com `{"error":"UNAUTHENTICATED"}` e não divulga eventos

#### Scenario: Identidade sem aprovação vigente
- **WHEN** a identidade autenticada não é Google OIDC validada, não tem registro aprovado ou possui estado diferente de `APPROVED`
- **THEN** o sistema responde `403` com `{"error":"FORBIDDEN"}` e não divulga eventos

#### Scenario: Revogação na mesma sessão
- **WHEN** uma sessão aprovada consulta a lista, sua aprovação deixa de ser `APPROVED` no banco e a mesma sessão faz outro GET sem novo login
- **THEN** a segunda requisição recebe `403` com `{"error":"FORBIDDEN"}` e não divulga eventos

#### Scenario: Falha na consulta de aprovação
- **WHEN** a consulta ao estado administrativo falha por indisponibilidade de infraestrutura
- **THEN** a requisição não recebe a lista nem é tratada como ausência normal de aprovação; a falha produz erro `5xx`

#### Scenario: Outras operações administrativas permanecem bloqueadas
- **WHEN** uma sessão aprovada solicita, dentro de `/api/admin/events/**`, um método ou caminho fora de `GET` e `POST /api/admin/events` e de `PUT` e `DELETE /api/admin/events/{id}`
- **THEN** o sistema nega a requisição e não executa a operação

### Requirement: Respostas da listagem sem cache
O sistema SHALL incluir `Cache-Control: no-store` em todas as respostas de `GET /api/admin/events`, inclusive sucesso, validação inválida e erros de autenticação ou autorização, sem alterar os códigos ou corpos de erro existentes em outras rotas.

#### Scenario: Sucesso ou filtro inválido
- **WHEN** a listagem responde `200` ou a validação de `dayId` responde `400`
- **THEN** a resposta contém `Cache-Control: no-store`

#### Scenario: Acesso negado
- **WHEN** a listagem responde `401` ou `403`
- **THEN** a resposta contém `Cache-Control: no-store` e preserva o respectivo corpo de erro existente

#### Scenario: Falha de infraestrutura
- **WHEN** `GET /api/admin/events` resulta em erro `5xx` por falha de infraestrutura
- **THEN** a resposta contém `Cache-Control: no-store`, sem aplicar esse requisito a outras rotas

### Requirement: Agenda pública preservada
O sistema SHALL manter `GET /api/agenda` público, com seu formato agrupado por dia e sua ordenação atual, independentemente do filtro ou do resultado da listagem administrativa.

#### Scenario: Visitante consulta agenda
- **WHEN** um visitante sem sessão solicita `GET /api/agenda` após a adição da listagem administrativa
- **THEN** a resposta pública mantém os dias e eventos no formato e na ordem existentes

### Requirement: Edição de evento aprovada
O sistema SHALL aceitar `PUT /api/admin/events/{id}` somente com sessão Google OIDC válida, aprovação `APPROVED` vigente consultada no banco para o `sub` validado a cada requisição e token CSRF da sessão no cabeçalho `X-CSRF-TOKEN`. A edição SHALL substituir todos os campos editáveis do evento. Edições concorrentes do mesmo evento não são detectadas: a última gravação prevalece.

#### Scenario: Administrador aprovado edita evento
- **WHEN** uma sessão aprovada envia, com CSRF válido, um corpo válido para um `id` existente
- **THEN** o sistema grava os novos valores e responde `200` com o evento completo: `id`, `dayId`, `name`, `venue`, `neighborhood`, `instagram`, `frequency` e `sortOrder`, com os nulos explícitos

#### Scenario: Opcional ausente na edição
- **WHEN** a edição autorizada omite `instagram` ou `frequency`, ou os envia nulos ou só com espaços, para um evento que tinha esses valores
- **THEN** o sistema grava esses campos como `null`

#### Scenario: Sessão ausente
- **WHEN** a requisição não possui sessão autenticada válida
- **THEN** o sistema responde `401` com `{"error":"UNAUTHENTICATED"}` e não altera o evento

#### Scenario: Identidade sem aprovação vigente
- **WHEN** o usuário autenticado não tem aprovação `APPROVED` atual, inclusive após revogação na mesma sessão, ou não possui identidade Google OIDC validada
- **THEN** o sistema responde `403` com `{"error":"FORBIDDEN"}` e não altera o evento

#### Scenario: CSRF ausente ou inválido
- **WHEN** uma sessão autenticada envia a edição sem token CSRF válido ou com token de outra sessão
- **THEN** o sistema responde `403` com `{"error":"CSRF_INVALID"}` e não altera o evento

#### Scenario: Falha na consulta de aprovação
- **WHEN** a consulta ao estado administrativo falha por indisponibilidade de infraestrutura
- **THEN** o sistema não altera o evento e responde com erro `5xx`, sem apresentar a falha como `403`

### Requirement: Contrato e validação da edição
O corpo da edição SHALL seguir o mesmo contrato e as mesmas regras de validação da criação, inclusive aparar espaços, persistir opcionais vazios como `null`, validar a URL do Instagram e rejeitar campos fora do contrato, como `id`.

#### Scenario: Corpo inválido
- **WHEN** uma edição autorizada envia um corpo que a criação rejeitaria, inclusive JSON malformado ou um campo `id` no corpo
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não altera o evento

### Requirement: Remoção de evento aprovada
O sistema SHALL aceitar `DELETE /api/admin/events/{id}` somente com sessão Google OIDC válida, aprovação `APPROVED` vigente consultada no banco a cada requisição e token CSRF da sessão. A remoção SHALL apagar o evento de forma definitiva.

#### Scenario: Administrador aprovado remove evento
- **WHEN** uma sessão aprovada envia, com CSRF válido, a remoção de um `id` existente
- **THEN** o sistema apaga exatamente esse evento e responde `204` sem corpo

#### Scenario: Remoção de evento inexistente
- **WHEN** uma remoção autorizada, com `{id}` válido, aponta para um evento que não existe, inclusive numa repetição de remoção já concluída
- **THEN** o sistema responde `204` sem corpo e não apaga nenhum outro evento

#### Scenario: Remoção sem autorização
- **WHEN** a remoção chega sem sessão, sem aprovação vigente, sem CSRF válido ou durante falha na consulta de aprovação
- **THEN** o sistema responde `401`, `403` ou `5xx` com os mesmos corpos da edição e não apaga o evento

### Requirement: Identificador do evento
O segmento `{id}` SHALL ser um inteiro decimal positivo que caiba em 64 bits, sem sinal, espaços ou zeros à esquerda. A validação do identificador e do corpo SHALL ocorrer depois da autorização e antes de qualquer leitura ou escrita em eventos.

#### Scenario: Identificador inválido
- **WHEN** uma requisição autorizada usa `{id}` igual a `0`, negativo, não numérico, com zeros à esquerda ou acima do limite de 64 bits
- **THEN** o sistema responde `400` com `{"error":"VALIDATION_ERROR"}` e não altera nem apaga nenhum evento

#### Scenario: Edição de evento inexistente
- **WHEN** uma edição autorizada, com `{id}` e corpo válidos, aponta para um evento que não existe
- **THEN** o sistema responde `404` com `{"error":"NOT_FOUND"}` e não cria evento

### Requirement: Respostas de edição e remoção sem cache
Todas as respostas de `PUT` e `DELETE` em `/api/admin/events/{id}` SHALL incluir `Cache-Control: no-store`, inclusive `400`, `401`, `403`, `404` e `5xx`.

#### Scenario: Qualquer resultado
- **WHEN** uma edição ou remoção termina com sucesso ou com qualquer erro
- **THEN** a resposta contém `Cache-Control: no-store`

### Requirement: Agenda pública reflete edição e remoção
`GET /api/agenda` SHALL refletir a edição ou remoção já concluída, sem mudar seu formato nem sua ordenação.

#### Scenario: Evento editado muda de dia
- **WHEN** um evento é editado para outro `dayId` e outro `sortOrder` e a agenda pública é consultada
- **THEN** o evento aparece só no novo dia, na posição do novo `sortOrder`, com os campos públicos atualizados

#### Scenario: Evento removido
- **WHEN** um evento é removido e a agenda pública é consultada
- **THEN** o evento não aparece em nenhum dia
