# Design

## Context

Ver `proposal.md` para a motivação e as specs `admin-access` e `admin-venues` para o contrato.

O que existe hoje:
- `admin_access` (V2) tem `id UUID`, `google_sub` único, `status` (`PENDING` ou `APPROVED`), `requested_at`, `email` e `name`. Nenhum código cria linhas fora dos testes.
- `AdminAccessAuthorizationManager` valida o token OIDC do Google (registro `google`, emissor `https://accounts.google.com` e `sub` não vazio) e depois exige `APPROVED` no banco a cada requisição.
- `GET /api/auth/session` devolve `accessStatus` (`NONE`, `PENDING` ou `APPROVED`).
- `SecurityConfig` deriva os matchers admin da lista `ADMIN_RESOURCES`, com `GET` e `POST` na coleção e `PUT` e `DELETE` no item com segmento único. O filtro de `no-store` roda antes do `CsrfFilter`.
- `AdminRequestValidation` valida corpo e `{id}` numérico cru. O advice de `400` vale só para os controllers listados nele.

## Goals / Non-Goals

**Goals:**

- Nenhum caminho HTTP aprova ninguém, exceto `PUT /api/admin/access/{id}` feito por um admin já aprovado.
- As regras "não remover a si mesmo" e "não remover o último admin" valem sob concorrência, garantidas pelo banco e não só pela aplicação.

**Non-Goals:**

- Novo status (`REJECTED`), histórico ou auditoria de quem aprovou. Rejeitar e revogar apagam a linha, por decisão do Vinícius.
- Notificar quem pediu acesso, convites e papéis diferentes de admin.
- Importar admins ou pendências do Supabase, o que o plano proíbe.

## Decisions

### 1. Métodos por recurso em `SecurityConfig`

`ADMIN_RESOURCES` vira um mapa de recurso para métodos permitidos na coleção e no item:
- `events`, `variable-venues` e `other-venues`: `GET` e `POST` na coleção, `PUT` e `DELETE` no item;
- `access`: só `GET` na coleção, `PUT` e `DELETE` no item.

Assim, `POST /api/admin/access` cai no `denyAll`. **Alternativa descartada:** deixar `POST` mapeado e rejeitar no controller. A rota ficaria liberada na camada de segurança sem existir.

### 2. Identidade Google separada da aprovação

A checagem de token Google sai de `AdminAccessAuthorizationManager` para um helper compartilhado. Um novo `GoogleIdentityAuthorizationManager` usa só essa checagem e protege `POST /api/auth/access-request`. O manager de admin passa a ser "identidade Google + `APPROVED`". O CSRF segue a regra padrão, e o filtro de `no-store` passa a cobrir também essa rota.

### 3. Pedido de acesso sem corrida

`INSERT ... ON CONFLICT (google_sub) DO NOTHING RETURNING id`, por query nativa. Se voltou uma linha, a resposta é `201 PENDING`. Se não, o serviço lê o status atual e responde `200`. Dois pedidos simultâneos do mesmo usuário não geram erro de chave única. O controller nunca lê o corpo. E-mail e nome vêm das claims `email` e `name` do `OidcUser`, ou ficam nulos.

### 4. Aprovação condicional com reconferência do executor

Numa transação:
1. `SELECT ... FROM admin_access WHERE status = 'APPROVED' FOR UPDATE`, a mesma trava usada pela remoção.
2. Se o `sub` de quem executa não está entre os travados, responder `403 FORBIDDEN`. Isso fecha a janela entre a autorização, que acontece no filtro, e a escrita.
3. `UPDATE admin_access SET status = 'APPROVED' WHERE id = :id AND status = 'PENDING'`, seguido da leitura do registro. Se não existe, a resposta é `404`. Se já estava aprovado, a resposta é `200` sem mudança. A atualização condicional torna aprovações simultâneas inofensivas. Como aprovação e remoção travam o mesmo conjunto de linhas, uma revogação em andamento faz a aprovação do revogado esperar e, ao seguir, falhar com `403`.

### 5. Remoção com trava nos aprovados

