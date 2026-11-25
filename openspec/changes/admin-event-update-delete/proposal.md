# Proposal

## Why

O backend já cria e lista eventos no PostgreSQL por rotas administrativas protegidas, mas administradores ainda não conseguem corrigir nem remover um evento. Editar e excluir completam o CRUD de eventos do Passo 8, que é pré-requisito para tirar o painel do Supabase.

## What Changes

- Adicionar `PUT /api/admin/events/{id}` como substituição completa do evento, com o mesmo contrato de corpo e validação do `POST`. Responde `200` com o evento atualizado no formato da listagem administrativa.
- Adicionar `DELETE /api/admin/events/{id}`. Responde `204` sem corpo.
- `{id}` aceita só inteiro decimal positivo. Valor inválido recebe `400 {"error":"VALIDATION_ERROR"}`, e `id` inexistente recebe `404 {"error":"NOT_FOUND"}`.
- As duas rotas usam a mesma autorização do `POST`: sessão Google OIDC, aprovação `APPROVED` consultada a cada requisição e token CSRF obrigatório.
- `Cache-Control: no-store` em todas as respostas das duas rotas, inclusive nos erros.
- Alterar o requisito de isolamento: além do `POST` e do `GET` da coleção, passam a ser liberados só o `PUT` e o `DELETE` do item. Todo o resto de `/api/admin/**` continua bloqueado.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `admin-events`: novos requisitos de edição e remoção, e mudança no requisito "Isolamento das demais rotas administrativas".

## Impact

- **Backend:** matchers em `SecurityConfig`, rotas novas em `AdminEventController`, serviço de escrita de eventos e reuso da validação do `POST`.
- **Testes:** integração com PostgreSQL de teste para autorização, CSRF, validação, 404, `no-store`, efeito na agenda pública e isolamento das demais rotas.
- **Sem mudanças** em schema, migrations, dependências ou frontend. O painel Vue segue no Supabase até o Passo 10.
