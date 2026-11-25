# Proposal

## Why

O backend já cria eventos no PostgreSQL por uma rota administrativa protegida, mas administradores ainda não conseguem consultar nessa base os registros necessários à futura edição. A listagem é o próximo incremento do Passo 8 e permite conferir o conteúdo efetivo da agenda sem antecipar a migração do painel Vue, que continua usando Supabase.

## What Changes

- Adicionar `GET /api/admin/events` para sessões Google OIDC com aprovação `APPROVED` vigente, retornando `200` com um array JSON simples de eventos do PostgreSQL, ou `[]` quando não houver resultados.
- Aceitar um único filtro opcional `dayId` com um dos sete identificadores minúsculos exatos; valores inválidos ou repetidos recebem `400 {"error":"VALIDATION_ERROR"}`.
- Expor `id`, `dayId`, `name`, `venue`, `neighborhood`, `instagram`, `frequency` e `sortOrder`; ordenar a lista de segunda a domingo e, dentro de cada dia, por `sortOrder` crescente, sem desempate novo.
- Exigir `Cache-Control: no-store` nas respostas da rota, inclusive 400, 401 e 403. Manter os contratos existentes de autenticação e autorização, sem exigir CSRF para GET.
- Evoluir expressamente o requisito da mudança `admin-event-creation` que bloqueava o GET: liberar apenas este método e caminho, preservar o POST já autorizado e manter todas as outras operações em `/api/admin/**` bloqueadas.

## Capabilities

### New Capabilities

- `admin-events`: acesso administrativo protegido a eventos. Ainda não há especificações principais em `openspec/specs/`; este delta amplia o comportamento descrito pelo delta `admin-event-creation/specs/admin-events/spec.md`, sem sincronizá-lo ou arquivá-lo.

### Modified Capabilities

Nenhuma especificação principal existente.

## Impact

- Backend: autorização pontual em `SecurityConfig`, leitura em `EventRepository`/serviço, DTO e controller administrativos, validação do parâmetro e cabeçalho de cache restrito à rota.
- Testes: contrato, filtro, ordem e empates, sessão e revogação, falha de infraestrutura, ausência de CSRF para GET, isolamento das demais rotas e regressão de `GET /api/agenda`.
- Sem mudanças no frontend Vue, schema, migrations ou dependências. O painel ainda lê e escreve no Supabase, enquanto esta rota e a agenda pública leem PostgreSQL; os dados podem divergir até a reconciliação e o corte do Passo 10.
- `PLAN.md` contém trechos anteriores ao POST administrativo e deve ser atualizado em uma etapa documental específica, sem ampliar esta implementação.
