# Backend

API Java + Spring Boot do Vem Dançar JP. Visão geral, comandos e plano de migração estão no `README.md` da raiz e em `PLAN.md`.

## Primeiro administrador

Ninguém vira admin por uma rota HTTP sem a aprovação de outro admin. O primeiro admin vem da variável `APP_BOOTSTRAP_ADMIN_SUB`, que contém o `sub` Google da pessoa.

Na inicialização, o backend:

- não faz nada se a variável estiver ausente ou vazia;
- não sobe se o valor tiver algo fora de `[A-Za-z0-9]` ou mais de 255 caracteres (a mensagem de erro não mostra o valor);
- aprova esse `sub`, criando o registro se preciso, mas só se não existir nenhum admin `APPROVED`;
- ignora a variável se já existir um admin `APPROVED`.

Várias instâncias subindo juntas são serializadas por um advisory lock do PostgreSQL, então só uma aprova. O log diz apenas se a variável foi aplicada ou ignorada, sem o `sub`. A variável pode continuar definida depois: ela só volta a valer se a base ficar sem nenhum admin aprovado.

### Como obter o `sub`

1. Suba o backend sem `APP_BOOTSTRAP_ADMIN_SUB`.
2. Faça login com Google em `/oauth2/authorization/google`.
3. Consulte `GET /api/auth/session` na mesma sessão e copie o campo `subject`.
4. Defina `APP_BOOTSTRAP_ADMIN_SUB` com esse valor no `.env` (nunca num arquivo versionado) e reinicie.

### Recuperação de um `sub` errado

O bootstrap aprova o valor da instância que subir primeiro, e não o valor correto. Por isso a ordem importa:

1. Corrija a variável na configuração do serviço, e não por instância, e implante.
2. Confirme que nenhuma instância com o valor antigo continua rodando. No Cloud Run, isso quer dizer 100% do tráfego na revisão nova e as instâncias antigas encerradas.
3. Só então apague os registros `APPROVED` no banco: `DELETE FROM admin_access WHERE status = 'APPROVED';`.
4. Reinicie.

Se você apagar antes de trocar a configuração, uma instância antiga pode aprovar de novo o `sub` errado.

## Gestão de acesso

- `POST /api/auth/access-request`: quem fez login com Google e tem e-mail verificado pede acesso. Cria um registro `PENDING` (`201`) ou devolve o status atual (`200`). Sem e-mail verificado, a resposta é `403 EMAIL_NOT_VERIFIED`.
- `GET /api/admin/access`: lista pendentes e aprovados. Só para admin.
- `PUT /api/admin/access/{id}` com `{"status":"APPROVED"}`: aprova um pendente. Só para admin.
- `DELETE /api/admin/access/{id}`: rejeita um pendente ou revoga um admin, apagando o registro. Ninguém remove o próprio acesso (`409 CANNOT_REMOVE_SELF`).

O contrato completo está em `openspec/specs/admin-access` depois do arquivamento da change `admin-access-management`.
