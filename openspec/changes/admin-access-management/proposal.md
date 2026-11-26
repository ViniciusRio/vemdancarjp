# Proposal

## Why

Passo 9. O backend já exige um admin `APPROVED` para tudo que é administrativo, mas ainda não existe forma de alguém virar admin nem de tirar o acesso de alguém, exceto editando o banco. Sem isso, o painel não tem como sair do Supabase, onde a gestão de admins vive hoje.

## What Changes

- **Primeiro admin:** a variável `APP_BOOTSTRAP_ADMIN_SUB` contém o `sub` Google de quem será o primeiro admin. Ao subir, o backend aprova esse `sub`, mas só se não existir nenhum admin `APPROVED`. Depois que existe um admin, a variável é ignorada.
- **Pedido de acesso:** `POST /api/auth/access-request` é usado por quem fez login com Google e ainda não é admin. Cria um registro `PENDING` com o e-mail e o nome do token. Se já houver registro, a rota não muda nada e devolve o status atual. Nenhum caminho dela aprova alguém.
- **Gestão de admins**, só para admins aprovados:
  - `GET /api/admin/access` lista pendentes e aprovados;
  - `PUT /api/admin/access/{id}` com `{"status":"APPROVED"}` aprova um pendente;
  - `DELETE /api/admin/access/{id}` rejeita um pendente ou revoga um aprovado, apagando o registro.
- Ninguém pode remover o próprio acesso, e o último admin aprovado não pode ser removido. As duas regras valem também sob concorrência.
- `{id}` é o UUID de `admin_access`, validado cru, em minúsculas e no formato canônico.
- O `sub` Google não aparece em nenhuma resposta.
- Todas as respostas trazem `no-store`, e as escritas exigem CSRF.
- A spec `admin-venues` passa a incluir `/api/admin/access/**` entre os prefixos administrativos que têm regra própria.

## Capabilities

### New Capabilities

- `admin-access`: primeiro admin, pedido de acesso e gestão dos admins (aprovar, rejeitar e revogar).

### Modified Capabilities

- `admin-venues`: o requisito "Demais rotas administrativas bloqueadas" passa a reconhecer `/api/admin/access/**` como prefixo com regra própria.

## Impact

- **Backend:**
  - um bootstrap na inicialização;
  - um controller de pedido de acesso e um de gestão;
  - um serviço transacional com trava contra remover o último admin;
  - um `AuthorizationManager` de "identidade Google válida" sem exigir aprovação;
  - `SecurityConfig` passa a permitir métodos por recurso na lista admin.
- **Sem migration:** o schema atual (`PENDING`/`APPROVED`, `email`, `name`, `requested_at`) basta.
- **Sem mudanças** no frontend. O painel Vue continua no Supabase até o Passo 10.
- **Fora de escopo:** importar admins ou pendências do Supabase, que é proibido pelo plano.