Numa transação:
1. Comparar o `google_sub` do alvo com o `sub` da sessão. Se for o mesmo, responder `409 CANNOT_REMOVE_SELF`.
2. Rodar `SELECT id, google_sub FROM admin_access WHERE status = 'APPROVED' FOR UPDATE`. Se o executor não está entre os travados, responder `403 FORBIDDEN`.
3. Reler o alvo. Se não existe, responder `204`.
4. Se o alvo é `APPROVED` e a contagem travada é `<= 1`, responder `409 LAST_ADMIN`.
5. Caso contrário, apagar.

No PostgreSQL em `READ COMMITTED`, a segunda transação espera a trava e, ao seguir, enxerga a linha já apagada pela primeira. Por isso, numa revogação cruzada entre dois admins, a segunda recebe `LAST_ADMIN`. **Alternativa descartada:** isolamento `SERIALIZABLE` com retry. Mais complexo para um caso que a trava resolve.

### 6. Primeiro admin

`AdminBootstrap`, um `ApplicationRunner`, lê `APP_BOOTSTRAP_ADMIN_SUB`. Se a variável vier vazia, não faz nada. Se o valor não casar `[A-Za-z0-9]{1,255}`, a aplicação falha ao subir, com uma mensagem que não contém o valor. Se vier preenchida, numa transação:
1. `pg_advisory_xact_lock` fixo, para serializar várias instâncias subindo ao mesmo tempo;
2. se não existe `APPROVED`, `INSERT ... ON CONFLICT (google_sub) DO UPDATE SET status = 'APPROVED'`.

O log diz só "aplicado" ou "ignorado", sem o `sub`. **Alternativas descartadas** (escolha do Vinícius): SQL manual e comando de CLI.

**Como obter o `sub`:** `GET /api/auth/session` passa a devolver `subject`, o `sub` da própria sessão. O procedimento fica documentado: subir sem a variável, fazer login, consultar a sessão, copiar o `subject`, definir a variável e reiniciar.

**Recuperação:** se um `sub` errado for aprovado, apagar os registros `APPROVED` no banco, corrigir a variável e reiniciar. Esse procedimento é coberto por teste.

### 7. Identificador e validação

`AdminRequestValidation` ganha `rawUuid(request)`, que aceita só `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`, e o advice passa a incluir o controller de acesso. O corpo do `PUT` passa por `requireObjectWithFields(body, {"status"})` e exige `status == "APPROVED"`.

### 8. Provas

- **Integração HTTP** (MockMvc com PostgreSQL de teste): contrato, autorização, CSRF, `no-store`, isolamento, revogação de sessão ativa e ausência do `sub` nas respostas.
- **Concorrência:** duas threads com `CountDownLatch` chamando o serviço em transações próprias, repetido algumas vezes. As invariantes conferidas são: pelo menos um `APPROVED`, no máximo uma revogação concluída e um único registro por `sub`.
- **Primeiro admin:** o runner roda direto contra o banco em cada estado inicial. Um teste de contexto confirma que a aplicação sobe com a variável definida.
- **`5xx`:** no teste de falhas de autorização, pela cadeia HTTP real.

## Risks / Trade-offs

- **[Variável esquecida no ambiente]** Se alguém apagar todos os admins direto no banco, a variável volta a valer no próximo boot. → É o comportamento desejado para recuperação. O ADR deve registrar que ela pode ficar definida.
- **[Sem histórico]** Apagar a linha perde quem foi rejeitado. → Decisão do Vinícius. Pode ser revista com um status novo, em outra change.
- **[Aprovar a pessoa errada]** A listagem identifica o solicitante pelo e-mail. → O pedido só é aceito com `email` e `email_verified == true` no token (`403 EMAIL_NOT_VERIFIED` caso contrário), então todo pedido listado tem um e-mail verificado pelo Google.
- **[`subject` na sessão]** A resposta da sessão passa a incluir o próprio `sub`. → Ele só é mostrado ao dono da sessão, com `no-store`. O `sub` é um identificador, não uma credencial.

## Migration Plan

Não há migration. Para ativar, defina `APP_BOOTSTRAP_ADMIN_SUB` com o seu `sub` no `.env` e suba o backend. Para reverter, basta remover o recurso `access` da lista e os controllers novos. Os registros existentes continuam válidos.
